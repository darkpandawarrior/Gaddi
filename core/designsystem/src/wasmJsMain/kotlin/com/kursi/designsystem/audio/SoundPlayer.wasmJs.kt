package com.kursi.designsystem.audio

import org.w3c.dom.Audio
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// ═══════════════════════════════════════════════════════════════════════════════
// SoundPlayer.wasmJs.kt — WASM/BROWSER actual. Bundled clip bytes are base64-encoded into a
// `data:` URL and played through the DOM `Audio` element — simpler and more portable across
// browsers than driving raw Web Audio AudioContext.decodeAudioData buffers for one-shot SFX.
//
// The clips are PCM WAV, which every browser engine decodes — the previous Ogg Vorbis assets
// played on Chromium/Firefox but were silent on WebKit/Safari. runCatching stays as the guard for
// autoplay-policy rejections, which are a user-gesture issue and not a codec one. Best-effort:
// compile-verified only here — needs an in-browser audio check across target browsers.
// See docs/experience-assets.md §3.
// ═══════════════════════════════════════════════════════════════════════════════

actual fun SoundPlayer(): SoundPlayer = WasmSoundPlayer()

@OptIn(ExperimentalEncodingApi::class, kotlin.js.ExperimentalWasmJsInterop::class)
private class WasmSoundPlayer : SoundPlayer {
    private val dataUrls = mutableMapOf<KursiSound, String>()

    // ponytail: every browser engine ships an Audio element and a PCM WAV decoder, so the only
    // real gate here is the autoplay policy — and that cannot be queried synchronously, it only
    // shows up as a rejected play() promise. Constant true is the honest answer to what this
    // flag can actually know.
    override val isAvailable: Boolean = true

    override suspend fun play(sound: KursiSound) {
        runCatching {
            val url =
                dataUrls.getOrPut(sound) {
                    val bytes = loadKursiSoundBytes(sound) ?: return@runCatching
                    "data:audio/wav;base64,${Base64.encode(bytes)}"
                }
            Audio(url).play()
        }
    }

    override fun release() {
        dataUrls.clear()
    }
}
