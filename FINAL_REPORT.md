# Цех — итоговая передача

Готов worker-клиент: авторизация, задания/start, приёмка, отправка, производство по норме/прототипу, история, recovery и остатки. Семь модулей, MVI/UDF, durable Room dispatcher.

## APK и команды

`app/build/outputs/apk/debug/app-debug.apk` — 14 955 978 байт.

SHA256: `cfecb7446393a228cc9317e13d93e36d30ed68fb584d2bfb2841812bd1049604`.

APK установлен и запущен на emulator-5554/API37. Debug default: `http://10.0.2.2:8000/`. SDK/JDK и настройка окружений — в README.md.

```sh
./scripts/verify.sh
CONNECTED_TESTS=1 ./scripts/verify.sh
python3 scripts/check-device-process-recovery.py --serial emulator-5554 --output /tmp/tseh-new-unique-run
```

Connected tests запускать на выделенном эмуляторе: AGP устанавливает/снимает target APK. После проверок обычный debug APK переустановить. Process test использует только отдельный пакет `.processfixture` и loopback synthetic backend.

## Независимо проверено

- 107 JVM-тест, 4 device-теста: 0 failures/errors/skips; lint, assembleDebug и архитектура успешны.
- Compose: login → receive → confirm → history → server stock refresh; prototype 2/4 → готовая норма → следующий выпуск 3/6 без materials. Recreate не повторяет START/EXECUTE.
- Реальный Android Keystore и Room reopen.
- Настоящий force-stop/relaunch: PID12852 → 13203, один физический факт, прежние body/token, повтор только по нажатию.
- 22 synthetic production fixtures прошли jsonschema по свежей локальной OpenAPI. Это не живая приёмка серверных складских расчётов.
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
