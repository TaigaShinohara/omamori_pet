package com.omamoripet.mobile.ui.screens

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omamoripet.mobile.R
import com.omamoripet.mobile.ui.components.OmamoriPetMobileAvatar
import com.omamoripet.mobile.ui.theme.LocalAnimationsEnabled
import com.omamoripet.shared.model.ONBOARDING_TOTAL_PAGES
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.model.OnboardingAnalyticsEvent
import com.omamoripet.shared.model.OnboardingAnalyticsTracker
import com.omamoripet.shared.model.OnboardingAnswers
import com.omamoripet.shared.model.OnboardingConcern
import com.omamoripet.shared.model.OnboardingDeviceStyle
import com.omamoripet.shared.model.OnboardingHapticChoice
import com.omamoripet.shared.model.OnboardingPageRole
import com.omamoripet.shared.model.PraiseSelector
import com.omamoripet.shared.model.PraiseTier
import com.omamoripet.shared.model.computeWindowDotsState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun OnboardingScreen(
    analyticsTracker: OnboardingAnalyticsTracker,
    initialAnswers: OnboardingAnswers = OnboardingAnswers(),
    onFinishToBreathing: (OnboardingAnswers) -> Unit,
    onFinishToHome: (OnboardingAnswers) -> Unit,
    onSkipOrTryLater: (OnboardingAnswers) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { ONBOARDING_TOTAL_PAGES })
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val animationsEnabled = LocalAnimationsEnabled.current

    var answers by remember(initialAnswers) { mutableStateOf(initialAnswers) }
    var concernAnswered by remember { mutableStateOf(false) }
    var deviceAnswered by remember { mutableStateOf(false) }
    var tryItCompleted by remember { mutableStateOf(false) }
    var isAutoTransitioning by remember { mutableStateOf(false) }
    val praiseSelector = remember { PraiseSelector() }

    val choicePraisePool = listOf(
        stringResource(R.string.ob_praise_choice_1),
        stringResource(R.string.ob_praise_choice_2),
        stringResource(R.string.ob_praise_choice_3)
    )
    val tryPraisePool = listOf(
        stringResource(R.string.ob_praise_try_1),
        stringResource(R.string.ob_praise_try_2),
        stringResource(R.string.ob_praise_try_3)
    )
    val completePraisePool = listOf(
        stringResource(R.string.ob_praise_complete_1),
        stringResource(R.string.ob_praise_complete_2),
        stringResource(R.string.ob_praise_complete_3)
    )
    val completePraise = remember(completePraisePool) {
        completePraisePool[praiseSelector.nextIndex(PraiseTier.LARGE, completePraisePool.size)]
    }

    val roles = remember { OnboardingPageRole.entries }

    LaunchedEffect(pagerState.currentPage) {
        val idx = pagerState.currentPage
        analyticsTracker.track(
            OnboardingAnalyticsEvent.PageViewed(pageIndex = idx, role = roles[idx])
        )
    }

    val currentPage = pagerState.currentPage
    val isCurrentChoiceBlocked = when (currentPage) {
        1 -> !concernAnswered
        2 -> !deviceAnswered
        8 -> !tryItCompleted
        else -> false
    }

    suspend fun goToPage(target: Int) {
        val clamped = target.coerceIn(0, ONBOARDING_TOTAL_PAGES - 1)
        if (animationsEnabled) {
            pagerState.animateScrollToPage(clamped)
        } else {
            pagerState.scrollToPage(clamped)
        }
    }

    BackHandler(enabled = currentPage > 0) {
        if (!isAutoTransitioning) {
            scope.launch { goToPage(currentPage - 1) }
        }
    }

    fun performSmallAdvance(targetPage: Int) {
        if (isAutoTransitioning) return
        performStandardHaptic(view, PraiseTier.SMALL)
        scope.launch { goToPage(targetPage) }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            // 固定ヘッダー：戻る（左） / 窓ドット（中央） / スキップ（右）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Box(modifier = Modifier.widthIn(min = 88.dp), contentAlignment = Alignment.CenterStart) {
                    if (currentPage > 0) {
                        TextButton(
                            onClick = {
                                scope.launch { goToPage(currentPage - 1) }
                            },
                            enabled = !isAutoTransitioning,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(stringResource(R.string.back), maxLines = 1)
                        }
                    }
                }

                OnboardingWindowDots(
                    currentPage = currentPage,
                    totalPages = ONBOARDING_TOTAL_PAGES
                )

                Box(modifier = Modifier.widthIn(min = 88.dp), contentAlignment = Alignment.CenterEnd) {
                    if (currentPage < ONBOARDING_TOTAL_PAGES - 1) {
                        TextButton(
                            onClick = {
                                isAutoTransitioning = false
                                analyticsTracker.track(
                                    OnboardingAnalyticsEvent.Skipped(
                                        pageIndex = currentPage,
                                        role = roles[currentPage]
                                    )
                                )
                                onSkipOrTryLater(answers)
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(stringResource(R.string.ob_skip), maxLines = 1)
                        }
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isCurrentChoiceBlocked && !isAutoTransitioning,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> HookPage(
                        animationsEnabled = animationsEnabled,
                        onNext = { performSmallAdvance(1) }
                    )
                    1 -> ChoiceQuestionPage(
                        title = stringResource(R.string.ob_p2_title),
                        subtitle = stringResource(R.string.ob_p2_body),
                        options = listOf(
                            stringResource(R.string.ob_p2_opt1),
                            stringResource(R.string.ob_p2_opt2),
                            stringResource(R.string.ob_p2_opt3)
                        ),
                        selectedIndex = if (concernAnswered) answers.concern.ordinal else null,
                        praisePool = choicePraisePool,
                        praiseSelector = praiseSelector,
                        animationsEnabled = animationsEnabled,
                        onLockChange = { isAutoTransitioning = it },
                        onAnswered = { idx ->
                            answers = answers.copy(concern = OnboardingConcern.entries[idx])
                            concernAnswered = true
                            analyticsTracker.track(
                                OnboardingAnalyticsEvent.QuestionAnswered(1, OnboardingPageRole.CHOICE_CONCERN)
                            )
                        },
                        onAdvance = {
                            scope.launch { goToPage(2) }
                        }
                    )
                    2 -> ChoiceQuestionPage(
                        title = stringResource(R.string.ob_p3_title),
                        subtitle = stringResource(R.string.ob_p3_body),
                        options = listOf(
                            stringResource(R.string.ob_p3_opt1),
                            stringResource(R.string.ob_p3_opt2),
                            stringResource(R.string.ob_p3_opt3)
                        ),
                        selectedIndex = if (deviceAnswered) answers.deviceStyle.ordinal else null,
                        praisePool = choicePraisePool,
                        praiseSelector = praiseSelector,
                        animationsEnabled = animationsEnabled,
                        onLockChange = { isAutoTransitioning = it },
                        onAnswered = { idx ->
                            answers = answers.copy(deviceStyle = OnboardingDeviceStyle.entries[idx])
                            deviceAnswered = true
                            analyticsTracker.track(
                                OnboardingAnalyticsEvent.QuestionAnswered(2, OnboardingPageRole.CHOICE_DEVICE)
                            )
                        },
                        onAdvance = {
                            scope.launch { goToPage(3) }
                        }
                    )
                    3 -> MatchDeclarationPage(
                        answers = answers,
                        animationsEnabled = animationsEnabled,
                        onNext = { performSmallAdvance(4) }
                    )
                    4 -> StandardInfoPage(
                        paneLabel = stringResource(R.string.ob_p5_title),
                        title = stringResource(R.string.ob_p5_title),
                        body = stringResource(R.string.ob_p5_body),
                        animationsEnabled = animationsEnabled,
                        visualContent = {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(164.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                )
                                OmamoriPetMobileAvatar(
                                    state = OmamoriState.CALM,
                                    description = null,
                                    interactive = false,
                                    size = 120.dp
                                )
                            }
                        },
                        onNext = { performSmallAdvance(5) }
                    )
                    5 -> StepPage(
                        stepNumber = 1,
                        title = stringResource(R.string.ob_p9_title),
                        body = stringResource(R.string.ob_p9_body),
                        iconRes = R.drawable.ic_spa_rounded,
                        animationsEnabled = animationsEnabled,
                        onNext = { performSmallAdvance(6) }
                    )
                    6 -> StepPage(
                        stepNumber = 2,
                        title = stringResource(R.string.ob_p10_title),
                        body = stringResource(R.string.ob_p10_body),
                        iconRes = R.drawable.ic_self_improvement_rounded,
                        animationsEnabled = animationsEnabled,
                        onNext = { performSmallAdvance(7) }
                    )
                    7 -> StepPage(
                        stepNumber = 3,
                        title = stringResource(R.string.ob_p11_title),
                        body = stringResource(R.string.ob_p11_body),
                        iconRes = R.drawable.ic_history_rounded,
                        animationsEnabled = animationsEnabled,
                        onNext = { performSmallAdvance(8) }
                    )
                    8 -> TryItBreathingPage(
                        hapticsEnabled = answers.hapticChoice != OnboardingHapticChoice.VISUAL_ONLY,
                        onToggleHaptics = { enabled ->
                            answers = answers.copy(
                                hapticChoice = if (enabled) {
                                    OnboardingHapticChoice.WITH_HAPTICS
                                } else {
                                    OnboardingHapticChoice.VISUAL_ONLY
                                }
                            )
                        },
                        praisePool = tryPraisePool,
                        praiseSelector = praiseSelector,
                        animationsEnabled = animationsEnabled,
                        onLockChange = { isAutoTransitioning = it },
                        onCompletedTry = {
                            tryItCompleted = true
                            scope.launch { goToPage(9) }
                        },
                        onTryLater = {
                            analyticsTracker.track(
                                OnboardingAnalyticsEvent.TryLaterSelected(8, OnboardingPageRole.TRY_IT)
                            )
                            onSkipOrTryLater(answers)
                        }
                    )
                    9 -> ConnectPage(
                        completePraise = completePraise,
                        tryItCompleted = tryItCompleted,
                        animationsEnabled = animationsEnabled,
                        onStartBreathing = {
                            performStandardHaptic(view, PraiseTier.LARGE)
                            analyticsTracker.track(
                                OnboardingAnalyticsEvent.Completed(
                                    pageIndex = 9,
                                    role = OnboardingPageRole.CONNECT,
                                    startedBreathingDirectly = true
                                )
                            )
                            onFinishToBreathing(answers)
                        },
                        onGoHome = {
                            performStandardHaptic(view, PraiseTier.SMALL)
                            analyticsTracker.track(
                                OnboardingAnalyticsEvent.Completed(
                                    pageIndex = 9,
                                    role = OnboardingPageRole.CONNECT,
                                    startedBreathingDirectly = false
                                )
                            )
                            onFinishToHome(answers)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingWindowDots(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    val state = computeWindowDotsState(currentPage = currentPage, totalPages = totalPages, maxVisibleDots = 5)
    val animationsEnabled = LocalAnimationsEnabled.current
    val cs = MaterialTheme.colorScheme

    // 視覚専用の窓ドット（総数を読み上げず、見出し側の章名で現在地を伝える）
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics {}
    ) {
        for (i in 0 until state.windowCount) {
            val isActive = i == state.activeOffsetInWindow
            val isEdgeReduced = (i == 0 && state.hasMoreBefore && !isActive) ||
                (i == state.windowCount - 1 && state.hasMoreAfter && !isActive)
            val targetWidth = when {
                state.isCompletedPage -> 10.dp
                isActive -> 22.dp
                isEdgeReduced -> 5.dp
                else -> 8.dp
            }
            val targetHeight = when {
                state.isCompletedPage -> 8.dp
                isActive -> 8.dp
                isEdgeReduced -> 5.dp
                else -> 8.dp
            }
            val targetColor = when {
                state.isCompletedPage || isActive -> cs.primary
                else -> cs.outlineVariant
            }
            val animSpec = if (animationsEnabled) tween<androidx.compose.ui.unit.Dp>(200) else snap()
            val width by animateDpAsState(targetWidth, animationSpec = animSpec, label = "dotW$i")
            val height by animateDpAsState(targetHeight, animationSpec = animSpec, label = "dotH$i")
            val color by animateColorAsState(
                targetColor,
                animationSpec = if (animationsEnabled) tween(200) else snap(),
                label = "dotC$i"
            )

            Box(
                modifier = Modifier
                    .size(width = width, height = height)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun OnboardingChoiceCard(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    animationsEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected && animationsEnabled) 1.02f else 1f,
        animationSpec = if (animationsEnabled) {
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
        } else {
            snap()
        },
        label = "choiceBounce"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = if (animationsEnabled) tween(220) else snap(),
        label = "choiceBg"
    )
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }

    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .defaultMinSize(minHeight = 64.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle_outline_rounded),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun HookPage(
    animationsEnabled: Boolean,
    onNext: () -> Unit
) {
    var pulseDone by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pulseDone || !animationsEnabled) 1f else 1.04f,
        animationSpec = if (animationsEnabled) tween(600, easing = FastOutSlowInEasing) else snap(),
        label = "hookPulse"
    )
    LaunchedEffect(Unit) {
        delay(220L)
        pulseDone = true
    }

    StandardInfoPage(
        paneLabel = stringResource(R.string.ob_p1_title),
        title = stringResource(R.string.ob_p1_title),
        body = stringResource(R.string.ob_p1_body),
        animationsEnabled = animationsEnabled,
        visualContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.scale(scale)
            ) {
                OmamoriPetMobileAvatar(
                    state = OmamoriState.ELEVATED_AT_REST,
                    description = null,
                    interactive = false,
                    size = 136.dp
                )
            }
        },
        onNext = onNext
    )
}

@Composable
private fun StandardInfoPage(
    paneLabel: String,
    title: String,
    body: String,
    animationsEnabled: Boolean,
    visualContent: @Composable () -> Unit,
    onNext: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .semantics {
                isTraversalGroup = true
                paneTitle = paneLabel
            }
    ) {
        Spacer(Modifier.height(8.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 200.dp)
                .semantics { traversalIndex = 2f }
        ) {
            visualContent()
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    heading()
                    traversalIndex = 0f
                }
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { traversalIndex = 1f }
            )
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp)
                .defaultMinSize(minHeight = 52.dp)
                .semantics { traversalIndex = 3f }
        ) {
            Text(stringResource(R.string.ob_next))
        }
    }
}

@Composable
private fun ChoiceQuestionPage(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int?,
    praisePool: List<String>,
    praiseSelector: PraiseSelector,
    animationsEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
    onAnswered: (Int) -> Unit,
    onAdvance: () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var localSelected by remember(selectedIndex) { mutableStateOf(selectedIndex) }
    var praiseText by remember { mutableStateOf<String?>(null) }
    var interruptSignal by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    var isTransitioning by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            interruptSignal?.cancel()
            onLockChange(false)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = isTransitioning,
                onClick = { interruptSignal?.complete(Unit) }
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .semantics {
                isTraversalGroup = true
                paneTitle = title
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    heading()
                    traversalIndex = 0f
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { traversalIndex = 1f }
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .semantics { traversalIndex = 2f }
        ) {
            options.forEachIndexed { index, optionText ->
                OnboardingChoiceCard(
                    text = optionText,
                    selected = localSelected == index,
                    enabled = !isTransitioning,
                    animationsEnabled = animationsEnabled,
                    onClick = {
                        if (isTransitioning) return@OnboardingChoiceCard
                        isTransitioning = true
                        onLockChange(true)
                        localSelected = index
                        onAnswered(index)
                        performStandardHaptic(view, PraiseTier.MEDIUM)
                        praiseText = praisePool[praiseSelector.nextIndex(PraiseTier.MEDIUM, praisePool.size)]
                        val signal = CompletableDeferred<Unit>()
                        interruptSignal = signal
                        scope.launch {
                            // 軽快な反応＋余韻（合計約650ms、タップで即割り込み）
                            withTimeoutOrNull(650L) { signal.await() }
                            isTransitioning = false
                            onLockChange(false)
                            onAdvance()
                        }
                    }
                )
            }
        }

        // 「次へ」ボタンは置かず、称賛または案内のみを同位置に表示
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .semantics { traversalIndex = 3f }
        ) {
            AnimatedContent(
                targetState = praiseText,
                transitionSpec = {
                    if (animationsEnabled) {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "choicePraise"
            ) { praise ->
                if (praise != null) {
                    Text(
                        text = praise,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = stringResource(R.string.ob_select_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchDeclarationPage(
    answers: OnboardingAnswers,
    animationsEnabled: Boolean,
    onNext: () -> Unit
) {
    val (reflection, title, body) = resolveMatchStrings(answers)
    var stage by remember { mutableIntStateOf(0) }

    LaunchedEffect(answers) {
        stage = 1
        delay(150L)
        stage = 2
        delay(150L)
        stage = 3
    }

    val stage1Alpha by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0f,
        animationSpec = if (animationsEnabled) tween(220) else snap(),
        label = "matchStage1"
    )
    val stage2Alpha by animateFloatAsState(
        targetValue = if (stage >= 2) 1f else 0f,
        animationSpec = if (animationsEnabled) tween(220) else snap(),
        label = "matchStage2"
    )
    val stage3Alpha by animateFloatAsState(
        targetValue = if (stage >= 3) 1f else 0f,
        animationSpec = if (animationsEnabled) tween(220) else snap(),
        label = "matchStage3"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .semantics {
                isTraversalGroup = true
                paneTitle = title
            }
    ) {
        Text(
            text = reflection,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .alpha(stage1Alpha)
                .semantics { traversalIndex = 0f }
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .defaultMinSize(minHeight = 190.dp)
                .alpha(stage2Alpha)
                .semantics { traversalIndex = 2f }
        ) {
            // ハートは心拍専用のため使わず、お守り輪の重なりで一致を表現
            Box(
                modifier = Modifier
                    .size(168.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            )
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
            )
            OmamoriPetMobileAvatar(
                state = OmamoriState.CALM,
                description = null,
                interactive = false,
                size = 112.dp
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alpha(stage3Alpha)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    heading()
                    traversalIndex = 0.5f
                }
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { traversalIndex = 1f }
            )
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp)
                .defaultMinSize(minHeight = 52.dp)
                .semantics { traversalIndex = 3f }
        ) {
            Text(stringResource(R.string.ob_next))
        }
    }
}

@Composable
private fun resolveMatchStrings(answers: OnboardingAnswers): Triple<String, String, String> {
    val isBothUndecided = answers.concern == OnboardingConcern.UNDECIDED &&
        answers.deviceStyle == OnboardingDeviceStyle.UNDECIDED
    val title = if (isBothUndecided) {
        stringResource(R.string.ob_p4_undecided_title)
    } else {
        stringResource(R.string.ob_p4_fit_title)
    }
    return when (answers.concern) {
        OnboardingConcern.CALM_DOWN -> when (answers.deviceStyle) {
            OnboardingDeviceStyle.HAS_WATCH -> Triple(
                stringResource(R.string.ob_p4_reflect_calm_watch),
                title,
                stringResource(R.string.ob_p4_body_calm_watch)
            )
            OnboardingDeviceStyle.PHONE_ONLY -> Triple(
                stringResource(R.string.ob_p4_reflect_calm_phone),
                title,
                stringResource(R.string.ob_p4_body_calm_phone)
            )
            OnboardingDeviceStyle.UNDECIDED -> Triple(
                stringResource(R.string.ob_p4_reflect_calm_undecided),
                title,
                stringResource(R.string.ob_p4_body_calm_undecided)
            )
        }
        OnboardingConcern.LEARN_BASELINE -> when (answers.deviceStyle) {
            OnboardingDeviceStyle.HAS_WATCH -> Triple(
                stringResource(R.string.ob_p4_reflect_learn_watch),
                title,
                stringResource(R.string.ob_p4_body_learn_watch)
            )
            OnboardingDeviceStyle.PHONE_ONLY -> Triple(
                stringResource(R.string.ob_p4_reflect_learn_phone),
                title,
                stringResource(R.string.ob_p4_body_learn_phone)
            )
            OnboardingDeviceStyle.UNDECIDED -> Triple(
                stringResource(R.string.ob_p4_reflect_learn_undecided),
                title,
                stringResource(R.string.ob_p4_body_learn_undecided)
            )
        }
        OnboardingConcern.UNDECIDED -> when (answers.deviceStyle) {
            OnboardingDeviceStyle.HAS_WATCH -> Triple(
                stringResource(R.string.ob_p4_reflect_open_watch),
                title,
                stringResource(R.string.ob_p4_body_open_watch)
            )
            OnboardingDeviceStyle.PHONE_ONLY -> Triple(
                stringResource(R.string.ob_p4_reflect_open_phone),
                title,
                stringResource(R.string.ob_p4_body_open_phone)
            )
            OnboardingDeviceStyle.UNDECIDED -> Triple(
                stringResource(R.string.ob_p4_reflect_open_undecided),
                title,
                stringResource(R.string.ob_p4_body_open_undecided)
            )
        }
    }
}

@Composable
private fun StepPage(
    stepNumber: Int,
    title: String,
    body: String,
    iconRes: Int,
    animationsEnabled: Boolean,
    onNext: () -> Unit
) {
    StandardInfoPage(
        paneLabel = title,
        title = title,
        body = body,
        animationsEnabled = animationsEnabled,
        visualContent = {
            // ステップ 1〜3 で同一構図を保ち、番号とアイコン部分のみ差し替える
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(0.78f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 26.dp, horizontal = 20.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape
                    ) {
                        Text(
                            text = stepNumber.toString(),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        },
        onNext = onNext
    )
}

@Composable
private fun TryItBreathingPage(
    hapticsEnabled: Boolean,
    onToggleHaptics: (Boolean) -> Unit,
    praisePool: List<String>,
    praiseSelector: PraiseSelector,
    animationsEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
    onCompletedTry: () -> Unit,
    onTryLater: () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var isBreathingMini by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var praiseText by remember { mutableStateOf<String?>(null) }
    var miniCueLabel by remember { mutableStateOf<String?>(null) }
    val breathAnim = remember { Animatable(0.4f) }
    var interruptMiniSignal by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    var interruptWaitSignal by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            interruptMiniSignal?.cancel()
            interruptWaitSignal?.cancel()
            onLockChange(false)
        }
    }

    val inhaleText = stringResource(R.string.ob_p12_inhale)
    val holdText = stringResource(R.string.ob_p12_hold)
    val exhaleText = stringResource(R.string.ob_p12_exhale)
    val title = stringResource(R.string.ob_p12_title)

    fun finishTryAndAdvance() {
        isBreathingMini = false
        isDone = true
        if (hapticsEnabled) {
            performStandardHaptic(view, PraiseTier.MEDIUM)
        }
        praiseText = praisePool[praiseSelector.nextIndex(PraiseTier.MEDIUM, praisePool.size)]
        val waitSignal = CompletableDeferred<Unit>()
        interruptWaitSignal = waitSignal
        scope.launch {
            withTimeoutOrNull(850L) { waitSignal.await() }
            onLockChange(false)
            onCompletedTry()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = isDone,
                onClick = { interruptWaitSignal?.complete(Unit) }
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .semantics {
                isTraversalGroup = true
                paneTitle = title
            }
    ) {
        // 試し操作ページは「見出し1行だけ」ルールを厳守（補足文なし）
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .semantics {
                    heading()
                    traversalIndex = 0f
                }
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.semantics { traversalIndex = 2f }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(188.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.ob_p12_tap_label)
                    ) {
                        if (isBreathingMini) {
                            // 途中タップで即完了へ割り込み可能
                            interruptMiniSignal?.complete(Unit)
                            return@clickable
                        }
                        if (isDone) {
                            // 戻ってきた場合も再試行できるようにリセットして開始
                            interruptWaitSignal?.cancel()
                            isDone = false
                            praiseText = null
                        }
                        isBreathingMini = true
                        onLockChange(true)
                        if (hapticsEnabled) {
                            performStandardHaptic(view, PraiseTier.SMALL)
                        }
                        val miniInterrupt = CompletableDeferred<Unit>()
                        interruptMiniSignal = miniInterrupt
                        scope.launch {
                            // 本番 4-2-6 と同比率のミニ呼吸（吸う2s → 止める1s → 吐く3s）
                            miniCueLabel = inhaleText
                            if (animationsEnabled) {
                                breathAnim.snapTo(0.25f)
                                val inhaleJob = launch {
                                    breathAnim.animateTo(1f, tween(2_000, easing = LinearEasing))
                                }
                                withTimeoutOrNull(2_000L) { miniInterrupt.await() }
                                inhaleJob.cancel()
                            } else {
                                withTimeoutOrNull(2_000L) { miniInterrupt.await() }
                            }

                            if (!miniInterrupt.isCompleted) {
                                miniCueLabel = holdText
                                if (hapticsEnabled) {
                                    performStandardHaptic(view, PraiseTier.SMALL)
                                }
                                withTimeoutOrNull(1_000L) { miniInterrupt.await() }
                            }

                            if (!miniInterrupt.isCompleted) {
                                miniCueLabel = exhaleText
                                if (hapticsEnabled) {
                                    performStandardHaptic(view, PraiseTier.SMALL)
                                }
                                if (animationsEnabled) {
                                    val exhaleJob = launch {
                                        breathAnim.animateTo(0.15f, tween(3_000, easing = LinearEasing))
                                    }
                                    withTimeoutOrNull(3_000L) { miniInterrupt.await() }
                                    exhaleJob.cancel()
                                } else {
                                    withTimeoutOrNull(3_000L) { miniInterrupt.await() }
                                }
                            }
                            finishTryAndAdvance()
                        }
                    }
            ) {
                OmamoriPetMobileAvatar(
                    state = if (isBreathingMini || isDone) OmamoriState.BREATHING else OmamoriState.CALM,
                    description = null,
                    breathFractionProvider = { breathAnim.value },
                    interactive = false,
                    size = 152.dp
                )
            }

            AnimatedContent(
                targetState = praiseText ?: miniCueLabel ?: stringResource(R.string.ob_p12_tap_label),
                transitionSpec = {
                    if (animationsEnabled) {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "tryStatus"
            ) { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (praiseText != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .semantics { traversalIndex = 3f }
        ) {
            if (isDone) {
                Button(
                    onClick = {
                        interruptWaitSignal?.cancel()
                        onLockChange(false)
                        onCompletedTry()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                ) {
                    Text(stringResource(R.string.ob_next))
                }
            }
            TextButton(
                onClick = { onToggleHaptics(!hapticsEnabled) },
                enabled = !isBreathingMini,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text = stringResource(
                        if (hapticsEnabled) R.string.ob_p12_haptics_on else R.string.ob_p12_haptics_off
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (!isDone) {
                TextButton(
                    onClick = {
                        interruptMiniSignal?.cancel()
                        interruptWaitSignal?.cancel()
                        onLockChange(false)
                        onTryLater()
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.ob_try_later))
                }
            }
        }
    }
}

@Composable
private fun ConnectPage(
    completePraise: String,
    tryItCompleted: Boolean,
    animationsEnabled: Boolean,
    onStartBreathing: () -> Unit,
    onGoHome: () -> Unit
) {
    val title = stringResource(R.string.ob_p15_title)
    val body = stringResource(R.string.ob_p15_body)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .semantics {
                isTraversalGroup = true
                paneTitle = title
            }
    ) {
        Spacer(Modifier.height(8.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .defaultMinSize(minHeight = 190.dp)
                .semantics { traversalIndex = 2f }
        ) {
            OmamoriPetMobileAvatar(
                state = OmamoriState.CALM,
                description = null,
                interactive = false,
                size = 136.dp
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (tryItCompleted) {
                Text(
                    text = completePraise,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    heading()
                    traversalIndex = 0f
                }
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { traversalIndex = 1f }
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp)
                .semantics { traversalIndex = 3f }
        ) {
            Button(
                onClick = onStartBreathing,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_spa_rounded),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ob_p15_cta))
            }
            TextButton(
                onClick = onGoHome,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(stringResource(R.string.ob_home_secondary))
            }
        }
    }
}

private fun performStandardHaptic(view: View, tier: PraiseTier) {
    val constant = when (tier) {
        PraiseTier.SMALL -> HapticFeedbackConstants.CLOCK_TICK
        PraiseTier.MEDIUM, PraiseTier.LARGE -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
        }
    }
    view.performHapticFeedback(constant)
}
