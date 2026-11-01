package com.logunovinc.workshopmanager.testing

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.logunovinc.workshopmanager.data.local.EncryptedCookieStorage
import com.logunovinc.workshopmanager.data.local.pending.*
import com.logunovinc.workshopmanager.data.remote.PersistentSessionCookieJar
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreAndRoomDeviceTest {
    @Test fun actualKeystoreEncryptsPersistentCookiesAndRejectsLateLogoutResponse() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(base.noBackupFilesDir, "connected-keystore-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = directory }
        val origin = "https://synthetic.invalid/auth/me".toHttpUrl()
        val storage = EncryptedCookieStorage(context)
        val jar = PersistentSessionCookieJar(storage).apply { restore() }
        jar.saveFromResponse(origin, listOf(Cookie.parse(origin, "session=synthetic-secret; Path=/auth; HttpOnly; Secure")!!))
        val encrypted = File(directory, "session.cookies").readBytes()
        assertFalse(encrypted.toString(Charsets.UTF_8).contains("synthetic-secret"))
        assertTrue(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias("workshop.session.aes.v1"))
        val restored = PersistentSessionCookieJar(EncryptedCookieStorage(context)).apply { restore() }
        assertEquals("synthetic-secret", restored.loadForRequest(origin).single().value)
        restored.saveFromResponse(origin, listOf(Cookie.parse(origin, "session=synthetic-updated; Path=/auth; HttpOnly; Secure")!!))
        assertEquals("synthetic-updated", PersistentSessionCookieJar(EncryptedCookieStorage(context)).apply { restore() }.loadForRequest(origin).single().value)
        val oldGeneration = restored.generation()
        restored.invalidate()
        restored.withGeneration(oldGeneration) { restored.saveFromResponse(origin, listOf(Cookie.parse(origin, "session=late; Path=/auth; Secure")!!)) }
        assertTrue(PersistentSessionCookieJar(EncryptedCookieStorage(context)).apply { restore() }.loadForRequest(origin).isEmpty())
        directory.deleteRecursively()
    }
    @Test fun roomRequestBytesSurviveActualDeviceDatabaseCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "connected-room-${UUID.randomUUID()}.db"
        var database = Room.databaseBuilder(context, CommandDatabase::class.java, name).build()
        val body = """{"expected_version":3,"client_token":"synthetic-token","lines":[{"task_line_id":1001,"qty":"4.0000"}]}""".toByteArray()
        try {
            val saved = PendingCommandEntity("synthetic-command", "https://synthetic.invalid/", 42, 101, "RECEIVE_GOODS", "synthetic-token", "POST", "api/workshop/tasks/101/executions", body, "synthetic-fingerprint", "{}", 1, "IN_FLIGHT")
            RoomCommandJournal(database).prepare(saved)
            database.close()
            database = Room.databaseBuilder(context, CommandDatabase::class.java, name).build()
            val restored = RoomCommandJournal(database).get(saved.id)!!
            assertArrayEquals(body, restored.body)
            assertEquals(saved.clientToken, restored.clientToken)
            assertEquals(42L, restored.actorId)
            assertNotNull(database.commands().active(saved.baseUrl, 42, 101))
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
