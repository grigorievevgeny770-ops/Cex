package com.logunovinc.workshopmanager.data.local.pending

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.pending.*
import com.logunovinc.workshopmanager.data.remote.dto.CommandAckDto
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable private data class SummaryRecord(val title: String, val lines: List<String>, val comment: String)
@Serializable private data class ErrorRecord(val kind: String, val message: String, val code: String?, val status: Int?, val issues: List<String>)
class JournalMapper(private val json: Json) {
    fun summary(summary: CommandSummary): String = json.encodeToString(SummaryRecord(summary.title, summary.lines, summary.comment))
    fun error(error: AppError): String = json.encodeToString(ErrorRecord(error.kind.name, error.message, error.code, error.httpStatus, error.fieldIssues))
    fun decodeError(value: String?): AppError? = value?.let {
        json.decodeFromString<ErrorRecord>(it).let { record -> AppError(ErrorKind.valueOf(record.kind), record.message, record.code, record.status, record.issues) }
    }
    fun confirmation(command: PendingCommandEntity): CommandConfirmation? = command.ackBody?.let { body ->
        val ack = json.decodeFromString<CommandAckDto>(body)
        val snapshot = ack.snapshot ?: return null
        CommandConfirmation(command.id, snapshot.id, snapshot.version, ack.result.executionId, ack.result.duplicate,
            terminal = snapshot.isCompleted == true || snapshot.isClosedIncomplete ||
                (snapshot.isCompleted == null && snapshot.isCancelled == null && snapshot.status == "completed") ||
                snapshot.status in setOf("handed_to_warehouse", "cancelled", "received"))
    }
    fun domain(command: PendingCommandEntity): PendingCommand {
        val summary = json.decodeFromString<SummaryRecord>(command.summary)
        val storedState = PendingState.valueOf(command.state)
        // IN_FLIGHT is conservatively unknown to any new subscriber or process; never auto-replayed.
        val state = if (storedState == PendingState.IN_FLIGHT) PendingState.UNKNOWN else storedState
        return PendingCommand(command.id, command.taskId, command.action, CommandSummary(summary.title, summary.lines, summary.comment),
            state, Instant.ofEpochMilli(command.createdAtMillis), command.attempts, decodeError(command.lastError), confirmation(command), command.executionId)
    }
}
