# Решения

2026-11-01:
- Проект остаётся в WorkshopManager; не смешивается с Python backend и LMRU.
- Один постоянный исполнитель; супервизор владеет контрактом, аудитом и коммитами.
- Мобильный пользователь worker; отсутствуют manager mutations и формы создания задач.
- Production v1 manual budget не используется. Production v2 проверен на fixtures, сверенных с появившейся локальной OpenAPI; живая write-приёмка остаётся отдельной.
- Точные BigDecimal, server truth, durable commands, явный retry без фонового replay обязательны.
- Все обычные обратимые решения выполняются без промежуточного approval.

## НУЖНО СОГЛАСОВАТЬ
- Удалённый backend v2 rollout; локальная OpenAPI v2 появилась во время этапа8. Клиентские fixtures сверены с актуальной локальной схемой.
- ЗАКРЫТО для локальной автоматической приёмки: собственный launcher создаёт изолированный настоящий backend и двух synthetic worker; обычный сервер8000 не используется для write-тестов.
- Серверная политика sheet batches/обрезков/брака; клиент не округляет расход эвристически.

## Повторобезопасность — этап 3
- Первый однозначный HTTP401/403/404/409/422 без предшествующего неизвестного исхода: immutable REJECTED diagnostic сохраняется, active slot освобождается.
- После timeout/500/process-death/локальной ошибки записи ACK последующие 4xx не доказывают отсутствие прежнего выполнения: NEEDS_REVIEW сохраняет slot, exact retry и безопасная проверка известного execution ID допустимы.
- Нет кнопки удаления неизвестного запроса с отправкой нового token. Без server evidence/by-token route (которого нет) ручная сверка менеджером является честным ограничением.

- По уточнению пользователя debug default — http://10.0.2.2:8000/ (локальный сервер компьютера через emulator alias). Release задаётся -PreleaseBaseUrl=https://.../; без настройки intentionally example.invalid. Сохранённый выбор DataStore приоритетнее default.

## НУЖНО СОГЛАСОВАТЬ — клиентские решения MVP
- Для предварительной проверки содержательности причины недостачи форма требует минимум 3 буквы/цифры; сервер остаётся окончательным валидатором. Максимум2000 взят из существующего OpenAPI.
- Release BASE_URL необходимо задать владельцу при сборке через -PreleaseBaseUrl=https://.../; placeholder example.invalid предотвращает случайное подключение к неподготовленному серверу.
- Уточнено read-only исходниками локального backend v2: производственный output имеет role="output" (production_planning.py и production_create.py). При отсутствии/неоднозначности output мобильный execution блокируется. Предыдущее допущение подтверждено источником.

- User explicitly requested local debug login prefill. Credentials live in ignored local.properties, are compiled only into ordinary debug APK, and do not enter Git. Local host aliases/port8000 only, no automatic login. This supersedes the initial prohibition on bundled credentials for this local debug convenience; release/processFixture remain empty.

- User simplified task scope: no own/queue or assigned-to-me selector. Always omit assignee_user_id, request all workshop tasks allowed to the actor by the server. Type/status filters remain.

- User simplified execution details: action audit is debug-only and collapsed by default, fetched on explicit expansion. Assignee/actor labels and task-line IDs are omitted even in debug. Server payload identifiers remain intact; operational facts and durable command recovery are not debug-only.

- User removed manual task lifecycle: direct physical execution, no claim/start/status filters or labels. Completion/cancellation flags and server allowed_actions remain internal safety facts, not user-managed statuses. Legacy status support exists only in data mapping during backend rollout; new task-state commands cannot be created.
- Closed on 2026-11-02: updated local backend contract is live (completion flags, no start endpoint). Cross-worker live write acceptance still requires a dedicated fixture backend. Android did not modify/deploy the backend.

- Current server query contract is strict: only view/type/warehouse_id/limit/offset. Removed compatibility active_only after authenticated422 reproduction; unsupported query parameters must not be sent for speculative compatibility.
