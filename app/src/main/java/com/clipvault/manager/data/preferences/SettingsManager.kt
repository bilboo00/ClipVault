package com.clipvault.manager.data.preferences

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

@Singleton
open class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds: DataStore<Preferences> by lazy { openDataStore() }

    /**
     * Test seam: production opens the app's `settings` DataStore file, tests
     * override this to point at an isolated temp file so JVM unit tests can
     * exercise the real persistence logic without Robolectric. Lazy so the
     * override is installed before first access.
     */
    @VisibleForTesting
    protected open fun openDataStore(): DataStore<Preferences> = context.settingsDataStore

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

    /** Accent palette id — see `com.clipvault.manager.ui.theme.AppPalette`. */
    val palette: StateFlow<Int> =
        ds.data.map { it[KEY_PALETTE] ?: 0 }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Android 12+ wallpaper-derived colours. Off by default. */
    val dynamicColors: StateFlow<Boolean> =
        ds.data.map { it[KEY_DYNAMIC_COLORS] ?: false }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

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

    /**
     * Recent search queries, most-recent-first, capped. Stored as a single
     * separator-joined string (not a string-set) so ordering survives
     * DataStore round-trips.
     */
    val recentSearches: StateFlow<List<String>> =
        ds.data.map { prefs ->
            prefs[KEY_RECENT_SEARCHES]
                ?.split(RECENT_SEPARATOR)
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.take(MAX_RECENT_SEARCHES)
                ?: emptyList()
        }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun setMonitoring(enabled: Boolean) = withContext(Dispatchers.IO) {
        ds.edit { it[KEY_MONITORING] = enabled }
    }

    suspend fun setRetentionDays(days: Int) = withContext(Dispatchers.IO) {
        ds.edit { it[KEY_RETENTION_DAYS] = days }
    }

    suspend fun setTheme(mode: Int) = withContext(Dispatchers.IO) {
        ds.edit { it[KEY_THEME] = mode }
    }

    suspend fun setPalette(id: Int) = withContext(Dispatchers.IO) {
        ds.edit { it[KEY_PALETTE] = id }
    }

    suspend fun setDynamicColors(enabled: Boolean) = withContext(Dispatchers.IO) {
        ds.edit { it[KEY_DYNAMIC_COLORS] = enabled }
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

    suspend fun addRecentSearch(query: String) = withContext(Dispatchers.IO) {
        // Sanitize the join separator so a query containing it can't split
        // into phantom entries on the next read.
        val q = query.trim().replace(RECENT_SEPARATOR, ' ')
        if (q.isEmpty()) return@withContext
        ds.edit { prefs ->
            val current = prefs[KEY_RECENT_SEARCHES]
                ?.split(RECENT_SEPARATOR)
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            prefs[KEY_RECENT_SEARCHES] = (listOf(q) + current.filter { !it.equals(q, ignoreCase = true) })
                .take(MAX_RECENT_SEARCHES)
                .joinToString(RECENT_SEPARATOR.toString())
        }
    }

    suspend fun clearRecentSearches() = withContext(Dispatchers.IO) {
        ds.edit { it.remove(KEY_RECENT_SEARCHES) }
    }

    private companion object {
        const val DEFAULT_MAX_IMAGE_BYTES = 500 * 1024 * 1024
        const val MAX_RECENT_SEARCHES = 8
        const val RECENT_SEPARATOR = '\u001F'
        val KEY_MONITORING: Preferences.Key<Boolean> = booleanPreferencesKey("monitoring_enabled")
        val KEY_RETENTION_DAYS: Preferences.Key<Int> = intPreferencesKey("retention_days")
        val KEY_THEME: Preferences.Key<Int> = intPreferencesKey("theme_mode")
        val KEY_PALETTE: Preferences.Key<Int> = intPreferencesKey("palette_id")
        val KEY_DYNAMIC_COLORS: Preferences.Key<Boolean> = booleanPreferencesKey("dynamic_colors")
        val KEY_BUBBLE: Preferences.Key<Boolean> = booleanPreferencesKey("bubble_enabled")
        val KEY_ONBOARDED: Preferences.Key<Boolean> = booleanPreferencesKey("onboarding_completed")
        val KEY_MASK: Preferences.Key<Boolean> = booleanPreferencesKey("mask_sensitive")
        val KEY_BIO_LOCK: Preferences.Key<Boolean> = booleanPreferencesKey("require_biometric")
        val KEY_MAX_IMAGE_BYTES: Preferences.Key<Int> = intPreferencesKey("max_image_bytes")
        val KEY_RECENT_SEARCHES: Preferences.Key<String> = stringPreferencesKey("recent_searches")
    }
}