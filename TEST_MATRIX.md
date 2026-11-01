# Матрица приёмки

Статусы устанавливает супервизор после фактического запуска. Fixtures не означают живую серверную приёмку.

| № | Сценарий | Уровень | Статус |
|---|---|---|---|
|1|Auth success/error, restart/update cookies, logout late response|data + instrumentation|JVM tests passed; Keystore instrumentation pending|
|2|No password persistence/logged cookies, actor/server isolation|data + review|Не запускался|
|3|Workshop42 после main7, missing/inactive/403|domain/data|Не запускался|
|4|Task scope/paging/filter race/unknown enums|data/VM|Не запускался|
|5|Start, worker race, stale version, permissions|data/VM|Не запускался|
|6|Receive full/partial/multiple step delta|domain/data|Не запускался|
|7|Receive10→4→5 final shortage1 без loss approval|data/VM|Не запускался|
|8|Zero final/reason, partial, excess/foreign line|domain/data|Не запускался|
|9|Frozen sheets/current catalog/baseqty0|data|Не запускался|
|10|Dispatch partial/remaining/retry/no main credit|data/VM|Не запускался|
|11|Fixed recipe preview20/10→3/6, no materials in command|MockWebServer|Не запускался|
|12|Cumulative rounding partial/final — server fixtures only|MockWebServer|Не запускался|
|13|Prototype q1>1, preview/progress atomic ACK|MockWebServer/VM|Не запускался|
|14|After prototype by_recipe no material entry|VM/UI|Не запускался|
|15|Concurrent prototype409/lost response exact prototype retry|data|Не запускался|
|16|Manual override forbidden/no v1 fallback|data/VM|Не запускался|
|17|Sheet batch/unknown reserve/missing/preview errors|data/VM|Не запускался|
|18|Manufacture500 pending/no optimistic stock|data/VM|Не запускался|
|19|BigDecimal string/Long/formatter separation|unit|Transport string/Long passed; formatter pending|
|20|Strict per-command fields|serialization|Не запускался|
|21|Stock/search/pages/negative/unknown/catalog separation|data/VM|Не запускался|
|22|Persist before network/Room failure prevents send|Room/dispatcher|Room/Robolectric/MockWebServer passed; physical process kill pending|
|23|Concurrent VM events one pending command|Room/dispatcher|Room/Robolectric/MockWebServer passed; physical process kill pending|
|24|Accepted response lost/restart/raw same token and body|Room/MockWebServer|Room/Robolectric/MockWebServer passed; physical process kill pending|
|25|ACK local failure honest state/safe retry|Room/dispatcher|Room/Robolectric/MockWebServer passed; physical process kill pending|
|26|401/403 preserves unknown pending/actor|data|Room/Robolectric/MockWebServer passed; physical process kill pending|
|27|Rotation/recomposition/back no auto write|Compose|Не запускался|
|28|History over50 paged IDs|data/VM|Не запускался|
|29|Unsupported environment separate from empty/auth failure|data/VM|Не запускался|
|30|Login→list→detail→confirm→success/history+stock refresh|Compose emulator|Не запускался|

Живые write-проверки: не разрешены на общей базе, нужен отдельный fixture backend. Production v2 живая интеграция: внешний блокер, routes отсутствуют на дату аудита.
