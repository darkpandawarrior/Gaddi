package com.kursi.ai.advisor

import com.kursi.ai.*
import com.kursi.engine.*
import com.siddharth.kmp.botspolicy.SearchBudget
import kotlinx.coroutines.CancellationException

/**
 * MoveAdvisor — pure, UI-free "AI brain" shared by the decision-coach, best-move highlight,
 * AI assistant, and auto-mode.
 *
 * # How to use (from the UI layer, never imported here)
 *
 * ```
 * // Shared instance (one per game session, keeps belief memory)
 * val advisor = MoveAdvisor(seed = gameSeed)
 *
 * // Feed every game event so belief stays fresh
 * advisor.observe(event, turnNumber)
 *
 * // On the human's turn:
 * val view    = redact(state, humanId)
 * val legal   = legalIntents(state, humanId)
 * val advices = advisor.advise(state, humanId, legal)   // ranked list
 * val best    = advisor.bestMove(state, humanId, legal) // for auto-mode
 * ```
 *
 * # Determinism
 * Given identical (state, humanId, legal, seed, budget, calls to [observe]) the output is
 * deterministic. No Compose, no I/O, no global state.
 */
class MoveAdvisor(
    seed: Long,
    val adviceBudget: SearchBudget = ADVICE_BUDGET,
) {
    private var rng = Rng(seed)
    private val beliefModel = BeliefModel()
    val memory = BotMemory()
    private val determinizer = Determinizer(beliefModel)
    private val search = IsmctsSearch(determinizer, adviceBudget, rolloutSeed = seed + 31337L)

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Feed a game event into the belief model. Call after every [applyIntent] so the
     * advisor's understanding of opponents' hands stays current.
     */
    fun observe(
        event: GameEvent,
        turnNumber: Int,
    ) = memory.observe(event, turnNumber)

    /**
     * Returns the single best [Intent] for [humanId] at this game state.
     * Intended for auto-mode: plays immediately without showing the ranked list.
     *
     * `suspend`: see [advise] — cancelling the caller stops the search mid-flight.
     */
    suspend fun bestMove(
        state: GameState,
        humanId: PlayerId,
        legal: List<Intent>,
    ): Intent {
        require(legal.isNotEmpty()) { "no legal intents" }
        if (legal.size == 1) return legal.single()
        return advise(state, humanId, legal).firstOrNull { it.recommended }?.intent ?: legal.first()
    }

    /**
     * Returns one [MoveAdvice] per element of [legal], ranked best-first.
     * Exactly one entry has [MoveAdvice.recommended] == true (the top pick).
     *
     * Works for both the action phase (DeclareAction intents) and the reaction phase
     * (Challenge / Block / Pass intents).
     *
     * `suspend`: the search underneath ([IsmctsSearch.evaluate]) polls for cancellation between
     * iterations, so a caller that cancels its coroutine when the human moves on (a new decision
     * arrives, the screen leaves composition) frees the CPU immediately instead of after the
     * whole ~200-400ms search completes.
     */
    suspend fun advise(
        state: GameState,
        humanId: PlayerId,
        legal: List<Intent>,
    ): List<MoveAdvice> {
        require(legal.isNotEmpty()) { "no legal intents" }
        val view = redact(state, humanId)

        // Run search — get per-move value estimates
        val (moveValues, r1) = runEvaluate(view, legal)
        rng = r1

        // Build advice entries (un-ranked first, in same order as legal)
        val rawAdvices: List<MoveAdvice> =
            legal.mapIndexed { idx, intent ->
                val mv = moveValues.getOrNull(idx) ?: MoveValue(intent, 0.5, 0.0)
                buildAdvice(intent, mv, view, state, humanId, recommended = false)
            }

        // Rank: primary = winProb desc, secondary = visitShare desc (most-visited tie-break)
        val sorted =
            rawAdvices.sortedWith(
                compareByDescending<MoveAdvice> { it.winProb }
                    .thenByDescending { moveValues.getOrNull(legal.indexOf(it.intent))?.visitShare ?: 0.0 },
            )

        // Mark exactly one as recommended (the top)
        return sorted.mapIndexed { idx, advice -> advice.copy(recommended = idx == 0) }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Calls [IsmctsSearch.evaluate] and returns the move values plus the advanced rng.
     * Isolated here so the rng advancement is always paired with the search call.
     *
     * Catches [Exception], never [kotlinx.coroutines.CancellationException]: a bare `catch
     * (Throwable)` here would swallow the human-moved-on cancellation this whole lane exists to
     * deliver, silently finishing the search anyway (and returning a stale fallback advice the
     * ViewModel would then have to discard). Cancellation must propagate; a real search failure
     * still degrades to the uniform-0.5 fallback exactly as before.
     */
    private suspend fun runEvaluate(
        view: PlayerView,
        legal: List<Intent>,
    ): Pair<List<MoveValue>, Rng> {
        val values =
            try {
                search.evaluate(view, legal, memory, rng, adviceBudget)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                // Fallback: uniform 0.5 for all moves
                legal.map { MoveValue(it, 0.5, 1.0 / legal.size) }
            }
        val (_, r1) = rng.nextLong()
        return values to r1
    }

    /**
     * Computes truthfulness, bluff flag, successOdds, and rationale for a single [intent].
     */
    private fun buildAdvice(
        intent: Intent,
        mv: MoveValue,
        view: PlayerView,
        state: GameState,
        humanId: PlayerId,
        recommended: Boolean,
    ): MoveAdvice {
        val label = labelFor(intent, view)

        val claimedRole: Role? = claimedRoleFor(intent)

        // truthful = human actually holds the claimed role; null if no claim
        val truthful: Boolean? =
            claimedRole?.let { role ->
                view.myInfluence.contains(role)
            }

        val bluff: Boolean = truthful == false // null → false, true → false, false → true

        val successOdds: Double? = computeSuccessOdds(intent, view, state, humanId, bluff, claimedRole)

        val rationale = rationaleFor(intent, view, truthful, bluff, claimedRole, successOdds, mv.winProb)

        return MoveAdvice(
            intent = intent,
            label = label,
            winProb = mv.winProb,
            recommended = recommended,
            truthful = truthful,
            bluff = bluff,
            successOdds = successOdds,
            rationale = rationale,
        )
    }

    // ── Claim role resolution ─────────────────────────────────────────────────

    /**
     * Returns the role this [intent] claims, if any.
     *
     * - [Intent.DeclareAction] of a role-action (Tax/Assassinate/Steal/Exchange) → the action's claimed role
     * - [Intent.Block] → the blocking role
     * - Everything else (Income, ForeignAid, Coup, Challenge, Pass, ChooseInfluenceToLose, ChooseExchange) → null
     */
    private fun claimedRoleFor(intent: Intent): Role? =
        when (intent) {
            is Intent.DeclareAction -> Rules.claimedRole(intent.action)
            is Intent.Block -> intent.role
            else -> null
        }

    // ── successOdds ───────────────────────────────────────────────────────────

    /**
     * Computes contextual odds:
     *   - Challenge → P(opponent is bluffing) via [BluffOdds]
     *   - Bluff action/block → rough P(not-challenged-and-caught) ≈ 1 − pBluff (opponent would
     *     challenge only if they think WE are bluffing; we use the symmetric BluffOdds for the
     *     human's own bluff from the opponent's perspective)
     *   - Truthful action/block / Pass / Coup / Income / ForeignAid → null
     */
    private fun computeSuccessOdds(
        intent: Intent,
        view: PlayerView,
        state: GameState,
        humanId: PlayerId,
        bluff: Boolean,
        claimedRole: Role?,
    ): Double? {
        val cfg = view.config

        // For Challenge: estimate P(claimer is bluffing)
        if (intent is Intent.Challenge) {
            val phase = view.phase
            val (actorId, roleBeingClaimed) =
                when (phase) {
                    is PhaseView.Reactions ->
                        when (phase.step) {
                            ReactionStep.CHALLENGE_ACTION -> phase.actor to phase.claimedRole
                            ReactionStep.CHALLENGE_BLOCK -> phase.blocker to phase.blockRole
                            else -> return null
                        }
                    else -> return null
                }
            if (actorId == null || roleBeingClaimed == null) return null

            val actorOppView = view.players.firstOrNull { it.id == actorId } ?: return null
            val eliminated = view.players.sumOf { it.faceUpRoles.count { r -> r == roleBeingClaimed } }
            val myHand = view.myInfluence.count { it == roleBeingClaimed }
            val totalVisible = view.players.sumOf { it.faceUpRoles.size } + view.myFaceUp.size

            val confidence =
                BluffOdds.estimate(
                    claimedRole = roleBeingClaimed,
                    copiesPerRole = cfg.copiesPerRole,
                    deckSize = cfg.deckSize,
                    eliminatedRolesForClaimedRole = eliminated,
                    myHandContainsClaimedRole = myHand,
                    opponentFaceDownCount = actorOppView.faceDownCount,
                    totalVisibleCards = totalVisible,
                )
            // pips 1..5 mapped linearly to P(bluff) ≈ (pips-1)/4
            // More precisely, reconstruct from BluffOdds thresholds:
            // pips=1 → pBluff<0.20, pips=2 → 0.20-0.38, etc.
            // We return the midpoint of each bucket.
            return when (confidence.pips) {
                1 -> 0.10
                2 -> 0.29
                3 -> 0.46
                4 -> 0.63
                else -> 0.86
            }
        }

        // For bluff action or bluff block: rough P(safe — not caught)
        // = 1 − P(someone challenges) which we approximate from BluffOdds on the human's bluff
        if (bluff && claimedRole != null) {
            val eliminated = view.players.sumOf { it.faceUpRoles.count { r -> r == claimedRole } }
            // myHandContainsClaimedRole = 0 (we're bluffing, so we don't hold it)
            val myHand = 0
            val totalVisible = view.players.sumOf { it.faceUpRoles.size } + view.myFaceUp.size
            val myInfluenceCount = view.myInfluence.size.coerceAtLeast(1)

            val confidence =
                BluffOdds.estimate(
                    claimedRole = claimedRole,
                    copiesPerRole = cfg.copiesPerRole,
                    deckSize = cfg.deckSize,
                    eliminatedRolesForClaimedRole = eliminated,
                    myHandContainsClaimedRole = myHand,
                    opponentFaceDownCount = myInfluenceCount, // k = my influence count (from opponents' PoV)
                    totalVisibleCards = totalVisible,
                )
            // P(opponents think I'm bluffing) ≈ midpoint of bucket
            val pBluffFromOppPov =
                when (confidence.pips) {
                    1 -> 0.10
                    2 -> 0.29
                    3 -> 0.46
                    4 -> 0.63
                    else -> 0.86
                }
            // P(not challenged) ≈ 1 − pBluffFromOppPov (crude but calibrated)
            return (1.0 - pBluffFromOppPov).coerceIn(0.0, 1.0)
        }

        return null
    }

    // ── Labels ────────────────────────────────────────────────────────────────

    private fun labelFor(
        intent: Intent,
        view: PlayerView,
    ): String =
        when (intent) {
            is Intent.DeclareAction ->
                when (val a = intent.action) {
                    Action.Income -> "Income (+1)"
                    Action.ForeignAid -> "Foreign Aid (+2)"
                    Action.Tax -> "Tax (NETA, +3)"
                    Action.Exchange -> "Exchange (JUGAADU)"
                    is Action.Coup -> "Coup → ${playerName(a.target, view)}"
                    is Action.Assassinate -> "Assassinate → ${playerName(a.target, view)}"
                    is Action.Steal -> "Steal → ${playerName(a.target, view)}"
                    is Action.Investigate -> "Investigate → ${playerName(a.target, view)} (PATRAKAAR)"
                    Action.BailPe -> "Bail Pe Bahar (restore influence)"
                    Action.Sabotage -> "Bali Khel (sacrifice → coins)"
                    is Action.Hawala -> "Hawala → ${playerName(a.to, view)}"
                    Action.Emergency -> "ADHYADESH (mass-Coup)"
                }
            is Intent.Block -> "Block as ${intent.role.name}"
            is Intent.Challenge -> "Challenge"
            is Intent.Pass -> "Pass"
            is Intent.ChooseInfluenceToLose -> "Lose influence"
            is Intent.ChooseExchange -> "Keep these cards"
            is Intent.ResolveInvestigate -> if (intent.forceRedraw) "Force redraw" else "Leave card"
        }

    private fun playerName(
        id: PlayerId,
        view: PlayerView,
    ): String = view.players.firstOrNull { it.id == id }?.let { "Seat ${it.seatIndex}" } ?: "P${id.raw}"

    // ── Rationale ─────────────────────────────────────────────────────────────

    private fun rationaleFor(
        intent: Intent,
        view: PlayerView,
        truthful: Boolean?,
        bluff: Boolean,
        claimedRole: Role?,
        successOdds: Double?,
        winProb: Double,
    ): String {
        // Challenge: odds-first rationale
        if (intent is Intent.Challenge) {
            return if (successOdds != null) {
                val pct = (successOdds * 100).toInt()
                if (pct >= 50) {
                    "~$pct% chance they're bluffing — challenge is favourable."
                } else {
                    "~$pct% chance they're bluffing — challenge is a long shot."
                }
            } else {
                "Challenge to test their claim."
            }
        }

        // Pass
        if (intent is Intent.Pass) return "Pass and let it proceed."

        // ChooseInfluenceLoss / ChooseExchange
        if (intent is Intent.ChooseInfluenceToLose) return "Reveal this card as your lost influence."
        if (intent is Intent.ChooseExchange) return "Keep these cards after the exchange."

        // Action / Block with a role claim
        if (claimedRole != null) {
            return when {
                truthful == true -> {
                    "You hold $claimedRole — this claim is real and safe to make."
                }
                bluff -> {
                    val riskStr =
                        if (successOdds != null) {
                            val safePct = (successOdds * 100).toInt()
                            " (~$safePct% chance it won't be challenged)"
                        } else {
                            ""
                        }
                    "Bluff: you don't hold $claimedRole; risky if challenged$riskStr."
                }
                else -> "Claimed $claimedRole — assess the risk before proceeding."
            }
        }

        // No-claim actions
        return when (val a = (intent as? Intent.DeclareAction)?.action) {
            Action.Income -> "Guaranteed +1 coin, no risk."
            Action.ForeignAid -> "+2 coins; opponents holding NETA may block."
            is Action.Coup -> "Pay 7 coins to eliminate a player — unblockable."
            else -> "Win probability estimate: ${(winProb * 100).toInt()}%."
        }
    }
}
