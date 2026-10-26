package com.noikonomist.slapme.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.safeData().map(SettingsCodec::decode).distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    /** Atomic read-modify-write: concurrent updates never overwrite each other. */
    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs -> SettingsCodec.encode(transform(SettingsCodec.decode(prefs)), prefs) }
    }
}
