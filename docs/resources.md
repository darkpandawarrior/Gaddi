# Gaddi — External Resources & Assets (license-vetted)

Curated free resources to absorb into the launch overhaul. **Shipping rule:** only **CC0** and permissive
(**MIT / Apache-2.0 / OFL**) assets or code enter the shipped game. **CC-BY** requires an entry in the in-app
**Credits** screen. **GPL / copyleft art or code is quarantined** — never bundled into this proprietary game.
Every "absorb" below carries a license-verification step before any code/asset is copied in.

Gaddi renders 100% on Canvas/vector, so the high-value absorbs are **shader code, juice technique, fonts, and
audio** — not sprite packs.

## Graphics / shaders — Workstream C (§7)

| Resource | Use | License | Note |
|---|---|---|---|
| [ShaderX](https://github.com/Debanshu777/ShaderX) | Reference architecture: AGSL (Android) + shared Skia `RuntimeEffect` (iOS/desktop/web) with `AbstractRuntimeShaderEffect` base + LRU cache — matches §7's expect/actual shader host exactly. | verify before copy | Pattern first; copy code only after license check. |
| [Cloudy](https://github.com/skydoves/Cloudy) | KMP blur with GPU shader + **CPU fallback** for old devices/web. Candidate **dependency** for the Focus-Pull bokeh (§4.1) and the no-shader fallback (§7.2, §11 web). | Apache-2.0 (verify) | Can be a real dep, not just reference. |
| [Shady](https://github.com/drinkthestars/shady) | AGSL gallery — grain, texture, animation. Reference for felt-weave / film-grain / vignette passes. | verify before copy | Some shaders ported from Shadertoy — check per-shader. |
| [Skia SkSL & Runtime Effects docs](https://skia.org/docs/user/sksl/) | Canonical SkSL reference for the non-Android backends. | docs | — |

## Game feel / juice — Workstream C (§7.3), design bible

Technique to absorb (free to learn from — no assets copied):
- [Balatro: Juicy Feedback in a Poker Roguelike](https://blakecrosley.com/guides/design/balatro) — the layered juice stack (animation → particles → screen → audio → haptics).
- [Recreating Balatro's Game Feel — Mix and Jam](https://www.youtube.com/watch?v=I1dAZuWurw4) — inertia/magnetic card snapping, causality animations.
- [Balatro Design Analysis: Visual Packaging & Interactive Feedback](https://medium.com/@yyh19971004/balatro-design-analysis-visual-packaging-and-interactive-feedback-cc6fa6a65370).

## UI / layout reference — Workstreams A (§3), comprehension (§6)

- [Game UI Database — Balatro](https://www.gameuidatabase.com/gameData.php?id=1935) — searchable real-game UI library (many card games) for density-layer + dock layout study.

## Typography — Workstream I (§13), `hi` locale

Latin display trio (Rozha One / Marcellus / DM Mono) already bundled. For Devanagari — all **OFL**, free commercial, embeddable:
- [Tiro Devanagari Hindi](https://fonts.google.com/specimen/Tiro+Devanagari+Hindi) — literary Devanagari display (Murty Classical Library of India origin; on-theme for the "sarkari document" register).
- [Hind](https://fonts.google.com/specimen/Hind) — Devanagari + Latin, built for UI; the body workhorse.
- Deco Latin alternates: Cinzel, Della Respira, Forum ([OFL, Google Fonts](https://cssauthor.com/best-free-retro-vintage-google-fonts/)).

## Audio — Workstream C (§7.3), the juice layer's sound

Gaddi is silent today; beats (stamp-slam / coin / card-deal) need SFX. **CC0 only** (per-clip verify):
- [Freesound](https://opengameart.org/content/cc0-sound-effects) (filter CC0) — 500k+ sounds.
- [Kenney audio](https://kenney.itch.io/kenney-game-assets) — UI/impact SFX, CC0.
- [Pixabay SFX](https://pixabay.com/sound-effects/search/cc0/) — commercial-use, no attribution.

## Mechanics / online UX reference — Workstream H (§12)

Engine is done; these inform online flow + AI ideas (study, don't copy):
- [octachrome/treason](https://github.com/octachrome/treason) — mature Coup w/ online multiplayer + AI; best for lobby/reaction-window UX.
- [8tp/Coup](https://github.com/8tp/Coup) — real-time, no-account join flow.

## CC0 asset libraries (limited use — vector game)

Seed icons / textures / placeholder audio only:
- [Kenney](https://kenney.itch.io/kenney-game-assets) (60k+ CC0).
- [OpenGameArt CC0](https://opengameart.org/content/cc0-resources) — ⚠️ filter strictly to CC0; much of OGA is GPL/CC-BY (quarantine those).
- [itch.io CC0 assets](https://itch.io/game-assets/assets-cc0).

## Design tooling (free, code-first — no paid Figma)

Figma Dev Mode is paid-only; the free Starter plan can't unlock it. Gaddi's design source of truth is **code**
(`core/designsystem`), so:
- **Design system** → built directly in `core/designsystem` (the real thing).
- **Mockups / screens** → the existing `:cmp-desktop:renderScreens` harness + Compose `@Preview`.
- **Concept mockups** → agent Artifact/visual tooling (free, inline).
- **Optional free canvas** → [Penpot](https://penpot.app) (open-source, free dev-inspect) — only if a visual canvas is wanted.

Figma stays **read-only / optional**; nothing in the launch depends on it.
