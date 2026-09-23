package com.kursi.designsystem.audio

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Smoke test for the audio pipeline (docs/experience-assets.md §3): every clip in the finalized
 * manifest resolves to real, non-empty bytes from composeResources, is still uncompressed PCM WAV,
 * and constructing + playing through a [SoundPlayer] never throws.
 */
class SoundPlayerTest {
    @Test
    fun everyClipInTheManifest_resolvesToNonEmptyBytes() =
        runBlocking {
            GaddiSound.entries.forEach { sound ->
                val bytes = loadGaddiSoundBytes(sound)
                assertNotNull(bytes, "missing clip resource for $sound (${sound.fileName})")
                assertTrue(bytes.isNotEmpty(), "empty clip resource for $sound (${sound.fileName})")
            }
        }

    /**
     * The regression guard for the bug this manifest exists because of: the clips shipped as Ogg
     * Vorbis and were silent on iOS, desktop and Safari, none of which carry a Vorbis decoder.
     * PCM WAV needs no decoder anywhere. Re-encoding to a compressed codec would put all 17 clips
     * back into silence on those targets with nothing else failing, so assert the container here —
     * a RIFF/WAVE header — rather than waiting for a human with a device to notice.
     */
    @Test
    fun everyClipIsStillRiffWave() =
        runBlocking {
            GaddiSound.entries.forEach { sound ->
                val bytes = assertNotNull(loadGaddiSoundBytes(sound), "missing clip for $sound")
                assertTrue(bytes.size > 12, "truncated clip for $sound (${sound.fileName})")
                assertEquals("RIFF", bytes.decodeAscii(0, 4), "not a RIFF container: ${sound.fileName}")
                assertEquals("WAVE", bytes.decodeAscii(8, 4), "not a WAVE payload: ${sound.fileName}")
            }
        }

    @Test
    fun soundPlayer_playsEveryClip_withoutThrowing() =
        runBlocking {
            val player = SoundPlayer()
            GaddiSound.entries.forEach { sound -> player.play(sound) }
            player.release()
        }
}

private fun ByteArray.decodeAscii(
    offset: Int,
    length: Int,
): String = buildString { for (i in offset until offset + length) append(this@decodeAscii[i].toInt().toChar()) }
