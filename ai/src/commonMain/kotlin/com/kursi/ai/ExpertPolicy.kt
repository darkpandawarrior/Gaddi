package com.kursi.ai

import com.kursi.engine.*
import com.siddharth.kmp.botspolicy.SearchBudget

/**
 * EXPERT tier AI — Information Set Monte Carlo Tree Search (ISMCTS) over determinized worlds.
 *
 * Architecture:
 *  - [BeliefModel]: Bayesian posterior over opponents' hidden roles, updated from observable events.
 *  - [Determinizer]: Samples a consistent full [GameState] from a [PlayerView] using the belief model.
 *  - [IsmctsSearch]: ISMCTS tree search — builds a shared tree across many determinizations so that
 *    moves are selected based on their expected value across the space of possible hidden states.
 *  - Fallback to [HardPolicy] if search errors or returns an illegal move.
 *
 * The caller can optionally call [observe] after each game event to keep the belief model updated.
 *
 * Implements both [Policy] and [SimPolicy] — see [EasyPolicy]'s KDoc for why.
 */
class ExpertPolicy(
    seed: Long,
    val budget: SearchBudget = SearchBudget(),
) : Policy,
    SimPolicy {
    private var rng = Rng(seed)
    private val fallback = HardPolicy(seed + 77777L)
    private val beliefModel = BeliefModel()
    val memory = BotMemory()
    private val determinizer = Determinizer(beliefModel)
    private val search = IsmctsSearch(determinizer, budget, rolloutSeed = seed + 999L)

    override fun decide(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        require(legal.isNotEmpty()) { "no legal intents supplied" }
        if (legal.size == 1) return legal.single()
        return try {
            val chosen = search.chooseIntent(view, legal, memory, rng)
            val (_, r) = rng.nextLong()
            rng = r
            if (chosen in legal) chosen else fallback.decide(view, legal)
        } catch (ignored: Throwable) {
            // Deliberately ignored, and named so. The search is best-effort: a determinization
            // bug, an arithmetic edge or a stack overflow deep in a rollout must degrade to the
            // fallback policy, never surface as a crash mid-hand. `ignored` matches detekt's
            // allowedExceptionNameRegex, so the intent is on the page instead of in a baseline.
            fallback.decide(view, legal)
        }
    }

    /** Feed game events into the belief model so it can track opponent behaviour. */
    fun observe(
        event: GameEvent,
        turnNumber: Int,
    ) = memory.observe(event, turnNumber)
}
