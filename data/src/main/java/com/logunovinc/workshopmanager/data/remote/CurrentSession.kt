package com.logunovinc.workshopmanager.data.remote

import kotlinx.coroutines.flow.Flow

/** Internal capability bound to both verified actor and the current cookie generation. */
data class SessionSnapshot(val baseUrl: String, val actorId: Long, val generation: Long)
interface CurrentSession {
    val profiles: Flow<SessionSnapshot?>
    fun snapshot(): SessionSnapshot?
    fun matches(snapshot: SessionSnapshot): Boolean
    fun invalidate(generation: Long)
}
