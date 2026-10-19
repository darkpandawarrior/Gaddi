package com.kursi.feature.game.coach

import com.kursi.ai.BluffOdds
import com.kursi.engine.*
import com.kursi.feature.game.*
import com.kursi.feature.game.overlays.*

internal fun isValidTarget(
    opp: OpponentView,
    action: Action,
    state: GameUiState,
): Boolean {
    if (opp.eliminated) return false
    return state.legalIntents.filterIsInstance<Intent.DeclareAction>().any { intent ->
        Rules.targetOf(intent.action) == opp.id && actionsSameType(intent.action, action)
    }
}

internal fun actionsSameType(
    a: Action,
    b: Action,
): Boolean =
    when {
        a is Action.Coup && b is Action.Coup -> true
        a is Action.Assassinate && b is Action.Assassinate -> true
        a is Action.Steal && b is Action.Steal -> true
        else -> false
    }

/**
 * The recommended target for [action] during target-select — the seat the player should aim at,
 * surfaced explicitly so they're not left to read per-plate suspicion pips on their own.
 *
 * Resolution (PUBLIC-info only; never touches a hidden card):
 *  1. The DECISION-COACH's own pick — among the advisor's [Intent.DeclareAction] entries of this
 *     action TYPE, the recommended one (else highest winProb). This is the AI brain's read, already
 *     computed for the human, so it stays inside the secrecy boundary.
 *  2. Fallback when advice hasn't arrived: the weakest valid target by public state — fewest
 *     face-down cards (closest to elimination), tie-broken by lowest coins, then seat order.
 *
 * Returns the target [PlayerId] and a short reason ("coach's pick" / "weakest seat"), or null when
 * no valid target exists (the dock then just shows the tap hint).
 */
internal fun recommendedTarget(
    action: Action,
    state: GameUiState,
): Pair<PlayerId, String>? {
    // 1) Coach's own pick for this action type.
    val coachPick =
        state.advice
            .filter { it.intent is Intent.DeclareAction }
            .filter { actionsSameType((it.intent as Intent.DeclareAction).action, action) }
            .let { matches -> matches.firstOrNull { it.recommended } ?: matches.maxByOrNull { it.winProb } }
            ?.let { (it.intent as Intent.DeclareAction).action }
            ?.let { Rules.targetOf(it) }
    if (coachPick != null) return coachPick to "coach's pick"

    // 2) Weakest valid target by public state — fewest face-down, then fewest coins.
    val weakest =
        state.view.players
            .filter { isValidTarget(it, action, state) }
            .minWithOrNull(
                compareBy<OpponentView> { it.faceDownCount }
                    .thenBy { it.coins }
                    .thenBy { it.seatIndex },
            )
            ?: return null
    return weakest.id to "weakest seat"
}

// ─────────────────────────── Opponent claim summary (Phase TILES) ────────────

/**
 * A standing read of an opponent's role-claims, derived purely from the PUBLIC event
 * history ([GameUiState.recentEvents]). Unlike the transient pending-claim, this persists
 * as events accrue so the plate keeps showing "claimed NETA ×2" etc. across the game
 * instead of collapsing to "— no claim —" on the human's turn.
 */
internal data class OpponentClaimSummary(
    /** The most-recent role this seat claimed (their standing claim), or null if never. */
    val standingRole: Role?,
    /** Short trail label for the plate, e.g. "claimed NETA ×2" / "last: BABU", or null. */
    val trail: String?,
    /** True if any of this seat's role-claims was caught bluffing (revealed without the role). */
    val caught: Boolean,
    /** True if any of this seat's role-claims was proven on a challenge (revealed with the role). */
    val proven: Boolean,
)

/**
 * Scan the public event log for [opp]'s role-claims and summarise them. Pure / deterministic.
 *
 * - `ActionDeclared.claimedRole` is the source of role-claims (Tax→NETA, Vasooli→BABU, …).
 * - `ChallengeRevealed{player=opp, hadRole}` tells us whether a challenged claim held up.
 *   hadRole=false ⇒ a bluff was caught; hadRole=true ⇒ the claim was proven.
 */
internal fun deriveClaimSummary(
    opp: OpponentView,
    events: List<GameEvent>,
): OpponentClaimSummary {
    val myClaims: List<Role> =
        events
            .filterIsInstance<GameEvent.ActionDeclared>()
            .filter { it.actor == opp.id }
            .mapNotNull { it.claimedRole }

    val standingRole = myClaims.lastOrNull()

    val reveals =
        events
            .filterIsInstance<GameEvent.ChallengeRevealed>()
            .filter { it.player == opp.id }
    val caught = reveals.any { !it.hadRole }
    val proven = reveals.any { it.hadRole }

    val trail: String? =
        when {
            standingRole == null -> null
            else -> {
                // Count how many times the standing role was claimed (×N if repeated).
                val n = myClaims.count { it == standingRole }
                val roleName = roleLabel(standingRole)
                if (n >= 2) "claimed $roleName ×$n" else "claimed $roleName"
            }
        }
    return OpponentClaimSummary(standingRole, trail, caught, proven)
}

/**
 * Suspicion / bluff-odds read on [opp]'s STANDING claim, from public state only. Returns
 * (pips, label) for the plate's compact chip, or null when there is no standing claim.
 * Reuses [BluffOdds.estimate] — the same pure estimator the reaction HintRail uses.
 */
internal fun deriveSuspicion(
    opp: OpponentView,
    standingRole: Role?,
    view: PlayerView,
    /**
     * This seat's INFERRED bluffRate (0..1) from the decision-coach's public-info belief, or null
     * if no read yet. When present it nudges the deck-math pips: a known serial-bluffer's claim
     * reads hotter, a straight-shooter's reads cooler — so the suspicion chip blends deck odds with
     * the opponent's observed STYLE rather than the cards alone.
     */
    bluffRate: Double? = null,
): Pair<Int, String>? {
    if (standingRole == null || opp.eliminated) return null
    val cfg = view.config
    val allFaceUp = view.players.flatMap { it.faceUpRoles } + view.myFaceUp
    val eliminatedForRole = allFaceUp.count { it == standingRole }
    val myHandHasRole = view.myInfluence.count { it == standingRole }
    val conf =
        BluffOdds.estimate(
            claimedRole = standingRole,
            copiesPerRole = cfg.copiesPerRole,
            deckSize = cfg.deckSize,
            eliminatedRolesForClaimedRole = eliminatedForRole,
            myHandContainsClaimedRole = myHandHasRole,
            opponentFaceDownCount = opp.faceDownCount,
            totalVisibleCards = allFaceUp.size,
        )
    if (bluffRate == null) return conf.pips to conf.label
    // Style nudge: bluffRate above the 0.20 baseline pushes pips up, below it pulls down. Capped at
    // ±1 pip so the deck math still leads and the read stays legible.
    val nudge =
        when {
            bluffRate >= 0.55 -> 1
            bluffRate <= 0.12 -> -1
            else -> 0
        }
    val pips = (conf.pips + nudge).coerceIn(1, 5)
    // Re-label off the blended pips, and flag when STYLE (not cards) is driving the read.
    val baseLabel =
        when (pips) {
            1 -> "likely honest"
            2 -> "probably real"
            3 -> "coin-flip"
            4 -> "probably bluffing"
            else -> "long shot"
        }
    val label =
        if (nudge > 0) {
            "$baseLabel · shady"
        } else if (nudge < 0) {
            "$baseLabel · steady"
        } else {
            baseLabel
        }
    return pips to label
}

/**
 * Bluff-odds read for the HUMAN's own [action] claim — i.e. "if I declare this and get
 * challenged, how exposed am I?" Returns null for unclaimed (safe) actions. Reuses the
 * same pure [BluffOdds.estimate] estimator the reaction HintRail and plates use, but from
 * the actor's own seat: it folds in whether my hand actually holds the claimed role.
 */
fun riskBluffConf(
    action: Action,
    state: GameUiState,
): BluffOdds.Confidence? {
    val role = Rules.claimedRole(action) ?: return null
    val cfg = state.view.config
    val allFaceUp = state.view.players.flatMap { it.faceUpRoles } + state.view.myFaceUp
    val eliminatedForRole = allFaceUp.count { it == role }
    val myHandHasRole = state.view.myInfluence.count { it == role }
    return BluffOdds.estimate(
        claimedRole = role,
        copiesPerRole = cfg.copiesPerRole,
        deckSize = cfg.deckSize,
        eliminatedRolesForClaimedRole = eliminatedForRole,
        myHandContainsClaimedRole = myHandHasRole,
        opponentFaceDownCount = 1,
        totalVisibleCards = allFaceUp.size,
    )
}

// ─────────────────────────── Decision-coach lookup ───────────────────────────

/** The [MoveAdvice] for [intent] from the coach, or null if none / advice not yet computed. */
internal fun adviceFor(
    state: GameUiState,
    intent: Intent,
): com.kursi.ai.advisor.MoveAdvice? = state.advice.firstOrNull { it.intent == intent }

/**
 * Coach advice for an ACTION chip. Non-target actions (Income/ForeignAid/Tax/Exchange) match
 * exactly. Target actions (Coup/Assassinate/Steal) have a concrete target inside each advice
 * entry but the chip hasn't picked one yet, so we match by action TYPE and surface the
 * recommended-or-best entry of that type — the truthful/bluff verdict is target-independent
 * (it's about the claimed role) and the odds read the same across targets.
 */
internal fun adviceForActionChip(
    state: GameUiState,
    action: Action,
): com.kursi.ai.advisor.MoveAdvice? {
    val declares = state.advice.filter { it.intent is Intent.DeclareAction }

    fun typeMatches(
        a: Action,
        b: Action,
    ): Boolean =
        when {
            a is Action.Coup && b is Action.Coup -> true
            a is Action.Assassinate && b is Action.Assassinate -> true
            a is Action.Steal && b is Action.Steal -> true
            else -> a == b
        }
    val matches = declares.filter { typeMatches((it.intent as Intent.DeclareAction).action, action) }
    return matches.firstOrNull { it.recommended } ?: matches.maxByOrNull { it.winProb }
}

/** Build the long-press DECISION-COACH chit from a [MoveAdvice], with an optional belief read. */
internal fun coachChitOf(
    advice: com.kursi.ai.advisor.MoveAdvice,
    beliefLine: String? = null,
): ChitContent.Coach =
    ChitContent.Coach(
        moveLabel = advice.label,
        truthful = advice.truthful,
        bluff = advice.bluff,
        successOdds = advice.successOdds,
        winProb = advice.winProb,
        recommended = advice.recommended,
        rationale = advice.rationale,
        beliefLine = beliefLine,
    )

/** Copies of [role] visible to the human: eliminated face-up plus held in their own hand. */
private fun accountedCopies(
    state: GameUiState,
    role: Role,
): Int {
    val allFaceUp = state.view.players.flatMap { it.faceUpRoles } + state.view.myFaceUp
    return allFaceUp.count { it == role } + state.view.myInfluence.count { it == role }
}

/**
 * Typed twin of [challengeBeliefLine]: true when a challenge is favourable (every copy accounted
 * for, or only one unseen with some already visible). Callers must not parse the localized line.
 */
internal fun challengeIsFavourable(
    state: GameUiState,
    claimedRole: Role,
): Boolean {
    val accountedFor = accountedCopies(state, claimedRole)
    val unseen = (state.view.config.copiesPerRole - accountedFor).coerceAtLeast(0)
    return unseen == 0 || (unseen == 1 && accountedFor > 0)
}

/**
 * The belief-grounded "is this claim a bluff?" line the coach leads with when the human is deciding
 * whether to CHALLENGE — built purely from public card-accounting (copies of [claimedRole] minus
 * those eliminated face-up and those in the human's own hand). When every copy is accounted for, the
 * claim is provably a bluff; otherwise it reports how many remain unseen. PUBLIC-info only.
 */
internal fun challengeBeliefLine(
    state: GameUiState,
    claimedRole: Role,
    claimLabel: String,
): String {
    val copies = state.view.config.copiesPerRole
    val accountedFor = accountedCopies(state, claimedRole)
    val unseen = (copies - accountedFor).coerceAtLeast(0)
    val roleName = roleLabel(claimedRole)
    return when {
        unseen == 0 ->
            "All $copies $roleName are accounted for — this $claimLabel is a bluff; challenge is favourable."
        unseen == 1 && accountedFor > 0 ->
            "Only 1 $roleName left unseen ($accountedFor already visible) — this $claimLabel is shaky; a challenge has real odds."
        else ->
            "$unseen of $copies $roleName still unseen — they could well hold $roleName; challenge is a gamble."
    }
}
