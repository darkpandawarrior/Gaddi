package com.kursi.ai.provider

import com.siddharth.kmp.ai.UnavailableOnDeviceLlm
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.result.AiResult

/**
 * Desktop/JVM on-device LLM tier (consolidation #7): routes through toolkit `:ai`'s
 * [UnavailableOnDeviceLlm] (no on-device model on desktop) instead of a hand-rolled duplicate.
 *
 * ktlint:standard:function-naming — a constructor-like factory. Kotlin's own convention
 * allows PascalCase here; ktlint only recognises the pattern when the function name equals
 * its return TYPE name, which it cannot be when the factory returns an interface.
 */
@Suppress("ktlint:standard:function-naming")
actual fun OnDeviceAiProvider(): AiProvider = JvmOnDeviceAiProvider()

private class JvmOnDeviceAiProvider : AiProvider {
    override val id = "on_device"
    override val displayName = "On-device AI"

    override suspend fun isAvailable(): Boolean = UnavailableOnDeviceLlm.isAvailable()

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> = UnavailableOnDeviceLlm.generate(messages.toOnDevicePrompt())
}
