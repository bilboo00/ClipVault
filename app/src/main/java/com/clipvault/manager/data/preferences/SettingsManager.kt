package com.clipvault.manager.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds = context.dataStore

    val monitoringEnabled: Flow<Boolean> =
        ds.data.map { it[KEY_MONITORING] ?: true }.distinctUntilChanged().flowOn(Dispatchers.IO)

    val maxHistoryDays: Flow<Int> =
        ds.data.map { it[KEY_RETENTION_DAYS] ?: 30 }.distinctUntilChanged().flowOn(Dispatchers.IO)

    val darkThemeOverride: Flow<Int> =
        ds.data.map { it[KEY_THEME] ?: 0 }.distinctUntilChanged().flowOn(Dispatchers.IO)

    val bubbleEnabled: Flow<Boolean> =
        ds.data.map { it[KEY_BUBBLE] ?: false }.distinctUntilChanged().flowOn(Dispatchers.IO)

    val onboardingCompleted: Flow<Boolean> =
        ds.data.map { it[KEY_ONBOARDED] ?: false }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun setMonitoring(enabled: Boolean) = withContext(Dispatchers.IO) {
        ds.edit { it[KEY_MONITORING] = enabled }
    }

    suspend fun setRetentionDays(days: Int) = withContext(Dispatchers.IO) {
        ds.edit { it[KEY_RETENTION_DAYS] = days }
    }

    suspend fun setTheme(mode: Int) = withContext(Dispatchers.IO) {
        ds.edit { it[KEY_THEME] = mode }
    }

    suspend fun setBubbleEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        ds.edit { it[KEY_BUBBLE] = enabled }
    }

    suspend fun setOnboardingCompleted(done: Boolean) = withContext(Dispatchers.IO) {
        ds.edit { it[KEY_ONBOARDED] = done }
    }

    private companion object {
        val KEY_MONITORING: Preferences.Key<Boolean> = booleanPreferencesKey("monitoring_enabled")
        val KEY_RETENTION_DAYS: Preferences.Key<Int> = intPreferencesKey("retention_days")
        val KEY_THEME: Preferences.Key<Int> = intPreferencesKey("theme_mode")
        val KEY_BUBBLE: Preferences.Key<Boolean> = booleanPreferencesKey("bubble_enabled")
        val KEY_ONBOARDED: Preferences.Key<Boolean> = booleanPreferencesKey("onboarding_completed")
    }
}