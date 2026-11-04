package com.logunovinc.workshopmanager.domain.pending

import com.logunovinc.workshopmanager.domain.*
import java.time.Instant
import kotlinx.coroutines.flow.Flow

enum class PendingState { PREPARED, IN_FLIGHT, UNKNOWN, NEEDS_REVIEW, ACKNOWLEDGED, REJECTED }
/** Exact human-readable quantities, line names and comment; never a raw HTTP payload. */
data class CommandSummary(val title: String, val lines: List<String>, val comment: String = "")
data class CommandConfirmation(val commandId: String, val taskId: Long, val taskVersion: Long, val executionId: Long?, val duplicate: Boolean, val terminal: Boolean = false)
data class PendingCommand(
    val id: String, val taskId: Long, val action: String, val summary: CommandSummary,
    val state: PendingState, val createdAt: Instant, val attempts: Int,
    val error: AppError? = null, val confirmation: CommandConfirmation? = null,
    val knownExecutionId: Long? = null,
)
sealed interface CommandOutcome {
    data class Confirmed(val confirmation: CommandConfirmation) : CommandOutcome
    data class Unresolved(val command: PendingCommand, val error: AppError) : CommandOutcome
    data class Rejected(val command: PendingCommand?, val error: AppError) : CommandOutcome
}
interface PendingCommandRepository {
    /** Only the current canonical server and verified actor's private journal is observable. */
    fun observe(): Flow<List<PendingCommand>>
    suspend fun retry(commandId: String): CommandOutcome
}
