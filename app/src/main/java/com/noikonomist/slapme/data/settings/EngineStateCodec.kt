package com.noikonomist.slapme.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.noikonomist.slapme.engine.DailyStats
import com.noikonomist.slapme.engine.EngineState
import com.noikonomist.slapme.engine.UsageState
import java.time.LocalDate

/** Maps [EngineState] to preferences and back, one key per field. Nullable fields are absent when null. */
object EngineStateCodec {
    const val VERSION = 1

    object Keys {
        val VERSION = intPreferencesKey("version")
        val LAST_EVENT_AT = longPreferencesKey("last_event_at")
        val ACTIVE = booleanPreferencesKey("active")
        val USAGE = stringPreferencesKey("usage")
        val FOREGROUND_PACKAGE = stringPreferencesKey("foreground_package")
        val STREAK_MS = longPreferencesKey("streak_ms")
        val IDLE_RUN_MS = longPreferencesKey("idle_run_ms")
        val LEVEL = intPreferencesKey("level")
        val LOCKOUT_UNTIL = longPreferencesKey("lockout_until")
        val LOCKOUT_COUNTING_MS = longPreferencesKey("lockout_counting_ms")
        val OVERRIDE_UNTIL = longPreferencesKey("override_until")
        val WARNING_ISSUED = booleanPreferencesKey("warning_issued")
        val USED_SNOOZE_PERIODS = stringSetPreferencesKey("used_snooze_periods")
        val TODAY_EPOCH_DAY = longPreferencesKey("today_epoch_day")
        val TODAY_COUNTED_MS = longPreferencesKey("today_counted_ms")
        val TODAY_LONGEST_FOCUS_MS = longPreferencesKey("today_longest_focus_ms")
        val FOCUS_RUN_MS = longPreferencesKey("focus_run_ms")
        val REPORTED_EXPIRED_SESSION = longPreferencesKey("reported_expired_session")
    }

    sealed interface Decoded {
        data object Empty : Decoded
        data class Ok(val state: EngineState) : Decoded
        data class Unusable(val reason: String) : Decoded
    }

    fun decode(prefs: Preferences): Decoded {
        if (prefs.asMap().isEmpty()) return Decoded.Empty
        val version = prefs[Keys.VERSION]
        if (version != VERSION) return Decoded.Unusable("version $version, expected $VERSION")
        val usage = UsageState.entries.firstOrNull { it.name == prefs[Keys.USAGE] }
            ?: return Decoded.Unusable("unknown usage state ${prefs[Keys.USAGE]}")

        return Decoded.Ok(
            EngineState(
                lastEventAtMillis = prefs[Keys.LAST_EVENT_AT],
                active = prefs[Keys.ACTIVE] ?: false,
                usage = usage,
                foregroundPackage = prefs[Keys.FOREGROUND_PACKAGE],
                streakMillis = prefs[Keys.STREAK_MS] ?: 0,
                idleRunMillis = prefs[Keys.IDLE_RUN_MS] ?: 0,
                level = prefs[Keys.LEVEL] ?: 0,
                lockoutUntilMillis = prefs[Keys.LOCKOUT_UNTIL],
                lockoutCountingMillis = prefs[Keys.LOCKOUT_COUNTING_MS] ?: 0,
                overrideUntilMillis = prefs[Keys.OVERRIDE_UNTIL],
                warningIssued = prefs[Keys.WARNING_ISSUED] ?: false,
                usedSnoozePeriods = prefs[Keys.USED_SNOOZE_PERIODS].orEmpty(),
                today = prefs[Keys.TODAY_EPOCH_DAY]?.let { day ->
                    DailyStats(
                        date = LocalDate.ofEpochDay(day),
                        countedMillis = prefs[Keys.TODAY_COUNTED_MS] ?: 0,
                        longestFocusMillis = prefs[Keys.TODAY_LONGEST_FOCUS_MS] ?: 0,
                    )
                },
                focusRunMillis = prefs[Keys.FOCUS_RUN_MS] ?: 0,
                reportedExpiredSessionId = prefs[Keys.REPORTED_EXPIRED_SESSION],
            ),
        )
    }

    /** Replaces everything in [prefs] with [state]. */
    fun encode(state: EngineState, prefs: MutablePreferences) {
        prefs.clear()
        prefs[Keys.VERSION] = VERSION
        state.lastEventAtMillis?.let { prefs[Keys.LAST_EVENT_AT] = it }
        prefs[Keys.ACTIVE] = state.active
        prefs[Keys.USAGE] = state.usage.name
        state.foregroundPackage?.let { prefs[Keys.FOREGROUND_PACKAGE] = it }
        prefs[Keys.STREAK_MS] = state.streakMillis
        prefs[Keys.IDLE_RUN_MS] = state.idleRunMillis
        prefs[Keys.LEVEL] = state.level
        state.lockoutUntilMillis?.let { prefs[Keys.LOCKOUT_UNTIL] = it }
        prefs[Keys.LOCKOUT_COUNTING_MS] = state.lockoutCountingMillis
        state.overrideUntilMillis?.let { prefs[Keys.OVERRIDE_UNTIL] = it }
        prefs[Keys.WARNING_ISSUED] = state.warningIssued
        prefs[Keys.USED_SNOOZE_PERIODS] = state.usedSnoozePeriods
        state.today?.let {
            prefs[Keys.TODAY_EPOCH_DAY] = it.date.toEpochDay()
            prefs[Keys.TODAY_COUNTED_MS] = it.countedMillis
            prefs[Keys.TODAY_LONGEST_FOCUS_MS] = it.longestFocusMillis
        }
        prefs[Keys.FOCUS_RUN_MS] = state.focusRunMillis
        state.reportedExpiredSessionId?.let { prefs[Keys.REPORTED_EXPIRED_SESSION] = it }
    }
}
