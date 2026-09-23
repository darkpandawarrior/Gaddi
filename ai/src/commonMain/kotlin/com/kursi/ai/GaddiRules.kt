package com.kursi.ai

import com.kursi.engine.ApplyOutcome
import com.kursi.engine.GameState
import com.kursi.engine.Intent
import com.kursi.engine.Phase
import com.kursi.engine.PlayerId
import com.kursi.engine.PlayerView
import com.kursi.engine.applyIntent
import com.kursi.engine.legalIntents
import com.siddharth.kmp.botspolicy.GameRules
import com.siddharth.kmp.botspolicy.Outcome
import com.siddharth.kmp.botspolicy.Policy as GenericPolicy

/**
 * Gaddi's concrete bot-policy shape: decide over the redacted [PlayerView] and legal [Intent]s. Every
 * bot tier in this module (Easy/Medium/Hard/Expert/Grandmaster/Persona) implements this — it replaces
 * the old engine-hosted `com.kursi.engine.Policy` (removed as part of the ai→engine inversion,
 * kmp-toolkit-family bots-policy lane: PROGRESS.md).
 */
typealias Policy = GenericPolicy<PlayerView, Intent>

/**
 * Adapts the engine's free-function API (whoActsNext/legalIntents/applyIntent/redact) to the generic
 * [GameRules] contract, so [com.siddharth.kmp.botspolicy.Ismcts] can drive Gaddi self-play without
 * importing `com.kursi.engine.*` itself.
 *
 * Lives in `:ai`, NOT `:engine` — the sketch that kicked off this inversion suggested `:engine` host
 * this wiring, but `:engine` must stay dependency-free (the `checkEnginePurity` tripwire) and cannot
 * see [GameRules] without depending on `:ai`, which would cycle back against `:ai`'s existing
 * dependency on `:engine`. `:engine` itself is untouched by this step except for the `Policy` →
 * `SimPolicy` rename in `Sim.kt` (that rename is unrelated to this adapter — it just frees the name).
 */
object GaddiRules : GameRules<GameState, Intent, PlayerId, PlayerView> {
    override fun whoActsNext(state: GameState): PlayerId? = com.kursi.engine.whoActsNext(state)

    override fun legalMoves(
        state: GameState,
        actor: PlayerId,
    ): List<Intent> = legalIntents(state, actor)

    override fun apply(
        state: GameState,
        move: Intent,
    ): Outcome<GameState> =
        when (val r = applyIntent(state, move)) {
            is ApplyOutcome.Accepted -> Outcome.Accepted(r.state)
            is ApplyOutcome.Rejected -> Outcome.Rejected(r.reason)
        }

    override fun isTerminal(state: GameState): Boolean = state.phase is Phase.GameOver

    override fun winner(state: GameState): PlayerId? = (state.phase as? Phase.GameOver)?.winner

    override fun redact(
        state: GameState,
        viewer: PlayerId,
    ): PlayerView = com.kursi.engine.redact(state, viewer)
}
