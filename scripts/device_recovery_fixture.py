#!/usr/bin/env python3
"""Temporary loopback-only synthetic backend for the isolated processfixture APK.
No imports from the real backend and no database access. Authentication is synthetic.
"""
import argparse
import hashlib
import json
import socket
import socketserver
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
lock = threading.RLock()
state = {'physical_executions': 0, 'attempts': [], 'accepted': 0, 'version': 2, 'token': None}


def save():
    temporary = args.output / 'state.tmp'
    temporary.write_text(json.dumps(state, ensure_ascii=False, indent=2))
    temporary.replace(args.output / 'state.json')


def task():
    return {'id': 101, 'type': 'RECEIVE_GOODS', 'status': 'new' if not state['accepted'] else 'partially_completed',
            'version': state['version'], 'warehouse_id': 42, 'assignee_user_id': 42 if state['accepted'] else None,
            'created_by': 1, 'created_at': '2026-11-01T12:00:00Z', 'comment': 'Isolated process-death fixture',
            'transfer_version': state['version'], 'allowed_actions': ['start', 'execute'],
            'lines': [{'id': 1001, 'product_id': 301, 'product_name': 'Материал', 'role': 'receive', 'base_unit': 'шт',
                       'planned_qty': '10', 'actual_qty': str(state['accepted']), 'remaining_qty': str(10 - state['accepted'])}]}


def page(items):
    return {'items': items, 'total': len(items), 'limit': 50, 'offset': 0}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass  # Never log cookies, auth requests or bodies.

    def send_json(self, body, status=200, cookie=None):
        raw = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(raw)))
        if cookie:
            self.send_header('Set-Cookie', cookie)
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        path = self.path.split('?', 1)[0]
        with lock:
            if path == '/__fixture_state':
                return self.send_json(state)
            if path == '/auth/me':
                return self.send_json({'id': 42, 'login': 'fixture-worker', 'role': 'worker'}) if 'session=process-fixture' in self.headers.get('Cookie', '') else self.send_json({'detail': 'Требуется вход'}, 401)
            if path == '/api/warehouses':
                return self.send_json([{'id': 7, 'name': 'Основной', 'code': 'main', 'is_active': True}, {'id': 42, 'name': 'Цех', 'code': 'workshop', 'is_active': True}])
            if path == '/api/workshop/tasks':
                return self.send_json(page([task()]))
            if path == '/api/workshop/tasks/101':
                return self.send_json(task())
            if path == '/api/workshop/tasks/101/executions':
                return self.send_json(page([]))
            if path == '/api/workshop/tasks/101/history':
                return self.send_json(page([]))
            return self.send_json({'code': 'NOT_FOUND', 'message': 'Fixture route unavailable'}, 404)

    def do_POST(self):
        raw = self.rfile.read(int(self.headers.get('Content-Length', '0')))
        path = self.path.split('?', 1)[0]
        with lock:
            if path == '/auth/login':
                # The account exists only in this in-memory fixture.
                return self.send_json({'login': 'fixture-worker', 'role': 'worker'}, cookie='session=process-fixture; Path=/; HttpOnly')
            if path == '/auth/logout':
                return self.send_json({'ok': True})
            if path != '/api/workshop/tasks/101/executions':
                return self.send_json({'code': 'NOT_FOUND', 'message': 'Fixture mutation unavailable'}, 404)
            payload = json.loads(raw)
            state['attempts'].append({'body': raw.decode('utf-8'), 'sha256': hashlib.sha256(raw).hexdigest()})
            if state['token'] is None:
                assert payload['type'] == 'RECEIVE_GOODS'
                assert payload['expected_version'] == 2 and payload['expected_transfer_version'] == 2
                assert payload['lines'] == [{'task_line_id': 1001, 'qty': '4'}]
                state['token'] = payload['client_token']
                state['physical_executions'] = 1
                state['accepted'] = 4
                state['version'] = 3
            else:
                assert payload['client_token'] == state['token'], 'A retry must retain its original token'
                assert raw.decode('utf-8') == state['attempts'][0]['body'], 'A retry must be byte-identical'
            save()
            if not (args.output / 'allow-reply').exists():
                # Server committed but the client never receives a response before force-stop.
                try:
                    self.connection.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                self.connection.close()
                return
            return self.send_json({'result': {'id': 101, 'execution_id': 5001, 'duplicate': True}, 'task': task()})


class LoopbackFixtureServer(ThreadingHTTPServer):
    def server_bind(self):
        # HTTPServer normally calls reverse DNS even for 127.0.0.1. This owned
        # loopback fixture needs no DNS and must not depend on external resolution.
        socketserver.TCPServer.server_bind(self)
        self.server_name = 'localhost'
        self.server_port = self.server_address[1]


server = LoopbackFixtureServer(('127.0.0.1', 0), Handler)
save()
(args.output / 'port').write_text(str(server.server_address[1]))
server.serve_forever()
