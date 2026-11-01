# Матрица приёмки

Статусы устанавливает супервизор после фактического запуска. Fixtures не означают живую серверную приёмку.

| № | Сценарий | Уровень | Статус |
|---|---|---|---|
|1|Auth success/error, restart/update cookies, logout late response|data + instrumentation|JVM + реальный Android Keystore: passed|
|2|No password persistence/logged cookies, actor/server isolation|data + review|Tests + статическое ревью: passed|
|3|Workshop42 после main7, missing/inactive/403|domain/data|Unit/MockWebServer/VM: passed|
|4|Task scope/paging/filter race/unknown enums|data/VM|Unit/MockWebServer/VM: passed|
|5|Direct execution, no new start, stale version, permissions|data/VM|Unit/MockWebServer/VM: passed|
|6|Receive full/partial/multiple step delta|domain/data|Unit/MWS/VM + real FastAPI UI partial/final: passed|
|7|Receive10→4→5 final shortage1 без loss approval|data/VM|Real FastAPI UI workers A/B 4+5final, unresolved1/no loss: passed|
|8|Zero final/reason, partial, excess/foreign line|domain/data|Unit/MockWebServer/VM: passed|
|9|Frozen sheets/current catalog/baseqty0|data|Unit/MockWebServer/VM: passed|
|10|Dispatch partial/remaining/retry/no main credit|data/VM|Unit partial/retry + real UI finished-product dispatch3/in-transit: passed|
|11|Fixed recipe preview20/10→3/6, no materials in command|MockWebServer|Real UI/backend input6/output3, no material entry: passed|
|12|Cumulative rounding partial/final — server fixtures only|MockWebServer|Unit/MockWebServer/Room/VM fixtures passed; live not run|
|13|Prototype q1>1, preview/progress atomic ACK|MockWebServer/VM|Real UI/backend first2/material4/recipe basis2: passed|
|14|After prototype by_recipe no material entry|VM/UI|Real UI/backend next3/material6 output-only: passed|
|15|Concurrent prototype409/lost response exact prototype retry|data|Unit/MockWebServer/Room/VM fixtures passed; live not run|
|16|Manual override forbidden/no v1 fallback|data/VM|Unit/MockWebServer/Room/VM fixtures passed; live not run|
|17|Sheet batch/unknown reserve/missing/preview errors|data/VM|Unit/MockWebServer/Room/VM fixtures passed; live not run|
|18|Manufacture500 pending/no optimistic stock|data/VM|Unit/MockWebServer/Room/VM fixtures passed; live not run|
|19|BigDecimal string/Long/formatter separation|unit|Transport string/Long + formatter: passed|
|20|Strict per-command fields|serialization|Serialization/MockWebServer: passed|
|21|Stock/search/pages/negative/unknown/catalog separation|data/VM|Unit/MWS search/pages + real API/DB/UI stock43/0/5: passed|
|22|Persist before network/Room failure prevents send|Room/dispatcher|Room/Robolectric/MockWebServer: passed|
|23|Concurrent VM events one pending command|Room/dispatcher|Room/Robolectric/MockWebServer: passed|
|24|Accepted response lost/restart/raw same token and body|Room/MockWebServer|Room/MWS + настоящий Android force-stop/relaunch: passed|
|25|ACK local failure honest state/safe retry|Room/dispatcher|Room/Robolectric/MockWebServer: passed|
|26|401/403 preserves unknown pending/actor|data|Room/Robolectric/MockWebServer: passed|
|27|Rotation/recomposition/back no auto write|Compose|Compose ActivityScenario.recreate + счётчики writes: passed|
|28|History over50 paged IDs|data/VM|Unit/MockWebServer/VM: passed|
|29|Unsupported environment separate from empty/auth failure|data/VM|Unit/MockWebServer/VM: passed|
|30|Login→list→detail→confirm→success/history+stock refresh|Compose emulator|Real Compose + actual FastAPI/SQLite all3workflows: passed|

Живые write-проверки: не разрешены на общей базе, нужен отдельный fixture backend. Локальные v2 routes появились во время разработки и сверены с fixtures. Реальная write-интеграция теперь пройдена на отдельном owned backend с двумя worker; результаты не относятся к общей пользовательской БД.

2026-11-02: реальная write-интеграция выполнена на отдельном owned FastAPI/SQLite, backend dc7e140. См. /tmp/workshop-real-backend-final-review/result.json. Общая БД не изменялась. Предыдущие отметки live not run для этих сценариев устарели; negative/race/fault live-случаи не входят в новый smoke.

## Close-incomplete (2026-11-02)

| Сценарий | Проверка | Результат |
|---|---|---|
|Actions/type/terminal gate, receive forbidden, legacy/prototype closable|domain tests + real Compose receive absence|passed|
|Reason blank/whitespace/2001 and Unicode boundary; cancel/double tap|domain/VM + Compose instrumentation|passed (6 connected tests)|
|Strict payload, cookie POST/public schema, errors401/403/404/409/422, no route fallback|MockWebServer|passed|
|Unknown500 → Room reopen →401/403/409/422 → identical retry; initial idempotency conflict retains slot|Room/MockWebServer|passed|
|Worker version conflict → refresh → explicit confirmation/new key; ACK recovery|VM + Compose|passed (6 connected tests)|
|Dispatch10/20 and zero, fixed3/10, prototype0/10 and2/10; history/remainder|real FastAPI Compose /tmp/tseh-real-close-run3/result.json|passed; independent repeat /tmp/tseh-close-root-real-review/result.json|
|No inventory/receipt/loss/execution creation by close|real owned DB before/after each close|passed for all five cases|

All real write cases use a separately archived backend and a newly owned SQLite fixture, never the shared user database. Timeout and permission faults are synthetic; they are not claimed as live-backend failure injection.
