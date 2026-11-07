package com.logunovinc.workshopmanager.data.local.pending

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object CommandMigrations {
    val V1_TO_V2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pending_commands ADD COLUMN scope TEXT NOT NULL DEFAULT 'task'")
            db.execSQL("UPDATE pending_commands SET scope = CASE WHEN action LIKE 'REQUEST_%' THEN 'request' WHEN action = 'SHEET_REQUEST' THEN 'sheet_create' WHEN action = 'SHEET_RECEIVE' THEN 'sheet' ELSE 'task' END")
            db.execSQL("CREATE TABLE active_commands_v2 (baseUrl TEXT NOT NULL, actorId INTEGER NOT NULL, taskId INTEGER NOT NULL, commandId TEXT NOT NULL, scope TEXT NOT NULL DEFAULT 'task', PRIMARY KEY(baseUrl, actorId, taskId, scope), FOREIGN KEY(commandId) REFERENCES pending_commands(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("INSERT INTO active_commands_v2 SELECT a.baseUrl,a.actorId,a.taskId,a.commandId,p.scope FROM active_commands a JOIN pending_commands p ON p.id=a.commandId")
            db.execSQL("DROP TABLE active_commands")
            db.execSQL("ALTER TABLE active_commands_v2 RENAME TO active_commands")
            db.execSQL("CREATE UNIQUE INDEX index_active_commands_commandId ON active_commands(commandId)")
        }
    }
}
