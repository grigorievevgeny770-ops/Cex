#!/usr/bin/env python3
"""Run unmodified archived FastAPI code exclusively inside an owned fixture root."""
import hashlib
import json
import os
import socket
import sys
from pathlib import Path


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


require(__debug__, 'Run without -O: business test assertions must remain enabled')
root = Path(sys.argv[1]).resolve()
marker = json.loads((root / 'fixture-manifest.json').read_text())
require(marker['kind'] == 'tseh-real-backend-owned-v1' and marker['root'] == str(root), 'Invalid ownership marker')
require(hashlib.sha256((root / 'source.tar').read_bytes()).hexdigest() == marker['archiveSha256'], 'Archive hash mismatch')
require(not (root / 'fixture.db').exists(), 'Never reuse an existing database')
require(Path.cwd().resolve() == root and (root / 'app/main.py').is_file(), 'Invalid isolated cwd/source')
os.environ.update(
    DATABASE_URL=f'sqlite:///{root}/fixture.db', SESSION_SECRET=marker['token'],
    OWNER_PASSWORD='isolated-owner-password', VOICE_LLM='off', SCAN_LLM='off',
    PHOTO_SALE='off', SCAN_DIR=str(root / 'scan'), STT_USAGE_LOG=str(root / 'stt.jsonl'),
    SCAN_USAGE_LOG=str(root / 'scan.jsonl'), VOICE_LLM_LOG=str(root / 'voice.jsonl'),
)
for key in ('LLM_API_KEY', 'LLM_API_KEY_2'):
    os.environ.pop(key, None)
sys.path.insert(0, str(root))


def guard(event, args):
    if event == 'sqlite3.connect':
        path = str(args[0])
        if path != ':memory:':
            require(Path(path).resolve().is_relative_to(root), 'SQLite outside fixture forbidden')
    if event == 'socket.connect':
        address = args[1]
        if isinstance(address, tuple):
            require(address[0] in ('127.0.0.1', '::1', 'localhost'), 'External network forbidden')
    if event == 'open' and isinstance(args[0], (str, bytes)):
        mode, flags = args[1:3]
        writing = isinstance(mode, str) and any(c in mode for c in 'wax+')
        writing = writing or (isinstance(flags, int) and flags & (os.O_WRONLY | os.O_RDWR | os.O_CREAT))
        if writing:
            require(Path(os.fsdecode(args[0])).resolve().is_relative_to(root), 'Write outside fixture forbidden')


sys.addaudithook(guard)
import app
require(Path(app.__file__).resolve().is_relative_to(root), 'Imported app outside archived fixture')
from app.main import app as application, startup
from app.core.db import engine, SessionLocal
require(Path(engine.url.database).resolve() == root / 'fixture.db', 'Wrong engine database')

# Test metadata only. Every business route/service remains the archived implementation.
@application.get('/_tseh_test_fixture', include_in_schema=False)
def fixture_attestation():
    return {'kind': marker['kind'], 'revision': marker['revision'],
            'tokenSha256': hashlib.sha256(marker['token'].encode()).hexdigest()}


@application.get('/_tseh_test_fixture/accounting', include_in_schema=False)
def fixture_accounting():
    """Read-only evidence from the owned SQLite DB; never substitutes a business response."""
    from sqlalchemy import text
    tables = ('inventory_movements', 'inventory_transfer_receipts', 'inventory_transfer_receipt_lines',
              'inventory_production_documents', 'inventory_loss_documents', 'inventory_loss_lines',
              'workshop_task_executions')
    with SessionLocal() as db:
        return {table: db.scalar(text(f'SELECT count(*) FROM {table}')) for table in tables}


startup()
from app.core.models import Supplier, Category, Product, User, Role
from app.core.auth import hash_password
from sqlalchemy import select
with SessionLocal() as db:
    db.add(User(login='ui-manager', password_hash=hash_password('isolated-manager-password'),
                role=db.scalar(select(Role).where(Role.name == 'manager')), is_active=True))
    supplier = Supplier(name='UI fixture supplier', code='ui-fixture')
    category = Category(name='UI fixture')
    db.add_all([supplier, category])
    db.flush()
    ids = {}
    for sku, name in [('material', 'Материал UI'), ('fixed', 'Изделие норма UI'), ('prototype', 'Изделие прототип UI')]:
        product = Product(supplier_id=supplier.id, category_id=category.id, supplier_sku=sku,
                          name=name, base_unit='шт', keep_in_stock=True, purchase_price=1, sale_price=2)
        db.add(product)
        db.flush()
        ids[sku] = product.id
    db.commit()
(root / 'products.json').write_text(json.dumps(ids))
import uvicorn
sock = socket.socket()
sock.bind(('127.0.0.1', 0))
sock.listen(128)
(root / 'port').write_text(str(sock.getsockname()[1]))
uvicorn.Server(uvicorn.Config(application, host='127.0.0.1', log_level='warning', access_log=False)).run(sockets=[sock])
