package com.kursi.core.network

import com.kursi.engine.Rules
import com.kursi.protocol.wire.WireAction
import com.kursi.protocol.wire.WireIntent
import com.kursi.protocol.wire.WirePhaseView
import com.kursi.protocol.wire.WirePlayerView
import com.kursi.protocol.wire.WireReactionStep
import com.kursi.protocol.wire.WireRole
import com.kursi.protocol.wire.toEngine
import com.kursi.protocol.wire.toWire

/** PATRAKAAR is the sixth role; it only enters the deck on large tables. See GameConfig. */
private const val ROLE_COUNT_WITH_PATRAKAAR = 6

/**
 * Derives the concrete [WireIntent]s the receiving seat may legally submit RIGHT NOW, using ONLY
 * the redacted [WirePlayerView] the server sent it.
 *
 * Why this lives client-side: the server is the sole authority that runs [com.kursi.engine.legalIntents]
 * against the full [com.kursi.engine.GameState], but a client never receives that state (secrecy). For
 * the UI to light up the right buttons it must reconstruct its own legal move-set from the public/own
 * facts in its [WirePlayerView]. This mirrors [com.kursi.engine.legalIntents] phase-for-phase, but never
 * needs another seat's hidden cards — every branch reads only:
 *   - public table facts (coins, eliminated, faceDownCount) from [WirePlayerView.players], and
 *   - the viewer's OWN cards ([WirePlayerView.myCards]) for the CardId-addressed choices.
 *
 * The server still RE-VALIDATES every submitted intent (anti-cheat #3 in MatchActor), so this function
 * being permissive is harmless — at worst the server rejects with an Error. It exists to make the UI
 * correct, not to be the security boundary.
 *
 * Returns an empty list when it is not this seat's turn to act in the current phase.
 */
fun WirePlayerView.legalIntents(): List<WireIntent> {
    val seat = viewer
    // ONE actor check, using the helper that already encodes "is it my turn" per phase. Each branch
    // below used to repeat its own `if (ph.actor != seat) return emptyList()`, which is five copies
    // of isMyTurn() free to drift away from it.
    if (!isMyTurn()) return emptyList()
    return when (val ph = phase) {
        is WirePhaseView.Turn -> legalActions().map { WireIntent.DeclareAction(seat, it) }

        is WirePhaseView.Reactions -> {
            when (ph.step) {
                WireReactionStep.CHALLENGE_ACTION, WireReactionStep.CHALLENGE_BLOCK ->
                    listOf(WireIntent.Challenge(seat), WireIntent.Pass(seat))
                WireReactionStep.BLOCK ->
                    listOf<WireIntent>(WireIntent.Pass(seat)) +
                        rolesThatBlock(ph.action).map { WireIntent.Block(seat, it) }
            }
        }

        is WirePhaseView.InfluenceLoss -> {
            // Address a specific OWN face-down CardId — only knowable from myCards.
            myCards.filter { !it.faceUp }.map { WireIntent.ChooseInfluenceToLose(seat, it.id) }
        }

        is WirePhaseView.Exchange -> {
            val ownFaceDown = myCards.filter { !it.faceUp }.map { it.id }
            val pool = ownFaceDown + ph.drawn.map { it.id }
            val keepSize = ownFaceDown.size
            combinations(pool, keepSize).map { WireIntent.ChooseExchange(seat, it) }
        }

        is WirePhaseView.InvestigatePeek ->
            listOf(
                WireIntent.ResolveInvestigate(seat, forceRedraw = false),
                WireIntent.ResolveInvestigate(seat, forceRedraw = true),
            )

        // Unreachable: isMyTurn() is false in Over, so the guard above already returned.
        is WirePhaseView.Over -> emptyList()
    }
}

/** True when it is currently [WirePlayerView.viewer]'s turn to provide input in the current phase. */
fun WirePlayerView.isMyTurn(): Boolean =
    when (val ph = phase) {
        is WirePhaseView.Turn -> ph.actor == viewer
        is WirePhaseView.Reactions -> ph.toRespond == viewer
        is WirePhaseView.InfluenceLoss -> ph.loser == viewer
        is WirePhaseView.Exchange -> ph.actor == viewer
        is WirePhaseView.InvestigatePeek -> ph.examiner == viewer
        is WirePhaseView.Over -> false
    }

/**
 * The legal turn [WireAction]s for the viewer when it is their [WirePhaseView.Turn] — mirrors
 * [com.kursi.engine.legalActions]. Bluffing is allowed (NOT role-gated), so no own-card check is needed.
 */
private fun WirePlayerView.legalActions(): List<WireAction> {
    val seat = viewer
    val cfg = config
    val targets = players.filter { it.id != seat && !it.eliminated }.map { it.id }
    val out = ArrayList<WireAction>()

    // I5: forced Khela (coup) at the threshold — it is the ONLY legal action.
    if (myCoins >= cfg.forcedCoupThreshold) {
        for (t in targets) out.add(WireAction.Coup(t))
        return out
    }
    out.add(WireAction.Income)
    out.add(WireAction.ForeignAid)
    if (myCoins >= cfg.coupCost) for (t in targets) out.add(WireAction.Coup(t))
    out.add(WireAction.Tax)
    // D9: Vasooli (Steal) on a 0-coin target is illegal.
    for (t in targets) if (players.first { it.id == t }.coins >= 1) out.add(WireAction.Steal(t))
    if (myCoins >= cfg.assassinateCost) for (t in targets) out.add(WireAction.Assassinate(t))
    out.add(WireAction.Exchange)
    // Jaanch (claims PATRAKAAR) — only when the 6th role is in this deck (roleCount == 6) and the target
    // has a face-down card to examine. Mirrors the engine's `Role.PATRAKAAR in cfg.activeRoles` gate.
    if (cfg.roleCount >= ROLE_COUNT_WITH_PATRAKAAR) {
        for (t in targets) if (players.first { it.id == t }.faceDownCount > 0) out.add(WireAction.Investigate(t))
    }
    return out
}

/** Roles that may block [action], expressed in wire form (delegates to the engine rule table). */
private fun rolesThatBlock(action: WireAction): List<WireRole> = Rules.rolesThatBlock(action.toEngine()).map { it.toWire() }

/**
 * All [k]-element subsets of [items], preserving input order within each subset.
 * Mirrors the engine's `combinations` used for [com.kursi.engine.Intent.ChooseExchange] enumeration.
 */
private fun <T> combinations(
    items: List<T>,
    k: Int,
): List<List<T>> {
    if (k < 0 || k > items.size) return emptyList()
    if (k == 0) return listOf(emptyList())
    if (k == items.size) return listOf(items.toList())
    val result = ArrayList<List<T>>()

    fun recurse(
        start: Int,
        current: MutableList<T>,
    ) {
        if (current.size == k) {
            result.add(current.toList())
            return
        }
        for (i in start until items.size) {
            current.add(items[i])
            recurse(i + 1, current)
            current.removeAt(current.size - 1)
        }
    }
    recurse(0, ArrayList())
    return result
}
