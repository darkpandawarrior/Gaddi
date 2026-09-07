package com.kursi.ai

import com.kursi.ai.provider.createSecureKeyStore
import com.siddharth.kmp.llmchat.AiProviderConfig
import com.siddharth.kmp.llmchat.ProviderId
import com.siddharth.kmp.llmchat.loadAiProviderConfig

/**
 * Builds the [MunshiNarrator] the app should actually construct [com.kursi.feature.game.GameViewModel]
 * with — reads whichever cloud-provider key the settings screen has saved into
 * [com.siddharth.kmp.llmchat.SecureKeyStore] so the BYOK cloud tier of [MunshiNarrator]'s own
 * provider chain is reachable. Before this, every real call site built `MunshiNarrator()` with the
 * zero-arg default, so [MunshiNarrator.narrate]'s cloud tier could never fire (see the lane brief).
 *
 * [consentGiven] false means "no on-device or cloud model call at all" — the exact promise the
 * settings screen's own AI-features toggle copy makes — so it maps to an empty [AiProviderConfig]
 * (`useOnDevice = false`, no keys), which collapses [MunshiNarrator]'s chain to its templated floor
 * only, same as [MunshiNarrator.narrate]'s "nothing above templated is available" case.
 */
fun createMunshiNarrator(
    consentGiven: Boolean,
    selectedProviderName: String,
): MunshiNarrator {
    if (!consentGiven) return MunshiNarrator(cloudConfig = AiProviderConfig())
    val selected = ProviderId.entries.firstOrNull { it.name == selectedProviderName } ?: ProviderId.OFFLINE_FALLBACK
    val config = loadAiProviderConfig(createSecureKeyStore()::getKey, selected, useOnDevice = true)
    return MunshiNarrator(cloudConfig = config)
}
