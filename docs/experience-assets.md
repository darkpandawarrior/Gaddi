# Gaddi — Experience Assets & Absorption Plan

Flesh out the full sensory experience (sound, music, fonts, haptics, particles) by absorbing vetted
external assets, mapped to game beats. **Shipping rule (same as [resources.md](resources.md)):** CC0 /
OFL / permissive only; **CC-BY → in-app Credits screen**; no copyleft. Every clip carries a license
check before it's bundled.

**The absorption pattern** mirrors the art pipeline (`KursiArt.readySlots`): build the pipeline + a
manifest of needed clips now; each asset is *inert until the file is dropped in*, then it lights up. No
half-built states, no broken audio if a clip is missing.

---

## 1. Sound design — the beat → sound map

Every meaningful `GameEvent` gets a sound. Source packs are Kenney CC0 (confirmed) + Freesound CC0 for
the signature one-offs.

| Game beat | Sound | Source (CC0) |
|---|---|---|
| Dehaadi (income +1) | single coin clink | Kenney **Casino Audio** |
| FDI (foreign aid +2) | double coin | Kenney Casino Audio |
| Ghotala (tax +3) | coin cascade | Kenney Casino Audio |
| Vasooli (steal 2) | coin swipe / slide | Kenney Casino Audio |
| Supari (assassinate) | blade thud / low impact | Kenney **Impact Sounds** |
| Setting (exchange) | card riffle + deal | Freesound CC0 "card deal" |
| **Khela (coup)** | heavy gavel SLAM | Kenney Impact Sounds |
| **Challenge declared** | rubber-stamp SLAM *(the signature sound)* | Freesound CC0 "rubber stamp" + Impact |
| Challenge → TRUE | brass success chime | Kenney **Interface Sounds** |
| Challenge → BLUFF | dull failure thud | Kenney Impact Sounds |
| Influence lost (card flips up) | paper slap / hard card flip | Freesound CC0 card |
| Match deal (start) | riffle shuffle + deal round | Freesound CC0 card |
| Tap-to-continue / turn pass | soft UI tick | Kenney Interface Sounds |
| Win the Gaddi | brass fanfare sting | Kenney **Music Jingles** |
| UI (tap/confirm/back) | subtle clicks | Kenney Interface Sounds |

Wired through the existing `MomentHost` (sounds fire on the same events that drive `ActionMomentOverlay`),
gated by the existing `soundEnabled` flag, with per-event volume + a global mute in Settings.

## 2. Music / ambience

- **Ambient loop — "License Raj office":** tanpura/harmonium drone under distant typewriter + ceiling-fan
  foley; understated, tense-but-bureaucratic. A single seamless loop, ducked during dramatic beats.
- **Tension layer:** a second stem that rises during a reaction/challenge window, falls after resolution.
- **Sources:** Pixabay Music (royalty-free, no-attribution — verify per track), Freesound CC0 ambient
  loops, Kenney Music Jingles for stings. Shortlist of exact tracks/URLs on request (needs listen-and-pick).

## 3. Audio architecture (the pipeline — code, wired AFTER the composition rebuild)

- New `core/audio` module (or in `core/designsystem`): `expect class SoundPlayer` —
  Android `SoundPool` (SFX) + `MediaPlayer`/ExoPlayer (music loop); desktop `javax.sound` / JavaFX media;
  iOS `AVAudioPlayer`; wasm Web Audio API.
- `enum KursiSound` + `play(KursiSound)`; a `GameEvent → KursiSound` map driven from the moment layer.
- composeResources `files/audio/` track (like the art `drawable/` track) — **graceful no-op when a clip is
  absent** + honors `soundEnabled`. Inert until clips are dropped in (mirrors `KursiArt`).
- Music: one looping ambient with volume ducking during dramatic tiers (reuse `BeatTier`).

## 4. Fonts — Devanagari for the Hindi locale (OFL)

- **Tiro Devanagari Hindi** (display) + **Hind** (UI/body) — both OFL, free commercial, embeddable. Bundle
  into `core/designsystem/composeResources/font/`, wire into `KursiType` for the `hi` locale (Latin
  Rozha/Marcellus/DM Mono already wired). Closes the l10n-typography gap for the game's Hindi voice.

## 5. Haptics vocabulary (mapped)

Distinct signature per action class (Android `HapticFeedback`/`Vibrator`, iOS Core Haptics via
expect/actual; silent no-op desktop/web): income = light tick · challenge slam = heavy double · coup =
single heavy · win = escalating triad · card lift/peek = soft. Reduced-motion/​settings respected.

## 6. Particles (code, no assets)

Coin trails (income/steal), stamp-ink spray (challenge), dust motes (ambient), crest confetti (win) —
object-pooled, capped counts, reduced-motion aware. Layer in the compositor between shaders and moment
overlays.

## Absorption status

| Track | Pipeline | Assets |
|---|---|---|
| Art (portraits/faces/hero) | ✅ ready (`KursiArt.readySlots`) | ⏭ need real files |
| Audio (SFX) | ✅ wired (`core/designsystem/.../audio/SoundPlayer` expect/actual + GameEvent→KursiSound map) | ✅ 17 clips bundled |
| Music (ambient/tension) | ⏭ (part of audio arch) | ⏭ shortlist on request |
| Latin fonts | ✅ wired | ✅ |
| Devanagari fonts | ⏭ | Tiro Devanagari + Hind (OFL) |
| Haptics | partial (spring juice done) | full vocabulary ⏭ |
| Particles | ⏭ | n/a (code) |
| Shaders (grain/specular/bloom) | ⏭ (AGSL host, in progress) | n/a (code) |

## Finalized SFX manifest (CC0 — downloaded + curated, ready to wire)

17 clips selected from the three Kenney CC0 packs, renamed to game-semantic names (208 KB total, OGG).
Source packs are **CC0 public domain** (no attribution required; `License.txt` retained). Final pick by
filename — a listen-and-swap pass is worth doing, but these are sensible defaults.

| KursiSound | Clip | Pack |
|---|---|---|
| CoinSingle / Double / Cascade / Swipe | chips-handle-1 / chips-stack-2 / chips-collide-1 / chips-handle-3 | Casino |
| CardDeal / Slide / PlaceHard / Fan | card-shuffle / card-slide-1 / card-place-4 / card-fan-1 | Casino |
| ImpactBlade (Supari) / ImpactGavel (Khela) | impactMetal_heavy_000 / _002 | Impact |
| StampSlam (challenge) | impactPlank_medium_000 | Impact |
| StingWin / StingTrue / StingBluff | impactBell_heavy_000 / confirmation_001 / impactPunch_heavy_002 | Impact/Interface |
| UiTap / UiConfirm / UiBack | click_001 / confirmation_002 / back_001 | Interface |

**Wired**: all 17 clips live at `core/designsystem/src/commonMain/composeResources/files/audio/`,
loaded through the `SoundPlayer` expect/actual (Android `SoundPool`, desktop `javax.sound.sampled`,
iOS `AVAudioPlayer`, wasm `Audio` element) and fired from a pure `GameEvent -> KursiSound` map in
`feature/game/GameSound.kt`, gated by the existing `soundEnabled` flag. Desktop/iOS currently
degrade to a silent no-op for these Ogg Vorbis clips (no bundled decoder on stock javax.sound /
Core Audio) — the pipeline is correct end to end and lights up if/when a WAV/PCM fallback or codec
SPI lands. Wasm is compile-verified only; needs an in-browser check. **Still needed:** the ambient
music loop (Kenney Music Jingles / Pixabay / Freesound CC0) — benefits most from a listen-and-pick
and a licence check.

## The honest download constraint

I can curate exact CC0 sources and build every pipeline, but I can't autonomously bulk-download binary
asset files — they need your download, or my download **with per-file permission** (I'll name file,
source, size). So the flow: pipeline + manifest ships → CC0 files dropped in → the experience lights up.
Nothing blocks the game in the meantime (all pipelines degrade gracefully to silent/vector/no-op).
