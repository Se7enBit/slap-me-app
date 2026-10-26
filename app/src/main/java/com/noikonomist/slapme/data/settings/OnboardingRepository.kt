package com.noikonomist.slapme.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class OnboardingState(
    val completed: Boolean = false,
    /** The prominent usage-access disclosure was accepted; only then may the app open those settings. */
    val usageDisclosureAccepted: Boolean = false,
    val batteryTipDismissedAtMillis: Long? = null,
)

/** Onboarding flags. Lives in the settings file under its own keys; [SettingsCodec] never touches them. */
class OnboardingRepository(private val store: DataStore<Preferences>) {

    val state: Flow<OnboardingState> = store.safeData().map { prefs ->
        OnboardingState(
            completed = prefs[COMPLETED] ?: false,
            usageDisclosureAccepted = prefs[DISCLOSURE_ACCEPTED] ?: false,
            batteryTipDismissedAtMillis = prefs[BATTERY_TIP_DISMISSED_AT],
        )
    }.distinctUntilChanged()

    suspend fun complete() {
        store.edit { it[COMPLETED] = true }
    }

    suspend fun acceptUsageDisclosure() {
        store.edit { it[DISCLOSURE_ACCEPTED] = true }
    }

    suspend fun dismissBatteryTip(atMillis: Long) {
        store.edit { it[BATTERY_TIP_DISMISSED_AT] = atMillis }
    }

    private companion object {
        val COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DISCLOSURE_ACCEPTED = booleanPreferencesKey("usage_disclosure_accepted")
        val BATTERY_TIP_DISMISSED_AT = longPreferencesKey("battery_tip_dismissed_at")
    }
}
