package com.kursi.feature.game.session

import com.kursi.ai.advisor.MoveAdvice
import com.kursi.ai.advisor.MoveAdvisor
import com.kursi.engine.Action
import com.kursi.engine.GameState
import com.kursi.engine.Intent
import com.kursi.engine.PhaseView
import com.kursi.engine.PlayerId
import com.kursi.engine.PlayerView
import com.kursi.engine.ReactionStep
import com.kursi.engine.Role
import com.kursi.engine.Rules
import com.kursi.engine.redact
import com.kursi.feature.game.OpponentPersona
import kotlin.math.roundToInt

/**
 * ReplayAnnotation — the advisor's read of ONE human decision in a replayed match (M6c §2).
 *
 * This is teach-by-review: at each human-decision frame the [ReplaySession] runs the FAIR
 * [MoveAdvisor] on that exact moment (redacted — it never peeks at hidden cards, exactly as live),
 * grades the move the human actually played against the advisor's recommended best, and surfaces:
 *  - [playedLabel] / [bestLabel] — what was played vs what the coach would have played,
 *  - [evGapPct] — the win-probability the choice bled vs the best move (0 when it WAS the best),
 *  - [verdict] — a coarse grade ([Verdict]) used to colour the annotation chrome,
 *  - [beliefHinglish] / [beliefEnglish] — the voiced belief read of the moment, in identity
 *    ("Teen NETA hisaab mein the — lalkaar faayde ka tha" / "All 3 NETA were accounted for — the
 *    challenge was favourable").
 *
 * Pure data — no Compose. The UI layer picks the language line and renders the chrome.
 */
data class ReplayAnnotation(
    /** Short label of the move the human actually played (e.g. "GHOTALA (NETA claim)", "Challenge"). */
    val playedLabel: String,
    /** Short label of the advisor's recommended best move at this moment. */
    val bestLabel: String,
    /** True when the human played the advisor's recommended best. */
    val matchedBest: Boolean,
    /** Win-probability lost vs the best move, as a whole percent in [0, 100]. 0 when it was the best. */
    val evGapPct: Int,
    /** Coarse grade of the decision, for the annotation's colour + headline. */
    val verdict: Verdict,
    /** The voiced belief read of the moment — Hinglish (deadpan sarkari). */
    val beliefHinglish: String,
    /** The voiced belief read of the moment — English. */
    val beliefEnglish: String,
) {
    /** Coarse grade of a reviewed decision — drives the annotation colour + the one-word headline. */
    enum class Verdict { SHARP, FINE, LOOSE, COSTLY }

    companion object {
        /**
         * Compute the annotation for the human's [chosen] move at [state] (full authoritative state).
         * [advisor] is a FAIR, internally-redacting [MoveAdvisor] reused across the replay (so the read
         * is deterministic and never peeks). [personas] names seats for the voiced read. Returns null
         * only when the move is ungradeable (no advice / chosen not legal here).
         *
         * `suspend`: [MoveAdvisor.advise] is now cancellable mid-search. Review annotation has no
         * "the player moved on" moment to cancel on (it runs once, to a fixed [REVIEW_ADVICE_BUDGET]
         * uncapped-time budget, while building a whole replay) — [ReplaySession.build] calls this via
         * `runBlocking`, unchanged behavior from before this was suspend.
         */
        suspend fun compute(
            advisor: MoveAdvisor,
            state: GameState,
            seat: PlayerId,
            legal: List<Intent>,
            chosen: Intent,
            personas: Map<PlayerId, OpponentPersona>,
        ): ReplayAnnotation? {
            if (legal.isEmpty()) return null
            val view = redact(state, seat)
            val advice = advisor.advise(state, seat, legal)
            val chosenAdvice = advice.firstOrNull { it.intent == chosen } ?: return null
            val best =
                advice.firstOrNull { it.recommended }
                    ?: advice.maxByOrNull { it.winProb }
                    ?: return null

            val matched = chosenAdvice.intent == best.intent
            val evGap = (best.winProb - chosenAdvice.winProb).coerceAtLeast(0.0)
            val evGapPct = (evGap * 100.0).roundToInt().coerceIn(0, 100)

            val verdict =
                when {
                    matched -> Verdict.SHARP
                    evGapPct <= 4 -> Verdict.FINE
                    evGapPct <= 12 -> Verdict.LOOSE
                    else -> Verdict.COSTLY
                }

            val (hi, en) = beliefRead(chosen, chosenAdvice, view, personas)

            return ReplayAnnotation(
                playedLabel = labelOf(chosen, view, personas),
                bestLabel = labelOf(best.intent, view, personas),
                matchedBest = matched,
                evGapPct = evGapPct,
                verdict = verdict,
                beliefHinglish = hi,
                beliefEnglish = en,
            )
        }

        // ── voiced belief read ────────────────────────────────────────────────────

        /**
         * The belief read of the moment, in identity. For a Challenge it counts how many copies of the
         * challenged role were ACCOUNTED FOR (visible face-up + in the viewer's own hand) and frames the
         * challenge as favourable / a long shot from the advisor's [successOdds]. For a bluff action it
         * names the bluff and its risk; for a truthful claim it confirms the held role; otherwise it
         * falls back to the advisor's own one-line rationale.
         */
        private fun beliefRead(
            chosen: Intent,
            advice: MoveAdvice,
            view: PlayerView,
            personas: Map<PlayerId, OpponentPersona>,
        ): Pair<String, String> {
            if (chosen is Intent.Challenge) {
                val role = challengedRole(view)
                val odds = advice.successOdds
                val favourable = (odds ?: 0.0) >= 0.5
                if (role != null) {
                    val accounted = accountedFor(view, role)
                    val copies = view.config.copiesPerRole
                    val roleHi = roleHinglish(role)
                    val nHi = numberHinglish(accounted)
                    val tail = if (favourable) "lalkaar faayde ka tha" else "lalkaar jokhim bhara tha"
                    val tailEn = if (favourable) "the challenge was favourable" else "the challenge was a long shot"
                    val hi = "$nHi $roleHi hisaab mein the — $tail."
                    val en = "All $accounted of $copies $role accounted for — $tailEn."
                    return hi to en
                }
                val tail = if (favourable) "lalkaar faayde ka tha" else "lalkaar jokhim bhara tha"
                val tailEn = if (favourable) "the challenge was favourable" else "the challenge was a long shot"
                return "Daawe pe shak — $tail." to "Doubting the claim — $tailEn."
            }

            // Bluff action / block — named bluff + its risk.
            if (advice.bluff) {
                val role = advice.intent.let { claimedRoleOf(it) }
                val safe = advice.successOdds
                val roleHi = role?.let { roleHinglish(it) } ?: "patta"
                val roleEn = role?.name ?: "the card"
                return if (safe != null) {
                    val pct = (safe * 100).roundToInt()
                    "$roleHi haath mein nahi tha — jhaansa. ~$pct% bina lalkaar bach jaata." to
                        "Did not hold $roleEn — a bluff. ~$pct% it slips through unchallenged."
                } else {
                    "$roleHi haath mein nahi tha — saaf jhaansa." to
                        "Did not hold $roleEn — a clean bluff."
                }
            }

            // Truthful claim — confirm the held role.
            if (advice.truthful == true) {
                val role = claimedRoleOf(advice.intent)
                val roleHi = role?.let { roleHinglish(it) } ?: "patta"
                val roleEn = role?.name ?: "the card"
                return "$roleHi sach mein tha — daawa pakka, surakshit." to
                    "Genuinely held $roleEn — the claim was real and safe."
            }

            // No-claim move — lean on the advisor's own rationale, lightly localised.
            val en = advice.rationale
            return en to en
        }

        /** The role being challenged in the current reaction window, if the view is in one. */
        private fun challengedRole(view: PlayerView): Role? {
            val ph = view.phase as? PhaseView.Reactions ?: return null
            return when (ph.step) {
                ReactionStep.CHALLENGE_ACTION -> ph.claimedRole
                ReactionStep.CHALLENGE_BLOCK -> ph.blockRole
                else -> null
            }
        }

        /**
         * How many copies of [role] are ACCOUNTED FOR from the viewer's seat: every face-up (eliminated /
         * revealed) copy on the table plus the copies in the viewer's own hand. The more accounted for,
         * the fewer remain hidden for an opponent to truthfully hold — the heart of the challenge read.
         */
        private fun accountedFor(
            view: PlayerView,
            role: Role,
        ): Int {
            val faceUpOpp = view.players.sumOf { p -> p.faceUpRoles.count { it == role } }
            val myFaceUp = view.myFaceUp.count { it == role }
            val myHand = view.myInfluence.count { it == role }
            return faceUpOpp + myFaceUp + myHand
        }

        // ── labels ────────────────────────────────────────────────────────────────

        private fun claimedRoleOf(intent: Intent): Role? =
            when (intent) {
                is Intent.DeclareAction -> Rules.claimedRole(intent.action)
                is Intent.Block -> intent.role
                else -> null
            }

        /** A short, identity-flavoured label for a move — the sarkari action name + any role claim. */
        private fun labelOf(
            intent: Intent,
            view: PlayerView,
            personas: Map<PlayerId, OpponentPersona>,
        ): String =
            when (intent) {
                is Intent.DeclareAction ->
                    when (val a = intent.action) {
                        Action.Income -> "DEHAADI (+1)"
                        Action.ForeignAid -> "FDI (+2)"
                        Action.Tax -> "GHOTALA (NETA)"
                        Action.Exchange -> "SETTING (JUGAADU)"
                        is Action.Coup -> "KHELA → ${seatName(a.target, view, personas)}"
                        is Action.Assassinate -> "SUPARI → ${seatName(a.target, view, personas)}"
                        is Action.Steal -> "VASOOLI → ${seatName(a.target, view, personas)}"
                        is Action.Investigate -> "JAANCH → ${seatName(a.target, view, personas)}"
                        Action.BailPe -> "BAIL PE BAHAR"
                        Action.Sabotage -> "BALI KHEL"
                        is Action.Hawala -> "HAWALA → ${seatName(a.to, view, personas)}"
                        Action.Emergency -> "ADHYADESH"
                    }
                is Intent.Block -> "Block (${intent.role})"
                is Intent.Challenge -> "Lalkaar / Challenge"
                is Intent.Pass -> "Pass"
                is Intent.ChooseInfluenceToLose -> "Patta gira diya"
                is Intent.ChooseExchange -> "Patte rakhe"
                is Intent.ResolveInvestigate -> if (intent.forceRedraw) "Force redraw" else "Leave card"
            }

        private fun seatName(
            id: PlayerId,
            view: PlayerView,
            personas: Map<PlayerId, OpponentPersona>,
        ): String =
            personas[id]?.name
                ?: view.players.firstOrNull { it.id == id }?.let { "Seat ${it.seatIndex}" }
                ?: "P${id.raw}"

        private fun roleHinglish(role: Role): String = role.name // role names already read as the cast

        private fun numberHinglish(n: Int): String =
            when (n) {
                0 -> "Ek bhi nahi"
                1 -> "Ek"
                2 -> "Do"
                3 -> "Teen"
                4 -> "Chaar"
                5 -> "Paanch"
                else -> n.toString()
            }
    }
}
