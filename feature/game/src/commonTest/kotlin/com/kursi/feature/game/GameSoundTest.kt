package com.kursi.feature.game

import com.kursi.designsystem.audio.GaddiSound
import com.kursi.engine.Action
import com.kursi.engine.CardId
import com.kursi.engine.GameEvent
import com.kursi.engine.LossReason
import com.kursi.engine.PlayerId
import com.kursi.engine.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Locks in the pure GameEvent -> GaddiSound mapping (docs/experience-assets.md §1 beat -> sound
 * map). Every case here mirrors a non-null branch of [mapEventToMoment] one-for-one (same events
 * drive both the visual moment and the SFX), plus the events that have no clip in the finalized
 * 17-clip manifest.
 */
class GameSoundTest {
    private val actor = PlayerId(0)
    private val target = PlayerId(1)

    @Test
    fun income_playsCoinSingle() {
        assertEquals(GaddiSound.CoinSingle, GameEvent.ActionDeclared(actor, Action.Income, Role.NETA).toGaddiSound())
    }

    @Test
    fun foreignAid_playsCoinDouble() {
        assertEquals(GaddiSound.CoinDouble, GameEvent.ActionDeclared(actor, Action.ForeignAid, null).toGaddiSound())
    }

    @Test
    fun tax_playsCoinCascade() {
        assertEquals(GaddiSound.CoinCascade, GameEvent.ActionDeclared(actor, Action.Tax, Role.NETA).toGaddiSound())
    }

    @Test
    fun steal_playsCoinSwipe() {
        assertEquals(GaddiSound.CoinSwipe, GameEvent.ActionDeclared(actor, Action.Steal(target), Role.BABU).toGaddiSound())
    }

    @Test
    fun exchange_playsCardDeal() {
        assertEquals(GaddiSound.CardDeal, GameEvent.ActionDeclared(actor, Action.Exchange, Role.JUGAADU).toGaddiSound())
    }

    @Test
    fun assassinate_playsImpactBlade() {
        assertEquals(
            GaddiSound.ImpactBlade,
            GameEvent.ActionDeclared(actor, Action.Assassinate(target), Role.BHAI).toGaddiSound(),
        )
    }

    @Test
    fun coup_playsImpactGavel() {
        assertEquals(GaddiSound.ImpactGavel, GameEvent.ActionDeclared(actor, Action.Coup(target), null).toGaddiSound())
    }

    @Test
    fun investigate_hasNoClip() {
        assertNull(GameEvent.ActionDeclared(actor, Action.Investigate(target), Role.VAKIL).toGaddiSound())
    }

    @Test
    fun variantActions_haveNoClip() {
        assertNull(GameEvent.ActionDeclared(actor, Action.BailPe, null).toGaddiSound())
        assertNull(GameEvent.ActionDeclared(actor, Action.Sabotage, null).toGaddiSound())
        assertNull(GameEvent.ActionDeclared(actor, Action.Emergency, null).toGaddiSound())
    }

    @Test
    fun challengeDeclared_playsStampSlam() {
        assertEquals(GaddiSound.StampSlam, GameEvent.Challenged(actor, target, Role.NETA).toGaddiSound())
    }

    @Test
    fun challengeRevealed_true_playsStingTrue() {
        assertEquals(
            GaddiSound.StingTrue,
            GameEvent.ChallengeRevealed(actor, CardId(0), Role.NETA, hadRole = true).toGaddiSound(),
        )
    }

    @Test
    fun challengeRevealed_bluff_playsStingBluff() {
        assertEquals(
            GaddiSound.StingBluff,
            GameEvent.ChallengeRevealed(actor, CardId(0), Role.NETA, hadRole = false).toGaddiSound(),
        )
    }

    @Test
    fun influenceLost_playsCardPlaceHard() {
        assertEquals(
            GaddiSound.CardPlaceHard,
            GameEvent.InfluenceLost(actor, CardId(0), Role.NETA, LossReason.COUPED).toGaddiSound(),
        )
    }

    @Test
    fun turnAdvanced_playsUiTap() {
        assertEquals(GaddiSound.UiTap, GameEvent.TurnAdvanced(toSeat = 1, turnNumber = 2).toGaddiSound())
    }

    @Test
    fun gameEnded_playsStingWin() {
        assertEquals(GaddiSound.StingWin, GameEvent.GameEnded(actor).toGaddiSound())
    }

    @Test
    fun blockedAndEliminated_haveNoClipInTheManifest() {
        assertNull(GameEvent.Blocked(actor, Role.NETA, Action.ForeignAid).toGaddiSound())
        assertNull(GameEvent.PlayerEliminated(actor).toGaddiSound())
    }
}
