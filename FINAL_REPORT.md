# Цех — итоговая передача

Готов worker-клиент: авторизация, задания с прямым выполнением, приёмка, отправка, производство по норме/прототипу, история, recovery и остатки. Семь модулей, MVI/UDF, durable Room dispatcher.

## APK и команды

`app/build/outputs/apk/debug/app-debug.apk` — 14 810 148 байт.

SHA256: `a3dc82d7b8b758e2c0ef6849edf5f97554b41c41630ebcd2171ae6484cb47fd4`.

APK установлен и запущен на emulator-5554/API37. Debug/release default: `https://147-45-154-211.sslip.io/` (saved environment takes precedence). SDK/JDK и настройка окружений — в README.md.

```sh
./scripts/verify.sh
CONNECTED_TESTS=1 ./scripts/verify.sh
python3 scripts/check-device-process-recovery.py --serial emulator-5554 --output /tmp/tseh-new-unique-run
```

Connected tests запускать на выделенном эмуляторе: AGP устанавливает/снимает target APK. После проверок обычный debug APK переустановить. Process test использует только отдельный пакет `.processfixture` и loopback synthetic backend.

## Независимо проверено

- 128 JVM-тестов, 8 обычных device-тестов и 2 real-backend UI E2E: 0 failures/errors/skips; lint, assembleDebug и архитектура успешны.
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
