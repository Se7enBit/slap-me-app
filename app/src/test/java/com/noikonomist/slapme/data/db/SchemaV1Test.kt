package com.noikonomist.slapme.data.db

import android.app.Application
import com.noikonomist.slapme.data.inMemoryDatabase
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema v1 is the first shipped schema, so there are no migrations yet. These checks make any change
 * to it fail until the version is bumped and a migration is added.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SchemaV1Test {

    private val schemaFile = File("schemas/com.noikonomist.slapme.data.db.SlapMeDatabase/1.json")
    private val schema by lazy { JSONObject(schemaFile.readText()).getJSONObject("database") }

    private val expectedColumns = mapOf(
        "schedule_block" to listOf("id", "daysMask", "startMinute", "endMinute", "enabled"),
        "focus_session" to listOf("id", "start", "plannedEnd", "actualEnd"),
        "slap_event" to listOf("id", "timestamp", "packageName", "level", "reason"),
        "override_event" to listOf("id", "timestamp", "type", "durationSeconds"),
        "daily_usage" to listOf("date", "countedSeconds", "longestFocusSeconds"),
    )

    @Test
    fun exportedSchemaIsVersion1() {
        assertTrue("missing ${schemaFile.absolutePath}", schemaFile.exists())
        assertEquals(1, schema.getInt("version"))
        assertEquals(SlapMeDatabase.VERSION, schema.getInt("version"))
    }

    @Test
    fun exportedSchemaHasTheTablesAndColumnsFromClaudeMd() {
        val entities = schema.getJSONArray("entities")
        val actual = (0 until entities.length()).associate { i ->
            val entity = entities.getJSONObject(i)
            val fields = entity.getJSONArray("fields")
            entity.getString("tableName") to (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
        }
        assertEquals(expectedColumns, actual)
    }

    @Test
    fun compiledDatabaseMatchesTheExportedSchema() {
        val db = inMemoryDatabase()
        try {
            val hash = db.openHelper.readableDatabase.query("SELECT identity_hash FROM room_master_table").use {
                it.moveToFirst()
                it.getString(0)
            }
            assertEquals(schema.getString("identityHash"), hash)
        } finally {
            db.close()
        }
    }
}
