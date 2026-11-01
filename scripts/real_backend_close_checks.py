"""Assertions for owned real-backend close-incomplete UI runs (no network entry point)."""
import json
import sqlite3
from decimal import Decimal as D


def verify_close(root, scenario, products, workers, owner, request, workshop, revision):
    keys=('dispatch','dispatchZero','fixed','prototypeZero','prototype')
    expected_actual={'dispatch':'10','dispatchZero':'0','fixed':'3','prototypeZero':'0','prototype':'2'}
    expected_remaining={'dispatch':'10','dispatchZero':'20','fixed':'7','prototypeZero':'10','prototype':'8'}
    expected_counts={'dispatch':1,'dispatchZero':0,'fixed':1,'prototypeZero':0,'prototype':1}
    closed={}
    for key in keys:
        task=request(owner,f"/api/workshop/tasks/{scenario[key]['id']}")
        assert task['is_closed_incomplete'] and not task['is_completed'] and not task['is_cancelled'],task
        assert task['closed_incomplete_reason']==f'Закрытие UI {key}'
        assert task['closed_incomplete_by']==workers[0]['user_id'] and task['closed_incomplete_at']
        assert 'execute' not in task['allowed_actions'] and 'close_incomplete' not in task['allowed_actions']
        output=next(line for line in task['lines'] if line['role']=='output') if task['type']=='MANUFACTURE_PRODUCT' else task['lines'][0]
        assert D(output['planned_qty'])==D('20' if key.startswith('dispatch') else '10')
        assert D(output['actual_qty'])==D(expected_actual[key])
        assert D(output['remaining_qty'])==D(expected_remaining[key])
        facts=request(owner,f"/api/workshop/tasks/{task['id']}/executions")
        assert facts['total']==expected_counts[key]
        closed[key]={'taskId':task['id'],'actual':output['actual_qty'],'remaining':output['remaining_qty'],
                     'reason':task['closed_incomplete_reason'],'at':task['closed_incomplete_at'],'executions':facts['total']}
    active=request(owner,f'/api/workshop/tasks?warehouse_id={workshop}&view=active')['items']
    history=request(owner,f'/api/workshop/tasks?warehouse_id={workshop}&view=history')['items']
    ids={item['taskId'] for item in closed.values()}
    assert not ids & {t['id'] for t in active}
    assert ids <= {t['id'] for t in history}
    tasks={key:request(owner,f"/api/workshop/tasks/{scenario[key]['id']}") for key in ('fixed','prototype','prototypeZero')}
    assert tasks['prototypeZero']['production']['recipe_status']=='awaiting_prototype'
    recipe=tasks['prototype']['production']
    assert recipe['recipe_status']=='ready' and D(recipe['basis_output']['qty'])==2 and D(recipe['materials'][0]['qty'])==4
    stock=request(owner,f'/api/warehouses/{workshop}/stock')['items']
    quantities={row['product_id']:row['qty'] for row in stock}
    assert D(quantities[products['material']])==30 and D(quantities[products['fixed']])==3 and D(quantities[products['prototype']])==2
    proofs=json.loads((root/'screenshots/close-accounting.json').read_text())
    assert set(proofs)==set(keys)
    for key,proof in proofs.items():
        assert proof['before']==proof['after'],(key,proof)
    with sqlite3.connect(f'file:{root / "fixture.db"}?mode=ro',uri=True) as db:
        assert db.execute('SELECT count(*) FROM workshop_task_executions').fetchone()[0]==3
        assert db.execute('SELECT count(*) FROM inventory_production_documents').fetchone()[0]==2
        assert db.execute('SELECT count(*) FROM inventory_loss_documents').fetchone()[0]==0
        assert db.execute('SELECT count(*) FROM inventory_transfer_receipts').fetchone()[0]==0
    return {'status':'PASS','scenario':'close','backendRevision':revision,'closedTasks':closed,
            'closeAccountingProofs':proofs,'workshopQuantities':quantities,'backend':'actual FastAPI/SQLite; unchanged accounting for every close'}
