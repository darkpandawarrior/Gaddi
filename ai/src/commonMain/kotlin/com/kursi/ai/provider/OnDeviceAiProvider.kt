package com.kursi.ai.provider

import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider

/**
 * An `expect class` has to declare the members it inherits rather than leaving them to the actuals —
 * the compiler checks the expect declaration against [AiProvider] on its own. `complete` carries no
 * default for `config` here because an override may not restate one; it still inherits the
 * interface's `AiConfig()` default at every call site.
 */
expect class OnDeviceAiProvider() : AiProvider {
    override val id: String
    override val displayName: String

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): String

    override suspend fun isAvailable(): Boolean
}

/**
 * Flattens a chat-shaped [AiMessage] list into the single text prompt toolkit `:ai`'s [com.siddharth.kmp.ai.OnDeviceLlm]
 * expects (that seam is one-shot text-in/text-out, not multi-turn) — shared by every platform actual
 * that routes through it.
 */
internal fun List<AiMessage>.toOnDevicePrompt(): String = joinToString("\n") { "${it.role}: ${it.content}" }
