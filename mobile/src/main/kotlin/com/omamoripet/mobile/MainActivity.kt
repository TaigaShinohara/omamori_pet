package com.omamoripet.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omamoripet.mobile.ui.screens.BreathingMobileScreen
import com.omamoripet.mobile.ui.screens.HistoryMobileScreen
import com.omamoripet.mobile.ui.screens.HomeMobileScreen
import com.omamoripet.mobile.ui.screens.SettingsMobileScreen
import com.omamoripet.mobile.ui.theme.OmamoriM3Theme
import kotlinx.coroutines.launch

private enum class TopDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    NOW("now", R.string.nav_now, Icons.Rounded.Spa),
    HISTORY("history", R.string.nav_history, Icons.Rounded.History),
    SETTINGS("settings", R.string.nav_settings, Icons.Rounded.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as OmamoriMobileApplication).repository

        setContent {
            OmamoriM3Theme {
                OmamoriMobileRoot(repository = repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OmamoriMobileRoot(repository: com.omamoripet.mobile.data.MobileRepository) {
    val uiState by repository.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val hcLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        scope.launch { repository.refreshHealthConnect() }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showTopLevelNav = currentDestination?.route != "breathing"

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                if (showTopLevelNav) {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(R.string.app_name)) }
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (showTopLevelNav && !useRail) {
                    NavigationBar {
                        TopDestination.entries.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(TopDestination.NOW.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = null) },
                                label = { Text(stringResource(dest.labelRes)) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useRail) {
                    NavigationRail {
                        TopDestination.entries.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(TopDestination.NOW.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = null) },
                                label = { Text(stringResource(dest.labelRes)) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = TopDestination.NOW.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(TopDestination.NOW.route) {
                        val notConnectedMsg = stringResource(R.string.snackbar_watch_not_connected)
                        HomeMobileScreen(
                            uiState = uiState,
                            onOpenBreathing = { navController.navigate("breathing") },
                            onTriggerWatchBreathing = {
                                scope.launch {
                                    val sent = repository.triggerWatchBreathing()
                                    if (!sent) {
                                        snackbarHostState.showSnackbar(notConnectedMsg)
                                        navController.navigate("breathing")
                                    }
                                }
                            },
                            onRefreshAi = { scope.launch { repository.refreshAiAdvice() } },
                            onPrepareGeminiNano = { scope.launch { repository.prepareGeminiNano() } },
                            onRequestHealthConnect = {
                                hcLauncher.launch(repository.healthConnectManager.requiredPermissions)
                            }
                        )
                    }
                    composable(TopDestination.HISTORY.route) {
                        HistoryMobileScreen(
                            uiState = uiState,
                            onTryBreathing = { navController.navigate("breathing") }
                        )
                    }
                    composable(TopDestination.SETTINGS.route) {
                        SettingsMobileScreen(
                            uiState = uiState,
                            onUpdatePreferences = { repository.updatePreferences(it) },
                            onRequestHealthConnect = {
                                hcLauncher.launch(repository.healthConnectManager.requiredPermissions)
                            },
                            onSelectDemoMode = { repository.setDemoMode(it) },
                            onClearDemoHistory = { repository.clearSimulatedHistory() }
                        )
                    }
                    composable("breathing") {
                        BreathingMobileScreen(
                            onBack = { navController.popBackStack() },
                            onCompleted = { duration ->
                                repository.addLocalBreathingCompletion(duration)
                            }
                        )
                    }
                }
            }
        }
    }
}
