package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.data.local.CookieStorage
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Request generation is bound around BridgeInterceptor's CookieJar callbacks. */
class PersistentSessionCookieJar(private val storage: CookieStorage, private val clock: () -> Long = System::currentTimeMillis) : CookieJar {
    @Serializable private data class StoredCookie(val origin: String, val value: String)
    private var cookies: List<Pair<String, Cookie>> = emptyList()
    private var initialized = false
    private var epoch = 0L
    private val requestEpoch = ThreadLocal<Long>()
    @Synchronized fun commitIfCurrent(generation: Long, block: () -> Unit): Boolean {
        if (generation != epoch) return false
        block()
        return true
    }
    @Synchronized fun generation(): Long = epoch
    @Synchronized fun isCurrent(generation: Long): Boolean = epoch == generation
    @Synchronized fun restore() {
        if (initialized) return
        cookies = storage.read()?.let { Json.decodeFromString<List<StoredCookie>>(it) }
            .orEmpty().mapNotNull { record -> Cookie.parse(record.origin.toHttpUrl(), record.value)?.let { record.origin to it } }
            .filter { it.second.expiresAt > clock() }
        initialized = true
    }
    @Synchronized fun invalidate(onInvalidated: () -> Unit = {}): Long {
        epoch++
        cookies = emptyList()
        initialized = true
        try { persist(emptyList()) } finally { onInvalidated() }
        return epoch
    }
    @Synchronized fun header(url: HttpUrl): String = matching(url).joinToString("; ") { "${it.name}=${it.value}" }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (requestEpoch.get()?.let { it != epoch } == true) emptyList() else matching(url)
    private fun matching(url: HttpUrl): List<Cookie> = cookies.map { it.second }.filter { it.expiresAt > clock() && it.matches(url) }
    @Synchronized override fun saveFromResponse(url: HttpUrl, incoming: List<Cookie>) {
        if (requestEpoch.get()?.let { it != epoch } == true) return
        val keys = incoming.map { Triple(it.name, it.domain, it.path) }.toSet()
        val updated = cookies.filterNot { Triple(it.second.name, it.second.domain, it.second.path) in keys }
            .filter { it.second.expiresAt > clock() } + incoming.filter { it.expiresAt > clock() }.map { url.toString() to it }
        persist(updated)
        cookies = updated
    }
    private fun persist(value: List<Pair<String, Cookie>>) {
        try { storage.write(Json.encodeToString(value.map { StoredCookie(it.first, it.second.toString()) })) }
        catch (failure: Exception) { throw IOException("Не удалось сохранить защищённую сессию", failure) }
    }
    fun <T> withGeneration(generation: Long, block: () -> T): T {
        requestEpoch.set(generation)
        return try { block() } finally { requestEpoch.remove() }
    }
}

class SessionGenerationInterceptor(
    private val jar: PersistentSessionCookieJar,
    private val generation: Long,
    private val unauthorized: (Long) -> Unit,
    private val allowStale: Boolean = false,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = jar.withGeneration(generation) {
        if (!allowStale && !jar.isCurrent(generation)) throw IOException("Сессия изменилась до отправки запроса")
        val response = chain.proceed(chain.request())
        if (response.code == 401) unauthorized(generation)
        response
    }
}
