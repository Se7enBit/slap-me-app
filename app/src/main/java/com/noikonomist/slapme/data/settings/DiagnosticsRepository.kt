package com.noikonomist.slapme.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A time the monitor was not running while the phone was in use (likely killed by the system). */
data class ServiceGapRecord(val atMillis: Long, val duration: Duration)

/** Health signals for the "your phone may be killing Slap Me" tip. */
class DiagnosticsRepository(private val store: DataStore<Preferences>) {

    val lastServiceGap: Flow<ServiceGapRecord?> = store.safeData().map { prefs ->
        val at = prefs[LAST_GAP_AT] ?: return@map null
        ServiceGapRecord(at, (prefs[LAST_GAP_MS] ?: 0).milliseconds)
    }

    suspend fun recordServiceGap(atMillis: Long, duration: Duration) {
        store.edit {
            it[LAST_GAP_AT] = atMillis
            it[LAST_GAP_MS] = duration.inWholeMilliseconds
        }
    }

    private companion object {
        val LAST_GAP_AT = longPreferencesKey("last_service_gap_at")
        val LAST_GAP_MS = longPreferencesKey("last_service_gap_ms")
    }
}
