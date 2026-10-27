// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.model

import java.time.DayOfWeek

/**
 * The weekdays a routine repeats on, stored as a bit set (Monday = bit 0 … Sunday = bit 6).
 * Never empty: "no days" is represented by no [RepeatDays] at all.
 */
@JvmInline
value class RepeatDays private constructor(val bits: Int) {

    operator fun contains(day: DayOfWeek): Boolean = bits and bit(day) != 0

    /** In week order, Monday first. */
    val days: List<DayOfWeek> get() = DayOfWeek.entries.filter { it in this }

    val isEveryDay: Boolean get() = bits == ALL_BITS
    val isWeekdays: Boolean get() = bits == WEEKDAY_BITS

    companion object {
        private const val ALL_BITS = 0b111_1111
        private const val WEEKDAY_BITS = 0b001_1111

        val EVERY_DAY = RepeatDays(ALL_BITS)
        val WEEKDAYS = RepeatDays(WEEKDAY_BITS)

        /** Null for an empty set. */
        fun of(days: Collection<DayOfWeek>): RepeatDays? = fromBits(days.fold(0) { bits, day -> bits or bit(day) })

        /** Null for null, zero or no valid day bits. */
        fun fromBits(bits: Int?): RepeatDays? = bits?.and(ALL_BITS)?.takeIf { it != 0 }?.let(::RepeatDays)

        private fun bit(day: DayOfWeek) = 1 shl (day.value - 1)
    }
}
