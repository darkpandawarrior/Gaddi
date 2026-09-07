package com.kursi.ai

import com.kursi.ai.advisor.MoveAdvice
import com.kursi.engine.Action
import com.kursi.engine.Intent
import com.kursi.engine.PlayerView
import com.kursi.engine.Role
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RankedAction(
    val action: String,
    val ismctsScore: Double,
)

object GameContextSerializer {
    private val json = Json { prettyPrint = false }

    fun serialize(
        view: PlayerView,
        ranked: List<MoveAdvice>,
    ): String {
        val opponents =
            view.players
                .filter { !it.eliminated && it.id != view.viewer }
                .map { opp ->
                    OpponentSnapshot(
                        name = "Seat${opp.seatIndex}",
                        coins = opp.coins,
                        influenceCount = opp.faceDownCount,
                        revealed = opp.faceUpRoles.joinToString(",") { it.label() },
                    )
                }

        val lastAction = describePhase(view)

        val candidates =
            ranked.take(5).map { advice ->
                RankedAction(
                    action = intentLabel(advice.intent),
                    ismctsScore = (advice.winProb * 100).toInt() / 100.0,
                )
            }

        val ctx =
            GameContext(
                coins = view.myCoins,
                influence =
                    view.myInfluence.map { it.label() } +
                        List(
                            (2 - view.myInfluence.size).coerceAtLeast(0),
                        ) { "?" },
                treasury = view.treasury,
                opponents = opponents,
                lastAction = lastAction,
                candidates = candidates,
            )

        return json.encodeToString(GameContext.serializer(), ctx)
    }

    private fun describePhase(view: PlayerView): String = view.phase.toString()

    /** Internal (not private): [com.kursi.ai.AiBotDecisionEngineTest] needs the exact string an LLM
     *  reply is matched against to script a "valid response" case without duplicating this mapping. */
    internal fun intentLabel(intent: Intent): String =
        when (intent) {
            is Intent.DeclareAction ->
                when (val a = intent.action) {
                    Action.Income -> "income"
                    Action.ForeignAid -> "foreign_aid"
                    Action.Tax -> "tax"
                    Action.Exchange -> "exchange"
                    is Action.Coup -> "coup_seat${a.target.raw}"
                    is Action.Assassinate -> "assassinate_seat${a.target.raw}"
                    is Action.Steal -> "steal_seat${a.target.raw}"
                    is Action.Investigate -> "investigate_seat${a.target.raw}"
                    Action.BailPe -> "bail_pe_bahar"
                    Action.Sabotage -> "sabotage"
                    is Action.Hawala -> "hawala_seat${a.to.raw}"
                    Action.Emergency -> "adhyadesh"
                }
            is Intent.Block -> "block_as_${intent.role.label()}"
            is Intent.Challenge -> "challenge"
            is Intent.Pass -> "pass"
            is Intent.ChooseInfluenceToLose -> "lose_influence"
            is Intent.ChooseExchange -> "keep_exchange"
            is Intent.ResolveInvestigate -> if (intent.forceRedraw) "force_redraw" else "leave_card"
        }

    fun intentMatchesLabel(
        intent: Intent,
        label: String,
    ): Boolean {
        val normalized = label.trim().lowercase()
        return intentLabel(intent) == normalized
    }

    private fun Role.label() = name.lowercase()

    @Serializable
    private data class GameContext(
        val coins: Int,
        val influence: List<String>,
        val treasury: Int,
        val opponents: List<OpponentSnapshot>,
        val lastAction: String,
        val candidates: List<RankedAction>,
    )

    @Serializable
    private data class OpponentSnapshot(
        val name: String,
        val coins: Int,
        val influenceCount: Int,
        val revealed: String,
    )
}
