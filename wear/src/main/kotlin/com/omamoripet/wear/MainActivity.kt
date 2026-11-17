package com.omamoripet.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.omamoripet.wear.breathing.HapticBreathingController
import com.omamoripet.wear.sensor.BiometricMonitorService
import com.omamoripet.wear.sync.WatchSyncClient
import com.omamoripet.wear.ui.BreathingWatchScreen
import com.omamoripet.wear.ui.DemoSettingsWatchScreen
import com.omamoripet.wear.ui.HomeWatchScreen
import com.omamoripet.wear.ui.OmamoriWearTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_BREATHING = "extra_open_breathing"
        const val EXTRA_TRIGGERED_BY_ALERT = "extra_triggered_by_alert"
    }

    private lateinit var breathingController: HapticBreathingController
    private lateinit var syncClient: WatchSyncClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = (application as OmamoriWearApplication).repository
        syncClient = WatchSyncClient(this)
        breathingController = HapticBreathingController(
            context = this,
            scope = lifecycleScope,
            readCurrentBpm = { repository.uiState.value.snapshot.currentHeartRateBpm },
            onActiveChanged = { active -> repository.setBreathingActive(active) },
            onSessionCompleted = { record ->
                lifecycleScope.launch { syncClient.sendBreathingSession(record) }
            }
        )

        if (BiometricMonitorService.canStartHealthForegroundService(this)) {
            BiometricMonitorService.start(this)
        }
        val openBreathingInitially = intent?.getBooleanExtra(EXTRA_OPEN_BREATHING, false) == true
        val triggeredByAlert = intent?.getBooleanExtra(EXTRA_TRIGGERED_BY_ALERT, false) == true

        setContent {
            val uiState by repository.uiState.collectAsStateWithLifecycle()
            val breathingState by breathingController.state.collectAsStateWithLifecycle()
            val navController = rememberSwipeDismissableNavController()

            var hasSensorPermission by remember { mutableStateOf(checkRequiredPermissions()) }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                hasSensorPermission = checkRequiredPermissions()
                if (BiometricMonitorService.canStartHealthForegroundService(this)) {
                    BiometricMonitorService.start(this)
                }
            }

            LaunchedEffect(Unit) {
                if (!hasSensorPermission) {
                    permissionLauncher.launch(requiredPermissions())
                }
                if (openBreathingInitially) {
                    breathingController.start(triggeredByAlert = triggeredByAlert)
                    navController.navigate("breathing")
                }
            }

            OmamoriWearTheme {
                AppScaffold {
                    SwipeDismissableNavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeWatchScreen(
                                uiState = uiState,
                                hasSensorPermission = hasSensorPermission,
                                onRequestPermissions = { permissionLauncher.launch(requiredPermissions()) },
                                onOpenAppSettings = {
                                    startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", packageName, null)
                                        }
                                    )
                                },
                                onStartBreathing = {
                                    breathingController.start(triggeredByAlert = false)
                                    navController.navigate("breathing")
                                },
                                onStartCalibration = {
                                    BiometricMonitorService.start(this@MainActivity, startCalibration = true)
                                },
                                onOpenSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }
                        composable("breathing") {
                            BreathingWatchScreen(
                                breathingState = breathingState,
                                onStop = {
                                    breathingController.stop()
                                    navController.popBackStack()
                                },
                                onDoneBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("settings") {
                            DemoSettingsWatchScreen(
                                currentScenario = uiState.simulationScenario,
                                alertsEnabled = uiState.preferences.alertsEnabled,
                                onSelectScenario = { repository.setSimulationScenario(it) },
                                onToggleAlerts = {
                                    val cur = uiState.preferences
                                    repository.updatePreferences(cur.copy(alertsEnabled = !cur.alertsEnabled))
                                },
                                onSnooze1h = { repository.snoozeAlertsForMinutes(60) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_BREATHING, false)) {
            breathingController.start(triggeredByAlert = intent.getBooleanExtra(EXTRA_TRIGGERED_BY_ALERT, false))
        }
    }

    private fun requiredPermissions(): Array<String> = buildList {
        add(BiometricMonitorService.heartRatePermission())
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun checkRequiredPermissions(): Boolean =
        BiometricMonitorService.hasHeartRatePermission(this) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
}
