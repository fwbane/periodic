package com.enabwf.periodic

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsRoomTest {
    private lateinit var database: TaskDatabase
    private lateinit var dao: TaskDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TaskDatabase::class.java).build()
        dao = database.taskDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun previousCompletionIsLookedUpBeforeTheSelectedRange() = runBlocking {
        val taskId = insertTask("Cadence")
        insertCompletion(taskId, "2026-01-01")
        insertCompletion(taskId, "2026-01-02")
        insertCompletion(taskId, "2026-01-03")

        val rows = dao.getAnalyticsCompletions(
            startTime = date("2026-01-02"),
            endTimeExclusive = date("2026-01-04"),
            includeArchived = false,
            taskId = null
        )

        assertEquals(2, rows.size)
        assertEquals(date("2026-01-01"), rows[0].previousCompletionTime)
        assertEquals(date("2026-01-02"), rows[1].previousCompletionTime)
    }

    @Test
    fun previousCompletionUsesIdToOrderEqualTimestamps() = runBlocking {
        val taskId = insertTask("Duplicate timestamps")
        insertCompletion(taskId, "2026-01-01")
        insertCompletion(taskId, "2026-01-02")
        insertCompletion(taskId, "2026-01-02")

        val rows = dao.getAnalyticsCompletions(
            startTime = date("2026-01-02"),
            endTimeExclusive = date("2026-01-03"),
            includeArchived = false,
            taskId = taskId
        )

        assertEquals(2, rows.size)
        assertEquals(date("2026-01-01"), rows[0].previousCompletionTime)
        assertEquals(date("2026-01-02"), rows[1].previousCompletionTime)
        val metrics = AnalyticsCalculator.calculate(
            rows,
            LocalDate.parse("2026-01-02"),
            LocalDate.parse("2026-01-02"),
            ZoneOffset.UTC
        )
        assertEquals(1, metrics.summary.eligibleIntervalCount)
    }

    @Test
    fun queryAppliesInclusiveStartExclusiveEndTaskAndArchiveFilters() = runBlocking {
        val activeId = insertTask("Active", active = true)
        val archivedId = insertTask("Archived", active = false)
        insertCompletion(activeId, "2025-12-31")
        insertCompletion(activeId, "2026-01-01")
        insertCompletion(activeId, "2026-01-03")
        insertCompletion(archivedId, "2026-01-02")

        val activeOnly = dao.getAnalyticsCompletions(
            startTime = date("2026-01-01"),
            endTimeExclusive = date("2026-01-03"),
            includeArchived = false,
            taskId = null
        )
        assertEquals(listOf(activeId), activeOnly.map { it.taskId })
        assertEquals(listOf(date("2026-01-01")), activeOnly.map { it.completionTime })

        val includingArchived = dao.getAnalyticsCompletions(
            startTime = date("2026-01-01"),
            endTimeExclusive = date("2026-01-03"),
            includeArchived = true,
            taskId = null
        )
        assertEquals(listOf(activeId, archivedId), includingArchived.map { it.taskId })

        val archivedTaskOnly = dao.getAnalyticsCompletions(
            startTime = null,
            endTimeExclusive = date("2026-01-04"),
            includeArchived = true,
            taskId = archivedId
        )
        assertEquals(1, archivedTaskOnly.size)
        assertEquals(archivedId, archivedTaskOnly.single().taskId)
    }

    @Test
    fun repositoryTagFilterUsesExactCommaSeparatedTags() = runBlocking {
        val exactId = insertTask("Exact", tags = listOf("home", "health"))
        val substringId = insertTask("Substring", tags = listOf("homework"))
        val caseId = insertTask("Case", tags = listOf("Home"))
        insertCompletion(exactId, "2026-01-01")
        insertCompletion(substringId, "2026-01-01")
        insertCompletion(caseId, "2026-01-01")

        val rows = TaskRepository(dao).getAnalyticsCompletions(
            startTime = null,
            endTimeExclusive = date("2026-01-02"),
            includeArchived = false,
            taskId = null,
            exactTag = " home "
        )

        assertEquals(listOf(exactId), rows.map { it.taskId })
    }

    @Test
    fun perTaskAdherenceUsesSelectedRangeRows() = runBlocking {
        val taskId = insertTask("Stretch")
        insertCompletion(taskId, "2026-01-01")
        insertCompletion(taskId, "2026-01-08")
        insertCompletion(taskId, "2026-01-15")

        val rangeRows = dao.getAnalyticsCompletions(
            startTime = date("2026-01-01"),
            endTimeExclusive = date("2026-01-31"),
            includeArchived = false,
            taskId = null
        )
        val tasks = dao.getAllTasks()
        val metrics = AnalyticsCalculator.calculate(
            rows = rangeRows,
            rangeStart = LocalDate.parse("2026-01-01"),
            rangeEnd = LocalDate.parse("2026-01-30"),
            zoneId = ZoneOffset.UTC,
            tasks = tasks
        )

        assertEquals(1, metrics.taskAdherence.size)
        assertEquals("Stretch", metrics.taskAdherence.single().taskName)
        assertEquals(2, metrics.taskAdherence.single().onScheduleCount)
    }

    @Test
    fun emptyQueryReturnsNoRows() = runBlocking {
        val rows = dao.getAnalyticsCompletions(
            startTime = date("2026-01-01"),
            endTimeExclusive = date("2026-01-02"),
            includeArchived = true,
            taskId = null
        )

        assertTrue(rows.isEmpty())
    }

    @Test
    fun migrationFiveToSixCreatesOrderedCompletionIndex() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE completion_table (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                taskId INTEGER NOT NULL,
                                completionTime INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )

        try {
            val sqlite = helper.writableDatabase
            TaskDatabase.MIGRATION_5_6.migrate(sqlite)

            val indexNames = mutableListOf<String>()
            sqlite.query("PRAGMA index_list('completion_table')").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) indexNames += cursor.getString(nameColumn)
            }
            assertTrue("Expected analytics completion index", INDEX_NAME in indexNames)

            val columns = mutableListOf<String>()
            sqlite.query("PRAGMA index_info('$INDEX_NAME')").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) columns += cursor.getString(nameColumn)
            }
            assertEquals(listOf("taskId", "completionTime"), columns)
            assertFalse(columns.isEmpty())
        } finally {
            helper.close()
        }
    }

    private suspend fun insertTask(
        name: String,
        tags: List<String> = emptyList(),
        active: Boolean = true
    ): Int = dao.insert(
        Task(
            name = name,
            periodInMillis = DAY_MILLIS,
            tags = tags,
            isActive = active
        )
    ).toInt()

    private suspend fun insertCompletion(taskId: Int, day: String) {
        dao.insertCompletionRecord(CompletionRecord(taskId = taskId, completionTime = date(day)))
    }

    private fun date(day: String): Date =
        Date.from(LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant())

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val INDEX_NAME = "index_completion_table_taskId_completionTime"
    }
}
