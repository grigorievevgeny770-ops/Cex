# Цех — итоговая передача

Готов worker-клиент: авторизация, задания с прямым выполнением, приёмка, отправка, производство по норме/прототипу, история, recovery и остатки. Семь модулей, MVI/UDF, durable Room dispatcher.

## APK и команды

`app/build/outputs/apk/debug/app-debug.apk` — 14 945 409 байт.

SHA256: `e712603bc2e50ef4f4e0f129a5b195edf5f81349d39181a08acb080e746fdc33`.

APK установлен и запущен на emulator-5554/API37. Debug default: `http://10.0.2.2:8000/`. SDK/JDK и настройка окружений — в README.md.

```sh
./scripts/verify.sh
CONNECTED_TESTS=1 ./scripts/verify.sh
python3 scripts/check-device-process-recovery.py --serial emulator-5554 --output /tmp/tseh-new-unique-run
```

Connected tests запускать на выделенном эмуляторе: AGP устанавливает/снимает target APK. После проверок обычный debug APK переустановить. Process test использует только отдельный пакет `.processfixture` и loopback synthetic backend.

## Независимо проверено

- 110 JVM-тест, 4 device-теста: 0 failures/errors/skips; lint, assembleDebug и архитектура успешны.
- Compose: login → receive → confirm → history → server stock refresh; prototype 2/4 → готовая норма → следующий выпуск 3/6 без materials. Recreate не повторяет START/EXECUTE.
- Реальный Android Keystore и Room reopen.
- Настоящий force-stop/relaunch: PID12852 → 13203, один физический факт, прежние body/token, повтор только по нажатию.
- Все 22 canonical production fixtures с completion flags прошли jsonschema по обновлённой локальной OpenAPI 02.11.2026. Совместимость с legacy status покрыта contract-тестами.
- Визуально проверены safe insets, скрытие клавиатуры, диалог прототипа и recovery.

Матрица: TEST_MATRIX.md. Логи и evidence: STAGE_LOG.md.

## Ограничения и открытые решения

- Живые write-сценарии, worker ACL и server stock/recipe транзакции настоящего backend не проверялись. Нужны отдельный fixture backend и worker от владельца; общая БД не изменялась.
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
