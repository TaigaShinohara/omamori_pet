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
                val deltaStr = delta?.let { "+$it" } ?: ""
                "動いていないのに心拍が ${bpm ?: "--"} bpm（普段 $deltaStr）と少し早めです。身体が反応しているだけなので、まずは1分だけ息を長く吐いてみましょう。"
            }
            OmamoriState.BREATHING -> {
                "いま深呼吸のリズムに合わせられています。肩の力を抜いて、吐く息を長めにしてみましょう。"
            }
            OmamoriState.CALM -> when {
                input.lastBreathingDeltaBpm != null && input.lastBreathingDeltaBpm < 0 ->
                    "先ほどの深呼吸で心拍が ${input.lastBreathingDeltaBpm} bpm 落ち着きました。今は平常時の安定したリズムに戻っています。"
                input.motionState == MotionState.RECENT_MOVEMENT ->
                    "直近に動きがあったため心拍は活動モードです。自然な反応なので心配はいりません。"
                else ->
                    "現在の心拍は ${bpm ?: input.restingBaselineBpm} bpm 付近で、平常時の目安（${input.restingBaselineBpm} bpm）とよく調和しています。このままで大丈夫です。"
            }
            OmamoriState.RESTING -> {
                "ウォッチの装着やデモ表示を切り替えると、平常時の基準（${input.restingBaselineBpm} bpm）と比べた状態をお届けします。"
            }
        }
    }

    private fun buildEnglish(input: AdvisorInput): String {
        val bpm = input.currentBpm
        val delta = bpm?.minus(input.restingBaselineBpm)
        return when (input.omamoriState) {
            OmamoriState.ELEVATED_AT_REST ->
                "You are still, and your heart rate is ${bpm ?: "--"} bpm (+${delta ?: 0} above your calm baseline). Nothing is wrong—try a gentle 1-minute exhale."
            OmamoriState.BREATHING ->
                "You are pacing a soft 4-2-6 breath. Let your shoulders drop on each slow exhale."
            OmamoriState.CALM -> when {
                input.lastBreathingDeltaBpm != null && input.lastBreathingDeltaBpm < 0 ->
                    "Your recent breathing session lowered your heart rate by ${-input.lastBreathingDeltaBpm} bpm. You are back in a calm, steady range."
                input.motionState == MotionState.RECENT_MOVEMENT ->
                    "You were moving recently, so a higher heart rate is completely natural right now."
                else ->
                    "Your heart rate (${bpm ?: input.restingBaselineBpm} bpm) aligns peacefully with your resting baseline (${input.restingBaselineBpm} bpm)."
            }
            OmamoriState.RESTING ->
                "Put on your Pixel Watch or switch on Demo Mode to reflect on your resting heart rate."
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
        return if (input.isJapanese) {
            """
            あなたは「お守りペット」という穏やかなセルフケアアプリの相棒です。
            以下の実測データだけに基づき、不安や緊張を和らげる優しい2文以内の日本語コメントを返してください。
            医療診断や危険を煽る表現、データにない憶測は禁止です。
            - 状態: ${input.omamoriState.name}
            - 現在心拍: ${input.currentBpm ?: "未計測"} bpm（平常時目安: ${input.restingBaselineBpm} bpm、差分: ${delta ?: "不明"}）
            - 動作状態: ${input.motionState.name}
            - 本日の静止中心拍上昇回数: ${input.todayElevatedCount} 回
            - 本日の深呼吸回数: ${input.todayBreathingCount} 回
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
            - Breathing sessions today: ${input.todayBreathingCount}
            """.trimIndent()
        }
    }

    override fun close() {
        runCatching { model.close() }
    }
}
