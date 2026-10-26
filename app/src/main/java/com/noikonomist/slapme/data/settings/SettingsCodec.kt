package com.noikonomist.slapme.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.noikonomist.slapme.engine.CountingMode
import com.noikonomist.slapme.engine.EngineConfig
import com.noikonomist.slapme.engine.EngineConfig.Defaults
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Maps [Settings] to preferences and back. Durations are stored in ms. A missing or invalid value
 * falls back to its default, field by field, so bad data never stops the app.
 */
object SettingsCodec {

    object Keys {
        /** Comma-separated ms, in ladder order. */
        val LEVELS_MS = stringPreferencesKey("levels_ms")
        val LOCKOUT_MS = longPreferencesKey("lockout_ms")
        val LOCKOUT_GRACE_MS = longPreferencesKey("lockout_grace_ms")
        val STREAK_RESET_BREAK_MS = longPreferencesKey("streak_reset_break_ms")
        val ESCALATION_RESET_BREAK_MS = longPreferencesKey("escalation_reset_break_ms")

        /** Absent or 0 = off. */
        val DAILY_CAP_MS = longPreferencesKey("daily_cap_ms")

        /** Absent = default; 0 = off. */
        val WARNING_LEAD_MS = longPreferencesKey("warning_lead_ms")
        val SNOOZE_MS = longPreferencesKey("snooze_ms")
        val FRICTION_OVERRIDE_MS = longPreferencesKey("friction_override_ms")
        val COUNTING_MODE = stringPreferencesKey("counting_mode")
        val BLACKLIST = stringSetPreferencesKey("blacklist")
        val WHITELIST = stringSetPreferencesKey("whitelist")
        val NAVIGATION_PACKAGES = stringSetPreferencesKey("navigation_packages")
        val ALWAYS_ON = booleanPreferencesKey("always_on")
        val ALWAYS_PLAY_SOUND = booleanPreferencesKey("always_play_sound")
    }

    fun decode(prefs: Preferences): Settings {
        val engine = runCatching {
            EngineConfig(
                levels = prefs[Keys.LEVELS_MS]?.let(::parseLevels)?.takeIf { it.isNotEmpty() } ?: Defaults.levels,
                lockoutDuration = prefs.positive(Keys.LOCKOUT_MS) ?: Defaults.lockoutDuration,
                lockoutGrace = prefs[Keys.LOCKOUT_GRACE_MS]?.takeIf { it >= 0 }?.milliseconds ?: Defaults.lockoutGrace,
                streakResetBreak = prefs.positive(Keys.STREAK_RESET_BREAK_MS) ?: Defaults.streakResetBreak,
                escalationResetBreak = prefs.positive(Keys.ESCALATION_RESET_BREAK_MS) ?: Defaults.escalationResetBreak,
                dailyCap = prefs.positive(Keys.DAILY_CAP_MS),
                warningLead = when (val lead = prefs[Keys.WARNING_LEAD_MS]) {
                    null -> Defaults.warningLead
                    0L -> null
                    else -> if (lead > 0) lead.milliseconds else Defaults.warningLead
                },
                snoozeDuration = prefs.positive(Keys.SNOOZE_MS) ?: Defaults.snoozeDuration,
                frictionOverrideDuration = prefs.positive(Keys.FRICTION_OVERRIDE_MS) ?: Defaults.frictionOverrideDuration,
                countingMode = prefs[Keys.COUNTING_MODE]
                    ?.let { name -> CountingMode.entries.firstOrNull { it.name == name } }
                    ?: Defaults.countingMode,
                blacklist = prefs[Keys.BLACKLIST].orEmpty(),
                whitelist = prefs[Keys.WHITELIST].orEmpty(),
            )
        }.getOrElse { EngineConfig() }

        return Settings(
            engine = engine,
            alwaysOn = prefs[Keys.ALWAYS_ON] ?: false,
            alwaysPlaySound = prefs[Keys.ALWAYS_PLAY_SOUND] ?: false,
            navigationPackages = prefs[Keys.NAVIGATION_PACKAGES] ?: Defaults.navigationPackages,
        )
    }

    fun encode(settings: Settings, prefs: MutablePreferences) {
        val engine = settings.engine
        prefs[Keys.LEVELS_MS] = engine.levels.joinToString(",") { it.inWholeMilliseconds.toString() }
        prefs[Keys.LOCKOUT_MS] = engine.lockoutDuration.inWholeMilliseconds
        prefs[Keys.LOCKOUT_GRACE_MS] = engine.lockoutGrace.inWholeMilliseconds
        prefs[Keys.STREAK_RESET_BREAK_MS] = engine.streakResetBreak.inWholeMilliseconds
        prefs[Keys.ESCALATION_RESET_BREAK_MS] = engine.escalationResetBreak.inWholeMilliseconds
        prefs[Keys.DAILY_CAP_MS] = engine.dailyCap?.inWholeMilliseconds ?: 0
        prefs[Keys.WARNING_LEAD_MS] = engine.warningLead?.inWholeMilliseconds ?: 0
        prefs[Keys.SNOOZE_MS] = engine.snoozeDuration.inWholeMilliseconds
        prefs[Keys.FRICTION_OVERRIDE_MS] = engine.frictionOverrideDuration.inWholeMilliseconds
        prefs[Keys.COUNTING_MODE] = engine.countingMode.name
        prefs[Keys.BLACKLIST] = engine.blacklist
        prefs[Keys.WHITELIST] = engine.whitelist
        prefs[Keys.NAVIGATION_PACKAGES] = settings.navigationPackages
        prefs[Keys.ALWAYS_ON] = settings.alwaysOn
        prefs[Keys.ALWAYS_PLAY_SOUND] = settings.alwaysPlaySound
    }

    private fun parseLevels(csv: String): List<Duration> =
        csv.split(',').mapNotNull { it.trim().toLongOrNull()?.takeIf { ms -> ms > 0 }?.milliseconds }

    private fun Preferences.positive(key: Preferences.Key<Long>): Duration? =
        this[key]?.takeIf { it > 0 }?.milliseconds
}
