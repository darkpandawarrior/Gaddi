package com.kursi.feature.game.session

import com.kursi.feature.game.GameAction
import com.kursi.feature.game.GameUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * M5 AUTO-MODE / M6e TAMASHA — the auto-play, auto-resolve, and spectator-demo machinery
 * extracted from [com.kursi.feature.game.GameViewModel] (which delegates to it) to keep the
 * VM under detekt's per-class function ceiling. Reads the live [GameViewModel] internals it
 * needs via the constructor callbacks rather than holding a reference to the VM itself.
 */
class AutoPlayer(
    private val session: () -> GameSession?,
    private val shown: () -> GameUiState?,
    private val submit: (GameAction.Submit) -> Unit,
    private val scope: CoroutineScope,
    private val speed: () -> Float,
) {
    /** M6e TAMASHA / DEMO — when true, the advisor auto-plays the human seat(s) so the whole game runs itself. */
    var spectator: Boolean = false

    /** The in-flight auto-mode resolve loop (auto-pass / auto-forced). Cancelled on each new submit. */
    private var autoJob: Job? = null

    /** M6e: the in-flight spectator auto-play job (plays the human seat's best move). */
    private var spectateJob: Job? = null

    /** A real submit lands — cancel any pending auto-resolve so it can't fire a stale move. */
    fun cancelAuto() {
        autoJob?.cancel()
    }

    /** Drop any in-flight spectator auto-play (e.g. from a prior game). */
    fun cancelSpectate() {
        spectateJob?.cancel()
    }

    /**
     * M5 ASSISTANT — resolve the best move off-thread and submit it like a tap. No-op if it is not a
     * human's turn or the game is over. Reuses the same legality-guarded submit path.
     */
    fun playBestMove() {
        val currentSession = session() ?: return
        if (shown()?.isHumanTurn != true) return
        scope.launch {
            val best = currentSession.bestHumanMove() ?: return@launch
            // Re-check on the main flow: the decision must still be live and the move still legal.
            val s = shown() ?: return@launch
            if (s.isHumanTurn && best in s.legalIntents) {
                submit(GameAction.Submit(best))
            }
        }
    }

    /**
     * M6e TAMASHA / DEMO. In spectator mode the advisor plays the human seat(s) too, so the whole game
     * unfolds on the real table as a watch-only demo. When control rests on a human decision, this
     * resolves the single best move OFF the main thread (reusing [GameSession.bestHumanMove], exactly
     * like [playBestMove]) after a paced beat, then submits it like a tap — which kicks the normal bot
     * advance loop and eventually hands control back to a human seat, re-triggering this. The paced
     * delay respects the live turn-speed multiplier so the demo is watchable. A no-op outside spectator
     * mode, when it is not a human's turn, or when the game is over. Returns true if it scheduled a play.
     */
    fun maybeAutoSpectate(currentSession: GameSession): Boolean {
        if (!spectator) return false
        if (shown()?.isHumanTurn != true) return false
        if (shown()?.isGameOver == true) return false
        spectateJob?.cancel()
        spectateJob =
            scope.launch {
                // Let the player watch the table settle before the demo "thinks" and acts.
                delay((ROUTINE_STEP_MS * speed()).toLong().coerceIn(MIN_SPECTATE_DELAY_MS, MAX_SPECTATE_DELAY_MS))
                val best = currentSession.bestHumanMove() ?: return@launch
                // bestHumanMove() is now a cancellable suspend search, so a cancel() from a newer
                // maybeAutoSpectate call already stops it mid-flight in the common case. This check
                // stays as a belt-and-braces guard for the case where bestMove() returned without ever
                // hitting a suspension point (e.g. a single legal move) so cancellation never fired.
                coroutineContext.ensureActive()
                // Re-check on the shown state: still this session, still the human's turn, still legal.
                val s = shown() ?: return@launch
                if (currentSession === session() && s.isHumanTurn && best in s.legalIntents) {
                    submit(GameAction.Submit(best))
                }
            }
        return spectateJob?.isActive == true
    }

    /**
     * M5 AUTO-MODE. When control rests on a human decision that the session deems auto-resolvable
     * AND the matching preference ([autoPass] / [autoForced]) is on, play it for them after a brief
     * readable beat. Loops so a chain of forced/pass-only decisions clears in one go (e.g.
     * forced-Coup → lose-influence). A no-op in pass-and-play (auto-resolving one hot-seat player's
     * choice would rob them of agency) and whenever no preference is enabled. Returns true if it
     * scheduled an auto-resolve.
     */
    fun maybeAutoResolve(
        currentSession: GameSession,
        autoPass: Boolean,
        autoForced: Boolean,
    ): Boolean {
        // Never auto-act for a hot-seat human in pass-and-play — each human keeps full agency.
        if (currentSession.isPassAndPlay) return false
        if (!autoPass && !autoForced) return false
        if (shown()?.isHumanTurn != true) return false

        autoJob?.cancel()
        autoJob =
            scope.launch {
                while (currentSession.awaitingHumanOrOver() && shown()?.isHumanTurn == true) {
                    val decision = currentSession.autoDecision() ?: break
                    val allowed =
                        when (decision.kind) {
                            GameSession.AutoKind.ONLY_PASS -> autoPass
                            GameSession.AutoKind.SINGLE_LEGAL,
                            GameSession.AutoKind.FORCED_COUP,
                            -> autoForced
                        }
                    if (!allowed) break
                    // Let the player register what they're being relieved of before it fires.
                    delay((AUTO_RESOLVE_BASE_MS * speed()).toLong().coerceIn(MIN_AUTO_RESOLVE_DELAY_MS, MAX_AUTO_RESOLVE_DELAY_MS))
                    // Bail if the situation changed under us (race with the advance loop).
                    val s = shown() ?: break
                    if (!s.isHumanTurn || decision.intent !in s.legalIntents) break
                    submit(GameAction.Submit(decision.intent))
                    // submit() kicks its own advance loop; yield so it can settle, then re-check.
                    return@launch
                }
            }
        return autoJob?.isActive == true
    }

    private companion object {
        /** A declared action that actually lands (tax / steal / assassinate / exchange / block): give time to absorb. */
        const val ROUTINE_STEP_MS = 2400L

        /** Clamp floor/ceiling for the spectator-demo's pre-move pause (readable even at FAST speed / SLOW speed). */
        const val MIN_SPECTATE_DELAY_MS = 800L
        const val MAX_SPECTATE_DELAY_MS = 4800L

        /** Base delay before an auto-resolved decision fires, and its speed-scaled clamp floor/ceiling. */
        const val AUTO_RESOLVE_BASE_MS = 1200L
        const val MIN_AUTO_RESOLVE_DELAY_MS = 400L
        const val MAX_AUTO_RESOLVE_DELAY_MS = 2800L
    }
}
