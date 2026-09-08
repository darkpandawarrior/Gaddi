package com.kursi.feature.game.session

import com.kursi.ai.OpponentInsight
import com.kursi.ai.Policy
import com.kursi.ai.advisor.MoveAdvice
import com.kursi.ai.advisor.MoveAdvisor
import com.kursi.ai.persona.PersonaPolicy
import com.kursi.engine.ApplyOutcome
import com.kursi.engine.GameConfig
import com.kursi.engine.GameEvent
import com.kursi.engine.GameState
import com.kursi.engine.Intent
import com.kursi.engine.Phase
import com.kursi.engine.PlayerId
import com.kursi.engine.Rules
import com.kursi.engine.applyIntent
import com.kursi.engine.initialState
import com.kursi.engine.legalIntents
import com.kursi.engine.redact
import com.kursi.engine.teamSafeIntents
import com.kursi.engine.whoActsNext
import com.kursi.feature.game.GameUiState
import com.kursi.feature.game.OpponentPersona
import com.kursi.feature.game.narrative.HumanChatInput
import com.kursi.feature.game.narrative.SocialDirector

/** TEAM KHEL: the coach's rationale for an ally-targeting option it refuses to recommend. */
private const val ALLY_WARN = "Saathi hai — uspe waar mat karo."

/**
 * Offline game coordinator — the authoritative source of [GameState] for a single local game.
 *
 * Responsibilities:
 * - Holds the full (un-redacted) [GameState].
 * - Auto-advances bot seats between human turns by calling their [Policy] with ONLY the
 *   bot's redacted [com.kursi.engine.PlayerView] — bots never see the full state.
 * - Exposes only a [GameUiState] (built from `redact(state, humanSeat)`) to callers.
 *
 * # M5 PASS-AND-PLAY
 * The session is generalized from a single human seat to a SET of [humanSeats]. The advance loop
 * pauses whenever the NEXT actor is ANY human seat (not just one). Bots fill every other seat. The
 * redacted [GameUiState] is always built for the seat that must act next ([activeHumanSeat]), so each
 * hot-seat player only ever sees their OWN hand — the engine's [redact] guarantees secrecy. The
 * single-human constructor delegates to the set form with `setOf(humanSeat)`.
 *
 * @param config           Game configuration (e.g. [GameConfig.forPlayers]).
 * @param seed             Deterministic RNG seed for the initial state.
 * @param humanSeats       The set of [PlayerId]s that humans control (>=1). Pass-and-play passes
 *                         more than one; vs-AI passes exactly one.
 * @param bots             Policy per non-human seat. Every non-human alive seat MUST have an entry.
 * @param opponentPersonas Optional persona display info keyed by PlayerId (shown in the UI).
 */
class GameSession(
    private val config: GameConfig,
    private val seed: Long,
    private val humanSeats: Set<PlayerId>,
    private val bots: Map<PlayerId, Policy>,
    private val opponentPersonas: Map<PlayerId, OpponentPersona> = emptyMap(),
    /**
     * DARBAR / NARRATIVE MODE — the social/chat conductor. Null = pristine play: no chat, no arcs, no
     * targeting nudge (the engine path is byte-for-byte unchanged). When present it (a) observes the
     * public event stream to evolve the social fabric + voice bot chatter and (b) biases each bot's
     * chosen intent among the SAME legal team-safe choices — never an illegal move, never engine state.
     */
    private val socialDirector: SocialDirector? = null,
) {
    init {
        require(humanSeats.isNotEmpty()) { "a session needs at least one human seat" }
    }

    /** Backward-compatible single-human constructor (vs-AI). */
    constructor(
        config: GameConfig,
        seed: Long,
        humanSeat: PlayerId,
        bots: Map<PlayerId, Policy>,
        opponentPersonas: Map<PlayerId, OpponentPersona> = emptyMap(),
        socialDirector: SocialDirector? = null,
    ) : this(config, seed, setOf(humanSeat), bots, opponentPersonas, socialDirector)

    /** True when this session is running in Darbar / narrative mode. */
    val isNarrative: Boolean get() = socialDirector != null

    /** True when this is a multi-human (pass-and-play) session. */
    val isPassAndPlay: Boolean get() = humanSeats.size > 1

    /**
     * The human seat that must act NOW, or null when it is a bot's turn / the game is over. In a
     * single-human session this is always [humanSeat] when [isHumanTurn]; in pass-and-play it is
     * whichever human seat the advance loop paused on.
     */
    fun activeHumanSeat(): PlayerId? = whoActsNext(state)?.takeIf { it in humanSeats }

    /** The canonical "this player's" seat — the lowest human seat. Used for coach/advisor anchoring. */
    private val humanSeat: PlayerId = humanSeats.minByOrNull { it.raw }!!
    private var state: GameState = initialState(config, seed)
    private val eventLog = ArrayDeque<GameEvent>(GameUiState.MAX_EVENTS * 2)

    /**
     * Append-only log of the HUMAN's submitted intents, in order. Because the engine is fully
     * deterministic (RNG carried in [GameState]) and bots derive their moves purely from the
     * redacted view, replaying this log onto a fresh [initialState] reconstructs the EXACT same
     * [GameState] — that is the entire persistence/resume contract (M3 §2). Bot intents are NOT
     * logged: they are recomputed by the same policies during replay.
     */
    private val humanIntentLog = ArrayList<Intent>()

    /** The ordered human intent log — the replay seed for [restore]. */
    fun humanActionLog(): List<Intent> = humanIntentLog.toList()

    /**
     * The DECISION-COACH brain for this game. Deterministically seeded off the game seed so the
     * same game always yields the same reads. [MoveAdvisor.advise] redacts internally, so it only
     * ever sees what the human is allowed to see — the coach is FAIR (no peeking at hidden cards).
     *
     * ISMCTS runs ~200–400 ms, so [adviseHuman] must NEVER be called on the UI thread; the
     * ViewModel runs it on its background coroutine scope.
     */
    private val advisor = MoveAdvisor(seed = seed)

    /**
     * The persona-wrapped bots, by seat. Used to deliver grudge signals ([PersonaPolicy.notifyHit])
     * and per-turn decay ([PersonaPolicy.notifyTurnPassed]) so VINDICTIVE personas retaliate against
     * whoever hit them recently. A subset of [bots] — only seats whose policy is a [PersonaPolicy].
     */
    private val personaBots: Map<PlayerId, PersonaPolicy> =
        bots.mapNotNull { (id, policy) -> (policy as? PersonaPolicy)?.let { id to it } }.toMap()

    /**
     * Most-recent declared-action actor — the candidate aggressor for an Assassinate/Coup that
     * resolves into an [GameEvent.InfluenceLost] a few events later. Reset on each TurnAdvanced.
     */
    private var lastActionActor: PlayerId? = null

    /**
     * Most-recent challenger — the candidate aggressor for a challenge-driven influence loss
     * ([GameEvent.InfluenceLost] with a LOST_CHALLENGE / LOST_BLOCK_CHALLENGE reason).
     */
    private var lastChallenger: PlayerId? = null

    /**
     * PUBLIC-info opponent dossiers for the human, derived from the decision-coach's [MoveAdvisor]
     * belief (which is fed ONLY public game events). SECRECY: every field is a function of
     * table-visible claims/blocks/reveals — never an opponent's hidden cards. Returns one
     * [OpponentInsight] per opponent of the human in seat order (eliminated seats included).
     */
    fun opponentInsights(): List<OpponentInsight> {
        val view = redact(state, activeViewerSeat())
        return OpponentInsight.forAll(view, advisor.memory, includeEliminated = true)
    }

    /**
     * The seat whose redacted view the UI should show right now. In pass-and-play this is the human
     * seat that must act ([activeHumanSeat]); otherwise it falls back to the canonical [humanSeat] so
     * a between-turns frame still shows a consistent (lowest-seat) board. SECRECY: the active human
     * only ever sees their own hand because the handoff guard gates the screen on seat change.
     */
    private fun activeViewerSeat(): PlayerId = activeHumanSeat() ?: humanSeat

    /**
     * Run the decision-coach on the CURRENT state and return ranked advice for the human's
     * legal moves (best-first, exactly one [MoveAdvice.recommended]). Returns an empty list when
     * it is not the human's turn (or the game is over) — there is nothing to coach.
     *
     * Heavy (ISMCTS): call OFF the main thread / off the paced bot loop.
     *
     * `suspend`: cancelling the caller (the human moves on to a fresh decision) stops the
     * search mid-flight instead of only between whole calls — see [MoveAdvisor.advise].
     */
    suspend fun adviseHuman(): List<MoveAdvice> {
        if (state.phase is Phase.GameOver) return emptyList()
        val seat = activeHumanSeat() ?: return emptyList()
        val legal = legalIntents(state, seat)
        if (legal.isEmpty()) return emptyList()
        val advice = advisor.advise(state, seat, legal)
        // TEAM KHEL: the coach must not RECOMMEND hitting an ally. Advice still covers every legal move
        // (so the UI annotates all of them + the ViewModel's coverage guard holds), but the recommended
        // star is moved onto the best team-safe move and ally-targeting options are flagged.
        return if (config.isTeamGame) teamAwareAdvice(advice, seat) else advice
    }

    /** Re-point the coach's recommendation away from any teammate-targeting move (TEAM KHEL). */
    private fun teamAwareAdvice(
        advice: List<MoveAdvice>,
        viewer: PlayerId,
    ): List<MoveAdvice> {
        val mates = state.teammatesOf(viewer).toSet()
        if (mates.isEmpty()) return advice

        fun hitsMate(a: MoveAdvice): Boolean {
            val da = a.intent as? Intent.DeclareAction ?: return false
            return Rules.targetOf(da.action)?.let { it in mates } == true
        }
        val rec = advice.firstOrNull { it.recommended }
        // If the recommendation is already team-safe, just flag the ally-targeting options.
        if (rec == null || !hitsMate(rec)) {
            return advice.map { if (hitsMate(it)) it.copy(recommended = false, rationale = ALLY_WARN) else it }
        }
        val safeBest = advice.filter { !hitsMate(it) }.maxByOrNull { it.winProb }
        return advice.map {
            when {
                it === safeBest -> it.copy(recommended = true)
                hitsMate(it) -> it.copy(recommended = false, rationale = ALLY_WARN)
                else -> it.copy(recommended = false)
            }
        }
    }

    /**
     * M5 ASSISTANT (best-move + auto-mode). Returns the single best [Intent] for the human seat that
     * must act now, or null when it is not a human's turn / the game is over. Heavy (ISMCTS) when more
     * than one move is legal — call OFF the main thread, exactly like [adviseHuman].
     *
     * `suspend`: see [adviseHuman] — cancelling the caller stops the search mid-flight.
     */
    suspend fun bestHumanMove(): Intent? {
        if (state.phase is Phase.GameOver) return null
        val seat = activeHumanSeat() ?: return null
        val legal = legalIntents(state, seat)
        if (legal.isEmpty()) return null
        // TEAM KHEL: best-move / auto / spectator never picks an ally hit (fall back to full legal only
        // if every move would target a teammate — e.g. a forced Khela with only allies left to target).
        val pool = if (config.isTeamGame) teamSafeIntents(state, seat, legal).ifEmpty { legal } else legal
        return advisor.bestMove(state, seat, pool)
    }

    /**
     * M5 AUTO-PASS / AUTO-FORCED. Classifies the human seat's current decision so the ViewModel can
     * decide whether to auto-resolve it. Returns null when it is not a human's turn.
     *
     * `suspend`: the FORCED_COUP branch calls [MoveAdvisor.bestMove] — see [adviseHuman].
     */
    suspend fun autoDecision(): AutoDecision? {
        if (state.phase is Phase.GameOver) return null
        val seat = activeHumanSeat() ?: return null
        val legal = legalIntents(state, seat)
        if (legal.isEmpty()) return null
        // AUTO-PASS: the only meaningful reaction is Pass (every legal move IS a Pass).
        if (legal.all { it is Intent.Pass }) {
            return AutoDecision(legal.first(), AutoKind.ONLY_PASS)
        }
        // AUTO-FORCED: a single legal move with no choice (e.g. lose-influence with one card), OR a
        // forced-Coup turn where every legal move is a Coup (only the target differs).
        if (legal.size == 1) {
            return AutoDecision(legal.first(), AutoKind.SINGLE_LEGAL)
        }
        val allForcedCoup = legal.all { it is Intent.DeclareAction && it.action is com.kursi.engine.Action.Coup }
        if (allForcedCoup) {
            // Best (target) chosen by the advisor — forced *that* you Coup, free *whom*. TEAM KHEL: prefer
            // a non-teammate target when the forced Khela still leaves one.
            val pool = if (config.isTeamGame) teamSafeIntents(state, seat, legal).ifEmpty { legal } else legal
            return AutoDecision(advisor.bestMove(state, seat, pool), AutoKind.FORCED_COUP)
        }
        return null
    }

    /** A move the session deems safe to auto-resolve for the human, plus why. */
    data class AutoDecision(
        val intent: Intent,
        val kind: AutoKind,
    )

    /** Classifies an auto-resolvable decision. */
    enum class AutoKind { ONLY_PASS, SINGLE_LEGAL, FORCED_COUP }

    /**
     * Initialise the game and auto-advance through any bot turns that precede the human's first
     * action.  Returns the first [GameUiState] that requires human input (or GameOver).
     */
    fun start(): GameUiState {
        state = initialState(config, seed)
        eventLog.clear()
        humanIntentLog.clear()
        socialDirector?.let {
            it.reset()
            it.greetTable(state.turnNumber)
        }
        advanceUntilHumanOrEnd()
        return buildUiState()
    }

    /**
     * RESUME (M3 §2): rebuild this session's state by replaying [humanIntents] onto a fresh
     * [initialState]. Because the engine is deterministic and bots play from the redacted view,
     * the reconstructed [GameState] is bit-for-bit identical to the original run. The replayed
     * intents are validated against the live legal set at each step; an intent that no longer
     * fits (e.g. a corrupt/old snapshot) stops the replay early and returns the furthest valid
     * state rather than throwing — the player simply resumes from there.
     *
     * Returns the first [GameUiState] that requires human input (or GameOver) after the replay.
     */
    fun restore(humanIntents: List<Intent>): GameUiState = restore(humanIntents, emptyList())

    /**
     * RESUME (M3 §2) with the DARBAR chat log. Replays [humanIntents] AND [chatLog] in lock-step:
     * each chat entry is tagged with the human-move index it was sent at, so it is re-applied to the
     * social director at the exact same boundary on replay. Because the nudge stream advances only in
     * bot-step order and pact-acceptance is rng-free, the reconstructed bot moves — and therefore the
     * whole [GameState] — are bit-for-bit identical.
     *
     * @param chatLog ordered (afterHumanMoves, input) pairs — the director's own logged chat.
     */
    fun restore(
        humanIntents: List<Intent>,
        chatLog: List<Pair<Int, HumanChatInput>>,
    ): GameUiState {
        state = initialState(config, seed)
        eventLog.clear()
        humanIntentLog.clear()
        socialDirector?.let {
            it.reset()
            it.greetTable(state.turnNumber)
        }
        advanceUntilHumanOrEnd()
        drainChat(chatLog, atMove = 0)
        for (intent in humanIntents) {
            if (state.phase is Phase.GameOver) break
            val seat = activeHumanSeat() ?: break
            if (intent !in legalIntents(state, seat)) break
            humanIntentLog.add(intent)
            state = applyAndRecord(intent).first
            advanceUntilHumanOrEnd()
            drainChat(chatLog, atMove = humanIntentLog.size)
        }
        return buildUiState()
    }

    /** Re-apply every logged chat sent at the boundary [atMove] (= human-move index) during replay. */
    private fun drainChat(
        chatLog: List<Pair<Int, HumanChatInput>>,
        atMove: Int,
    ) {
        val d = socialDirector ?: return
        chatLog.filter { it.first == atMove }.forEach { (after, input) ->
            d.onHumanChat(input, after, state.turnNumber, redact(state, humanSeat))
        }
    }

    // ─────────────────────────── DARBAR (narrative chat) ───────────────────────────

    /**
     * The player said something in the Darbar. Drives the social fabric + story arcs and is LOGGED
     * (tagged with the current human-move count) so resume replays it deterministically. Returns the
     * refreshed [GameUiState] (new chat feed + suggestions). No-op outside narrative mode.
     */
    fun applyHumanChat(input: HumanChatInput): GameUiState {
        socialDirector?.onHumanChat(input, humanIntentLog.size, state.turnNumber, redact(state, activeViewerSeat()))
        return buildUiState()
    }

    /** The Darbar chat log (for the resume snapshot). Empty outside narrative mode. */
    fun chatLog(): List<Pair<Int, HumanChatInput>> = socialDirector?.chatLog() ?: emptyList()

    /**
     * Submit the human's chosen [intent].
     *
     * - Asserts the game is not over and it is the human's turn.
     * - Applies the intent (throws [IllegalStateException] if rejected by the engine).
     * - Auto-advances bot turns until the human must act again or the game ends.
     *
     * @return the updated [GameUiState].
     */
    fun submitHuman(intent: Intent): GameUiState {
        check(state.phase !is Phase.GameOver) { "game is already over" }
        check(activeHumanSeat() != null) {
            "it is not a human's turn (next actor: ${whoActsNext(state)})"
        }

        humanIntentLog.add(intent)
        state = applyAndRecord(intent).first
        advanceUntilHumanOrEnd()
        return buildUiState()
    }

    /** Peek at the current [GameUiState] without advancing the game. */
    fun currentUiState(): GameUiState = buildUiState()

    /**
     * The full (un-redacted) authoritative [GameState]. Exposed for persistence/replay verification
     * (M3 §2): a snapshot→restore round-trip must reproduce a bit-for-bit identical state. Callers
     * in the UI layer use [currentUiState] / the redacted view instead — this is the ground truth.
     */
    fun snapshotState(): GameState = state

    /** One increment of game advancement: the resulting view plus the events it produced. */
    data class AdvanceStep(
        val ui: GameUiState,
        val newEvents: List<GameEvent>,
    )

    /**
     * Apply the human's [intent] WITHOUT auto-advancing bots, so the caller can pace the bot
     * round itself (see [stepBotOnce]). Same legality checks as [submitHuman].
     */
    fun applyHuman(intent: Intent): AdvanceStep {
        check(state.phase !is Phase.GameOver) { "game is already over" }
        check(activeHumanSeat() != null) {
            "it is not a human's turn (next actor: ${whoActsNext(state)})"
        }
        humanIntentLog.add(intent)
        val (newState, events) = applyAndRecord(intent)
        state = newState
        return AdvanceStep(buildUiState(), events)
    }

    /**
     * If the next actor is a bot, apply EXACTLY ONE bot action and return the new view + the
     * events it produced. Returns null when it is the human's turn or the game is over (i.e.
     * there is nothing to step). Lets the ViewModel space the bot round out for readability.
     */
    fun stepBotOnce(): AdvanceStep? {
        val phase = state.phase
        if (phase is Phase.GameOver) return null
        val next = whoActsNext(state) ?: return null
        if (next in humanSeats) return null

        val policy =
            bots[next]
                ?: error("No policy registered for seat ${next.raw} — seats with policies: ${bots.keys.map { it.raw }}")
        val botView = redact(state, next)
        val legal = legalIntents(state, next)
        check(legal.isNotEmpty()) { "engine produced no legal intents for bot ${next.raw} in phase $phase" }
        // TEAMS: an allied bot never coups/assassinates/steals-from/investigates a teammate. In
        // free-for-all teamSafeIntents is a pass-through (returns [legal] unchanged).
        val choices = teamSafeIntents(state, next, legal)
        val botIntent = nudge(next, policy.decide(botView, choices), choices, botView)
        val (newState, events) = applyAndRecord(botIntent)
        state = newState
        return AdvanceStep(buildUiState(), events)
    }

    /** True when no bot stepping is pending — i.e. it is a human seat's turn, or the game is over. */
    fun awaitingHumanOrOver(): Boolean {
        if (state.phase is Phase.GameOver) return true
        return whoActsNext(state) in humanSeats
    }

    // ─────────────────────────── private helpers ───────────────────────────

    /**
     * Drive the game forward through zero or more bot turns until either:
     * - [whoActsNext] returns [humanSeat], or
     * - the phase is [Phase.GameOver].
     *
     * Bots receive ONLY their redacted [com.kursi.engine.PlayerView] — structural guarantee
     * that no bot policy can inspect opponents' hidden cards.
     */
    private fun advanceUntilHumanOrEnd() {
        while (true) {
            val phase = state.phase
            if (phase is Phase.GameOver) return
            val next = whoActsNext(state) ?: return
            if (next in humanSeats) return

            // Bot's turn: fetch the policy and let it decide from its own redacted view.
            val policy =
                bots[next]
                    ?: error("No policy registered for seat ${next.raw} — seats with policies: ${bots.keys.map { it.raw }}")
            val botView = redact(state, next)
            val legal = legalIntents(state, next)
            check(legal.isNotEmpty()) { "engine produced no legal intents for bot ${next.raw} in phase $phase" }
            // TEAMS: filter out teammate-hostile choices (pass-through in free-for-all).
            val choices = teamSafeIntents(state, next, legal)
            val botIntent = nudge(next, policy.decide(botView, choices), choices, botView)
            state = applyAndRecord(botIntent).first
        }
    }

    /**
     * DARBAR: let the social director bias a bot's chosen intent toward the table's social pressure,
     * among the SAME legal team-safe [choices]. Identity (returns [chosen]) outside narrative mode and
     * whenever no pressure applies. The director's nudge stream advances in strict bot-step order, so
     * this is deterministic across a fresh run and a replay.
     */
    private fun nudge(
        seat: PlayerId,
        chosen: Intent,
        choices: List<Intent>,
        botView: com.kursi.engine.PlayerView,
    ): Intent = socialDirector?.nudgeBotIntent(seat.raw, chosen, choices, botView) ?: chosen

    private fun applyAndRecord(intent: Intent): Pair<GameState, List<GameEvent>> =
        when (val outcome = applyIntent(state, intent)) {
            is ApplyOutcome.Accepted -> {
                // Feed the LEARNING COACH live history: every applied event is observed so the
                // advisor + opponent dossiers reason from what has actually happened this game.
                // Use the resolved state's turnNumber (the turn the event belongs to).
                val turn = outcome.state.turnNumber
                outcome.events.forEach {
                    addEvent(it)
                    advisor.observe(it, turn)
                    routeGrudges(it)
                }
                // DARBAR: evolve the social fabric + voice proactive bot chatter from the SAME public
                // event stream (deterministic; runs identically on a live step and a replay step).
                socialDirector?.let { d ->
                    d.observe(outcome.events, turn)
                    if (outcome.events.any { it is GameEvent.TurnAdvanced } && outcome.state.phase !is Phase.GameOver) {
                        d.maybeTurnChatter(redact(outcome.state, humanSeat), turn)
                    }
                }
                outcome.state to outcome.events
            }
            is ApplyOutcome.Rejected ->
                error("Intent rejected by engine: ${outcome.reason} [$intent]")
        }

    /**
     * Attribute each resolved hit to its aggressor and feed the victim bot's [PersonaPolicy.notifyHit]
     * so VINDICTIVE personas retaliate. We watch the public event stream and reconstruct causality:
     *
     *  - [GameEvent.ActionDeclared]  → remember the actor (candidate aggressor for an
     *    Assassinate/Coup that lands a few events later).
     *  - [GameEvent.Challenged]      → remember the challenger (aggressor for a challenge-driven loss).
     *  - [GameEvent.InfluenceLost]   → a card was lost; map the reason to the right aggressor and bump
     *    the *victim's* grudge against that aggressor.
     *  - [GameEvent.CoinsTransferred]→ a Steal: the recipient hit the giver (a softer, weight-1 hit).
     *  - [GameEvent.TurnAdvanced]    → decay every persona's grudges once and clear per-turn context.
     *
     * Self-hits (a bot that bluffed and lost its OWN card to a challenge it provoked) never credit a
     * grudge against the bot itself. Grudges only ever point at *another* seat.
     */
    private fun routeGrudges(event: GameEvent) {
        when (event) {
            is GameEvent.ActionDeclared -> lastActionActor = event.actor
            is GameEvent.Challenged -> lastChallenger = event.challenger
            is GameEvent.InfluenceLost -> {
                val victim = event.player
                val aggressor =
                    when (event.reason) {
                        com.kursi.engine.LossReason.ASSASSINATED,
                        com.kursi.engine.LossReason.COUPED,
                        com.kursi.engine.LossReason.EMERGENCY_COUPED,
                        -> lastActionActor
                        com.kursi.engine.LossReason.LOST_CHALLENGE,
                        com.kursi.engine.LossReason.LOST_BLOCK_CHALLENGE,
                        -> lastChallenger
                        com.kursi.engine.LossReason.SABOTAGED -> null // voluntary; no grudge target
                    }
                // Losing a whole influence is the heaviest hit → weight 2.
                creditGrudge(victim = victim, aggressor = aggressor, weight = 2)
            }
            is GameEvent.CoinsTransferred -> {
                // A Steal: the giver was hit by the taker. Coin transfers that aren't a steal
                // (e.g. none currently) would still be a benign weight-1 nudge.
                creditGrudge(victim = event.from, aggressor = event.to, weight = 1)
            }
            is GameEvent.TurnAdvanced -> {
                personaBots.values.forEach { it.notifyTurnPassed() }
                lastActionActor = null
                lastChallenger = null
            }
            else -> {}
        }
    }

    /** Bump [victim]'s grudge against [aggressor], guarding null + self-hit. */
    private fun creditGrudge(
        victim: PlayerId,
        aggressor: PlayerId?,
        weight: Int,
    ) {
        if (aggressor == null || aggressor == victim) return
        personaBots[victim]?.notifyHit(aggressor, weight)
    }

    private fun addEvent(event: GameEvent) {
        eventLog.addLast(event)
        while (eventLog.size > GameUiState.MAX_EVENTS * 2) eventLog.removeFirst()
    }

    private fun buildUiState(): GameUiState {
        val phase = state.phase
        val isOver = phase is Phase.GameOver
        val nextActor = whoActsNext(state)
        val activeHuman = nextActor?.takeIf { it in humanSeats && !isOver }
        val isHumanTurn = activeHuman != null
        // SECRECY: redact for whichever human must act now (pass-and-play hot-seat). Between human
        // turns the active seat changes, and the handoff guard blanks the screen until the next
        // player taps — so a player never sees another's hand.
        val viewerSeat = activeHuman ?: humanSeat
        val legal = if (isHumanTurn) legalIntents(state, viewerSeat) else emptyList()
        val view = redact(state, viewerSeat)
        val winner = if (phase is Phase.GameOver) phase.winner.raw else null

        // Keep at most MAX_EVENTS most-recent events.
        val recent = eventLog.toList().takeLast(GameUiState.MAX_EVENTS)

        // PUBLIC-info opponent dossiers from the decision-coach's belief (fed only public events).
        // Cheap (no search) — pure folding of the accumulated claim/reveal history into a read.
        val insights = OpponentInsight.forAll(view, advisor.memory, includeEliminated = true)

        return GameUiState(
            view = view,
            legalIntents = legal,
            recentEvents = recent,
            isHumanTurn = isHumanTurn,
            isGameOver = isOver,
            winnerSeat = winner,
            opponentPersonas = opponentPersonas,
            opponentInsights = insights,
            activeSeat = if (isHumanTurn) viewerSeat.raw else null,
            isPassAndPlay = isPassAndPlay,
            // DARBAR: surface the live chat + the player's available lines. Suggestions are computed
            // for the human's redacted view (never leaks hidden info).
            narrativeEnabled = socialDirector != null,
            chatFeed = socialDirector?.feed().orEmpty(),
            chatSuggestions = socialDirector?.suggestions(redact(state, humanSeat)).orEmpty(),
            activeArcs = socialDirector?.activeArcs().orEmpty(),
            lifetimeCoins = state.lifetimeCoins,
        )
    }
}
