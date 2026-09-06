package com.kursi.ai.provider

import com.siddharth.kmp.ai.CompositeOnDeviceLlm
import com.siddharth.kmp.ai.FoundationModelsOnDeviceLlm
import com.siddharth.kmp.ai.MediaPipeOnDeviceLlm
import com.siddharth.kmp.ai.OnDeviceLlm
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider

/**
 * iOS on-device LLM tier (consolidation #7): routes through toolkit `:ai`'s Foundation Models →
 * MediaPipe chain. Both backends are stubs pending a Swift bridge (see toolkit's own KDoc on
 * FoundationModelsOnDeviceLlm/MediaPipeOnDeviceLlm) — same always-unavailable behavior as Kursi's old
 * local stub, now sourced from the shared toolkit instead of a duplicate.
 */
actual class OnDeviceAiProvider actual constructor() : AiProvider {
    actual override val id = "on_device"
    actual override val displayName = "On-device AI (Apple Intelligence)"

    private val llm: OnDeviceLlm = CompositeOnDeviceLlm(listOf(FoundationModelsOnDeviceLlm(), MediaPipeOnDeviceLlm()))

    actual override suspend fun isAvailable(): Boolean = llm.isAvailable()

    actual override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): String = llm.generate(messages.toOnDevicePrompt()) ?: ""
}
