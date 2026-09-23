package com.kursi.feature.game

import com.kursi.designsystem.audio.GaddiSound
import com.kursi.engine.Action
import com.kursi.engine.GameEvent

// ═══════════════════════════════════════════════════════════════════════════════
// GameSound.kt — the pure engine GameEvent -> GaddiSound mapping (docs/experience-assets.md §1
// beat -> sound map). Fires on the SAME events [mapEventToMoment] (GameMoments.kt) turns into
// visual moments, so SFX and theatre are always in lockstep. Kept as its own pure function
// (rather than folded into GaddiMoment) so it's unit-testable standalone and the sound layer
// never depends on presentation-only GaddiMoment types.
//
// Events with no entry in the finalized 17-clip manifest (Block, PlayerEliminated, and the
// bookkeeping/variant events) resolve to null — no sound, matching mapEventToMoment's null cases.
// ═══════════════════════════════════════════════════════════════════════════════

/** Maps a single engine [GameEvent] to the [GaddiSound] clip it should trigger, or null. */
internal fun GameEvent.toGaddiSound(): GaddiSound? =
    when (this) {
        is GameEvent.ActionDeclared ->
            when (action) {
                Action.Income -> GaddiSound.CoinSingle
                Action.ForeignAid -> GaddiSound.CoinDouble
                Action.Tax -> GaddiSound.CoinCascade
                is Action.Steal -> GaddiSound.CoinSwipe
                Action.Exchange -> GaddiSound.CardDeal
                is Action.Assassinate -> GaddiSound.ImpactBlade
                is Action.Coup -> GaddiSound.ImpactGavel
                // Jaanch (Investigate) and the variant actions have no dedicated clip.
                is Action.Investigate -> null
                Action.BailPe, Action.Sabotage, is Action.Hawala, Action.Emergency -> null
            }

        // Challenge declared — the signature rubber-stamp SLAM.
        is GameEvent.Challenged -> GaddiSound.StampSlam

        // Challenge resolved: the claim held (SACH) vs. was a bluff (JHOOTH).
        is GameEvent.ChallengeRevealed -> if (hadRole) GaddiSound.StingTrue else GaddiSound.StingBluff

        // A card flips face-up — paper-slap / hard card flip.
        is GameEvent.InfluenceLost -> GaddiSound.CardPlaceHard

        // Turn pass — soft UI tick.
        is GameEvent.TurnAdvanced -> GaddiSound.UiTap

        // Win the Gaddi — brass fanfare sting.
        is GameEvent.GameEnded -> GaddiSound.StingWin

        // No bespoke clip in the manifest for these — folded silently into the neighbouring beat.
        is GameEvent.Blocked,
        is GameEvent.CardReplaced,
        is GameEvent.ActionResolved,
        is GameEvent.ActionNegated,
        is GameEvent.CoinsChanged,
        is GameEvent.CoinsTransferred,
        is GameEvent.PlayerEliminated,
        is GameEvent.Exchanged,
        is GameEvent.Investigated,
        is GameEvent.InvestigateRedraw,
        is GameEvent.InfluenceRestored,
        is GameEvent.CoinsGifted,
        is GameEvent.EmergencyDeclared,
        is GameEvent.KhazanaWon,
        is GameEvent.DarjaReached,
        -> null
    }
