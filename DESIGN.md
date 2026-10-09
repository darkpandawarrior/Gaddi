# Gaddi DESIGN.md

> Inherits: house design standard (AgentHarness skill `design-md`). This file wins on conflict.
> Agents: read this before creating or changing UI. Values live in
> `core/designsystem/src/commonMain/kotlin/com/kursi/designsystem/` (`KursiTheme.kt`,
> `Shapes.kt`, `KursiMotion.kt`, `Components.kt`). Package and symbols still say `kursi`;
> the product is Gaddi.
> Dial: ENERGY 6 / RHYTHM 5 / MOTION 5
> Prose sources, distilled here: `docs/design-language.md`, `docs/brand/BRAND.md`,
> `docs/art/STYLE_LOCK.md`, `docs/superpowers/specs/2026-07-18-kursi-launch-overhaul-design.md`.
> Gaddi is a standalone design system; it does not consume the kmp-toolkit tokens.

## Overview

Gaddi is a bluffing card game that looks like a satirical political poster. The direction is
"License Raj Deco" (also called Sarkari Noir): 1950s to 70s Indian government documents, ration
cards, share certificates, enamel station signs, parliament art-deco. The interface is itself a
pompous government artifact. One warm overhead lamp lights teak, aged brass and document paper;
depth is cast shadow and material, never an outline around a region.

Every screen should read as the same lit world as the in-game board (`feature/game`). Exactly
one focal point per screen. Voice is Hinglish, deadpan, never cruel to people (see `BRAND.md`).

## Colors

Single dark scheme (`KursiDecoColorScheme`, `darkColorScheme`). There is no light theme.

| M3 role | Value | Token |
|---|---|---|
| primary | #C99A3B | BrandTokens.BrassAged |
| onPrimary | #2A1B14 | BrandTokens.TeakDark |
| primaryContainer | #8A6A28 | BrandTokens.BrassDark |
| onPrimaryContainer | #EDE3CC | BrandTokens.PaperCream |
| secondary | #3A2418 | BrandTokens.TeakMid |
| onSecondary / onBackground / onSurface | #F2E8D0 | KursiNeutrals.TextPrimary |
| background | #1E1008 | BrandTokens.TeakInk |
| surface | #3A2418 | BrandTokens.TeakMid |
| surfaceVariant | #4A3020 | (KursiFeltColors.Surface3) |
| onSurfaceVariant | #CBB882 | KursiNeutrals.TextSecondary |
| error | #C1272D | BrandTokens.StampRed |
| outline | #C99A3B | BrandTokens.BrassAged |

Extra brand tokens: TeakDark #2A1B14, GoldAntique #E8C874 (foil, primary CTA fill), PaperDeep
#D4C9A8, CreamInk #3A2C14 (text on paper), Oxblood #7A2E2E, CivilGreen #2D6A4F, CivicBlue
#1B4F72, PendingAmber #D4A017. Text: muted #8C7045, disabled #5A4030. Review verdict ramp:
Sharp #6FCF97, Fine #B7C66B, Loose #E0B354, then StampRed.

Role hues are Okabe-Ito, CVD-safe and locked (`KursiRoleHues`, never change): Neta #0072B2,
Bhai #D55E00, Babu #009E73, Jugaadu #E69F00, Vakil #CC79A7, Patrakaar #56B4E9. A second,
non-colour channel carries identity: `RoleFramePattern` (solid ring, hatched, dotted, woven,
double rule, ticked). Ten seat colours live in `KursiSeatColors`, separate from role hues.

Rules: gold is primary and focal; oxblood or stamp red is reserved for the single thing that needs
attention (destructive, alert); everything else recedes into warm neutrals.

## Typography

Three bundled OFL fonts (`composeResources/font/`): Rozha One (display, titles, used sparingly),
Marcellus (body and reading), DM Mono (labels, numerals, tracked uppercase eyebrows). The
theme patches them into `MaterialTheme.typography` (display slots Rozha, body and title slots
Marcellus, `labelSmall` DM Mono). Prefer `KursiType`; sizes sp / line height:

display 28/34, cardRole 26/30, title 20/26, name 17/22, body 15/20, label 13/18, caption 11/14,
numeric 14/18, title_md 18/22, title_sm 15/18, label_md 13/16, label_sm 11, label_micro 10/12,
numeral_sm 13/16 (DM Mono). Stay on this scale. Headers are engraved: a small-caps DM Mono
eyebrow plus a hairline gold rule, or a sparing Rozha title.

## Layout

`KursiDimens` 4dp grid: xs 4, sm 8, md 12, lg 16, xl 24. Keep a roughly 8pt rhythm with generous
but composed negative space; elements are anchored, nothing floats in a void. Lists are rows on
the ground separated by hairline rules, not stacks of bordered cards. Targets at least 48dp.
Desktop, Android, iOS and web share one layout via Compose Multiplatform.

## Elevation & Depth

No bordered boxes. A region is either bare on the lit ground or a raised lit surface (subtle
gradient plus a real drop shadow). The ground is a warm radial key-light pool high centre with a
vignetted rim (`drawKeyLightPool`, `drawTableVignette`, `FeltTableBackground`). Soft shadow depth
`KursiDimens.shadow_soft` 3dp. Texture tokens (`TextureTokens`): guilloche alpha 0.18, paper grain
0.06, teak hatch 0.04, emblem 0.035, film grain 0.018, warm bloom 0.05. Hairline strokes: 0.75dp
(brass at 40% alpha), idle ring 1dp, active ring 2dp.

## Shapes

`KursiRadii`: xs 6 (pip backplates), sm 11 (chips, coin pills), md 14 (avatars, card backs),
lg 20 (opponent chips, dock, status spine), xl 22 (influence cards, action buttons), xxl 28 (felt
area, reaction theatre, sheets). `Squircle(radius)` is currently a plain rounded rectangle.
`KursiDimens` also has r_sm 6, r_md 10, r_lg 14 for older call sites.

## Components

Reuse before inventing (`Components.kt`, `AAAChrome.kt`, `OpponentSeatToken.kt`, `RoleGlyph.kt`,
`ChatBubble.kt`): `RoleCard` (aged paper, brass double rim), `OpponentPlate`, `SeatAvatar`
(brass-rimmed disc, role-hued radial fill), `CoinPill`, `InfluencePips`, `ClaimChip`,
`SuspicionChip`, `KursiActionButton`, `StatusSpine`, `OutcomeTag`, `CountdownBar`, `ActionBar`,
`WaxSeal`, `FeltTableBackground`. Buttons are raised stamps: gold fill with dark ink text for
primary, dark raised gradient with a brass hairline for secondary, dimmed when disabled. Extract
a shared component only when two screens need the identical new thing.

## Motion

`KursiMotion` springs: `snap` (no bounce, high stiffness) for presses, `settle` (low bounce) for
cards and tokens landing, `dramatic` (medium bounce, low stiffness) for stamp slams and
eliminations. FOCUS_PULL_MS 420, DEAL_MS 300. Always honour `LocalReducedMotion`.

## Do's and Don'ts

- Do build depth from shadow and material, and give each screen one focal point.
- Do take colour from `BrandTokens`, `KursiNeutrals` and `KursiRoleHues`; text on paper is CreamInk.
- Do check every screen with `:cmp-desktop:renderScreens` and read the PNG against the game board.
- Do keep role hues untouched and pair them with the frame pattern.
- Don't draw a 1 to 2dp brass or oxblood border around a block; dissolve it.
- Don't use a full-width filled gold gradient bar as a header.
- Don't use oxblood for anything but the one attention element.
- Don't satirise people, castes, religions or real parties; the target is the archetype.

## Known drift (code wins)

`docs/brand/BRAND.md` lists Deep Teak #1A1A2E, Document Cream #F4ECD8, and role colours that
differ from code (it has Babu orange #E69F00, Jugaadu sky #56B4E9, no Patrakaar). The values above
are read from `KursiTheme.kt`; treat `BRAND.md` hexes as stale.

## Agent notes

- Gate (from `docs/design-language.md`): `:feature:game:jvmTest :core:designsystem:jvmTest
  testAndroidHostTest detekt ktlintCheck :cmp-desktop:renderScreens
  :cmp-android:compileNoGmsDebugKotlin`.
- Art follows `docs/art/STYLE_LOCK.md`: every colour in a piece must trace to a token.
- Run the `antislop` skill as the filter on any UI diff and report its Delivery Gate result.

## Changelog

| Date | Change | Why | Source |
|---|---|---|---|
| 2026-10-09 | Initial version, distilled from the code token files and existing design docs | Establish design direction for agents | DESIGN.md rollout |

## Open questions

- Known drift: docs/brand/BRAND.md hexes (Deep Teak, Document Cream, role colours) differ from KursiTheme.kt; BRAND.md treated as stale.

## Evolving this file

Agents: when you change UI and find this file wrong or silent, fix it in the same change and add a Changelog row. Code token files win over this file; when they disagree, correct the doc. A user correction of a visual choice with a stated reason becomes a rule here immediately. Lessons that apply beyond this repo go to the LEARNINGS log of the `design-md` skill in AgentHarness.
