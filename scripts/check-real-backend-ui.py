#!/usr/bin/env python3
"""Opt-in Compose E2E against actual FastAPI and a fresh isolated SQLite database.
Never accepts a backend URL; never touches the working server or its database.
"""
import argparse, hashlib, json, os, secrets, subprocess, sys, tarfile, tempfile, time, urllib.request, http.cookiejar
from pathlib import Path
sys.dont_write_bytecode=True
if sys.version_info < (3,12):
    raise SystemExit('Python 3.12+ required; use /opt/homebrew/bin/python3.12')
if not __debug__:
    raise SystemExit('Optimized Python disables safety assertions; run without -O')
p=argparse.ArgumentParser();p.add_argument('--backend',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--serial',default='emulator-5554');p.add_argument('--scenario',choices=('baseline','close'),default='baseline');a=p.parse_args()
if not a.serial.startswith('emulator-'):
    raise SystemExit('Dedicated emulator required')
repo=Path(__file__).resolve().parent.parent; source=a.backend.resolve(); root=a.output.resolve()
if not any(root.is_relative_to(folder.resolve()) for folder in (Path(tempfile.gettempdir()),Path('/tmp'))):
    raise SystemExit('Fixture output must be in the OS temporary directory')
if root.exists():
    raise SystemExit('Use a fresh owned output directory')
root.mkdir(parents=True); token=secrets.token_hex(32)
revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=source,text=True).strip()
manifest={'kind':'tseh-real-backend-owned-v1','root':str(root),'revision':revision,'token':token}
(root/'fixture-manifest.json').write_text(json.dumps(manifest));os.chmod(root/'fixture-manifest.json',0o600)
archive=root/'source.tar'
with archive.open('wb') as f: subprocess.run(['git','archive',revision+':code/app'],cwd=source,stdout=f,check=True)
manifest['archiveSha256']=hashlib.sha256(archive.read_bytes()).hexdigest()
(root/'fixture-manifest.json').write_text(json.dumps(manifest))
(root/'app').mkdir()
with tarfile.open(archive) as t: t.extractall(root/'app',filter='data')
python=source/'code/.venv/bin/python';log=(root/'server.log').open('w')
server_env={key:value for key,value in os.environ.items() if key not in ('PYTHONPATH','PYTHONHOME','PYTHONOPTIMIZE')}
server_env.update(PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1')
server=subprocess.Popen([str(python),str(repo/'scripts/real_backend_fixture_server.py'),str(root)],cwd=root,stdout=log,stderr=subprocess.STDOUT,env=server_env)
def client(): return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def request(c,path,body=None):
    req=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json'})
    with c.open(req,timeout=20) as r:return json.load(r)
def wait(predicate):
    end=time.monotonic()+60
    while time.monotonic()<end:
        if server.poll() is not None: raise AssertionError('Fixture server exited; inspect server.log')
        try:
            result=predicate()
            if result:return result
        except (OSError,ValueError):pass
        time.sleep(.15)
    raise AssertionError('Fixture startup timed out')
adb=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Library/Android/sdk')))/'platform-tools/adb'
def device(*args):return subprocess.run([str(adb),'-s',a.serial,*args],check=True,capture_output=True,text=True).stdout
try:
    wait(lambda:(root/'port').exists());port=int((root/'port').read_text());
    if port==8000: raise RuntimeError('Working server port forbidden')
    base=f'http://127.0.0.1:{port}';owner=client();wait(lambda:request(owner,'/health'))
    attestation=request(owner,'/_tseh_test_fixture')
    if attestation!={'kind':manifest['kind'],'revision':revision,'tokenSha256':hashlib.sha256(token.encode()).hexdigest()}:
        raise RuntimeError('Fixture identity mismatch before seeding')
    request(owner,'/auth/login',{'login':'admin','password':'isolated-owner-password'})
    workers=[]
    for n in (1,2):workers.append(request(owner,'/api/workers',{'login':f'ui-worker-{n}','password':'isolated-worker-password','full_name':f'UI worker {n}','client_token':f'worker-{n}'}))
    warehouses=request(owner,'/api/warehouses');main=next(w['id'] for w in warehouses if w['code']=='main');workshop=next(w['id'] for w in warehouses if w['code']=='workshop')
    products=json.loads((root/'products.json').read_text())
    def receipt(wid,qty,key):
        r=request(owner,'/api/receipts',{'warehouse_id':wid,'client_token':key,'lines':[{'product_id':products['material'],'qty':str(qty),'price':'1'}]})
        request(owner,f"/api/receipts/{r['id']}/post",{'expected_version':1,'client_token':key+'-post'})
    receipt(main,10,'main-stock');receipt(workshop,50,'workshop-stock')
    tr=request(owner,'/api/transfers',{'source_warehouse_id':main,'destination_warehouse_id':workshop,'client_token':'send-plan','lines':[{'product_id':products['material'],'qty':'10'}]})
    request(owner,f"/api/transfers/{tr['id']}/dispatch",{'expected_version':1,'client_token':'send'})
    receive=request(owner,'/api/workshop/tasks?view=active&type=RECEIVE_GOODS&warehouse_id='+str(workshop))['items'][0]
    fixed=request(owner,'/api/workshop/v2/manufacturing-tasks',{'mode':'fixed_recipe','client_token':'fixed-plan','output':{'product_id':products['fixed'],'qty':'10'},'materials':[{'product_id':products['material'],'qty':'20'}]})['task']
    proto=request(owner,'/api/workshop/v2/manufacturing-tasks',{'mode':'prototype_required','client_token':'proto-plan','output':{'product_id':products['prototype'],'qty':'10'}})['task']
    dispatch=request(owner,'/api/workshop/tasks',{'type':'DISPATCH_GOODS','client_token':'dispatch-plan','lines':[{'product_id':products['material'] if a.scenario=='close' else products['fixed'],'qty':'20' if a.scenario=='close' else '3'}]})['task']
    scenario={'receive':receive,'fixed':fixed,'prototype':proto,'dispatch':dispatch,'materialId':products['material']}
    if a.scenario=='close':
        scenario['dispatchZero']=request(owner,'/api/workshop/tasks',{'type':'DISPATCH_GOODS','client_token':'dispatch-zero-plan','lines':[{'product_id':products['material'],'qty':'20'}]})['task']
        scenario['prototypeZero']=request(owner,'/api/workshop/v2/manufacturing-tasks',{'mode':'prototype_required','client_token':'prototype-zero-plan','output':{'product_id':products['prototype'],'qty':'10'}})['task']
        scenario['workshopId']=workshop
    (root/'scenario.json').write_text(json.dumps(scenario,ensure_ascii=False))
    with (root/'build.log').open('w') as f:subprocess.run([str(repo/'gradlew'),':app:assembleDebug',':app:assembleDebugAndroidTest'],cwd=repo,stdout=f,stderr=subprocess.STDOUT,check=True)
    device('install','-r',str(repo/'app/build/outputs/apk/debug/app-debug.apk'));device('install','-r',str(repo/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
    encoded=__import__('base64').b64encode(json.dumps(scenario).encode()).decode()
    test_class='RealCloseIncompleteConnectedTest' if a.scenario=='close' else 'RealBackendConnectedTest'
    output=device('shell','am','instrument','-w','-e','class','com.logunovinc.workshopmanager.testing.'+test_class,'-e','realBackendUrl',f'http://10.0.2.2:{port}/','-e','realBackendRevision',revision,'-e','realBackendMarker',token,'-e','realBackendScenario',encoded,'com.logunovinc.workshopmanager.test/com.logunovinc.workshopmanager.testing.WorkshopTestRunner')
    (root/'instrumentation.log').write_text(output)
    subprocess.run([str(adb),'-s',a.serial,'pull','/sdcard/Android/data/com.logunovinc.workshopmanager/files/real-backend-evidence',str(root/'screenshots')],capture_output=True)
    assert 'OK (1 test)' in output, output
    if a.scenario=='close':
        from real_backend_close_checks import verify_close
        result=verify_close(root,scenario,products,workers,owner,request,workshop,revision)
    else:
        tasks={key:request(owner,f"/api/workshop/tasks/{scenario[key]['id']}") for key in ('receive','fixed','prototype','dispatch')}
        assert tasks['receive']['is_completed'] and tasks['receive']['outcome']=='shortage'
        assert tasks['prototype']['production']['recipe_status']=='ready'
        assert all('assignee_user_id' not in t and 'status' not in t for t in tasks.values())
        executions={key:request(owner,f"/api/workshop/tasks/{task['id']}/executions")['items'] for key,task in tasks.items()}
        assert {e['executed_by'] for e in executions['receive']}=={w['user_id'] for w in workers}
        counts={key:request(owner,f"/api/workshop/tasks/{task['id']}/executions")['total'] for key,task in tasks.items()}
        assert counts=={'receive':2,'fixed':1,'prototype':2,'dispatch':1},counts
        from decimal import Decimal as D
        # Assert each server fact independently; aggregate stock alone could hide compensating errors.
        proofs = {}
        def fact(key, index, expected):
            execution = sorted(executions[key], key=lambda item: item['id'])[index]
            actual = sorted((line['role'], line['product_id'], D(line['actual_qty'])) for line in execution['lines'])
            wanted = sorted((role, product_id, D(qty)) for role, product_id, qty in expected)
            assert actual == wanted, (key, index, actual, wanted)
            proofs.setdefault(key, []).append({
                'executionId': execution['id'], 'actorId': execution['executed_by'],
                'lines': [{'role': role, 'productId': product_id, 'qty': str(qty)} for role, product_id, qty in actual],
            })
            return execution
        first_receive = fact('receive', 0, [('input', products['material'], '4')])
        final_receive = fact('receive', 1, [('input', products['material'], '5')])
        assert first_receive['executed_by'] == workers[0]['user_id']
        assert final_receive['executed_by'] == workers[1]['user_id']
        fact('fixed', 0, [('input', products['material'], '6'), ('output', products['fixed'], '3')])
        first_prototype = fact('prototype', 0, [('input', products['material'], '4'), ('output', products['prototype'], '2')])
        fact('prototype', 1, [('input', products['material'], '6'), ('output', products['prototype'], '3')])
        fact('dispatch', 0, [('input', products['fixed'], '3')])
        recipe = tasks['prototype']['production']
        assert D(recipe['basis_output']['qty']) == D('2')
        assert len(recipe['materials']) == 1
        assert recipe['materials'][0]['product_id'] == products['material']
        assert D(recipe['materials'][0]['qty']) == D('4')
        assert recipe['learned_from_execution_id'] == first_prototype['id']
        recipe_proof = {'basisOutputQty': recipe['basis_output']['qty'],
                        'materialProductId': recipe['materials'][0]['product_id'],
                        'materialQty': recipe['materials'][0]['qty'],
                        'learnedFromExecutionId': recipe['learned_from_execution_id']}
        stock=request(owner,f'/api/warehouses/{workshop}/stock')['items'];quantities={r['product_id']:r['qty'] for r in stock}
        from decimal import Decimal as D
        assert D(quantities[products['material']])==43,quantities # 50+9-6-10
        assert D(quantities[products['fixed']])==0 and D(quantities[products['prototype']])==5
        mainstock=request(owner,f'/api/warehouses/{main}/stock')['items'];assert all(D(r['qty'])==0 for r in mainstock)
        import sqlite3
        with sqlite3.connect(f'file:{root / "fixture.db"}?mode=ro',uri=True) as db:
            assert db.execute('SELECT count(*) FROM inventory_production_documents').fetchone()[0]==3
            assert db.execute("SELECT count(*) FROM inventory_movements WHERE kind LIKE '%loss%'").fetchone()[0]==0
            discrepancy=db.execute('SELECT missing_qty,resolved_by FROM inventory_transfer_discrepancies').fetchall()
            assert discrepancy==[(1,None)],discrepancy
            assert db.execute('SELECT count(*) FROM workshop_task_executions').fetchone()[0]==6
            assert db.execute('SELECT count(*) FROM workshop_tasks WHERE assigned_to IS NOT NULL').fetchone()[0]==0
            shipped=executions['dispatch'][0]['transfer_id']
            transfer=request(owner,f'/api/transfers/{shipped}')
            (root/'dispatch-transfer.json').write_text(json.dumps(transfer,indent=2))
            assert transfer['document']['status']=='in_transit'
            assert all(line['product_id']==products['fixed'] for line in transfer['lines'])
            assert sum(D(b['in_transit']) for b in transfer['balances'])==3
            assert all(D(b['accepted'])==0 and D(b['confirmed_loss'])==0 for b in transfer['balances'])
            inbound=request(owner,f"/api/transfers/{tasks['receive']['transfer_id']}")
            assert sum(D(b['unresolved_missing']) for b in inbound['balances'])==1
            assert all(D(b['confirmed_loss'])==0 for b in inbound['balances'])
            movements=db.execute('SELECT kind,qty FROM inventory_movements WHERE transfer_line_id IN (SELECT id FROM inventory_transfer_lines WHERE transfer_id=?)',(shipped,)).fetchall()
            assert any(kind=='transfer_dispatch' and D(str(qty))==-3 for kind,qty in movements),movements
            assert not any(kind=='transfer_receive' for kind,qty in movements),movements
        result={'status':'PASS','backendRevision':revision,'executionCounts':counts,'executionProofs':proofs,'prototypeRecipeProof':recipe_proof,'workshopQuantities':quantities,'receiveOutcome':'shortage','sameTaskDifferentWorkers':True,'backend':'actual FastAPI/SQLite, no mocked responses'}
    (root/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
finally:
    server.terminate()
    try:server.wait(timeout=15)
    except subprocess.TimeoutExpired:server.kill();server.wait()
    log.close()
