package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.data.local.pending.PendingCommandEntity
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RawCommandResponse(val status: Int, val contentType: String?, val body: String)
interface RawCommandSender { suspend fun send(command: PendingCommandEntity, session: SessionSnapshot): RawCommandResponse }
class OkHttpCommandSender(private val factory: ApiFactory, private val current: CurrentSession) : RawCommandSender {
    override suspend fun send(command: PendingCommandEntity, session: SessionSnapshot): RawCommandResponse {
        check(current.matches(session)) { "Authenticated profile changed before send" }
        val url = command.baseUrl.toHttpUrl().resolve(command.path.removePrefix("/")) ?: error("Invalid command path")
        val request = Request.Builder().url(url).method(command.method, command.body.toRequestBody("application/json".toMediaType())).build()
        val call = factory.httpClient(session.generation, current::invalidate).newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use { value ->
                            val result = RawCommandResponse(value.code, value.header("Content-Type"), value.body?.string().orEmpty())
                            if (continuation.isActive) continuation.resume(result)
                        }
                    } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }
            })
        }
    }
}
