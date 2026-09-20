package com.kursi.designsystem.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File

// ═══════════════════════════════════════════════════════════════════════════════
// SoundPlayer.android.kt — ANDROID actual. SoundPool loaded from a per-clip temp file written
// from the bundled composeResources bytes (SoundPool has no byte-array load API).
//
// Needs an application Context (for the cache dir) — install it once from the app shell, mirroring
// the sibling com.siddharth.kmp.feedback.FeedbackAndroid pattern:
//     KursiSoundAndroid.install(applicationContext)
// Sound is a silent no-op until installed.
// ═══════════════════════════════════════════════════════════════════════════════

/** Install hook for the application Context; see file header. */
object KursiSoundAndroid {
    @Volatile
    internal var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
    }
}

actual class SoundPlayer actual constructor() {
    private val pool: SoundPool? =
        runCatching {
            SoundPool
                .Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                ).build()
        }.getOrNull()

    private val soundIds = mutableMapOf<KursiSound, Int>()

    actual suspend fun play(sound: KursiSound) {
        val pool = pool ?: return
        val ctx = KursiSoundAndroid.appContext ?: return
        runCatching {
            var id = soundIds[sound]
            if (id == null) {
                val bytes = loadKursiSoundBytes(sound) ?: return@runCatching
                val tmp = File.createTempFile("kursi_${sound.name}", ".wav", ctx.cacheDir)
                tmp.deleteOnExit()
                tmp.writeBytes(bytes)
                id = pool.load(tmp.absolutePath, 1)
                soundIds[sound] = id
                // ponytail: fire-and-forget load — SoundPool decodes async (typically single-digit
                // ms for these clips), so the very first play of a never-before-heard sound
                // can occasionally be silent. Upgrade to setOnLoadCompleteListener-gated playback
                // if that edge case turns out to be audible on-device.
            }
            pool.play(id, 1f, 1f, 1, 0, 1f)
        }
    }

    actual fun release() {
        runCatching { pool?.release() }
        soundIds.clear()
    }
}
