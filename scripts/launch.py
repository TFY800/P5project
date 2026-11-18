#!/usr/bin/env python3
"""One-click macOS startup, reusing the reviewed local lifecycle tools."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

import dev


def needs_build():
    for name in dev.SERVICES:
        jar = dev.ROOT / (name + '-service') / 'target' / (name + '-service-0.0.1-SNAPSHOT.jar')
        stamp = dev.LOCAL / (name + '.build.json')
        try:
            record = json.loads(stamp.read_text())
            if record.get('source') != dev.fingerprint(name):
                return True
            if record.get('jar') != hashlib.sha256(jar.read_bytes()).hexdigest():
                return True
        except (OSError, ValueError):
            return True
    return False


def main():
    parser = argparse.ArgumentParser(description='Start the local P5 auction project.')
    parser.add_argument('--no-browser', action='store_true', help='Start without opening the browser.')
    args = parser.parse_args()
    os.chdir(dev.ROOT)
    print('P5 / SecondRound 一键启动', flush=True)
    print('项目目录：' + str(dev.ROOT), flush=True)
    java = shutil.which('java')
    if not java:
        raise SystemExit('未找到 Java。请安装 JDK 21 后重试。')
    version = subprocess.run([java, '-version'], capture_output=True, text=True)
    if version.returncode or not re.search(r'version "21(?:\.|\")', version.stdout + version.stderr):
        raise SystemExit('当前不是 JDK 21。请安装或选择 JDK 21 后重试。')

    try:
        dev.pg_bin()
    except SystemExit:
        if dev.environment().get('PG_BIN'):
            raise SystemExit('PG_BIN 指向的 PostgreSQL 不可用，请检查本地 .env；未修改现有配置。')
        for tool in ['cc', 'make', 'curl', 'shasum']:
            if not shutil.which(tool):
                raise SystemExit('缺少 ' + tool + '，无法准备 PostgreSQL。请参阅 docs/local-development.md。')
        print('首次启动：下载并编译仓库内的 PostgreSQL，可能需要几分钟；不会安装系统软件。', flush=True)
        subprocess.run([str(dev.ROOT / 'scripts/bootstrap-postgres.sh')], check=True)

    print('\n[1/3] 启动数据库，保留已有数据和密码…', flush=True)
    dev.db_init()
    if needs_build():
        print('\n[2/3] 构建四个服务（首次需要下载 Maven 依赖）…', flush=True)
        dev.build()
    else:
        print('\n[2/3] 源码和构建产物一致，复用现有构建。', flush=True)
    print('\n[3/3] 启动四个服务和网页，等待健康检查…', flush=True)
    for name in dev.SERVICES + ['client']:
        dev.start(name)
    print('\n启动完成：http://localhost:8080', flush=True)
    print('可以关闭此终端窗口，服务会继续运行。', flush=True)
    print('停止应用：双击 Stop.command；日志：.local/。', flush=True)
    if not args.no_browser:
        if subprocess.run(['/usr/bin/open', 'http://localhost:8080']).returncode:
            print('浏览器未能自动打开，请手动访问上述地址。', flush=True)


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print('\n启动已中断；已启动的进程可通过 Stop.command 停止。', file=sys.stderr)
        sys.exit(130)
    except subprocess.CalledProcessError:
        sys.exit('启动未完成，请检查上方错误和 .local/ 日志；没有重置数据库或配置。')
