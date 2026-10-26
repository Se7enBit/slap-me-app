package com.noikonomist.slapme.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.noikonomist.slapme.engine.EngineState
import com.noikonomist.slapme.util.SlapLog
import kotlinx.coroutines.flow.first

/** Saves the engine state so a process death or reboot keeps the streak, level, lockout and snoozes. */
class EngineStateRepository(private val store: DataStore<Preferences>) {

    suspend fun load(): EngineState = when (val decoded = EngineStateCodec.decode(store.safeData().first())) {
        EngineStateCodec.Decoded.Empty -> EngineState()
        is EngineStateCodec.Decoded.Ok -> decoded.state
        is EngineStateCodec.Decoded.Unusable -> {
            SlapLog.w("Saved engine state unusable (${decoded.reason}); starting fresh")
            EngineState()
        }
    }

    suspend fun save(state: EngineState) {
        store.edit { EngineStateCodec.encode(state, it) }
    }
}
