package com.kursi.ai

import com.kursi.ai.provider.OnDeviceAiProvider
import com.kursi.ai.provider.TemplatedAiProvider
import com.kursi.engine.GameEvent
import com.kursi.engine.PlayerView
import com.siddharth.kmp.llmchat.AiChunk
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.llmchat.AiProviderConfig
import com.siddharth.kmp.llmchat.buildProviderChain
import com.siddharth.kmp.llmchat.firstAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * THE MUNSHI — the AI narrator (spec §8.1). Turns the redacted [PlayerView] the human is currently
 * looking at + the recent [GameEvent]s into ONE grounded, in-character sentence, streamed in as the
 * model writes it, or an empty [Flow] when nothing should upgrade the caller's own templated line.
 *
 * PROVIDER MATRIX / SELECTION POLICY (spec §8.5): on-device is tried first (auto-detected, zero
 * setup for the player) → any BYOK cloud provider [cloudConfig] explicitly opts into (inert by
 * default — a null API key simply never enters the chain, per
 * [com.siddharth.kmp.llmchat.buildProviderChain]) → the templated floor, which this class reports
 * back to its caller as an empty flow rather than any generated text. That keeps tier 3 truthful:
 * the real templated copy lives at the call site (e.g. `BeatHeadline.headlineFor`), not here.
 *
 * ENCAPSULATION: none of [com.siddharth.kmp.llmchat]'s types appear in this class's own public
 * surface (only plain KMP/engine/coroutines-flow types do), so a caller like `:feature:game` never
 * needs that BYOK/on-device dependency graph on its own compile classpath — it only ever sees
 * `Flow<String>`.
 *
 * STREAMING + CANCELLATION (spec §8.6): [narrate] emits the accumulated line so far as tokens
 * arrive via [AiProvider.completeStream] — a provider not yet taught real token streaming still
 * works (its `completeStream` default replays one whole-line [AiChunk.Token]). Cancelling the
 * collecting coroutine (the caller starts a fresh beat) tears down this call's whole chain,
 * including whichever backend is mid-generation — there is no separate stop/abort call to make;
 * [narrate] itself never blocks a beat, since the caller's templated line has already rendered
 * synchronously by the time this is collected.
 *
 * GUARDRAILS (spec §8.6) — enforced by what this class does NOT do: it never sees or receives
 * hidden cards ([view]/[events] are already redacted/public-only upstream), never writes to
 * `humanIntentLog`, never mutates `GameState`, and never gates a legal action. Callers must not
 * fold the result into any of those, and must not persist it into a replay record — it is
 * display-only, always regenerable, never part of the deterministic replay contract.
 */
class MunshiNarrator(
    cloudConfig: AiProviderConfig = AiProviderConfig(useOnDevice = true),
    onDevice: AiProvider = OnDeviceAiProvider(),
) {
    private val chain = buildProviderChain(cloudConfig, fallback = TemplatedAiProvider, onDevice = onDevice)

    /**
     * Stream the narration for the most recent meaningful beat visible in [events], from [view]'s
     * point of view. Each emission is the full line accumulated so far (never blank); an empty
     * flow (nothing emitted at all) means no provider above the templated floor is available, the
     * call timed out before any usable text arrived, or it failed — every one of those collapses
     * to "the caller keeps its own templated line," the same as the old single-shot API's `null`.
     */
    fun narrate(
        view: PlayerView,
        events: List<GameEvent>,
    ): Flow<String> =
        flow {
            val provider = firstAvailable(chain, TemplatedAiProvider)
            // Identity, not `===`: buildProviderChain wraps every entry (including the templated
            // fallback) in an internal guard type, so the instance picked here is never reference-
            // equal to the raw TemplatedAiProvider singleton even when it IS the templated tier.
            if (provider.id == TemplatedAiProvider.id) return@flow
            val soFar = StringBuilder()
            withTimeoutOrNull(TIMEOUT_MS) {
                provider
                    .completeStream(
                        messages =
                            listOf(
                                AiMessage(AiMessage.Role.SYSTEM, SYSTEM_PROMPT),
                                AiMessage(AiMessage.Role.USER, buildPrompt(view, events)),
                            ),
                        config = AiConfig(maxTokens = MAX_TOKENS, temperature = TEMPERATURE),
                    ).collect { chunk ->
                        if (chunk is AiChunk.Token) soFar.append(chunk.text)
                        // AiChunk.Failed: nothing to add — whatever's already in `soFar` (possibly
                        // nothing) is what the blank check below judges.
                        val line = soFar.toString().trim()
                        if (line.isNotBlank()) emit(line)
                    }
            }
        }.catch { e -> if (e is CancellationException) throw e }

    /** Redacted-view-only context (spec §8.6): never anything beyond what [view]/[events] carry. */
    private fun buildPrompt(
        view: PlayerView,
        events: List<GameEvent>,
    ): String {
        val opponents =
            view.players
                .filter { it.id != view.viewer }
                .joinToString("; ") { o ->
                    if (o.eliminated) "Seat${o.seatIndex}:out" else "Seat${o.seatIndex}:${o.coins}c/${o.faceDownCount}inf"
                }
        val recent = events.takeLast(EVENT_WINDOW).joinToString(" | ") { it.toString() }
        return "You (the viewer) have ${view.myCoins} coins and ${view.myInfluence.size} influence. " +
            "Opponents: $opponents. Recent table events: $recent. " +
            "Narrate the most recent meaningful event in one grounded sentence, in-character as the court scribe."
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val EVENT_WINDOW = 6
        const val MAX_TOKENS = 60
        const val TEMPERATURE = 0.8f
        const val SYSTEM_PROMPT =
            "You are the Munshi, a court scribe narrating a political card game called Kursi. Write ONE short, " +
                "grounded, in-character sentence (max 20 words) describing the most recent event from the facts " +
                "given. Hinglish flavor. Never invent facts, names, or hidden cards beyond what is given."
    }
}
