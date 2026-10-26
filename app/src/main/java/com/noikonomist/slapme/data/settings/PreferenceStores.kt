package com.noikonomist.slapme.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import com.noikonomist.slapme.util.SlapLog
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * A preferences DataStore that starts empty instead of crashing when its file is corrupt. Uses the
 * Okio storage, whose atomic replace also works on Windows (where the JVM unit tests run).
 */
fun preferencesStore(scope: CoroutineScope, produceFile: () -> File): DataStore<Preferences> =
    DataStoreFactory.create(
        storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer, producePath = { produceFile().toOkioPath() }),
        corruptionHandler = ReplaceFileCorruptionHandler { e ->
            SlapLog.e("Preferences file corrupt; starting empty", e)
            emptyPreferences()
        },
        scope = scope,
    )

/** Read errors (I/O) give empty preferences, i.e. defaults, rather than ending the flow. */
internal fun DataStore<Preferences>.safeData(): Flow<Preferences> = data.catch { e ->
    if (e is IOException) {
        SlapLog.e("Reading preferences failed; using defaults", e)
        emit(emptyPreferences())
    } else {
        throw e
    }
}
