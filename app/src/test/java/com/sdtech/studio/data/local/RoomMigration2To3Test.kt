package com.sdtech.studio.data.local

import android.content.ContentResolver
import android.content.ContentValues
import android.database.CharArrayBuffer
import android.database.ContentObserver
import android.database.Cursor
import android.database.DataSetObserver
import android.database.sqlite.SQLiteTransactionListener
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Pair
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import com.sdtech.studio.video.SceneBackgroundType
import com.sdtech.studio.video.SceneTextStyle
import com.sdtech.studio.video.SceneTransition
import com.sdtech.studio.video.SceneType
import com.sdtech.studio.video.domain.ProjectDocumentMigrator
import com.sdtech.studio.video.domain.toScenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RoomMigration2To3Test {

    private data class ProjectRow(
        val id: String,
        val type: String,
        val title: String,
        val ratio: String,
        val status: String,
        var payloadJson: String,
        val createdAt: Long,
        val updatedAt: Long,
        val thumbnailPath: String? = null,
        val caption: String = "",
        var documentVersion: Int = 1
    )

    private class FakeSupportSQLiteDatabase(
        val rows: MutableList<ProjectRow>
    ) : SupportSQLiteDatabase {
        val executedSql = mutableListOf<String>()
        var documentVersionColumnAdded = false

        override fun execSQL(sql: String) {
            executedSql.add(sql)
            if (sql.contains("ALTER TABLE projects ADD COLUMN documentVersion", ignoreCase = true)) {
                documentVersionColumnAdded = true
            }
        }

        override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
            executedSql.add("$sql [${bindArgs.joinToString()}]")
            if (sql.startsWith("UPDATE projects SET payloadJson = ?, documentVersion = ? WHERE id = ?", ignoreCase = true)) {
                val newPayload = bindArgs[0] as String
                val newVersion = (bindArgs[1] as Number).toInt()
                val targetId = bindArgs[2] as String
                rows.firstOrNull { it.id == targetId }?.apply {
                    payloadJson = newPayload
                    documentVersion = newVersion
                }
            }
        }

        override fun query(query: String): Cursor {
            executedSql.add(query)
            return ProjectRowsCursor(rows.toList())
        }

        override fun compileStatement(sql: String): SupportSQLiteStatement = throw UnsupportedOperationException()
        override fun beginTransaction() = Unit
        override fun beginTransactionNonExclusive() = Unit
        override fun beginTransactionWithListener(transactionListener: SQLiteTransactionListener) = Unit
        override fun beginTransactionWithListenerNonExclusive(transactionListener: SQLiteTransactionListener) = Unit
        override fun endTransaction() = Unit
        override fun setTransactionSuccessful() = Unit
        override fun inTransaction(): Boolean = false
        override val isDbLockedByCurrentThread: Boolean = false
        override fun yieldIfContendedSafely(): Boolean = false
        override fun yieldIfContendedSafely(sleepAfterYieldDelayMillis: Long): Boolean = false
        override var version: Int = 2
        override val maximumSize: Long = 0L
        override fun setMaximumSize(numBytes: Long): Long = 0L
        override var pageSize: Long = 4096L
        override fun query(query: String, bindArgs: Array<out Any?>): Cursor = query(query)
        override fun query(query: SupportSQLiteQuery): Cursor = query(query.sql)
        override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor = query(query.sql)
        override fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long = 0L
        override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int = 0
        override fun update(table: String, conflictAlgorithm: Int, values: ContentValues, whereClause: String?, whereArgs: Array<out Any?>?): Int = 0
        override fun needUpgrade(newVersion: Int): Boolean = true
        override val path: String = ":memory:"
        override val isOpen: Boolean = true
        override val isReadOnly: Boolean = false
        override fun setLocale(locale: Locale) = Unit
        override fun setMaxSqlCacheSize(cacheSize: Int) = Unit
        override fun setForeignKeyConstraintsEnabled(enabled: Boolean) = Unit
        override fun enableWriteAheadLogging(): Boolean = false
        override fun disableWriteAheadLogging() = Unit
        override val isWriteAheadLoggingEnabled: Boolean = false
        override val attachedDbs: List<Pair<String, String>>? = emptyList()
        override val isDatabaseIntegrityOk: Boolean = true
        override fun close() = Unit
    }

    private class ProjectRowsCursor(private val items: List<ProjectRow>) : Cursor {
        private var pos = -1
        private val cols = listOf("id", "type", "ratio", "payloadJson", "createdAt", "updatedAt")

        override fun getCount(): Int = items.size
        override fun getPosition(): Int = pos
        override fun move(offset: Int): Boolean = moveToPosition(pos + offset)
        override fun moveToPosition(position: Int): Boolean {
            pos = position
            return pos in items.indices
        }
        override fun moveToFirst(): Boolean = moveToPosition(0)
        override fun moveToLast(): Boolean = moveToPosition(items.lastIndex)
        override fun moveToNext(): Boolean = moveToPosition(pos + 1)
        override fun moveToPrevious(): Boolean = moveToPosition(pos - 1)
        override fun isFirst(): Boolean = pos == 0 && items.isNotEmpty()
        override fun isLast(): Boolean = pos == items.lastIndex && items.isNotEmpty()
        override fun isBeforeFirst(): Boolean = pos < 0
        override fun isAfterLast(): Boolean = pos >= items.size
        override fun getColumnIndex(columnName: String): Int = cols.indexOf(columnName)
        override fun getColumnIndexOrThrow(columnName: String): Int =
            cols.indexOf(columnName).takeIf { it >= 0 } ?: throw IllegalArgumentException("Missing column $columnName")
        override fun getColumnName(columnIndex: Int): String = cols[columnIndex]
        override fun getColumnNames(): Array<String> = cols.toTypedArray()
        override fun getColumnCount(): Int = cols.size
        override fun getBlob(columnIndex: Int): ByteArray = ByteArray(0)
        override fun getString(columnIndex: Int): String? {
            val row = items[pos]
            return when (cols[columnIndex]) {
                "id" -> row.id
                "type" -> row.type
                "ratio" -> row.ratio
                "payloadJson" -> row.payloadJson
                else -> null
            }
        }
        override fun copyStringToBuffer(columnIndex: Int, buffer: CharArrayBuffer?) = Unit
        override fun getShort(columnIndex: Int): Short = getLong(columnIndex).toShort()
        override fun getInt(columnIndex: Int): Int = getLong(columnIndex).toInt()
        override fun getLong(columnIndex: Int): Long {
            val row = items[pos]
            return when (cols[columnIndex]) {
                "createdAt" -> row.createdAt
                "updatedAt" -> row.updatedAt
                else -> 0L
            }
        }
        override fun getFloat(columnIndex: Int): Float = 0f
        override fun getDouble(columnIndex: Int): Double = 0.0
        override fun getType(columnIndex: Int): Int = Cursor.FIELD_TYPE_STRING
        override fun isNull(columnIndex: Int): Boolean = getString(columnIndex) == null
        override fun deactivate() = Unit
        override fun requery(): Boolean = false
        override fun close() = Unit
        override fun isClosed(): Boolean = false
        override fun registerContentObserver(observer: ContentObserver?) = Unit
        override fun unregisterContentObserver(observer: ContentObserver?) = Unit
        override fun registerDataSetObserver(observer: DataSetObserver?) = Unit
        override fun unregisterDataSetObserver(observer: DataSetObserver?) = Unit
        override fun setNotificationUri(cr: ContentResolver?, uri: Uri?) = Unit
        override fun getNotificationUri(): Uri? = null
        override fun getWantsAllOnMoveCalls(): Boolean = false
        override fun setExtras(extras: Bundle?) = Unit
        override fun getExtras(): Bundle = Bundle.EMPTY
        override fun respond(extras: Bundle?): Bundle = Bundle.EMPTY
    }

    @Test
    fun `MIGRATION_2_3 migrates v1 video projects to v2 TimelineProject and keeps old drafts intact`() {
        val v1VideoJson = """
            {"version":1,"scenes":[{"id":"s1","type":"INTRO","text":"Welcome to our launch","textStyle":"BIG_STATEMENT","backgroundType":"BRAND_COLOR","backgroundUri":null,"clipTrimStartMs":0,"clipTrimEndMs":null,"voiceFilePath":"/data/vo1.m4a","voiceOriginalFilePath":null,"normalizedLufs":-16.0,"normalizedTruePeakDbtp":-1.1,"normalizationGainDb":2.0,"voiceDurationMs":2600,"waveformLevels":[0.2,0.8],"manualDurationMs":null,"transition":"FADE"},{"id":"s2","type":"CONTENT","text":"Key feature walkthrough with video clip","textStyle":"HIGHLIGHTED_KEYWORD","backgroundType":"VIDEO_CLIP","backgroundUri":"content://media/external/video/55","clipTrimStartMs":500,"clipTrimEndMs":4500,"voiceFilePath":"/data/vo2.m4a","voiceOriginalFilePath":"/data/vo2_orig.m4a","normalizedLufs":-15.8,"normalizedTruePeakDbtp":-1.0,"normalizationGainDb":1.5,"voiceDurationMs":3500,"waveformLevels":[0.4,0.6,0.9],"manualDurationMs":4000,"transition":"FADE"},{"id":"s3","type":"OUTRO","text":"Contact us today","textStyle":"BULLET_POINT","backgroundType":"GRADIENT","backgroundUri":null,"clipTrimStartMs":0,"clipTrimEndMs":null,"voiceFilePath":null,"voiceOriginalFilePath":null,"normalizedLufs":null,"normalizedTruePeakDbtp":null,"normalizationGainDb":null,"voiceDurationMs":null,"waveformLevels":[],"manualDurationMs":4000,"transition":"NONE"}],"showIntro":true,"showOutro":true}
        """.trimIndent()

        val postPayloadJson = """{"templateId":"announcement","headline":"Big News"}"""

        val rows = mutableListOf(
            ProjectRow(
                id = "vid-draft-1",
                type = "VIDEO",
                title = "Product Launch Video",
                ratio = "9:16",
                status = "DRAFT",
                payloadJson = v1VideoJson,
                createdAt = 1000L,
                updatedAt = 2000L,
                caption = "Launch caption"
            ),
            ProjectRow(
                id = "post-draft-1",
                type = "POST",
                title = "Announcement Post",
                ratio = "1:1",
                status = "DRAFT",
                payloadJson = postPayloadJson,
                createdAt = 1500L,
                updatedAt = 2500L,
                caption = "Post caption"
            )
        )

        val db = FakeSupportSQLiteDatabase(rows)
        SDStudioDatabase.MIGRATION_2_3.migrate(db)

        assertTrue(db.documentVersionColumnAdded)

        val migratedVideoRow = rows.first { it.id == "vid-draft-1" }
        assertEquals("Product Launch Video", migratedVideoRow.title)
        assertEquals("9:16", migratedVideoRow.ratio)
        assertEquals("Launch caption", migratedVideoRow.caption)
        assertEquals(2, migratedVideoRow.documentVersion)
        assertTrue(ProjectDocumentMigrator.isV2Json(migratedVideoRow.payloadJson))
        assertFalse(ProjectDocumentMigrator.isV1Json(migratedVideoRow.payloadJson))

        val timeline = ProjectDocumentMigrator.decodeProjectDocument(
            projectId = "vid-draft-1",
            canvasRatio = migratedVideoRow.ratio,
            payloadJson = migratedVideoRow.payloadJson
        )
        assertEquals(2, timeline.version)
        assertEquals(30, timeline.fps)
        assertEquals("9:16", timeline.canvasRatio)
        assertEquals(3, timeline.sceneBlocks.size)

        // Verify old draft scenes reopen untouched with identical order, text, backgrounds, voice files, and durations
        val reopenedScenes = timeline.toScenes()
        assertEquals(3, reopenedScenes.size)
        assertEquals(listOf("s1", "s2", "s3"), reopenedScenes.map { it.id })

        assertEquals(SceneType.INTRO, reopenedScenes[0].type)
        assertEquals("Welcome to our launch", reopenedScenes[0].text)
        assertEquals("/data/vo1.m4a", reopenedScenes[0].voiceFilePath)
        assertEquals(2600L, reopenedScenes[0].voiceDurationMs)
        assertEquals(SceneTransition.FADE, reopenedScenes[0].transition)

        assertEquals(SceneType.CONTENT, reopenedScenes[1].type)
        assertEquals("Key feature walkthrough with video clip", reopenedScenes[1].text)
        assertEquals(SceneTextStyle.HIGHLIGHTED_KEYWORD, reopenedScenes[1].textStyle)
        assertEquals(SceneBackgroundType.VIDEO_CLIP, reopenedScenes[1].backgroundType)
        assertEquals("content://media/external/video/55", reopenedScenes[1].backgroundUri)
        assertEquals(500L, reopenedScenes[1].clipTrimStartMs)
        assertEquals(4500L, reopenedScenes[1].clipTrimEndMs)
        assertEquals("/data/vo2.m4a", reopenedScenes[1].voiceFilePath)
        assertEquals("/data/vo2_orig.m4a", reopenedScenes[1].voiceOriginalFilePath)
        assertEquals(4000L, reopenedScenes[1].manualDurationMs)

        assertEquals(SceneType.OUTRO, reopenedScenes[2].type)
        assertEquals("Contact us today", reopenedScenes[2].text)
        assertEquals(SceneTextStyle.BULLET_POINT, reopenedScenes[2].textStyle)
        assertEquals(SceneBackgroundType.GRADIENT, reopenedScenes[2].backgroundType)
        assertEquals(4000L, reopenedScenes[2].manualDurationMs)
        assertEquals(SceneTransition.NONE, reopenedScenes[2].transition)

        // Verify POST draft is untouched
        val postRow = rows.first { it.id == "post-draft-1" }
        assertEquals(postPayloadJson, postRow.payloadJson)
        assertEquals("Post caption", postRow.caption)
        assertEquals(1, postRow.documentVersion)
    }
}
