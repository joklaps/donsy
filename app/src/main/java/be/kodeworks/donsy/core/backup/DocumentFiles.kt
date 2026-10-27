// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Files the user picked with the system file picker (Storage Access Framework), so Donsy needs no
 * storage permission. An interface, so tests can stand in for the picker's files.
 */
interface DocumentFiles {
    /** The file's bytes, or null when it can't be read. */
    suspend fun read(uri: Uri): ByteArray?

    /** Replaces the file's contents; false when it can't be written. */
    suspend fun write(uri: Uri, bytes: ByteArray): Boolean
}

class ContentResolverDocumentFiles @Inject constructor(
    @ApplicationContext private val context: Context,
) : DocumentFiles {

    override suspend fun read(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    override suspend fun write(uri: Uri, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            // "wt": truncate, so a shorter export never leaves the end of an older one behind.
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
