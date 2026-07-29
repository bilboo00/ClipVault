package com.clipvault.manager.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.ui.detail.ClipDetailScreen
import com.clipvault.manager.ui.home.HomeScreen
import com.clipvault.manager.ui.nav.ClipVaultBottomBar
import com.clipvault.manager.ui.nav.Route
import com.clipvault.manager.ui.onboarding.OnboardingScreen
import com.clipvault.manager.ui.search.SearchScreen
import com.clipvault.manager.ui.settings.SettingsScreen
import com.clipvault.manager.ui.settings.SettingsViewModel
import com.clipvault.manager.ui.collections.CollectionsScreen
import com.clipvault.manager.ui.snippets.SnippetsScreen
import com.clipvault.manager.ui.stats.StatsScreen
import com.clipvault.manager.ui.tags.TagsScreen
import com.clipvault.manager.ui.theme.ClipboardManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var repository: ClipboardRepository

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* user choice */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureNotificationPermission()

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
            val onboardingDone by settingsViewModel.onboardingDone.collectAsStateWithLifecycle(true)
            var showOnboarding by remember { mutableStateOf(!onboardingDone) }

            // When onboarding state changes, sync the shown screen
            LaunchedEffect(onboardingDone) {
                showOnboarding = !onboardingDone
            }

            ClipboardManagerTheme(themeOverride = settingsState.themeMode) {
                if (showOnboarding) {
                    OnboardingScreen(
                        onFinished = { showOnboarding = false }
                    )
                } else {
                    val nav = rememberNavController()
                    val navBackStackEntry by nav.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    Scaffold(
                        bottomBar = {
                            if (currentRoute != Route.Detail.path) {
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
                            enterTransition = { EnterTransition.None },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { ExitTransition.None }
                        ) {
                            composable(Route.Home.path) {
                                HomeScreen(
                                    onOpenDetail = { clipId ->
                                        nav.navigate(Route.Detail.build(clipId))
                                    }
                                )
                            }
                            composable(Route.Search.path) {
                                SearchScreen()
                            }
                            composable(Route.Settings.path) {
                                SettingsScreen()
                            }
                            composable(Route.Snippets.path) {
                                SnippetsScreen()
                            }
                            composable(Route.Stats.path) {
                                StatsScreen()
                            }
                            composable(Route.Tags.path) {
                                TagsScreen()
                            }
                            composable(Route.Collections.path) {
                                CollectionsScreen()
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
                                    onBack = { nav.popBackStack() },
                                    onDeleted = { nav.popBackStack() }
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
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> handleSharedText(intent)
            Intent.ACTION_VIEW -> handleDeepLink(intent.data)
        }
    }

    private fun handleSharedText(intent: Intent) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        lifecycleScope.launch {
            repository.saveIfNew(text, sourceLabel = "share")
            intent.action = null
        }
    }

    private fun handleDeepLink(uri: Uri?) {
        uri ?: return
        val pathSegments = uri.pathSegments
        if (uri.host == "clip" && pathSegments.size >= 2) {
            val clipId = pathSegments[1].toLongOrNull() ?: return
            startActivity(intent.apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra(EXTRA_DEEP_LINK_CLIP_ID, clipId)
            })
        }
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
        const val EXTRA_DEEP_LINK_CLIP_ID = "deep_link_clip_id"
    }
}