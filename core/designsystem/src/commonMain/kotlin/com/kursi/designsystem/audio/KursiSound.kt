package com.kursi.designsystem.audio

// ═══════════════════════════════════════════════════════════════════════════════
// KursiSound.kt — the finalized 17-clip CC0 SFX manifest (docs/experience-assets.md §1 / the
// "Finalized SFX manifest" table). Every clip is Kenney CC0 (public domain), bundled under
// composeResources/files/audio/. [fileName] is the path SoundPlayer resolves via Res.readBytes.
//
// FORMAT: uncompressed PCM WAV, not the original Ogg Vorbis. Vorbis needs a decoder that three of
// the four targets do not have — iOS Core Audio has none, stock javax.sound has none, and WebKit
// ships none — so 17 of 17 clips were silently no-oping everywhere except Android and
// Chromium/Firefox. PCM needs no decoder anywhere. Transcoded with ffmpeg at the source rate and
// channel count (no resampling, no downmix), so nothing about how the clips sound changed; the
// only cost is 208 KB -> 1.7 MB on disk. Do not "optimise" this back to a compressed codec
// without checking that codec against iOS AND Safari AND stock javax.sound.
// ═══════════════════════════════════════════════════════════════════════════════

/** One resolvable SFX clip. See [SoundPlayer] for playback and docs/experience-assets.md for the beat map. */
enum class KursiSound(
    val fileName: String,
) {
    CoinSingle("coin_single.wav"),
    CoinDouble("coin_double.wav"),
    CoinCascade("coin_cascade.wav"),
    CoinSwipe("coin_swipe.wav"),
    CardDeal("card_deal.wav"),
    CardSlide("card_slide.wav"),
    CardPlaceHard("card_place_hard.wav"),
    CardFan("card_fan.wav"),
    ImpactBlade("impact_blade.wav"),
    ImpactGavel("impact_gavel.wav"),
    StampSlam("stamp_slam.wav"),
    StingWin("sting_win.wav"),
    StingTrue("sting_true.wav"),
    StingBluff("sting_bluff.wav"),
    UiTap("ui_tap.wav"),
    UiConfirm("ui_confirm.wav"),
    UiBack("ui_back.wav"),
}
