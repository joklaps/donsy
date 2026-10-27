// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.time

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Controllable clock for tests. Wall clock and monotonic time advance together unless set apart. */
class FakeTimeSource(
    var now: Instant = Instant.parse("2026-10-21T10:00:00Z"),
    var elapsedRealtimeMillis: Long = 0L,
    var zone: ZoneId = ZoneId.of("Europe/Brussels"),
    var bootCount: Int = 1,
) : TimeSource {
    override fun now(): Instant = now

    override fun elapsedRealtimeMillis(): Long = elapsedRealtimeMillis

    override fun zone(): ZoneId = zone

    override fun bootCount(): Int = bootCount

    fun advanceBy(duration: Duration) {
        now += duration
        elapsedRealtimeMillis += duration.toMillis()
    }

    /** The user changes the clock: wall time jumps, monotonic time doesn't. */
    fun setWallClock(instant: Instant) {
        now = instant
    }

    /** The device restarts after [downtime]: monotonic time starts again near zero. */
    fun reboot(downtime: Duration = Duration.ofMinutes(1)) {
        now += downtime
        elapsedRealtimeMillis = 5_000
        bootCount += 1
    }
}
