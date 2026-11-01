# Цех — план реализации
Контракт пользователя: 01.11.2026. Самостоятельное Android-приложение worker.

- [x] 0. Аудит среды и read-only capability API
- [x] 1. Модули, Compose, DI, навигация, собираемый debug
- [x] 2. DTO, HTTP, точные числа, защищённая cookie-сессия, Login/Restore/Logout
- [x] 3. Room PendingCommand, dispatcher, exact retry/recovery до write UI
- [x] 4. Workshop resolver, task list/detail/start/full history
- [ ] 5. Receive partial/final/shortage/sheets/recovery
- [ ] 6. Dispatch partial/confirmation/exact retry
- [ ] 7. Stock search/pagination/detail/dimensions
- [ ] 8. Production v2 preview/prototype/by_recipe с контрактными fixtures
- [ ] 9. Regression, Compose E2E, lint, APK, передача

На каждом этапе исполнитель пишет application code и tests; супервизор проверяет и делает локальный commit. Push/deploy не разрешены. Живая production v2 интеграция заблокирована внешним backend.
