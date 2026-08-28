package com.clipvault.manager.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.security.BiometricManager
import com.clipvault.manager.util.ImageCopier
import kotlinx.coroutines.flow.MutableStateFlow
import com.clipvault.manager.ui.detail.ClipDetailScreen
import com.clipvault.manager.ui.home.HomeScreen
import com.clipvault.manager.ui.nav.ClipVaultBottomBar
import com.clipvault.manager.ui.nav.Route
import com.clipvault.manager.ui.onboarding.OnboardingScreen
import com.clipvault.manager.ui.search.SearchScreen
import com.clipvault.manager.ui.settings.SettingsScreen
import com.clipvault.manager.ui.settings.SettingsViewModel
import com.clipvault.manager.ui.collections.CollectionsScreen
import com.clipvault.manager.ui.lock.LockScreen
import com.clipvault.manager.ui.snippets.SnippetsScreen
import com.clipvault.manager.ui.stats.StatsScreen
import com.clipvault.manager.ui.tags.TagsScreen
import com.clipvault.manager.ui.theme.ClipboardManagerTheme
import com.clipvault.manager.ui.theme.Motion
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var repository: ClipboardRepository
    @Inject lateinit var settingsManager: SettingsManager
    @Inject lateinit var biometricManager: BiometricManager

    /** Clip id extracted from a deep link; consumed by the NavHost. */
    private val deepLinkClipId = MutableStateFlow<Long?>(null)

    /**
     * True once a share (SEND / SEND_MULTIPLE) intent was imported. Survives
     * config changes via [onSaveInstanceState] so a rotation or process-restore
     * doesn't re-copy the shared image into storage again.
     */
    private var consumedShareIntent = false

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* user choice */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val themeMode = settingsManager.darkThemeOverride.value
        val systemDark = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (themeMode) {
            1 -> false
            2, 3 -> true
            else -> systemDark
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ) { isDark },
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ) { isDark }
        )
        consumedShareIntent = savedInstanceState?.getBoolean(STATE_SHARE_CONSUMED, false) ?: false
        ensureNotificationPermission()

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
            val onboardingDone by settingsViewModel.onboardingDone.collectAsStateWithLifecycle(true)
            var showOnboarding by remember { mutableStateOf(!onboardingDone) }

            // Biometric lock state. rememberSaveable survives config changes so
            // a successful unlock isn't thrown away by a rotation.
            val requireBiometric by settingsManager.requireBiometric.collectAsStateWithLifecycle(false)
            var appLocked by rememberSaveable(requireBiometric) { mutableStateOf(requireBiometric) }
            // If the device has no PIN/pattern/fingerprint at all, the prompt
            // can never succeed — don't lock the user out of the app entirely.
            val canAuthenticate = remember(requireBiometric) {
                biometricManager.canAuthenticate(this@MainActivity)
            }

            // When onboarding state changes, sync the shown screen
            LaunchedEffect(onboardingDone) {
                showOnboarding = !onboardingDone
            }

            // Auto-trigger the biometric prompt when the lock screen is shown
            // so the user doesn't have to tap "Unlock" first. The prompt is
            // started only while the activity is RESUMED — androidx.biometric
            // requires a resumed host, and firing from first-frame composition
            // crashed the process on cold start. Keyed on the lock state's
            // edge so rotation doesn't re-prompt mid-session.
            var hasPromptedThisLock by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(appLocked, canAuthenticate) {
                if (appLocked && canAuthenticate && !hasPromptedThisLock) {
                    hasPromptedThisLock = true
                    this@MainActivity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        biometricManager.prompt(
                            activity = this@MainActivity,
                            title = "Unlock ClipVault",
                            subtitle = "Authenticate to access your clipboard",
                            onSuccess = { appLocked = false },
                            onFailure = { /* keep locked; LockScreen lets user retry */ },
                            onCancel = { /* user dismissed; keep locked */ }
                        )
                    }
                }
                if (!appLocked) hasPromptedThisLock = false
            }

            ClipboardManagerTheme(themeOverride = settingsState.themeMode) {
                if (appLocked && requireBiometric && canAuthenticate) {
                    LockScreen(
                        activity = this@MainActivity,
                        canAuthenticate = canAuthenticate,
                        onAuthenticate = {
                            biometricManager.prompt(
                                activity = this@MainActivity,
                                title = "Unlock ClipVault",
                                subtitle = "Authenticate to access your clipboard",
                                onSuccess = { appLocked = false },
                                onFailure = { msg ->
                                    android.widget.Toast.makeText(
                                        this@MainActivity,
                                        "Authentication failed: $msg",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onCancel = {
                                    // User dismissed the prompt — stay locked,
                                    // no toast spam.
                                }
                            )
                        }
                    )
                } else if (showOnboarding) {
                    OnboardingScreen(
                        onFinished = { showOnboarding = false }
                    )
                } else {
                    val nav = rememberNavController()
                    val navBackStackEntry by nav.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val pendingDeepLinkId by deepLinkClipId.collectAsStateWithLifecycle()

                    // Navigate to the clip targeted by a deep link, then clear it
                    // so rotation / resume doesn't re-trigger.
                    LaunchedEffect(pendingDeepLinkId) {
                        val id = pendingDeepLinkId ?: return@LaunchedEffect
                        nav.navigate(Route.Detail.build(id))
                        deepLinkClipId.value = null
                    }

                    Scaffold(
                        bottomBar = {
                            if (currentRoute != Route.Detail.path &&
                                currentRoute != Route.Tags.path &&
                                currentRoute != Route.Collections.path
                            ) {
                                ClipVaultBottomBar(
                                    currentRoute = currentRoute,
                                    onNavigate = { route ->
                                        nav.navigate(route) {
                                            popUpTo(nav.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    ) { padding ->
                        NavHost(
                            navController = nav,
                            startDestination = Route.Home.path,
                            modifier = androidx.compose.ui.Modifier.padding(padding),
                            enterTransition = {
                                slideInHorizontally(
                                    animationSpec = tween(Motion.Medium, easing = Motion.EmphasizedDecelerate)
                                ) { it / 4 } + fadeIn(animationSpec = tween(Motion.Medium))
                            },
                            exitTransition = {
                                slideOutHorizontally(
                                    animationSpec = tween(Motion.Medium, easing = Motion.EmphasizedAccelerate)
                                ) { -it / 5 } + fadeOut(animationSpec = tween(Motion.Medium))
                            },
                            popEnterTransition = {
                                slideInHorizontally(
                                    animationSpec = tween(Motion.Medium, easing = Motion.EmphasizedDecelerate)
                                ) { -it / 4 } + fadeIn(animationSpec = tween(Motion.Medium))
                            },
                            popExitTransition = {
                                slideOutHorizontally(
                                    animationSpec = tween(Motion.Medium, easing = Motion.EmphasizedAccelerate)
                                ) { it / 5 } + fadeOut(animationSpec = tween(Motion.Medium))
                            }
                        ) {
                            composable(Route.Home.path) {
                                HomeScreen(
                                    onOpenDetail = { clipId ->
                                        nav.navigate(Route.Detail.build(clipId))
                                    }
                                )
                            }
                            composable(Route.Search.path) {
                                SearchScreen(
                                    onOpenDetail = { clipId ->
                                        nav.navigate(Route.Detail.build(clipId))
                                    }
                                )
                            }
                            composable(Route.Settings.path) {
                                SettingsScreen(
                                    onNavigate = { route ->
                                        nav.navigate(route) {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable(Route.Snippets.path) {
                                SnippetsScreen()
                            }
                            composable(Route.Stats.path) {
                                StatsScreen()
                            }
                            composable(Route.Tags.path) {
                                TagsScreen(onBack = { nav.popBackStack() })
                            }
                            composable(Route.Collections.path) {
                                CollectionsScreen(onBack = { nav.popBackStack() })
                            }
                            composable(
                                route = Route.Detail.path,
                                arguments = listOf(navArgument(Route.Detail.ARG) {
                                    type = NavType.LongType
                                })
                            ) { backStack ->
                                val id = backStack.arguments?.getLong(Route.Detail.ARG) ?: 0L
                                ClipDetailScreen(
                                    clipId = id,
                                    onBack = { nav.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent.getBooleanExtra(EXTRA_FROM_TILE, false) ||
            intent.getBooleanExtra(EXTRA_FROM_BUBBLE, false)
        ) {
            intent.removeExtra(EXTRA_FROM_TILE)
            intent.removeExtra(EXTRA_FROM_BUBBLE)
            lifecycleScope.launch { readAndSaveClipboard() }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A brand-new intent must be processed; only the already-consumed one
        // is suppressed on recreation.
        consumedShareIntent = false
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SHARE_CONSUMED, consumedShareIntent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (consumedShareIntent) return
                handleSharedContent(intent)
                consumedShareIntent = true
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (consumedShareIntent) return
                handleSharedMultiple(intent)
                consumedShareIntent = true
            }
            Intent.ACTION_VIEW -> handleDeepLink(intent.data)
        }
    }

    private fun handleSharedContent(intent: Intent) {
        val stream = if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (stream != null) {
            saveSharedImage(stream)
        } else {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_HTML_TEXT)
            if (!text.isNullOrBlank()) {
                lifecycleScope.launch { repository.saveIfNew(text, sourceLabel = "share") }
            }
        }
        // Consume immediately so rotation / re-entry doesn't re-import.
        intent.action = null
        intent.data = null
    }

    private fun handleSharedMultiple(intent: Intent) {
        val streams = if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        streams?.forEach { saveSharedImage(it) }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank()) {
            lifecycleScope.launch { repository.saveIfNew(text, sourceLabel = "share") }
        }
        intent.action = null
        intent.data = null
    }

    private fun saveSharedImage(uri: Uri) {
        lifecycleScope.launch {
            val savedPath = ImageCopier.copyToInternalStorage(this@MainActivity, uri)
            if (savedPath != null) {
                repository.saveImage(savedPath, sourceLabel = "share")
            }
        }
    }

    private fun handleDeepLink(uri: Uri?) {
        uri ?: return
        // clipvault://clip/{id} and https://clipvault.app/clip/{id}
        val clipId = when (uri.host) {
            "clip", "clipvault.app" -> uri.lastPathSegment?.toLongOrNull()
            else -> null
        } ?: return
        if (clipId > 0) deepLinkClipId.value = clipId
        // Consume the intent so onResume / re-entry doesn't re-trigger.
        intent.action = null
        intent.data = null
    }

    private fun readAndSaveClipboard() {
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData? = cm.primaryClip
            if (clip == null || clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
            if (text.isNotBlank()) {
                lifecycleScope.launch { repository.saveIfNew(text, sourceLabel = "launch") }
            }
        } catch (_: SecurityException) {
            // Defensive
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        const val EXTRA_FROM_TILE = "from_tile"
        const val EXTRA_FROM_BUBBLE = "from_bubble"
        private const val STATE_SHARE_CONSUMED = "share_consumed"
    }
}