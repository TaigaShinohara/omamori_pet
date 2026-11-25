package com.omamoripet.mobile.ui.screens

import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omamoripet.mobile.R
import com.omamoripet.mobile.ai.AiBackendStatus
import com.omamoripet.mobile.data.MobileDemoMode
import com.omamoripet.mobile.data.MobileUiState
import com.omamoripet.mobile.data.FeelingRecord
import com.omamoripet.mobile.health.HealthConnectAvailability
import com.omamoripet.mobile.ui.components.OmamoriPetMobileAvatar
import com.omamoripet.mobile.ui.theme.LocalAnimationsEnabled
import com.omamoripet.mobile.ui.theme.OmamoriMotion
import com.omamoripet.shared.domain.BreathingTimeline
import com.omamoripet.shared.model.AlertPreferences
import com.omamoripet.shared.model.BreathingSessionRecord
import com.omamoripet.shared.model.CalmEventAction
import com.omamoripet.shared.model.CalmEventRecord
import com.omamoripet.shared.model.Feeling
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.model.PetBodyTone
import com.omamoripet.shared.model.PetExpressionMode
import com.omamoripet.shared.model.PetVisualState
import com.omamoripet.shared.model.OnboardingDeviceStyle
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun HomeMobileScreen(
    uiState: MobileUiState,
    isExpanded: Boolean = false,
    onOpenBreathing: () -> Unit,
    onTriggerWatchBreathing: () -> Unit,
    onRefreshAi: () -> Unit,
    onRequestHealthConnect: () -> Unit,
    onOpenHealthConnectSettings: () -> Unit,
    onUpdateHealthConnect: () -> Unit,
    onDismissHealthConnect: () -> Unit,
    onDismissWatchGuide: () -> Unit,
    onExitDemo: () -> Unit,
    onRecordFeeling: suspend (Feeling) -> Unit,
    modifier: Modifier = Modifier
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val feeling = activeFeelingFace(uiState.feelingRecords.firstOrNull())
    var showFeelingDialog by rememberSaveable { mutableStateOf(false) }
    if (showFeelingDialog) {
        FeelingRecordDialog(onDismiss = { showFeelingDialog = false }, onRecord = onRecordFeeling)
    }
    var showSecondary by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(animationsEnabled) {
        if (animationsEnabled && !showSecondary) {
            kotlinx.coroutines.delay(70L)
        }
        showSecondary = true
    }

    val hasWatchData = uiState.lastWatchSyncMs != null
    val needsHealthConnectAction =
        uiState.healthConnectAvailability != HealthConnectAvailability.UNAVAILABLE &&
            !uiState.hasHealthConnectPermission &&
            !uiState.hcDismissedOnHome &&
            uiState.onboardingAnswers.deviceStyle != OnboardingDeviceStyle.PHONE_ONLY
    val showWatchGuide =
        uiState.onboardingAnswers.deviceStyle == OnboardingDeviceStyle.HAS_WATCH &&
            !hasWatchData &&
            !uiState.isSimulated &&
            !uiState.watchGuideDismissedOnHome

    val secondaryEnter = if (animationsEnabled) fadeIn(tween(220)) else EnterTransition.None

    if (isExpanded) {
        // Expanded 画面 (>= 840dp): 主役の Hero と補助の Reflection を Supporting Pane として並置
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = modifier.fillMaxSize()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .widthIn(max = 1120.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Column(modifier = Modifier.weight(1.1f)) {
                    HeroStatusCard(
                        uiState = uiState,
                        feeling = feeling,
                        onOpenFeeling = { showFeelingDialog = true },
                        onOpenBreathing = onOpenBreathing,
                        onTriggerWatchBreathing = onTriggerWatchBreathing,
                        onExitDemo = onExitDemo
                    )
                }
                AnimatedVisibility(
                    visible = showSecondary,
                    enter = secondaryEnter,
                    modifier = Modifier.weight(0.9f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (showWatchGuide) {
                            WatchSetupGuideCard(onDismiss = onDismissWatchGuide)
                        }
                        AiReflectionCard(
                            uiState = uiState,
                            onRefreshAi = onRefreshAi
                        )
                        if (needsHealthConnectAction) {
                            HealthConnectRecoveryCard(
                                availability = uiState.healthConnectAvailability,
                                permissionAttempted = uiState.hcPermissionAttempted,
                                onRequestHealthConnect = onRequestHealthConnect,
                                onOpenSettings = onOpenHealthConnectSettings,
                                onUpdateHealthConnect = onUpdateHealthConnect,
                                onDismiss = onDismissHealthConnect
                            )
                        }
                    }
                }
            }
        }
    } else {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = modifier.fillMaxSize()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                HeroStatusCard(
                    uiState = uiState,
                    feeling = feeling,
                    onOpenFeeling = { showFeelingDialog = true },
                    onOpenBreathing = onOpenBreathing,
                    onTriggerWatchBreathing = onTriggerWatchBreathing,
                    onExitDemo = onExitDemo
                )

                AnimatedVisibility(
                    visible = showSecondary,
                    enter = secondaryEnter
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (showWatchGuide) {
                            WatchSetupGuideCard(onDismiss = onDismissWatchGuide)
                        }

                        AiReflectionCard(
                            uiState = uiState,
                            onRefreshAi = onRefreshAi
                        )

                        // 連携済みの日常利用時やスマホ単体希望時は設定側へ任せ、必要時だけ穏やかに案内を出す
                        if (needsHealthConnectAction) {
                            HealthConnectRecoveryCard(
                                availability = uiState.healthConnectAvailability,
                                permissionAttempted = uiState.hcPermissionAttempted,
                                onRequestHealthConnect = onRequestHealthConnect,
                                onOpenSettings = onOpenHealthConnectSettings,
                                onUpdateHealthConnect = onUpdateHealthConnect,
                                onDismiss = onDismissHealthConnect
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HeroStatusCard(
    uiState: MobileUiState,
    feeling: Feeling?,
    onOpenFeeling: () -> Unit,
    onOpenBreathing: () -> Unit,
    onTriggerWatchBreathing: () -> Unit,
    onExitDemo: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val animationsEnabled = LocalAnimationsEnabled.current
    val colorSpec = if (animationsEnabled) {
        OmamoriMotion.effectsSlow<androidx.compose.ui.graphics.Color>()
    } else {
        snap()
    }

    val hasWatchData = uiState.lastWatchSyncMs != null
    val isPhoneOnlyIdle = !hasWatchData && !uiState.isSimulated
    val visualState = uiState.petVisualState
    val targetContainerColor = if (visualState.tone == PetBodyTone.DATA_MISSING) {
        cs.surfaceContainerHigh
    } else {
        cs.primaryContainer
    }
    val targetContentColor = if (visualState.tone == PetBodyTone.DATA_MISSING) {
        cs.onSurface
    } else {
        cs.onPrimaryContainer
    }
    val containerColor by animateColorAsState(targetContainerColor, animationSpec = colorSpec, label = "heroBg")
    val contentColor by animateColorAsState(targetContentColor, animationSpec = colorSpec, label = "heroFg")

    val delta = uiState.currentHeartRateBpm?.minus(uiState.restingBaselineBpm)
    val stateTitle = if (isPhoneOnlyIdle) {
        stringResource(R.string.state_phone_ready_title)
    } else {
        stringResource(resolvePetStateLabelRes(uiState.omamoriState, uiState.petHeartRateColor))
    }
    val stateDesc = when {
        isPhoneOnlyIdle && uiState.isWatchConnected -> stringResource(R.string.state_watch_paired_waiting_desc)
        isPhoneOnlyIdle -> stringResource(R.string.state_phone_ready_desc)
        uiState.omamoriState == OmamoriState.ELEVATED_AT_REST -> stringResource(R.string.state_elevated_desc, delta ?: 0)
        uiState.omamoriState == OmamoriState.BREATHING -> stringResource(R.string.state_breathing_desc)
        else -> ""
    }
    val motionIconRes = when (uiState.motionState) {
        MotionState.STILL_CONFIRMED -> R.drawable.ic_self_improvement_rounded
        MotionState.RECENT_MOVEMENT -> R.drawable.ic_directions_walk_rounded
        MotionState.OFF_BODY -> R.drawable.ic_watch_off_rounded
    }
    val motionText = when (uiState.motionState) {
        MotionState.STILL_CONFIRMED -> stringResource(R.string.motion_still)
        MotionState.RECENT_MOVEMENT -> stringResource(R.string.motion_moving)
        MotionState.OFF_BODY -> stringResource(R.string.motion_off)
    }
    val timeFormatter = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val formattedWatchSyncTime = remember(uiState.lastWatchSyncMs, timeFormatter) {
        uiState.lastWatchSyncMs?.let { timeFormatter.format(Date(it)) }
    }
    val syncStatusText = when {
        uiState.isSimulated -> stringResource(R.string.watch_badge_demo)
        formattedWatchSyncTime != null ->
            stringResource(R.string.watch_badge_connected, formattedWatchSyncTime)
        else -> ""
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = if (animationsEnabled) OmamoriMotion.spatialDefault() else snap()
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (syncStatusText.isNotEmpty() || uiState.isSimulated) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_watch_rounded),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = syncStatusText,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (uiState.isSimulated) {
                        TextButton(
                            onClick = onExitDemo,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.watch_badge_demo_exit),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (feeling != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.width(92.dp)
                    ) {
                        OmamoriPetMobileAvatar(
                            visualState = visualState,
                            feeling = feeling,
                            description = stringResource(R.string.app_name),
                            size = 92.dp
                        )
                        Text(
                            text = stringResource(feeling.labelRes),
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                } else {
                    OmamoriPetMobileAvatar(
                        visualState = visualState,
                        description = stringResource(R.string.app_name),
                        size = 92.dp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    // 状態変更時だけ穏やかに切り替え（心拍数値の頻繁な更新で揺れないようにキーを state に限定）
                    AnimatedContent(
                        targetState = stateTitle,
                        transitionSpec = {
                            if (animationsEnabled) {
                                fadeIn(tween(220)) togetherWith fadeOut(tween(150))
                            } else {
                                EnterTransition.None togetherWith ExitTransition.None
                            }
                        },
                        label = "heroStateTitle"
                    ) { resolvedTitle ->
                        Text(
                            text = resolvedTitle,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    if (!isPhoneOnlyIdle && uiState.currentHeartRateBpm != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.semantics(mergeDescendants = true) {}
                        ) {
                            Text(
                                text = uiState.currentHeartRateBpm.toString(),
                                style = MaterialTheme.typography.displaySmall
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = buildString {
                                    append(stringResource(R.string.heart_rate_unit))
                                    if (delta != null) {
                                        val sign = if (delta >= 0) "+" else ""
                                        append(" ($sign$delta)")
                                    }
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }

            if (stateDesc.isNotEmpty()) {
                Text(
                    text = stateDesc,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (!uiState.hasCompletedFirstBreathing && isPhoneOnlyIdle) {
                Text(
                    text = stringResource(R.string.first_breath_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor.copy(alpha = 0.85f)
                )
            }

            // Watch 接続・デモ時はモーションと目安を1行に集約し、未接続時は HC 値があるときのみ添える
            if (!isPhoneOnlyIdle) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(motionIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$motionText · ${stringResource(R.string.baseline_format, uiState.restingBaselineBpm)}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
                    )
                }
            } else if (uiState.healthConnectRestingBpm != null) {
                Text(
                    text = stringResource(R.string.baseline_hc_only, uiState.healthConnectRestingBpm),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onOpenBreathing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(painter = painterResource(R.drawable.ic_spa_rounded), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cta_breathe_phone), maxLines = 1)
                }
                TextButton(
                    onClick = onOpenFeeling,
                    colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.feeling_record_action))
                }
                if (uiState.isWatchConnected && !uiState.isSimulated && (!hasWatchData || uiState.motionState != MotionState.OFF_BODY)) {
                    TextButton(
                        onClick = onTriggerWatchBreathing,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = contentColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_watch_rounded), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.cta_breathe_watch), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchSetupGuideCard(
    onDismiss: () -> Unit
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    val expandedLabel = stringResource(R.string.section_expanded)
    val collapsedLabel = stringResource(R.string.section_collapsed)

    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = if (animationsEnabled) OmamoriMotion.spatialDefault() else snap()
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .semantics {
                        stateDescription = if (expanded) expandedLabel else collapsedLabel
                    }
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_watch_rounded),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.watch_guide_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard_arrow_right_rounded),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(if (expanded) 90f else 0f)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = if (animationsEnabled) fadeIn() + expandVertically() else EnterTransition.None,
                exit = if (animationsEnabled) fadeOut() + shrinkVertically() else ExitTransition.None
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                ) {
                    Text(stringResource(R.string.watch_guide_step1), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.watch_guide_step2), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.watch_guide_step3), style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(stringResource(R.string.watch_guide_dismiss))
                    }
                }
            }
        }
    }
}

@Composable
private fun AiReflectionCard(
    uiState: MobileUiState,
    onRefreshAi: () -> Unit
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    // Hero (ElevatedCard) との優先順位の差を出すため、補助カードはシャドウなしの Filled Card を使う
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = if (animationsEnabled) OmamoriMotion.spatialDefault() else snap()
            )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_auto_awesome_rounded),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ai_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onRefreshAi,
                    enabled = !uiState.isAiRefreshing && !uiState.isAiPreparing
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh_rounded),
                        contentDescription = stringResource(R.string.ai_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedContent(
                targetState = uiState.isAiRefreshing || uiState.isAiPreparing,
                transitionSpec = {
                    if (animationsEnabled) {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "aiReflectionBody",
                modifier = Modifier.padding(end = 8.dp)
            ) { refreshing ->
                if (refreshing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = if (uiState.isAiPreparing) {
                                stringResource(R.string.ai_preparing)
                            } else {
                                uiState.aiAdviceText.ifBlank { "..." }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = uiState.aiAdviceText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (uiState.aiPrepareFailed) {
                Text(
                    text = stringResource(R.string.ai_prepare_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HealthConnectRecoveryCard(
    availability: HealthConnectAvailability,
    permissionAttempted: Boolean,
    onRequestHealthConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateHealthConnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val isUpdateRequired = availability == HealthConnectAvailability.UPDATE_REQUIRED
    val bodyText = when {
        isUpdateRequired -> stringResource(R.string.hc_update_required)
        permissionAttempted -> stringResource(R.string.hc_denied_recovery)
        else -> stringResource(R.string.hc_missing_perm)
    }
    val primaryLabel = when {
        isUpdateRequired -> stringResource(R.string.hc_update_btn)
        permissionAttempted -> stringResource(R.string.hc_open_settings_btn)
        else -> stringResource(R.string.hc_grant_btn)
    }
    val onPrimaryClick = when {
        isUpdateRequired -> onUpdateHealthConnect
        permissionAttempted -> onOpenSettings
        else -> onRequestHealthConnect
    }

    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_health_and_safety_rounded),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = stringResource(R.string.hc_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onPrimaryClick,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(primaryLabel, maxLines = 1)
                }
                if (isUpdateRequired || permissionAttempted) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(stringResource(R.string.hc_later_btn), maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DemoModeSelector(
    current: MobileDemoMode,
    onSelect: (MobileDemoMode) -> Unit
) {
    val options = listOf(
        MobileDemoMode.LIVE_WATCH to stringResource(R.string.demo_live),
        MobileDemoMode.DEMO_CALM to stringResource(R.string.demo_calm),
        MobileDemoMode.DEMO_ELEVATED_AT_REST to stringResource(R.string.demo_elevated),
        MobileDemoMode.DEMO_MOVING to stringResource(R.string.demo_moving)
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (mode, label) ->
            val selected = current == mode
            FilterChip(
                selected = selected,
                onClick = { onSelect(mode) },
                label = { Text(label) },
                leadingIcon = if (selected) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.ic_check_rounded),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PetExpressionPreview() {
    val expressions = listOf(
        R.drawable.pet_calm to R.string.pet_state_calm,
        R.drawable.pet_strain_yellow to R.string.pet_state_high,
        R.drawable.pet_strain_red to R.string.pet_state_very_high,
        R.drawable.pet_strain_purple to R.string.pet_state_extremely_high
    )

    Text(
        text = stringResource(R.string.pet_expression_preview),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() }
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
    ) {
        expressions.forEach { (drawable, label) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(drawable),
                    contentDescription = null,
                    modifier = Modifier.size(60.dp)
                )
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreathingMobileScreen(
    hapticsEnabled: Boolean = true,
    isFirstBreathing: Boolean = false,
    onBack: () -> Unit,
    onCompleted: (String, Long, Int) -> Unit,
    modifier: Modifier = Modifier,
    visualState: PetVisualState = PetVisualState(PetBodyTone.GREEN, PetExpressionMode.CALM),
    elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() }
) {
    val context = LocalContext.current
    val view = LocalView.current
    val animationsEnabled = LocalAnimationsEnabled.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }

    BackHandler {
        vibrator?.cancel()
        onBack()
    }

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            vibrator?.cancel()
        }
    }

    val wasFirstBreathing = rememberSaveable { isFirstBreathing }
    val latestHapticsEnabled by rememberUpdatedState(hapticsEnabled)
    val latestAnimationsEnabled by rememberUpdatedState(animationsEnabled)
    val latestOnCompleted by rememberUpdatedState(onCompleted)
    val latestClock by rememberUpdatedState(elapsedRealtimeMs)
    var sessionId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var startedElapsedMs by rememberSaveable { mutableStateOf(elapsedRealtimeMs()) }
    var startedAtEpochMs by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var running by rememberSaveable { mutableStateOf(true) }
    var completed by rememberSaveable { mutableStateOf(false) }
    var lastCuedPhase by rememberSaveable { mutableIntStateOf(-1) }
    var phaseIndex by remember { mutableIntStateOf(0) }
    var cycle by remember { mutableIntStateOf(1) }
    val totalCycles = BreathingTimeline.TOTAL_CYCLES
    val phaseProgressAnim = remember { Animatable(0f) }
    val phaseDurations = remember { listOf(4_000, 2_000, 6_000) }

    // The monotonic clock owns the session; frames only draw its current position.
    // Save the start and completion so rotation never restarts or re-records it.
    LaunchedEffect(running, sessionId) {
        if (!running) return@LaunchedEffect
        while (true) {
            val now = latestClock()
            val elapsed = if (now >= startedElapsedMs) now - startedElapsedMs
                else (System.currentTimeMillis() - startedAtEpochMs).coerceAtLeast(0L)
            val position = BreathingTimeline.at(elapsed)
            phaseIndex = position.phaseIndex
            cycle = position.cycle
            phaseProgressAnim.snapTo(position.progress)
            if (position.completed) {
                running = false
                completed = true
                latestOnCompleted(sessionId, startedAtEpochMs, BreathingTimeline.DURATION_SECONDS)
                if (latestHapticsEnabled) vibrateMobileCompletion(vibrator)
                break
            }
            val cue = (position.cycle - 1) * 3 + position.phaseIndex
            if (cue != lastCuedPhase) {
                lastCuedPhase = cue
                if (latestHapticsEnabled) vibrateMobileBreathCue(vibrator, position.phaseIndex)
            }
            if (latestAnimationsEnabled) withFrameNanos { } else kotlinx.coroutines.delay(100L)
        }
    }

    val breathFractionProvider = remember {
        {
            if (!running) {
                0.5f
            } else {
                val progress = phaseProgressAnim.value.coerceIn(0f, 1f)
                when (phaseIndex) {
                    0 -> progress
                    1 -> 1f
                    else -> 1f - progress
                }
            }
        }
    }
    val linearProgressProvider = remember {
        { phaseProgressAnim.value.coerceIn(0f, 1f) }
    }
    val cueText = when {
        completed -> stringResource(R.string.breathing_complete)
        phaseIndex == 0 -> stringResource(R.string.breathing_inhale)
        phaseIndex == 1 -> stringResource(R.string.breathing_hold)
        else -> stringResource(R.string.breathing_exhale)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            OmamoriPetMobileAvatar(
                visualState = if (running) visualState.copy(mode = PetExpressionMode.BREATHING) else visualState,
                description = null,
                breathFractionProvider = breathFractionProvider,
                interactive = false,
                size = 160.dp
            )

            AnimatedContent(
                targetState = if (completed) -1 else phaseIndex,
                transitionSpec = {
                    if (animationsEnabled) {
                        (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 8 }) togetherWith
                            fadeOut(tween(150))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "breathingCue"
            ) { targetPhase ->
                val targetCueText = when (targetPhase) {
                    -1 -> stringResource(R.string.breathing_complete)
                    0 -> stringResource(R.string.breathing_inhale)
                    1 -> stringResource(R.string.breathing_hold)
                    else -> stringResource(R.string.breathing_exhale)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (targetPhase == -1 && wasFirstBreathing) {
                        Text(
                            text = stringResource(R.string.ob_praise_complete_2),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                    Text(
                        text = targetCueText,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }

            if (running) {
                val cycleSummary = stringResource(R.string.breathing_cycle_progress, cycle, totalCycles)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BreathingCountdownText(
                        phaseDurationMs = phaseDurations[phaseIndex],
                        progressProvider = linearProgressProvider
                    )
                    LinearProgressIndicator(
                        progress = linearProgressProvider,
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(6.dp)
                    )
                    // サイクル位置の穏やかなインジケータ（セット数テキストの重複をなくしドットに一本化）
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = cycleSummary
                        }
                    ) {
                        for (index in 1..totalCycles) {
                            val dotColor = when {
                                index < cycle -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                index == cycle -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                            Box(
                                modifier = Modifier
                                    .size(width = if (index == cycle) 18.dp else 8.dp, height = 8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            AnimatedContent(
                targetState = completed,
                transitionSpec = {
                    if (animationsEnabled) {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(150))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "breathingControls"
            ) { isDone ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isDone) {
                        OutlinedButton(
                            onClick = {
                                sessionId = UUID.randomUUID().toString()
                                startedElapsedMs = elapsedRealtimeMs()
                                startedAtEpochMs = System.currentTimeMillis()
                                lastCuedPhase = -1
                                completed = false
                                running = true
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(stringResource(R.string.breathing_restart))
                        }
                        Button(
                            onClick = onBack,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(stringResource(R.string.breathing_done_back))
                        }
                    } else {
                        TextButton(
                            onClick = {
                                running = false
                                vibrator?.cancel()
                                onBack()
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_arrow_back_rounded), contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.breathing_stop))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreathingCountdownText(
    phaseDurationMs: Int,
    progressProvider: () -> Float
) {
    val remainingSec by remember(phaseDurationMs) {
        derivedStateOf {
            val p = progressProvider().coerceIn(0f, 1f)
            ceil(((1f - p) * phaseDurationMs) / 1000.0).toInt().coerceAtLeast(1)
        }
    }
    Text(
        text = stringResource(R.string.breathing_seconds_left, remainingSec),
        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun vibrateMobileBreathCue(vibrator: Vibrator?, phaseIndex: Int) {
    if (vibrator == null || !vibrator.hasVibrator()) return
    val effect = when (phaseIndex) {
        0 -> VibrationEffect.createWaveform(longArrayOf(0, 50, 70, 70), intArrayOf(0, 40, 0, 65), -1)
        1 -> VibrationEffect.createOneShot(30L, 30)
        else -> VibrationEffect.createWaveform(longArrayOf(0, 90, 90, 50), intArrayOf(0, 60, 0, 35), -1)
    }
    vibrator.vibrate(effect)
}

private fun vibrateMobileCompletion(vibrator: Vibrator?) {
    if (vibrator == null || !vibrator.hasVibrator()) return
    vibrator.vibrate(VibrationEffect.createOneShot(100L, 60))
}

private sealed interface HistoryTimelineItem {
    val key: String
    val timestampEpochMs: Long
    val isSimulated: Boolean

    data class Breath(val record: BreathingSessionRecord) : HistoryTimelineItem {
        override val key: String = "b_${record.id}"
        override val timestampEpochMs: Long = record.startedAtEpochMs
        override val isSimulated: Boolean = record.isSimulated
    }

    data class Event(val record: CalmEventRecord) : HistoryTimelineItem {
        override val key: String = "e_${record.id}"
        override val timestampEpochMs: Long = record.timestampEpochMs
        override val isSimulated: Boolean = record.isSimulated
    }

    data class FeelingEntry(val record: FeelingRecord) : HistoryTimelineItem {
        override val key: String = "f_${record.id}"
        override val timestampEpochMs: Long = record.recordedAtEpochMs
        override val isSimulated: Boolean = false
    }
}

private data class PreparedHistoryItem(
    val item: HistoryTimelineItem,
    val formattedTime: String
)

private data class PreparedHistory(
    val todayItems: List<PreparedHistoryItem>,
    val earlierItems: List<PreparedHistoryItem>,
    val todayBreaths: Int,
    val demoTodayBreaths: Int,
    val isEmpty: Boolean
)

@Composable
fun HistoryMobileScreen(
    uiState: MobileUiState,
    onTryBreathing: () -> Unit,
    onOpenFeeling: (FeelingRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val timeOnlyFormatter = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val prepared = remember(uiState.breathingSessions, uiState.calmEvents, uiState.feelingRecords, formatter, timeOnlyFormatter) {
        val todayCutoff = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val timeline = (uiState.breathingSessions.map { HistoryTimelineItem.Breath(it) } +
            uiState.calmEvents.map { HistoryTimelineItem.Event(it) } +
            uiState.feelingRecords.map { HistoryTimelineItem.FeelingEntry(it) })
            .sortedByDescending { it.timestampEpochMs }
        val today = ArrayList<PreparedHistoryItem>(timeline.size)
        val earlier = ArrayList<PreparedHistoryItem>(timeline.size)
        for (entry in timeline) {
            if (entry.timestampEpochMs >= todayCutoff) {
                today.add(PreparedHistoryItem(entry, timeOnlyFormatter.format(Date(entry.timestampEpochMs))))
            } else {
                earlier.add(PreparedHistoryItem(entry, formatter.format(Date(entry.timestampEpochMs))))
            }
        }
        val todayBreaths = uiState.breathingSessions.count { !it.isSimulated && it.startedAtEpochMs >= todayCutoff }
        val demoTodayBreaths = uiState.breathingSessions.count { it.isSimulated && it.startedAtEpochMs >= todayCutoff }
        PreparedHistory(
            todayItems = today,
            earlierItems = earlier,
            todayBreaths = todayBreaths,
            demoTodayBreaths = demoTodayBreaths,
            isEmpty = timeline.isEmpty()
        )
    }
    val animationsEnabled = LocalAnimationsEnabled.current
    var showTimeline by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(animationsEnabled) {
        if (animationsEnabled && !showTimeline) {
            kotlinx.coroutines.delay(60L)
        }
        showTimeline = true
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        if (prepared.isEmpty) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_spa_rounded),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.history_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.history_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = onTryBreathing,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.history_try_breathing))
                }
            }
        } else {
            AnimatedVisibility(
                visible = showTimeline,
                enter = if (animationsEnabled) fadeIn(tween(180)) else EnterTransition.None,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (prepared.todayBreaths > 0 || prepared.demoTodayBreaths > 0) {
                        item(key = "summary_header") {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_spa_rounded),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = when {
                                            prepared.todayBreaths == 0 && prepared.demoTodayBreaths > 0 ->
                                                stringResource(R.string.history_demo_summary, prepared.demoTodayBreaths)
                                            prepared.todayBreaths > 0 && prepared.demoTodayBreaths > 0 ->
                                                stringResource(R.string.history_today_with_demo_summary, prepared.todayBreaths, prepared.demoTodayBreaths)
                                            else ->
                                                stringResource(R.string.history_today_summary, prepared.todayBreaths)
                                        },
                                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
                                    )
                                }
                            }
                        }
                    }

                    if (prepared.todayItems.isNotEmpty()) {
                        item(key = "section_today") {
                            SectionHeader(text = stringResource(R.string.history_section_today))
                        }
                        itemsIndexed(prepared.todayItems, key = { _, entry -> entry.item.key }) { index, entry ->
                            HistoryTimelineRow(
                                item = entry.item,
                                timeLabel = entry.formattedTime,
                                showDivider = index < prepared.todayItems.lastIndex,
                                onOpenFeeling = onOpenFeeling,
                                modifier = if (animationsEnabled) Modifier.animateItem() else Modifier
                            )
                        }
                    }

                    if (prepared.earlierItems.isNotEmpty()) {
                        item(key = "section_earlier") {
                            SectionHeader(text = stringResource(R.string.history_section_earlier))
                        }
                        itemsIndexed(prepared.earlierItems, key = { _, entry -> entry.item.key }) { index, entry ->
                            HistoryTimelineRow(
                                item = entry.item,
                                timeLabel = entry.formattedTime,
                                showDivider = index < prepared.earlierItems.lastIndex,
                                onOpenFeeling = onOpenFeeling,
                                modifier = if (animationsEnabled) Modifier.animateItem() else Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTimelineRow(
    item: HistoryTimelineItem,
    timeLabel: String,
    showDivider: Boolean,
    onOpenFeeling: (FeelingRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val headlineStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")
    val supportingStyle = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
    Column(modifier = modifier) {
        when (item) {
            is HistoryTimelineItem.Breath -> {
                val s = item.record
                val headlineText = when {
                    s.preBpm != null && s.postBpm != null -> {
                        val deltaStr = stringResource(R.string.history_breath_delta, s.preBpm!!, s.postBpm!!)
                        stringResource(R.string.history_breath_item, deltaStr)
                    }
                    s.durationSeconds == 60 -> stringResource(R.string.history_breath_item_plain)
                    else -> {
                        val secStr = stringResource(R.string.history_breath_duration, s.durationSeconds)
                        stringResource(R.string.history_breath_item, secStr)
                    }
                }
                ListItem(
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_spa_rounded),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    headlineContent = {
                        Text(headlineText, style = headlineStyle)
                    },
                    supportingContent = { Text(timeLabel, style = supportingStyle) },
                    trailingContent = if (item.isSimulated) {
                        { DemoTagBadge() }
                    } else {
                        null
                    }
                )
            }
            is HistoryTimelineItem.Event -> {
                val e = item.record
                val isSnoozed = e.actionTaken == CalmEventAction.SNOOZED
                ListItem(
                    leadingContent = {
                        Icon(
                            painter = painterResource(
                                if (isSnoozed) R.drawable.ic_notifications_paused_rounded else R.drawable.ic_favorite_border_rounded
                            ),
                            contentDescription = null,
                            tint = if (isSnoozed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary
                        )
                    },
                    headlineContent = {
                        Text(
                            text = if (isSnoozed) {
                                stringResource(R.string.history_event_snoozed)
                            } else {
                                stringResource(R.string.history_event_item, e.peakBpm, e.restingBaselineBpm)
                            },
                            style = headlineStyle
                        )
                    },
                    supportingContent = { Text(timeLabel, style = supportingStyle) },
                    trailingContent = if (item.isSimulated) {
                        { DemoTagBadge() }
                    } else {
                        null
                    }
                )
            }
            is HistoryTimelineItem.FeelingEntry -> {
                val feeling = item.record.feeling
                ListItem(
                    leadingContent = {
                        Image(painterResource(feeling.faceRes), contentDescription = null, modifier = Modifier.size(36.dp))
                    },
                    headlineContent = { Text(stringResource(feeling.labelRes), style = headlineStyle) },
                    supportingContent = { Text(timeLabel, style = supportingStyle) },
                    trailingContent = {
                        Icon(painterResource(R.drawable.ic_keyboard_arrow_right_rounded), contentDescription = null)
                    },
                    modifier = Modifier.clickable(role = Role.Button) { onOpenFeeling(item.record) }
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun DemoTagBadge() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = stringResource(R.string.history_demo_tag),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SettingsMobileScreen(
    uiState: MobileUiState,
    onUpdatePreferences: (AlertPreferences) -> Unit,
    onRequestHealthConnect: () -> Unit,
    onRequestFeelingHealth: () -> Unit,
    onRequestFeelingPeriod: () -> Unit,
    onOpenHealthConnectSettings: () -> Unit,
    onUpdateHealthConnect: () -> Unit,
    onPrepareGeminiNano: () -> Unit,
    onOpenPrivacyRationale: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onResetOnboardingAnswers: () -> Unit,
    onSelectDemoMode: (MobileDemoMode) -> Unit,
    onClearDemoHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = uiState.preferences
    val hasDemoRecords = uiState.breathingSessions.any { it.isSimulated } || uiState.calmEvents.any { it.isSimulated }
    val haptic = LocalHapticFeedback.current
    val animationsEnabled = LocalAnimationsEnabled.current
    var showSecondary by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(animationsEnabled) {
        if (animationsEnabled && !showSecondary) {
            kotlinx.coroutines.delay(60L)
        }
        showSecondary = true
    }

    var showResetConfirmation by remember { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var dataExpanded by rememberSaveable { mutableStateOf(false) }
    var guideDemoExpanded by rememberSaveable(uiState.isSimulated, hasDemoRecords) {
        mutableStateOf(uiState.isSimulated || hasDemoRecords)
    }

    var sliderBpm by remember(prefs.elevationThresholdBpm) {
        mutableFloatStateOf(prefs.elevationThresholdBpm.toFloat())
    }
    val roundedSliderBpm = sliderBpm.roundToInt()

    val demoModeLabel = when (uiState.demoMode) {
        MobileDemoMode.LIVE_WATCH -> stringResource(R.string.demo_live)
        MobileDemoMode.DEMO_CALM -> stringResource(R.string.demo_calm)
        MobileDemoMode.DEMO_ELEVATED_AT_REST -> stringResource(R.string.demo_elevated)
        MobileDemoMode.DEMO_MOVING -> stringResource(R.string.demo_moving)
    }

    val hasAnyIntegration = uiState.hasHealthConnectPermission ||
        uiState.feelingHealthPermissionsGranted > 0 ||
        uiState.hasPeriodPermission
    val expandedLabel = stringResource(R.string.section_expanded)
    val collapsedLabel = stringResource(R.string.section_collapsed)
    val dataArrowRotation by animateFloatAsState(
        targetValue = if (dataExpanded) 90f else 0f,
        animationSpec = if (animationsEnabled) tween(180) else snap(),
        label = "dataArrowRotation"
    )
    val guideDemoArrowRotation by animateFloatAsState(
        targetValue = if (guideDemoExpanded) 90f else 0f,
        animationSpec = if (animationsEnabled) tween(180) else snap(),
        label = "guideDemoArrowRotation"
    )

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_onboarding_sub)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetOnboardingAnswers()
                        showResetConfirmation = true
                    }
                ) {
                    Text(stringResource(R.string.settings_reset_confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            SectionHeader(text = stringResource(R.string.settings_alerts_section))

            ListItem(
                leadingContent = { Icon(painterResource(R.drawable.ic_notifications_active_rounded), contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_alerts_toggle)) },
                trailingContent = {
                    Switch(
                        checked = prefs.alertsEnabled,
                        onCheckedChange = null
                    )
                },
                modifier = Modifier.toggleable(
                    value = prefs.alertsEnabled,
                    role = Role.Switch,
                    onValueChange = { onUpdatePreferences(prefs.copy(alertsEnabled = it)) }
                )
            )

            // 通知 OFF 時は関連する感度・おやすみ時間を折りたたみ、無効状態のノイズをなくす
            AnimatedVisibility(
                visible = prefs.alertsEnabled,
                enter = if (animationsEnabled) fadeIn() + expandVertically() else EnterTransition.None,
                exit = if (animationsEnabled) fadeOut() + shrinkVertically() else ExitTransition.None
            ) {
                Column {
                    val thresholdStateDesc = stringResource(R.string.settings_threshold_label, roundedSliderBpm)
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = thresholdStateDesc,
                            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")
                        )
                        Slider(
                            value = sliderBpm,
                            onValueChange = { next ->
                                val nextInt = next.roundToInt()
                                if (nextInt != roundedSliderBpm) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                sliderBpm = next
                            },
                            onValueChangeFinished = {
                                onUpdatePreferences(prefs.copy(elevationThresholdBpm = sliderBpm.roundToInt()))
                            },
                            valueRange = 10f..25f,
                            steps = 2,
                            modifier = Modifier.semantics {
                                stateDescription = thresholdStateDesc
                            }
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.settings_threshold_sensitive),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.settings_threshold_relaxed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    ListItem(
                        leadingContent = { Icon(painterResource(R.drawable.ic_hotel_rounded), contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.settings_quiet_hours)) },
                        trailingContent = {
                            Switch(
                                checked = prefs.quietHoursEnabled,
                                onCheckedChange = null
                            )
                        },
                        modifier = Modifier.toggleable(
                            value = prefs.quietHoursEnabled,
                            role = Role.Switch,
                            onValueChange = { onUpdatePreferences(prefs.copy(quietHoursEnabled = it)) }
                        )
                    )
                }
            }

            ListItem(
                leadingContent = { Icon(painterResource(R.drawable.ic_spa_rounded), contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_haptics_toggle)) },
                trailingContent = {
                    Switch(
                        checked = prefs.breathingHapticsEnabled,
                        onCheckedChange = null
                    )
                },
                modifier = Modifier.toggleable(
                    value = prefs.breathingHapticsEnabled,
                    role = Role.Switch,
                    onValueChange = { onUpdatePreferences(prefs.copy(breathingHapticsEnabled = it)) }
                )
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AnimatedVisibility(
                visible = showSecondary,
                enter = if (animationsEnabled) fadeIn(tween(180)) else EnterTransition.None
            ) {
                Column {
                    // Bランク折りたたみ①：健康データ・AI連携
                    ListItem(
                        leadingContent = { Icon(painterResource(R.drawable.ic_favorite_rounded), contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.settings_integrations_section)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (hasAnyIntegration) {
                                        R.string.settings_integrations_connected
                                    } else {
                                        R.string.settings_integrations_not_set
                                    }
                                )
                            )
                        },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_keyboard_arrow_right_rounded),
                                contentDescription = null,
                                modifier = Modifier.rotate(dataArrowRotation)
                            )
                        },
                        modifier = Modifier
                            .semantics {
                                stateDescription = if (dataExpanded) expandedLabel else collapsedLabel
                            }
                            .clickable(role = Role.Button) { dataExpanded = !dataExpanded }
                    )

                    AnimatedVisibility(
                        visible = dataExpanded,
                        enter = if (animationsEnabled) fadeIn() + expandVertically() else EnterTransition.None,
                        exit = if (animationsEnabled) fadeOut() + shrinkVertically() else ExitTransition.None
                    ) {
                        Column {
                            ListItem(
                                leadingContent = { Icon(painterResource(R.drawable.ic_favorite_rounded), contentDescription = null) },
                                headlineContent = { Text(stringResource(R.string.hc_title)) },
                                supportingContent = {
                                    Text(
                                        when {
                                            uiState.healthConnectAvailability == HealthConnectAvailability.UPDATE_REQUIRED ->
                                                stringResource(R.string.hc_update_required)
                                            uiState.healthConnectAvailability == HealthConnectAvailability.UNAVAILABLE ->
                                                stringResource(R.string.hc_unavailable)
                                            uiState.hasHealthConnectPermission && uiState.healthConnectRestingBpm != null ->
                                                stringResource(R.string.hc_ready, uiState.healthConnectRestingBpm)
                                            uiState.hasHealthConnectPermission ->
                                                stringResource(R.string.hc_connected_badge)
                                            uiState.hcPermissionAttempted ->
                                                stringResource(R.string.hc_denied_recovery)
                                            else ->
                                                stringResource(R.string.hc_missing_perm)
                                        }
                                    )
                                },
                                trailingContent = when {
                                    uiState.healthConnectAvailability == HealthConnectAvailability.AVAILABLE &&
                                        uiState.hasHealthConnectPermission -> {
                                        {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_check_circle_outline_rounded),
                                                contentDescription = stringResource(R.string.hc_connected_badge),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    uiState.healthConnectAvailability != HealthConnectAvailability.UNAVAILABLE &&
                                        !uiState.hasHealthConnectPermission -> {
                                        {
                                            TextButton(
                                                onClick = {
                                                    when {
                                                        uiState.healthConnectAvailability == HealthConnectAvailability.UPDATE_REQUIRED ->
                                                            onUpdateHealthConnect()
                                                        uiState.hcPermissionAttempted ->
                                                            onOpenHealthConnectSettings()
                                                        else ->
                                                            onRequestHealthConnect()
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    when {
                                                        uiState.healthConnectAvailability == HealthConnectAvailability.UPDATE_REQUIRED ->
                                                            stringResource(R.string.hc_update_btn)
                                                        uiState.hcPermissionAttempted ->
                                                            stringResource(R.string.hc_open_settings_btn)
                                                        else ->
                                                            stringResource(R.string.hc_grant_btn)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    else -> null
                                }
                            )

                            if (uiState.healthConnectAvailability == HealthConnectAvailability.AVAILABLE) {
                                val hasFeelingHealthPermissions = uiState.feelingHealthPermissionsGranted == 3
                                ListItem(
                                    leadingContent = { Icon(painterResource(R.drawable.ic_history_rounded), contentDescription = null) },
                                    headlineContent = { Text(stringResource(R.string.feeling_health_settings_title)) },
                                    supportingContent = {
                                        Text(
                                            "${stringResource(R.string.feeling_health_settings_summary)} · ${uiState.feelingHealthPermissionsGranted}/3"
                                        )
                                    },
                                    trailingContent = {
                                        TextButton(
                                            onClick = if (hasFeelingHealthPermissions) {
                                                onOpenHealthConnectSettings
                                            } else {
                                                onRequestFeelingHealth
                                            }
                                        ) {
                                            Text(
                                                stringResource(
                                                    if (hasFeelingHealthPermissions) {
                                                        R.string.hc_open_settings_btn
                                                    } else {
                                                        R.string.feeling_health_grant
                                                    }
                                                )
                                            )
                                        }
                                    }
                                )
                                ListItem(
                                    leadingContent = { Icon(painterResource(R.drawable.ic_favorite_border_rounded), contentDescription = null) },
                                    headlineContent = { Text(stringResource(R.string.feeling_period_settings_title)) },
                                    supportingContent = { Text(stringResource(R.string.feeling_period_settings_summary)) },
                                    trailingContent = {
                                        TextButton(
                                            onClick = if (uiState.hasPeriodPermission) {
                                                onOpenHealthConnectSettings
                                            } else {
                                                onRequestFeelingPeriod
                                            }
                                        ) {
                                            Text(
                                                stringResource(
                                                    if (uiState.hasPeriodPermission) {
                                                        R.string.hc_open_settings_btn
                                                    } else {
                                                        R.string.feeling_health_grant
                                                    }
                                                )
                                            )
                                        }
                                    }
                                )
                            }

                            if (uiState.aiBackendStatus != AiBackendStatus.FALLBACK_ONLY || uiState.isAiPreparing) {
                                ListItem(
                                    leadingContent = { Icon(painterResource(R.drawable.ic_auto_awesome_rounded), contentDescription = null) },
                                    headlineContent = { Text(stringResource(R.string.ai_nano_title)) },
                                    supportingContent = {
                                        Text(
                                            when {
                                                uiState.isAiPreparing -> stringResource(R.string.ai_preparing)
                                                uiState.aiPrepareFailed -> stringResource(R.string.ai_prepare_failed)
                                                uiState.aiBackendStatus == AiBackendStatus.GEMINI_NANO_AVAILABLE ->
                                                    stringResource(R.string.ai_nano_ready)
                                                else -> stringResource(R.string.ai_nano_downloadable)
                                            }
                                        )
                                    },
                                    trailingContent = {
                                        when {
                                            uiState.isAiPreparing ->
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            uiState.aiBackendStatus == AiBackendStatus.GEMINI_NANO_AVAILABLE ->
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_check_circle_outline_rounded),
                                                    contentDescription = stringResource(R.string.ai_nano_ready),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            else ->
                                                TextButton(onClick = onPrepareGeminiNano) {
                                                    Text(stringResource(R.string.ai_download_btn))
                                                }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Bランク折りたたみ②：使い方・体験デモ
                    ListItem(
                        leadingContent = { Icon(painterResource(R.drawable.ic_auto_awesome_rounded), contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.settings_guide_demo_section)) },
                        supportingContent = if (uiState.isSimulated) {
                            { Text(demoModeLabel) }
                        } else {
                            null
                        },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_keyboard_arrow_right_rounded),
                                contentDescription = null,
                                modifier = Modifier.rotate(guideDemoArrowRotation)
                            )
                        },
                        modifier = Modifier
                            .semantics {
                                stateDescription = if (guideDemoExpanded) expandedLabel else collapsedLabel
                            }
                            .clickable(role = Role.Button) { guideDemoExpanded = !guideDemoExpanded }
                    )

                    AnimatedVisibility(
                        visible = guideDemoExpanded,
                        enter = if (animationsEnabled) fadeIn() + expandVertically() else EnterTransition.None,
                        exit = if (animationsEnabled) fadeOut() + shrinkVertically() else ExitTransition.None
                    ) {
                        Column {
                            ListItem(
                                leadingContent = { Icon(painterResource(R.drawable.ic_auto_awesome_rounded), contentDescription = null) },
                                headlineContent = { Text(stringResource(R.string.settings_replay_onboarding)) },
                                trailingContent = {
                                    Icon(painterResource(R.drawable.ic_keyboard_arrow_right_rounded), contentDescription = null)
                                },
                                modifier = Modifier.clickable(onClick = onReplayOnboarding)
                            )

                            ListItem(
                                leadingContent = { Icon(painterResource(R.drawable.ic_delete_outline_rounded), contentDescription = null) },
                                headlineContent = { Text(stringResource(R.string.settings_reset_onboarding)) },
                                supportingContent = if (showResetConfirmation) {
                                    { Text(stringResource(R.string.settings_reset_onboarding_done)) }
                                } else {
                                    null
                                },
                                modifier = Modifier.clickable { showResetDialog = true }
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                DemoModeSelector(
                                    current = uiState.demoMode,
                                    onSelect = onSelectDemoMode
                                )
                                PetExpressionPreview()
                                if (hasDemoRecords) {
                                    OutlinedButton(
                                        onClick = onClearDemoHistory,
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(painterResource(R.drawable.ic_delete_outline_rounded), contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.settings_clear_demo))
                                    }
                                }
                            }
                        }
                    }

                    ListItem(
                        leadingContent = { Icon(painterResource(R.drawable.ic_privacy_tip_rounded), contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.settings_privacy_item)) },
                        trailingContent = {
                            Icon(painterResource(R.drawable.ic_keyboard_arrow_right_rounded), contentDescription = null)
                        },
                        modifier = Modifier.clickable(onClick = onOpenPrivacyRationale)
                    )

                    Text(
                        text = stringResource(R.string.settings_disclaimer_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun WellnessNoticeCard(modifier: Modifier = Modifier) {
    OutlinedCard(
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_disclaimer_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.settings_disclaimer_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}
