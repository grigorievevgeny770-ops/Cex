# Цех — Android

Приложение работника склада: задания, приёмка, отправка, производство по
норме/первому прототипу, история и остатки цеха. Kotlin, Compose Material3,
Hilt, Retrofit/OkHttp, Kotlin Serialization, Room и DataStore.

На 01.11.2026 production v2 подтверждён read-only локальным OpenAPI и
исходниками backend. Клиент проверен на synthetic fixtures/MockWebServer;
живая интеграция с выделенным backend/worker ещё не выполнялась. Старые
серверы без v2 и legacy manual manufacture остаются явно неподдерживаемыми.
Текущее состояние окружений и открытые решения: `API_CONTRACT.md`,
`DECISIONS.md`, `MOBILE_TODO.md`, `STAGE_LOG.md`, `TEST_MATRIX.md`.

## SDK, JDK и сборка

minSdk 26, compile/target SDK 36. Нужны Android SDK Platform 36, SDK Build
Tools, JDK 25 для Gradle daemon (Android Studio bundled JBR) и JDK 17 для
JVM toolchain. Gradle wrapper 9.6.0, AGP 9.5.0, Kotlin 2.3.21, KSP 2.3.9,
Hilt 2.60.1, Compose BOM 2026.03.00, Room 2.8.5. Версии закреплены в
`gradle/libs.versions.toml`. `local.properties` с sdk.dir не входит в Git.

```sh
./scripts/verify.sh
# выполняет architecture checks и ./gradlew test lint assembleDebug

# на выделенном тестовом эмуляторе:
CONNECTED_TESTS=1 ./scripts/verify.sh
# отдельно: ./gradlew connectedDebugAndroidTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Установка: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Connected tests устанавливают/снимают APK через Android Gradle Plugin.
Запускайте их на выделенном эмуляторе, а не на повседневном устройстве
работника. После проверок установите обычный debug APK снова. Espresso 3.7.0
задан явно: старая транзитивная версия Compose не работает на API 37.

## Адреса и аккаунт

Debug по умолчанию: **`http://10.0.2.2:8000/`**, локальный backend компьютера
из стандартного Android Emulator. Сохранённый выбор окружения имеет
приоритет; адрес виден и меняется на экране входа.
Для телефона по USB выполните `adb reverse tcp:8000 tcp:8000` и выберите
`http://127.0.0.1:8000/`. Открывать backend в LAN не требуется.
HTTP допускается только debuggable-вариантами для localhost/127.0.0.1/10.0.2.2.

Release требует HTTPS:

```sh
./gradlew :app:assembleRelease -PreleaseBaseUrl=https://your-test-host/
```

Без настройки release использует намеренно нерабочий
`https://example.invalid/`. Trust-all/отключения hostname verification нет.
Release и processFixture не содержат предзаполненных учётных данных.
Для обычного debug можно локально задать `debug.prefill.login` и
`debug.prefill.password` в игнорируемом `local.properties`. Значения попадут
только в обычный debug APK и заполнят поля после начальной проверки сессии
(также при недоступном backend), только для localhost/127.0.0.1/10.0.2.2
на порту 8000. Автовхода нет. Смена сервера очищает поля; после попытки
входа пароль очищается из экранного состояния. Значения в Git не входят.
Для живой проверки владелец
backend предоставляет worker с workshop permissions и отдельные synthetic
товары/остатки; manager-аккаунт не доказывает права worker.

## Слои и состояния

`app` связывает навигацию и DI; `domain` — чистый Kotlin/JVM;
`data` — HTTP/DTO, cookie, Room и настройки; `core:ui` — общие компоненты;
`feature:auth`, `feature:tasks`, `feature:stock` — ViewModel/MVI/UI.
Features зависят только от domain/core:ui. Composable content получает state
и callbacks, route связывает ViewModel. Границы контролирует
`scripts/check-architecture.py`.

Склад находится по разрешённому `code=workshop`, без literal ID или выбора
первого склада. Список содержит все задания цеха, доступные текущему
работнику по серверным правам; фильтра по исполнителю нет. Тип задания,
текущие/завершённые и пагинация применяются сервером, страницы по 50. Список
заданий обновляется примерно раз 30 секунд только в RESUMED. Карточки,
список и остатки перечитываются при возврате после складских действий.
Факты выполнения всегда доступны отдельными страницами и не ограничены
вложенными 50 строками. Аудит «История действий» доступен только в обычном
debug и по умолчанию свёрнут: загрузка начинается после раскрытия
«Отладочная история». В release и processFixture нет этого блока и запросов
аудита. Журнал восстановления незавершённых команд доступен всегда. В карточке и
формах выполнения не показываются исполнитель/actor ID, task_line_id и
техническая роль строки; факты подписаны названием товара.

Stock показывает физический/зарезервированный/свободный остаток, включая
нулевые и отрицательные позиции по умолчанию. Nullable свободные листы и
размерные остатки отображаются как «неизвестно». Каталог не подтверждает
наличие на складе; текущая геометрия не пересчитывает frozen строки задания.

## Сессия и сохранённые команды

После login вызывается `/auth/me`, ID берётся только из его ответа. Cookie
сохраняются AES-GCM в no-backup private storage с ключом Android Keystore;
пароль не сохраняется. Общий CookieJar учитывает host/path/secure/expiry и
Set-Cookie. Generation guards отсекают поздние cookie и session-ответы после
logout/смены окружения.

До HTTP складской команды Room атомарно сохраняет точный UTF-8 body/token,
server+actor+task и безопасную summary. SQLite slot и общий per-task mutex
исключают новую параллельную неподтверждённую команду. ACK и полный task
snapshot сохраняются до освобождения слота. Logout/401 сохраняют журнал,
но другой actor/server его не видит и не может отправлять.

После timeout/500/process death — только явный повтор исходного запроса с
теми же байтами и token; автоматического фонового replay нет. Первичный
однозначный отказ сохраняется как REJECTED и освобождает слот. После уже
неопределённого исхода последующий отказ прав/версии требует сверки и
сохраняет NEEDS_REVIEW. Кнопки «забыть и отправить новым token» нет.

Приёмка/отправка вводят факт текущего шага, не накопленный итог. Нулевой
final приёмки допустим с причиной недостачи. Потери клиент не утверждает.
Отправленный товар находится в пути до отдельной приёмки основным складом.
Payload отправки не содержит final/reason/expected_transfer_version.

BigDecimal хранится/передаётся точно строкой; ввод поддерживает запятую,
qty до 4 знаков, длину до 3 без молчаливого округления. Общий formatter
для отображения даёт до 2 знаков HALF_UP с русской запятой. Точная summary
подтверждения/восстановления сохраняет фактическую точность ввода.

## Производство v2

Готовая норма: только выпуск → серверный preview расхода → подтверждение.
Прототип: выпуск и материалы один раз → preview предлагаемой нормы → один
атомарный execution → свежий TaskRead переключает экран на готовую норму.
Сам preview фазу не меняет. Прототип входит в план. Любое изменение фактов,
комментария, версии задания/нормы делает прежний preview недействительным.
By-recipe DTO конструктивно не содержит materials; v1 fallback отсутствует.

Frozen output выбирается как единственная TaskLine с `role=output`, не как
первый элемент и не по product_id. Это подтверждено backend
`production_planning.py`/`production_create.py`. Неоднозначная строка и
неизвестные schema/mode/status не получают кнопки изменения. Новые материалы
прототипа выбираются из каталога; площадь/серверная норма локально не считаются.

22 fixtures в `data/src/test/resources/production-v2` сверены с актуальным
локальным OpenAPI. Дополнительная схема-проверка не нужна Android runtime
и не импортирует/не запускает backend. Нужен Python >=3.10 (проверен 3.12):

```sh
/opt/homebrew/bin/python3.12 -m venv /tmp/tseh-contract-check
/tmp/tseh-contract-check/bin/pip install -r scripts/requirements-contract.txt
curl --fail http://127.0.0.1:8000/openapi.json -o /tmp/tseh-openapi.json
/tmp/tseh-contract-check/bin/python scripts/check-production-fixtures.py --openapi /tmp/tseh-openapi.json
```

## Реальные проверки устройства

На API 37 connected tests проходят реальные Login→list→detail→receive
summary/confirm→success/history→stock refresh и prototype→ready→by_recipe,
а также recreate без автоматических start/execute. Hilt test DI использует
только MockWebServer и отдельное test storage. Отдельно проверены настоящий
Android Keystore (ciphertext/reopen/update/logout guard) и Room disk reopen.

Настоящий force-stop/cold restart проверяется отдельно на изолированном
applicationId `com.logunovinc.workshopmanager.processfixture`:

```sh
python3 scripts/check-device-process-recovery.py --serial emulator-5554 --output /tmp/tseh-process-check-unique
```

Нужен новый output-каталог для каждого запуска. Скрипт поднимает временный
fixture исключительно на loopback, собирает/устанавливает processFixture APK,
очищает только его synthetic данные, вводит synthetic аккаунт и сохраняет
скриншоты/JSON evidence в output. Сервер принимает один факт и теряет ответ;
скрипт force-stop приложения, проверяет новый PID, отсутствие auto replay,
затем явный retry с тем же body/token и единственным фактом. Основной APK и
его настройки скрипт не очищает. Без явного fixtureBaseUrl этот вариант
направлен на закрытый `http://127.0.0.1:9/`, а не на рабочий backend.

Проверены 107 unit/contract/VM/Room тест и 4 connected теста. Все write
проверки — synthetic; пользовательская/удалённая БД не изменялась. Живая
сквозная приёмка main→workshop→manufacture→dispatch на выделенном backend
остаётся отдельной интеграционной проверкой после provisioning владельцем.

Официальные справочники: [AGP](https://developer.android.com/build/releases/agp-9-5-0-release-notes),
[Compose BOM](https://developer.android.com/develop/ui/compose/bom),
[Hilt](https://dagger.dev/hilt/gradle-setup),
[AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test),
[Room](https://developer.android.com/jetpack/androidx/releases/room).
