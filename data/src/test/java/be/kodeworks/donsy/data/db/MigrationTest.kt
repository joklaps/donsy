// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import be.kodeworks.donsy.data.db.entity.EnergyCheckInEntity
import be.kodeworks.donsy.domain.model.EnergyLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Every schema version must migrate to the latest one without losing data. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DonsyDatabase::class.java)

    @Test
    fun version1ToLatestKeepsDataAndAddsPausedAt() = runTest {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                INSERT INTO tasks (id, title, notes, status, importance, created_at, updated_at)
                VALUES (1, 'Biology test', '', 'ACTIVE', 'HIGH', 1000, 1000)
                """,
            )
            execSQL("INSERT INTO subtasks (id, task_id, title, position, is_done, created_at, updated_at) VALUES (10, 1, 'Topics', 0, 1, 1000, 1000)")
            execSQL("INSERT INTO brain_dump_items (id, text, status, created_at, updated_at) VALUES (5, 'Call grandma', 'INBOX', 1000, 1000)")
            execSQL(
                """
                INSERT INTO focus_sessions (id, task_id, subtask_id, planned_duration_millis, status, started_at,
                    accumulated_run_millis, boot_count)
                VALUES (3, 1, 10, 900000, 'PAUSED', 2000, 60000, 4)
                """,
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, LATEST_VERSION, true).close()

        // Open with the real Room schema to read through the DAOs.
        val database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), DonsyDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Biology test", database.taskDao().getTask(1)?.title)
            assertEquals(listOf("Topics"), database.taskDao().getSubtasks(1).map { it.title })
            assertEquals("Call grandma", database.brainDumpDao().getById(5)?.text)
            val session = database.focusSessionDao().get(3)!!
            assertEquals(60_000L, session.accumulatedRunMillis)
            assertNull(session.pausedAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun version2ToLatestKeepsDataAndAddsEnergyCheckIns() = runTest {
        helper.createDatabase(dbName, 2).apply {
            execSQL("INSERT INTO tasks (id, title, notes, status, importance, created_at, updated_at) VALUES (1, 'Essay', '', 'ACTIVE', 'NORMAL', 1000, 1000)")
            execSQL(
                """
                INSERT INTO focus_sessions (id, task_id, planned_duration_millis, status, started_at,
                    accumulated_run_millis, boot_count, paused_at)
                VALUES (3, 1, 900000, 'PAUSED', 2000, 60000, 4, 3000)
                """,
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, LATEST_VERSION, true).close()

        val database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), DonsyDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Essay", database.taskDao().getTask(1)?.title)
            assertEquals(Instant.ofEpochMilli(3000), database.focusSessionDao().get(3)?.pausedAt)
            assertNull(database.energyCheckInDao().observeLatest().first())
            database.energyCheckInDao().insert(EnergyCheckInEntity(level = EnergyLevel.HIGH, checkedInAt = Instant.ofEpochMilli(4000)))
            assertEquals(EnergyLevel.HIGH, database.energyCheckInDao().observeLatest().first()?.level)
        } finally {
            database.close()
        }
    }

    @Test
    fun version3ToLatestKeepsDueDatesAndAddsAnEmptyDueTime() = runTest {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                """
                INSERT INTO tasks (id, title, notes, status, importance, due_date, created_at, updated_at)
                VALUES (1, 'Essay', '', 'ACTIVE', 'NORMAL', ${LocalDate.of(2026, 10, 24).toEpochDay()}, 1000, 1000)
                """,
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, LATEST_VERSION, true).close()

        val database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), DonsyDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val task = database.taskDao().getTask(1)!!
            assertEquals(LocalDate.of(2026, 10, 24), task.dueDate)
            assertNull(task.dueTime)
            database.taskDao().updateTask(task.copy(dueTime = LocalTime.of(8, 30)))
            assertEquals(LocalTime.of(8, 30), database.taskDao().getTask(1)?.dueTime)
        } finally {
            database.close()
        }
    }

    @Test
    fun version4ToLatestKeepsTasksAsOrdinaryOnes() = runTest {
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                """
                INSERT INTO tasks (id, title, notes, status, importance, due_date, due_time, created_at, updated_at)
                VALUES (1, 'Essay', '', 'ACTIVE', 'NORMAL', ${LocalDate.of(2026, 10, 24).toEpochDay()}, 510, 1000, 1000)
                """,
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, LATEST_VERSION, true).close()

        val database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), DonsyDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val task = database.taskDao().getTask(1)!!
            assertEquals(LocalTime.of(8, 30), task.dueTime)
            assertNull(task.repeatDays)
            assertNull(task.routineDoneOn)
        } finally {
            database.close()
        }
    }

    /** Catches a version bump without an exported schema, or this test forgetting a new version. */
    @Test
    fun latestVersionIsTheDatabasesAndEverySchemaIsExported() {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DonsyDatabase::class.java).build()
        try {
            assertEquals(LATEST_VERSION, database.openHelper.readableDatabase.version)
        } finally {
            database.close()
        }
        val exported = InstrumentationRegistry.getInstrumentation().context.assets
            .list(DonsyDatabase::class.java.name)
            .orEmpty()
            .toSet()
        assertEquals((1..LATEST_VERSION).map { "$it.json" }.toSet(), exported)
    }

    /** Each single step, so a broken migration points at the version that broke. */
    @Test
    fun everyVersionMigratesToTheNextOne() {
        helper.createDatabase(dbName, 1).close()
        for (version in 2..LATEST_VERSION) {
            helper.runMigrationsAndValidate(dbName, version, true).close()
        }
    }

    private companion object {
        /** Raise together with @Database(version) and add a test for the new step. */
        const val LATEST_VERSION = 5
    }
}
