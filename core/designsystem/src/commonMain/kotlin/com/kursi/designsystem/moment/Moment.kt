package com.kursi.designsystem.moment

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
// Moment.kt — Pure data describing WHAT happened. No Compose, no timing.
// Design: gaddi-plan/docs/15c_action_moments.md §1.1
//
// The presentation layer maps each resolved engine event to one GaddiMoment.
// The overlay player owns all motion; this file carries only data.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * A seat ID — thin typealias over Int so call-sites stay readable.
 * The presentation layer maps engine PlayerId → SeatId before enqueuing.
 */
typealias SeatId = Int

/**
 * A role identifier string — e.g. "NETA", "BHAI", "BABU", "JUGAADU", "VAKIL".
 * Kept as String so this file has zero engine imports and stays fully self-contained.
 */
typealias RoleId = String

/**
 * A bark key — an opaque string identifying a localised voice-over line.
 * The SoundBus resolves this to an audio resource; null means no bark.
 */
typealias BarkKey = String

/**
 * Haptic intensity to fire when the moment triggers.
 * Three HeavyLong slots are reserved exactly for Coup, Elimination, Win — per spec §0.
 */
enum class HapticBeat { None, Tick, Thud, DoubleBuzz, HeavyLong }

/**
 * One on-screen moment. Emitted by the presentation reducer when an engine event resolves.
 *
 * Design rules (from 15c §0):
 * - Pure data: no Compose, no timing code — the player owns motion.
 * - [durationMs]: budget in milliseconds. 0.6s routine / 0.9–1.1s reactions / 1.4s max.
 * - [barkKey]: text-toast floor. null = no bark. VO is additive on top.
 * - [haptic]: HeavyLong reserved for Coup, Elimination, Win only.
 */
@Immutable
sealed interface GaddiMoment {
    val actorSeat: SeatId
    val durationMs: Int
    val barkKey: BarkKey?
    val haptic: HapticBeat

    // ── Economic actions (coins travel) ──────────────────────────────────────

    /**
     * Dehaadi / Income: +1 Khokha from treasury. Humblest action — no stamp.
     * Beat: single CoinTrail(treasury → actor, count=1) + +1 letterpress press-in.
     */
    @Immutable
    data class Income(
        override val actorSeat: SeatId,
        val actorName: String = "",
        override val barkKey: BarkKey? = null,
    ) : GaddiMoment {
        override val durationMs = 900
        override val haptic = HapticBeat.Tick
    }

    /**
     * FDI / Foreign Aid: +2 Khokhas from off-table edge.
     * Beat: paper-plane entry arc, 2× CoinTrail + double +2 press-in.
     */
    @Immutable
    data class ForeignAid(
        override val actorSeat: SeatId,
        val actorName: String = "",
        override val barkKey: BarkKey? = null,
    ) : GaddiMoment {
        override val durationMs = 1000
        override val haptic = HapticBeat.Tick
    }

    /**
     * Ghotala / Tax: +3 Khokhas, NETA claim.
     * Beat: RubberStamp("GHOTALA", Neta-blue) then 3× CoinTrail spill.
     * [roleHue] is GaddiRoleHues.Neta from the caller.
     */
    @Immutable
    data class Tax(
        override val actorSeat: SeatId,
        val roleHue: Color,
        val actorName: String = "",
        override val barkKey: BarkKey? = null,
    ) : GaddiMoment {
        override val durationMs = 1100
        override val haptic = HapticBeat.Tick
    }

    /**
     * Vasooli / Steal: steal 2 Khokhas from victim, BABU claim.
     * Beat: RubberStamp(victim seat, Babu-green) then 2× CoinTrail(victim→actor) yank.
     */
    @Immutable
    data class Steal(
        override val actorSeat: SeatId,
        val victim: SeatId,
        val roleHue: Color,
        val actorName: String = "",
        val victimName: String = "",
        override val barkKey: BarkKey? = null,
    ) : GaddiMoment {
        override val durationMs = 1300
        override val haptic = HapticBeat.Thud
    }

    /**
     * Supari / Assassinate: pay 3, target loses influence; BHAI claim.
     * Beat: TickerSlip(chit) slides actor→target, RubberStamp(Bhai-vermillion), hazard flash;
     *        3× CoinTrail(actor→treasury) for the fee.
     */
    @Immutable
    data class Assassinate(
        override val actorSeat: SeatId,
        val target: SeatId,
        val roleHue: Color,
        val actorName: String = "",
        val targetName: String = "",
        override val barkKey: BarkKey? = null,
    ) : GaddiMoment {
        override val durationMs = 1500
        override val haptic = HapticBeat.Thud
    }

    /**
     * Setting / Exchange: swap cards with deck; JUGAADU claim.
     * Beat: CardFlip × 2 (fan-up, riso shimmer), shuffle-ghost, interlocking-arrows stamp.
     */
    @Immutable
    data class Exchange(
        override val actorSeat: SeatId,
        val roleHue: Color,
        val actorName: String = "",
        override val barkKey: BarkKey? = null,
    ) : GaddiMoment {
        override val durationMs = 1100
        override val haptic = HapticBeat.Tick
    }

    /**
     * Khela / Coup: pay 7, unblockable. THE hero action.
     * Beat: table dim, striker-glyph flick → target, table-wide HalftoneBurst shockwave,
     *        ~150ms slow-mo hold, ChairTip begins (then flows into Elimination moment).
     * HeavyLong haptic: one of exactly three in the game.
     */
    @Immutable
    data class Coup(
        override val actorSeat: SeatId,
        val target: SeatId,
        val actorName: String = "",
        val targetName: String = "",
        override val barkKey: BarkKey? = null,
    ) : GaddiMoment {
        override val durationMs = 2000
        override val haptic = HapticBeat.HeavyLong
    }

    // ── Reaction / outcome events ─────────────────────────────────────────────

    /**
     * A block is declared and stands.
     * Beat: blocker role glyph RubberStamps over the action's ticker slip with a "no-ring";
     *        original slip dims beneath it (cause-and-effect physical cancel).
     */
    @Immutable
    data class Block(
        override val actorSeat: SeatId,
        val blockedSeat: SeatId,
        val roleHue: Color,
        val blockerName: String = "",
        val blockedName: String = "",
        override val barkKey: BarkKey? = null,
    ) : GaddiMoment {
        override val durationMs = 1200
        override val haptic = HapticBeat.Tick
    }

    /**
     * A challenge is thrown — the dare, before reveal. Suspense-only; no outcome yet.
     * Beat: torn-paper connector snaps taut from challenger → claimant; claimant shiver.
     */
    @Immutable
    data class Challenge(
        override val actorSeat: SeatId,
        val claimant: SeatId,
        val challengerName: String = "",
        val claimantName: String = "",
        override val barkKey: BarkKey? = null,
    ) : GaddiMoment {
        override val durationMs = 1100
        override val haptic = HapticBeat.Tick
    }

    /**
     * THE MONEY MOMENT — challenge resolves.
     * Beat: CardFlip (riso shimmer) → 180ms hold → verdict:
     *   truthful=true : bright RubberStamp(role glyph, roleHue) + gold HalftoneBurst;
     *   truthful=false: desaturate + red "JHOOTH!" stamp diagonal.
     * Flows into challenger InfluenceLoss (if truthful) or claimant InfluenceLoss (if caught).
     */
    @Immutable
    data class Reveal(
        override val actorSeat: SeatId,
        val claimant: SeatId,
        val claimedRole: RoleId,
        val truthful: Boolean,
        val roleHue: Color,
        val playerName: String = "",
        override val barkKey: BarkKey? = null,
    ) : GaddiMoment {
        override val durationMs = 1800
        override val haptic = if (truthful) HapticBeat.Thud else HapticBeat.DoubleBuzz
    }

    /**
     * A card is flipped & stamped EXPOSED — influence loss event.
     * Beat: CardFlip face-up → red "EXPOSED" RubberStamp diagonal + ink-bleed bloom;
     *        card desaturates −40% and tilts into "burned" rest.
     */
    @Immutable
    data class InfluenceLoss(
        override val actorSeat: SeatId,
        val lostRole: RoleId,
        val roleHue: Color,
        val playerName: String = "",
        override val barkKey: BarkKey? = null,
    ) : GaddiMoment {
        override val durationMs = 1300
        override val haptic = HapticBeat.Thud
    }

    /**
     * 0 influence — the chair tips. Wistful, never mocking.
     * Beat: ChairTip (logo wobble realized); seat-poster peels out of frame;
     *        grayscale wipe; "GADDI GAYI" band. Consolation toast.
     * HeavyLong haptic: one of exactly three in the game.
     */
    @Immutable
    data class Elimination(
        override val actorSeat: SeatId,
        val playerName: String = "",
        override val barkKey: BarkKey? = null,
    ) : GaddiMoment {
        override val durationMs = 2000
        override val haptic = HapticBeat.HeavyLong
    }

    /**
     * Turn passes to next seat. Shortest moment — frequency demands restraint.
     * Beat: gold sweep arc from old → new seat; new seat rim-pulses gold.
     * No bark, no haptic: too frequent to fatigue the player.
     */
    @Immutable
    data class TurnHandoff(
        override val actorSeat: SeatId,
        val nextSeat: SeatId,
        val nextName: String = "",
    ) : GaddiMoment {
        override val barkKey = null
        override val durationMs = 600
        override val haptic = HapticBeat.None
    }

    /**
     * Last player seated — Gaddi claimed!
     * Beat: victor seat-poster ascends to composite throne; currency-marigold confetti falls;
     *        GADDI wordmark RubberStamp dead-centre + "Gaddi aapki!" press-in tagline.
     * HeavyLong haptic: the last of the three reserved celebratory pulses.
     */
    @Immutable
    data class Win(
        override val actorSeat: SeatId,
        val winnerName: String = "",
        override val barkKey: BarkKey? = null,
    ) : GaddiMoment {
        override val durationMs = 2200
        override val haptic = HapticBeat.HeavyLong
    }
}
