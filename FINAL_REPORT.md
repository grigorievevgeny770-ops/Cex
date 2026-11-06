# Цех — итоговая передача

Готов worker-клиент: авторизация, задания с прямым выполнением, приёмка, отправка, производство по норме/прототипу, история, recovery и остатки. Семь модулей, MVI/UDF, durable Room dispatcher.

## APK и команды

`app/build/outputs/apk/debug/app-debug.apk` — 14 783 773 байт.

SHA256: `2be87a529a7cb52180190a0599f87267ecfa4bd1be60bcd38bd62acb08e3bde7`.

APK установлен и запущен на emulator-5554/API37. Debug/release default: `https://147-45-154-211.sslip.io/` (saved environment takes precedence). SDK/JDK и настройка окружений — в README.md.

```sh
./scripts/verify.sh
CONNECTED_TESTS=1 ./scripts/verify.sh
python3 scripts/check-device-process-recovery.py --serial emulator-5554 --output /tmp/tseh-new-unique-run
```

Connected tests запускать на выделенном эмуляторе: AGP устанавливает/снимает target APK. После проверок обычный debug APK переустановить. Process test использует только отдельный пакет `.processfixture` и loopback synthetic backend.

## Независимо проверено

- 135 JVM-тестов, 11 обычных device-тестов и 2 real-backend UI E2E: 0 failures/errors/skips; lint, assembleDebug и архитектура успешны.
- Compose: login → receive → confirm → history → server stock refresh; prototype 2/4 → готовая норма → следующий выпуск 3/6 без materials. Recreate не повторяет START/EXECUTE.
- Реальный Android Keystore и Room reopen.
- Настоящий force-stop/relaunch: PID12852 → 13203, один физический факт, прежние body/token, повтор только по нажатию.
- Все 22 canonical production fixtures с completion flags прошли jsonschema по обновлённой локальной OpenAPI 02.11.2026. Совместимость с legacy status покрыта contract-тестами.
- Визуально проверены safe insets, скрытие клавиатуры, диалог прототипа и recovery.

Матрица: TEST_MATRIX.md. Логи и evidence: STAGE_LOG.md.

## Ограничения и открытые решения

- Реальные операции проверены на изолированном FastAPI/SQLite из backend dc7e140: два worker, приёмка/норма/прототип/отправка и сверка API/БД. Общая БД и обычный сервер8000 не менялись. Live negative/race/fault сценарии этим smoke-набором покрыты не полностью; они остаются в unit/contract-тестах.
- Локальный backend обновился во время разработки: 116 paths/3 production v2 POST. Удалённый: 68 paths, workshop/v2 отсутствуют. Deployment не выполнялся.
- Device-проверки — API37; отдельного device-прогона API26 не было.
- НУЖНО СОГЛАСОВАТЬ: порог 3 буквы/цифры для причины недостачи, release HTTPS BASE_URL, серверную политику обрезков/брака/листовых партий. См. DECISIONS.md.
- Release без настройки адреса использует example.invalid. Offline-first и фоновый replay исключены контрактом MVP.

## Локальные коммиты

| Commit | Блок |
|---|---|
| c7b98c1 | Аудит и контракт |
| 0ac622f | Модули/Compose/DI/build |
| 9d73b3f | Авторизация и cookie |
| 68ec8c5 | Локальный debug URL |
| 72b9026 | Durable commands/retry |
| 6026662 | Задания/start/history/recovery |
| f772f39 | Приёмка |
| 31605b3 | Отправка |
| e9bf863 | Остатки |
| deb4ec1 | Производство v2 |
| Финальный HEAD (`git log -1`) | Device/E2E/process recovery/UI fixes/передача |

Push, публикация APK и server deployment не выполнялись.

Debug-prefill enabled by the user for local port8000 only. Values come from ignored local.properties and are included in ordinary debug APK only; no auto-login. Release/processFixture defaults stay empty. Prefill regression tests and test-APK compilation passed; emulator fields verified without submitting login.

Task assignee filters removed at user request. Requests always omit assignee_user_id; the server determines the available workshop scope. Type/status filters and server pagination remain. Independent verify passed105tests/lint/build; updated APK installed and launched.

Action audit is now debug-only, collapsed by default and loaded only when expanded. Assignee/actor labels and task-line identifiers are removed from task detail and execution display, including debug. Operational facts and command recovery remain available. Independent107tests/lint/APK/test-APK build passed.

Manual task lifecycle removed: no TaskStatus/domain assignment/start producer, no status filters/labels/start controls. Direct execution uses server permissions and completion flags, with legacy transport compatibility. Independent110JVM tests,4device tests,lint/build/test-APK passed. The updated local runtime is now confirmed by OpenAPI (136 paths, no start endpoint); cross-worker live write scenarios remain untested.

Warehouse list/detail no longer display reserved quantities, reserved sheets or unallocated reserved amounts. Physical/free server values remain; data contracts and server reserve accounting are unchanged. Independent110tests/lint/build passed; normal debug APK updated on emulator.

Refresh validation fixed: removed unsupported active_only query. Authenticated read reproduced old request422(query.active_only extra_forbidden) and corrected request200. Isolated diagnostic auth session closed; no inventory/task mutations. Emulator refresh gesture left the task screen free of the validation error. Independent110tests/lint/build and22fixture-schema checks passed.

## Настоящий backend без MockWebServer

Запуск из корня Android-проекта:

```sh
/opt/homebrew/bin/python3.12 scripts/check-real-backend-ui.py \
  --backend /Users/chulkova/PetProjects/sklad \
  --serial emulator-5554 \
  --output /tmp/tseh-real-ui-$(date +%s)
```

Независимый отчёт: `/tmp/workshop-real-backend-final-review/result.json`.
Backend revision: `dc7e140afb4d44e3fdb939ed4ae1d7d6d2af05b1`.
Реальные worker UI-команды: приёмка4 + ещё5 final (недостача1, разные worker),
fixed расход6/выпуск3, prototype расход4/выпуск2 + расход6/выпуск3,
dispatch3 изготовленных изделий. Сохранена норма4на2, learned execution4.
Всего6фактов/3производственных документа; материал43, fixed0, prototype5;
отправка3 в пути, принято0, потери0, assigned_to NULL.

Каждый запуск архивирует неизменённый backend HEAD в новый temp-каталог,
создаёт SQLite/аккаунты/данные только там и останавливает свой uvicorn после
проверки. Launcher не принимает URL существующего сервера. Read-only
attestation привязывает UI к owned marker/revision до входа. Обычный набор
из4device-тестов не запускает live-тест неявно и не пропускает его через skip.

## Закрытие без полного выполнения

Добавлены worker-действие close_incomplete, диалог с обязательной причиной, immutable durable POST и серверная история закрытых задач. Приёмка исключена. Closed metadata/UTC и реальные remaining сохраняются; закрытие не считается полным выполнением. Timeout/restart сохраняет UUID/body/profile; idempotency conflict удерживает блокировку для сверки. Локальная default URL остаётся http://10.0.2.2:8000/, автоматического remote fallback нет.

Независимый actual backend Compose прогон: /tmp/tseh-close-root-real-review/result.json (PASS, backend adf37594383132e57669c45180fb47fad494c1f9). Пять случаев: dispatch10/20, dispatch0/20, fixed3/10, prototype0/10 и2/10. До/после каждого close одинаковы счётчики движений, приёмок, потерь, production документов и execution фактов. Задачи исчезают из active и доступны в history с остатками10/20/7/10/8. Read-only OpenAPI для capability загружается без worker-cookie, business requests используют прежнюю защищённую cookie-сессию.

Ошибки сети/прав/версий и Room reopen проверены синтетическими тестами, без внедрения отказов в пользовательский backend. Админские замечания и Telegram в этот объём не входят. Общий backend/его БД не изменялись.

Прежний actual-backend E2E независимо повторён после изменений: /tmp/tseh-close-root-baseline-review/result.json PASS. Финальный root verify: /tmp/tseh-close-root-final-check.log; APK переустановлен и запущен как обычное приложение (без instrumentation). Текущий локальный commit: `git log -1 --oneline`; новый этап не требует дополнительных согласований. Удалённое окружение этим запуском не проверялось.

## Завершение приёмки из карточки
Добавлена отдельная кнопка «Завершить приёмку»: открывает существующий final receive workflow с фактом текущего шага и причиной недостачи. Нулевой новый факт допустим, предыдущие поступления сохраняются; close-incomplete для RECEIVE не вызывается. Навигация и rotation не отправляют команды.

Проверено: 125 JVM, 7 connected, lint/assembleDebug; /tmp/tseh-final-receive-verify2.log. Независимый root verify: /tmp/tseh-receive-root-verify.log BUILD SUCCESSFUL. Compose: partial4 → zero final с причиной, exact lines[] и transfer version3; факты4/remaining6 сохранены. Actual backend: /tmp/tseh-final-receive-real-run1/result.json PASS, revision565e9e69881bb36eb61507f4d02a4e10c64fd96d, partial4 → новая кнопка → final5/shortage1. Только owned SQLite fixture, без пользовательских складских движений.

## 2026-11-03 — селектор сервера и новый default
На экране входа: удалённый HTTPS147-45-154-211.sslip.io, локальный emulator10.0.2.2:8000, USB127.0.0.1:8000 и свой URL. Пользователь явно выбрал remote default для обычного debug/release; существующий сохранённый выбор не перезаписывается. Пункт своего URL требует применения, до этого вход заблокирован. Смена адреса использует прежний session/cookie generation guard и очищает credentials; pending остаются по server+actor. Выбор не вызывает login/HTTP и не делает fallback. ProcessFixture/test DI остаются изолированными.

128unit +8connected,0fail/errors/skips,lint/assembleDebug: /tmp/tseh-server-selector-verify2.log. Независимый root verify: /tmp/tseh-server-selector-root-verify.log. Новая Compose-проверка использует fake repository и не подключается к удалённому серверу; живую совместимость/авторизацию этого сервера данный этап не проверяет. Предыдущие real-backend E2E отчёты относятся к предшествующим этапам.

## 2026-11-04 — минимальный рабочий интерфейс

Все основные экраны сокращены до товара, количества, единиц, размеров и коротких рабочих подсказок. Убраны ID/версии/SKU, ссылки на документы, технический аудит, дубли аккаунта/склада и необязательные комментарии форм. Листовые материалы вводятся листами/длиной; результат preview показывает расход именно текущей партии. Список изготовления показывает готовое изделие; история сохраняет фактическое количество и остаток. Операционная история и завершённые записи recovery свёрнуты. На складе «Доступно» показывается только при отличии от «На складе»; unknown/negative не заменяются нулём.

Domain/data HTTP/DTO/command payloads не менялись. Журнал хранит прежние версии, байты и UUID; UI только убирает известные служебные подписи. Неопределённый исход явно обозначается и допускает только ручной повтор исходного действия. Приёмка/недостача, close incomplete, прототип и автоматическая норма сохраняют серверную семантику.

Проверки: 131unit/9connected, failures/errors/skips=0, lint/assembleDebug (CONNECTED_TESTS=1 scripts/verify.sh, /tmp/tseh-simple-ui-final-verify6.log). Независимый root scripts/verify.sh: /tmp/tseh-simple-ui-root-verify.log PASS. Actual backend Compose baseline: /tmp/tseh-simple-ui-real-baseline/result.json PASS, backend d67be7c5d5ee5e60af5ff7399469c8e91c64747c; receive4+5final/shortage1, fixed3/6, prototype2/4→3/6, dispatch3 in transit, exact fact proofs and stock43/0/5. Все движения только в owned fixture DB. Финальные снимки /tmp/tseh-simple-ui-final-screens визуально проверены супервизором.

Actual process-death UI proof: /tmp/tseh-simple-ui-process-recovery3/result.json PASS. PID21069→21477, one physical fact, identical body/token, no replay on launch; safe manual retry returned ACK. Separate .processfixture package, synthetic loopback server only. Root visually reviewed recovery unknown-outcome screen. Fixture harness fixes remove unnecessary reverse DNS and wait for IME before coordinate selection; application semantics unchanged.

## 2026-11-04 — завершение с автоматическим возвратом
Необязательный комментарий удалён из UiState/intents и подтверждений новых receive/dispatch/manufacture форм; обязательные причины сохранены. Domain/API comment остаётся совместимым пустым для новых команд, исторические/pending комментарии не переписываются.

После durable ACK текущего task.is_completed или is_closed_incomplete route возвращается к существующему списку. CommandId обрабатывается один раз при RESUMED; список принудительно перечитывается с текущим фильтром, старый незавершённый GET отменяется/отбрасывается по generation. Частичный факт, preview, простое открытие закрытой карточки, ошибка/unknown и неудачное локальное сохранение ACK не вызывают перехода. Исторический result не подменяет текущий task из ACK. Явный retry подтверждённого завершения работает так же.

135unit/11connected,0 failures/errors/skips; lint/assembleDebug: /tmp/tseh-terminal-ack-final-verify3.log. Root independently ran scripts/verify.sh: /tmp/tseh-terminal-ack-root-verify.log PASS and re-counted XML135/11. UI: delayed ACK leaves page until body received, exactly one list reload after completion; background ACK consumed on resume, rotation no duplicate command/navigation; 500 unknown stays. Room ACK-save failure remains unresolved and exact retry recovers terminal receipt.

Actual backend baseline /tmp/tseh-terminal-ack-real-baseline/result.json PASS (terminal receive/full dispatch return, partial fixed/prototype stay,6 facts/stock43/0/5). Actual close5 /tmp/tseh-terminal-ack-real-close2/result.json PASS (all5return/freshlist/cardgone; history card stays, accounting unchanged/stock30/3/2). These writes use owned synthetic SQLite fixtures only; shared database is untouched.

## 2026-11-06 — без отдельного экрана проверки
Удалён экран проверки действий и все его пользовательские переходы. Pending-журнал/ACK сохраняются; безопасный ручной retry находится в карточке задания, включая уже закрытое задание с потерянным ответом. Формы ведут обратно к заданию. Старый route identifier сохранён только как невидимый redirect для совместимого восстановления NavController, без screen/VM/кнопок.
Новые unit-проверки охватывают закрытый task, task GET failure, двойное нажатие, terminal ACK, ошибку после снятия pending и отсутствие перехода по старому ACK. MWS Compose inline retry сравнивает точные байты двух попыток. Manual instrumentation suite /tmp/tseh-remove-recovery-manual-ui2.log:15 tests PASS (включая3 ранее незакоммиченные проверки кликов). Набор данного коммита содержит12 device-тестов;3 старые локальные проверки не включены. Unit/lint/build /tmp/tseh-remove-recovery-final-verify3.log и независимый root /tmp/tseh-remove-recovery-root-verify.log. Debug build /tmp/tseh-remove-recovery-debug-build.log SUCCESS. Process-death driver адаптирован на прямой вход в task, но новый physical force-stop run в этом этапе не запускался; предыдущие process-proof отчёты относятся к старому UI.

## 2026-11-06 — pull-to-refresh и один возврат
Кнопки «Обновить» заменены жестом на пяти detail/execution экранах; существующие жесты списков сохранены. Общий Material3 контейнер действительно отключает gesture при write/retry/close/review; manual-refresh indicator не пересоздаёт список. Ввод количества/причины/материалов сохраняется, read не создаёт POST. Кнопки «К заданию» удалены, возврат — тулбаром/системным «Назад».
До нового пользовательского ограничения прошли138unit/lint/assemble (/tmp/tseh-pull-refresh-final-verify3.log), independent root verify (/tmp/tseh-pull-refresh-root-verify.log),17manualUI (/tmp/tseh-pull-refresh-manual-ui.log). Из17 три старые локальные проверки кликов не входят в коммит; в репозитории14device cases. Два новых физических swipe cases проверили один GET, сохранение ввода, блокировку во время отправки и stock4→7 без POST. После указания «тесты только по просьбе» запусков нет; дальнейшая политика в AGENTS.md. VPN/network/auth code на этом этапе не менялся.

2026-11-06 debug prefill: предоставленные пользователем значения хранятся в игнорируемом local.properties и обычном debug BuildConfig; root remote HTTPS разрешён для предзаполнения без автовхода. Release/processFixture пустые. assembleDebug SUCCESS /tmp/tseh-debug-prefill-build.log; текущий Gradle вывел APK в intermediates/apk/debug, итоговый файл скопирован в привычный outputs/apk/debug. Тесты не запускались; существующие test expectations обновлены без запуска.

2026-11-06 latest working changes: stock available-only selector removed; all stock requests keep onlyAvailable=false. Automatic network activity uses pinned top linear progress, only explicit PullRefresh uses the circular indicator. Debug defaults populate immediately and after logout, without autologin. Shareable debug APK built by scripts/build-debug-apk.sh with injected.testOnly=false; signature verified, no testOnly package distributed. Latest build log /tmp/tseh-final-manual-progress-prefill-build.log SUCCESS. Download alias app/build/outputs/apk/debug/ceh-debug-installable.apk. No tests or Git operations after user prohibition; changes remain uncommitted.


2026-11-06 подготовка коммита по явному запросу пользователя: независимые стеки вкладок, сохранение ввода/прокрутки и загруженного диапазона при автообновлении; поиск материалов прототипа только по физическим остаткам цеха, пустая выдача при пустом запросе; автоматическая складская длина листа, ввод только числа листов; единый QuantityFormatter для отображения. Последняя сборка /tmp/tseh-quantity-display-build.log: assembleDebug SUCCESS, APK без testOnly, подпись проверена. Этот APK после последних изменений не устанавливался на устройство. Тесты после пользовательского ограничения не запускались. Ранее оставленные локальные проверки физических кликов включены в текущую передачу вместе с адаптациями test sources; результаты прежних запусков не являются проверкой текущих изменений. local.properties и APK в коммит не включаются.
