# Style-Lock QA Gate

Acceptance checklist for every piece of art that lands in
`core/designsystem/src/commonMain/composeResources/drawable/`, per design spec
§9 (Art policy): *"AI-generated art is launch art, not placeholder... generate
in-style → style-lock QA gate → asset track. Rejected pieces regenerate;
nothing ships unreviewed."*

This gate governs the 18 asset-track slots wired through
`core/designsystem/src/commonMain/kotlin/com/kursi/designsystem/art/GaddiArt.kt`
(`GaddiArtRegistry.readySlots`):

| Slot kind | Count | Resource names |
|---|---|---|
| Persona portrait | 10 | `portrait_netaji_vachan`, `portrait_bhai_teja`, `portrait_babu_filewala`, `portrait_jugaadu_chhotu`, `portrait_vakil_loophole`, `portrait_inspector_damaad`, `portrait_seth_khokhawala`, `portrait_madam_sarpanch`, `portrait_dalla_tiwari`, `portrait_maaji_anna` |
| Role card face | 6 | `role_face_neta`, `role_face_bhai`, `role_face_babu`, `role_face_jugaadu`, `role_face_vakil`, `role_face_patrakaar` |
| Hero moment | 2 | `moment_crest`, `moment_tipped_chair` |

A piece **fails** this gate if it misses any item below. Failures regenerate;
nothing merges into `GaddiArtRegistry.readySlots` unreviewed. A piece only
needs to be re-run through the checklist once per submission, not per pixel —
this is a acceptance gate, not a style guide to redesign from.

## 1. Palette adherence

Every color used in the piece must be traceable to a token in
`core/designsystem/src/commonMain/kotlin/com/kursi/designsystem/GaddiTheme.kt`.
No off-palette hues — the "teak-and-brass council chamber" identity depends on
a closed set:

**Brand surface (`BrandTokens`):**

| Token | Hex | Use |
|---|---|---|
| `TeakDark` | `#2A1B14` | Ground / deep warm base |
| `TeakMid` | `#3A2418` | Gradient end, mid surface |
| `TeakInk` | `#1E1008` | Deepest ground / outer window |
| `BrassAged` | `#C99A3B` | Primary bezel / border metal |
| `GoldAntique` | `#E8C874` | Highlights, foil, coin tops |
| `BrassDark` | `#8A6A28` | Shadow / rim on brass |
| `PaperCream` | `#EDE3CC` | Card faces, certificate paper |
| `CreamInk` | `#3A2C14` | Text on certificate paper |
| `PaperDeep` | `#D4C9A8` | Deeper panel insets |
| `Oxblood` | `#7A2E2E` | Danger / elimination stamp |
| `StampRed` | `#C1272D` | Bright danger seal |
| `CivilGreen` | `#2D6A4F` | Success / truth (semantic) |
| `CivicBlue` | `#1B4F72` | Block (semantic) |
| `PendingAmber` | `#D4A017` | Pending (semantic) |

**Role hues (`GaddiRoleHues` — Okabe-Ito, CVD-safe, locked, MUST NOT change):**

| Role | Token | Hex |
|---|---|---|
| NETA | `Neta` | `#0072B2` |
| BHAI | `Bhai` | `#D55E00` |
| BABU | `Babu` | `#009E73` |
| JUGAADU | `Jugaadu` | `#E69F00` |
| VAKIL | `Vakil` | `#CC79A7` |
| PATRAKAAR | `Patrakaar` | `#56B4E9` |

Role card faces (`role_face_*`) use the matching role hue as the dominant
accent. Persona portraits may use the full brand surface palette plus the
sitter's role hue as an accent (a portrait is a character, not a badge — it
doesn't need to be dominated by the role color the way a card face does).

- [ ] Every color in the piece maps to a token above (or a tint/shade of one
      at consistent alpha — no arbitrary new hues).
- [ ] The role hue used (card faces, or the accent on a persona portrait)
      matches that persona/role's locked Okabe-Ito color exactly.

## 2. Line-weight consistency

The existing hand-struck glyphs
(`core/designsystem/src/commonMain/kotlin/com/kursi/designsystem/RoleGlyph.kt`)
define the house line language: strokes scale as
`baseStroke = side * 0.052 * weight`, boosted 1.12–1.7× at small sizes for
legibility, rendered with round caps/joins, and "intaglio" (engraved) —
a recessed shadow offset down-right, a lit highlight offset up-left, ink on
the engraving floor. New art doesn't have to be literally engraved, but must
read as the same weight of mark at the same viewing size:

- [ ] Line/stroke weight is visually consistent with the RoleGlyph reference
      at the same render size (no hairline-thin or poster-bold outliers next
      to the existing marks).
- [ ] The piece reads as a struck/engraved metal or stamped-paper mark, not a
      flat vector icon or a photoreal render — matches "License Raj Deco →
      Sarkari Noir" (lamplit, materialy, not glossy/modern-flat).
- [ ] Legible as a silhouette at the smallest size it will render at (a role
      face shown at chit-pip scale, a portrait shown as a small avatar).

## 3. CVD non-color channel (`RoleFramePattern`)

Every role-associated asset must carry its non-color discriminant so the game
stays readable under deuteranopia/protanopia — color alone is never the only
signal. Per `GaddiTheme.kt` (`RoleFramePattern`, `GaddiColors.roles[role].framePattern`):

| Role | Pattern | Description |
|---|---|---|
| NETA | `SolidRing` | Simple solid engraved ring |
| BHAI | `Hatched` | 45° hatch inside the ring |
| BABU | `Dotted` | Evenly spaced dots |
| JUGAADU | `Woven` | Interlocking braid pattern |
| VAKIL | `DoubleRule` | Two concentric rings |
| PATRAKAAR | `Ticked` | Ring with radial tick marks |

- [ ] `role_face_*` art embeds its role's frame pattern as a physical bezel
      element in the composition (not just implied by color) — visible and
      distinguishable from the other five patterns at render size.
- [ ] Pattern placement doesn't collide with or obscure the glyph/portrait it
      frames.

## 4. Contrast

- [ ] Foreground mark vs. background reaches WCAG-AA-equivalent contrast
      (≥ 3:1 for the mark against its immediate background) at the smallest
      render size the slot is used at.
- [ ] The piece survives a grayscale conversion — role identity must still be
      distinguishable by the frame pattern (§3) once color is removed, not
      just by hue.

## 5. Format & budget

- [ ] Filename matches the registry name exactly (see table above) — the
      compose-resources id is derived from the filename; a mismatch breaks
      the build, it doesn't silently miss.
- [ ] Raster (PNG/WebP), no vector `.xml` (not multiplatform-safe per the
      existing font-loading precedent in this module).
- [ ] Per spec §7.4: total asset track budget ≤ 8 MB per target. Individual
      persona portraits and role faces should be well under 200 KB each;
      hero-moment art (crest, tipped chair) under 500 KB each.

## 6. Licensing

- [ ] AI-generated art produced for this project is original work — no
      licensing entry needed (§9.1: "AI-generated art (own work)... are
      unrestricted").
- [ ] If any reference image, style LoRA, or generation pipeline was seeded
      with third-party material under a non-CC0/MIT/Apache/OFL license, that
      fact is flagged before submission — it does not pass this gate silently.

## Sign-off

A piece that passes every box above is added to
`GaddiArtRegistry.readySlots` in `GaddiArt.kt` (the single line that flips a
slot from fallback to asset) and its placeholder file is replaced in place.
Record the pass in the PR/commit description; no separate log file required.
