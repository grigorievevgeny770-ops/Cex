"""Execute the shipped migration SQL against an isolated SQLite v1 fixture."""
import json
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[1]
schemas = root / 'data/schemas/com.logunovinc.workshopmanager.data.local.pending.CommandDatabase'
old = json.loads((schemas / '1.json').read_text())['database']
new = json.loads((schemas / '2.json').read_text())['database']
db = sqlite3.connect(':memory:')
db.execute('PRAGMA foreign_keys=ON')
for table in old['entities']:
    db.execute(table['createSql'].replace('${TABLE_NAME}', table['tableName']))
    for index in table['indices']:
        db.execute(index['createSql'].replace('${TABLE_NAME}', table['tableName']))
body = b'{"client_token":"preserve-token","expected_version":3}'
db.execute('INSERT INTO pending_commands VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
    ('saved','https://synthetic.invalid/',42,7,'REQUEST_START','preserve-token','POST',
     'api/workshop/requests/7/start',body,'hash','{}',1,'UNKNOWN',1,None,1,None,None,None))
db.execute('INSERT INTO active_commands VALUES (?,?,?,?)', ('https://synthetic.invalid/',42,7,'saved'))
source = (root / 'data/src/main/java/com/logunovinc/workshopmanager/data/local/pending/CommandMigrations.kt').read_text()
for sql in re.findall(r'db.execSQL\("([^"\n]+)"\)', source):
    db.execute(sql)
assert db.execute('SELECT body,clientToken,scope,state FROM pending_commands').fetchone() == (body,'preserve-token','request','UNKNOWN')
for table in new['entities']:
    actual = {row[1] for row in db.execute('PRAGMA table_info('+table['tableName']+')')}
    assert actual == {field['columnName'] for field in table['fields']}
assert db.execute('SELECT scope FROM active_commands').fetchone() == ('request',)
assert db.execute('PRAGMA foreign_key_check').fetchall() == []
print('Room 1→2 SQL: preserved exact bytes/token/state, scopes, columns and foreign keys')
