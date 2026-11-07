> Актуальные добавления заявок и чертежей: [PRODUCTION_REQUESTS.md](PRODUCTION_REQUESTS.md), [DRAWINGS_V11.md](DRAWINGS_V11.md). Разделы ниже описывают также прежние контракты; ранние списки отсутствующих возможностей не относятся к новым API заявок.

# API контракты Цех

Актуальный контракт заявок и ограничения новой реализации: [PRODUCTION_REQUESTS.md](PRODUCTION_REQUESTS.md). Исторические записи ниже не заменяют этот контракт; серверная реализация теперь подготовлена отдельно в sklad.

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

## Receive finalization entry (2026-11-02)
RECEIVE remains excluded from close-incomplete. The detail finalization button opens the existing receipt form with final=true; only an explicit review/confirmation submits the existing executions payload with expected_version, expected_transfer_version, client_token, type=RECEIVE_GOODS, lines, final=true, reason, comment. Empty lines mean zero newly received, not removal of earlier receipts. Final shortage is server-completed receipt work, not loss approval or is_closed_incomplete.

## 2026-11-04 — presentation simplification
Normal UI no longer renders transport IDs/versions/documents or raw journal labels. This does not remove or rewrite any task/recipe version, command token, payload, actor/server isolation, ACK or quantities in storage/HTTP. UI quantities/sheet dimensions and server preview shortages remain authoritative. Completed/closed/shortage outcomes remain distinct. No new server routes or inventory semantics are introduced.

## Completion navigation after durable ACK
CommandConfirmation exposes a presentation-independent terminal flag derived only from the persisted ACK's current task.is_completed/is_closed_incomplete (legacy status=completed only when both new completion flags are absent). Historical result flags, remaining quantities, preview and GET alone never manufacture success. ACK schema fields are read-only additions; exact saved request method/body/token/profile and Room schema do not change. An ACK save failure remains unresolved and cannot navigate. New forms submit an empty compatible comment; saved older commands retain their original comments and bytes.

2026-11-06: removal of global recovery UI changes only navigation/presentation. Manual retry by saved command ID now lives on task detail; Task.canExecute is not required for this read/replay of the already confirmed intent. Raw bytes/token/server/actor, acknowledgement persistence, terminal navigation and shared dispatcher mutex remain unchanged.

## 2026-11-06 — клиент заявок, если сервер объявил маршруты

Android вызывает заявки, остатки и лист только когда публичный `openapi.json` выбранного сервера содержит ключи `/api/workshop/requests`, `/api/workshop/stock-summary` и `/api/workshop/sheet-requests`. Иначе остаётся прежний список заданий, приёмка, отправка, производство и склад. Отсутствие маршрута не кэшируется. Команды идут через прежний durable dispatcher: `expected_version` или создание листа, `client_token`, точное тело, без исполнителя и без частичного количества изделий.

`norm_known=true` — тело manufacture без `materials`, норму считает сервер. `norm_known=false` — клиент отправляет фактические листы (`product_id`, `sheets`, `sheet_length_m`) и не подставляет норму. `null` блокирует изготовление. Отмена — `POST .../reverse` с `target=manufacture|hand_over`. Приёмка листа доступна только в статусе `confirmed` и с действием `receive`. Подтверждение и отгрузку делает менеджер не в этом приложении.

Репозиторий `github.com/grigorievevgeny770-ops/sklad` из этого окружения отвечает 404, поэтому серверная реализация в sklad сюда не вошла. Ниже остаётся требуемый контракт.

## 2026-11-06 — заявки цеха, требуемый контракт

Согласованная схема (заявка на изделия, общие статусы без исполнителя, цех режет только плоский лист и выпускает доборные элементы) сопоставлена с этим контрактом. Android не создаёт заявку, не пишет статус и не хранит id каталогов.

Что уже есть и используется:
- Список: `GET /api/workshop/tasks?warehouse_id&view=active|history&type=MANUFACTURE_PRODUCT&limit&offset`. Неизвестные query-параметры по-прежнему не отправляются.
- Строка изделия: `lines[]` с `role=output`, `product_id`, `product_name`, `planned_qty`, `base_unit`, опционально `sheet_length_m`. Отдельного поля «размер» нет.
- Дата и комментарий: `due_at`, `comment`.
- Отмена менеджера читается как `is_cancelled` (или legacy `status=cancelled`, только если новых флагов нет). История запрашивается `view=history`.
- «Новая» / «В работе» — только подпись к уже сохранённому факту: нет `started_at`, нет legacy `in_progress|partially_completed|waiting_resolution` и нет ненулевого `actual_qty`/`actual_sheets` → «Новая»; иначе при открытых флагах → «В работе». `started_at` и legacy `status` читаются и никуда не отправляются.
- Плоский лист в остатках: `GET /api/warehouses/{id}/stock` плюс `GET /api/products/{id}`. В раздел попадает только `is_sheet=true`. Ошибка чтения товара не превращает позицию ни в лист, ни в готовую продукцию.
- Расход в старой форме прототипа предлагает только товар с `is_sheet=true` и известной длиной. Норму клиент не считает: preview/execution v2 не менялись.

Чего в контракте нет, поэтому в интерфейсе нет кнопок и нет локальной имитации:
- Общий статус заявки `new | in_progress | manufactured | handed_to_warehouse | cancelled` и команды смены. `POST /start` снят. `is_completed` не означает ни «Изготовлено», ни «Передано на склад».
- Заявка из нескольких изделий одной командой. Текущий manufacture execution требует одну строку `role=output`.
- Отмена выпуска или передачи с обратными движениями и сохранённой историей.
- Остаток готовой продукции, ожидающей передачи, и переданное за день.
- Серверные списки разрешённых товаров. `Product` содержит `id`, `name`, `base_unit`, `is_sheet`, `sku` и не содержит раздел каталога.

Блокирующая работа backend, без копирования sklad2 в этот репозиторий. Id ниже — ориентир с TEST/PROD, не константы клиента:

1. Два существующих каталога готовых доборных элементов, без нового справочника и без дублей. Сервер возвращает уже существующие `product_id`:
   - «Цех» → «Стандартные»;
   - «Металл Профиль»: стандартные элементы отделки и фасонные изделия. Фасонные не исключать, пока это отдельно не согласовано.
2. Сырьё — только плоский лист существующего каталога («Металл Профиль» → «Листы плоские»). Признак в текущем API — `is_sheet=true`. Если лист в каталоге им не помечен, Android его не покажет.
3. `GET /api/workshop/requests?view=active|history` с полями `id`, `version`, `status`, `due_at`, `comment`, `lines[]{catalog_product_id, product_name, size, quantity, unit}`. `catalog_product_id` ссылается на товар из пункта 1, а не на копию.
4. `POST /api/workshop/requests/{id}/start`, `/manufacture`, `/hand-over`. Тело как у существующих команд: `expected_version`, `client_token`; без `assignee_user_id`, без частичного количества и без причины недостачи. `/manufacture` целиком по заявке; известную норму списывает сервер, неизвестную принимает как фактический расход плоского листа и может сохранить её позже. Ответ `{result, request}` с новой `version`.
5. Отмена заявки остаётся в sklad2. `view=history` должен возвращать отменённые.
6. `POST /api/workshop/requests/{id}/reverse` с `expected_version`, `client_token` и целью `manufacture|hand_over`: компенсирующие движения, история не удаляется.
7. `GET /api/workshop/stock-summary?on=YYYY-MM-DD`: `flat_sheet[]`, `awaiting_transfer[]`, `transferred_on[]` с товаром, количеством и, для листа, длиной и числом листов. Пока этого маршрута нет, клиент заполняет только плоский лист из пунктов выше.
