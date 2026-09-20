package com.kursi.designsystem.audio

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

// ═══════════════════════════════════════════════════════════════════════════════
// SoundPlayer.jvm.kt — DESKTOP actual. javax.sound.sampled Clip playback from the bundled
// composeResources bytes.
//
// The vanilla JDK ships no Ogg Vorbis decoder, so AudioSystem.getAudioInputStream() threw
// UnsupportedAudioFileException on the old .ogg clips and desktop was a silent no-op. This file
// predicted its own fix — "dropping in a WAV/PCM clip makes this target audible with no code
// change" — and that is exactly what happened: the bundled clips are PCM WAV now, which
// javax.sound reads natively, and no SPI jar or new dependency was added. runCatching stays as
// the guard for a headless/no-mixer environment. See docs/experience-assets.md §3.
// ═══════════════════════════════════════════════════════════════════════════════

actual class SoundPlayer actual constructor() {
    private val clipBytes = mutableMapOf<KursiSound, ByteArray>()

    @Volatile
    private var released = false

    actual suspend fun play(sound: KursiSound) {
        if (released) return
        runCatching {
            val bytes = clipBytes.getOrPut(sound) { loadKursiSoundBytes(sound) ?: return@runCatching }
            AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { stream ->
                val clip = AudioSystem.getClip()
                clip.open(stream)
                clip.addLineListener { event -> if (event.type == LineEvent.Type.STOP) clip.close() }
                clip.start()
            }
        }
    }

    actual fun release() {
        released = true
        clipBytes.clear()
    }
}
