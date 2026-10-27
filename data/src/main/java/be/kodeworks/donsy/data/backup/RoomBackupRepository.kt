// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.backup

import androidx.room.withTransaction
import be.kodeworks.donsy.data.db.DonsyDatabase
import be.kodeworks.donsy.data.db.entity.BrainDumpItemEntity
import be.kodeworks.donsy.data.db.entity.FocusSessionEntity
import be.kodeworks.donsy.data.db.entity.SubtaskEntity
import be.kodeworks.donsy.data.db.entity.TaskEntity
import be.kodeworks.donsy.data.preferences.BackupSettings
import be.kodeworks.donsy.data.preferences.UserPreferencesRepository
import be.kodeworks.donsy.domain.backup.Backup
import be.kodeworks.donsy.domain.backup.BackupReadResult
import be.kodeworks.donsy.domain.backup.BackupRepository
import be.kodeworks.donsy.domain.backup.BackupSummary
import be.kodeworks.donsy.domain.backup.RestoreResult
import be.kodeworks.donsy.domain.model.BrainDumpStatus
import be.kodeworks.donsy.domain.model.Effort
import be.kodeworks.donsy.domain.model.FocusSessionStatus
import be.kodeworks.donsy.domain.model.Importance
import be.kodeworks.donsy.domain.model.TaskStatus
import be.kodeworks.donsy.domain.time.TimeSource
import java.time.DateTimeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class RoomBackupRepository @Inject constructor(
    private val database: DonsyDatabase,
    private val preferences: UserPreferencesRepository,
    private val timeSource: TimeSource,
) : BackupRepository {

    private val dao = database.backupDao()

    override suspend fun export(passphrase: CharArray?): ByteArray {
        // One transaction, so the file is a consistent snapshot.
        val file = database.withTransaction {
            BackupFile(
                exportedAt = timeSource.now().toEpochMilli(),
                tasks = dao.tasks().map { it.toJson() },
                steps = dao.subtasks().map { it.toJson() },
                notes = dao.brainDumpItems().map { it.toJson() },
                focusSessions = dao.finishedFocusSessions().map { it.toJson() },
                settings = preferences.backupSettings().toJson(),
            )
        }
        val plain = json.encodeToString(BackupFile.serializer(), file).encodeToByteArray()
        if (passphrase == null) return plain
        // Key derivation is deliberately slow; keep it off the main thread.
        val encrypted = withContext(Dispatchers.Default) { BackupCrypto.encrypt(plain, passphrase) }
        return json.encodeToString(EncryptedBackupFile.serializer(), encrypted).encodeToByteArray()
    }

    override suspend fun read(file: ByteArray, passphrase: CharArray?): BackupReadResult = withContext(Dispatchers.Default) {
        try {
            val header = json.parseToJsonElement(file.decodeToString()).jsonObject
            val format = header["format"]?.jsonPrimitive?.content
            val version = header["formatVersion"]?.jsonPrimitive?.intOrNull ?: return@withContext BackupReadResult.NotABackup
            when (format) {
                FORMAT -> readPlain(file, version)
                ENCRYPTED_FORMAT -> {
                    if (version > ENCRYPTED_FORMAT_VERSION) return@withContext BackupReadResult.FromNewerVersion
                    if (passphrase == null) return@withContext BackupReadResult.NeedsPassphrase
                    val encrypted = json.decodeFromString(EncryptedBackupFile.serializer(), file.decodeToString())
                    val plain = BackupCrypto.decrypt(encrypted, passphrase) ?: return@withContext BackupReadResult.WrongPassphrase
                    val innerVersion = json.parseToJsonElement(plain.decodeToString()).jsonObject["formatVersion"]?.jsonPrimitive?.int
                        ?: return@withContext BackupReadResult.NotABackup
                    readPlain(plain, innerVersion)
                }
                else -> BackupReadResult.NotABackup
            }
        } catch (_: SerializationException) {
            BackupReadResult.NotABackup
        } catch (_: IllegalArgumentException) {
            // Also covers bad Base64, unknown enum names and unparsable dates.
            BackupReadResult.NotABackup
        } catch (_: DateTimeException) {
            BackupReadResult.NotABackup
        }
    }

    private fun readPlain(file: ByteArray, version: Int): BackupReadResult {
        if (version > FORMAT_VERSION) return BackupReadResult.FromNewerVersion
        val backup = json.decodeFromString(BackupFile.serializer(), file.decodeToString())
        // Map everything now: a broken file is reported before anything is replaced.
        val contents = Contents(
            tasks = backup.tasks.map { it.toEntity() },
            steps = backup.steps.map { it.toEntity() },
            notes = backup.notes.map { it.toEntity() },
            focusSessions = backup.focusSessions.map { it.toEntity() },
            settings = backup.settings.toSettings(),
        )
        if (!contents.isConsistent()) return BackupReadResult.NotABackup
        return BackupReadResult.Ready(RoomBackup(contents.summary(Instant.ofEpochMilli(backup.exportedAt)), contents))
    }

    override suspend fun restore(backup: Backup): RestoreResult {
        val contents = (backup as RoomBackup).contents
        val restored = database.withTransaction {
            if (dao.activeFocusSessionCount() > 0) return@withTransaction false
            // Children first, so no foreign key is ever left dangling.
            dao.deleteFocusSessions()
            dao.deleteBrainDumpItems()
            dao.deleteSubtasks()
            dao.deleteTasks()
            // Short-lived and not exported: an old check-in shouldn't steer the restored tasks.
            dao.deleteEnergyCheckIns()
            dao.insertTasks(contents.tasks)
            dao.insertSubtasks(contents.steps)
            dao.insertBrainDumpItems(contents.notes)
            dao.insertFocusSessions(contents.focusSessions)
            true
        }
        if (!restored) return RestoreResult.FocusSessionActive
        preferences.restoreSettings(contents.settings)
        return RestoreResult.Restored
    }

    private class RoomBackup(override val summary: BackupSummary, val contents: Contents) : Backup

    private class Contents(
        val tasks: List<TaskEntity>,
        val steps: List<SubtaskEntity>,
        val notes: List<BrainDumpItemEntity>,
        val focusSessions: List<FocusSessionEntity>,
        val settings: BackupSettings,
    ) {
        /** Unique ids and no links to missing rows; the database would reject anything else. */
        fun isConsistent(): Boolean {
            val taskIds = tasks.map { it.id }.toSet()
            val stepIds = steps.map { it.id }.toSet()
            return taskIds.size == tasks.size &&
                stepIds.size == steps.size &&
                notes.map { it.id }.toSet().size == notes.size &&
                focusSessions.map { it.id }.toSet().size == focusSessions.size &&
                steps.all { it.taskId in taskIds } &&
                notes.all { it.convertedTaskId == null || it.convertedTaskId in taskIds } &&
                focusSessions.all { (it.taskId == null || it.taskId in taskIds) && (it.subtaskId == null || it.subtaskId in stepIds) }
        }

        fun summary(exportedAt: Instant) = BackupSummary(
            exportedAt = exportedAt,
            tasks = tasks.count { it.deletedAt == null && it.repeatDays == null },
            routines = tasks.count { it.deletedAt == null && it.repeatDays != null },
            notes = notes.count { it.deletedAt == null && it.status == BrainDumpStatus.INBOX },
            focusSessions = focusSessions.size,
            recentlyDeleted = tasks.count { it.deletedAt != null } + notes.count { it.deletedAt != null },
        )
    }

    private companion object {
        val json = Json {
            // A newer app may add fields within the same format version.
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}

private fun Instant.millis() = toEpochMilli()

private fun Long.instant(): Instant = Instant.ofEpochMilli(this)

private fun TaskEntity.toJson() = TaskJson(
    id = id,
    title = title,
    notes = notes,
    status = status.name,
    importance = importance.name,
    dueDate = dueDate?.toString(),
    dueTime = dueTime?.toString(),
    repeatDays = repeatDays,
    routineDoneOn = routineDoneOn?.toString(),
    estimateMinutes = estimateMinutes,
    effort = effort?.name,
    notNowUntil = notNowUntil?.millis(),
    lastWorkedAt = lastWorkedAt?.millis(),
    sourceBrainDumpId = sourceBrainDumpId,
    createdAt = createdAt.millis(),
    updatedAt = updatedAt.millis(),
    completedAt = completedAt?.millis(),
    deletedAt = deletedAt?.millis(),
)

private fun TaskJson.toEntity() = TaskEntity(
    id = id,
    title = title,
    notes = notes,
    status = TaskStatus.valueOf(status),
    importance = Importance.valueOf(importance),
    dueDate = dueDate?.let(LocalDate::parse),
    dueTime = dueTime?.let(LocalTime::parse),
    repeatDays = repeatDays,
    routineDoneOn = routineDoneOn?.let(LocalDate::parse),
    estimateMinutes = estimateMinutes,
    effort = effort?.let(Effort::valueOf),
    notNowUntil = notNowUntil?.instant(),
    lastWorkedAt = lastWorkedAt?.instant(),
    sourceBrainDumpId = sourceBrainDumpId,
    createdAt = createdAt.instant(),
    updatedAt = updatedAt.instant(),
    completedAt = completedAt?.instant(),
    deletedAt = deletedAt?.instant(),
)

private fun SubtaskEntity.toJson() = StepJson(
    id = id,
    taskId = taskId,
    title = title,
    position = position,
    isDone = isDone,
    createdAt = createdAt.millis(),
    updatedAt = updatedAt.millis(),
    completedAt = completedAt?.millis(),
)

private fun StepJson.toEntity() = SubtaskEntity(
    id = id,
    taskId = taskId,
    title = title,
    position = position,
    isDone = isDone,
    createdAt = createdAt.instant(),
    updatedAt = updatedAt.instant(),
    completedAt = completedAt?.instant(),
)

private fun BrainDumpItemEntity.toJson() = NoteJson(
    id = id,
    text = text,
    status = status.name,
    convertedTaskId = convertedTaskId,
    createdAt = createdAt.millis(),
    updatedAt = updatedAt.millis(),
    deletedAt = deletedAt?.millis(),
)

private fun NoteJson.toEntity() = BrainDumpItemEntity(
    id = id,
    text = text,
    status = BrainDumpStatus.valueOf(status),
    convertedTaskId = convertedTaskId,
    createdAt = createdAt.instant(),
    updatedAt = updatedAt.instant(),
    deletedAt = deletedAt?.instant(),
)

private fun FocusSessionEntity.toJson() = FocusSessionJson(
    id = id,
    taskId = taskId,
    stepId = subtaskId,
    plannedDurationMillis = plannedDurationMillis,
    status = status.name,
    startedAt = startedAt.millis(),
    endedAt = endedAt?.millis(),
    accumulatedRunMillis = accumulatedRunMillis,
)

/** Only finished sessions are restored; one claiming to be active is taken as ended early. */
private fun FocusSessionJson.toEntity(): FocusSessionEntity {
    val status = FocusSessionStatus.valueOf(status)
    val finished = status != FocusSessionStatus.RUNNING && status != FocusSessionStatus.PAUSED
    return FocusSessionEntity(
        id = id,
        taskId = taskId,
        subtaskId = stepId,
        plannedDurationMillis = plannedDurationMillis,
        status = if (finished) status else FocusSessionStatus.ENDED_EARLY,
        startedAt = startedAt.instant(),
        endedAt = endedAt?.instant() ?: startedAt.instant(),
        accumulatedRunMillis = accumulatedRunMillis,
        bootCount = 0,
    )
}

private fun BackupSettings.toJson() = SettingsJson(
    focusDurations = focusDurations,
    focusDefaultMinutes = focusDefaultMinutes,
    preciseEndAlerts = preciseEndAlerts,
    archiveAfterDays = archiveAfterDays,
)

private fun SettingsJson.toSettings() = BackupSettings(
    focusDurations = focusDurations,
    focusDefaultMinutes = focusDefaultMinutes,
    preciseEndAlerts = preciseEndAlerts,
    archiveAfterDays = archiveAfterDays,
)
