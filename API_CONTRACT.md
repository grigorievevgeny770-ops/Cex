# API контракты Цех

## Read-only capability audit — 2026-11-01
Локальный http://127.0.0.1:8000/openapi.json: HTTP200, info.version=2.0.0, 113 paths, /api/workshop/tasks присутствует. Production v2 routes отсутствуют.
Удалённый https://147-45-154-211.sslip.io/openapi.json: HTTP200, та же info.version=2.0.0, 67 paths, /api/workshop/tasks отсутствует; v2 отсутствует. info.version не доказывает совместимость.

## Существующие v1
- POST auth/login {login,password}, GET auth/me, POST auth/logout; persistent session CookieJar; login без id, затем me.
- GET api/warehouses — массив; workshop выбирается строго по code=workshop и активности.
- GET api/warehouses/{id}/stock и /stock/{product_id}; GET api/products и /{id}/stock-definition.
- GET api/workshop/tasks, GET /{task_id}; POST /{task_id}/start.
- POST /{task_id}/executions только RECEIVE_GOODS и DISPATCH_GOODS.
- GET /{task_id}/executions, /executions/{execution_id}, /history — серверная пагинация.
- Все successful writes HTTP200 {result,task}; task.version/transfer_version — актуальная истина.
- Receive отдельный DTO: expected_version, expected_transfer_version, client_token, comment, lines(task_line_id,qty либо sheets), final, reason. Значение — факт шага; zero-final допустим с reason.
- Dispatch отдельный DTO: type, expected_version, client_token, comment, lines. Без final/reason/transfer_version.
- Decimal JSON string, Long IDs/versions, integer sheets; неизвестные enum запрещают mutations.
- Errors native {code,message,details}, legacy auth {detail}; 401 сохраняет pending, 409 требует refresh, network/500 оставляют unknown.

## Проектируемые production v2 — НЕ существующий API
POST api/workshop/v2/tasks/{task_id}/manufacturing-preview и manufacturing-executions.
Descriptor schema_version2, mode fixed_recipe|prototype_required, recipe_status ready|awaiting_prototype, recipe_version, basis_output/materials.
Execution mode by_recipe: output(task_line_id,qty либо sheets), expected_version, expected_recipe_version, client_token, comment; materials запрещены.
Prototype: те же версии/output + materials(product_id,qty?,sheets?,sheet_length_m?,unit_name?,unit_qty?). Preview без token/comment. Норма закрепляется только server execution ACK; prototype входит в план.
Preview является advisory read-only server расчётом; клиент не вычисляет и не списывает норму самостоятельно.
Legacy missing descriptor/schema1 — неподдерживаемый production workflow.

## Durable writes
До сети Room хранит canonicalBaseUrl+actorId+taskId, exact method/path/UTF-8 body/token, состояние и ACK. Одна активная команда на profile/task, transactional guard + Mutex. Retry побайтно, без новых версий/token. Logout/401 не удаляет pending/ACK, другой actor их не видит. Никаких write-проверок общей БД.
