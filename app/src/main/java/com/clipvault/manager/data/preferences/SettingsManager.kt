package com.clipvault.manager.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds = context.dataStore

    // SettingsManager is @Singleton (process-scoped), so this scope lives for
    // the app process — fine for sharing settings flows.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.w("SettingsManager", "scope failure", e)
        }
    )

    val monitoringEnabled: StateFlow<Boolean> =
        ds.data.map { it[KEY_MONITORING] ?: true }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * Suspend helper that returns the actual persisted value of
     * [monitoringEnabled] by reading DataStore directly. Unlike
     * `monitoringEnabled.first()` — which would return the StateFlow's
     * initial value without ever subscribing — this goes through the
     * underlying `ds.data` flow so callers that gate startup work
     * (e.g. starting the foreground clipboard service) on the real
     * preference aren't surprised by a stale default.
     */
    suspend fun observeMonitoringEnabledRaw(): Boolean =
        ds.data.first()[KEY_MONITORING] ?: true

    val maxHistoryDays: StateFlow<Int> =
        ds.data.map { it[KEY_RETENTION_DAYS] ?: 30 }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 30)

    val darkThemeOverride: StateFlow<Int> =
        ds.data.map { it[KEY_THEME] ?: 0 }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    val bubbleEnabled: StateFlow<Boolean> =
        ds.data.map { it[KEY_BUBBLE] ?: false }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val onboardingCompleted: StateFlow<Boolean> =
        ds.data.map { it[KEY_ONBOARDED] ?: false }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val maskSensitiveContent: StateFlow<Boolean> =
        ds.data.map { it[KEY_MASK] ?: false }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val requireBiometric: StateFlow<Boolean> =
        ds.data.map { it[KEY_BIO_LOCK] ?: false }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val maxImageBytes: StateFlow<Int> =
        ds.data.map { it[KEY_MAX_IMAGE_BYTES] ?: DEFAULT_MAX_IMAGE_BYTES }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), DEFAULT_MAX_IMAGE_BYTES)

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

    suspend fun setMaskSensitiveContent(enabled: Boolean) = withContext(Dispatchers.IO) {
        ds.edit { it[KEY_MASK] = enabled }
    }

    suspend fun setRequireBiometric(enabled: Boolean) = withContext(Dispatchers.IO) {
        ds.edit { it[KEY_BIO_LOCK] = enabled }
    }

    suspend fun setMaxImageBytes(bytes: Int) = withContext(Dispatchers.IO) {
        ds.edit { it[KEY_MAX_IMAGE_BYTES] = bytes }
    }

    private companion object {
        const val DEFAULT_MAX_IMAGE_BYTES = 500 * 1024 * 1024
        val KEY_MONITORING: Preferences.Key<Boolean> = booleanPreferencesKey("monitoring_enabled")
        val KEY_RETENTION_DAYS: Preferences.Key<Int> = intPreferencesKey("retention_days")
        val KEY_THEME: Preferences.Key<Int> = intPreferencesKey("theme_mode")
        val KEY_BUBBLE: Preferences.Key<Boolean> = booleanPreferencesKey("bubble_enabled")
        val KEY_ONBOARDED: Preferences.Key<Boolean> = booleanPreferencesKey("onboarding_completed")
        val KEY_MASK: Preferences.Key<Boolean> = booleanPreferencesKey("mask_sensitive")
        val KEY_BIO_LOCK: Preferences.Key<Boolean> = booleanPreferencesKey("require_biometric")
        val KEY_MAX_IMAGE_BYTES: Preferences.Key<Int> = intPreferencesKey("max_image_bytes")
    }
}