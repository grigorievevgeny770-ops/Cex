# Цех — Android

Kotlin / Jetpack Compose приложение работника склада. Разработка ведётся по
MOBILE_TODO.md; текущая версия может содержать ещё незавершённые сценарии.
Производство v2 является проектируемым серверным контрактом; v1 manual
manufacture не используется вместо него.

## Инструменты

Android SDK Platform 36, Build Tools (устанавливаются Android Studio), JDK 25
для Gradle daemon (Android Studio bundled JBR), JDK 17 для JVM toolchain.
Gradle wrapper 9.6.0, AGP 9.5.0. minSdk 26, compile/target SDK 36.
Kotlin 2.3.21, KSP 2.3.9, Hilt 2.60.1, Compose BOM 2026.03.00.
Проверенные версии зафиксированы в `gradle/libs.versions.toml`.
`local.properties` с sdk.dir является локальным и не входит в Git.

## Проверки и сборка

Из корня проекта:

```sh
./scripts/verify.sh
# эквивалентно:
python3 scripts/check-architecture.py
./gradlew test lint assembleDebug
# при работающем эмуляторе:
CONNECTED_TESTS=1 ./scripts/verify.sh
# только UI:
./gradlew connectedDebugAndroidTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Установка: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Окружения

Адрес выбирается в приложении; логины и пароли не включены в исходники.
Для Android Emulator локальный backend: `http://10.0.2.2:8000/`.
Для телефона по USB: `adb reverse tcp:8000 tcp:8000`, затем
`http://127.0.0.1:8000/`. Не нужно открывать backend в LAN.
Debug допускает HTTP только localhost/127.0.0.1/10.0.2.2.
Release допускает HTTPS с системной проверкой сертификата.
Удалённый сервер может не поддерживать workshop API: см. API_CONTRACT.md.

## Границы

`app` связывает навигацию, DI и реализации; `domain` — чистый Kotlin/JVM;
`data` — HTTP/Room/secure storage; `core:ui` — общие компоненты;
`feature:auth`, `feature:tasks`, `feature:stock` — MVI/UI.
Features зависят только от domain/core:ui, domain не зависит от Android.
Проверка `scripts/check-architecture.py` контролирует зависимости.

Официальные справочники: [AGP](https://developer.android.com/build/releases/agp-9-5-0-release-notes),
[Compose BOM](https://developer.android.com/develop/ui/compose/bom),
[Hilt](https://dagger.dev/hilt/gradle-setup),
[KSP](https://github.com/google/ksp/releases).
