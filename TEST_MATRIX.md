# Матрица приёмки

Статусы устанавливает супервизор после фактического запуска. Fixtures не означают живую серверную приёмку.

| № | Сценарий | Уровень | Статус |
|---|---|---|---|
|1|Auth success/error, restart/update cookies, logout late response|data + instrumentation|JVM + реальный Android Keystore: passed|
|2|No password persistence/logged cookies, actor/server isolation|data + review|Tests + статическое ревью: passed|
|3|Workshop42 после main7, missing/inactive/403|domain/data|Unit/MockWebServer/VM: passed|
|4|Task scope/paging/filter race/unknown enums|data/VM|Unit/MockWebServer/VM: passed|
|5|Start, worker race, stale version, permissions|data/VM|Unit/MockWebServer/VM: passed|
|6|Receive full/partial/multiple step delta|domain/data|Unit/MWS/VM + Compose partial receive: passed|
|7|Receive10→4→5 final shortage1 без loss approval|data/VM|Unit/MockWebServer/VM: passed|
|8|Zero final/reason, partial, excess/foreign line|domain/data|Unit/MockWebServer/VM: passed|
|9|Frozen sheets/current catalog/baseqty0|data|Unit/MockWebServer/VM: passed|
|10|Dispatch partial/remaining/retry/no main credit|data/VM|Unit/MockWebServer/VM: passed|
|11|Fixed recipe preview20/10→3/6, no materials in command|MockWebServer|Unit/MockWebServer/Room/VM fixtures passed; live not run|
|12|Cumulative rounding partial/final — server fixtures only|MockWebServer|Unit/MockWebServer/Room/VM fixtures passed; live not run|
|13|Prototype q1>1, preview/progress atomic ACK|MockWebServer/VM|Unit/MWS + Compose prototype q1=2: passed; live not run|
|14|After prototype by_recipe no material entry|VM/UI|VM + реальный Compose prototype→by_recipe: passed; live not run|
|15|Concurrent prototype409/lost response exact prototype retry|data|Unit/MockWebServer/Room/VM fixtures passed; live not run|
|16|Manual override forbidden/no v1 fallback|data/VM|Unit/MockWebServer/Room/VM fixtures passed; live not run|
|17|Sheet batch/unknown reserve/missing/preview errors|data/VM|Unit/MockWebServer/Room/VM fixtures passed; live not run|
|18|Manufacture500 pending/no optimistic stock|data/VM|Unit/MockWebServer/Room/VM fixtures passed; live not run|
|19|BigDecimal string/Long/formatter separation|unit|Transport string/Long + formatter: passed|
|20|Strict per-command fields|serialization|Serialization/MockWebServer: passed|
|21|Stock/search/pages/negative/unknown/catalog separation|data/VM|Unit/MWS/VM + Compose stock refresh: passed|
|22|Persist before network/Room failure prevents send|Room/dispatcher|Room/Robolectric/MockWebServer: passed|
|23|Concurrent VM events one pending command|Room/dispatcher|Room/Robolectric/MockWebServer: passed|
|24|Accepted response lost/restart/raw same token and body|Room/MockWebServer|Room/MWS + настоящий Android force-stop/relaunch: passed|
|25|ACK local failure honest state/safe retry|Room/dispatcher|Room/Robolectric/MockWebServer: passed|
|26|401/403 preserves unknown pending/actor|data|Room/Robolectric/MockWebServer: passed|
|27|Rotation/recomposition/back no auto write|Compose|Compose ActivityScenario.recreate + счётчики writes: passed|
|28|History over50 paged IDs|data/VM|Unit/MockWebServer/VM: passed|
|29|Unsupported environment separate from empty/auth failure|data/VM|Unit/MockWebServer/VM: passed|
|30|Login→list→detail→confirm→success/history+stock refresh|Compose emulator|Реальный Compose на API37 + synthetic HTTP fixture: passed|

Живые write-проверки: не разрешены на общей базе, нужен отдельный fixture backend. Локальные v2 routes появились во время разработки и сверены с fixtures. Живая write-интеграция с настоящим backend/выделенным worker не выполнялась; synthetic MockWebServer не доказывает серверные ACL/расчёты/транзакции.
