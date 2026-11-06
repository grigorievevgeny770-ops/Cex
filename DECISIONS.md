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

## 2026-11-02 — закрытие невыполненного остатка
- Реализуется отдельная worker-команда close-incomplete, а не start/assign/cancel или фиктивный складской факт. Разрешение определяет allowed_actions; приёмка исключена.
- Для доступа к закрытым заданиям добавлена серверная навигация Доступные / История заданий / Все. Это чтение архивных задач; ручное управление статусами не возвращается.
- Причина trim перед подтверждением, проверяется по Unicode code points (1..2000); подтверждённые байты неизменяемы. Новая причина/версия требует нового явного подтверждения после определённого отказа. Неопределённая команда блокирует замену.
- Capability OpenAPI проверяется только при закрытии в выбранном окружении, без автоматического перехода на удалённый сервер. Отрицательный результат можно перепроверить после rollout.

## 2026-11-02 — завершение приёмки из карточки
По запросу пользователя приёмку можно явно завершить отдельной кнопкой в карточке. Используется существующий финальный receive workflow; close-incomplete для RECEIVE запрещён сервером. Навигационный аргумент final только предвыбирает режим формы, не выполняет команду. Ранее принятый факт не повторяется; пустой новый факт допустим с причиной недостачи.

## 2026-11-03 — выбор сервера
URL на экране входа выбирается селектором: локальный Android Emulator, USB localhost, удалённый HTTPS147-45-154-211.sslip.io и другой адрес. Preset выбирается только явно; сохранённое окружение сохраняется. Пользовательский URL по-прежнему валидируется прежним ChangeEnvironmentUseCase. Смена окружения сбрасывает старую cookie-сессию и введённые credentials, pending остаются изолированы по server+actor. Выбор сервера сам не выполняет вход или складские операции.

Уточнение пользователя: удалённый https://147-45-154-211.sslip.io/ становится default для обычных debug/release при отсутствии сохранённой настройки. Это заменяет прежний локальный default, но не меняет уже сохранённый явный выбор и изоляцию processFixture/тестов. Автоматического fallback нет.

## 2026-11-04 — упрощение рабочего интерфейса
По прямому запросу пользователя основной UI оставляет товар, количество, единицы, размеры/листы и короткие рабочие подсказки. Технические ID/версии/document refs, авторы, SKU, дубли склада/аккаунта, длинные API/recipe пояснения и аудит действий скрываются. Короткая история фактов и закрытые задания остаются доступны; причина закрытия и комментарий-инструкция сохраняются. Для листов всегда показываются физические sheets и frozen length, даже при отображаемом base qty0.
Семантика и права не меняются: ready расход задаёт preview сервера, prototype вводится один раз, RECEIVE final shortage отдельно от close-incomplete. Подтверждения и блокировки pending остаются. Восстановление показывает понятный исход и состав действия без raw JSON/token/версий; journal хранится неизменно и повторяет прежние байты. Неопределённый исход не обозначается успехом. Изменения являются presentation only; сервер/DB не модифицируются.

## 2026-11-04 — без комментария и возврат после завершения
Необязательный комментарий удаляется из состояния/intent новых receive/dispatch/manufacture форм, а не только скрывается. Обязательные причины недостачи/закрытия, указания задания, исторические и pending комментарии сохраняются; новые команды используют пустой совместимый comment.
После сохранённого server ACK с current task.is_completed либо is_closed_incomplete интерфейс возвращается к списку и принудительно перечитывает его с текущим фильтром. Частичный факт оставляет форму открытой. Ошибка/unknown/ошибка сохранения ACK не даёт навигации; read карточки сам по себе тоже не закрывает экран истории. Навигация не подменяет durable результат и обрабатывает commandId один раз. Legacy ACK без признаков завершения не считается завершённым по догадке.

## 2026-11-06 — убрать отдельную проверку действий
По запросу пользователя удалён отдельный экран и все пользовательские переходы «Проверка действий». Незавершённый запрос остаётся в durable журнале; безопасный ручной повтор перенесён прямо в карточку соответствующего задания. Повтор доступен и для уже завершённого сервером задания с потерянным ответом, независимо от execute actions, но всегда под прежним actor/server через существующий dispatcher. Формы предлагают вернуться к заданию. Старые ACK/комментарии/байты запроса не удаляются и не переписываются. После terminal ACK сохраняется возврат к обновлённому списку.

## 2026-11-06 — обновление жестом и один возврат
Все явные кнопки «Обновить» в detail/receive/dispatch/manufacture/stock detail заменяются pull-to-refresh, списки сохраняют существующий жест. Общий Material3-контейнер поддерживает enabled без пересоздания списка. Обновление запускает только существующие read use cases, сохраняет ввод и блокируется во время write/retry/close/review; public VM guard проверяет актуальное состояние независимо от жеста.
Кнопки «К заданию» и обслуживающие только их callbacks удалены. Возврат выполняется тулбаром/системным «Назад» к уже существующей карточке; успешное терминальное ACK по-прежнему возвращает к обновлённому списку. Pending-повтор в карточке и исходные байты не меняются.

Постоянное уточнение пользователя 06.11.2026: запуск тестов только по явной просьбе. Это заменяет прежнее автоматическое выполнение тестов после этапов. Просьба собрать APK не разрешает тестовые задачи; правило продублировано в AGENTS.md. Последние138unit/17UI завершились до получения этого указания; после него новых запусков нет.

2026-11-06: по прямому запросу пользователя текущая обычная debug-сборка предзаполняет предоставленные учётные данные worker для указанного удалённого HTTPS-сервера (и разрешённых локальных root URL8000). Значения находятся только в игнорируемом local.properties и debug BuildConfig; в Git их нет. Release/processFixture остаются пустыми, автологин отсутствует. Тесты не запускались; только assembleDebug.

## 2026-11-06 — первая заявка без подмены контракта
Цех режет только плоский лист и выпускает доборные элементы из двух существующих каталогов: «Цех» / «Стандартные» и доборные/фасонные «Металл Профиль». Android не хранит id разделов и не заводит свой справочник. Готовое изделие на заявке — это `product_id`, который прислал сервер. Расход и остаток листа определяются только `is_sheet`. Статусы «Изготовлено» и «Передано на склад», приёмка листа отдельно от заявки, три раздела остатков и отмена движения не рисуются как готовые, пока backend не даст поля из `API_CONTRACT.md`. Cookie, idempotency, точные количества и защита от второго списания не меняются. Старые экраны приёмки, отправки, частичного выпуска и закрытия остатка из карточки заявки убраны; их HTTP-команды в data-слое сохранены.
