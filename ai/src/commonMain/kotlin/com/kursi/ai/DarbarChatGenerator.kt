package com.kursi.ai

import com.kursi.ai.persona.BotPersona
import com.kursi.engine.PlayerView
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.result.getOrNull

enum class ChatTrigger {
    GAME_START,
    CHALLENGE_WON,
    CHALLENGE_LOST,
    COUP_LANDED,
    INFLUENCE_LOST,
    BLOCK_SUCCESS,
    PLAYER_ELIMINATED,
    BOAST,
    TRASH_TALK,
}

object DarbarChatGenerator {
    private val chatConfig = AiConfig(maxTokens = 40, temperature = 0.9f)

    suspend fun generateLine(
        persona: BotPersona,
        arc: DarbarArc?,
        trigger: ChatTrigger,
        view: PlayerView,
        provider: AiProvider,
    ): String {
        val systemPrompt = buildChatSystemPrompt(persona, arc)
        val userPrompt = buildChatUserPrompt(trigger, view)

        val result =
            runCatching {
                provider.complete(
                    messages =
                        listOf(
                            AiMessage(AiMessage.Role.SYSTEM, systemPrompt),
                            AiMessage(AiMessage.Role.USER, userPrompt),
                        ),
                    config = chatConfig,
                )
            }.getOrNull()?.getOrNull().orEmpty().trim()

        return result.ifBlank { fallbackLine(persona, trigger) }
    }

    private fun buildChatSystemPrompt(
        persona: BotPersona,
        arc: DarbarArc?,
    ): String {
        val arcNote = arc?.let { " Current arc: ${it.name.lowercase()}." } ?: ""
        return buildString {
            append("You are ${persona.name} (${persona.title}) in a political card game called Kursi.")
            append(arcNote)
            append(" Speak in Hinglish (mix Hindi and English) in character, max 10 words.")
            append(" No game rules — just a sharp in-character quip about the current situation.")
        }
    }

    private fun buildChatUserPrompt(
        trigger: ChatTrigger,
        view: PlayerView,
    ): String {
        val context =
            "Coins: ${view.myCoins}, Influence left: ${view.myInfluence.size}. " +
                "Opponents alive: ${view.players.count { !it.eliminated }}."
        val triggerDesc =
            when (trigger) {
                ChatTrigger.GAME_START -> "The game has just started."
                ChatTrigger.CHALLENGE_WON -> "You just won a challenge against someone who doubted you."
                ChatTrigger.CHALLENGE_LOST -> "You just lost a challenge — your bluff was called."
                ChatTrigger.COUP_LANDED -> "You just executed a Coup and eliminated someone's influence."
                ChatTrigger.INFLUENCE_LOST -> "You just lost an influence card."
                ChatTrigger.BLOCK_SUCCESS -> "You successfully blocked an action against you."
                ChatTrigger.PLAYER_ELIMINATED -> "A player has just been eliminated from the game."
                ChatTrigger.BOAST -> "Things are going well for you right now."
                ChatTrigger.TRASH_TALK -> "You want to unsettle an opponent."
            }
        return "$triggerDesc $context Say something in character."
    }

    private fun fallbackLine(
        persona: BotPersona,
        trigger: ChatTrigger,
    ): String {
        val barks = persona.barks.lines
        val event =
            when (trigger) {
                ChatTrigger.GAME_START -> com.kursi.ai.persona.BarkEvent.ACT
                ChatTrigger.CHALLENGE_WON -> com.kursi.ai.persona.BarkEvent.WIN_CHALLENGE
                ChatTrigger.CHALLENGE_LOST -> com.kursi.ai.persona.BarkEvent.LOSE_CHALLENGE
                ChatTrigger.COUP_LANDED -> com.kursi.ai.persona.BarkEvent.ACT
                ChatTrigger.INFLUENCE_LOST -> com.kursi.ai.persona.BarkEvent.ELIMINATED
                ChatTrigger.BLOCK_SUCCESS -> com.kursi.ai.persona.BarkEvent.BLOCK
                ChatTrigger.PLAYER_ELIMINATED -> com.kursi.ai.persona.BarkEvent.WIN_GAME
                ChatTrigger.BOAST -> com.kursi.ai.persona.BarkEvent.TAUNT
                ChatTrigger.TRASH_TALK -> com.kursi.ai.persona.BarkEvent.TAUNT
            }
        return barks[event]?.randomOrNull() ?: ""
    }
}
