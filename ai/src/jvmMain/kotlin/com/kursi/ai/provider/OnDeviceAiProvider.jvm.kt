package com.kursi.ai.provider

import com.siddharth.kmp.ai.UnavailableOnDeviceLlm
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider

/**
 * Desktop/JVM on-device LLM tier (consolidation #7): routes through toolkit `:ai`'s
 * [UnavailableOnDeviceLlm] (no on-device model on desktop) instead of a hand-rolled duplicate.
 */
actual class OnDeviceAiProvider actual constructor() : AiProvider {
    actual override val id = "on_device"
    actual override val displayName = "On-device AI"

    actual override suspend fun isAvailable(): Boolean = UnavailableOnDeviceLlm.isAvailable()

    actual override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): String = UnavailableOnDeviceLlm.generate(messages.toOnDevicePrompt()) ?: ""
}
