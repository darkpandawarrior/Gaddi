package com.kursi.ai

import com.kursi.ai.advisor.MoveAdvisor
import com.kursi.ai.persona.BotPersona
import com.kursi.engine.GameState
import com.kursi.engine.Intent
import com.kursi.engine.PlayerId
import com.kursi.engine.legalIntents
import com.kursi.engine.redact
import com.siddharth.kmp.botspolicy.SearchBudget
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.result.PromptGuard
import com.siddharth.kmp.result.getOrNull
import kotlinx.coroutines.withTimeoutOrNull

private val QUICK_BUDGET = SearchBudget(maxMillis = 200L, maxIterations = 800, rolloutHorizon = 8)

class AiBotDecisionEngine(
    seed: Long,
) {
    private val advisor = MoveAdvisor(seed, QUICK_BUDGET)

    suspend fun decide(
        state: GameState,
        botId: PlayerId,
        persona: BotPersona,
        arc: DarbarArc?,
        provider: AiProvider,
    ): Intent {
        val legal = legalIntents(state, botId)
        if (legal.size == 1) return legal.single()

        val view = redact(state, botId)
        val ranked = advisor.advise(state, botId, legal)
        val fallback = ranked.firstOrNull { it.recommended }?.intent ?: legal.first()

        // Availability check: a provider that reports itself unavailable never gets called — no
        // 5s timeout wait for a model that was never going to answer, straight to the ISMCTS floor.
        if (!provider.isAvailable()) return fallback

        val context = GameContextSerializer.serialize(view, ranked)
        val systemPrompt = PersonaPrompts.systemPrompt(persona, arc)
        // The serialized game state is otherwise-untrusted free text from the model's point of view
        // (opponent seat labels are engine-controlled today, but this is the one seam a future
        // player-nameable field would reach without another engineer having to remember to add the
        // guard) — same PromptGuard every other AiProvider seam applies to its USER message.
        val guardedContext = PromptGuard.wrap(context).text

        val llmResponse =
            withTimeoutOrNull(5_000L) {
                runCatching {
                    provider.complete(
                        messages =
                            listOf(
                                AiMessage(AiMessage.Role.SYSTEM, systemPrompt),
                                AiMessage(AiMessage.Role.USER, guardedContext),
                            ),
                    )
                }.getOrNull()?.getOrNull()
            }

        return resolveIntent(llmResponse?.trim(), ranked, legal, botId) ?: fallback
    }

    private fun resolveIntent(
        actionStr: String?,
        ranked: List<com.kursi.ai.advisor.MoveAdvice>,
        legal: List<Intent>,
        botId: PlayerId,
    ): Intent? {
        if (actionStr.isNullOrBlank()) return null

        val candidate =
            ranked.firstOrNull { advice ->
                GameContextSerializer.intentMatchesLabel(advice.intent, actionStr)
            }
        if (candidate != null) return candidate.intent

        return legal.firstOrNull { intent ->
            GameContextSerializer.intentMatchesLabel(intent, actionStr)
        }
    }
}
