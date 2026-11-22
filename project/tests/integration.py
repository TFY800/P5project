#!/usr/bin/env python3
"""Real HTTP + real PostgreSQL acceptance tests. Creates uniquely named records; never drops data.
Run against repository-managed local services: python3 tests/integration.py -v
The recovery test briefly stops/restarts only notification and auction via recorded process identity.
"""
import base64
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import hashlib
import hmac
import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import sys
import time
import unittest
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import dev


def call(port, path, method='GET', body=None, token=None, key=None, internal=None):
    headers = {'Content-Type': 'application/json', 'Origin': 'http://localhost:8080'}
    if token: headers['Authorization'] = 'Bearer ' + token
    if key: headers['Idempotency-Key'] = key
    if internal: headers['X-Internal-Key'] = internal
    request = urllib.request.Request(f'http://localhost:{port}{path}', method=method,
                                     data=json.dumps(body).encode() if body is not None else None, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=12) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as response:
        with response:
            return response.code, json.load(response)


def control(command, service):
    subprocess.run([sys.executable, str(ROOT / 'scripts/dev.py'), command, service], check=True, capture_output=True)


def wait_for(predicate, timeout=15):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        value = predicate()
        if value: return value
        time.sleep(.2)
    raise AssertionError('Timed out waiting for expected state')


class WebSocket:
    """Small RFC6455 test client using stdlib; browser tests exercise the actual browser stack too."""
    def __init__(self, auction):
        self.socket = socket.create_connection(('localhost', 8084), 3)
        self.socket.settimeout(5)
        key = base64.b64encode(os.urandom(16)).decode()
        self.socket.sendall((f'GET /ws HTTP/1.1\r\nHost: localhost:8084\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\nOrigin: http://localhost:8080\r\n\r\n').encode())
        headers = b''
        while not headers.endswith(b'\r\n\r\n'): headers += self.socket.recv(1)
        assert b'101' in headers.split(b'\r\n')[0], headers.split(b'\r\n')[0]
        self.send(json.dumps({'action': 'subscribe', 'auctionId': auction}).encode())
        assert self.receive()['type'] == 'subscribed'

    def send(self, data, opcode=1):
        mask = os.urandom(4)
        header = bytes([0x80 | opcode])
        if len(data) < 126: header += bytes([0x80 | len(data)])
        else: header += bytes([0x80 | 126]) + struct.pack('!H', len(data))
        self.socket.sendall(header + mask + bytes(v ^ mask[i % 4] for i, v in enumerate(data)))

    def read(self, n):
        result = b''
        while len(result) < n:
            chunk = self.socket.recv(n - len(result))
            if not chunk: raise ConnectionError('Socket closed')
            result += chunk
        return result

    def receive(self):
        while True:
            first, second = self.read(2)
            length = second & 127
            if length == 126: length = struct.unpack('!H', self.read(2))[0]
            if length == 127: length = struct.unpack('!Q', self.read(8))[0]
            payload = self.read(length)
            opcode = first & 15
            if opcode == 9: self.send(payload, 10)
            elif opcode == 1: return json.loads(payload)
            elif opcode == 8: raise ConnectionError('Socket closed')

    def close(self):
        self.socket.close()


class Acceptance(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.env = dev.environment()
        cls.run_id = uuid.uuid4().hex[:12]
        cls.accounts = []
        for i in range(5):
            email = f'p5-test-{cls.run_id}-{i}@example.com'
            password = 'Course-Password-123'
            code, user = call(8081, '/api/auth/register', 'POST', {'email': email, 'password': password, 'displayName': f'Test user {i}'})
            assert code == 201, (code, user)
            code, login = call(8081, '/api/auth/login', 'POST', {'email': email, 'password': password})
            assert code == 200
            cls.accounts.append({'user': user, 'token': login['accessToken'], 'email': email, 'password': password})
        cls.seller, cls.buyer, cls.other = cls.accounts[:3]

    def item(self, owner=None):
        owner = owner or self.seller
        code, item = call(8082, '/api/items', 'POST', {'name': 'Acceptance item ' + self.run_id,
            'description': 'A real integration test listing. Used condition.', 'imageUrl': 'https://example.com/item.png'}, owner['token'], str(uuid.uuid4()))
        self.assertEqual(code, 201, item)
        return item

    def auction(self, seconds=120, item=None):
        item = item or self.item()
        body = {'itemId': item['id'], 'startingPrice': 1000, 'minimumIncrement': 100,
                'endsAt': (datetime.now(timezone.utc) + timedelta(seconds=seconds)).isoformat()}
        key = str(uuid.uuid4())
        code, auction = call(8083, '/api/auctions', 'POST', body, self.seller['token'], key)
        self.assertEqual(code, 202, auction)
        self.assertEqual(auction['status'], 'ACTIVE', auction)
        return auction, body, key

    def bid(self, auction, amount, account=None, key=None):
        return call(8083, '/api/auctions/' + auction['id'] + '/bids', 'POST', {'amount': amount},
                    (account or self.buyer)['token'], key or str(uuid.uuid4()))

    def snapshot(self, auction):
        code, value = call(8083, '/api/auctions/' + auction['id'])
        self.assertEqual(code, 200)
        return value

    def test_01_authentication_validation_and_database_isolation(self):
        code, body = call(8081, '/api/auth/login', 'POST', {'email': self.seller['email'], 'password': 'WrongPassword123'})
        self.assertEqual(code, 401)
        code, body = call(8081, '/api/auth/register', 'POST', {'email': '  ' + self.seller['email'].upper() + '  ', 'password': 'Course-Password-123', 'displayName': 'Duplicate'})
        self.assertEqual(code, 409)
        self.assertEqual(call(8081, '/api/users/me')[0], 401)
        code, body = call(8081, '/api/users/me', token=self.buyer['token'])
        self.assertEqual(code, 200)
        self.assertFalse(any('password' in key.lower() for key in body))
        self.assertEqual(call(8081, '/api/auth/register', 'POST', {'email': 'bad', 'password': 'tiny', 'displayName': ''})[0], 400)
        self.assertEqual(call(8082, '/api/items', 'POST', {})[0], 401)
        # Neither an altered signature nor a legitimately signed expired JWT is accepted.
        parts = self.buyer['token'].split('.')
        self.assertEqual(call(8081, '/api/users/me', token=parts[0] + '.' + parts[1] + '.invalid')[0], 401)
        claims = json.loads(base64.urlsafe_b64decode(parts[1] + '=='))
        claims['exp'] = int(time.time()) - 120
        encode = lambda value: base64.urlsafe_b64encode(value).decode().rstrip('=')
        content = parts[0] + '.' + encode(json.dumps(claims).encode())
        signature = encode(hmac.new(self.env['JWT_SECRET'].encode(), content.encode(), hashlib.sha256).digest())
        self.assertEqual(call(8081, '/api/users/me', token=content + '.' + signature)[0], 401)
        for bad_claims in [dict(claims, exp=None), dict(claims, exp=int(time.time())+3600, aud=['another-app']),
                           dict(claims, exp=int(time.time())+3600, iss='another-issuer')]:
            content = parts[0] + '.' + encode(json.dumps(bad_claims).encode())
            signature = encode(hmac.new(self.env['JWT_SECRET'].encode(), content.encode(), hashlib.sha256).digest())
            self.assertEqual(call(8081, '/api/users/me', token=content + '.' + signature)[0], 401)
        for actor, target in [('USER', 'item'), ('ITEM', 'auction'), ('AUCTION', 'user')]:
            env = os.environ.copy(); env['PGPASSWORD'] = self.env[actor + '_DB_PASSWORD']
            result = subprocess.run([str(dev.pg_bin() / 'psql'), '-X', '-w', '-h', '127.0.0.1', '-p', '55432',
                                     '-U', 'p5_' + actor.lower(), '-d', 'p5_' + target, '-c', 'SELECT 1'], env=env, capture_output=True)
            self.assertNotEqual(result.returncode, 0, 'Cross-service database access must be rejected')
        stored = dev.admin_sql("SELECT password_hash FROM users WHERE id='" + self.seller['user']['id'] + "';", 'p5_user')
        self.assertTrue(stored.startswith('$2'))
        self.assertNotEqual(stored, self.seller['password'])

    def test_02_item_ownership_and_freeze(self):
        item = self.item()
        changes = {'name': 'Changed item', 'description': 'Changed condition', 'imageUrl': 'https://example.com/updated.png'}
        self.assertEqual(call(8082, '/api/items/' + item['id'], 'PUT', changes, self.buyer['token'])[0], 403)
        self.assertEqual(call(8082, '/api/items/' + item['id'], 'PUT', changes, self.seller['token'])[0], 200)
        self.assertEqual(call(8082, '/internal/items/' + item['id'] + '/freeze', 'POST', {})[0], 401)
        self.assertEqual(call(8084, '/internal/events', 'POST', {}, self.buyer['token'])[0], 401)
        auction, body, key = self.auction(item=item)
        code, replay = call(8083, '/api/auctions', 'POST', body, self.seller['token'], key)
        self.assertEqual(replay['id'], auction['id'])
        self.assertEqual(call(8083, '/api/auctions', 'POST', body, self.seller['token'], str(uuid.uuid4()))[0], 409)
        self.assertEqual(call(8082, '/api/items/' + item['id'], 'PUT', changes, self.seller['token'])[0], 409)
        self.assertEqual(call(8082, '/api/items/mine', token=self.seller['token'])[0], 200)
        bad = dict(changes, imageUrl='relative-path.png')
        self.assertEqual(call(8082, '/api/items', 'POST', bad, self.seller['token'])[0], 400)
        bad['imageUrl'] = 'javascript:alert(1)'
        self.assertEqual(call(8082, '/api/items', 'POST', bad, self.seller['token'])[0], 400)
        item_key = str(uuid.uuid4())
        one = call(8082, '/api/items', 'POST', changes, self.seller['token'], item_key)
        two = call(8082, '/api/items', 'POST', changes, self.seller['token'], item_key)
        self.assertEqual(one[1]['id'], two[1]['id'])
        self.assertEqual(call(8082, '/api/items?page=0&size=2')[1]['size'], 2)

    def test_03_auction_creation_permissions_and_recovery(self):
        item = self.item()
        body = {'itemId': item['id'], 'startingPrice': 1000, 'minimumIncrement': 100,
                'endsAt': (datetime.now(timezone.utc) + timedelta(minutes=5)).isoformat()}
        code, rejected = call(8083, '/api/auctions', 'POST', body, self.buyer['token'], str(uuid.uuid4()))
        self.assertEqual(rejected['status'], 'FAILED')
        self.assertIsNone(call(8082, '/api/items/' + item['id'])[1]['frozenBy'])
        body['endsAt'] = '2020-01-01T00:00:00Z'
        self.assertEqual(call(8083, '/api/auctions', 'POST', body, self.seller['token'], str(uuid.uuid4()))[0], 400)
        body['endsAt'] = (datetime.now(timezone.utc) + timedelta(minutes=5)).isoformat()
        control('stop', 'item')
        try:
            code, preparing = call(8083, '/api/auctions', 'POST', body, self.seller['token'], str(uuid.uuid4()))
            self.assertEqual(preparing['status'], 'PREPARING')
        finally:
            control('start', 'item')
        wait_for(lambda: call(8083, '/api/auctions/' + preparing['id'])[1]['auction']['status'] == 'ACTIVE')
        self.assertEqual(call(8082, '/api/items/' + item['id'])[1]['frozenBy'], preparing['id'])

    def test_04_bid_rules_and_idempotency(self):
        auction, _, _ = self.auction()
        self.assertEqual(self.bid(auction, 999)[0], 409)
        self.assertEqual(self.bid(auction, 1000.5)[0], 400)
        self.assertEqual(call(8083, '/api/auctions/' + auction['id'] + '/bids', 'POST', {'amount':1000}, self.buyer['token'])[0], 400)
        self.assertEqual(self.bid(auction, 1000, self.seller)[0], 403)
        key = str(uuid.uuid4())
        code, first = self.bid(auction, 1000, key=key)
        self.assertEqual(code, 201)
        code, replay = self.bid(auction, 1000, key=key)
        self.assertEqual(first['id'], replay['id'])
        self.assertEqual(self.bid(auction, 1200, key=key)[0], 409)
        self.assertEqual(self.bid(auction, 1099, self.other)[0], 409)
        self.assertEqual(self.bid(auction, 1100, self.other)[0], 201)
        snapshot = self.snapshot(auction)
        self.assertEqual(snapshot['bidCount'], 2)
        self.assertEqual(snapshot['auction']['currentPrice'], 1100)
        self.assertEqual(snapshot['auction']['version'], 2)
        for path in ['mine', 'participated', 'won']:
            self.assertEqual(call(8083, '/api/auctions/' + path)[0], 401)
            self.assertEqual(call(8083, '/api/auctions/' + path, token=self.buyer['token'])[0], 200)

    def test_05_concurrent_bids_with_real_postgres_lock(self):
        auction, _, _ = self.auction()
        with ThreadPoolExecutor(max_workers=12) as pool:
            responses = list(pool.map(lambda i: self.bid(auction, 1000 + i * 100, self.accounts[1 + i % 4]), range(12)))
        self.assertTrue(all(code in (201, 409) for code, _ in responses))
        snapshot = self.snapshot(auction)
        accepted = sorted(snapshot['bids'], key=lambda b: b['version'])
        prices = [b['amount'] for b in accepted]
        self.assertGreater(len(prices), 0)
        self.assertEqual(prices, sorted(set(prices)))
        self.assertEqual(snapshot['auction']['currentPrice'], 2100)
        self.assertEqual(snapshot['auction']['version'], len(accepted))
        # Same idempotency key raced through multiple independent HTTP requests.
        key = str(uuid.uuid4())
        with ThreadPoolExecutor(max_workers=8) as pool:
            responses = list(pool.map(lambda _: self.bid(auction, 2300, self.buyer, key), range(8)))
        self.assertTrue(all(code == 201 for code, _ in responses))
        self.assertEqual(len({body['id'] for _, body in responses}), 1)
        self.assertEqual(self.snapshot(auction)['bidCount'], len(accepted) + 1)

    def test_06_expiry_winner_unsold_and_restart(self):
        auction, _, _ = self.auction(seconds=4)
        self.assertEqual(self.bid(auction, 1000)[0], 201)
        empty, _, _ = self.auction(seconds=4)
        control('stop', 'auction')
        time.sleep(4.2)
        control('start', 'auction')
        wait_for(lambda: self.snapshot(auction)['auction']['status'] == 'ENDED')
        snapshot = self.snapshot(auction)
        self.assertEqual(snapshot['auction']['leadingBidder'], self.buyer['user']['id'])
        self.assertEqual(snapshot['auction']['currentPrice'], 1000)
        self.assertEqual(snapshot['bidCount'], 1)
        self.assertEqual(self.bid(auction, 2000)[0], 409)
        wait_for(lambda: self.snapshot(empty)['auction']['status'] == 'ENDED')
        self.assertIsNone(self.snapshot(empty)['auction']['leadingBidder'])
        self.assertIn(auction['id'], [a['id'] for a in call(8083, '/api/auctions/won', token=self.buyer['token'])[1]['content']])

    def test_07_grouped_websocket_and_outbox_recovery(self):
        auction, _, _ = self.auction()
        unrelated, _, _ = self.auction()
        first, second, other = WebSocket(auction['id']), WebSocket(auction['id']), WebSocket(unrelated['id'])
        try:
            self.assertEqual(self.bid(auction, 1000)[0], 201)
            one, two = first.receive(), second.receive()
            self.assertEqual(one['type'], 'price_updated')
            self.assertEqual(one['eventId'], two['eventId'])
            self.assertEqual(one['auctionId'], auction['id'])
            other.socket.settimeout(1)
            with self.assertRaises(socket.timeout): other.receive()
        finally:
            first.close(); second.close(); other.close()
        control('stop', 'notification')
        try:
            self.assertEqual(self.bid(auction, 1200)[0], 201)
            time.sleep(1)
            pending = dev.admin_sql("SELECT count(*) FROM outbox WHERE NOT delivered AND payload LIKE '%" + auction['id'] + "%';", 'p5_auction')
            self.assertGreater(int(pending), 0)
            self.assertEqual(self.snapshot(auction)['auction']['currentPrice'], 1200)
        finally:
            control('start', 'notification')
        recovered = WebSocket(auction['id'])
        try:
            event = recovered.receive()
            self.assertEqual(event['version'], 2)
            self.assertEqual(event['price'], 1200)
            self.assertEqual(event['type'], 'price_updated')
        finally:
            recovered.close()
        wait_for(lambda: dev.admin_sql("SELECT count(*) FROM outbox WHERE NOT delivered AND payload LIKE '%" + auction['id'] + "%';", 'p5_auction') == '0')
        reconnected = WebSocket(auction['id'])
        try:
            self.assertEqual(self.snapshot(auction)['auction']['version'], 2)
        finally:
            reconnected.close()


    def test_08_expiry_is_checked_after_waiting_for_row_lock(self):
        auction, _, _ = self.auction(seconds=2)
        env = os.environ.copy()
        env['PGPASSWORD'] = self.env['AUCTION_DB_PASSWORD']
        env['PGAPPNAME'] = 'p5-row-lock-test'
        query = "BEGIN; SELECT id FROM auctions WHERE id='" + auction['id'] + "' FOR UPDATE; SELECT pg_sleep(3); COMMIT;"
        lock = subprocess.Popen([str(dev.pg_bin() / 'psql'), '-X', '-w', '-h', '127.0.0.1', '-p', '55432',
                                 '-U', 'p5_auction', '-d', 'p5_auction', '-v', 'ON_ERROR_STOP=1', '-c', query],
                                env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            wait_for(lambda: dev.admin_sql("SELECT count(*) FROM pg_stat_activity WHERE application_name='p5-row-lock-test' AND wait_event='PgSleep';", 'p5_auction') == '1')
            code, _ = self.bid(auction, 1000)
            self.assertEqual(code, 409, 'A bid queued before expiry must still be rejected if it acquires the lock after expiry')
            self.assertEqual(self.snapshot(auction)['bidCount'], 0)
        finally:
            lock.wait(timeout=5)


if __name__ == '__main__':
    unittest.main()
