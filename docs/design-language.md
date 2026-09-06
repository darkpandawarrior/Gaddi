# Gaddi Design Language — "Sarkari Noir" (apply to EVERY screen)

The AAA standard proven on the in-game FOCUS board. Every screen and flow must read as the same lit,
crafted world. **Reference artifacts:** the rebuilt game board (`feature/game`), and the approved mockup
`scratchpad/kursi-aaa-board.html`. When in doubt, match those.

## The 9 non-negotiables

1. **No bordered boxes.** Depth comes from **shadow + material, never an outline** around a region. Kill
   `decoPanel`-style bordered panels. Content either sits directly on the lit ground, or on a *raised lit
   surface* (subtle gradient + a real drop shadow) — no 1–2dp brass/oxblood border framing a block.
2. **One lit material language.** A warm overhead lamp on teak / aged-brass / document-paper. Every screen
   shares the warm ground (radial key-light pool high-centre → vignetted rim). Brass catches light with a
   hot highlight + dark rim; paper is warm cream; depth is cast shadow.
3. **Engraved chrome, not fat bars.** Headers = a small-caps **DM Mono** eyebrow/label + a **hairline gold
   rule**, or a **Rozha** display title used sparingly — never a full-width filled gold gradient bar.
4. **Crafted elements.** Buttons = raised *stamps* (gold-fill for primary/focal with dark ink text; dark
   raised gradient + brass hairline for secondary; dimmed for disabled). Cards = aged paper with brass
   double-rim. Tokens/avatars = brass-rimmed discs with role-hued radial fill + inner highlight. Lists =
   rows on the ground separated by **hairline rules**, not a stack of bordered cards.
5. **Type scale + hierarchy.** Rozha One (display/titles, sparing), Marcellus (body/reading), DM Mono
   (labels/numerals/eyebrows, tracked uppercase). Stay on the scale. Exactly one clear focal point/screen.
6. **Spacing rhythm.** Consistent ~8pt rhythm; generous but *intentional* negative space (never a dead
   void — space is composed, elements are anchored, nothing floats aimlessly).
7. **One accent.** Oxblood/stamp-red is reserved for the single element needing attention
   (destructive/alert). Gold = primary/focal. Everything else recedes into the warm neutrals.
8. **Motion.** `KursiMotion` springs on transitions/press; always reduced-motion aware.
9. **Accessibility.** Semantics/contentDescription, ≥48dp targets, contrast on the dark ground, dynamic
   type / font-scale.

## Reuse, don't reinvent

`KursiType` (real Rozha/Marcellus/DM Mono, already wired) · `BrandTokens` · `KursiRoleHues` +
`RoleFramePattern` · `KursiMotion` · the lit felt/teak background + `drawKeyLightPool`/`drawTableVignette`
· `OpponentSeatToken` (brass-rimmed token) · `RoleCard` (paper card) · `BrassMedallion` · the engraved
header pattern (`EngravedTurnHeader` in `status/StatusSpine.kt`) · the raised stamp-chip
(`CompactActionChip`). Extract a shared component only if ≥2 screens need the exact same new thing;
otherwise treat inline (YAGNI).

## Per-screen checklist (what each agent does)

- [ ] Dissolve every bordered box → lit ground / shadow-depth raised surface.
- [ ] Header → engraved (eyebrow + hairline / sparing Rozha title), no fat bar.
- [ ] Buttons → stamps; cards → paper+brass; lists → hairline rows; avatars → brass tokens.
- [ ] Type on the scale; one focal point; one accent (oxblood) only where it belongs.
- [ ] Consistent spacing rhythm; nothing floats in a void.
- [ ] Motion springs + reduced-motion; a11y semantics + targets + contrast.
- [ ] **Self-check via the screen's render fixture** (`cmp-desktop/build/shots/<name>.png`) — iterate until
      it reads as the same world as the game board. Don't ship a box.

## Ground truth = the render

`:cmp-desktop:renderScreens` renders every screen; READ the PNG and compare to this standard. Local renders
write to gitignored `build/shots/` (tracked baselines refresh in CI). Gate every change:
`:feature:game:jvmTest :core:designsystem:jvmTest testAndroidHostTest detekt ktlintCheck
:cmp-desktop:renderScreens :cmp-android:compileNoGmsDebugKotlin` must be green; replay tests pass; no AI
commit trailer; explicit-path commits; leave the pre-existing WIP unstaged.
