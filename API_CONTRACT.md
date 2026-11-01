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

## Production v2 — исходно проектируемый, теперь доступен в локальной OpenAPI
POST api/workshop/v2/tasks/{task_id}/manufacturing-preview и manufacturing-executions.
Descriptor schema_version2, mode fixed_recipe|prototype_required, recipe_status ready|awaiting_prototype, recipe_version, basis_output/materials.
Execution mode by_recipe: output(task_line_id,qty либо sheets), expected_version, expected_recipe_version, client_token, comment; materials запрещены.
Prototype: те же версии/output + materials(product_id,qty?,sheets?,sheet_length_m?,unit_name?,unit_qty?). Preview без token/comment. Норма закрепляется только server execution ACK; prototype входит в план.
Preview является advisory read-only server расчётом; клиент не вычисляет и не списывает норму самостоятельно.
Legacy missing descriptor/schema1 — неподдерживаемый production workflow.

## Durable writes
До сети Room хранит canonicalBaseUrl+actorId+taskId, exact method/path/UTF-8 body/token, состояние и ACK. Одна активная команда на profile/task, transactional guard + Mutex. Retry побайтно, без новых версий/token. Logout/401 не удаляет pending/ACK, другой actor их не видит. Никаких write-проверок общей БД.

## Повторная capability проверка во время этапа 8
Локальный сервер обновлён независимо от Android-разработки: OpenAPI HTTP200, 116 paths, присутствуют все три v2 production POST; snapshot /tmp/workshop-openapi-v2.json. Контракт Preview/Descriptor/ByRecipe/Prototype теперь подтверждается локальной схемой и source /Users/chulkova/PetProjects/sklad/code/app/workshop/production_router.py. Наличие routes не означает пройденные live write-сценарии. Android агент не перезапускал и не изменял backend/БД.
Серверное ТЗ обнаружено: /Users/chulkova/PetProjects/sklad/WORKSHOP_PRODUCTION_NORMS_SPEC.md. Роль frozen output подтверждена production_planning.py/production_create.py как output. Удалённое окружение требует отдельного повторного аудита перед передачей.

## Финальный read-only аудит
Локальный OpenAPI:116 paths, tasks присутствует,3v2 production POST. Удалённый OpenAPI:68 paths, tasks отсутствует, v2 отсутствует.22synthetic fixtures прошли jsonschema4.26.0 Draft202012Validator по свежему снимку /tmp/workshop-openapi-v2.json; Python3.12 isolatedvenv. Живые авторизованные write-проверки настоящего backend не запускались.

The mobile client no longer sends assignee_user_id (user-requested scope simplification). The backend parameter may remain supported for other clients; this app always uses the full permitted workshop scope.

## Direct-execution contract transition
The app no longer creates /start requests or sends status/assignee filters. New TaskRead completion flags is_completed/is_cancelled are supported; legacy status is optional transport compatibility only. Active queue requests use view=active plus active_only=true for the old server. Canonical manufacturing fixtures now use new flags, so current schema validation requires the corresponding updated runtime OpenAPI. The last live audit still advertises old status/start and assignment scope; no Android task restarted or deployed that backend. Historical start pending bodies are retained unchanged for safe recovery.

## 2026-11-02 — live contract and refresh fix
The local runtime is updated:136paths, TaskRead is_completed/is_cancelled, no /start endpoint. GETtasks accepts only view(active/history/all), type, warehouse_id, limit and offset. Its query model forbids unknown parameters: active_only=true produces422 VALIDATION_ERROR, loc=query.active_only, extra_forbidden. The app now sends view=active without active_only. Separate authenticated read confirmed corrected request200; no stock/task mutations.22canonical production fixtures passed jsonschema against the new live OpenAPI snapshot. Earlier pending-rollout notes above are historical and superseded for local API availability.

## Actual backend integration acceptance
2026-11-02: production HTTP/repositories and Compose verified against unmodified archived backend dc7e140 with its actual FastAPI/SQLite business stack. Owned fixture runtime only: receive4+5final/shortage1 by two workers, fixed6→3, prototype4→2 then6→3, finished-product dispatch3/in-transit. API and own DB postconditions passed; no business endpoint responses mocked. Shared server8000/DB were not modified. New runner metadata attestation is test-only and absent from business contract/normal APK.

## 2026-11-02 — close incomplete
Selected local OpenAPI advertises POST /api/workshop/tasks/{task_id}/close-incomplete (backend adf3759). Strict body: expected_version (current TaskRead), client_token (UUID), reason (nonblank, <=2000 Unicode code points); no other execution fields. The existing cookie/base URL and durable dispatcher are reused. Capability is checked lazily before close confirmation with an unauthenticated public OpenAPI GET on the same selected server (worker cookies on this public route are rejected by backend legacy ACL); business writes retain the existing authenticated cookie client. The session generation is guarded across this read and submit; positive capability expires after five minutes, absence is not cached. No cancel fallback.
TaskRead maps is_closed_incomplete, closed_incomplete_reason/by/at independently of completion/cancellation; closed tasks cannot execute, even with malformed allowed_actions. DISPATCH and MANUFACTURE can close only with close_incomplete action, including unsupported/awaiting-prototype production. RECEIVE cannot. Server remaining quantities are retained. Lists query view=active/history/all; historical receipt versions never replace task.version. Unknown outcomes preserve exact body/key/server/actor across Room reopen and manual retry.
