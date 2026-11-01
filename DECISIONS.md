# Решения

2026-11-01:
- Проект остаётся в WorkshopManager; не смешивается с Python backend и LMRU.
- Один постоянный исполнитель; супервизор владеет контрактом, аудитом и коммитами.
- Мобильный пользователь worker; отсутствуют manager mutations и формы создания задач.
- Production v1 manual budget не используется. Production v2 реализуется только как клиент проектируемого контракта с fixtures до backend rollout.
- Точные BigDecimal, server truth, durable commands, явный retry без фонового replay обязательны.
- Все обычные обратимые решения выполняются без промежуточного approval.

## НУЖНО СОГЛАСОВАТЬ
- Backend v2 rollout и окончательная совместная OpenAPI фиксация production-контракта.
- Предоставление владельцем отдельного worker и изолированного synthetic backend для живых write-проверок.
- Серверная политика sheet batches/обрезков/брака; клиент не округляет расход эвристически.
