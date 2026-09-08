package com.kursi.feature.game.session

import com.kursi.ai.Policy
import com.kursi.ai.advisor.MoveAdvisor
import com.kursi.engine.GameConfig
import com.kursi.engine.GameState
import com.kursi.engine.Intent
import com.kursi.engine.PlayerId
import com.kursi.engine.legalIntents
import com.kursi.feature.game.GameUiState
import com.kursi.feature.game.OpponentPersona
import kotlinx.coroutines.runBlocking

/**
 * ReplaySession — deterministic step-by-step review of a FINISHED match (M6c §2).
 *
 * # The determinism contract
 * The engine + bots are a pure function of (seed + the human intent log): bots derive every move from
 * their redacted [com.kursi.engine.PlayerView], and all RNG is carried in [GameState]. So replaying
 * the human log onto a fresh [GameSession] reconstructs the EXACT same sequence of [GameState]s as the
 * original live game — that is the entire review/replay guarantee, and the same property [GameSession.restore]
 * relies on for resume (M3 §2).
 *
 * # What a "step" is
 * A live [GameSession] pauses control exactly at the points the HUMAN had to decide (plus the terminal
 * GameOver). Those are the meaningful review stops: the Review UI walks them with [next]/[prev]/[stepTo],
 * each yielding the [GameUiState] the human saw at that decision — redacted EXACTLY as during live play
 * via [com.kursi.engine.redact], so review respects secrecy (a reviewer never sees hidden cards they
 * couldn't see live). [humanDecisionIndices] marks which steps were genuine human decisions (vs the
 * final game-over frame), so the UI can stop on them.
 *
 * # Reconstruction (eager, deterministic)
 * On [build], we replay the log one human intent at a time onto a fresh session, snapshotting the
 * redacted [GameUiState] at the start of each human decision and then at the terminal state. Replay is
 * eager and cheap (no search on the human side — the human's moves are the recorded log; only the bots
 * between them run their policies, exactly as live). Stepping is then O(1) index lookup.
 *
 * SECRECY: every captured frame is built by [GameSession] from `redact(state, viewerSeat)`. The full
 * [GameState] sequence is captured ONLY for the determinism test ([gameStates]) — the UI never sees it.
 */
class ReplaySession private constructor(
    /** The redacted human-perspective frame at each step (decision points, then the final frame). */
    private val frames: List<ReplayFrame>,
    /** The full authoritative GameState at each step — for the determinism test only, NOT the UI. */
    private val states: List<GameState>,
    /** The match outcome (winning raw seat). */
    val winnerSeat: Int,
    /** The persona lineup for this match, by seat (for the Review UI header). */
    val personas: Map<PlayerId, OpponentPersona>,
) {
    /**
     * The advisor read of the current step's human decision (M6c §2 — advisor annotations), or null
     * when the current step is not a human decision (e.g. the terminal frame). Lazily computed once
     * per frame and cached: the advisor runs a FAIR ISMCTS read on the redacted decision view, grades
     * the recorded choice against the recommended best, and surfaces the EV gap + a voiced belief read.
     */
    fun annotationAt(target: Int): ReplayAnnotation? {
        val i = target.coerceIn(0, stepCount - 1)
        return frames[i].annotation
    }

    /** The advisor annotation for the CURRENT step, or null on a non-decision frame. */
    fun currentAnnotation(): ReplayAnnotation? = frames[index].annotation

    /** Number of reviewable steps (human-decision frames + the terminal frame). Always ≥ 1. */
    val stepCount: Int get() = frames.size

    /** The current step index, clamped to [0, stepCount). Starts at 0. */
    var index: Int = 0
        private set

    /**
     * The step indices that were genuine HUMAN decisions (the Review UI stops on these). Excludes the
     * terminal game-over frame, which is a result frame rather than a decision.
     */
    val humanDecisionIndices: List<Int> =
        frames.indices.filter { frames[it].isHumanDecision }

    /** The [GameUiState] at the current [index]. */
    fun current(): GameUiState = frames[index].ui

    /** Jump to [target] (clamped) and return its [GameUiState]. */
    fun stepTo(target: Int): GameUiState {
        index = target.coerceIn(0, stepCount - 1)
        return frames[index].ui
    }

    /** Advance one step (no-op at the end) and return the new [GameUiState]. */
    fun next(): GameUiState = stepTo(index + 1)

    /** Go back one step (no-op at the start) and return the new [GameUiState]. */
    fun prev(): GameUiState = stepTo(index - 1)

    /** True when the current step is a human decision (vs the terminal frame). */
    fun isHumanDecisionStep(): Boolean = frames[index].isHumanDecision

    /**
     * The full authoritative [GameState] at each step, in order. EXPOSED FOR THE DETERMINISM TEST ONLY
     * (M6c §3): replaying a recorded match must reproduce the same GameState sequence as the original
     * live run. The UI must never read this — it gets the redacted [current]/[frames] instead.
     */
    fun gameStates(): List<GameState> = states

    /**
     * One captured review step: the redacted frame, whether it was a human decision, and (for a
     * decision frame) the advisor's read of that exact moment — what was played vs the recommended
     * best, the EV gap, and the voiced belief read. The annotation is null on the terminal frame.
     */
    private data class ReplayFrame(
        val ui: GameUiState,
        val isHumanDecision: Boolean,
        val annotation: ReplayAnnotation? = null,
    )

    companion object {
        /**
         * The advisor budget for review annotations. A whole finished game gets annotated at build time
         * (one read per human decision), so this is leaner than the live [com.kursi.ai.ADVICE_BUDGET].
         *
         * REPRODUCIBILITY: this is purely ITERATION-bounded (no wall-clock cap) so a replay annotates
         * IDENTICALLY every time it is opened, regardless of machine load — that is the whole promise of a
         * deterministic replay. A time cap (as the live coach uses for responsiveness) would let the ISMCTS
         * complete a different iteration count under CPU pressure and yield non-reproducible reads.
         */
        val REVIEW_ADVICE_BUDGET =
            com.siddharth.kmp.botspolicy.SearchBudget(
                maxMillis = Long.MAX_VALUE,
                maxIterations = 900,
                rolloutHorizon = 10,
            )

        /**
         * Reconstruct a [ReplaySession] from a [snapshot] (seed + human log) plus the match identity.
         * [botFor] supplies the SAME [Policy] per bot seat that played live — pass the exact policies
         * the match used (e.g. rebuilt from the seed via the persona assigner) so the replay is
         * deterministic. [personas] is the display lineup; [winnerSeat] the recorded outcome.
         */
        fun build(
            snapshot: MatchSnapshot,
            humanSeats: Set<PlayerId>,
            bots: Map<PlayerId, Policy>,
            winnerSeat: Int,
            personas: Map<PlayerId, OpponentPersona> = emptyMap(),
            /**
             * The advisor's search budget for the per-decision annotations. Defaults to a SHORTER
             * review budget than live coaching so reconstructing a whole game's annotations stays snappy
             * (a dozen decisions × this budget). The read is still fair — just a touch coarser.
             */
            adviceBudget: com.siddharth.kmp.botspolicy.SearchBudget = REVIEW_ADVICE_BUDGET,
        ): ReplaySession {
            val config = GameConfig.forPlayers(snapshot.players)
            val log: List<Intent> = snapshot.humanLog.map { it.toEngine() }

            val frames = ArrayList<ReplayFrame>()
            val states = ArrayList<GameState>()

            // Replay the human log one intent at a time. Before EACH human intent we snapshot the
            // decision frame the human faced; the bots between decisions auto-advance inside the
            // session exactly as they did live (deterministic from the redacted view).
            val session =
                GameSession(
                    config = config,
                    seed = snapshot.seed,
                    humanSeats = humanSeats,
                    bots = bots,
                    opponentPersonas = personas,
                )
            // FAIR advisor read for the annotations. It redacts internally (never peeks at hidden
            // cards), so the annotation is exactly the read the human could have had live — teach by
            // review. Seeded off the match seed so a given recorded game always annotates identically.
            val advisor = MoveAdvisor(seed = snapshot.seed, adviceBudget = adviceBudget)
            // The canonical "you" seat — the lowest human seat — is whose decisions we annotate.
            val youSeat = humanSeats.minByOrNull { it.raw } ?: PlayerId(0)

            var ui = session.start()
            for (intent in log) {
                if (ui.isGameOver) break
                // Capture the human-decision frame the player saw before submitting this intent.
                if (ui.isHumanTurn) {
                    val fullState = session.snapshotState()
                    // Only annotate the canonical "you" seat's genuine choices (≥2 legal options). A
                    // forced single-legal move is not a decision worth grading.
                    val seat = ui.activeSeat?.let { PlayerId(it) } ?: youSeat
                    val annotation =
                        if (seat == youSeat) {
                            // ponytail: compute() is suspend (MoveAdvisor.advise is now cancellable
                            // mid-search for the live coach). Replay build has no "player moved on"
                            // moment to cancel on — it runs once per recorded decision to a fixed,
                            // uncapped-time REVIEW_ADVICE_BUDGET — so runBlocking here is zero
                            // behavior change from when compute() was a plain function.
                            runBlocking {
                                ReplayAnnotation.compute(
                                    advisor = advisor,
                                    state = fullState,
                                    seat = seat,
                                    legal = legalIntents(fullState, seat),
                                    chosen = intent,
                                    personas = personas,
                                )
                            }
                        } else {
                            null
                        }
                    frames.add(ReplayFrame(ui, isHumanDecision = true, annotation = annotation))
                    states.add(fullState)
                }
                if (intent !in session.currentUiState().legalIntents) {
                    // Defensive: a corrupt/old record whose log no longer fits — stop the replay here
                    // rather than throwing. The frames captured so far remain reviewable.
                    break
                }
                ui = session.submitHuman(intent)
            }
            // Terminal frame: the final state after the last logged move (game-over, or the furthest
            // reconstructable point). Marked as NOT a human decision so the UI treats it as a result.
            frames.add(ReplayFrame(ui, isHumanDecision = false))
            states.add(session.snapshotState())

            return ReplaySession(
                frames = frames,
                states = states,
                winnerSeat = winnerSeat,
                personas = personas,
            )
        }
    }
}
