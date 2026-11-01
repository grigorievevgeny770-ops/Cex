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
