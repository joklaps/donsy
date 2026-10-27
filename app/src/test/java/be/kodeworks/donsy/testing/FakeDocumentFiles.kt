// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.testing

import android.net.Uri
import be.kodeworks.donsy.core.backup.DocumentFiles

/** Stands in for files picked with the system file picker. */
class FakeDocumentFiles : DocumentFiles {
    val files = mutableMapOf<Uri, ByteArray>()

    /** Uris that can't be read or written, like a file on a removed SD card. */
    val broken = mutableSetOf<Uri>()

    override suspend fun read(uri: Uri): ByteArray? = if (uri in broken) null else files[uri]

    override suspend fun write(uri: Uri, bytes: ByteArray): Boolean {
        if (uri in broken) return false
        files[uri] = bytes
        return true
    }
}
