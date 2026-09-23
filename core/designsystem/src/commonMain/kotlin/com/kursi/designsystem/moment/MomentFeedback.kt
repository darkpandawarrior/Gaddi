package com.kursi.designsystem.moment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.siddharth.kmp.feedback.HapticPattern
import com.siddharth.kmp.feedback.SoundPlayer
import com.siddharth.kmp.feedback.defaultSoundPlayer

// ═══════════════════════════════════════════════════════════════════════════════
// MomentFeedback.kt — M3 glue between the moment data model and the :core:feedback
// expect/actual layer. Translates the moment's [HapticBeat] taxonomy to the platform
// [HapticPattern]. SFX playback for game beats now goes through the fine-grained
// com.kursi.designsystem.audio.SoundPlayer (docs/experience-assets.md §3), wired at the
// GameEvent level in feature/game/GameMoments.kt — this file stays wired for HAPTICS only,
// so a moment never plays both a coarse tone-synth blip and a real CC0 clip together.
//
// The overlay fires haptics GATED by the master sound toggle (AppPrefs.soundFlow); when
// the toggle is off the overlay never calls in here, so there's no buzz either.
// ═══════════════════════════════════════════════════════════════════════════════

/** Translate the moment-layer [HapticBeat] taxonomy into the device [HapticPattern]. */
internal fun HapticBeat.toPattern(): HapticPattern =
    when (this) {
        HapticBeat.None -> HapticPattern.None
        HapticBeat.Tick -> HapticPattern.Tick
        HapticBeat.Thud -> HapticPattern.Thud
        HapticBeat.DoubleBuzz -> HapticPattern.DoubleBuzz
        HapticBeat.HeavyLong -> HapticPattern.HeavyLong
    }

/**
 * Remembers a platform [SoundPlayer] for the lifetime of the composition and releases its
 * native resources (audio lines / SoundPool) on dispose.
 */
@Composable
fun rememberSoundPlayer(): SoundPlayer {
    val player = remember { defaultSoundPlayer() }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

/**
 * Fires the haptic for [moment] through [player]. Caller is responsible for the sound-enabled
 * gate; this function assumes feedback is permitted. (SFX is fired separately — see file header.)
 */
internal fun SoundPlayer.fire(moment: GaddiMoment) {
    haptic(moment.haptic.toPattern())
}
