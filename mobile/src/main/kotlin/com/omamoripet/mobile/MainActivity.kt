package com.omamoripet.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omamoripet.mobile.data.MobileDemoMode
import com.omamoripet.mobile.ui.screens.BreathingMobileScreen
import com.omamoripet.mobile.ui.screens.HistoryMobileScreen
import com.omamoripet.mobile.ui.screens.FeelingDetailScreen
import com.omamoripet.mobile.ui.screens.HomeMobileScreen
import com.omamoripet.mobile.ui.screens.OnboardingScreen
import com.omamoripet.mobile.ui.screens.SettingsMobileScreen
import com.omamoripet.mobile.ui.theme.LocalAnimationsEnabled
import com.omamoripet.mobile.ui.theme.OmamoriM3Theme
import com.omamoripet.shared.model.CURRENT_ONBOARDING_VERSION
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class TopDestination(
    val route: String,
    val labelRes: Int,
    val titleRes: Int,
    @param:DrawableRes val selectedIconRes: Int,
    @param:DrawableRes val unselectedIconRes: Int
) {
    NOW(
        route = "now",
        labelRes = R.string.nav_now,
        titleRes = R.string.app_name,
        selectedIconRes = R.drawable.ic_spa_rounded,
        unselectedIconRes = R.drawable.ic_spa_outlined
    ),
    HISTORY(
        route = "history",
        labelRes = R.string.nav_history,
        titleRes = R.string.history_title,
        selectedIconRes = R.drawable.ic_history_rounded,
        unselectedIconRes = R.drawable.ic_history_outlined
    ),
    SETTINGS(
        route = "settings",
        labelRes = R.string.nav_settings,
        titleRes = R.string.nav_settings,
        selectedIconRes = R.drawable.ic_settings_rounded,
        unselectedIconRes = R.drawable.ic_settings_outlined
    )
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val animationsEnabled = LocalAnimationsEnabled.current
    var forceShowOnboarding by remember { mutableStateOf(false) }
    var pendingOpenBreathing by remember { mutableStateOf(false) }
    var pendingTryLaterMessage by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner, repository) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                repository.refreshWatchConnection()
                delay(15_000L)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                repository.refreshRecentFeelingHealth()
                scope.launch {
                    repository.refreshHealthConnect()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hcLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        repository.markHealthConnectPermissionAttempted()
        scope.launch { repository.refreshHealthConnect() }
    }
    val feelingHealthLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        repository.refreshRecentFeelingHealth(force = true)
        scope.launch { repository.refreshHealthConnect() }
    }

    if (!uiState.isOnboardingLoaded) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {}
        return
    }

    val showOnboarding = forceShowOnboarding || uiState.completedOnboardingVersion < CURRENT_ONBOARDING_VERSION
    if (showOnboarding) {
        val initialOnboardingAnswers = remember(forceShowOnboarding, uiState.onboardingAnswers, uiState.preferences.breathingHapticsEnabled) {
            uiState.onboardingAnswers.copy(
                hapticChoice = if (uiState.preferences.breathingHapticsEnabled) {
                    com.omamoripet.shared.model.OnboardingHapticChoice.WITH_HAPTICS
                } else {
                    com.omamoripet.shared.model.OnboardingHapticChoice.VISUAL_ONLY
                }
            )
        }
        OnboardingScreen(
            analyticsTracker = repository.onboardingAnalytics,
            initialAnswers = initialOnboardingAnswers,
            onFinishToBreathing = { answers ->
                repository.completeOnboarding(answers, CURRENT_ONBOARDING_VERSION)
                pendingOpenBreathing = true
                forceShowOnboarding = false
            },
            onFinishToHome = { answers ->
                repository.completeOnboarding(answers, CURRENT_ONBOARDING_VERSION)
                forceShowOnboarding = false
            },
            onSkip = { answers ->
                if (!forceShowOnboarding) {
                    repository.completeOnboarding(answers, CURRENT_ONBOARDING_VERSION)
                }
                forceShowOnboarding = false
            },
            onTryLater = { answers ->
                if (!forceShowOnboarding) {
                    repository.completeOnboarding(answers, CURRENT_ONBOARDING_VERSION)
                }
                pendingTryLaterMessage = true
                forceShowOnboarding = false
            }
        )
        return
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showTopLevelNav = currentDestination?.route !in setOf("breathing", "feeling/{id}")

    LaunchedEffect(pendingOpenBreathing) {
        if (pendingOpenBreathing) {
            pendingOpenBreathing = false
            navController.navigate("breathing") {
                launchSingleTop = true
            }
        }
    }
    val tryLaterSnackbarText = stringResource(R.string.ob_try_later_snackbar)
    LaunchedEffect(pendingTryLaterMessage) {
        if (pendingTryLaterMessage) {
            pendingTryLaterMessage = false
            snackbarHostState.showSnackbar(tryLaterSnackbarText)
        }
    }
    val currentTopDest = TopDestination.entries.firstOrNull { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    } ?: TopDestination.NOW

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        val isExpanded = maxWidth >= 840.dp

        Scaffold(
            modifier = if (showTopLevelNav) {
                Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            } else {
                Modifier
            },
            topBar = {
                if (showTopLevelNav) {
                    CenterAlignedTopAppBar(
                        title = {
                            AnimatedContent(
                                targetState = currentTopDest.titleRes,
                                transitionSpec = {
                                    if (animationsEnabled) {
                                        fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                                    } else {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    }
                                },
                                label = "topBarTitle"
                            ) { titleRes ->
                                Text(stringResource(titleRes))
                            }
                        },
                        scrollBehavior = scrollBehavior
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
                                icon = {
                                    Icon(
                                        painter = painterResource(if (selected) dest.selectedIconRes else dest.unselectedIconRes),
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes), maxLines = 1) }
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
                        Spacer(Modifier.height(12.dp))
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
                                icon = {
                                    Icon(
                                        painter = painterResource(if (selected) dest.selectedIconRes else dest.unselectedIconRes),
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes), maxLines = 1) }
                            )
                        }
                    }
                }

                val topEnter = if (animationsEnabled) fadeIn(tween(180)) else EnterTransition.None
                val topExit = if (animationsEnabled) fadeOut(tween(140)) else ExitTransition.None
                val modalEnter = if (animationsEnabled) {
                    fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 12 }
                } else {
                    EnterTransition.None
                }
                val modalExit = if (animationsEnabled) {
                    fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 12 }
                } else {
                    ExitTransition.None
                }

                NavHost(
                    navController = navController,
                    startDestination = TopDestination.NOW.route,
                    enterTransition = { topEnter },
                    exitTransition = { topExit },
                    popEnterTransition = { topEnter },
                    popExitTransition = { topExit },
                    modifier = Modifier.weight(1f)
                ) {
                    composable(TopDestination.NOW.route) {
                        val notConnectedMsg = stringResource(R.string.snackbar_watch_not_connected)
                        val openPhoneAction = stringResource(R.string.snackbar_watch_open_phone)
                        HomeMobileScreen(
                            uiState = uiState,
                            isExpanded = isExpanded,
                            onOpenBreathing = { navController.navigate("breathing") },
                            onTriggerWatchBreathing = {
                                scope.launch {
                                    val sent = repository.triggerWatchBreathing()
                                    if (!sent) {
                                        val result = snackbarHostState.showSnackbar(
                                            message = notConnectedMsg,
                                            actionLabel = openPhoneAction
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            navController.navigate("breathing")
                                        }
                                    }
                                }
                            },
                            onRefreshAi = { scope.launch { repository.refreshAiAdvice() } },
                            onRequestHealthConnect = {
                                hcLauncher.launch(repository.healthConnectManager.requiredPermissions)
                            },
                            onOpenHealthConnectSettings = { openHealthConnectSettings(context) },
                            onUpdateHealthConnect = { openHealthConnectUpdate(context) },
                            onDismissHealthConnect = { repository.dismissHealthConnectOnHome() },
                            onDismissWatchGuide = { repository.dismissWatchGuideOnHome() },
                            onExitDemo = { repository.setDemoMode(MobileDemoMode.LIVE_WATCH) },
                            onRecordFeeling = repository::recordFeeling
                        )
                    }
                    composable(TopDestination.HISTORY.route) {
                        HistoryMobileScreen(
                            uiState = uiState,
                            onTryBreathing = { navController.navigate("breathing") },
                            onOpenFeeling = { navController.navigate("feeling/${it.id}") }
                        )
                    }
                    composable("feeling/{id}") { entry ->
                        val recordId = entry.arguments?.getString("id")
                        val record = uiState.feelingRecords.firstOrNull { it.id == recordId }
                        LaunchedEffect(recordId) { recordId?.let { repository.refreshFeelingHealth(it) } }
                        FeelingDetailScreen(
                            record = record,
                            breathingSessions = uiState.breathingSessions,
                            onBack = { navController.popBackStack() },
                            onSaveContext = repository::updateFeelingContext
                        )
                    }
                    composable(TopDestination.SETTINGS.route) {
                        SettingsMobileScreen(
                            uiState = uiState,
                            onUpdatePreferences = { repository.updatePreferences(it) },
                            onRequestHealthConnect = {
                                hcLauncher.launch(repository.healthConnectManager.requiredPermissions)
                            },
                            onRequestFeelingHealth = {
                                feelingHealthLauncher.launch(repository.healthConnectManager.feelingPermissions)
                            },
                            onRequestFeelingPeriod = {
                                feelingHealthLauncher.launch(repository.healthConnectManager.periodPermissions)
                            },
                            onOpenHealthConnectSettings = { openHealthConnectSettings(context) },
                            onUpdateHealthConnect = { openHealthConnectUpdate(context) },
                            onPrepareGeminiNano = { scope.launch { repository.prepareGeminiNano() } },
                            onOpenPrivacyRationale = {
                                context.startActivity(Intent(context, PrivacyRationaleActivity::class.java))
                            },
                            onReplayOnboarding = { forceShowOnboarding = true },
                            onResetOnboardingAnswers = { repository.resetOnboardingAndAnswers() },
                            onSelectDemoMode = { repository.setDemoMode(it) },
                            onClearDemoHistory = { repository.clearSimulatedHistory() }
                        )
                    }
                    composable(
                        route = "breathing",
                        enterTransition = { modalEnter },
                        exitTransition = { modalExit },
                        popEnterTransition = { topEnter },
                        popExitTransition = { modalExit }
                    ) {
                        BreathingMobileScreen(
                            hapticsEnabled = uiState.preferences.breathingHapticsEnabled,
                            isFirstBreathing = !uiState.hasCompletedFirstBreathing,
                            onBack = { navController.popBackStack() },
                            onCompleted = { id, startedAt, duration ->
                                repository.addLocalBreathingCompletion(id, startedAt, duration)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun openHealthConnectSettings(context: Context) {
    val hcIntent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
    val launched = runCatching {
        context.startActivity(hcIntent)
        true
    }.getOrDefault(false)
    if (!launched) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            )
        }
    }
}

private fun openHealthConnectUpdate(context: Context) {
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        "market://details?id=com.google.android.apps.healthdata".toUri()
    )
    val launched = runCatching {
        context.startActivity(marketIntent)
        true
    }.getOrDefault(false)
    if (!launched) {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata".toUri()
                )
            )
        }
    }
}
