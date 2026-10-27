// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.backup

import java.time.Instant

/** What a backup file holds, shown before restoring it. Recently deleted items are counted apart. */
data class BackupSummary(
    val exportedAt: Instant,
    val tasks: Int,
    val routines: Int,
    val notes: Int,
    val focusSessions: Int,
    val recentlyDeleted: Int,
)

/** A backup file that has been read (and decrypted), ready to restore. */
interface Backup {
    val summary: BackupSummary
}

sealed interface BackupReadResult {
    data class Ready(val backup: Backup) : BackupReadResult

    /** The file is protected; read it again with the passphrase. */
    data object NeedsPassphrase : BackupReadResult

    data object WrongPassphrase : BackupReadResult

    data object NotABackup : BackupReadResult

    /** Made by a newer Donsy that this version can't read. */
    data object FromNewerVersion : BackupReadResult
}

sealed interface RestoreResult {
    data object Restored : RestoreResult

    /** Nothing changed: a running or paused focus session would lose its task. */
    data object FocusSessionActive : RestoreResult
}

/**
 * Export and restore of everything the user made (docs/spec.md, "Data safety"): tasks with their
 * steps, routines, BrainDump items, recently deleted items, finished focus sessions and settings.
 */
interface BackupRepository {
    /** The whole backup file; with a [passphrase] it is encrypted. */
    suspend fun export(passphrase: CharArray?): ByteArray

    suspend fun read(file: ByteArray, passphrase: CharArray?): BackupReadResult

    /** Replaces everything with the backup's contents, in one transaction. */
    suspend fun restore(backup: Backup): RestoreResult
}
