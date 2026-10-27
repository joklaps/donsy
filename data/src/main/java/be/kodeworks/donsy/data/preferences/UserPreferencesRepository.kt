// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import be.kodeworks.donsy.domain.archive.AutoArchive
import be.kodeworks.donsy.domain.focus.FocusDurations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Simple app preferences. Tasks, BrainDump items and focus sessions live in Room instead. */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Whether the "tap the mic on your keyboard" hint has been shown. */
    val voiceHintSeen: Flow<Boolean> = dataStore.data.map { it[Keys.VOICE_HINT_SEEN] ?: false }

    suspend fun markVoiceHintSeen() {
        dataStore.edit { it[Keys.VOICE_HINT_SEEN] = true }
    }

    /** Opt-in: end focus sessions with an exact alarm (needs Android's permission on 12+). */
    val preciseEndAlerts: Flow<Boolean> = dataStore.data.map { it[Keys.PRECISE_END_ALERTS] ?: false }

    suspend fun setPreciseEndAlerts(enabled: Boolean) {
        dataStore.edit { it[Keys.PRECISE_END_ALERTS] = enabled }
    }

    /** The Focus screen's "Get the end alert right on time?" card was answered (either way). */
    val preciseAlertsOfferAnswered: Flow<Boolean> = dataStore.data.map { it[Keys.PRECISE_ALERTS_OFFER_ANSWERED] ?: false }

    suspend fun markPreciseAlertsOfferAnswered() {
        dataStore.edit { it[Keys.PRECISE_ALERTS_OFFER_ANSWERED] = true }
    }

    /** The notification permission is asked once, at the first focus session. */
    val notificationPermissionAsked: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFICATION_PERMISSION_ASKED] ?: false }

    suspend fun markNotificationPermissionAsked() {
        dataStore.edit { it[Keys.NOTIFICATION_PERMISSION_ASKED] = true }
    }

    /** Session lengths offered on the Focus screen, in minutes, shortest first. */
    val focusDurations: Flow<List<Int>> = dataStore.data
        .map { it.focusDurations() }
        .distinctUntilChanged()

    /** The preselected session length; always one of [focusDurations]. */
    val focusDefaultMinutes: Flow<Int> = dataStore.data
        .map { prefs ->
            FocusDurations.defaultMinutes(prefs[Keys.FOCUS_DEFAULT_MINUTES], prefs.focusDurations())
        }
        .distinctUntilChanged()

    /** Offers [minutes] on the Focus screen or stops offering it. The last remaining length can't be removed. */
    suspend fun setFocusDurationOffered(minutes: Int, offered: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs.focusDurations()
            val updated = if (offered) current + minutes else current - minutes
            if (updated.isNotEmpty()) prefs[Keys.FOCUS_DURATIONS] = updated.mapTo(mutableSetOf()) { it.toString() }
        }
    }

    suspend fun setFocusDefaultMinutes(minutes: Int) {
        dataStore.edit { it[Keys.FOCUS_DEFAULT_MINUTES] = minutes }
    }

    /** Days after which a done task moves to the archive by itself; [AutoArchive.NEVER] turns it off. */
    val archiveAfterDays: Flow<Int> = dataStore.data.map { it[Keys.ARCHIVE_AFTER_DAYS] ?: AutoArchive.DEFAULT_DAYS }

    suspend fun setArchiveAfterDays(days: Int) {
        dataStore.edit { it[Keys.ARCHIVE_AFTER_DAYS] = days }
    }

    /** The settings that go into an export. One-time hints and permission answers stay with the device. */
    suspend fun backupSettings(): BackupSettings {
        val prefs = dataStore.data.first()
        return BackupSettings(
            focusDurations = prefs.focusDurations(),
            focusDefaultMinutes = FocusDurations.defaultMinutes(prefs[Keys.FOCUS_DEFAULT_MINUTES], prefs.focusDurations()),
            preciseEndAlerts = prefs[Keys.PRECISE_END_ALERTS] ?: false,
            archiveAfterDays = prefs[Keys.ARCHIVE_AFTER_DAYS] ?: AutoArchive.DEFAULT_DAYS,
        )
    }

    suspend fun restoreSettings(settings: BackupSettings) {
        dataStore.edit { prefs ->
            // Through choices(), so a file with unknown or no lengths still leaves valid ones.
            prefs[Keys.FOCUS_DURATIONS] = FocusDurations.choices(settings.focusDurations.toSet()).mapTo(mutableSetOf()) { it.toString() }
            prefs[Keys.FOCUS_DEFAULT_MINUTES] = settings.focusDefaultMinutes
            prefs[Keys.PRECISE_END_ALERTS] = settings.preciseEndAlerts
            prefs[Keys.ARCHIVE_AFTER_DAYS] = settings.archiveAfterDays
        }
    }

    /** Routine reminders due up to this moment have been handled; null before the first check. */
    val routineRemindersCheckedUntil: Flow<Instant?> =
        dataStore.data.map { prefs -> prefs[Keys.ROUTINE_REMINDERS_CHECKED_UNTIL]?.let(Instant::ofEpochMilli) }

    suspend fun setRoutineRemindersCheckedUntil(at: Instant) {
        dataStore.edit { it[Keys.ROUTINE_REMINDERS_CHECKED_UNTIL] = at.toEpochMilli() }
    }

    private fun Preferences.focusDurations(): List<Int> =
        FocusDurations.choices(this[Keys.FOCUS_DURATIONS]?.mapNotNull { it.toIntOrNull() }?.toSet())

    private object Keys {
        val VOICE_HINT_SEEN = booleanPreferencesKey("voice_hint_seen")
        val PRECISE_END_ALERTS = booleanPreferencesKey("precise_end_alerts")
        val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
        val PRECISE_ALERTS_OFFER_ANSWERED = booleanPreferencesKey("precise_alerts_offer_answered")
        val FOCUS_DURATIONS = stringSetPreferencesKey("focus_durations")
        val FOCUS_DEFAULT_MINUTES = intPreferencesKey("focus_default_minutes")
        val ROUTINE_REMINDERS_CHECKED_UNTIL = longPreferencesKey("routine_reminders_checked_until")
        val ARCHIVE_AFTER_DAYS = intPreferencesKey("archive_after_days")
    }
}

/** Settings as they are exported and restored (docs/spec.md, "Data safety"). */
data class BackupSettings(
    val focusDurations: List<Int>,
    val focusDefaultMinutes: Int,
    val preciseEndAlerts: Boolean,
    val archiveAfterDays: Int,
)
