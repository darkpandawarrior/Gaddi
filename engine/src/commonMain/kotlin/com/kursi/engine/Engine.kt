package com.kursi.engine

/**
 * ADHYADESH (Emergency) also demands a meaningful payment on top of the lifetime-coin threshold —
 * without it a seat that had once been rich but is now broke could still fire it.
 */
private const val EMERGENCY_MIN_COINS = 7

/**
 * Darja (rank) milestones: lifetime-coin threshold to the level it awards, ascending.
 *
 * Public because the UI has to agree with the engine about what level a coin count is worth, and
 * it did not: :feature:game's GameUiState.darjaLevelFor carried its own copy of 8/12/16/20 as four
 * literals in a `when`. Two copies of a ladder is one ladder and one bug waiting for someone to
 * retune the other.
 */
val DARJA_LADDER: List<Pair<Int, Int>> = listOf(8 to 1, 12 to 2, 16 to 3, 20 to 4)

/** The Darja level [lifetimeCoins] has earned, 0 if none. Single source of truth for engine and UI. */
fun darjaLevelOf(lifetimeCoins: Int): Int = DARJA_LADDER.lastOrNull { lifetimeCoins >= it.first }?.second ?: 0

// ─────────────────────────── Public engine API ───────────────────────────

/** Deterministic initial state: shuffle + deal via the seeded RNG. (config, seed) fully determines this. */
fun initialState(
    config: GameConfig,
    seed: Long,
): GameState {
    val cards = LinkedHashMap<CardId, Role>()
    var id = 0
    // Deck is a uniform multiset over the ACTIVE roles only (PATRAKAAR is absent below the big-table threshold).
    for (role in config.activeRoles) repeat(config.copiesPerRole) { cards[CardId(id++)] = role }

    var rng = Rng(seed)
    var pool: List<CardId> = cards.keys.sortedBy { it.raw }
    val locations = LinkedHashMap<CardId, CardLocation>()
    val players = ArrayList<PlayerState>(config.seatCount)
    for (seat in 0 until config.seatCount) {
        val pid = PlayerId(seat)
        repeat(config.influencePerPlayer) {
            val (c, rem, r) = rng.draw(pool)
            pool = rem
            rng = r
            locations[c] = CardLocation.Hand(pid, faceUp = false)
        }
        players.add(PlayerState(pid, seat, config.startingCoins))
    }
    for (c in pool) locations[c] = CardLocation.Deck

    val treasury = config.effectiveCoinSupply - config.startingCoins * config.seatCount
    return GameState(config, cards, locations, players, treasury, Phase.AwaitingAction(0), rng.state, 1)
}

/**
 * The single source of truth for "is the game decided, and who won?". Returns the winner's [PlayerId] when
 * the game is over, or null while it is still live.
 *
 * Free-for-all (teams == null): over iff exactly one player is alive — the classic last-player-standing rule.
 * TEAMS: over iff exactly one team still has any alive member — last-team-standing. The reported winner is
 * the team's REPRESENTATIVE (lowest-seat alive member), chosen deterministically so the result is stable.
 *
 * This is invariant-safe and additive: in free-for-all it is exactly the old `alive.size == 1` check, so
 * non-team games are byte-for-byte unchanged.
 */
fun winnerOrNull(state: GameState): PlayerId? {
    val alive = state.alivePlayers()
    if (alive.isEmpty()) return null
    return if (!state.config.isTeamGame) {
        if (alive.size == 1) alive.first().id else null
    } else {
        if (state.aliveTeams().size == 1) alive.minBySeat() else null
    }
}

private fun List<PlayerState>.minBySeat(): PlayerId = this.minBy { it.seatIndex }.id

/** The single player whose input is needed next, or null at game over. */
fun whoActsNext(state: GameState): PlayerId? =
    when (val ph = state.phase) {
        is Phase.AwaitingAction -> state.playerAtSeat(ph.actorSeat).id
        is Phase.AwaitingReactions -> ph.ctx.eligible.firstOrNull { it !in ph.ctx.responded.keys }
        is Phase.AwaitingInfluenceLoss -> ph.loser
        is Phase.AwaitingExchange -> ph.actor
        is Phase.AwaitingInvestigatePeek -> ph.examiner
        is Phase.GameOver -> null
    }

/** Legal turn actions for [pid] (only non-empty when it is pid's turn to declare). Bluffing is allowed: not role-gated. */
fun legalActions(
    state: GameState,
    pid: PlayerId,
): Set<Action> {
    val ph = state.phase as? Phase.AwaitingAction ?: return emptySet()
    if (state.playerAtSeat(ph.actorSeat).id != pid || !state.isAlive(pid)) return emptySet()
    val cfg = state.config
    val me = state.player(pid)
    val targets = state.alivePlayers().filter { it.id != pid }.map { it.id }
    val out = LinkedHashSet<Action>()

    if (me.coins >= cfg.forcedCoupThreshold) { // I5: forced Khela
        for (t in targets) out.add(Action.Coup(t))
        return out
    }
    out.add(Action.Income)
    out.add(Action.ForeignAid)
    if (me.coins >= cfg.effectiveCoupCost(state.turnNumber)) for (t in targets) out.add(Action.Coup(t)) // ANARCHY/INFLATION: cost varies
    out.add(Action.Tax)
    for (t in targets) if (state.player(t).coins >= 1) out.add(Action.Steal(t)) // D9: Vasooli on 0-coin target is illegal
    // INFLATION: the Supari cost rises with the turn number, so affordability is re-checked here
    // rather than read off a constant.
    if (me.coins >= cfg.effectiveAssassinateCost(state.turnNumber)) {
        for (t in targets) out.add(Action.Assassinate(t))
    }
    out.add(Action.Exchange)
    // Jaanch (claims PATRAKAAR) — only offered when the 6th role is in this deck (otherwise it is a
    // guaranteed-losing bluff). Free action, no cost; target must have a face-down card to examine.
    if (Role.PATRAKAAR in cfg.activeRoles) {
        for (t in targets) if (state.faceDownInfluence(t).isNotEmpty()) out.add(Action.Investigate(t))
    }

    // ── Variant actions (only when their flag is enabled) ──────────────────────────────────────────────
    // BAIL PE BAHAR: must have a revealed card to restore and enough coins.
    if (cfg.bailEnabled && me.coins >= cfg.bailCost && state.faceUpCards(pid).isNotEmpty()) {
        out.add(Action.BailPe)
    }
    // BALI KHEL: must have 2+ face-down cards — cannot sacrifice last influence.
    if (cfg.sabotageEnabled && state.faceDownInfluence(pid).size >= 2) {
        out.add(Action.Sabotage)
    }
    // HAWALA: gift coins to any alive opponent (must have coins to give).
    if (cfg.hawalaEnabled && me.coins >= 1) {
        for (t in targets) out.add(Action.Hawala(t))
    }
    // ADHYADESH (Emergency): lifetime coins ≥ threshold AND current coins ≥ 7 (must pay something meaningful).
    if (cfg.emergencyEnabled &&
        (state.lifetimeCoins[pid] ?: 0) >= cfg.emergencyThreshold &&
        me.coins >= EMERGENCY_MIN_COINS
    ) {
        out.add(Action.Emergency)
    }

    return out
}

/** Concrete legal intents for [pid] given the current phase (used by both the UI and the bots). */
fun legalIntents(
    state: GameState,
    pid: PlayerId,
): List<Intent> =
    when (val ph = state.phase) {
        is Phase.AwaitingAction ->
            if (state.playerAtSeat(ph.actorSeat).id == pid) {
                legalActions(state, pid).map { Intent.DeclareAction(pid, it) }
            } else {
                emptyList()
            }

        is Phase.AwaitingReactions -> {
            if (whoActsNext(state) != pid) {
                emptyList()
            } else {
                when (ph.ctx.step) {
                    ReactionStep.CHALLENGE_ACTION, ReactionStep.CHALLENGE_BLOCK ->
                        listOf(Intent.Challenge(pid), Intent.Pass(pid))
                    ReactionStep.BLOCK ->
                        listOf(Intent.Pass(pid)) + Rules.rolesThatBlock(ph.ctx.pending.action).map { Intent.Block(pid, it) }
                }
            }
        }

        is Phase.AwaitingInfluenceLoss ->
            if (ph.loser == pid) {
                state.faceDownInfluence(pid).map { Intent.ChooseInfluenceToLose(pid, it) }
            } else {
                emptyList()
            }

        is Phase.AwaitingExchange ->
            if (ph.actor == pid) {
                val pool = state.faceDownInfluence(pid) + ph.drawn
                val keepSize = state.faceDownInfluence(pid).size
                combinations(pool, keepSize).map { Intent.ChooseExchange(pid, it) }
            } else {
                emptyList()
            }

        is Phase.AwaitingInvestigatePeek ->
            if (ph.examiner == pid) {
                listOf(Intent.ResolveInvestigate(pid, forceRedraw = false), Intent.ResolveInvestigate(pid, forceRedraw = true))
            } else {
                emptyList()
            }

        is Phase.GameOver -> emptyList()
        // Variant phases: BailPe auto-picks (no player choice); Sabotage/Emergency use existing InfluenceLoss.
        // No additional legalIntents branches needed — they resolve through existing phase machinery.
    }

/**
 * TEAM-AWARE legal-intent filter for BOTS. Returns [legalIntents] with every teammate-hostile choice removed
 * so an allied bot never coups / assassinates / steals-from / investigates a teammate. In free-for-all this is
 * a no-op (returns the input unchanged), so it is safe to wrap unconditionally.
 *
 * This is a BOT-policy convenience layer ON TOP of the engine's true legality: the engine itself does NOT
 * forbid a teammate-targeted action (a human is free to defect). Only the action-declaration phase is filtered
 * — reactions (challenge/block) are NOT, because challenging a teammate's bluff or declining to block them is
 * a legitimate (if rude) team play, and removing them could empty the reaction window.
 *
 * Safety: if filtering would leave NO legal action (e.g. a forced coup where every alive target is a teammate),
 * the unfiltered list is returned — the engine's forced-move guarantee always wins over team etiquette.
 */
fun teamSafeIntents(
    state: GameState,
    pid: PlayerId,
    legal: List<Intent> = legalIntents(state, pid),
): List<Intent> {
    if (!state.config.isTeamGame) return legal
    if (state.phase !is Phase.AwaitingAction) return legal
    val mates = state.teammatesOf(pid).toSet()
    if (mates.isEmpty()) return legal
    val filtered =
        legal.filter { intent ->
            if (intent !is Intent.DeclareAction) return@filter true
            val target = Rules.targetOf(intent.action) ?: return@filter true
            target !in mates
        }
    return filtered.ifEmpty { legal }
}

/** Validate [intent] and produce the next state + descriptive events, or reject it. */
fun applyIntent(
    state: GameState,
    intent: Intent,
): ApplyOutcome {
    val expected = whoActsNext(state) ?: return ApplyOutcome.Rejected("game over: no actor pending")
    if (intent.actor != expected) return ApplyOutcome.Rejected("expected $expected to act, got ${intent.actor}")
    val e = EngineStep(state)
    return try {
        e.dispatch(intent)
        ApplyOutcome.Accepted(e.state.copy(rng = e.rng.state), e.events)
    } catch (ex: IllegalIntent) {
        ApplyOutcome.Rejected(ex.message ?: "illegal intent")
    }
}

private class IllegalIntent(
    message: String,
) : Exception(message)

// ─────────────────────────── Resolution engine ───────────────────────────

private class EngineStep(
    start: GameState,
) {
    var state: GameState = start
    var rng: Rng = start.rngEngine()
    val events = ArrayList<GameEvent>()
    private val cfg = start.config

    fun dispatch(intent: Intent) {
        when (val ph = state.phase) {
            is Phase.AwaitingAction -> {
                val a = (intent as? Intent.DeclareAction) ?: throw IllegalIntent("expected DeclareAction")
                declareAction(state.playerAtSeat(ph.actorSeat).id, a.action)
            }
            is Phase.AwaitingReactions -> reaction(ph.ctx, intent)
            is Phase.AwaitingInfluenceLoss -> {
                val c = (intent as? Intent.ChooseInfluenceToLose) ?: throw IllegalIntent("expected ChooseInfluenceToLose")
                chooseLoss(ph, c.card)
            }
            is Phase.AwaitingExchange -> {
                val c = (intent as? Intent.ChooseExchange) ?: throw IllegalIntent("expected ChooseExchange")
                chooseExchange(ph, c.keep)
            }
            is Phase.AwaitingInvestigatePeek -> {
                val c = (intent as? Intent.ResolveInvestigate) ?: throw IllegalIntent("expected ResolveInvestigate")
                resolveInvestigatePeek(ph, c.forceRedraw)
            }
            is Phase.GameOver -> throw IllegalIntent("game over")
        }
    }

    // ── coin / card mutators ──
    private fun setCoins(
        pid: PlayerId,
        coins: Int,
    ) {
        state = state.copy(players = state.players.map { if (it.id == pid) it.copy(coins = coins) else it })
    }

    private fun gain(
        pid: PlayerId,
        amount: Int,
    ) {
        val take = minOf(amount, state.treasury)
        if (take <= 0) return
        setCoins(pid, state.player(pid).coins + take)
        state = state.copy(treasury = state.treasury - take)
        events.add(GameEvent.CoinsChanged(pid, +take))
        // Track lifetime coins for KhazanaRaj / Emergency unlock (only when feature is active).
        if (cfg.khazanaEnabled || cfg.emergencyEnabled) {
            val prev = state.lifetimeCoins[pid] ?: 0
            val next = prev + take
            state = state.copy(lifetimeCoins = state.lifetimeCoins + (pid to next))
            checkKhazanaMilestones(pid, prev, next)
        }
    }

    private fun checkKhazanaMilestones(
        pid: PlayerId,
        prev: Int,
        next: Int,
    ) {
        if (!cfg.khazanaEnabled) return
        // Emit Darja milestone events when a threshold is crossed for the first time.
        for ((threshold, level) in DARJA_LADDER) {
            if (prev < threshold && next >= threshold) {
                events.add(GameEvent.DarjaReached(pid, level, next))
            }
        }
        // Khazana win: first to reach target lifetime coins wins, even with multiple players still alive.
        if (next >= cfg.khazanaTarget && state.phase !is Phase.GameOver) {
            state = state.copy(phase = Phase.GameOver(pid))
            events.add(GameEvent.KhazanaWon(pid, next))
            events.add(GameEvent.GameEnded(pid))
        }
    }

    private fun pay(
        pid: PlayerId,
        amount: Int,
    ) {
        if (state.player(pid).coins < amount) throw IllegalIntent("not enough coins")
        setCoins(pid, state.player(pid).coins - amount)
        state = state.copy(treasury = state.treasury + amount)
        events.add(GameEvent.CoinsChanged(pid, -amount))
    }

    private fun transfer(
        from: PlayerId,
        to: PlayerId,
        amount: Int,
    ) {
        val take = minOf(amount, state.player(from).coins)
        setCoins(from, state.player(from).coins - take)
        setCoins(to, state.player(to).coins + take)
        events.add(GameEvent.CoinsTransferred(from, to, take))
    }

    private fun setLoc(
        card: CardId,
        loc: CardLocation,
    ) {
        state = state.copy(locations = state.locations + (card to loc))
    }

    private fun drawFromDeck(): CardId {
        val pool = state.deckCards
        if (pool.isEmpty()) throw IllegalIntent("deck exhausted")
        val (c, _, r) = rng.draw(pool)
        rng = r
        return c
    }

    private fun revealCardOfRole(
        pid: PlayerId,
        role: Role,
    ): CardId? = state.faceDownInfluence(pid).firstOrNull { state.cards[it] == role }

    private fun replaceProvenCard(
        pid: PlayerId,
        card: CardId,
    ) {
        setLoc(card, CardLocation.Deck) // shuffle proven card back…
        val fresh = drawFromDeck() // …draw a replacement (may redraw the same card)
        setLoc(fresh, CardLocation.Hand(pid, faceUp = false))
        events.add(GameEvent.CardReplaced(pid, card, fresh))
    }

    // ── declaration ──
    private fun declareAction(
        actor: PlayerId,
        action: Action,
    ) {
        if (action !in legalActions(state, actor)) throw IllegalIntent("illegal action $action")
        val claimed = Rules.claimedRole(action)
        events.add(GameEvent.ActionDeclared(actor, action, claimed))
        when (action) { // E4/E10: coup & assassinate cost paid at declaration
            is Action.Coup -> pay(actor, cfg.effectiveCoupCost(state.turnNumber))
            is Action.Assassinate -> pay(actor, cfg.effectiveAssassinateCost(state.turnNumber)) // INFLATION: cost varies
            else -> {}
        }
        enterReactionsOrResolve(PendingAction(actor, action, claimed))
    }

    private fun enterReactionsOrResolve(pending: PendingAction) {
        val a = pending.action
        when {
            Rules.isChallengeable(a) -> enterStep(ReactionStep.CHALLENGE_ACTION, pending, null)
            Rules.isBlockable(a) -> enterStep(ReactionStep.BLOCK, pending, null)
            else -> resolveEffect(pending) // Income / Coup
        }
    }

    // ── reaction windows ──
    private fun eligibleFor(
        step: ReactionStep,
        pending: PendingAction,
        block: BlockClaim?,
    ): List<PlayerId> {
        val actorSeat = state.seatOf(pending.actor)
        return when (step) {
            // Everyone-but-the-actor, clockwise from the actor's left.
            ReactionStep.CHALLENGE_ACTION -> state.aliveFromLeftOf(actorSeat, setOf(pending.actor))
            ReactionStep.BLOCK ->
                if (pending.action == Action.ForeignAid) {
                    state.aliveFromLeftOf(actorSeat, setOf(pending.actor))
                } else {
                    val t = Rules.targetOf(pending.action)
                    if (t != null && state.isAlive(t)) listOf(t) else emptyList()
                }
            // Everyone-but-the-blocker (the ACTOR may challenge their own action's block), clockwise.
            ReactionStep.CHALLENGE_BLOCK -> state.aliveFromLeftOf(actorSeat, setOf(block!!.blocker))
        }
    }

    private fun enterStep(
        step: ReactionStep,
        pending: PendingAction,
        block: BlockClaim?,
    ) {
        val eligible = eligibleFor(step, pending, block)
        val ctx = ReactionCtx(pending, step, eligible, emptyMap(), block)
        if (eligible.isEmpty()) {
            resolveStep(ctx)
        } else {
            state = state.copy(phase = Phase.AwaitingReactions(ctx))
        }
    }

    private fun reaction(
        ctx: ReactionCtx,
        intent: Intent,
    ) {
        val responder = intent.actor
        val r: Reaction =
            when (ctx.step) {
                ReactionStep.CHALLENGE_ACTION, ReactionStep.CHALLENGE_BLOCK ->
                    when (intent) {
                        is Intent.Challenge -> Reaction.Challenge
                        is Intent.Pass -> Reaction.Pass
                        else -> throw IllegalIntent("expected Challenge/Pass")
                    }
                ReactionStep.BLOCK ->
                    when (intent) {
                        is Intent.Block -> {
                            if (intent.role !in Rules.rolesThatBlock(ctx.pending.action)) {
                                throw IllegalIntent("${intent.role} cannot block ${ctx.pending.action}")
                            }
                            Reaction.Block(intent.role)
                        }
                        is Intent.Pass -> Reaction.Pass
                        else -> throw IllegalIntent("expected Block/Pass")
                    }
            }
        val responded = ctx.responded + (responder to r)
        val next = ctx.copy(responded = responded)
        if (responded.size >= ctx.eligible.size) {
            resolveStep(next)
        } else {
            state = state.copy(phase = Phase.AwaitingReactions(next))
        }
    }

    private fun resolveStep(ctx: ReactionCtx) {
        when (ctx.step) {
            ReactionStep.CHALLENGE_ACTION -> {
                val challenger = ctx.eligible.firstOrNull { ctx.responded[it] is Reaction.Challenge }
                when {
                    challenger != null -> resolveActionChallenge(ctx.pending, challenger)
                    Rules.isBlockable(ctx.pending.action) -> enterStep(ReactionStep.BLOCK, ctx.pending, null)
                    else -> resolveEffect(ctx.pending)
                }
            }
            ReactionStep.BLOCK -> {
                val blocker = ctx.eligible.firstOrNull { ctx.responded[it] is Reaction.Block }
                if (blocker != null) {
                    val role = (ctx.responded.getValue(blocker) as Reaction.Block).role
                    events.add(GameEvent.Blocked(blocker, role, ctx.pending.action))
                    enterStep(ReactionStep.CHALLENGE_BLOCK, ctx.pending, BlockClaim(blocker, role))
                } else {
                    resolveEffect(ctx.pending)
                }
            }
            ReactionStep.CHALLENGE_BLOCK -> {
                val challenger = ctx.eligible.firstOrNull { ctx.responded[it] is Reaction.Challenge }
                if (challenger != null) {
                    resolveBlockChallenge(ctx.pending, ctx.block!!, challenger)
                } else { // block stands → action negated
                    events.add(GameEvent.ActionNegated(ctx.pending.actor, ctx.pending.action))
                    endTurn(ctx.pending.actor)
                }
            }
        }
    }

    private fun resolveActionChallenge(
        pending: PendingAction,
        challenger: PlayerId,
    ) {
        val role = pending.claimedRole!!
        events.add(GameEvent.Challenged(challenger, pending.actor, role))
        val card = revealCardOfRole(pending.actor, role)
        if (card != null) { // truthful → challenger loses, THEN action survives
            events.add(GameEvent.ChallengeRevealed(pending.actor, card, role, true))
            // Spec A.5 step 3: remove challenger's penalty card → reshuffle prover's proven card → prover draws.
            // The reveal-replace is deferred into the continuation so InfluenceLost precedes CardReplaced.
            requireLoss(challenger, LossReason.LOST_CHALLENGE, AfterLoss.AfterActionSurvived(pending, card))
        } else { // bluff → actor loses, action fails (cost NOT refunded — E4)
            events.add(GameEvent.ActionNegated(pending.actor, pending.action))
            requireLoss(pending.actor, LossReason.LOST_CHALLENGE, AfterLoss.EndTurn(pending.actor))
        }
    }

    private fun resolveBlockChallenge(
        pending: PendingAction,
        block: BlockClaim,
        challenger: PlayerId,
    ) {
        events.add(GameEvent.Challenged(challenger, block.blocker, block.role))
        val card = revealCardOfRole(block.blocker, block.role)
        if (card != null) { // block truthful → challenger loses, THEN block stands
            events.add(GameEvent.ChallengeRevealed(block.blocker, card, block.role, true))
            // Spec A.5 step 3 order: challenger's penalty card removed first, then blocker reveal-replaces.
            requireLoss(challenger, LossReason.LOST_BLOCK_CHALLENGE, AfterLoss.BlockProven(pending, block.blocker, card))
        } else { // block bluffed → block fails, action resolves
            requireLoss(block.blocker, LossReason.LOST_BLOCK_CHALLENGE, AfterLoss.ResolveEffect(pending))
        }
    }

    // ── effects ──
    private fun resolveEffect(pending: PendingAction) {
        val a = pending.action
        val actor = pending.actor
        if (a != Action.Exchange) events.add(GameEvent.ActionResolved(actor, a))
        when (a) {
            Action.Income -> {
                gain(actor, cfg.incomeAmount)
                if (state.phase !is Phase.GameOver) endTurn(actor)
            }
            Action.ForeignAid -> {
                gain(actor, cfg.foreignAidAmount)
                if (state.phase !is Phase.GameOver) endTurn(actor)
            }
            Action.Tax -> {
                gain(actor, cfg.taxAmount)
                if (state.phase !is Phase.GameOver) endTurn(actor)
            }
            is Action.Steal -> {
                if (state.isAlive(a.target)) transfer(a.target, actor, cfg.stealAmount)
                if (state.phase !is Phase.GameOver) endTurn(actor)
            }
            is Action.Coup ->
                if (state.isAlive(
                        a.target,
                    )
                ) {
                    requireLoss(a.target, LossReason.COUPED, AfterLoss.EndTurn(actor))
                } else {
                    endTurn(actor)
                }
            is Action.Assassinate ->
                if (state.isAlive(
                        a.target,
                    )
                ) {
                    requireLoss(a.target, LossReason.ASSASSINATED, AfterLoss.EndTurn(actor))
                } else {
                    endTurn(actor)
                }
            Action.Exchange -> startExchange(actor)
            is Action.Investigate -> startInvestigate(actor, a.target)
            // ── Variant actions ─────────────────────────────────────────────────────────────────────────
            Action.BailPe -> resolveBailPe(actor)
            Action.Sabotage -> requireLoss(actor, LossReason.SABOTAGED, AfterLoss.EndTurn(actor))
            is Action.Hawala -> resolveHawala(actor, a.to)
            Action.Emergency -> resolveEmergency(actor)
        }
    }

    private fun resolveBailPe(actor: PlayerId) {
        val faceUp = state.faceUpCards(actor)
        if (faceUp.isEmpty()) {
            endTurn(actor)
            return
        }
        pay(actor, cfg.bailCost)
        val card = faceUp.first() // restore the first revealed card (auto-pick, lowest card id)
        val role = state.cards.getValue(card)
        setLoc(card, CardLocation.Hand(actor, faceUp = false))
        events.add(GameEvent.InfluenceRestored(actor, card, role))
        endTurn(actor)
    }

    private fun resolveHawala(
        actor: PlayerId,
        to: PlayerId,
    ) {
        val amount = minOf(cfg.hawalaMaxGift, state.player(actor).coins)
        if (amount > 0 && state.isAlive(to)) {
            // Hawala bypasses treasury — direct peer-to-peer transfer.
            val actual = minOf(amount, state.player(actor).coins)
            setCoins(actor, state.player(actor).coins - actual)
            setCoins(to, state.player(to).coins + actual)
            events.add(GameEvent.CoinsGifted(actor, to, actual))
            // Receiving player's coins do NOT count toward their lifetime (no earning — it's a gift).
        }
        endTurn(actor)
    }

    private fun resolveEmergency(actor: PlayerId) {
        events.add(GameEvent.EmergencyDeclared(actor))
        val currentCoins = state.player(actor).coins
        if (currentCoins > 0) pay(actor, currentCoins)
        val targets =
            state
                .alivePlayers()
                .filter { it.id != actor }
                .sortedBy { it.seatIndex }
                .map { it.id }
        if (targets.isEmpty()) {
            endTurn(actor)
            return
        }
        requireLoss(targets.first(), LossReason.EMERGENCY_COUPED, AfterLoss.EmergencyNext(actor, targets.drop(1)))
    }

    private fun startInvestigate(
        examiner: PlayerId,
        target: PlayerId,
    ) {
        // Target may have lost their last influence in an interleaving — if so, nothing to examine; end turn.
        val peeked = state.faceDownInfluence(target).firstOrNull()
        if (peeked == null) {
            events.add(GameEvent.Investigated(examiner, target))
            endTurn(examiner)
            return
        }
        events.add(GameEvent.Investigated(examiner, target))
        // The examiner now privately knows [peeked] (surfaced only to them in redact) and decides force-redraw.
        state = state.copy(phase = Phase.AwaitingInvestigatePeek(examiner, target, peeked))
    }

    private fun resolveInvestigatePeek(
        ph: Phase.AwaitingInvestigatePeek,
        forceRedraw: Boolean,
    ) {
        if (forceRedraw && state.faceDownInfluence(ph.target).contains(ph.peeked)) {
            // Shuffle the examined card back into the deck and have the target redraw a fresh one.
            // Reuses the canonical reveal-replace: returns role to deck, draws a new face-down card.
            replaceProvenCard(ph.target, ph.peeked)
            events.add(GameEvent.InvestigateRedraw(ph.target))
        }
        endTurn(ph.examiner)
    }

    private fun startExchange(actor: PlayerId) {
        val drawn = ArrayList<CardId>(cfg.exchangeDrawCount)
        repeat(cfg.exchangeDrawCount) {
            val c = drawFromDeck()
            setLoc(c, CardLocation.ExchangeHeld(actor))
            drawn.add(c)
        }
        state = state.copy(phase = Phase.AwaitingExchange(actor, drawn))
    }

    private fun chooseExchange(
        ph: Phase.AwaitingExchange,
        keep: List<CardId>,
    ) {
        val actor = ph.actor
        val hand = state.faceDownInfluence(actor)
        val pool = (hand + ph.drawn).toSet()
        val keepSize = hand.size
        if (keep.size != keepSize) throw IllegalIntent("must keep exactly $keepSize cards")
        if (keep.toSet().size != keep.size) throw IllegalIntent("duplicate keep cards")
        if (!keep.all { it in pool }) throw IllegalIntent("keep card not in exchange pool")
        val keepSet = keep.toSet()
        val returned = pool.filter { it !in keepSet }
        for (c in keep) setLoc(c, CardLocation.Hand(actor, faceUp = false))
        for (c in returned) setLoc(c, CardLocation.Deck)
        events.add(GameEvent.Exchanged(actor, keep, returned))
        endTurn(actor)
    }

    // ── influence loss ──
    private fun requireLoss(
        loser: PlayerId,
        reason: LossReason,
        after: AfterLoss,
    ) {
        if (state.faceDownInfluence(loser).isEmpty()) {
            resolveAfter(after)
            return
        } // nothing left to lose
        state = state.copy(phase = Phase.AwaitingInfluenceLoss(loser, reason, after))
    }

    private fun chooseLoss(
        ph: Phase.AwaitingInfluenceLoss,
        card: CardId,
    ) {
        if (card !in state.faceDownInfluence(ph.loser)) throw IllegalIntent("not a face-down card of ${ph.loser}")
        val role = state.cards.getValue(card)
        setLoc(card, CardLocation.Hand(ph.loser, faceUp = true))
        events.add(GameEvent.InfluenceLost(ph.loser, card, role, ph.reason))
        if (state.faceDownInfluence(ph.loser).isEmpty()) events.add(GameEvent.PlayerEliminated(ph.loser))
        // BALI KHEL (Sabotage): grant coins to the player who voluntarily sacrificed influence.
        if (ph.reason == LossReason.SABOTAGED) gain(ph.loser, cfg.sabotageGain)
        // KhazanaRaj may have fired inside gain(); if so, the game is already over.
        if (state.phase is Phase.GameOver) return
        winnerOrNull(state)?.let { w ->
            state = state.copy(phase = Phase.GameOver(w))
            events.add(GameEvent.GameEnded(w))
            return
        }
        resolveAfter(ph.after)
    }

    private fun resolveAfter(after: AfterLoss) {
        when (after) {
            is AfterLoss.EndTurn -> endTurn(after.actor)
            is AfterLoss.AfterActionSurvived -> {
                // Reveal-replace happens AFTER the challenger's loss (spec A.5 step 3).
                replaceProvenCard(after.pending.actor, after.provenCard)
                if (Rules.isBlockable(after.pending.action)) {
                    enterStep(ReactionStep.BLOCK, after.pending, null)
                } else {
                    resolveEffect(after.pending)
                }
            }
            is AfterLoss.BlockProven -> {
                // Reveal-replace happens AFTER the challenger's loss (spec A.5 step 3), then the block stands.
                replaceProvenCard(after.blocker, after.provenCard)
                events.add(GameEvent.ActionNegated(after.pending.actor, after.pending.action))
                endTurn(after.pending.actor)
            }
            is AfterLoss.ResolveEffect -> resolveEffect(after.pending)
            is AfterLoss.EmergencyNext -> {
                // ADHYADESH chain: after one forced loss, advance to the next target.
                val nextTarget = after.remaining.firstOrNull { state.isAlive(it) }
                if (nextTarget == null) {
                    endTurn(after.actor)
                } else {
                    requireLoss(
                        nextTarget,
                        LossReason.EMERGENCY_COUPED,
                        AfterLoss.EmergencyNext(after.actor, after.remaining.drop(1)),
                    )
                }
            }
        }
    }

    private fun endTurn(turnActor: PlayerId) {
        // KhazanaRaj (or a previous EmergencyNext step) may have already set GameOver inside gain().
        if (state.phase is Phase.GameOver) return
        winnerOrNull(state)?.let { w ->
            state = state.copy(phase = Phase.GameOver(w))
            events.add(GameEvent.GameEnded(w))
            return
        }
        val seat = state.seatOf(turnActor)
        // Single seat-ordering primitive (Model.aliveFromLeftOf): clockwise-skip-eliminated walk.
        // The next actor is the first alive player to the left of the current seat. alive.size > 1
        // above guarantees firstOrNull() is non-null (turnActor may or may not still be alive).
        val nextActor = state.aliveFromLeftOf(seat).first()
        val next = state.seatOf(nextActor)
        state = state.copy(phase = Phase.AwaitingAction(next), turnNumber = state.turnNumber + 1)
        events.add(GameEvent.TurnAdvanced(next, state.turnNumber))
    }
}

// ─────────────────────────── Redaction (the secrecy boundary) ───────────────────────────

data class OpponentView(
    val id: PlayerId,
    val seatIndex: Int,
    val coins: Int,
    val faceUpRoles: List<Role>,
    val faceDownCount: Int,
    val eliminated: Boolean,
    /** Team id (public — team membership is open information in the TEAMS variant). Null in free-for-all. */
    val team: Int? = null,
)

/**
 * One of the VIEWER'S OWN cards, resolved to its [role] and [faceUp] state, tagged with its [id].
 * SECRECY-CRITICAL: this type only ever carries the viewer's own card identities — it is never
 * populated from any opponent's hidden card. It lets a consumer map a viewer-owned [CardId] back to
 * its role (needed for Exchange keep-choices, influence-loss selection, and inspect UIs) without
 * exposing CardIds that the viewer is not entitled to see.
 */
data class OwnCard(
    val id: CardId,
    val role: Role,
    val faceUp: Boolean,
)

/**
 * A card belonging to ANOTHER player whose [role] the viewer is privately entitled to see right now —
 * specifically, the Jaanch examiner's peek at the target's face-down card. Kept DISTINCT from [OwnCard]
 * precisely because [OwnCard]'s secrecy contract is "viewer's own card only"; this type is the ONLY
 * channel through which a viewer ever learns an opponent's hidden role, and it is populated solely for
 * the examiner during [Phase.AwaitingInvestigatePeek].
 */
data class PeekedCard(
    val id: CardId,
    val owner: PlayerId,
    val role: Role,
)

sealed interface PhaseView {
    data class Turn(
        val actor: PlayerId,
    ) : PhaseView

    data class Reactions(
        val actor: PlayerId,
        val action: Action,
        val claimedRole: Role?,
        val step: ReactionStep,
        val toRespond: PlayerId?,
        val blocker: PlayerId?,
        val blockRole: Role?,
    ) : PhaseView

    data class InfluenceLoss(
        val loser: PlayerId,
        val reason: LossReason,
    ) : PhaseView

    /**
     * The exchanging [actor] is choosing which cards to keep.
     * [drawn] holds the cards the actor drew from the deck — populated with the actual [OwnCard]s ONLY
     * when the viewer IS the exchanging actor; it is EMPTY for every other viewer (secrecy: a drawn
     * card is the actor's private information until kept/returned). This makes Exchange evaluable for
     * the acting viewer's UI and AI without leaking the drawn roles to anyone else.
     */
    data class Exchange(
        val actor: PlayerId,
        val drawn: List<OwnCard> = emptyList(),
    ) : PhaseView

    /**
     * The Jaanch examiner is deciding whether to force a redraw.
     * SECRECY-CRITICAL: [examinedCard] (the role the examiner privately learned) is non-null ONLY in the
     * EXAMINER'S OWN view; it is null for the target and every other viewer — they see only that an
     * examination is happening, never which card was seen. This is the secrecy boundary for Jaanch.
     */
    data class InvestigatePeek(
        val examiner: PlayerId,
        val target: PlayerId,
        val examinedCard: PeekedCard? = null,
    ) : PhaseView

    data class Over(
        val winner: PlayerId,
    ) : PhaseView
}

data class PlayerView(
    val viewer: PlayerId,
    val config: GameConfig,
    val treasury: Int,
    val deckCount: Int,
    val turnNumber: Int,
    val myCoins: Int,
    val myInfluence: List<Role>, // viewer's own face-down roles — secret to everyone else
    val myFaceUp: List<Role>,
    /**
     * The viewer's OWN cards as (CardId, role, faceUp) — covers both face-down influence and face-up
     * (revealed) cards. SECRECY-CRITICAL: contains ONLY the viewer's own cards, never an opponent's.
     * [myInfluence]/[myFaceUp] remain the canonical role-only lists; [myCards] is the additive
     * CardId↔role resolver for consumers that need to address a specific viewer-owned card.
     */
    val myCards: List<OwnCard>,
    val players: List<OpponentView>, // every player's PUBLIC face (no hidden roles)
    val phase: PhaseView,
    /** The viewer's own team id in the TEAMS variant, or null in free-for-all. */
    val myTeam: Int? = null,
) {
    /**
     * Public ids of the viewer's teammates (excluding the viewer). EMPTY in free-for-all. Team-aware bots
     * use this to avoid targeting allies. Team membership is open information, so this leaks nothing secret.
     */
    val teammates: List<PlayerId>
        get() =
            if (myTeam == null) {
                emptyList()
            } else {
                players.filter { it.id != viewer && it.team == myTeam }.map { it.id }
            }

    /**
     * Alive players a team-aware bot may legitimately TARGET — every alive player except the viewer and,
     * in the TEAMS variant, the viewer's teammates. In free-for-all this is just "alive opponents". This is
     * the single targeting primitive AI policies should use when selecting a victim, so targeting consults
     * team membership uniformly.
     */
    val targetableOpponents: List<OpponentView>
        get() {
            val mates = teammates.toSet()
            return players.filter { !it.eliminated && it.id != viewer && it.id !in mates }
        }
}

/**
 * Project [state] to what [viewer] is allowed to see. The strict-subset secrecy boundary: a player's
 * own face-down roles never appear except in their own [PlayerView.myInfluence].
 */
fun redact(
    state: GameState,
    viewer: PlayerId,
): PlayerView {
    val pub =
        state.players.sortedBy { it.seatIndex }.map { p ->
            OpponentView(
                id = p.id,
                seatIndex = p.seatIndex,
                coins = p.coins,
                faceUpRoles = state.faceUpCards(p.id).map { state.cards.getValue(it) }.sorted(),
                faceDownCount = state.faceDownInfluence(p.id).size,
                eliminated = !state.isAlive(p.id),
                team = state.config.teamOfSeat(p.seatIndex),
            )
        }
    // The viewer's OWN cards only — face-down influence + face-up reveals. NEVER any opponent's card.
    val myCards =
        state.faceDownInfluence(viewer).map { OwnCard(it, state.cards.getValue(it), faceUp = false) } +
            state.faceUpCards(viewer).map { OwnCard(it, state.cards.getValue(it), faceUp = true) }
    return PlayerView(
        viewer = viewer,
        config = state.config,
        treasury = state.treasury,
        deckCount = state.deckCards.size,
        turnNumber = state.turnNumber,
        myCoins = state.player(viewer).coins,
        myInfluence = state.faceDownInfluence(viewer).map { state.cards.getValue(it) }.sorted(),
        myFaceUp = state.faceUpCards(viewer).map { state.cards.getValue(it) }.sorted(),
        myCards = myCards,
        players = pub,
        phase = phaseView(state, viewer),
        myTeam = state.teamOf(viewer),
    )
}

private fun phaseView(
    state: GameState,
    viewer: PlayerId,
): PhaseView =
    when (val ph = state.phase) {
        is Phase.AwaitingAction -> PhaseView.Turn(state.playerAtSeat(ph.actorSeat).id)
        is Phase.AwaitingReactions ->
            PhaseView.Reactions(
                actor = ph.ctx.pending.actor,
                action = ph.ctx.pending.action,
                claimedRole = ph.ctx.pending.claimedRole,
                step = ph.ctx.step,
                toRespond = whoActsNext(state),
                blocker = ph.ctx.block?.blocker,
                blockRole = ph.ctx.block?.role,
            )
        is Phase.AwaitingInfluenceLoss -> PhaseView.InfluenceLoss(ph.loser, ph.reason)
        is Phase.AwaitingExchange -> {
            // The drawn cards are the actor's private info: surface them ONLY to the actor's own view.
            val drawn =
                if (viewer == ph.actor) {
                    ph.drawn.map { OwnCard(it, state.cards.getValue(it), faceUp = false) }
                } else {
                    emptyList()
                }
            PhaseView.Exchange(ph.actor, drawn)
        }
        is Phase.AwaitingInvestigatePeek -> {
            // The examined card's identity is the examiner's PRIVATE knowledge: surface it ONLY to the examiner.
            // The target must NOT learn which of their cards was peeked (it is one of their own face-down cards,
            // but they don't know the engine examined THAT specific one vs another), and no third party learns it.
            val examined =
                if (viewer == ph.examiner) {
                    PeekedCard(ph.peeked, ph.target, state.cards.getValue(ph.peeked))
                } else {
                    null
                }
            PhaseView.InvestigatePeek(ph.examiner, ph.target, examined)
        }
        is Phase.GameOver -> PhaseView.Over(ph.winner)
    }

private fun Role.sorted(): Role = this

private fun List<Role>.sorted(): List<Role> = this.sortedBy { it.ordinal }

// ─────────────────────────── Invariants ───────────────────────────

/** Asserts the engine's structural invariants. Throws IllegalStateException on any violation. */
fun checkInvariants(state: GameState) {
    val cfg = state.config
    // I1 — card conservation: each CardId present once, per-role copies exact, total == deckSize.
    check(state.locations.size == cfg.deckSize) { "I1: ${state.locations.size} located cards, expected ${cfg.deckSize}" }
    check(state.cards.size == cfg.deckSize) { "I1: card map size ${state.cards.size}" }
    // Only the ACTIVE roles appear, each with exactly copiesPerRole copies; inactive roles must be ABSENT.
    for (r in Role.entries) {
        val n = state.cards.count { it.value == r }
        val expected = if (r in cfg.activeRoles) cfg.copiesPerRole else 0
        check(n == expected) { "I1: role $r has $n copies, expected $expected" }
    }
    for (id in 0 until cfg.deckSize) check(state.locations.containsKey(CardId(id))) { "I1: missing card $id" }

    // I2 — coin conservation (treasury + players = effective supply; scarcity may reduce the supply).
    val totalCoins = state.treasury + state.players.sumOf { it.coins }
    check(totalCoins == cfg.effectiveCoinSupply) { "I2: coins $totalCoins != supply ${cfg.effectiveCoinSupply}" }
    check(state.treasury >= 0) { "I2: negative treasury" }
    check(state.players.all { it.coins >= 0 }) { "I2: negative player coins" }

    // I4 — influence bounds: 0..influencePerPlayer face-down per player.
    for (p in state.players) {
        val inf = state.influenceCount(p.id)
        check(inf in 0..cfg.influencePerPlayer) { "I4: ${p.id} has $inf influence" }
    }

    // I10 — win condition coherence.
    val alive = state.alivePlayers()
    when (val ph = state.phase) {
        is Phase.GameOver -> {
            when {
                // KHAZANA RAJ: win by coin milestone — multiple players may still be alive at game end.
                cfg.khazanaEnabled && (state.lifetimeCoins[ph.winner] ?: 0) >= cfg.khazanaTarget -> {
                    check(state.isAlive(ph.winner) || true) { "I10: KhazanaRaj GameOver winner ${ph.winner} sanity" }
                }
                !cfg.isTeamGame -> {
                    // Classic free-for-all: exactly one player alive, and it is the declared winner.
                    check(
                        alive.size == 1 && alive.first().id == ph.winner,
                    ) { "I10: GameOver(${ph.winner}) but alive=${alive.map { it.id }}" }
                }
                else -> {
                    // TEAMS: exactly one team alive, the winner is alive and on that team.
                    check(state.aliveTeams().size == 1) { "I10: team GameOver but aliveTeams=${state.aliveTeams()}" }
                    check(state.isAlive(ph.winner)) { "I10: team GameOver winner ${ph.winner} is not alive" }
                    check(state.aliveTeams().single() == state.teamOf(ph.winner)) { "I10: winner ${ph.winner} not on the surviving team" }
                }
            }
        }
        else -> check(alive.size >= 1) { "I10: non-terminal phase with ${alive.size} alive" }
    }
}

// ─────────────────────────── Small utilities ───────────────────────────

fun <T> combinations(
    items: List<T>,
    k: Int,
): List<List<T>> {
    if (k == 0) return listOf(emptyList())
    if (k > items.size) return emptyList()
    val res = ArrayList<List<T>>()

    fun rec(
        start: Int,
        acc: List<T>,
    ) {
        if (acc.size == k) {
            res.add(acc)
            return
        }
        for (i in start until items.size) rec(i + 1, acc + items[i])
    }
    rec(0, emptyList())
    return res
}
