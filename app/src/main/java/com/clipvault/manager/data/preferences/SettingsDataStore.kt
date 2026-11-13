package com.clipvault.manager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The single process-wide delegate for the `settings` preferences file.
 *
 * DataStore rejects a second live instance for the same file ("There are
 * multiple DataStores active for the same file"). Both [SettingsManager] and
 * the Glance widget read these preferences, and the widget's read is wrapped
 * in a catch block — a duplicate instance there would silently report masking
 * as disabled. Keeping one delegate guarantees they share the same store.
 */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
