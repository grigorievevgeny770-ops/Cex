#!/usr/bin/env python3
"""Actual emulator force-stop recovery test, isolated .processfixture package only.
Starts its own loopback synthetic fixture. Never contacts the user's warehouse DB.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--serial', default='emulator-5554')
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    raise SystemExit('This destructive-to-fixture-data test only targets an emulator.')
root = Path(__file__).resolve().parent.parent
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=True)
if (output / "port").exists():
    raise SystemExit("Use a fresh output directory for each fixture run.")
adb = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk'))) / 'platform-tools/adb'
package = 'com.logunovinc.workshopmanager.processfixture'
component = package + '/com.logunovinc.workshopmanager.MainActivity'


def device(*command, check=True):
    return subprocess.run([str(adb), '-s', args.serial, *command], check=check, capture_output=True).stdout


def pause(seconds=0.3):
    time.sleep(seconds)


def nodes():
    device('shell', 'uiautomator', 'dump', '/sdcard/workshop-process-fixture.xml')
    xml = device('shell', 'cat', '/sdcard/workshop-process-fixture.xml')
    return list(ET.fromstring(xml).iter('node'))


def screenshot(name):
    (output / (name + '.png')).write_bytes(device('exec-out', 'screencap', '-p'))


size = device('shell', 'wm', 'size').decode()
width, height = map(int, re.findall(r'(\d+)x(\d+)', size)[-1])


def click(node):
    left, top, right, bottom = map(int, re.findall(r'\d+', node.attrib['bounds']))
    device('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
    pause()


def find(text, scroll=False, attempts=12, exact=False):
    for _ in range(attempts):
        tree = nodes()
        for node in tree:
            candidates=(node.attrib.get('text',''),node.attrib.get('content-desc',''))
            if any(value == text if exact else text in value for value in candidates):
                return node
        if scroll:
            device('shell', 'input', 'swipe', str(width // 2), str(int(height * .78)), str(width // 2), str(int(height * .35)), '300')
        pause(.3)
    screenshot('failure')
    raise AssertionError('UI text not found: ' + text)


def wait_state(predicate, timeout=20):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        state = json.loads((output / 'state.json').read_text())
        if predicate(state):
            return state
        pause(.1)
    raise AssertionError('Fixture state condition timed out')


server_log = (output / 'fixture-server.log').open('w')
server = subprocess.Popen([sys.executable, str(root / 'scripts/device_recovery_fixture.py'), '--output', str(output)], stdout=server_log, stderr=subprocess.STDOUT)
try:
    deadline = time.monotonic() + 15
    while not (output / 'port').exists():
        if time.monotonic() > deadline or server.poll() is not None:
            raise AssertionError('Synthetic fixture did not start')
        pause(.1)
    port = int((output / 'port').read_text())
    with (output / 'build.log').open('w') as log:
        subprocess.run([str(root / 'gradlew'), ':app:assembleProcessFixture', f'-PfixtureBaseUrl=http://10.0.2.2:{port}/'], cwd=root, stdout=log, stderr=subprocess.STDOUT, check=True)
    apk = root / 'app/build/outputs/apk/processFixture/app-processFixture.apk'
    device('install', '-r', str(apk))
    # This package is exclusively generated for synthetic tests; the normal app is never cleared.
    device('shell', 'pm', 'clear', package)
    device('shell', 'am', 'start', '-W', '-n', component)
    login = find('Логин')
    click(login)
    device('shell', 'input', 'text', 'fixture-worker')
    click(find('Пароль'))
    device('shell', 'input', 'text', 'synthetic-only')
    device('shell', 'input', 'keyevent', '4')
    pause(1) # IME collapse must settle before taking coordinate bounds.
    click(find('Войти', scroll=True))
    click(find('Приёмка', scroll=True))
    click(find('Принять товары', scroll=True))
    click(find('Принять сейчас', scroll=True))
    device('shell', 'input', 'text', '4')
    device('shell', 'input', 'keyevent', '4')
    pause(1) # IME collapse must settle before taking coordinate bounds.
    for node in nodes():
        if node.attrib.get('checkable') == 'true' and node.attrib.get('checked') == 'true':
            click(node) # This scenario deliberately accepts a partial batch.
    click(find('Принять', scroll=True, exact=True))
    find('Принять эту часть?', exact=True)
    click(find('Подтвердить', exact=True))
    first = wait_state(lambda s: s['physical_executions'] == 1)
    pause(.5)
    screenshot('before-force-stop')
    previous_pid = device('shell', 'pidof', package).decode().strip()
    assert previous_pid, 'Fixture app process is not running'
    device('shell', 'am', 'force-stop', package)
    assert not device('shell', 'pidof', package, check=False).strip(), 'Process survived force-stop'
    attempts_before_restart = len(json.loads((output / 'state.json').read_text())['attempts'])
    (output / 'allow-reply').write_text('Only explicit retry may now receive its duplicate ACK.')
    device('shell', 'am', 'start', '-W', '-n', component)
    find('Приёмка', scroll=True, exact=True)
    after_restart = wait_state(lambda s: s['physical_executions'] == 1)
    assert len(after_restart['attempts']) == attempts_before_restart, 'Launch automatically replayed a stock command'
    new_pid = device('shell', 'pidof', package).decode().strip()
    assert new_pid and new_pid != previous_pid, 'Expected a new app process'
    click(find('Приёмка', scroll=True, exact=True))
    find('Исход неизвестен', scroll=True)
    screenshot('after-cold-restart-pending')
    click(find('Повторить действие', scroll=True))
    final = wait_state(lambda s: len(s['attempts']) > attempts_before_restart)
    find('Готово', scroll=True, exact=True)
    assert final['physical_executions'] == 1
    assert final['accepted'] == 4
    assert all(a['body'] == first['attempts'][0]['body'] for a in final['attempts'])
    screenshot('after-explicit-retry-ack')
    result = {'result': 'PASS', 'old_pid': previous_pid, 'new_pid': new_pid, 'physical_executions': final['physical_executions'],
              'attempts_before_restart': attempts_before_restart, 'attempts_after_explicit_retry': len(final['attempts']),
              'exact_body_sha256': final['attempts'][0]['sha256'], 'same_token_and_body': True,
              'automatic_replay_on_launch': False, 'package': package, 'fixture_base_url': f'http://10.0.2.2:{port}/'}
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
finally:
    server.terminate()
    server.wait(timeout=10)
    server_log.close()
