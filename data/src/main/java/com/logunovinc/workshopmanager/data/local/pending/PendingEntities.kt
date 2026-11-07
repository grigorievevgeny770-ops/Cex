package com.logunovinc.workshopmanager.data.local.pending

import androidx.room.*

@Entity(tableName = "pending_commands", indices = [Index(value = ["baseUrl", "actorId", "taskId", "fingerprint"])])
data class PendingCommandEntity(
    @PrimaryKey val id: String,
    val baseUrl: String, val actorId: Long, val taskId: Long,
    val action: String, val clientToken: String,
    val method: String, val path: String, val body: ByteArray,
    val fingerprint: String, val summary: String, val createdAtMillis: Long,
    val state: String, val attempts: Int = 0, val lastAttemptAtMillis: Long? = null,
    val uncertain: Boolean = false, val lastError: String? = null,
    val ackBody: String? = null, val executionId: Long? = null,
    @ColumnInfo(defaultValue = "'task'") val scope: String = "task",
)

/** A separate PK slot provides SQLite-enforced one unresolved command per profile/task. */
@Entity(tableName = "active_commands", primaryKeys = ["baseUrl", "actorId", "taskId", "scope"],
    foreignKeys = [ForeignKey(entity = PendingCommandEntity::class, parentColumns = ["id"], childColumns = ["commandId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["commandId"], unique = true)])
data class ActiveCommandEntity(val baseUrl: String, val actorId: Long, val taskId: Long, val commandId: String, @ColumnInfo(defaultValue = "'task'") val scope: String = "task")

@Dao
interface PendingCommandDao {
    @Query("SELECT * FROM pending_commands WHERE id = :id") suspend fun get(id: String): PendingCommandEntity?
    @Query("SELECT * FROM pending_commands WHERE baseUrl = :baseUrl AND actorId = :actorId ORDER BY createdAtMillis DESC")
    fun observe(baseUrl: String, actorId: Long): kotlinx.coroutines.flow.Flow<List<PendingCommandEntity>>
    @Query("SELECT * FROM pending_commands WHERE baseUrl = :baseUrl AND actorId = :actorId AND taskId = :taskId AND scope = :scope AND fingerprint = :fingerprint AND state != 'REJECTED' AND (scope != 'sheet_create' OR state != 'ACKNOWLEDGED') LIMIT 1")
    suspend fun byFingerprint(baseUrl: String, actorId: Long, taskId: Long, fingerprint: String, scope: String = "task"): PendingCommandEntity?
    @Query("SELECT pending_commands.* FROM pending_commands INNER JOIN active_commands ON pending_commands.id = active_commands.commandId WHERE active_commands.baseUrl = :baseUrl AND active_commands.actorId = :actorId AND active_commands.taskId = :taskId AND active_commands.scope = :scope")
    suspend fun active(baseUrl: String, actorId: Long, taskId: Long, scope: String = "task"): PendingCommandEntity?
    @Insert suspend fun insert(command: PendingCommandEntity)
    @Insert suspend fun insertSlot(slot: ActiveCommandEntity)
    @Update suspend fun update(command: PendingCommandEntity)
    @Query("DELETE FROM active_commands WHERE commandId = :id") suspend fun release(id: String)
}

@Database(entities = [PendingCommandEntity::class, ActiveCommandEntity::class], version = 2, exportSchema = true)
abstract class CommandDatabase : RoomDatabase() { abstract fun commands(): PendingCommandDao }
