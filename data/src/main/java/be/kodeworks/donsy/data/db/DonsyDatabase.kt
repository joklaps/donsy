// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import be.kodeworks.donsy.data.db.dao.BackupDao
import be.kodeworks.donsy.data.db.dao.BrainDumpDao
import be.kodeworks.donsy.data.db.dao.EnergyCheckInDao
import be.kodeworks.donsy.data.db.dao.FocusSessionDao
import be.kodeworks.donsy.data.db.dao.TaskDao
import be.kodeworks.donsy.data.db.entity.BrainDumpItemEntity
import be.kodeworks.donsy.data.db.entity.EnergyCheckInEntity
import be.kodeworks.donsy.data.db.entity.FocusSessionEntity
import be.kodeworks.donsy.data.db.entity.SubtaskEntity
import be.kodeworks.donsy.data.db.entity.TaskEntity

/**
 * Every schema change after the first release needs a version bump, a migration and a
 * migration test. Exported schemas live in data/schemas/.
 */
@Database(
    entities = [
        TaskEntity::class,
        SubtaskEntity::class,
        BrainDumpItemEntity::class,
        FocusSessionEntity::class,
        EnergyCheckInEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // 2: focus_sessions.paused_at
        AutoMigration(from = 1, to = 2),
        // 3: energy_check_ins
        AutoMigration(from = 2, to = 3),
        // 4: tasks.due_time
        AutoMigration(from = 3, to = 4),
        // 5: tasks.repeat_days, tasks.routine_done_on (routines)
        AutoMigration(from = 4, to = 5),
    ],
)
@TypeConverters(Converters::class)
abstract class DonsyDatabase : RoomDatabase() {
    abstract fun brainDumpDao(): BrainDumpDao

    abstract fun taskDao(): TaskDao

    abstract fun focusSessionDao(): FocusSessionDao

    abstract fun energyCheckInDao(): EnergyCheckInDao

    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "donsy.db"
    }
}
