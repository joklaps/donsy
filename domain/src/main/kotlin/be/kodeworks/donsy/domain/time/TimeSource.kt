// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.time

import java.time.Instant
import java.time.ZoneId

/**
 * The only way domain code reads the clock, so rules stay deterministic and testable.
 */
interface TimeSource {
    /** Wall-clock time; can jump when the user or network changes the clock. */
    fun now(): Instant

    /** Monotonic milliseconds since boot; immune to clock changes, resets on reboot. */
    fun elapsedRealtimeMillis(): Long

    fun zone(): ZoneId

    /**
     * Identifies the current boot. When it differs from a stored value, the device restarted and
     * [elapsedRealtimeMillis] values from before cannot be compared any more.
     */
    fun bootCount(): Int
}
