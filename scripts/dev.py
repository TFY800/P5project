#!/usr/bin/env python3
"""Local lifecycle tools. Only stop processes whose PID, start time and command match our records."""
import argparse
import json
import hashlib
import os
from pathlib import Path
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / '.local'
SERVICES = ['user', 'item', 'auction', 'notification']


def environment():
    env = os.environ.copy()
    config = ROOT / '.env'
    if config.exists():
        for line in config.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith('#'):
                key, sep, value = line.partition('=')
                if not sep or not key.replace('_', '').isalnum():
                    raise SystemExit('Invalid .env format; use KEY=value without shell expressions.')
                env.setdefault(key, value)
    env['MAVEN_USER_HOME'] = str(ROOT / '.build-cache/maven')
    return env


def pg_bin():
    configured = os.environ.get('PG_BIN') or environment().get('PG_BIN')
    choices = [Path(configured)] if configured else []
    choices += [ROOT / '.build-cache/postgres/bin', Path('/opt/homebrew/opt/postgresql@17/bin'),
                Path('/opt/homebrew/opt/postgresql@18/bin'), Path('/Applications/Postgres.app/Contents/Versions/latest/bin')]
    if shutil.which('pg_ctl'):
        choices.append(Path(shutil.which('pg_ctl')).parent)
    for path in choices:
        if (path / 'pg_ctl').exists():
            return path
    raise SystemExit('PostgreSQL not found. Set PG_BIN, install PostgreSQL, or run scripts/bootstrap-postgres.sh.')


def init_env():
    path = ROOT / '.env'
    if path.exists():
        print('.env already exists; preserved.')
        return
    values = {'JWT_SECRET': secrets.token_hex(48), 'INTERNAL_SECRET': secrets.token_hex(48),
              'PG_ADMIN_PASSWORD': secrets.token_hex(24), 'CLIENT_ORIGIN': 'http://localhost:8080'}
    for name in SERVICES[:3]:
        values[name.upper() + '_DB_PASSWORD'] = secrets.token_hex(24)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as stream:
        stream.write(''.join(f'{key}={value}\n' for key, value in values.items()))
    print('Created private .env with random local secrets; values are not printed.')


def admin_sql(sql, database='postgres'):
    env = environment()
    env['PGPASSWORD'] = env['PG_ADMIN_PASSWORD']
    result = subprocess.run([str(pg_bin() / 'psql'), '-X', '-w', '-h', '127.0.0.1', '-p', '55432',
                             '-U', 'p5_admin', '-d', database, '-v', 'ON_ERROR_STOP=1', '-At'],
                            input=sql, text=True, env=env, capture_output=True)
    if result.returncode:
        # Never echo SQL or environment: SQL can contain generated role credentials.
        raise SystemExit('Database initialization failed. Check PostgreSQL and local credentials; no databases were reset.')
    return result.stdout.strip()


def db_start():
    LOCAL.mkdir(mode=0o700, exist_ok=True)
    data = LOCAL / 'postgres'
    env = environment()
    binary = pg_bin()
    if not data.exists():
        password = LOCAL / 'pg-init-password'
        fd = os.open(password, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        try:
            with os.fdopen(fd, 'w') as out:
                out.write(env['PG_ADMIN_PASSWORD'])
            with (LOCAL / 'initdb.log').open('w') as log:
                subprocess.run([str(binary / 'initdb'), '-D', str(data), '-U', 'p5_admin',
                                '--pwfile=' + str(password), '--auth=scram-sha-256', '--encoding=UTF8', '--locale=C'],
                               stdout=log, stderr=log, check=True)
        finally:
            password.unlink(missing_ok=True)
    if not (data / 'PG_VERSION').exists():
        raise SystemExit('Existing data directory is not an initialized cluster; preserved without modification.')
    if subprocess.run([str(binary / 'pg_ctl'), '-D', str(data), 'status'], capture_output=True).returncode == 0:
        print('Repository PostgreSQL is already running.')
        return
    with socket.socket() as probe:
        probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            probe.bind(('127.0.0.1', 55432))
        except OSError:
            raise SystemExit('Port 55432 is occupied; no existing PostgreSQL instance was changed.')
    subprocess.run([str(binary / 'pg_ctl'), '-D', str(data), '-l', str(LOCAL / 'postgres.log'),
                    '-o', f'-p 55432 -h 127.0.0.1 -k "{LOCAL}"', '-w', 'start'], check=True)


def db_init():
    init_env()
    db_start()
    env = environment()
    for service in SERVICES[:3]:
        name = 'p5_' + service
        exists = admin_sql("SELECT 1 FROM pg_roles WHERE rolname='" + name + "';")
        if not exists:
            password = env[service.upper() + '_DB_PASSWORD'].replace("'", "''")
            admin_sql(f"CREATE ROLE {name} LOGIN PASSWORD '{password}' NOSUPERUSER NOCREATEDB NOCREATEROLE;")
        owner = admin_sql(f"SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='{name}';")
        if not owner:
            admin_sql(f'CREATE DATABASE {name} OWNER {name};')
            admin_sql(f'REVOKE CONNECT ON DATABASE {name} FROM PUBLIC; GRANT CONNECT ON DATABASE {name} TO {name};')
        elif owner != name:
            raise SystemExit(f'{name} has an unexpected owner; preserved without modification.')
    print('Three separate databases/roles are ready. Existing passwords and data were preserved.')


def identity(pid):
    result = subprocess.run(['ps', '-p', str(pid), '-o', 'lstart=', '-o', 'command='], capture_output=True, text=True)
    return result.stdout.strip() if result.returncode == 0 else None


def record_path(name):
    return LOCAL / (name + '.pid.json')


def owned(name):
    path = record_path(name)
    if not path.exists():
        return None
    record = json.loads(path.read_text())
    if identity(record['pid']) != record['identity']:
        return None
    return record['pid']


def stop(name):
    pid = owned(name)
    if pid:
        os.kill(pid, signal.SIGTERM)
        for _ in range(150):
            current = owned(name)
            if not current:
                break
            time.sleep(.1)
        if owned(name):
            raise SystemExit(f'{name} did not stop gracefully; left for manual inspection, no SIGKILL sent.')
        print(name + ' stopped')
    record_path(name).unlink(missing_ok=True)


def fingerprint(name):
    service = ROOT / (name + '-service')
    files = [service / 'pom.xml'] + sorted(p for p in (service / 'src/main').rglob('*') if p.is_file())
    digest = hashlib.sha256()
    for path in files:
        digest.update(str(path.relative_to(service)).encode())
        digest.update(path.read_bytes())
    return digest.hexdigest()


def build():
    # Replacing an executing Spring Boot JAR breaks its lazy class loader. Stop only our owned JVMs first.
    for name in reversed(SERVICES):
        if owned(name):
            stop(name)
    env = environment()
    for name in SERVICES:
        subprocess.run([str(ROOT / (name + '-service') / 'mvnw'), '-B', '-f',
                        str(ROOT / (name + '-service') / 'pom.xml'),
                        '-Dmaven.repo.local=' + str(ROOT / '.build-cache/repository'), 'verify'], env=env, check=True)
        LOCAL.mkdir(mode=0o700, exist_ok=True)
        jar = ROOT / (name + '-service') / 'target' / (name + '-service-0.0.1-SNAPSHOT.jar')
        (LOCAL / (name + '.build.json')).write_text(json.dumps({'source': fingerprint(name), 'jar': hashlib.sha256(jar.read_bytes()).hexdigest()}))


def start(name):
    LOCAL.mkdir(mode=0o700, exist_ok=True)
    if owned(name):
        print(name + ' already running')
        return
    if name == 'client':
        command = [sys.executable, '-m', 'http.server', '8080', '--bind', '127.0.0.1', '--directory', str(ROOT / 'client')]
        port = 8080
        health = '/'
    else:
        jar = ROOT / (name + '-service') / 'target' / (name + '-service-0.0.1-SNAPSHOT.jar')
        if not jar.exists():
            raise SystemExit('Build first: python3 scripts/dev.py build')
        stamp = LOCAL / (name + '.build.json')
        record = json.loads(stamp.read_text()) if stamp.exists() else {}
        if record.get('source') != fingerprint(name) or record.get('jar') != hashlib.sha256(jar.read_bytes()).hexdigest():
            raise SystemExit(name + ' JAR does not match verified sources. Run python3 scripts/dev.py build first.')
        command = ['java', '-XX:-UsePerfData', '-jar', str(jar)]
        port = 8081 + SERVICES.index(name)
        health = '/api/health'
    with socket.socket() as probe:
        probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            probe.bind(('127.0.0.1', port))
        except OSError:
            raise SystemExit(f'Port {port} is occupied. No unrelated process was stopped.')
    with (LOCAL / (name + '.log')).open('a') as log:
        proc = subprocess.Popen(command, cwd=ROOT, env=environment(), stdout=log, stderr=log, start_new_session=True)
    time.sleep(.1)
    record_path(name).write_text(json.dumps({'pid': proc.pid, 'identity': identity(proc.pid)}))
    for _ in range(180):
        if proc.poll() is not None:
            raise SystemExit(f'{name} exited. See .local/{name}.log (secrets are not intentionally logged).')
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{port}{health}', timeout=1) as response:
                if response.status == 200:
                    print(f'{name} ready on {port}')
                    return
        except OSError:
            time.sleep(.5)
    stop(name)
    raise SystemExit(name + ' startup timed out')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['env', 'db-init', 'db-start', 'db-stop', 'build', 'start', 'stop', 'status'])
    parser.add_argument('service', nargs='?', choices=SERVICES + ['client'])
    args = parser.parse_args()
    if args.command == 'env':
        init_env()
    elif args.command == 'db-init':
        db_init()
    elif args.command == 'db-start':
        db_start()
    elif args.command == 'db-stop':
        subprocess.run([str(pg_bin() / 'pg_ctl'), '-D', str(LOCAL / 'postgres'), '-m', 'fast', '-w', 'stop'], check=True)
    elif args.command == 'build':
        build()
    else:
        names = [args.service] if args.service else SERVICES + ['client']
        if args.command == 'stop':
            names.reverse()
        for name in names:
            if args.command == 'start':
                start(name)
            elif args.command == 'stop':
                stop(name)
            else:
                print(name + ': ' + ('running (owned PID ' + str(owned(name)) + ')' if owned(name) else 'not managed/running'))


if __name__ == '__main__':
    main()
