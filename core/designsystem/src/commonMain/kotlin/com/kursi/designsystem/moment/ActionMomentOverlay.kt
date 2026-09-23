package com.kursi.designsystem.moment

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════════════════
// ActionMomentOverlay.kt — the single overlay composable + FIFO queue host.
// Design: gaddi-plan/docs/15c_action_moments.md §1.2
//
// API usage (screen wiring):
//
//     val host = rememberMomentHost()
//
//     Box(Modifier.fillMaxSize()) {
//         GameTable(...)                   // your game screen content
//         ActionMomentOverlay(
//             host = host,
//             anchors = tableAnchors,      // from your layout measurement
//             reducedMotion = false,
//             onMomentDone = { /* optional */ },
//         )
//     }
//
//     // Fire a moment anywhere in your presentation logic:
//     LaunchedEffect(engineEvent) {
//         host.play(GaddiMoment.Tax(actorSeat = 0, roleHue = GaddiRoleHues.Neta))
//     }
//
// The overlay fills its parent Box but does NOT intercept input behind it
// (pointerInput only captures tap-to-skip while a moment is actively playing).
// ═══════════════════════════════════════════════════════════════════════════════

// ─────────────────────────── MomentHost ──────────────────────────────────────

/**
 * Stable handle that screens hold to enqueue moments.
 * Created via [rememberMomentHost].
 */
@Stable
class MomentHost {
    internal val queue = mutableStateListOf<GaddiMoment>()

    /** Enqueue a moment. Thread-safe from the composition context. */
    fun play(moment: GaddiMoment) {
        queue.add(moment)
    }

    /** Enqueue multiple moments in order (e.g. Reveal → InfluenceLoss chain). */
    fun playAll(vararg moments: GaddiMoment) {
        moments.forEach { queue.add(it) }
    }

    /** Clear all pending moments (e.g. on game-state reset). */
    fun clearQueue() {
        queue.clear()
    }

    /** Number of moments waiting. */
    val pending: Int get() = queue.size

    /** Whether any moment is currently queued or playing. */
    val isActive: Boolean get() = queue.isNotEmpty()
}

/**
 * Creates and remembers a [MomentHost] stable across recompositions.
 *
 * ```kotlin
 * val host = rememberMomentHost()
 * // fire a moment:
 * host.play(GaddiMoment.Income(actorSeat = 2))
 * ```
 */
@Composable
fun rememberMomentHost(): MomentHost = remember { MomentHost() }

// ─────────────────────────── momentSpec ──────────────────────────────────────

/**
 * Returns an [androidx.compose.animation.core.AnimationSpec] for the given moment.
 * Most moments use a simple tween; heroes use a keyframes-like tween with
 * the same durationMs baked in from the sealed type.
 */
private fun momentSpec(m: GaddiMoment) = tween<Float>(durationMillis = m.durationMs, easing = androidx.compose.animation.core.LinearEasing)

// ─────────────────────────── Static variant ──────────────────────────────────

/**
 * Reduced-motion static variant (TENET 6): renders a TAILORED static end-frame per
 * moment — a held stamp, a JHOOTH/SACH verdict card, a tipped chair, a GADDI crest,
 * a coin-row, etc. (see [MomentStaticFrame]). A TickerSlip is also laid in the corner
 * as the permanent accessible record, so the slip floor is never lost.
 */
@Composable
private fun StaticMomentFrame(
    moment: GaddiMoment,
    anchors: TableAnchors,
) {
    // The characterful, beat-specific frozen frame.
    MomentStaticFrame(moment = moment, anchors = anchors)

    // The ticker-slip record floor — slid fully in, top-end corner.
    val (glyph, effect) = momentToSlipContent(moment)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().padding(top = 8.dp, end = 8.dp),
        contentAlignment = androidx.compose.ui.Alignment.TopEnd,
    ) {
        TickerSlip(
            glyphText = glyph,
            effectText = effect,
            tint = momentTint(moment),
            progress = 1.0f,
        )
    }
}

/** Maps a moment to a ticker-slip glyph + effect label. */
private fun momentToSlipContent(m: GaddiMoment): Pair<String, String> =
    when (m) {
        is GaddiMoment.Income -> "INC" to "+1"
        is GaddiMoment.ForeignAid -> "FDI" to "+2"
        is GaddiMoment.Tax -> "TAX" to "+3"
        is GaddiMoment.Steal -> "STL" to "steal 2"
        is GaddiMoment.Assassinate -> "SUP" to "supari"
        is GaddiMoment.Exchange -> "EXC" to "exchange"
        is GaddiMoment.Coup -> "KHL" to "KHELA!"
        is GaddiMoment.Block -> "BLK" to "blocked"
        is GaddiMoment.Challenge -> "CHK" to "challenge!"
        is GaddiMoment.Reveal -> "REV" to if (m.truthful) "SACH" else "JHOOTH"
        is GaddiMoment.InfluenceLoss -> "EXP" to "EXPOSED"
        is GaddiMoment.Elimination -> "OUT" to "GADDI GAYI"
        is GaddiMoment.TurnHandoff -> "→" to "seat ${m.nextSeat}"
        is GaddiMoment.Win -> "WIN" to "Gaddi aapki!"
    }

/** Returns the tint color for a moment's ticker slip and static variant. */
private fun momentTint(m: GaddiMoment): androidx.compose.ui.graphics.Color =
    when (m) {
        is GaddiMoment.Tax -> m.roleHue
        is GaddiMoment.Steal -> m.roleHue
        is GaddiMoment.Assassinate -> m.roleHue
        is GaddiMoment.Exchange -> m.roleHue
        is GaddiMoment.Block -> m.roleHue
        is GaddiMoment.Reveal -> m.roleHue
        is GaddiMoment.InfluenceLoss -> m.roleHue
        is GaddiMoment.Coup,
        is GaddiMoment.Elimination,
        is GaddiMoment.Win,
        -> com.kursi.designsystem.BrandTokens.GoldAntique
        is GaddiMoment.Challenge -> com.kursi.designsystem.BrandTokens.StampRed
        else -> com.kursi.designsystem.BrandTokens.BrassAged
    }

// ─────────────────────────── ActionMomentOverlay ─────────────────────────────

/**
 * Single overlay that sits ABOVE the game table felt, BELOW the action dock.
 * Plays moments FIFO from [host]'s queue.
 *
 * - Owns the progress Animatable and drives all beat composables.
 * - [reducedMotion]: when true, collapses every moment to a 120ms crossfade to
 *   the static end-frame variant. The ticker slip + text toast always write.
 * - Tap-to-skip: tapping anywhere during a moment snaps progress to 1.0f and
 *   immediately pops the queue entry.
 * - Does NOT block input beneath it when no moment is playing (Box with no pointerInput).
 *
 * @param host           Created via [rememberMomentHost]; call [MomentHost.play] to enqueue.
 * @param anchors        TableAnchors from your layout measurement (seat centers + treasury).
 * @param reducedMotion  True if the OS/user has requested reduced motion.
 * @param soundEnabled   Master sound/haptics gate (AppPrefs.soundFlow). When false the
 *                       overlay is fully silent — no SFX, no haptic.
 * @param soundPlayer    Platform feedback sink (see [rememberSoundPlayer]). When null,
 *                       feedback is skipped (e.g. render harness / previews).
 * @param onMomentDone   Called after each moment completes (optional; e.g. for logging).
 * @param modifier       Applied to the root Box — typically Modifier.fillMaxSize().
 */
@Composable
fun ActionMomentOverlay(
    host: MomentHost,
    anchors: TableAnchors,
    reducedMotion: Boolean = false,
    soundEnabled: Boolean = false,
    soundPlayer: com.siddharth.kmp.feedback.SoundPlayer? = null,
    onMomentDone: (GaddiMoment) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Peek at the head of the queue
    val current: GaddiMoment? = host.queue.firstOrNull()

    current?.let { m ->
        // One Animatable progress 0→1 drives the WHOLE moment.
        val progress = remember(m) { Animatable(0f) }
        var skipped by remember(m) { mutableStateOf(false) }

        // Fire SFX + haptic ONCE per moment, the instant it begins playing — gated by
        // the master sound toggle. Reduced-motion does not suppress audio: the feel cue
        // still lands even when the theatre is collapsed to the static frame.
        LaunchedEffect(m) {
            if (soundEnabled && soundPlayer != null) {
                soundPlayer.fire(m)
            }
        }

        LaunchedEffect(m) {
            val spec =
                if (reducedMotion) {
                    tween<Float>(120, easing = androidx.compose.animation.core.LinearEasing)
                } else {
                    momentSpec(m)
                }
            progress.animateTo(1f, spec)
            // Pop head, notify caller
            if (host.queue.isNotEmpty()) host.queue.removeAt(0)
            onMomentDone(m)
        }

        // Skip handler: snap to end then pop immediately
        if (skipped) {
            LaunchedEffect(Unit) {
                progress.snapTo(1f)
                if (host.queue.isNotEmpty()) host.queue.removeAt(0)
                onMomentDone(m)
            }
        }

        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .pointerInput(m) {
                        detectTapGestures { skipped = true }
                    },
        ) {
            if (reducedMotion) {
                // Reduced motion: crossfade to static end-frame (120ms)
                Crossfade(
                    targetState = m,
                    animationSpec = tween(120),
                    label = "reducedMotionMoment",
                ) { moment ->
                    StaticMomentFrame(moment = moment, anchors = anchors)
                }
            } else {
                val p = progress.value
                MomentBeatContent(m = m, progress = p, anchors = anchors)
            }
        }
    }
}

// ─────────────────────────── Beat dispatcher ─────────────────────────────────

/**
 * Dispatches to the correct beat composable for the given [m].
 * This is the when-on-type that maps GaddiMoment variants to their beat composables.
 */
@Composable
private fun MomentBeatContent(
    m: GaddiMoment,
    progress: Float,
    anchors: TableAnchors,
) {
    when (m) {
        is GaddiMoment.Income -> IncomeBeat(m, progress, anchors)
        is GaddiMoment.ForeignAid -> ForeignAidBeat(m, progress, anchors)
        is GaddiMoment.Tax -> TaxBeat(m, progress, anchors)
        is GaddiMoment.Steal -> StealBeat(m, progress, anchors)
        is GaddiMoment.Assassinate -> AssassinateBeat(m, progress, anchors)
        is GaddiMoment.Exchange -> ExchangeBeat(m, progress, anchors)
        is GaddiMoment.Coup -> CoupBeat(m, progress, anchors)
        is GaddiMoment.Block -> BlockBeat(m, progress, anchors)
        is GaddiMoment.Challenge -> ChallengeBeat(m, progress, anchors)
        is GaddiMoment.Reveal -> RevealBeat(m, progress, anchors)
        is GaddiMoment.InfluenceLoss -> InfluenceLossBeat(m, progress, anchors)
        is GaddiMoment.Elimination -> EliminationBeat(m, progress, anchors)
        is GaddiMoment.TurnHandoff -> TurnHandoffBeat(m, progress, anchors)
        is GaddiMoment.Win -> WinBeat(m, progress, anchors)
    }
}
