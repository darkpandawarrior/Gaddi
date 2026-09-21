package com.kursi.ai

import com.kursi.engine.*

class Determinizer(
    private val beliefModel: BeliefModel,
) {
    fun sample(
        view: PlayerView,
        memory: BotMemory,
        rng: Rng,
    ): Pair<GameState, Rng> {
        val cfg = view.config
        var r = rng

        // --- Build the unseen pool (roles not visible to viewer) ---
        // Iterate activeRoles (NOT Role.entries): PATRAKAAR is only in the deck on big tables, so
        // small tables must not seed it into the unseen pool.
        val unseenPool = mutableListOf<Role>()
        for (role in cfg.activeRoles) repeat(cfg.copiesPerRole) { unseenPool.add(role) }
        // Remove viewer's own cards
        for (role in view.myInfluence) unseenPool.remove(role)
        for (role in view.myFaceUp) unseenPool.remove(role)
        // Remove all opponents' face-up cards
        for (opp in view.players) {
            if (opp.id == view.viewer) continue
            for (role in opp.faceUpRoles) unseenPool.remove(role)
        }
        // If we are reconstructing an Exchange that WE are running, the drawn cards are known to us
        // (PhaseView.Exchange.drawn is populated for the actor's own view). They were already pulled out
        // of the deck, so remove their roles from the unseen pool — they must not also reappear in the
        // deck remainder. Their actual CardIds are assigned below with an ExchangeHeld location so that
        // ISMCTS can enumerate and value the keep-set just like the real engine.
        val exchangeDrawnRoles: List<Role> =
            (view.phase as? PhaseView.Exchange)
                ?.takeIf { it.actor == view.viewer }
                ?.drawn
                ?.map { it.role }
                ?: emptyList()
        for (role in exchangeDrawnRoles) unseenPool.remove(role)

        // --- Assign face-down cards to each opponent (belief-weighted) ---
        val assignments = mutableMapOf<PlayerId, MutableList<Role>>()
        val aliveOpps = view.players.filter { !it.eliminated && it.id != view.viewer }
        for (opp in aliveOpps) {
            val belief = memory.beliefs[opp.id] ?: OpponentBelief()
            val post = beliefModel.posterior(view, belief)
            val assigned = mutableListOf<Role>()
            repeat(opp.faceDownCount) {
                if (unseenPool.isEmpty()) return@repeat
                val (picked, r1) = weightedDraw(unseenPool, post, r)
                r = r1
                unseenPool.remove(picked)
                assigned.add(picked)
            }
            assignments[opp.id] = assigned
        }

        // --- Build cards + locations maps ---
        // CardId assignment: 0..deckSize-1
        // Order: viewer face-down, viewer face-up, each opp (face-up then face-down), deck remainder
        val cards = linkedMapOf<CardId, Role>()
        val locations = linkedMapOf<CardId, CardLocation>()
        var cid = 0

        // Viewer face-down
        for (role in view.myInfluence) {
            cards[CardId(cid)] = role
            locations[CardId(cid)] = CardLocation.Hand(view.viewer, faceUp = false)
            cid++
        }
        // Viewer face-up
        for (role in view.myFaceUp) {
            cards[CardId(cid)] = role
            locations[CardId(cid)] = CardLocation.Hand(view.viewer, faceUp = true)
            cid++
        }
        // Opponents (all, including eliminated — they have faceUpRoles and faceDownCount==0)
        for (opp in view.players) {
            if (opp.id == view.viewer) continue
            // Face-up
            for (role in opp.faceUpRoles) {
                cards[CardId(cid)] = role
                locations[CardId(cid)] = CardLocation.Hand(opp.id, faceUp = true)
                cid++
            }
            // Face-down (sampled)
            for (role in (assignments[opp.id] ?: emptyList())) {
                cards[CardId(cid)] = role
                locations[CardId(cid)] = CardLocation.Hand(opp.id, faceUp = false)
                cid++
            }
        }
        // Exchange-held: the viewer's drawn cards (known roles) get concrete CardIds so the
        // reconstructed AwaitingExchange.drawn matches what legalIntents/chooseExchange expect.
        val exchangeDrawnIds = ArrayList<CardId>(exchangeDrawnRoles.size)
        for (role in exchangeDrawnRoles) {
            cards[CardId(cid)] = role
            locations[CardId(cid)] = CardLocation.ExchangeHeld(view.viewer)
            exchangeDrawnIds.add(CardId(cid))
            cid++
        }

        // Deck: shuffle remaining pool
        var poolLeft = unseenPool.toList()
        while (poolLeft.isNotEmpty()) {
            val (drawn, remaining, r1) = r.draw(poolLeft)
            r = r1
            poolLeft = remaining
            cards[CardId(cid)] = drawn
            locations[CardId(cid)] = CardLocation.Deck
            cid++
        }

        // Safety: if we're short (shouldn't happen with correct accounting) pad with NETA to deck
        // This guards against eliminated player edge cases
        while (cid < cfg.deckSize) {
            cards[CardId(cid)] = Role.NETA
            locations[CardId(cid)] = CardLocation.Deck
            cid++
        }

        // --- Build PlayerState list ---
        val players =
            view.players
                .map { opp ->
                    val coins = if (opp.id == view.viewer) view.myCoins else opp.coins
                    PlayerState(opp.id, opp.seatIndex, coins)
                }.sortedBy { it.seatIndex }

        // --- Reconstruct phase ---
        val phase = reconstructPhase(view, players, exchangeDrawnIds, cards, locations)

        val state =
            GameState(
                config = cfg,
                cards = cards,
                locations = locations,
                players = players,
                treasury = view.treasury,
                phase = phase,
                rng = r.state,
                turnNumber = view.turnNumber,
            )

        return state to r
    }

    /**
     * Draw one role from the pool, weighted by the posterior distribution.
     * Falls back to uniform if weights sum to zero.
     */
    private fun weightedDraw(
        pool: List<Role>,
        posterior: Map<Role, Double>,
        rng: Rng,
    ): Pair<Role, Rng> {
        val weights = pool.map { posterior[it] ?: (1.0 / Role.entries.size) }
        val totalW = weights.sum()
        val (randLong, r1) = rng.nextLong()
        // Map randLong to [0, totalW) using unsigned shift to get a non-negative value
        val u = ((randLong ushr 11).toDouble() / (1L shl 52).toDouble()) * totalW
        var cumSum = 0.0
        for (i in weights.indices) {
            cumSum += weights[i]
            if (cumSum > u || i == weights.size - 1) return pool[i] to r1
        }
        return pool.last() to r1
    }

    private fun reconstructPhase(
        view: PlayerView,
        players: List<PlayerState>,
        exchangeDrawnIds: List<CardId>,
        cards: Map<CardId, Role>,
        locations: Map<CardId, CardLocation>,
    ): Phase =
        when (val ph = view.phase) {
            is PhaseView.Turn -> Phase.AwaitingAction(players.first { it.id == ph.actor }.seatIndex)
            is PhaseView.Reactions -> {
                val pending = PendingAction(ph.actor, ph.action, ph.claimedRole)
                val blocker = ph.blocker
                val blockRole = ph.blockRole
                val block =
                    if (blocker != null && blockRole != null) {
                        BlockClaim(blocker, blockRole)
                    } else {
                        null
                    }
                val toRespond = ph.toRespond
                val eligible: List<PlayerId> = if (toRespond != null) listOf(toRespond) else emptyList()
                Phase.AwaitingReactions(ReactionCtx(pending, ph.step, eligible, emptyMap(), block))
            }
            is PhaseView.InfluenceLoss ->
                Phase.AwaitingInfluenceLoss(ph.loser, ph.reason, AfterLoss.EndTurn(ph.loser))
            is PhaseView.Exchange ->
                // Populate drawn from the sampled deck pool (the viewer's known drawn CardIds) so ISMCTS
                // can enumerate keep-sets and value the Exchange. Empty when the viewer isn't the actor
                // (secrecy: we don't know another actor's drawn cards) — matches the engine's own redaction.
                Phase.AwaitingExchange(ph.actor, if (ph.actor == view.viewer) exchangeDrawnIds else emptyList())
            is PhaseView.InvestigatePeek -> {
                // The examiner is choosing whether to force a redraw. Bind the engine's `peeked` CardId to
                // one of the TARGET's sampled face-down cards. If the examiner privately knows the peeked
                // role (examinedCard != null — only the examiner ever does), prefer a face-down card with
                // that exact role so the reconstructed peek matches reality; else fall back to any of the
                // target's face-down cards.
                val targetFaceDown =
                    locations.entries
                        .filter {
                            val l = it.value
                            l is CardLocation.Hand && l.player == ph.target && !l.faceUp
                        }.map { it.key }
                val peekedRole = ph.examinedCard?.role
                val peeked = (
                    peekedRole?.let { role -> targetFaceDown.firstOrNull { cards[it] == role } }
                        ?: targetFaceDown.firstOrNull()
                )
                if (peeked != null) {
                    Phase.AwaitingInvestigatePeek(ph.examiner, ph.target, peeked)
                } else {
                    // Degenerate (target somehow has no face-down card): treat as a no-op turn end.
                    Phase.AwaitingAction(players.first { it.id == ph.examiner }.seatIndex)
                }
            }
            is PhaseView.Over -> Phase.GameOver(ph.winner)
        }
}
