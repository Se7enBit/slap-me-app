package com.noikonomist.slapme.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.noikonomist.slapme.data.db.SlapMeDatabase
import com.noikonomist.slapme.data.settings.preferencesStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import org.junit.rules.TemporaryFolder

/** A preferences DataStore on a fresh file; one per test, since DataStore allows one instance per file. */
fun testPreferencesStore(scope: CoroutineScope, folder: TemporaryFolder, name: String = "test"): DataStore<Preferences> =
    preferencesStore(scope) { File(folder.root, "$name.preferences_pb") }

/** Robolectric only. */
fun inMemoryDatabase(): SlapMeDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SlapMeDatabase::class.java)
        .allowMainThreadQueries()
        .build()
