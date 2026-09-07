package com.kursi.feature.game

import com.kursi.ai.ExpertPolicy
import com.kursi.ai.GrandmasterPolicy
import com.kursi.ai.MunshiNarrator
import com.kursi.ai.Policy
import com.kursi.ai.persona.BotDifficulty
import com.kursi.ai.persona.PersonaAssigner
import com.kursi.ai.persona.PersonaPolicy
import com.kursi.engine.GameConfig
import com.kursi.engine.GameEvent
import com.kursi.engine.PlayerId
import com.kursi.feature.game.narrative.ChatVoice
import com.kursi.feature.game.narrative.SeatInfo
import com.kursi.feature.game.narrative.SocialDirector
import com.kursi.feature.game.session.AutoPlayer
import com.kursi.feature.game.session.GameSession
import com.kursi.feature.game.session.MatchSnapshot
import com.kursi.feature.game.session.toEngine
import com.kursi.feature.game.session.toInput
import com.siddharth.kmp.mvi.EffectEmitter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** One-shot, non-replayable signals from [GameViewModel] — collect via [GameViewModel.effects]. */
sealed interface GameEffect {
    /** The UI dispatched a [GameAction.Submit] whose intent was not in the currently-shown legal set. */
    data object IllegalMove : GameEffect
}

/**
 * MVI ViewModel for a Kursi offline game.
 *
 * Uses a plain [CoroutineScope] (not androidx.lifecycle.ViewModel) for KMP-wide compatibility —
 * the StateFlow MVI contract is identical on all targets.  An app-layer wrapper can supply a
 * platform-scoped [CoroutineScope] (e.g. viewModelScope on Android) to align with the Activity
 * lifecycle, or call [clear] when the screen is gone.
 *
 * Human always occupies seat 0 ([humanSeat] = PlayerId(0)).
 *
 * State is exposed as [StateFlow<GameUiState?>]; null means no game has been started yet.
 *
 * Bot seats are assigned distinct [PersonaPolicy] wrappers via [PersonaAssigner].
 * Difficulty maps to tier:
 *   Easy   → EasyPolicy  (random-ish with guardrails)
 *   Medium → MediumPolicy (hand-authored heuristics)
 *   Hard   → HardPolicy  (belief-based, truthful blocks)
 *   Expert → ExpertPolicy (ISMCTS, 4 000 iter / 700 ms per turn)
 */
class GameViewModel(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /**
     * Reactive coach-enabled preference sourced from [AppPrefs.coachEnabledFlow] by the caller.
     * Keeping this as a plain [StateFlow] avoids a dependency on `core:prefs` in this module.
     * Null = treat as always-true (default on, as in tests / render harness).
     */
    private val coachEnabledFlow: StateFlow<Boolean>? = null,
    /**
     * Callback to persist a coach-enabled change from the in-game toggle back to AppPrefs.
     * Null = no persistence (toggle only affects in-memory state, useful in tests).
     */
    private val onCoachEnabledChange: ((Boolean) -> Unit)? = null,
    /**
     * M3 RESUME. Persist the in-progress match snapshot string after every human move so a relaunch
     * can offer/auto-resume. Null = no persistence (tests / render harness). Called with null to
     * clear the snapshot once the game ends.
     */
    private val onSnapshot: ((String?) -> Unit)? = null,
    /**
     * M5 AUTO-MODE. Turn-speed multiplier (1.0 = normal). Sourced from [AppPrefs.turnSpeedFlow] in
     * the app; null = treat as 1.0 (tests / render harness). Scales the bot-step delays so the player
     * can speed up / slow down 10-player rounds.
     */
    private val turnSpeedFlow: StateFlow<Float>? = null,
    /** M5 AUTO-PASS. When true, the human's only-Pass reactions are passed automatically. Null = off. */
    private val autoPassFlow: StateFlow<Boolean>? = null,
    /** M5 AUTO-FORCED. When true, forced moves (single-legal / forced Coup) are auto-played. Null = off. */
    private val autoPlayForcedFlow: StateFlow<Boolean>? = null,
    /**
     * M6b ANALYTICS. Sink for the per-game decision-quality tally, fired once when the game ends. The
     * app layer maps it into the lifetime decision-quality ledger ([AppPrefs.recordDecisionTally]).
     * Null = no persistence (tests / render harness still accumulate it in [decisionTally]).
     */
    private val onDecisionTally: ((MatchDecisionTally) -> Unit)? = null,
    /**
     * M6c REPLAY. Sink for a finished-match record, fired exactly once when the game ends. The app
     * layer prepends it to the capped recent-matches store ([AppPrefs.addRecentMatch]) so the match
     * can be reviewed/replayed deterministically afterward. Null = no recording (tests / render
     * harness still build the record into [lastCompletedMatch]).
     */
    private val onCompletedMatch: ((com.kursi.feature.game.session.CompletedMatch) -> Unit)? = null,
    /**
     * DARBAR — the language the bots chat in. Defaults to Hinglish (the game's on-brand voice). The
     * app can wire this from the language preference; tests / render harness use the default.
     */
    private val language: Language = Language.HINGLISH,
    /** PROGRESSIVE-DISCLOSURE layer flow (spec §3), sourced from AppPrefs by the app. Null = ANALYST. */
    private val densityLayerFlow: StateFlow<DensityLayer>? = null,
    /** Persist a density-layer change (e.g. graduation / settings). Null = in-memory only. */
    private val onDensityLayerChange: ((DensityLayer) -> Unit)? = null,
    /**
     * MUNSHI (Track 3, spec §8.1) — the AI narrator's provider-matrix selection, built once. Defaults
     * to [MunshiNarrator]'s own zero-arg config (on-device only, no BYOK) for tests / render harness;
     * the app wires a real one via [com.kursi.ai.createMunshiNarrator] using whichever cloud-provider
     * key the settings screen has saved, so the narrator's BYOK cloud tier is actually reachable.
     */
    private val munshi: MunshiNarrator = MunshiNarrator(),
) {
    private val _state = MutableStateFlow<GameUiState?>(null)
    val state: StateFlow<GameUiState?> = _state.asStateFlow()

    // ── DARBAR / narrative state ──────────────────────────────────────────────────

    /** True when the live match runs in narrative mode (bots chat + arcs + social nudges). */
    private var narrativeEnabled: Boolean = false

    /** The story arc the player led with on the Story screen (display only). */
    private var matchStoryArc: String? = null

    /** Highest chat-message id the player has already seen — drives the unread badge. */
    private var chatReadHwm: Long = 0L

    /** Serializes the two ASYNC director writers (the paced bot loop + a chat tap) so they never
     * touch the social fabric concurrently. The human-move path is serial by phase (only the human's
     * turn), and wasm is single-threaded — so this closes the realistic overlap. */
    private val darbarMutex = Mutex()

    /** The in-flight chat-handling coroutine; cancelled before a human game move. */
    private var darbarChatJob: Job? = null

    /** Stamp the live unread count onto a narrative state before publishing it. */
    private fun GameUiState.withUnread(): GameUiState =
        if (!narrativeEnabled) this else copy(unreadChat = chatFeed.count { it.id > chatReadHwm && !it.fromPlayer })

    /** Publish [ui] to the UI, stamping the Darbar unread badge, then kick off Munshi narration for it. */
    private fun emitState(ui: GameUiState) {
        val stamped = ui.withUnread()
        _state.value = stamped
        requestNarration(stamped)
    }

    private val coroutineScope: CoroutineScope = scope
    private val job: Job? = scope.coroutineContext[Job]
    private var session: GameSession? = null

    // One-shot effects (e.g. illegal-move rejection) — reuses the existing coroutineScope above,
    // no new scope and no androidx.lifecycle.ViewModel dependency.
    private val effectEmitter = EffectEmitter<GameEffect>(coroutineScope)
    val effects: Flow<GameEffect> = effectEmitter.effects

    /** The in-flight paced bot-advance loop (one bot action per tick). Cancelled on each new submit/new game. */
    private var advanceJob: Job? = null

    /**
     * Completed by [GameAction.ContinueBeat] (or, in online play — Track 6 — a server-timeout job) to
     * release a gated beat. Non-null only while the paced loop is holding on a [PendingBeat].
     */
    private var beatAck: CompletableDeferred<Unit>? = null

    /** M5: current turn-speed multiplier applied to the pacing constants. 1.0 = normal. */
    private var speedMultiplier: Float = turnSpeedFlow?.value ?: 1.0f

    /** M5 AUTO-MODE / M6e TAMASHA — auto-play, auto-resolve, and spectator-demo machinery. */
    private val autoPlayer =
        AutoPlayer(
            session = { session },
            shown = { _state.value },
            submit = ::submitIntent,
            scope = coroutineScope,
            speed = { speedMultiplier },
        )

    /**
     * The in-flight DECISION-COACH computation (ISMCTS ~200–400 ms). Cancelled/replaced on every
     * new human decision and on new game, so stale advice never lands on a fresh decision point.
     */
    private var adviceJob: Job? = null

    /**
     * The in-flight Munshi narration job for the beat most recently published via [emitState].
     * Cancelled/replaced on every new beat so a stale line can never land on a newer one.
     */
    private var narrationJob: Job? = null

    private companion object {
        /** Pure bookkeeping (income / foreign aid / a coin tick / a bare turn pass): readable but brisk. */
        const val TRIVIAL_STEP_MS = 1600L

        /** A declared action that actually lands (tax / steal / assassinate / exchange / block): give time to absorb. */
        const val ROUTINE_STEP_MS = 2400L

        /** Dramatic beats (challenge / block-stand / reveal / influence loss / elimination / win): full theatrical weight. */
        const val DRAMATIC_STEP_MS = 4000L

        /** Okabe-Ito-ish hues for hot-seat human players (ARGB Long), distinct from the bot personas. */
        val HUMAN_SEAT_COLORS =
            longArrayOf(
                0xFF009E73L, // bluish green
                0xFFD55E00L, // vermillion
                0xFFCC79A7L, // reddish purple
                0xFF56B4E9L, // sky blue
                0xFFE69F00L, // orange
                0xFF0072B2L, // blue
            )
    }

    /** Keeps track of PersonaPolicy instances so we can feed events to ExpertPolicy.observe. */
    private var activeBotPolicies: Map<PlayerId, PersonaPolicy> = emptyMap()

    /**
     * M6b ANALYTICS — the running decision-quality tally for the live match. Folded with one
     * [DecisionQuality] per gradeable human decision and reconciled for bluff outcomes + emitted via
     * [onDecisionTally] at game end. Exposed for tests / render harness inspection.
     */
    var decisionTally: MatchDecisionTally = MatchDecisionTally()
        private set

    /** Guards against double-emitting the tally if game-over is observed more than once. */
    private var tallyEmitted: Boolean = false

    /**
     * M6b — running count of the HUMAN's bluffs that were CAUGHT this match, accumulated from the
     * live event stream (a [GameEvent.ChallengeRevealed] for seat 0 with hadRole=false). Whole-game
     * accurate (it watches every batch as it lands, not a capped tail), so it reconciles
     * [MatchDecisionTally.bluffsOk] = bluffsTried − caught at game end.
     */
    private var humanBluffsCaught: Int = 0

    /** Current match identity — used to (re)build the resume snapshot after each human move. */
    private var matchSeed: Long = 0L
    private var matchPlayers: Int = 0
    private var matchDifficulty: Difficulty = Difficulty.Medium
    private var matchHumanCount: Int = 1

    /** The human player's display name — threaded into moment beats and chat. Default "Khiladi". */
    var humanDisplayName: String = "Khiladi"
        private set

    /** M6c: the persona lineup for the live match (human + bot seats), captured at game start. */
    private var matchPersonas: Map<PlayerId, OpponentPersona> = emptyMap()

    /** M6c: guards against double-recording the completed match if game-over is observed twice. */
    private var matchRecorded: Boolean = false

    /**
     * M6c: the finished-match record built when the live game ended — exposed for tests / render
     * harness inspection (the app layer persists it via [onCompletedMatch]). Null until game-over.
     */
    var lastCompletedMatch: com.kursi.feature.game.session.CompletedMatch? = null
        private set

    /**
     * Persist (or clear) the resume snapshot for the live session. Writes the seed + the human
     * action log so a relaunch can deterministically replay back to here. A no-op when there is no
     * session or no [onSnapshot] sink wired.
     */
    private fun saveSnapshot() {
        val sink = onSnapshot ?: return
        val s = session ?: return
        val snap =
            MatchSnapshot.of(
                seed = matchSeed,
                players = matchPlayers,
                difficulty = matchDifficulty,
                humanLog = s.humanActionLog(),
                humanCount = matchHumanCount,
                narrativeEnabled = narrativeEnabled,
                chatLog = s.chatLog(),
            )
        sink(snap.encode())
    }

    /**
     * M6b — finalise + emit the decision-quality tally exactly once when the game ends. Reconciles
     * bluff outcomes (tried − caught) into [MatchDecisionTally.bluffsOk], stamps the reconciled tally
     * back onto [decisionTally], and fires [onDecisionTally]. Idempotent via [tallyEmitted].
     */
    private fun emitDecisionTallyOnce() {
        if (tallyEmitted) return
        tallyEmitted = true
        val survived = decisionTally.bluffsTried - humanBluffsCaught
        decisionTally = decisionTally.withBluffOutcome(survived)
        if (!decisionTally.isEmpty) onDecisionTally?.invoke(decisionTally)
    }

    /**
     * M6c — build + emit the finished-match record exactly once when the game ends. Captures the
     * recorded outcome (winner), the full human intent log (the replay seed), the persona lineup, and
     * the reconciled decision tally — everything [com.kursi.feature.game.session.ReplaySession] needs
     * to reconstruct the game deterministically for review. Idempotent via [matchRecorded].
     *
     * MUST be called AFTER [emitDecisionTallyOnce] so the reconciled (bluff-outcome) tally is folded in.
     */
    private fun recordCompletedMatchOnce(winnerSeat: Int) {
        if (matchRecorded) return
        val s = session ?: return
        matchRecorded = true
        val personas =
            matchPersonas.values
                .sortedBy { it.playerId.raw }
                .map { p ->
                    com.kursi.feature.game.session.SnapPersona(
                        seat = p.playerId.raw,
                        name = p.name,
                        monogram = p.monogram,
                        seatColorArgb = p.seatColorArgb,
                        isHuman = p.playerId.raw < matchHumanCount,
                    )
                }
        val record =
            com.kursi.feature.game.session.CompletedMatch.of(
                seed = matchSeed,
                players = matchPlayers,
                difficulty = matchDifficulty,
                humanLog = s.humanActionLog(),
                winnerSeat = winnerSeat,
                humanCount = matchHumanCount,
                personas = personas,
                tally =
                    com.kursi.feature.game.session.SnapTally(
                        decisions = decisionTally.decisions,
                        matchedBest = decisionTally.matchedBest,
                        evLostMilli = decisionTally.evLostMilli,
                        challenges = decisionTally.challenges,
                        challengesGood = decisionTally.challengesGood,
                        bluffsTried = decisionTally.bluffsTried,
                        bluffsOk = decisionTally.bluffsOk,
                    ),
                recordedOrdinal = nextRecordOrdinal++,
            )
        lastCompletedMatch = record
        onCompletedMatch?.invoke(record)
    }

    /** Monotonic stamp for completed-match records (display tie-break; the engine has no clock). */
    private var nextRecordOrdinal: Long = 0L

    /**
     * Finalise a game-over: emit the decision tally, then record the completed match. Reads the winner
     * from the current UI state. A single funnel so every game-over site (human-ends, bot-ends,
     * resume-finished) records consistently and exactly once.
     */
    private fun finishGameOver() {
        emitDecisionTallyOnce()
        val winner = _state.value?.winnerSeat ?: return
        recordCompletedMatchOnce(winner)
    }

    /**
     * Human's fixed seat index.  Bots occupy all other seats.
     */
    val humanSeat: PlayerId = PlayerId(0)

    init {
        // Whenever coachEnabledFlow emits, re-stamp coachEnabled onto the live state so every
        // gated surface recomposes immediately — no game restart required.
        if (coachEnabledFlow != null) {
            coroutineScope.launch {
                coachEnabledFlow.collect { enabled ->
                    _state.value = _state.value?.copy(coachEnabled = enabled)
                }
            }
        }
        // M5: keep the live turn-speed multiplier in sync so a settings change applies mid-game.
        if (turnSpeedFlow != null) {
            coroutineScope.launch {
                turnSpeedFlow.collect { speedMultiplier = it }
            }
        }
        // PROGRESSIVE-DISCLOSURE: re-stamp densityLayer onto the live state whenever the flow emits.
        if (densityLayerFlow != null) {
            coroutineScope.launch {
                densityLayerFlow.collect { layer ->
                    _state.value = _state.value?.copy(densityLayer = layer)
                }
            }
        }
    }

    /**
     * Toggle the DECISION-COACH on/off. Calls [onCoachEnabledChange] to persist (if wired) AND
     * immediately re-stamps [coachEnabled] on the live state so the HintRail toggle takes effect
     * without waiting for the flow round-trip.
     */
    fun toggleCoach() {
        val newValue = !(_state.value?.coachEnabled ?: true)
        onCoachEnabledChange?.invoke(newValue) // persist (coachEnabledFlow will re-emit and sync)
        if (coachEnabledFlow == null) {
            // No flow wired (e.g. tests / render harness): update state directly.
            _state.value = _state.value?.copy(coachEnabled = newValue)
        }
    }

    /**
     * PROGRESSIVE-DISCLOSURE — change the density layer (e.g. from Settings, or a future
     * graduation prompt). Calls [onDensityLayerChange] to persist (if wired) AND immediately
     * re-stamps the live state so the UI reflects it without waiting for the flow round-trip
     * (mirrors [toggleCoach]). Nothing gates on [GameUiState.densityLayer] yet (Wave 1 Track 4).
     */
    fun setDensityLayer(layer: DensityLayer) {
        onDensityLayerChange?.invoke(layer)
        if (densityLayerFlow == null) {
            // No flow wired (e.g. tests / render harness): update state directly.
            _state.value = _state.value?.copy(densityLayer = layer)
        }
    }

    /**
     * Dispatch an MVI [action].
     *
     * This is intentionally synchronous — [GameSession] is a pure in-memory reducer
     * (no I/O, no suspend) so there is no need to launch a coroutine per action.
     * The scope is kept for potential future async work (e.g. network save).
     */
    fun onAction(action: GameAction) {
        when (action) {
            is GameAction.NewGame -> startGame(action)
            is GameAction.Submit -> submitIntent(action)
            is GameAction.PlayBestMove -> autoPlayer.playBestMove()
            is GameAction.SendChat -> sendChat(action)
            is GameAction.MarkChatRead -> markChatRead()
            is GameAction.ContinueBeat -> beatAck?.complete(Unit)
        }
    }

    /**
     * DARBAR — the player tapped a chat chip. Handled off the UI thread under [darbarMutex] so it
     * never races the paced bot loop's social-fabric updates. Persists the (now longer) chat log so a
     * relaunch replays it.
     */
    private fun sendChat(action: GameAction.SendChat) {
        val cs = session ?: return
        if (!narrativeEnabled) return
        darbarChatJob?.cancel()
        darbarChatJob =
            coroutineScope.launch {
                val ui = darbarMutex.withLock { cs.applyHumanChat(action.input) }
                // Only land it if the player's own line is the freshest thing — keep the live board view.
                val shown = _state.value
                if (cs === session && shown != null) {
                    _state.value =
                        shown
                            .copy(
                                chatFeed = ui.chatFeed,
                                chatSuggestions = ui.chatSuggestions,
                                activeArcs = ui.activeArcs,
                            ).withUnread()
                }
                if (_state.value?.isGameOver != true) saveSnapshot()
            }
    }

    /** DARBAR — the player opened the Darbar; clear the unread badge. */
    private fun markChatRead() {
        val feed = _state.value?.chatFeed ?: return
        chatReadHwm = feed.maxOfOrNull { it.id } ?: chatReadHwm
        _state.value = _state.value?.copy(unreadChat = 0)
    }

    /** Cancel all coroutines; call when the ViewModel is no longer needed. */
    fun clear() {
        job?.cancel()
    }

    // ─────────────────────────── private ───────────────────────────

    private fun startGame(action: GameAction.NewGame) {
        advanceJob?.cancel() // drop any paced bot round still running from a prior game
        adviceJob?.cancel() // drop any decision-coach computation from a prior game
        narrationJob?.cancel() // drop any Munshi narration computation from a prior game
        autoPlayer.cancelSpectate() // M6e: drop any spectator auto-play from a prior game
        // M6b: fresh match → fresh decision-quality tally.
        decisionTally = MatchDecisionTally()
        tallyEmitted = false
        humanBluffsCaught = 0
        // M6c: fresh match → allow a new completed-match record; clear the prior one.
        matchRecorded = false
        lastCompletedMatch = null
        // M6e TEAM KHEL — split the seats into [teamCount] teams (last-team-standing) when requested.
        // teamCount < 2 → null map → classic free-for-all (byte-for-byte unchanged engine path).
        val teams = TeamAssignment.build(action.playerCount, action.teamCount)
        // DRAFT variant — start from the hand-picked deck if one was chosen (fall back to classic scaling
        // if it can't form a viable deck for this seat count). ANARCHY + TEAMS layer on additively.
        val baseConfig =
            action.draftRoles
                ?.takeIf { it.size >= GameConfig.MIN_ACTIVE_ROLES }
                ?.let { runCatching { GameConfig.drafted(action.playerCount, it) }.getOrNull() }
                ?: GameConfig.forPlayers(action.playerCount)
        val config =
            baseConfig.copy(
                teams = teams ?: baseConfig.teams,
                anarchy = action.anarchy,
                // Variant flags — all false by default (classic behavior unchanged).
                bailEnabled = action.bailEnabled,
                sabotageEnabled = action.sabotageEnabled,
                hawalaEnabled = action.hawalaEnabled,
                emergencyEnabled = action.emergencyEnabled,
                khazanaEnabled = action.khazanaEnabled,
                khazanaTarget = action.khazanaTarget,
                inflationEnabled = action.inflationEnabled,
                scarcityEnabled = action.scarcityEnabled,
            )
        autoPlayer.spectator = action.spectator
        humanDisplayName = action.playerName.ifBlank { "Khiladi" }
        // DARBAR: fresh narrative state for the new match.
        narrativeEnabled = action.narrativeEnabled
        matchStoryArc = action.storyArc
        chatReadHwm = 0L
        darbarChatJob?.cancel()
        val seed = action.seed ?: Random.nextLong()
        matchSeed = seed
        matchPlayers = action.playerCount
        matchDifficulty = action.difficulty
        matchHumanCount = action.humanCount.coerceIn(1, action.playerCount)

        // Difficulty → BotDifficulty mapping.
        val botDifficulty =
            when (action.difficulty) {
                Difficulty.Easy -> BotDifficulty.EASY
                Difficulty.Medium -> BotDifficulty.MEDIUM
                Difficulty.Hard -> BotDifficulty.HARD
                Difficulty.Expert -> BotDifficulty.EXPERT
                Difficulty.Grandmaster -> BotDifficulty.GRANDMASTER
            }

        // M5 PASS-AND-PLAY: the first [humanCount] seats are humans (hot-seat); bots fill the rest.
        val humanCount = action.humanCount.coerceIn(1, config.seatCount)
        val humanSeats = (0 until humanCount).map { PlayerId(it) }.toSet()
        val botSeatCount = config.seatCount - humanCount

        // Assign one persona+policy per BOT seat.
        val assignments =
            if (botSeatCount > 0) {
                PersonaAssigner.assign(
                    seatCount = botSeatCount,
                    difficulty = botDifficulty,
                    seed = seed,
                )
            } else {
                emptyList()
            }

        // Bots occupy the seats AFTER the human block: humanCount .. seatCount-1.
        val bots = mutableMapOf<PlayerId, Policy>()
        val personaMap = mutableMapOf<PlayerId, PersonaPolicy>()
        val opponentPersonas = mutableMapOf<PlayerId, OpponentPersona>()

        assignments.forEachIndexed { index, (persona, policy) ->
            val seatId = PlayerId(humanCount + index)
            bots[seatId] = policy
            personaMap[seatId] = policy
            opponentPersonas[seatId] =
                OpponentPersona(
                    playerId = seatId,
                    name = persona.name,
                    monogram = persona.monogram,
                    seatColorArgb = persona.seatColorArgb,
                )
        }
        // PASS-AND-PLAY: give every human seat a labelled persona too, so the handoff guard + the
        // table can name "Khiladi 1 / 2 / ..." instead of a generic seat number. Single-human vs-AI
        // keeps seat 0 unlabelled (the player is "Aap").
        if (humanCount > 1) {
            humanSeats.sortedBy { it.raw }.forEachIndexed { i, seat ->
                opponentPersonas[seat] =
                    OpponentPersona(
                        playerId = seat,
                        name = "Khiladi ${i + 1}",
                        monogram = "K${i + 1}",
                        seatColorArgb = HUMAN_SEAT_COLORS[i % HUMAN_SEAT_COLORS.size],
                    )
            }
        }
        activeBotPolicies = personaMap
        matchPersonas = opponentPersonas.toMap() // M6c: capture the lineup for the completed-match record

        // DARBAR: build the social conductor for narrative mode. It reads each bot's PersonalityProfile
        // (flaws) + name, and its grudge teeth route back into the real PersonaPolicy grudge maps.
        val director: SocialDirector? =
            if (action.narrativeEnabled) {
                val seats =
                    (0 until config.seatCount).map { seat ->
                        val pid = PlayerId(seat)
                        if (seat < humanCount) {
                            SeatInfo(seat, opponentPersonas[pid]?.name ?: "Aap", personaId = null, profile = null, isHuman = true)
                        } else {
                            val pp = personaMap[pid]
                            SeatInfo(seat, pp?.persona?.name ?: "Seat $seat", pp?.persona?.id, pp?.persona?.personality, isHuman = false)
                        }
                    }
                SocialDirector(
                    seed = seed,
                    seats = seats,
                    voice = ChatVoice(language),
                    humanSeat = 0,
                    onGrudge = { holder, target, weight -> personaMap[PlayerId(holder)]?.notifyHit(PlayerId(target), weight) },
                )
            } else {
                null
            }

        val newSession =
            GameSession(
                config = config,
                seed = seed,
                humanSeats = humanSeats,
                bots = bots,
                opponentPersonas = opponentPersonas,
                socialDirector = director,
            )
        session = newSession

        val initialUi =
            if (action.resumeLog != null) {
                // RESUME: replay the human action log (+ the Darbar chat log) onto a fresh deterministic state.
                newSession.restore(
                    action.resumeLog.map { it.toEngine() },
                    action.resumeChat?.map { it.afterHumanMoves to it.toInput() } ?: emptyList(),
                )
            } else {
                newSession.start()
            }
        emitState(
            initialUi.copy(
                coachEnabled = coachEnabledFlow?.value ?: true,
                densityLayer = initialDensityLayer(densityLayerFlow),
            ),
        )
        // A resumed game re-establishes its snapshot (identical content); a fresh game writes its
        // empty-log baseline so a relaunch before the first move still finds the in-progress match.
        if (!initialUi.isGameOver) {
            saveSnapshot()
        } else {
            onSnapshot?.invoke(null)
            finishGameOver()
        }
        // If the human acts first, start coaching their opening decision immediately.
        requestAdvice(newSession)
        // M5: if the opening decision is auto-resolvable (e.g. resumed mid-forced-Coup), handle it.
        autoPlayer.maybeAutoResolve(newSession, autoPassFlow?.value == true, autoPlayForcedFlow?.value == true)
        // M6e: in spectator/demo mode, let the advisor play the opening human decision too.
        autoPlayer.maybeAutoSpectate(newSession)
    }

    private fun submitIntent(action: GameAction.Submit) {
        val currentSession = session ?: return

        // Guard: only forward intents that are legal in the state the UI is CURRENTLY showing
        // ([_state.value], not the live session — the paced advance loop below mutates the
        // session off-thread). A stale or double-tap (e.g. an action chip tapped after the turn
        // already advanced into a reaction window) is silently ignored; previously the engine
        // rejected it with an IllegalStateException that crashed the whole app.
        val shown = _state.value ?: return
        if (action.intent !in shown.legalIntents) {
            effectEmitter.emit(GameEffect.IllegalMove)
            return
        }

        // M6b ANALYTICS — grade this decision against the coach's already-computed read BEFORE the
        // move is applied (the advice describes the pre-move decision). Cheap: a list lookup over the
        // ranked advice the decision-coach produced off-thread, never a fresh search. Ungradeable
        // decisions (forced single-legal, or advice not yet landed) are simply skipped — they don't
        // count toward accuracy, so the stat stays honest.
        DecisionQuality.grade(action.intent, shown.advice)?.let { q ->
            decisionTally += q
        }

        // A real submit lands — cancel any pending auto-resolve so it can't fire a stale move.
        autoPlayer.cancelAuto()
        // DARBAR: drop any in-flight chat handling so it can't mutate the fabric under the move.
        darbarChatJob?.cancel()

        // Apply the human's own action immediately so their move + its moment lands at once.
        val humanStep =
            try {
                currentSession.applyHuman(action.intent)
            } catch (e: IllegalStateException) {
                return
            }
        feedEventsToExperts(humanStep.newEvents)
        // The human just acted — any coaching for the PREVIOUS decision is now stale.
        adviceJob?.cancel()
        emitState(humanStep.ui)
        // Persist the in-progress snapshot after every human move (the only thing replay needs).
        if (humanStep.ui.isGameOver) {
            onSnapshot?.invoke(null)
            finishGameOver() // M6b+M6c: the human's move ended the game — emit tally + record the match.
        } else {
            saveSnapshot()
        }
        // The human's move can immediately open a fresh decision on them (e.g. a reaction window
        // bounces back, or a 2p game returns straight to their turn). Coach it right away.
        if (humanStep.ui.isHumanTurn) {
            requestAdvice(currentSession)
            // M5: the bounce-back decision may itself be auto-resolvable (e.g. lose-influence with
            // one card after a self-challenge). Handle it before spinning the bot loop.
            if (autoPlayer.maybeAutoResolve(currentSession, autoPassFlow?.value == true, autoPlayForcedFlow?.value == true)) return
            // M6e: in spectator mode, the demo plays the bounce-back human decision too.
            if (autoPlayer.maybeAutoSpectate(currentSession)) return
        }

        // Then walk the bot round ONE action at a time with a readable pause between each, so the
        // player can follow who did what instead of the whole round resolving in a single jump.
        advanceJob?.cancel()
        advanceJob =
            coroutineScope.launch {
                var lastEvents = humanStep.newEvents
                while (!currentSession.awaitingHumanOrOver()) {
                    awaitBeat(lastEvents) // let the previous beat's moment breathe (or hold for a tap)
                    // DARBAR: the social fabric is touched here AND by a chat tap — serialize the two.
                    val step = darbarMutex.withLock { currentSession.stepBotOnce() } ?: break
                    feedEventsToExperts(step.newEvents)
                    emitState(step.ui)
                    lastEvents = step.newEvents
                }
                // The bot round has resolved back to the human (or the game is over). If it is the
                // human's decision now, kick off the decision-coach for it.
                if (currentSession.awaitingHumanOrOver() && _state.value?.isHumanTurn == true) {
                    requestAdvice(currentSession)
                    // M5: auto-resolve a pass-only / forced decision the bot round handed back to us.
                    autoPlayer.maybeAutoResolve(currentSession, autoPassFlow?.value == true, autoPlayForcedFlow?.value == true)
                    // M6e: in spectator mode, the demo plays the human decision the bot round handed back.
                    autoPlayer.maybeAutoSpectate(currentSession)
                }
                // The game can end during the bot round (a bot couped the human out) without the human
                // acting again — clear the resume snapshot so a relaunch doesn't offer a finished match.
                if (_state.value?.isGameOver == true) {
                    onSnapshot?.invoke(null)
                    finishGameOver() // M6b+M6c: a bot ended the game during the paced round.
                }
            }
    }

    /**
     * Cancel any in-flight coaching and compute fresh advice for [session]'s current human
     * decision OFF the main thread. When it completes, fold the advice into the shown state —
     * but only if the shown state is still the SAME decision (same session, still the human's
     * turn, identical legal moves). This guards against a stale job landing on a new decision.
     */
    private fun requestAdvice(session: GameSession) {
        adviceJob?.cancel()
        adviceJob =
            coroutineScope.launch {
                val advice = session.adviseHuman()
                if (advice.isEmpty()) return@launch
                // Only apply if we're still on the very decision this advice was computed for:
                // same session, still the human's turn, advice not already set, and the advice
                // covers exactly the currently-shown legal moves (order-independent membership).
                val shown = _state.value
                if (session === this@GameViewModel.session &&
                    shown != null &&
                    shown.isHumanTurn &&
                    shown.advice.isEmpty() &&
                    shown.legalIntents.toSet() == advice.map { it.intent }.toSet()
                ) {
                    emitState(shown.copy(advice = advice))
                }
            }
    }

    /**
     * MUNSHI (Track 3, spec §8.1) — cancel any in-flight narration and ask for a fresh line for the
     * beat just published in [ui]. Per spec §8.6's latency rule, [ui]'s templated headline has
     * ALREADY rendered synchronously by the time this fires (see
     * [com.kursi.feature.game.overlays.headlineFor]); this only ever upgrades it in place if a
     * nicer line lands before the NEXT beat cancels it. Staleness guard: [GameUiState.recentEvents]
     * is a fresh list built per-beat ([com.kursi.feature.game.session.GameSession]), so reference
     * equality on it — rather than the whole state object, which other in-place updates (coach
     * toggle, unread badge, advice landing, ...) also `.copy()` — is exactly "still the same beat".
     *
     * DISPLAY-ONLY (spec §8.6): the result only ever lands in the ephemeral
     * [GameUiState.narrationText]/[GameUiState.narrationStreaming] fields — never `humanIntentLog`,
     * never `GameState`, never a legal-action gate, and never the replay record.
     *
     * STREAMING + CANCELLATION: [MunshiNarrator.narrate] is a [kotlinx.coroutines.flow.Flow] that
     * emits the line as it grows; `narrationJob?.cancel()` above cancels the collecting coroutine,
     * which (per [MunshiNarrator.narrate]'s own kdoc) tears down the in-flight generation too — not
     * just this ViewModel's wait for it.
     */
    private fun requestNarration(ui: GameUiState) {
        narrationJob?.cancel()
        narrationJob =
            coroutineScope.launch {
                munshi.narrate(ui.view, ui.recentEvents).collect { line ->
                    val shown = _state.value
                    if (shown != null && shown.recentEvents === ui.recentEvents) {
                        _state.value = shown.copy(narrationText = line, narrationStreaming = true)
                    }
                }
                // The flow above ran to natural completion (a cancelled job never reaches here) —
                // this beat's line, if any landed, is settled: no more tokens will arrive for it.
                val shown = _state.value
                if (shown != null && shown.recentEvents === ui.recentEvents) {
                    _state.value = shown.copy(narrationStreaming = false)
                }
            }
    }

    /** Three-tier rhythm: dramatic beats linger, declared actions are readable, bookkeeping is brisk. */
    private fun pauseFor(events: List<GameEvent>): Long {
        val base =
            when (tierFor(events)) {
                BeatTier.DRAMATIC -> DRAMATIC_STEP_MS
                BeatTier.ROUTINE -> ROUTINE_STEP_MS
                BeatTier.TRIVIAL -> TRIVIAL_STEP_MS
            }
        // M5 TURN-SPEED: scale the pacing by the live multiplier (SLOW 1.4× / NORMAL 1.0× / FAST 0.5×),
        // clamped so even FAST keeps a readable floor and SLOW never drags past ~3s.
        return (base * speedMultiplier).toLong().coerceIn(400L, 6000L)
    }

    /**
     * Pace the gap before the NEXT bot step. In ANALYST — or when no density-layer flow is wired
     * (tests / render harness) — this is exactly today's timed [delay]. In FOCUS/GUIDED a non-trivial
     * beat instead surfaces a [PendingBeat] and SUSPENDS until [GameAction.ContinueBeat] completes
     * [beatAck]. Trivial beats always flow (no tap for a bare income tick).
     *
     * ONLINE (Track 6) will additionally complete [beatAck] from a server ack-timeout so one player
     * cannot stall the table; the suspension point here is that hook.
     */
    private suspend fun awaitBeat(events: List<GameEvent>) {
        val layer = _state.value?.densityLayer ?: DensityLayer.ANALYST
        val gated =
            (layer == DensityLayer.FOCUS || layer == DensityLayer.GUIDED) &&
                tierFor(events) != BeatTier.TRIVIAL
        if (!gated) {
            delay(pauseFor(events))
            return
        }
        val ack = CompletableDeferred<Unit>()
        beatAck = ack
        _state.value = _state.value?.copy(pendingBeat = PendingBeat(tierFor(events)))
        try {
            ack.await()
        } finally {
            beatAck = null
            _state.value = _state.value?.copy(pendingBeat = null)
        }
    }

    /**
     * Feed recent game events to each bot's underlying [ExpertPolicy.observe] so
     * the belief model tracks claim/reveal history.
     */
    private fun feedEventsToExperts(events: List<GameEvent>) {
        // M6b: tally the HUMAN's caught bluffs as they reveal (seat 0 challenged, hadRole=false).
        for (e in events) {
            if (e is GameEvent.ChallengeRevealed && e.player == humanSeat && !e.hadRole) {
                humanBluffsCaught++
            }
        }
        activeBotPolicies.values.forEach { personaPolicy ->
            val turnNumber = _state.value?.view?.turnNumber ?: 0
            when (val base = personaPolicy.base) {
                is ExpertPolicy -> events.forEach { base.observe(it, turnNumber) }
                is GrandmasterPolicy -> events.forEach { base.observe(it, turnNumber) }
                else -> { /* non-search tiers keep their own memory via PersonaPolicy.observe */ }
            }
        }
    }
}

/** Initial density layer for a fresh match: the flow's value, or ANALYST when no flow is wired. */
private fun initialDensityLayer(flow: StateFlow<DensityLayer>?): DensityLayer = flow?.value ?: DensityLayer.ANALYST
