package com.kursi.ai.provider

import com.siddharth.kmp.ai.UnavailableOnDeviceLlm
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.result.AiResult

/**
 * Web/wasmJs on-device LLM tier (consolidation #7 catches up): routes through toolkit `:ai`'s
 * [UnavailableOnDeviceLlm] (no on-device model in a browser) instead of a hand-rolled local stub —
 * same pattern as [OnDeviceAiProvider.jvm.kt]. A real answer in the browser now comes from the
 * BYOK cloud tier in [com.kursi.ai.MunshiNarrator]'s own provider chain instead: see
 * [com.kursi.ai.createMunshiNarrator] for how a saved cloud-provider key reaches it.
 */
actual class OnDeviceAiProvider actual constructor() : AiProvider {
    actual override val id = "on_device"
    actual override val displayName = "On-device AI"

    actual override suspend fun isAvailable(): Boolean = UnavailableOnDeviceLlm.isAvailable()

    actual override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> = UnavailableOnDeviceLlm.generate(messages.toOnDevicePrompt())
}
