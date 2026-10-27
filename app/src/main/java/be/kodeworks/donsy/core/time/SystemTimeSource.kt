// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import be.kodeworks.donsy.domain.time.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class SystemTimeSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : TimeSource {
    override fun now(): Instant = Instant.now()

    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

    override fun zone(): ZoneId = ZoneId.systemDefault()

    override fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
}
