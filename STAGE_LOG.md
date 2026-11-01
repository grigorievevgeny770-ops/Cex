# Журнал этапов и проверок

## Этап 0 — 2026-11-01
- Исходный каталог: Android scaffold, README/AGENTS/production norms spec отсутствуют; Git отсутствовал.
- Системная Java17.0.19; scaffold daemon JDK25. SDK platforms34/35/36/36.1/37.0, adb emulator-5554 доступен.
- Read-only OpenAPI проверен обоих окружений; результаты в API_CONTRACT.md. Серверные mutations не выполнялись.
- Официальные сведения: https://developer.android.com/build/releases/agp-9-5-0-release-notes (stable October2026), https://developer.android.com/build/migrate-to-built-in-kotlin, https://developer.android.com/training/dependency-injection/hilt-android.
- Начат этап1 постоянным исполнителем. Сборка/тесты ещё не заявлены.

## Этап 1 — 2026-11-01
- Созданы 7 модулей и Compose navigation/theme/Hilt composition; статическая проверка границ успешна.
- SDK36/min26, AGP9.5/Gradle9.6/JDK25 daemon/JVM17/Kotlin2.3.21/KSP2.3.9/Hilt2.60.1/Compose BOM2026.03.00 закреплены. BOM2026.09 требует SDK37, оставлена совместимая stable BOM.
- Независимый `./scripts/verify.sh`: BUILD SUCCESSFUL; test задачи NO-SOURCE на этом этапе, lint без errors, assembleDebug создал APK. Лог /tmp/workshop-stage1-final.log.
- Найденный lint defect includeSubdomains исправлен исполнителем до commit; без suppress. Предупреждения о newer dependencies/SDK остаются информационными.
- Исходный экран scaffold не считается завершённым workflow; следующие этапы обязательны.
- Дополнительно после commit: adb install -r debug APK Success, am start -W MainActivity Status ok/COLD (API37 emulator sdk_gphone16k_arm64). Это launch smoke основы, не scenario30.

## Этап 2 — 2026-11-01
- Auth/session domain usecases, Retrofit DTO datasource, DataStore environment, encrypted AES-GCM/Keystore cookie storage, generation guarded CookieJar, MVI Login/Restore/Logout и app DI.
- Review обнаружил late logout/restore state races и non-atomic session context; исправлено до acceptance, regression tests добавлены.
- Независимый ./scripts/verify.sh: BUILD SUCCESSFUL (/tmp/workshop-stage2-review.log). 18 JVM tests: domain3/data14/feature-auth1, failures0/errors0. Exact decimal strings/Long, cookie restart/update/matching/expiry, legacy/native errors, HTML404, late session races проверены synthetic MockWebServer.
- Actual Android Keystore instrumentation пока не запускался; включён в финальные проверки. Нет write-проверок действующего backend.

## Этап 3 — 2026-11-01
- Room-журнал exact bytes/token, transactional active slots, singleton per-task locks, ACK snapshot+release transaction, profile isolation, explicit retry без auto replay.
- Независимый ./scripts/verify.sh SUCCESSFUL (/tmp/workshop-stage3-final-review.log): 32 tests, zero failures/errors, lint и assembleDebug. Реальные Room SQLite Robolectric проверки: persist-before-network, DB failure no send, concurrent dispatchers, lost reply→disk DB reopen→same bytes/token, ACK-save failure, cancellation, 401/403 после unknown. Это simulation reopen, не реальный kill Android process.
- Отдельно исправлен default окружения по замечанию пользователя: commit68ec8c5. Debug local10.0.2.2, release HTTPS configurable; remote не используется по умолчанию. APK installed/relaunched; UI hierarchy показывает local URL. Backend health200, protected reads401, real login/write не выполнялись.

## Этап 4 — 2026-11-01
- Workshop resolver, server task filters/pagination, task detail/start через durable dispatcher, полная paged история/исполнения, recovery journal UI, lifecycle RESUMED polling30sec и shared quantity/date formatter.
- Review: защищены StartClick во время Sending, stale detail после refresh failure; missing/inactive warehouse очищает устаревший список; malformed dates классифицируются как incompatible server.
- Независимый ./scripts/verify.sh SUCCESSFUL (/tmp/workshop-stage4-review.log), 52 tests0fail/errors: domain8/data35/coreUI1/auth1/tasks7; lint/assembleDebug/architecture passed. Проверены ID42 после main7, filters/races, >50history, current taskversion из ACK вместо result,409/403 без fallback. Compose execution E2E остаётся этапу9.

## Этап 5 — 2026-11-01
- Receive delta input/review/confirm через journal: partial/final shortage/zero final/reason, frozen sheets и exact comma decimals; no loss approval/no optimistic stock.
- Detail refresh при RESUMED и journal observer безопасно снимают блокировку только matching ACK. Input/review не отправляют writes, submit flight guard.
- Независимый ./scripts/verify.sh SUCCESSFUL (/tmp/workshop-stage5-review.log):63 tests0fail, lint/assembleDebug. Новые domain5/data3/VM3 проверяют10→4→5finalshortage1,zeroFinal,sheets-onlyqty0,foreign/overremaining/overprecision,unknown exactretry, recreated VM pending→ACK→fresh detail. Live backend writes не выполнялись.

## Этап 6 — 2026-11-01
- Dispatch form/review/confirmation через common durable dispatcher, отдельный strict DTO, общий frozen physical-line validator, явное объяснение in-transit без main credit.
- Независимый ./scripts/verify.sh SUCCESSFUL (/tmp/workshop-stage6-review.log),72 tests0fail, lint/assembleDebug/architecture. Проверены partial3→7, sheets-only,409reserve/422insufficient, exact500retry и doubletap/recovery VM. Живые stock mutations не выполнялись.

## Этап 7 — 2026-11-01
- Stock list/detail/server search/pagination/onlyAvailable, dimensions nullable availability, unallocated reserves, shared formatter и read refresh при entry/resume. Catalog/stock-definition отдельные read модели для prototype.
- Независимый ./scripts/verify.sh SUCCESSFUL (/tmp/workshop-stage7-review.log):82 tests0fail, lint/assembleDebug/architecture. Новые проверки: querypage2,zero/negative,unknownfreeSheets,Long,currentgeometryunits/catalognotstock,403/missingroute и VMfilterrace/debounce55positions/detailrefreshservertruth.

### Изменение внешнего состояния во время этапа8
Read-only audit: локальный OpenAPI теперь116paths/3productionv2POST. Backend source/spec обнаружены в соседнем sklad; backend обновлён другим процессом, Android агент его не менял/не перезапускал. Снапшот /tmp/workshop-openapi-v2.json передан исполнителю для сверки. Live write-интеграция ещё не проводилась; dedicated fixture worker отсутствует.

## Этап 8 — 2026-11-01
- V2 client fixed-recipe/prototype: descriptor gate, separate strict DTOs, server-only preview, catalog materialpicker/rawmaterial validation, revision/version-bound confirm, atomic serverACK→freshTask phase switch, exact originalprototype retry. No v1 manufacture fallback/no localrecipe math.
- Свежая OpenAPI116paths проверена вместе с backend source. Уточнены nullable recipe_status/details,499materials,<1e10 qty/unitqty и duplicate(product,length). Server UTC normalize подтверждён исполнителем read-only.
- Независимый ./scripts/verify.sh SUCCESSFUL (/tmp/workshop-stage8-review.log),101tests0fail, lint/assembleDebug/architecture. 20positivefixtures дополнительно сверены супервизором сfreshOpenAPI requiredfields/types/enums (не numeric/format validation). Unit/MWS/Room-reopen/VM покрываютfixed20/10→3/6,cumulative-roundingfixtures,prototype2/4→next3/6,409concurrency,originalprototype lostreplyretry,unknownreserve/batch422/deficit,stalepreview/phaseguards. Это клиентские fixtures, не живая serverstock приёмка.

## Этап 9 — финальная независимая проверка
- CONNECTED_TESTS=1 ./scripts/verify.sh: SUCCESSFUL. JVM101/device4, failures/errors/skips0; lint, assembleDebug и архитектура прошли. Лог: /tmp/workshop-final-connected-review.log.
- Реальные Compose receive/history/stock и prototype/by_recipe, ActivityScenario.recreate без auto writes, Android Keystore, device Room reopen проверены.
- python3 scripts/check-device-process-recovery.py --serial emulator-5554 --output /tmp/workshop-final-process-review: PASS. Force-stop PID12852→13203, physical fact1, attempts1→2 только после explicit retry, body/token прежние, no auto replay. Body SHA256: 2de0b399730d061545bca9d363b72798c89ad84f7272bed7e23e5be7f42360a4. Только isolated .processfixture и loopback synthetic backend.
- 22 fixtures прошли независимый jsonschema4.26.0 checker по свежему OpenAPI. Для optional checker нужен Python>=3.10, проверено Python3.12; Android verify от него не зависит.
- UI fixes до приёмки: safe insets, IME скрывается перед preview/confirm, стабильный screenshot диалога. Espresso3.7.0 закреплён для API37; StrictMode не отключался. PNG: /tmp/tseh-connected-screenshots/connected-screenshots/ и /tmp/workshop-final-process-review/.
- Финальная assembleDebug SUCCESSFUL: /tmp/workshop-final-apk-build.log. APK14954598bytes, SHA2561b13b4addf4712357356a7666cd9033bcff1eb587b642815af346db00b8fd199. Normal debug установлен/запущен Statusok; UI подтверждает http://10.0.2.2:8000/.
- Настоящие backend writes/login/worker ACL/production stock integration не запускались. Local v2 доступен, remote workshop API отсутствует. Нет push/deploy/изменений серверной БД.

## Follow-up — local debug login prefill
- User requested local default login/password fields. Values added only to ignored local.properties; debug BuildConfig consumes them, release/processFixture empty. No auto-login, remote/other-port fields empty, environment switch clears fields, submitted passwords still clear.
- Independent ./scripts/verify.sh SUCCESSFUL105tests; :app:assembleDebugAndroidTest SUCCESSFUL. Logs /tmp/workshop-prefill-review.log and /tmp/workshop-prefill-test-apk-review.log. No actual login POST.
- APK installed/relaunched; UI automation confirms requested login visible and masked password populated. APK14954598bytes SHA256c6b7664eb2572b534d23e938ce0bb2eed105b831bb54c27cc1099e1d28aeae0c.

## Follow-up — all available workshop tasks
- User removed the own/queue versus assigned-to-me switch. UI chips and TaskFilter.assignedToMe removed; Retrofit request no longer declares assignee_user_id. Server ACL/scope remains authoritative; no local page filtering or permission changes.
- Contract tests assert no assignee parameter across three task types, both status modes and page2. Filter race test uses type selection. Independent ./scripts/verify.sh SUCCESSFUL105tests/lint/build: /tmp/workshop-all-available-review.log.
- Updated normal debug APK installed/launched Statusok.14952739bytes SHA2560286514b7a7cb10dc9347ddf6501b961bfd721bfc66a6473576cc6f0c9a24aa0. No backend mutations.

## Follow-up — debug-only action audit and clean execution details
- User requested action history only in debug mode and removal of assignee/line IDs even in debug. Added default-collapsed debug-only audit disclosure. No dedicated history requests until expanded; refresh/pagination gated, collapse cancels/invalidate stale responses. Release/processFixture have no audit control.
- Removed assignee/actor labels and task-line ID/role labels from detail/execution display. Fact rows use product names with non-identifier fallbacks. Operational facts and PendingRecovery unchanged. Success links renamed to task card.
- Independent ./scripts/verify.sh SUCCESSFUL107tests/lint/APK; :app:assembleDebugAndroidTest SUCCESSFUL. Logs /tmp/workshop-debug-audit-review.log and /tmp/workshop-debug-audit-test-apk-review.log. No live write requests.
- Updated debug APK installed/launched Statusok.14955978bytes SHA256cfecb7446393a228cc9317e13d93e36d30ed68fb584d2bfb2841812bd1049604.

## Follow-up — direct execution without task lifecycle controls
- Removed task-status filters/labels, claim/start button/dialog/intents/ViewModel state, StartTaskUseCase, TaskRepository.start, Start DTO/API/binding, and canStart. Domain TaskStatus/assignment lifecycle removed; new commands go directly to execution endpoints. New start submits are rejected; historical stored start commands retain exact safe-retry evidence.
- Domain uses nullable isCompleted/isCancelled plus allowed_actions for safe execution. New backend bool flags take precedence; known legacy status maps privately in data, unknown/partial contract fails closed. Active queue uses data-only view=active plus legacy active_only=true; type filter/paging remain. No status/assignee query.
- Canonical production fixtures updated to new completion flags. New runtime schema audit is pending rollout; do not claim these current fixtures validated against legacy runtime. Contract tests cover both formats and no-start generation.
- Independent ./scripts/verify.sh SUCCESSFUL110tests/lint/build; :app:assembleDebugAndroidTest SUCCESSFUL. Manual isolated device instrumentation OK4tests12.003s. Logs /tmp/workshop-direct-execution-review.log, /tmp/workshop-direct-execution-test-apk-review.log, /tmp/workshop-direct-execution-device-review.log. No real backend writes.
- Updated normal debug APK installed/launched Statusok.14945409bytes SHA256b4d8e2c3327c481003079d71557d26e02de29136fb9ef2687eeaa614e2c5666e.
- Final read-only runtime audit still shows old TaskRead.status and /start endpoint (116paths). Backend source is concurrently being updated in another repository; it was not edited/restarted/deployed by this Android task. Any employee accessing formerly assigned tasks still requires that server rollout; existing ACL is not bypassed.

## 2026-11-02 — hide warehouse reserve quantities
- Removed reserve displays from warehouse list/detail totals, dimensions/sheet reserves and unallocated reserve fragment. Physical/free values and unknown availability preserved. DTO/domain/server reserve accounting and production previews unchanged.
- Independent ./scripts/verify.sh SUCCESSFUL110tests/lint/assembleDebug/architecture, /tmp/workshop-hide-stock-reserves-review.log. No mirrored label tests added; connected test sources unchanged. No backend writes.
- Normal debug APK installed/launched Statusok;14945409bytes SHA2563a48363c40915f75c5422a61a18626b3964d8da58fffa20ca192838eced86167.

## 2026-11-02 — fix refresh Request validation failed
- Root cause: the legacy active_only=true parameter remained in task GET for speculative compatibility. Updated backend uses a strict query model and returns422 VALIDATION_ERROR, details.errors loc=query.active_only/type=extra_forbidden. Removed it; canonical request uses view=active plus supported type/warehouse_id/limit/offset only.
- Read-only diagnostic with a separate authenticated worker session reproduced old query422 and corrected query200 (Page.items). Diagnostic login/logout200; no inventory/task mutations, password/cookie not logged.
- Strict MockWebServer regression reproduces422 and asserts exact allowed query keys for all3types/no-type and pages0/50. Independent ./scripts/verify.sh SUCCESSFUL110tests/lint/build: /tmp/workshop-refresh-query-fix-review.log.
- Current local OpenAPI136paths, TaskRead completion flags present, /start absent.22canonical fixtures independently pass jsonschema against /tmp/workshop-openapi-refresh.json. Prior server-rollout/schema blockers are closed for local API availability; live multi-worker write acceptance remains unperformed.
- Updated APK installed/launched Statusok. Tasks screen present; validation error absent before/after refresh gesture.14945409bytes SHA256e712603bc2e50ef4f4e0f129a5b195edf5f81349d39181a08acb080e746fdc33.

## 2026-11-02 — real-backend UI E2E without server mocks
- Added opt-in RealBackendConnectedTest and check-real-backend-ui.py. Actual backend Git HEAD dc7e140afb4d44e3fdb939ed4ae1d7d6d2af05b1 archived unmodified into owned fresh OS-temp root; actual FastAPI/uvicorn, startup/migrations, SQLAlchemy/SQLite and business services run on loopback ephemeral port. No real server8000 URL accepted, no source backend edits.
- Fixture guard before app imports: marker/archive hash, explicit safe checks, fresh DB/cwd, sanitized Python startup env, SQL/file-write/external-network guards, app.__file__/engine path under owned root. Metadata-only attestation binds launcher/Android to marker+revision before auth; business responses are not stubbed. Existing output/Python3.9/optimizedPython guard negative checks passed; sentinel preserved, no DB created.
- Independent final live run PASS: /tmp/workshop-real-backend-final-review/result.json and instrumentation.log. Real Compose loginworker1 receive4/recreate(no duplicate), loginworker2 receive5final shortage1, fixedinput6/output3, prototypeinput4/output2 theninput6/output3, dispatch finishedproduct3. Every fact/actor/recipe basis4on2 and learned execution checked through API. DB verifies6executions/3productiondocuments/no loss/assigned_toNULL; stock43/0/5, main0, dispatch transit3/accepted0/loss0.
- Independent previous live run /tmp/workshop-real-backend-root-review also passed; readonly direct DB inspection independently confirmed per-execution quantities4,5,6→3,4→2,6→3,dispatch3.
- Independent ./scripts/verify.sh SUCCESSFUL110tests/lint/build: /tmp/workshop-real-backend-fast-review.log. Default manual instrumentation OK4tests11.737s, no real-test skip: /tmp/workshop-real-backend-default-device-review.log. @RealBackendOnly class excluded by default, explicit real invocation requires attestation.
- Real backend/helper safety changes only in test code/scripts; app production code unchanged. Normal APK SHA256 remains e712603bc2e50ef4f4e0f129a5b195edf5f81349d39181a08acb080e746fdc33. Restored normal application cold start Statusok; backend repository remained clean, working server/DB unmodified.
