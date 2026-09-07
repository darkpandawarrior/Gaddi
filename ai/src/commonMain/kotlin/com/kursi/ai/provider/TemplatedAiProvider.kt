package com.kursi.ai.provider

import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result

/**
 * PROVIDER MATRIX TIER 3 (spec §8.5) — the always-available floor every selection chain falls back
 * to. Deliberately returns a blank completion rather than any generated text: callers (e.g.
 * [com.kursi.ai.MunshiNarrator]) treat a blank/absent AI line as "no upgrade" and simply keep
 * showing their own deterministic templated string, which is what makes this tier truthful — the
 * actual templated copy lives at each call site (e.g. [com.kursi.feature.game] `KursiVoice`), not
 * here. This object exists only so `:ai` always has a non-null, always-[isAvailable] last resort to
 * plug into [com.siddharth.kmp.llmchat.buildProviderChain]'s `fallback` slot.
 */
object TemplatedAiProvider : AiProvider {
    override val id = "templated"
    override val displayName = "Templated (offline, deterministic)"

    override suspend fun isAvailable() = true

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> = Result.Success("")
}
