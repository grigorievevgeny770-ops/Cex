package com.logunovinc.workshopmanager.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logunovinc.workshopmanager.data.local.pending.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.production.*
import com.logunovinc.workshopmanager.domain.pending.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class ManufacturingRecoveryTest {
    @Test fun lostPrototypeReplyDatabaseReopenRetriesOriginalPrototypeAlthoughRecipeIsAlreadyReady() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "prototype-recovery-${UUID.randomUUID()}.db"
        var database = Room.databaseBuilder(context, CommandDatabase::class.java, name).allowMainThreadQueries().build()
        ProductionHarness(RoomCommandJournal(database)).use { h ->
            try {
                val task = h.task("prototype_task")
                val command = h.prepare(task, "2.0000", listOf(PrototypeMaterialInput(301, "4.0000")))
                val (preview, _) = h.preview(task, command, "prototype_preview_2_4")
                h.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
                val unknown = ExecuteManufacturingUseCase(h.repository)(task, command, preview, "First prototype") as CommandOutcome.Unresolved
                val first = h.server.takeRequest().body.readUtf8()
                database.close()
                database = Room.databaseBuilder(context, CommandDatabase::class.java, name).allowMainThreadQueries().build()
                h.rebuild(RoomCommandJournal(database))
                assertEquals(RecipeStatus.READY, h.task("prototype_ready_task").production!!.status)
                h.server.enqueue(h.reply(h.ack("prototype_ready_task", duplicate = true)))
                val result = h.dispatcher.retry(unknown.command.id) as CommandOutcome.Confirmed
                val repeated = h.server.takeRequest().body.readUtf8()
                assertEquals(first, repeated)
                val body = h.json.parseToJsonElement(repeated).jsonObject
                assertEquals("prototype", body["mode"]!!.jsonPrimitive.content)
                assertEquals(0L, body["expected_recipe_version"]!!.jsonPrimitive.long)
                assertTrue(body.containsKey("materials"))
                assertTrue(result.confirmation.duplicate)
                assertEquals(1, database.commands().observe(h.server.url("/").toString(), 42).first().size)
            } finally { database.close(); context.deleteDatabase(name) }
        }
    }
}
