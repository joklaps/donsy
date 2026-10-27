// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.backup

import kotlinx.serialization.Serializable

/*
 * The export file format (docs/spec.md, "Data safety"). Plain JSON that a person can read:
 * times are epoch milliseconds, dates and times ISO-8601 text, enums their names. Ids are kept,
 * so links between tasks, steps, notes and sessions survive a restore.
 *
 * A change that older versions can't read raises FORMAT_VERSION; added optional fields don't.
 */

internal const val FORMAT = "donsy-backup"
internal const val FORMAT_VERSION = 1

@Serializable
internal data class BackupFile(
    val format: String = FORMAT,
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAt: Long,
    val tasks: List<TaskJson>,
    val steps: List<StepJson>,
    val notes: List<NoteJson>,
    val focusSessions: List<FocusSessionJson>,
    val settings: SettingsJson,
)

@Serializable
internal data class TaskJson(
    val id: Long,
    val title: String,
    val notes: String = "",
    val status: String,
    val importance: String,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val repeatDays: Int? = null,
    val routineDoneOn: String? = null,
    val estimateMinutes: Int? = null,
    val effort: String? = null,
    val notNowUntil: Long? = null,
    val lastWorkedAt: Long? = null,
    val sourceBrainDumpId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val deletedAt: Long? = null,
)

@Serializable
internal data class StepJson(
    val id: Long,
    val taskId: Long,
    val title: String,
    val position: Int,
    val isDone: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
)

/** A BrainDump item. */
@Serializable
internal data class NoteJson(
    val id: Long,
    val text: String,
    val status: String,
    val convertedTaskId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** A finished session. The timer's device-bound fields (boot count, elapsed clock) are left out. */
@Serializable
internal data class FocusSessionJson(
    val id: Long,
    val taskId: Long? = null,
    val stepId: Long? = null,
    val plannedDurationMillis: Long,
    val status: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val accumulatedRunMillis: Long = 0,
)

@Serializable
internal data class SettingsJson(
    val focusDurations: List<Int>,
    val focusDefaultMinutes: Int,
    val preciseEndAlerts: Boolean,
    val archiveAfterDays: Int,
)

internal const val ENCRYPTED_FORMAT = "donsy-backup-encrypted"
internal const val ENCRYPTED_FORMAT_VERSION = 1

/** A passphrase-protected export: the plain file, encrypted with AES-256-GCM. Bytes are Base64. */
@Serializable
internal data class EncryptedBackupFile(
    val format: String = ENCRYPTED_FORMAT,
    val formatVersion: Int = ENCRYPTED_FORMAT_VERSION,
    val kdf: String,
    val iterations: Int,
    val salt: String,
    val iv: String,
    val data: String,
)
