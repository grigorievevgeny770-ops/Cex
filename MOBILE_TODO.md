# Цех — план реализации
Контракт пользователя: 01.11.2026. Самостоятельное Android-приложение worker.

- [x] 0. Аудит среды и read-only capability API
- [x] 1. Модули, Compose, DI, навигация, собираемый debug
- [x] 2. DTO, HTTP, точные числа, защищённая cookie-сессия, Login/Restore/Logout
- [x] 3. Room PendingCommand, dispatcher, exact retry/recovery до write UI
- [x] 4. Workshop resolver, task list/detail/start/full history
- [x] 5. Receive partial/final/shortage/sheets/recovery
- [x] 6. Dispatch partial/confirmation/exact retry
- [x] 7. Stock search/pagination/detail/dimensions
- [x] 8. Production v2 preview/prototype/by_recipe с контрактными fixtures
- [x] 9. Regression, Compose E2E, lint, APK, передача

На каждом этапе исполнитель пишет application code и tests; супервизор проверяет и делает локальный commit. Push/deploy не разрешены. Локальный backend v2 появился во время этапа8; живая write-интеграция ожидает выделенный synthetic backend/worker.

## Дополнение: закрытие без полного выполнения
- [x] Domain/DTO/UseCase/Repository/RemoteDataSource, capability gate и durable close command.
- [x] Диалог причины и серверные active/history/all списки; закрытый остаток не считается выполненным.
- [x] Независимая Compose/real fixture приёмка закрытия и baseline; APK установлен, этап готов к локальному commit.
