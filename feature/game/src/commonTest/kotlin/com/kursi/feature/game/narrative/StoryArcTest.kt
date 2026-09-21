package com.kursi.feature.game.narrative

import com.kursi.ai.social.CharacterFlaw
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pure unit tests for [StoryArcs] — begin / reply produce the expected [ArcStep] content.
 *
 * StoryArcs has NO side effects, no RNG, and no engine access; every assertion is against
 * the returned value types ([SocialOp], [ArcBeat], [ArcState]).
 */
class StoryArcTest {
    // ── helpers ────────────────────────────────────────────────────────────────

    private fun beginAccepted(
        arc: ArcId,
        target: Int = 2,
        name: String = "TargetBot",
    ) = StoryArcs.begin(arc, target, name, accepted = true)

    private fun beginRejected(
        arc: ArcId,
        target: Int = 2,
        name: String = "TargetBot",
    ) = StoryArcs.begin(arc, target, name, accepted = false)

    // ── AFWAAH ────────────────────────────────────────────────────────────────

    @Test
    fun afwaah_begin_emitsAllTableSuspicion() {
        val step = beginAccepted(ArcId.AFWAAH)
        val suspicions = step.ops.filterIsInstance<SocialOp.Suspicion>()
        val allTableSuspicion = suspicions.firstOrNull { it.observer == SocialOp.ALL }
        assertNotNull(allTableSuspicion, "AFWAAH.begin must emit a Suspicion(ALL, target)")
        assertEquals(2, allTableSuspicion.target)
        assertTrue(allTableSuspicion.delta > 0f, "suspicion delta must be positive")
    }

    @Test
    fun afwaah_begin_emitsThreatOnTarget() {
        val step = beginAccepted(ArcId.AFWAAH)
        val threat = step.ops.filterIsInstance<SocialOp.Threat>().firstOrNull { it.seat == 2 }
        assertNotNull(threat, "AFWAAH.begin must emit a Threat on the target seat")
        assertTrue(threat.delta > 0f, "threat delta must be positive")
    }

    @Test
    fun afwaah_begin_beatHasPlayerAndNarratorLine() {
        val step = beginAccepted(ArcId.AFWAAH)
        val playerBeat = step.beats.firstOrNull { it.fromPlayer }
        assertNotNull(playerBeat, "AFWAAH.begin must have a beat from the player")
        val narratorBeat = step.beats.firstOrNull { it.speakerSeat < 0 }
        assertNotNull(narratorBeat, "AFWAAH.begin must have a narrator/SYSTEM beat")
    }

    @Test
    fun afwaah_begin_nextStateIsAlive() {
        val step = beginAccepted(ArcId.AFWAAH)
        assertNotNull(step.nextState)
        assertFalse(step.nextState.ended, "AFWAAH arc must not be ended immediately after begin")
        assertEquals(ArcId.AFWAAH, step.nextState.arc)
    }

    @Test
    fun afwaah_begin_agitatesParanoiaOnTarget() {
        val step = beginAccepted(ArcId.AFWAAH)
        val agitate =
            step.ops
                .filterIsInstance<SocialOp.Agitate>()
                .firstOrNull { it.seat == 2 && it.flaw == CharacterFlaw.PARANOIA }
        assertNotNull(agitate, "AFWAAH.begin must agitate PARANOIA on the target")
        assertTrue(agitate.delta > 0f)
    }

    // ── GATHBANDHAN ───────────────────────────────────────────────────────────

    @Test
    fun gathbandhan_begin_accepted_emitsAllyOp() {
        val step = beginAccepted(ArcId.GATHBANDHAN, target = 3)
        val ally = step.ops.filterIsInstance<SocialOp.Ally>().firstOrNull { it.a == 0 && it.b == 3 }
        assertNotNull(ally, "GATHBANDHAN accepted must emit Ally(0, target)")
    }

    @Test
    fun gathbandhan_begin_accepted_nextStateHasAllySet() {
        val step = beginAccepted(ArcId.GATHBANDHAN, target = 3)
        assertNotNull(step.nextState)
        assertEquals(3, step.nextState.ally, "accepted GATHBANDHAN next state must carry the ally seat")
        assertFalse(step.nextState.ended)
    }

    @Test
    fun gathbandhan_begin_declined_doesNotEmitAlly() {
        val step = beginRejected(ArcId.GATHBANDHAN, target = 3)
        val allies = step.ops.filterIsInstance<SocialOp.Ally>()
        assertTrue(allies.isEmpty(), "declined GATHBANDHAN must not emit an Ally op")
    }

    @Test
    fun gathbandhan_begin_declined_marksEnded() {
        val step = beginRejected(ArcId.GATHBANDHAN, target = 3)
        assertTrue(step.nextState?.ended == true, "declined GATHBANDHAN must mark arc as ended")
    }

    @Test
    fun gathbandhan_reply_deflect_emitsBetrayAndGrudge() {
        // First begin accepted, then "knife the ally" deflect reply.
        val begun = beginAccepted(ArcId.GATHBANDHAN, target = 3)
        val arcState = begun.nextState!!
        val input =
            HumanChatInput(
                suggestionId = "test.deflect",
                kind = ChatActionKind.DEFLECT,
                arc = ArcId.GATHBANDHAN,
                targetSeat = 3,
            )
        val reply = StoryArcs.reply(arcState, input)
        val betray = reply.ops.filterIsInstance<SocialOp.Betray>().firstOrNull()
        assertNotNull(betray, "GATHBANDHAN DEFLECT reply must emit a Betray op")
        val grudge = reply.ops.filterIsInstance<SocialOp.Grudge>().firstOrNull()
        assertNotNull(grudge, "GATHBANDHAN DEFLECT reply must emit a Grudge op for the scorned ally")
        assertTrue(reply.nextState?.ended == true, "arc must end after the knife")
    }

    // ── STING ─────────────────────────────────────────────────────────────────

    @Test
    fun sting_begin_agitatesEgoAndGreed() {
        val step = beginAccepted(ArcId.STING, target = 1)
        val egoAgitate =
            step.ops
                .filterIsInstance<SocialOp.Agitate>()
                .firstOrNull { it.seat == 1 && it.flaw == CharacterFlaw.EGO }
        assertNotNull(egoAgitate, "STING.begin must agitate EGO")
        val greedAgitate =
            step.ops
                .filterIsInstance<SocialOp.Agitate>()
                .firstOrNull { it.seat == 1 && it.flaw == CharacterFlaw.GREED }
        assertNotNull(greedAgitate, "STING.begin must agitate GREED")
    }

    @Test
    fun sting_begin_beatFromPlayerAndBotResponse() {
        val step = beginAccepted(ArcId.STING, target = 1)
        val playerBeat = step.beats.firstOrNull { it.fromPlayer }
        assertNotNull(playerBeat, "STING.begin must have a player beat")
        val botBeat = step.beats.firstOrNull { it.speakerSeat == 1 }
        assertNotNull(botBeat, "STING.begin must have the flattered bot respond")
    }

    // ── BADLA ─────────────────────────────────────────────────────────────────

    @Test
    fun badla_begin_emitsTrustTowardPlayer() {
        val step = beginAccepted(ArcId.BADLA, target = 2)
        val trust =
            step.ops
                .filterIsInstance<SocialOp.Trust>()
                .firstOrNull { it.observer == 2 && it.target == 0 }
        assertNotNull(trust, "BADLA.begin must emit Trust(target→player) to win the bot over")
        assertTrue(trust.delta > 0f)
    }

    @Test
    fun badla_reply_emitsGrudgeOnRival() {
        // Approach the target first.
        val begun = beginAccepted(ArcId.BADLA, target = 2)
        val arcState = begun.nextState!!
        // Now player points the grudge at rival seat 3.
        val input =
            HumanChatInput(
                suggestionId = "badla.point.2.3",
                kind = ChatActionKind.ARC_REPLY,
                arc = ArcId.BADLA,
                targetSeat = 3, // the RIVAL we want the vengeful bot to hit
            )
        val reply = StoryArcs.reply(arcState, input)
        val grudge =
            reply.ops
                .filterIsInstance<SocialOp.Grudge>()
                .firstOrNull { it.holder == 2 && it.target == 3 }
        assertNotNull(grudge, "BADLA.reply with rival=3 must emit Grudge(holder=2, target=3)")
        assertTrue(grudge.weight > 0, "grudge weight must be positive")
        // Threat on the rival must also tick up.
        val threat = reply.ops.filterIsInstance<SocialOp.Threat>().firstOrNull { it.seat == 3 }
        assertNotNull(threat, "BADLA.reply must also raise threat on the rival")
    }

    @Test
    fun badla_reply_missingTargetSeat_isNoOp() {
        val arcState = beginAccepted(ArcId.BADLA, target = 2).nextState!!
        val input =
            HumanChatInput(
                suggestionId = "badla.noop",
                kind = ChatActionKind.ARC_REPLY,
                arc = ArcId.BADLA,
                targetSeat = null, // no rival picked → should not crash, no grudge emitted
            )
        val reply = StoryArcs.reply(arcState, input)
        // Should return the same arc state unchanged with no grudge ops.
        val grudges = reply.ops.filterIsInstance<SocialOp.Grudge>()
        assertTrue(grudges.isEmpty(), "BADLA.reply without a rival should emit no Grudge ops")
    }

    // ── ArcBeat shape / fromPlayer flag ──────────────────────────────────────

    @Test
    fun allArcs_begin_playerBeatHasFromPlayerTrue() {
        for (arc in ArcId.entries) {
            val step = beginAccepted(arc, target = 2)
            val playerBeat = step.beats.firstOrNull { it.fromPlayer }
            assertNotNull(playerBeat, "$arc begin must include a player beat with fromPlayer=true")
            assertEquals(0, playerBeat.speakerSeat, "player beat must originate from seat 0")
        }
    }

    @Test
    fun allArcs_begin_nextStateArcIdMatchesInput() {
        for (arc in ArcId.entries) {
            val step = beginAccepted(arc, target = 2)
            val next = step.nextState ?: continue // some declined arcs set ended and may return
            assertEquals(arc, next.arc, "nextState.arc must match the input ArcId for $arc")
        }
    }
}
