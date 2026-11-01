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
