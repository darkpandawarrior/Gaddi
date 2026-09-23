package com.kursi.ai.provider

import com.siddharth.kmp.ai.CompositeOnDeviceLlm
import com.siddharth.kmp.ai.FoundationModelsOnDeviceLlm
import com.siddharth.kmp.ai.MediaPipeOnDeviceLlm
import com.siddharth.kmp.ai.OnDeviceLlm
import com.siddharth.kmp.llmchat.AiChunk
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.result.AiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * iOS on-device LLM tier (consolidation #7): routes through toolkit `:ai`'s Foundation Models →
 * MediaPipe chain. Both backends are stubs pending a Swift bridge (see toolkit's own KDoc on
 * FoundationModelsOnDeviceLlm/MediaPipeOnDeviceLlm) — same always-unavailable behavior as Gaddi's old
 * local stub, now sourced from the shared toolkit instead of a duplicate.
 *
 * ktlint:standard:function-naming — a constructor-like factory. Kotlin's own convention
 * allows PascalCase here; ktlint only recognises the pattern when the function name equals
 * its return TYPE name, which it cannot be when the factory returns an interface.
 */
@Suppress("ktlint:standard:function-naming")
actual fun OnDeviceAiProvider(): AiProvider = IosOnDeviceAiProvider()

private class IosOnDeviceAiProvider : AiProvider {
    override val id = "on_device"
    override val displayName = "On-device AI (Apple Intelligence)"

    private val llm: OnDeviceLlm = CompositeOnDeviceLlm(listOf(FoundationModelsOnDeviceLlm(), MediaPipeOnDeviceLlm()))

    override suspend fun isAvailable(): Boolean = llm.isAvailable()

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> = llm.generate(messages.toOnDevicePrompt())

    override fun completeStream(
        messages: List<AiMessage>,
        config: AiConfig,
    ): Flow<AiChunk> = llm.generateStream(messages.toOnDevicePrompt()).map { AiChunk.Token(it) }
}
