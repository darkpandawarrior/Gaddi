package com.kursi.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.engine.Role
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.dm_mono_medium
import kursi.core.designsystem.generated.resources.marcellus_regular
import kursi.core.designsystem.generated.resources.rozha_one_regular
import org.jetbrains.compose.resources.Font

// ═══════════════════════════════════════════════════════════════════════════════
// §8 LICENSE RAJ DECO — "teak-and-brass council chamber" visual identity.
// Tokens approved in gaddi-plan/docs/15a_retro_identity.md §DIRECTION 2.
// Fonts: Rozha One (OFL), Marcellus (OFL), DM Mono (OFL) — bundled via
// compose.components.resources under composeResources/font/. All three load
// on jvm (desktop), Android, iOS (arm64 + simulator), and wasmJs targets.
// ═══════════════════════════════════════════════════════════════════════════════

// ─────────────────────────── Brand palette (teak / brass / enamel) ────────────

/** License Raj Deco brand surface palette. */
object BrandTokens {
    // Ground — warm teak/oxblood

    /** Teak dark: deep warm council-chamber ground. */
    val TeakDark = Color(0xFF2A1B14)

    /** Teak mid: slightly lighter warm teak — gradient end. */
    val TeakMid = Color(0xFF3A2418)

    /** Ink / outer window background — deepest teak. */
    val TeakInk = Color(0xFF1E1008)

    /** Pure black, used only as a shadow colour. Not a surface: nothing in the app is this dark. */
    val ShadowBlack = Color(0xFF000000)

    /** Cool phosphor cyan — the "sarkari teleprinter" scanline sheen on live surfaces. */
    val PhosphorCyan = Color(0xFF8FE7DA)

    // Decision-quality verdicts (Review screen). A four-stop ramp from "best move" to "costly
    // miss", ending on StampRed. These lived as raw 0xFF... literals inside ReviewScreen until
    // the quality pass; a colour in a screen is a token that escaped the design system.

    /** Verdict SHARP — best or near-best move. */
    val VerdictSharp = Color(0xFF6FCF97)

    /** Verdict FINE — close enough. */
    val VerdictFine = Color(0xFFB7C66B)

    /** Verdict LOOSE — leaked some EV. */
    val VerdictLoose = Color(0xFFE0B354)

    // Metal — aged brass / antique gold

    /** Aged brass: primary bezel and border metal. */
    val BrassAged = Color(0xFFC99A3B)

    /** Antique gold: highlights, foil, coin tops. */
    val GoldAntique = Color(0xFFE8C874)

    /** Brass dark: shadow/rim on brass elements. */
    val BrassDark = Color(0xFF8A6A28)

    // Paper / certificate

    /** Document cream: card faces, certificate paper. */
    val PaperCream = Color(0xFFEDE3CC)

    /** Cream ink: text on certificate paper. */
    val CreamInk = Color(0xFF3A2C14)

    /** Parchment tint: slightly deeper panel insets. */
    val PaperDeep = Color(0xFFD4C9A8)

    // Danger / stamp

    /** Oxblood: danger / elimination stamp. */
    val Oxblood = Color(0xFF7A2E2E)

    /** Stamp red: bright danger seal. */
    val StampRed = Color(0xFFC1272D)

    // Semantic (kept functional)

    /** Success / truth — muted civil green. */
    val CivilGreen = Color(0xFF2D6A4F)

    /** Block — civic blue. */
    val CivicBlue = Color(0xFF1B4F72)

    /** Pending — amber */
    val PendingAmber = Color(0xFFD4A017)
}

// ─────────────────────────── Semantic palette ───────────────────────────

/** Semantic outcome colors for the deco identity. */
object GaddiSemantics {
    val Success = Color(0xFF2D9E5A)
    val Danger = BrandTokens.StampRed
    val Block = Color(0xFF3A86FF)
    val Pending = BrandTokens.PendingAmber
}

// ─────────────────────────── Legacy surface aliases ───────────────────────────
// Kept so :feature:game can still reference GaddiFeltColors without a compile error.
// All values now map to deco equivalents.

/** Surface palette — deco remapping. Legacy names kept for :feature:game compat. */
object GaddiFeltColors {
    /** Was FeltTop (green). Now: teak dark — the council-chamber ground. */
    val FeltTop = BrandTokens.TeakDark

    /** Was FeltBottom. Now: teak mid. */
    val FeltBottom = BrandTokens.TeakMid

    /** App background. Now: deep teak ink. */
    val Ink = BrandTokens.TeakInk

    /** Inset surface. Now: teak dark. */
    val Surface1 = BrandTokens.TeakDark

    /** Mid-level surface. Now: teak mid. */
    val Surface2 = BrandTokens.TeakMid

    /** Raised surface. Now: slightly lighter teak. */
    val Surface3 = Color(0xFF4A3020)

    /** Gold rim — aged brass bezel. */
    val GoldRim = BrandTokens.BrassAged

    /** Khokha coin / bright gold accent. */
    val GoldCoin = BrandTokens.GoldAntique

    /** Coin rim / shadow — brass dark. */
    val GoldRimDark = BrandTokens.BrassDark
}

// ─────────────────────────── Text / neutral palette ───────────────────────────

/** Neutral text and disabled colors — deco remapping. */
object GaddiNeutrals {
    /** Primary text on teak: warm near-white. */
    val TextPrimary = Color(0xFFF2E8D0)

    /** Secondary text: muted parchment. */
    val TextSecondary = Color(0xFFCBB882)

    /** Muted text: aged brass tint. */
    val TextMuted = Color(0xFF8C7045)

    /** Disabled text: dim teak. */
    val TextDisabled = Color(0xFF5A4030)

    /** Cream — used for text on certificate paper or in role cards. */
    val Cream = BrandTokens.PaperCream
}

// ─────────────────────────── Role hues (Okabe-Ito CVD-safe) — UNCHANGED ────────

/** Role hues — locked Okabe-Ito palette. MUST NOT CHANGE per CVD contract. */
object GaddiRoleHues {
    val Neta = Color(0xFF0072B2)
    val Bhai = Color(0xFFD55E00)
    val Babu = Color(0xFF009E73)
    val Jugaadu = Color(0xFFE69F00)
    val Vakil = Color(0xFFCC79A7)

    /**
     * PATRAKAAR — Okabe-Ito "sky blue". The 6th canonical Okabe-Ito hue, deliberately the
     * lighter blue so it stays CVD-distinct from NETA's darker blue (the pair is designed to be
     * separable under deutan/protan vision by lightness), and distinct from all four other hues.
     */
    val Patrakaar = Color(0xFF56B4E9)
}

// ─────────────────────────── Seat colors (10 slots) — UNCHANGED ───────────────

/** 10 distinct seat identity colors — separate from role hues. Unchanged. */
object GaddiSeatColors {
    val all: List<Color> =
        listOf(
            Color(0xFFE63946), // 0 — red
            Color(0xFF457B9D), // 1 — steel
            Color(0xFFF4A261), // 2 — apricot
            Color(0xFF2A9D8F), // 3 — teal
            Color(0xFF9B5DE5), // 4 — violet
            Color(0xFFFFCA3A), // 5 — gold
            Color(0xFFFF6B9D), // 6 — rose
            Color(0xFF6A994E), // 7 — olive
            Color(0xFF4CC9F0), // 8 — sky
            Color(0xFFBC6C25), // 9 — umber
        )

    operator fun get(seatIndex: Int): Color = all[seatIndex % all.size]
}

// ─────────────────────────── Texture tokens ───────────────────────────────────

/**
 * TextureTokens — describes the License Raj Deco material grammar.
 * Actual rendering lives in DecoTexture.kt (Canvas-based, multiplatform-safe).
 *
 * - GUILLOCHÉ: interfering sine-wave line pattern on bezels/borders — drawn via Canvas.
 * - DEBOSS: inner shadow on cream card faces — drawn via canvas InnerShadow.
 * - PAPER GRAIN: subtle noise overlay on card/paper surfaces.
 * - BRASS SPECULAR: animated linear-gradient highlight on brass elements.
 */
object TextureTokens {
    /** Alpha of the guilloché line overlay on brass bezels. */
    const val guillocheLinesAlpha = 0.18f

    /** Alpha of the paper grain noise overlay on cream surfaces. */
    const val paperGrainAlpha = 0.06f

    /** Alpha of the engraved hatch on dark teak panels. */
    const val teakHatchAlpha = 0.04f

    /** Alpha of the ghosted chair-in-sunburst centre emblem. */
    const val emblomAlpha = 0.035f

    /** Number of guilloché sine waves per 100dp of width. */
    const val guillocheDensity = 12

    /** Brass specular highlight width fraction (0..1). */
    const val brassSpecularWidth = 0.25f

    /**
     * Film-grain per-pixel noise amplitude added by the [com.kursi.designsystem.shader.feltMaterial]
     * runtime-shader pass (additive, centred on zero — keep tiny, this is texture not TV static).
     */
    const val filmGrainIntensity = 0.018f

    /**
     * Warm bloom lift strength around the key-light pool, added by the
     * [com.kursi.designsystem.shader.feltMaterial] runtime-shader pass.
     */
    const val warmBloomStrength = 0.05f
}

// ─────────────────────────── Role frame patterns (CVD non-color channel) ─────

/**
 * Per-role bezel/frame pattern for the guilloché engraving.
 * These are the non-color CVD-safe discriminants: solid ring / hatched /
 * dotted / woven / double-rule. Rendered in DecoTexture guilloché composable.
 */
enum class RoleFramePattern {
    SolidRing, // NETA   — simple solid engraved ring
    Hatched, // BHAI   — 45° hatch inside the ring
    Dotted, // BABU   — evenly spaced dots
    Woven, // JUGAADU — interlocking braid pattern
    DoubleRule, // VAKIL  — two concentric rings
    Ticked, // PATRAKAAR — ring with radial tick marks (a press/registration bezel)
}

// ─────────────────────────── Role visual ──────────────────────────────────────

/** Per-role visual descriptor — deco identity. */
@Immutable
data class RoleVisual(
    val role: Role,
    /** Okabe-Ito base hue — the enamel badge fill. */
    val color: Color,
    /** Same hue at ~+10–12% lightness for text on dark teak. */
    val lightColor: Color,
    /** Satirical character name. */
    val characterName: String,
    /** Character sub-title. */
    val title: String,
    /** Hindi/desi flavor name for the action. */
    val hindiAction: String,
    /** Plain-language power action line — spec §3. */
    val actionLine: String,
    /**
     * Short, fixed-width-safe claim line for the Medium spotlight medallion. The full
     * [actionLine] ("ACTION · Tax +3 Khokhas") overflows the narrow claim-card band and
     * TextAutoSize does not reliably shrink in the live app, clipping to "…Tax +3 Kh…".
     * This pre-abbreviated form is deterministic: it always fits the Medium card in ≤2
     * lines at a fixed font. Keep it under ~18 chars.
     */
    val claimLineShort: String,
    /** Plain-language block line — spec §3. */
    val blockLine: String,
    /** CVD non-color channel: engraved bezel frame pattern. */
    val framePattern: RoleFramePattern,
)

/** Stateless lookup — initialized once at app start. Never mutated. */
object GaddiColors {
    val roles: Map<Role, RoleVisual> =
        mapOf(
            Role.NETA to
                RoleVisual(
                    role = Role.NETA,
                    color = GaddiRoleHues.Neta,
                    lightColor = Color(0xFF3399CC),
                    characterName = "Netaji Vachan",
                    title = "The Politician",
                    hindiAction = "Ghotala",
                    actionLine = "ACTION · Tax +3 Khokhas",
                    claimLineShort = "Tax +3",
                    blockLine = "BLOCKS · Foreign Aid",
                    framePattern = RoleFramePattern.SolidRing,
                ),
            Role.BHAI to
                RoleVisual(
                    role = Role.BHAI,
                    color = GaddiRoleHues.Bhai,
                    lightColor = Color(0xFFE87733),
                    characterName = "Bhai Teja",
                    title = "The Don",
                    hindiAction = "Supari",
                    actionLine = "ACTION · Assassinate −3 (target loses influence)",
                    claimLineShort = "Supari −3",
                    blockLine = "BLOCKS · —",
                    framePattern = RoleFramePattern.Hatched,
                ),
            Role.BABU to
                RoleVisual(
                    role = Role.BABU,
                    color = GaddiRoleHues.Babu,
                    lightColor = Color(0xFF00C48D),
                    characterName = "Babu Filewala",
                    title = "The Bureaucrat",
                    hindiAction = "Vasooli",
                    actionLine = "ACTION · Steal 2 Khokhas",
                    claimLineShort = "Steal 2",
                    blockLine = "BLOCKS · Steal",
                    framePattern = RoleFramePattern.Dotted,
                ),
            Role.JUGAADU to
                RoleVisual(
                    role = Role.JUGAADU,
                    color = GaddiRoleHues.Jugaadu,
                    lightColor = Color(0xFFFFBF33),
                    characterName = "Chhotu",
                    title = "The Fixer",
                    hindiAction = "Setting",
                    actionLine = "ACTION · Exchange cards",
                    claimLineShort = "Exchange",
                    blockLine = "BLOCKS · Steal",
                    framePattern = RoleFramePattern.Woven,
                ),
            Role.VAKIL to
                RoleVisual(
                    role = Role.VAKIL,
                    color = GaddiRoleHues.Vakil,
                    lightColor = Color(0xFFDD99BF),
                    characterName = "Vakil Saab",
                    title = "The Lawyer",
                    hindiAction = "—",
                    actionLine = "ACTION · None",
                    claimLineShort = "No action",
                    blockLine = "BLOCKS · Assassinate",
                    framePattern = RoleFramePattern.DoubleRule,
                ),
            Role.PATRAKAAR to
                RoleVisual(
                    role = Role.PATRAKAAR,
                    color = GaddiRoleHues.Patrakaar,
                    lightColor = Color(0xFF8CCBF0),
                    characterName = "Patrakaar Devi",
                    title = "The Journalist",
                    hindiAction = "Jaanch",
                    actionLine = "ACTION · Investigate (peek a card, force a redraw)",
                    claimLineShort = "Jaanch",
                    blockLine = "BLOCKS · —",
                    framePattern = RoleFramePattern.Ticked,
                ),
        )

    fun forRole(role: Role): RoleVisual = roles[role] ?: error("No RoleVisual for $role — add to GaddiColors.roles")
}

// ─────────────────────────── Seal palette ─────────────────────────────────────

/**
 * Seal colors — per-role identity tints for crests, log dots, matrix cells, chit headers.
 * Distinct from [GaddiSeatColors] (player slots).
 */
object GaddiSealPalette {
    /** NETA — khadi white with saffron edge (use as badge fill). */
    val Neta = Color(0xFFF5F0E8)

    /** BHAI — blood maroon. */
    val Bhai = Color(0xFF8B1A1A)

    /** BABU — sarkari grey-green. */
    val Babu = Color(0xFF5A7A6A)

    /** JUGAADU — jugaad yellow. */
    val Jugaadu = Color(0xFFD4A017)

    /** VAKIL — ink black with red-tab feel (use red-edge tint). */
    val Vakil = Color(0xFF1A1A2E)

    /** PATRAKAAR — newsprint blue-grey (the press card / ink-on-paper tint). */
    val Patrakaar = Color(0xFF2E5A78)
}

// ─────────────────────────── Typography tokens ────────────────────────────────

/**
 * GaddiFonts — holds the three real OFL font families loaded from bundled TTFs.
 * Built in a @Composable context via [rememberGaddiFonts] so that
 * org.jetbrains.compose.resources.Font() resolves on all targets (jvm, Android,
 * iOS, wasmJs).
 */
data class GaddiFonts(
    val rozhaOne: FontFamily,
    val marcellus: FontFamily,
    val dmMono: FontFamily,
)

/**
 * Loads the three bundled font families from composeResources/font/.
 * Call once per theme composition; the result is stable across recompositions
 * because Font() returns the same object for the same resource key.
 */
@Composable
private fun rememberGaddiFonts(): GaddiFonts =
    GaddiFonts(
        rozhaOne = FontFamily(Font(Res.font.rozha_one_regular, weight = FontWeight.Normal)),
        marcellus = FontFamily(Font(Res.font.marcellus_regular, weight = FontWeight.Normal)),
        dmMono = FontFamily(Font(Res.font.dm_mono_medium, weight = FontWeight.Medium)),
    )

/**
 * GaddiType — License Raj Deco type scale.
 *
 * Each token resolves its [fontFamily] through [LocalGaddiFonts] at read time (a
 * `@Composable get()`, not a stored constant), so every call site — 500+ across the
 * app — picks up the real bundled Rozha One / Marcellus / DM Mono the moment it reads
 * under [GaddiTheme], with zero call-site changes. Outside a themed composition (bare
 * @Preview, non-KMP-resource contexts) [LocalGaddiFonts] falls back to system
 * serif/mono, so previews keep rendering.
 *
 * Slot assignments:
 *   display / cardRole / title_md → Rozha One (deco display)
 *   title / name / body / label* / caption / title_sm / label_micro / label_sm / label_md → Marcellus (body serif)
 *   numeric / numeral_sm → DM Mono (tabular mono)
 */
object GaddiType {
    /** 28 / 700 — turn banner, Rozha One display. */
    val display: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalGaddiFonts.current.rozhaOne,
                fontSize = 28.sp,
                fontWeight = FontWeight(700),
                lineHeight = 34.sp,
                letterSpacing = 0.sp,
            )

    /** 26 / 700 — role names on hand cards. */
    val cardRole: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalGaddiFonts.current.rozhaOne,
                fontSize = 26.sp,
                fontWeight = FontWeight(700),
                lineHeight = 30.sp,
            )

    /** 20 / 700 — status spine, action buttons. Marcellus-style. */
    val title: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalGaddiFonts.current.marcellus,
                fontSize = 20.sp,
                fontWeight = FontWeight(700),
                lineHeight = 26.sp,
            )

    /** 17 / 700 — player names. */
    val name: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalGaddiFonts.current.marcellus,
                fontSize = 17.sp,
                fontWeight = FontWeight(700),
                lineHeight = 22.sp,
            )

    /** 15 / 500 — power lines, log rows. Small-caps feel. */
    val body: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalGaddiFonts.current.marcellus,
                fontSize = 15.sp,
                fontWeight = FontWeight(500),
                lineHeight = 20.sp,
            )

    /** 13 / 600 uppercase + tracking — chips, coin counts, button sublabels. */
    val label: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalGaddiFonts.current.marcellus,
                fontSize = 13.sp,
                fontWeight = FontWeight(600),
                lineHeight = 18.sp,
                letterSpacing = 0.8.sp,
            )

    /** 11 / 600 — pip labels, timers. DM Mono for numerals. */
    val caption: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalGaddiFonts.current.marcellus,
                fontSize = 11.sp,
                fontWeight = FontWeight(600),
                lineHeight = 14.sp,
                letterSpacing = 0.3.sp,
            )

    /** Monospaced numeric — coin counts, serial numbers. DM Mono. */
    val numeric: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalGaddiFonts.current.dmMono,
                fontSize = 14.sp,
                fontWeight = FontWeight(600),
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
            )

    // ── Refined density ramp (Step 0) ─────────────────────────────

    /** 10/12 — cost chips, pip counts, log timestamps, affordance hints. */
    val label_micro: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalGaddiFonts.current.marcellus,
                fontSize = 10.sp,
                fontWeight = FontWeight(500),
                lineHeight = 12.sp,
                letterSpacing = 0.2.sp,
            )

    /** 11/14 — action name in dock chips, opponent meta, log actor. */
    val label_sm: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalGaddiFonts.current.marcellus,
                fontSize = 11.sp,
                fontWeight = FontWeight(600),
                lineHeight = 14.sp,
                letterSpacing = 0.5.sp,
            )

    /** 13/16 — opponent nameplate name, dock cost display. */
    val label_md: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalGaddiFonts.current.marcellus,
                fontSize = 13.sp,
                fontWeight = FontWeight(700),
                lineHeight = 16.sp,
                letterSpacing = 0.3.sp,
            )

    /** 15/18 — status spine text, popover title. */
    val title_sm: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalGaddiFonts.current.marcellus,
                fontSize = 15.sp,
                fontWeight = FontWeight(700),
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
            )

    /** 18/22 — reveal card persona name. */
    val title_md: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalGaddiFonts.current.rozhaOne,
                fontSize = 18.sp,
                fontWeight = FontWeight(700),
                lineHeight = 22.sp,
                letterSpacing = 0.sp,
            )

    /** 13 tabular — coin/treasury numerals (monospaced). */
    val numeral_sm: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalGaddiFonts.current.dmMono,
                fontSize = 13.sp,
                fontWeight = FontWeight(600),
                lineHeight = 16.sp,
                letterSpacing = 0.sp,
            )
}

/**
 * Builds a [Typography] instance with real bundled fonts patched in.
 * Rozha One → display slots; Marcellus → body/title slots; DM Mono → label slots
 * that map to numeral/mono usage (labelSmall in M3 = our caption/numeric).
 */
@Composable
private fun rememberGaddiTypography(fonts: GaddiFonts): Typography {
    val rz = fonts.rozhaOne
    val mc = fonts.marcellus
    val dm = fonts.dmMono
    return Typography(
        displayLarge = GaddiType.display.copy(fontFamily = rz),
        displayMedium = GaddiType.display.copy(fontFamily = rz),
        displaySmall = GaddiType.display.copy(fontFamily = rz),
        headlineLarge = GaddiType.title.copy(fontFamily = mc),
        headlineMedium = GaddiType.title.copy(fontFamily = mc),
        headlineSmall = GaddiType.title.copy(fontFamily = mc),
        titleLarge = GaddiType.title.copy(fontFamily = mc),
        titleMedium = GaddiType.name.copy(fontFamily = mc),
        titleSmall = GaddiType.name.copy(fontFamily = mc),
        bodyLarge = GaddiType.body.copy(fontFamily = mc),
        bodyMedium = GaddiType.body.copy(fontFamily = mc),
        bodySmall = GaddiType.body.copy(fontFamily = mc),
        labelLarge = GaddiType.label.copy(fontFamily = mc),
        labelMedium = GaddiType.label.copy(fontFamily = mc),
        labelSmall = GaddiType.numeral_sm.copy(fontFamily = dm),
    )
}

// ─────────────────────────── GaddiDimens ─────────────────────────────────────

/**
 * GaddiDimens — 4dp grid spacing/radii/stroke density scale.
 * All new components use these; hard-coded px/dp constants are being phased out.
 */
object GaddiDimens {
    // ── Spacing (4dp grid) ──────────────────────────────────────────────
    val space_xs: androidx.compose.ui.unit.Dp = 4.dp
    val space_sm: androidx.compose.ui.unit.Dp = 8.dp
    val space_md: androidx.compose.ui.unit.Dp = 12.dp
    val space_lg: androidx.compose.ui.unit.Dp = 16.dp
    val space_xl: androidx.compose.ui.unit.Dp = 24.dp

    // ── Radii ────────────────────────────────────────────────────────────
    val r_sm: androidx.compose.ui.unit.Dp = 6.dp
    val r_md: androidx.compose.ui.unit.Dp = 10.dp
    val r_lg: androidx.compose.ui.unit.Dp = 14.dp

    // ── Stroke weights ───────────────────────────────────────────────────

    /** Hairline: default card/plate edge — brass at 40% alpha. */
    val stroke_hairline: androidx.compose.ui.unit.Dp = 0.75.dp

    /** Idle ring: opponent plate state ring when inactive. */
    val stroke_ring_idle: androidx.compose.ui.unit.Dp = 1.dp

    /** Active ring: thick ring for acting/targetable states. */
    val stroke_ring_active: androidx.compose.ui.unit.Dp = 2.dp

    /** Soft warm drop shadow depth. */
    val shadow_soft: androidx.compose.ui.unit.Dp = 3.dp
}

// ─────────────────────────── CompositionLocals ───────────────────────────

val LocalGaddiColors = staticCompositionLocalOf { GaddiColors }

/**
 * The real bundled OFL font families, provided by [GaddiTheme]. The [GaddiType]
 * token styles ship with [FontFamily.Serif]/[FontFamily.Monospace] placeholders so
 * the object can initialize without a @Composable resource context; this Local lets
 * in-game composables pull the genuine Rozha One / Marcellus / DM Mono on the table
 * (status-spine verdicts, claim medallion, reveal names, turn banner — M4 §3).
 *
 * Default falls back to system serif/mono so non-themed previews still render.
 */
val LocalGaddiFonts =
    staticCompositionLocalOf {
        GaddiFonts(
            rozhaOne = FontFamily.Serif,
            marcellus = FontFamily.Serif,
            dmMono = FontFamily.Monospace,
        )
    }

/**
 * Patches a [GaddiType] token style with the real Rozha One display serif. Use on
 * in-game HERO text so the deco display face lands on the table, not just on app-flow
 * screens routed through MaterialTheme.typography. Read inside a composition under
 * [GaddiTheme].
 *
 * ```kotlin
 * Text("Aapki baari", style = GaddiType.display.rozha())
 * ```
 */
@Composable
fun TextStyle.rozha(): TextStyle = this.copy(fontFamily = LocalGaddiFonts.current.rozhaOne)

/** Patches a token style with the real Marcellus body serif. */
@Composable
fun TextStyle.marcellus(): TextStyle = this.copy(fontFamily = LocalGaddiFonts.current.marcellus)

/** Patches a token style with the real DM Mono numeral face. */
@Composable
fun TextStyle.dmMono(): TextStyle = this.copy(fontFamily = LocalGaddiFonts.current.dmMono)

/**
 * Whether the player has reduced motion on (spec §10). Provided once at the [GameScreen]
 * root from `AppPrefs.reducedMotionFlow`; read by shared juice sites (press feedback, card
 * lift/flip springs) that would otherwise need a `reducedMotion` parameter threaded through
 * every call site. Defaults false so non-game screens/previews keep today's behavior.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

// ─────────────────────────── Material3 dark scheme (deco palette) ─────────────

private val GaddiDecoColorScheme =
    darkColorScheme(
        primary = BrandTokens.BrassAged,
        onPrimary = BrandTokens.TeakDark,
        primaryContainer = BrandTokens.BrassDark,
        onPrimaryContainer = BrandTokens.PaperCream,
        secondary = BrandTokens.TeakMid,
        onSecondary = GaddiNeutrals.TextPrimary,
        background = BrandTokens.TeakInk,
        onBackground = GaddiNeutrals.TextPrimary,
        surface = BrandTokens.TeakMid,
        onSurface = GaddiNeutrals.TextPrimary,
        surfaceVariant = Color(0xFF4A3020),
        onSurfaceVariant = GaddiNeutrals.TextSecondary,
        error = BrandTokens.StampRed,
        onError = GaddiNeutrals.TextPrimary,
        outline = BrandTokens.BrassAged,
    )

// ─────────────────────────── Theme entry point ───────────────────────────

/**
 * Root theme composable for all Gaddi UI — License Raj Deco identity.
 *
 * Wraps [MaterialTheme] with the teak-brass-enamel dark scheme and deco typography.
 * Loads bundled OFL fonts (Rozha One, Marcellus, DM Mono) via
 * compose.components.resources and patches them into the Typography.
 * Provides [GaddiColors] via [LocalGaddiColors].
 *
 * ```kotlin
 * GaddiTheme {
 *     GameScreen(...)
 * }
 * ```
 */
@Composable
fun GaddiTheme(content: @Composable () -> Unit) {
    val fonts = rememberGaddiFonts()
    val typography = rememberGaddiTypography(fonts)
    MaterialTheme(
        colorScheme = GaddiDecoColorScheme,
        typography = typography,
    ) {
        CompositionLocalProvider(
            LocalGaddiFonts provides fonts,
            content = content,
        )
    }
}
