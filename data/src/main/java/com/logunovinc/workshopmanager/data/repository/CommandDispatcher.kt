package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.local.pending.*
import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.data.remote.dto.CommandAckDto
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.pending.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

/** One registry is shared by all dispatcher instances in the process; SQLite slots survive processes. */
class CommandMutexRegistry {
    private val locks = ConcurrentHashMap<String, Mutex>()
    suspend fun <T> withTask(server: String, actor: Long, task: Long, block: suspend () -> T): T =
        locks.getOrPut("$server|$actor|$task") { Mutex() }.withLock { block() }
    companion object { val process = CommandMutexRegistry() }
}

class CommandDispatcher(
    private val journal: CommandJournal,
    private val sender: RawCommandSender,
    private val current: CurrentSession,
    private val json: Json,
    private val errors: ApiErrorMapper,
    private val mapper: JournalMapper,
    private val locks: CommandMutexRegistry = CommandMutexRegistry.process,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Called only by data repositories; serializes once before the atomic durable prepare. */
    suspend fun submit(taskId: Long, action: String, method: String, path: String, summary: CommandSummary, body: (String) -> ByteArray): CommandOutcome {
        val profile = current.snapshot() ?: return unauthenticated()
        if (taskId <= 0 || method != "POST" || !path.matches(Regex("/?api/workshop/(v2/)?tasks/$taskId/(executions|manufacturing-executions)")))
            return CommandOutcome.Rejected(null, AppError(ErrorKind.VALIDATION, "Недопустимая складская команда"))
        return locks.withTask(profile.baseUrl, profile.actorId, taskId) {
            if (!current.matches(profile)) return@withTask unauthenticated()
            try {
                val token = UUID.randomUUID().toString()
                val exactBody = body(token)
                val payload = json.parseToJsonElement(exactBody.toString(Charsets.UTF_8)).jsonObject
                require(payload["client_token"]?.jsonPrimitive?.content == token) { "Missing command identity" }
                val fingerprintBody = JsonObject(payload.filterKeys { it != "client_token" }).toString()
                val fingerprint = MessageDigest.getInstance("SHA-256").digest((method + "\n" + path + "\n" + fingerprintBody).toByteArray()).joinToString("") { "%02x".format(it) }
                val entity = PendingCommandEntity(UUID.randomUUID().toString(), profile.baseUrl, profile.actorId, taskId, action, token,
                    method, path, exactBody.copyOf(), fingerprint, mapper.summary(summary), clock(), PendingState.PREPARED.name)
                when (val result = journal.prepare(entity)) {
                    is PrepareResult.Created -> send(result.command, profile)
                    is PrepareResult.Existing -> existing(result.command)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { CommandOutcome.Rejected(null, AppError(ErrorKind.LOCAL_STORAGE, "Команда не отправлена: не удалось записать её в защищённый журнал")) }
        }
    }
    suspend fun retry(commandId: String): CommandOutcome {
        val profile = current.snapshot() ?: return unauthenticated()
        val command = try { journal.get(commandId) } catch (error: Exception) { return CommandOutcome.Rejected(null, errors.throwable(error)) }
            ?: return CommandOutcome.Rejected(null, AppError(ErrorKind.NOT_FOUND, "Команда не найдена в журнале"))
        if (command.baseUrl != profile.baseUrl || command.actorId != profile.actorId) return unauthenticated()
        return locks.withTask(profile.baseUrl, profile.actorId, command.taskId) {
            val latest = journal.get(commandId) ?: return@withTask CommandOutcome.Rejected(null, AppError(ErrorKind.NOT_FOUND, "Команда не найдена"))
            if (!current.matches(profile)) return@withTask unauthenticated()
            if (latest.state == PendingState.ACKNOWLEDGED.name || latest.state == PendingState.REJECTED.name) existing(latest)
            else send(latest, profile)
        }
    }
    suspend fun acknowledgedBody(commandId: String): String? {
        val profile = current.snapshot() ?: return null
        val command = journal.get(commandId) ?: return null
        return command.ackBody.takeIf { command.baseUrl == profile.baseUrl && command.actorId == profile.actorId && current.matches(profile) }
    }
    private fun existing(command: PendingCommandEntity): CommandOutcome = mapper.confirmation(command)?.let { CommandOutcome.Confirmed(it) }
        ?: mapper.decodeError(command.lastError)?.let { error ->
            if (command.state == PendingState.REJECTED.name) CommandOutcome.Rejected(mapper.domain(command), error)
            else CommandOutcome.Unresolved(mapper.domain(command), error)
        }
        ?: CommandOutcome.Unresolved(mapper.domain(command), AppError(ErrorKind.CONFLICT, "Для задания уже сохранён запрос. Проверьте его исход и повторяйте только исходную команду"))

    private suspend fun send(saved: PendingCommandEntity, profile: SessionSnapshot): CommandOutcome {
        val priorUnknown = saved.uncertain || saved.state in setOf(PendingState.IN_FLIGHT.name, PendingState.UNKNOWN.name, PendingState.NEEDS_REVIEW.name)
        val attempt = saved.copy(state = PendingState.IN_FLIGHT.name, attempts = saved.attempts + 1, lastAttemptAtMillis = clock(), uncertain = priorUnknown)
        // If this write fails, no HTTP request is allowed.
        try { journal.update(attempt) } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            val failure = AppError(ErrorKind.LOCAL_STORAGE, "Запрос не отправлен: журнал недоступен; сохранённую команду можно повторить позже")
            return if (priorUnknown) CommandOutcome.Unresolved(mapper.domain(saved), failure) else CommandOutcome.Rejected(mapper.domain(saved), failure)
        }
        try {
            if (!current.matches(profile)) return recordUncertain(attempt, AppError(ErrorKind.AUTHENTICATION, "Войдите тем же пользователем на тот же сервер для восстановления"), PendingState.NEEDS_REVIEW)
            val response = sender.send(attempt, profile)
            if (response.status == 200) {
                val ack = json.decodeFromString<CommandAckDto>(response.body)
                require(ack.task.id == saved.taskId && ack.result.id == saved.taskId && ack.task.version > 0)
                val acknowledged = attempt.copy(state = PendingState.ACKNOWLEDGED.name, ackBody = response.body, executionId = ack.result.executionId, lastError = null)
                try { journal.update(acknowledged, releaseSlot = true) }
                catch (error: Exception) {
                    return recordUncertain(attempt.copy(executionId = ack.result.executionId), AppError(ErrorKind.LOCAL_STORAGE, "Сервер подтвердил действие, но квитанция не сохранена. Повторите исходный запрос для восстановления"), PendingState.UNKNOWN)
                }
                return CommandOutcome.Confirmed(mapper.confirmation(acknowledged)!!)
            }
            val retrofitResponse = retrofit2.Response.error<Unit>(response.status, response.body.toResponseBody((response.contentType ?: "application/json").toMediaType()))
            val error = errors.response(retrofitResponse)
            val deterministic = response.status in setOf(400, 401, 403, 404, 409, 422)
            if (deterministic && !priorUnknown) {
                val rejected = attempt.copy(state = PendingState.REJECTED.name, lastError = mapper.error(error))
                journal.update(rejected, releaseSlot = true)
                return CommandOutcome.Rejected(mapper.domain(rejected), error)
            }
            return recordUncertain(attempt, error, if (deterministic) PendingState.NEEDS_REVIEW else PendingState.UNKNOWN)
        } catch (error: CancellationException) {
            withContext(NonCancellable) { recordUncertain(attempt, AppError(ErrorKind.NETWORK, "Запрос был прерван; исход неизвестен"), PendingState.UNKNOWN) }
            throw error
        } catch (error: Exception) {
            return recordUncertain(attempt, errors.throwable(error), PendingState.UNKNOWN)
        }
    }
    private suspend fun recordUncertain(command: PendingCommandEntity, error: AppError, state: PendingState): CommandOutcome {
        val uncertain = command.copy(state = state.name, uncertain = true, lastError = mapper.error(error))
        try { journal.update(uncertain) } catch (failure: Exception) {
            // Durable IN_FLIGHT still carries the exact request and is read as UNKNOWN after restart.
        }
        return CommandOutcome.Unresolved(mapper.domain(uncertain), error)
    }
    private fun unauthenticated() = CommandOutcome.Rejected(null, AppError(ErrorKind.AUTHENTICATION, "Войдите тем же пользователем на тот же сервер; чужую команду отправлять нельзя"))
}
