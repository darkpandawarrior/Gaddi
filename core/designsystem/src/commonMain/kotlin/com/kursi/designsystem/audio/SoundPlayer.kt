package com.kursi.designsystem.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kursi.core.designsystem.generated.resources.Res

// ═══════════════════════════════════════════════════════════════════════════════
// SoundPlayer.kt — the audio expect/actual layer (docs/experience-assets.md §3), modeled on the
// shader layer's per-platform actuals (MaterialShader.kt) and rememberSoundPlayer() in the
// moment-feedback layer (MomentFeedback.kt, which stays wired for haptics only — see that file).
//
// Every actual decodes lazily on first play() per KursiSound and caches the platform handle.
// load/decode/play are wrapped in runCatching PER ACTUAL so a missing clip, an unsupported codec
// (e.g. no Ogg Vorbis decoder on stock javax.sound / Core Audio), or any other platform hiccup
// degrades to a silent no-op — never a crash. Callers gate every play() call on their own
// sound-enabled flag (GameScreen's soundEnabled, sourced from AppPrefs) — this class never
// re-checks a preference itself, exactly like the sibling feedback SoundPlayer.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Reads [sound]'s bundled clip bytes from composeResources (files/audio/), or null if missing /
 * unreadable. Shared by every platform actual so resource-path resolution lives in one place.
 */
internal suspend fun loadKursiSoundBytes(sound: KursiSound): ByteArray? = runCatching { Res.readBytes("files/audio/${sound.fileName}") }.getOrNull()

/**
 * Plays the bundled CC0 SFX clips from the finalized manifest ([KursiSound]).
 *
 * Contract (same as [com.siddharth.kmp.feedback.SoundPlayer]): [play] must never throw — a
 * missing resource, an unavailable audio device, or a headless CI box all degrade to silence.
 * [release] frees native resources and is safe to call more than once.
 */
interface SoundPlayer {
    /**
     * Whether this platform can actually make noise. False means every [play] is a silent no-op —
     * a missing Android install() hook, an audio session that would not activate, a headless box
     * with no mixer. It exists so that "the sound silently does nothing" is an assertable fact
     * rather than something only a human with a device can notice; the whole 17-clip manifest was
     * inaudible on two platforms at once precisely because nothing surfaced it.
     */
    val isAvailable: Boolean

    suspend fun play(sound: KursiSound)

    fun release()
}

/**
 * Builds the platform [SoundPlayer]. Named like a constructor on purpose: every call site reads
 * `SoundPlayer()` exactly as it did when this was an `expect class`.
 *
 * An interface plus an `expect fun` factory rather than an `expect class`: expect/actual
 * CLASSIFIERS are still Beta (KT-61573) and warn on every compile of every source set, and the
 * only ways to silence that are the blanket `-Xexpect-actual-classes` flag or a `@Suppress` on
 * each of the five files. Expect/actual FUNCTIONS are stable, the platform types become ordinary
 * private classes, and nothing at a call site changes.
 */
expect fun SoundPlayer(): SoundPlayer

/** Remembers a [SoundPlayer] for the composition's lifetime and releases it on dispose. */
@Composable
fun rememberKursiSoundPlayer(): SoundPlayer {
    val player = remember { SoundPlayer() }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}
