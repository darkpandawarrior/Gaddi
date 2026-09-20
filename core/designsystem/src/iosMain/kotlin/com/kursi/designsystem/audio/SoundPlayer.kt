package com.kursi.designsystem.audio

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.Foundation.NSData
import platform.Foundation.create

// ═══════════════════════════════════════════════════════════════════════════════
// SoundPlayer.ios.kt — IOS actual. AVAudioPlayer fed from an in-memory NSData built from the
// bundled composeResources bytes.
//
// Core Audio has no built-in Ogg Vorbis decoder, so AVAudioPlayer(data:fileTypeHint:) returned
// nil for every clip and this whole actual was a silent no-op on device. Fixed at the asset
// layer instead of here: the bundled clips are now PCM WAV, which Core Audio decodes natively.
// The hint below is the WAVE UTI. Compile-verified only here (:core:designsystem klib compile);
// on-device/simulator audio verification is still a separate step. See docs/experience-assets.md §3.
//
// A decodable clip is necessary but not sufficient: AVAudioPlayer is silent unless the process
// AVAudioSession is configured and active. The default category (soloAmbient) also stops whatever
// the user was already listening to. Ambient is the right category for game SFX — it mixes with
// other audio and honours the ring/silent switch — and mirrors the sibling
// com.siddharth.kmp.feedback.FeedbackIos, which has always done exactly this.
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalForeignApi::class)
actual class SoundPlayer actual constructor() {
    private val players = mutableMapOf<KursiSound, AVAudioPlayer>()

    /** False when the audio session could not be configured, i.e. play() is a silent no-op. */
    actual val isAvailable: Boolean =
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryAmbient, null)
            session.setActive(true, null)
        }.isSuccess

    actual suspend fun play(sound: KursiSound) {
        runCatching {
            val player =
                players.getOrPut(sound) {
                    val bytes = loadKursiSoundBytes(sound) ?: return@runCatching
                    AVAudioPlayer(data = bytes.toNSData(), fileTypeHint = "com.microsoft.waveform-audio", error = null)
                        .also { it.prepareToPlay() }
                }
            player.currentTime = 0.0
            player.play()
        }
    }

    actual fun release() {
        runCatching { players.values.forEach { it.stop() } }
        players.clear()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
