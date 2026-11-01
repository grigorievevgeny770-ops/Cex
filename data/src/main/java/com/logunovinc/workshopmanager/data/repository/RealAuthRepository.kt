package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.local.EnvironmentSettings
import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.auth.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

class RealAuthRepository(
    private val settings: EnvironmentSettings,
    private val factory: ApiFactory,
    private val errors: ApiErrorMapper,
    private val allowLocalHttp: Boolean,
    defaultUrl: String,
) : AuthRepository {
    private val mutableSession = MutableStateFlow(SessionState())
    override val session = mutableSession.asStateFlow()
    private val mutableEnvironment = MutableStateFlow(defaultUrl)
    override val environment = mutableEnvironment.asStateFlow()
    private val initializeMutex = Mutex()
    private val transitionLock = Any()
    private var initialized = false
    private val jar get() = factory.cookieJar
    private data class Context(val baseUrl: String, val generation: Long)
    private fun context() = synchronized(transitionLock) { Context(environment.value, jar.generation()) }
    private fun commit(context: Context, block: () -> Unit): Boolean = synchronized(transitionLock) {
        jar.commitIfCurrent(context.generation, block)
    }
    private suspend fun initialize() = initializeMutex.withLock {
        if (!initialized) {
            val saved = CanonicalBaseUrl.parse(settings.read()) ?: error("Invalid stored environment")
            requireTransport(saved)
            synchronized(transitionLock) {
                mutableEnvironment.value = saved
                jar.restore()
                initialized = true
            }
        }
    }
    private fun requireTransport(url: String) {
        val address = url.toHttpUrl()
        require(address.isHttps || (allowLocalHttp && address.host in setOf("127.0.0.1", "10.0.2.2", "localhost"))) {
            "HTTP разрешён только локально в debug; используйте HTTPS"
        }
    }
    private fun remote(context: Context, allowStale: Boolean = false): AuthRemoteDataSource = AuthRemoteDataSource(
        factory.retrofit(context.baseUrl, context.generation, ::unauthorized, allowStale).create(AuthApi::class.java))

    fun unauthorized(generation: Long) = synchronized(transitionLock) {
        jar.commitIfCurrent(generation) {
            jar.invalidate { mutableSession.value = SessionState(restoring = false, message = "Сессия истекла. Войдите снова") }
        }
        Unit
    }
    override suspend fun restore(): Outcome<Actor> = withContext(Dispatchers.IO) {
        var requestContext: Context? = null
        try {
            initialize()
            val snapshot = context().also { requestContext = it }
            verify(remote(snapshot), snapshot)
        } catch (error: Exception) {
            val failure = Outcome.Failure(errors.throwable(error))
            requestContext?.let { commit(it) { mutableSession.value = SessionState(restoring = false, message = failure.error.message) } }
                ?: synchronized(transitionLock) {
                    if (mutableSession.value.restoring) mutableSession.value = SessionState(restoring = false, message = failure.error.message)
                }
            failure
        }
    }
    override suspend fun login(login: String, password: String): Outcome<Actor> = withContext(Dispatchers.IO) {
        try {
            initialize()
            val snapshot = synchronized(transitionLock) {
                val generation = jar.invalidate { mutableSession.value = SessionState(restoring = false) }
                Context(environment.value, generation)
            }
            val source = remote(snapshot)
            val response = source.login(login, password)
            if (!jar.isCurrent(snapshot.generation) && response.code() != 401) return@withContext stale()
            if (!response.isSuccessful) return@withContext Outcome.Failure(errors.response(response, missingRoute = true))
            if (response.body() == null) return@withContext incompatible()
            verify(source, snapshot)
        } catch (error: Exception) { Outcome.Failure(errors.throwable(error)) }
    }
    private suspend fun verify(source: AuthRemoteDataSource, snapshot: Context): Outcome<Actor> {
        val response = source.me()
        if (!jar.isCurrent(snapshot.generation) && response.code() != 401) return stale()
        if (!response.isSuccessful) {
            val error = errors.response(response, missingRoute = true)
            commit(snapshot) { mutableSession.value = SessionState(restoring = false, message = error.message) }
            return Outcome.Failure(error)
        }
        val body = response.body() ?: return incompatible()
        if (body.id <= 0) return incompatible()
        val actor = Actor(body.id, body.login, body.role)
        return if (commit(snapshot) { mutableSession.value = SessionState(restoring = false, actor = actor) }) Outcome.Success(actor) else stale()
    }
    override suspend fun logout(): Outcome<Boolean> = withContext(Dispatchers.IO) {
        try {
            initialize()
            val (snapshot, cookie, loggedOutContext) = synchronized(transitionLock) {
                val before = Context(environment.value, jar.generation())
                val oldCookie = jar.header(before.baseUrl.toHttpUrl())
                val generation = jar.invalidate { mutableSession.value = SessionState(restoring = false) }
                Triple(before, oldCookie, Context(before.baseUrl, generation))
            }
            val confirmed = try { remote(snapshot, allowStale = true).logout(cookie).let { it.isSuccessful && it.body()?.ok == true } }
                catch (error: Exception) { errors.throwable(error); false }
            commit(loggedOutContext) {
                mutableSession.value = SessionState(restoring = false, message = if (confirmed) "Вы вышли из аккаунта" else "Локальный выход выполнен; отзыв сессии сервером не подтверждён")
            }
            Outcome.Success(confirmed)
        } catch (error: Exception) { Outcome.Failure(errors.throwable(error)) }
    }
    override suspend fun changeEnvironment(baseUrl: String): Outcome<Unit> = withContext(Dispatchers.IO) {
        try {
            initialize()
            val canonical = CanonicalBaseUrl.parse(baseUrl) ?: return@withContext Outcome.Failure(AppError(ErrorKind.VALIDATION, "Некорректный адрес сервера"))
            try { requireTransport(canonical) } catch (error: IllegalArgumentException) {
                return@withContext Outcome.Failure(AppError(ErrorKind.VALIDATION, error.message ?: "Используйте HTTPS"))
            }
            if (canonical != environment.value) {
                settings.write(canonical)
                synchronized(transitionLock) {
                    jar.invalidate {
                        mutableSession.value = SessionState(restoring = false)
                        mutableEnvironment.value = canonical
                    }
                }
            }
            Outcome.Success(Unit)
        } catch (error: Exception) { Outcome.Failure(errors.throwable(error)) }
    }
    private fun stale(): Outcome.Failure = Outcome.Failure(AppError(ErrorKind.CONFLICT, "Сессия или окружение изменились; ответ пропущен"))
    private fun incompatible(): Outcome.Failure = Outcome.Failure(AppError(ErrorKind.INCOMPATIBLE_SERVER, "Сервер ещё не поддерживает приложение"))
}
