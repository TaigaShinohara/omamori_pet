package com.omamoripet.mobile.ai

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

enum class AiBackendStatus {
    GEMINI_NANO_AVAILABLE,
    GEMINI_NANO_DOWNLOADABLE,
    FALLBACK_ONLY
}

data class AdvisorInput(
    val omamoriState: OmamoriState,
    val currentBpm: Int?,
    val restingBaselineBpm: Int,
    val motionState: MotionState,
    val todayElevatedCount: Int,
    val todayBreathingCount: Int,
    val lastBreathingDeltaBpm: Int?,
    val isWatchConnected: Boolean = false,
    val isJapanese: Boolean = Locale.getDefault().language == "ja"
)

data class AdvisorOutput(
    val text: String,
    val usedGeminiNano: Boolean,
    val backendStatus: AiBackendStatus
)

interface CalmAdvisorEngine {
    suspend fun checkBackendStatus(): AiBackendStatus
    suspend fun generateReflection(input: AdvisorInput): AdvisorOutput
    suspend fun prepareGeminiNano(): Boolean
    fun close()
}

/**
 * 確定論的・安全なローカルフォールバック助言エンジン。
 * 不安を煽る言葉を使わず、実測値のみを踏まえた短い寄り添いの言葉を返す。
 */
class TemplateCalmAdvisorEngine : CalmAdvisorEngine {
    override suspend fun checkBackendStatus(): AiBackendStatus = AiBackendStatus.FALLBACK_ONLY

    override suspend fun generateReflection(input: AdvisorInput): AdvisorOutput {
        val text = if (input.isJapanese) buildJapanese(input) else buildEnglish(input)
        return AdvisorOutput(
            text = text,
            usedGeminiNano = false,
            backendStatus = AiBackendStatus.FALLBACK_ONLY
        )
    }

    override suspend fun prepareGeminiNano(): Boolean = false

    override fun close() = Unit

    private fun buildJapanese(input: AdvisorInput): String {
        val bpm = input.currentBpm
        val delta = bpm?.minus(input.restingBaselineBpm)
        return when (input.omamoriState) {
            OmamoriState.ELEVATED_AT_REST -> {
                val deltaStr = delta?.let { if (it >= 0) "+$it" else "$it" } ?: "--"
                "静止中に ${bpm ?: "--"} bpm（普段より $deltaStr）。自然な反応なので、1分だけ長く吐いてみましょう。"
            }
            OmamoriState.BREATHING -> {
                "肩の力を抜いて、吐く息を長めに。"
            }
            OmamoriState.CALM -> when {
                input.lastBreathingDeltaBpm != null && input.lastBreathingDeltaBpm < 0 ->
                    "深呼吸で ${-input.lastBreathingDeltaBpm} bpm 落ち着きました。安定したリズムです。"
                input.motionState == MotionState.RECENT_MOVEMENT ->
                    "歩いたあとの自然な変化です。心配いりません。"
                else ->
                    "目安（${input.restingBaselineBpm} bpm）と調和した穏やかなペースです。"
            }
            OmamoriState.RESTING -> {
                if (input.isWatchConnected) {
                    "手首につけると、いつものリズムで見守りを再開。"
                } else {
                    "時計がなくても1分間の深呼吸ができます。"
                }
            }
        }
    }

    private fun buildEnglish(input: AdvisorInput): String {
        val bpm = input.currentBpm
        val delta = bpm?.minus(input.restingBaselineBpm)
        return when (input.omamoriState) {
            OmamoriState.ELEVATED_AT_REST ->
                "Still at ${bpm ?: "--"} bpm (+${delta ?: 0}). Try a gentle 1-minute exhale."
            OmamoriState.BREATHING ->
                "Drop your shoulders and lengthen each exhale."
            OmamoriState.CALM -> when {
                input.lastBreathingDeltaBpm != null && input.lastBreathingDeltaBpm < 0 ->
                    "Your breath eased heart rate by ${-input.lastBreathingDeltaBpm} bpm. Steady and calm."
                input.motionState == MotionState.RECENT_MOVEMENT ->
                    "Post-walk rise is completely natural right now."
                else ->
                    "Steady around your ${input.restingBaselineBpm} bpm baseline."
            }
            OmamoriState.RESTING -> {
                if (input.isWatchConnected) {
                    "Put on your Watch to resume quiet awareness."
                } else {
                    "Start a 1-minute breath anytime, no watch required."
                }
            }
        }
    }
}

/**
 * Pixel 等対応端末で Gemini Nano (ML Kit GenAI Prompt API / AICore) を優先使用し、
 * 非対応・準備中・エラー時は TemplateCalmAdvisorEngine へシームレスにフォールバックする。
 */
class GeminiNanoAdvisorEngine(
    private val fallback: TemplateCalmAdvisorEngine = TemplateCalmAdvisorEngine()
) : CalmAdvisorEngine {

    private val model by lazy { Generation.getClient() }

    override suspend fun checkBackendStatus(): AiBackendStatus = withContext(Dispatchers.IO) {
        runCatching {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> AiBackendStatus.GEMINI_NANO_AVAILABLE
                FeatureStatus.DOWNLOADABLE -> AiBackendStatus.GEMINI_NANO_DOWNLOADABLE
                else -> AiBackendStatus.FALLBACK_ONLY
            }
        }.getOrDefault(AiBackendStatus.FALLBACK_ONLY)
    }

    override suspend fun prepareGeminiNano(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (model.checkStatus() == FeatureStatus.DOWNLOADABLE) {
                model.download().collect { /* wait for download completion */ }
                model.checkStatus() == FeatureStatus.AVAILABLE
            } else {
                model.checkStatus() == FeatureStatus.AVAILABLE
            }
        }.getOrDefault(false)
    }

    override suspend fun generateReflection(input: AdvisorInput): AdvisorOutput = withContext(Dispatchers.IO) {
        val status = checkBackendStatus()
        if (status != AiBackendStatus.GEMINI_NANO_AVAILABLE) {
            return@withContext fallback.generateReflection(input).copy(backendStatus = status)
        }

        val prompt = buildPrompt(input)
        val generated = runCatching {
            val request = generateContentRequest(
                TextPart(prompt)
            ) {}
            model.generateContent(request).candidates.firstOrNull()?.text?.trim()
        }.getOrNull()

        if (!generated.isNullOrBlank()) {
            AdvisorOutput(
                text = generated,
                usedGeminiNano = true,
                backendStatus = status
            )
        } else {
            fallback.generateReflection(input).copy(backendStatus = status)
        }
    }

    private fun buildPrompt(input: AdvisorInput): String {
        val delta = input.currentBpm?.let { it - input.restingBaselineBpm }
        val stateMeaningJa = when (input.omamoriState) {
            OmamoriState.CALM -> "落ち着いている（平常時ペース）"
            OmamoriState.ELEVATED_AT_REST -> "3分以上静止中なのに普段より心拍が高め"
            OmamoriState.BREATHING -> "深呼吸ガイド中"
            OmamoriState.RESTING -> "装着・計測待機中"
        }
        val breathDeltaJa = input.lastBreathingDeltaBpm?.let {
            if (it < 0) "直近の深呼吸で心拍が ${-it} bpm 低下" else "直近の深呼吸完了（変化: +$it bpm）"
        } ?: "直近の深呼吸前後比較なし"

        return if (input.isJapanese) {
            """
            あなたは「お守りペット」という穏やかなセルフケアアプリの相棒です。
            以下の実測データだけに基づき、不安や緊張を和らげる優しい2文以内の日本語コメントを返してください。
            医療診断や危険を煽る表現、データにない憶測は禁止です。
            - 状態: $stateMeaningJa
            - 現在心拍: ${input.currentBpm ?: "未計測"} bpm（平常時目安: ${input.restingBaselineBpm} bpm、差分: ${delta ?: "不明"}）
            - 動作状態: ${input.motionState.name}
            - 本日の静止中心拍上昇回数: ${input.todayElevatedCount} 回
            - 本日の深呼吸回数: ${input.todayBreathingCount} 回（$breathDeltaJa）
            """.trimIndent()
        } else {
            """
            You are Omamori Pet, a gentle wellness companion.
            Based ONLY on these real measurements, reply in 2 short calm sentences to reassure the user.
            Never diagnose, never alarm, and never speculate beyond the numbers.
            - State: ${input.omamoriState.name}
            - Current HR: ${input.currentBpm ?: "N/A"} bpm (Resting baseline: ${input.restingBaselineBpm} bpm, Delta: ${delta ?: "N/A"})
            - Motion: ${input.motionState.name}
            - Elevated-at-rest notices today: ${input.todayElevatedCount}
            - Breathing sessions today: ${input.todayBreathingCount} (Last delta: ${input.lastBreathingDeltaBpm ?: "N/A"})
            """.trimIndent()
        }
    }

    override fun close() {
        runCatching { model.close() }
    }
}
