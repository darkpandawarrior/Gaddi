package com.kursi.designsystem.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kursi.designsystem.BrandTokens
import com.kursi.designsystem.GaddiColors
import com.kursi.designsystem.RoleGlyph
import com.kursi.designsystem.SeatAvatar
import com.kursi.engine.Role
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.moment_crest
import kursi.core.designsystem.generated.resources.moment_tipped_chair
import kursi.core.designsystem.generated.resources.portrait_babu_filewala
import kursi.core.designsystem.generated.resources.portrait_bhai_teja
import kursi.core.designsystem.generated.resources.portrait_dalla_tiwari
import kursi.core.designsystem.generated.resources.portrait_inspector_damaad
import kursi.core.designsystem.generated.resources.portrait_jugaadu_chhotu
import kursi.core.designsystem.generated.resources.portrait_maaji_anna
import kursi.core.designsystem.generated.resources.portrait_madam_sarpanch
import kursi.core.designsystem.generated.resources.portrait_netaji_vachan
import kursi.core.designsystem.generated.resources.portrait_seth_khokhawala
import kursi.core.designsystem.generated.resources.portrait_vakil_loophole
import kursi.core.designsystem.generated.resources.role_face_babu
import kursi.core.designsystem.generated.resources.role_face_bhai
import kursi.core.designsystem.generated.resources.role_face_jugaadu
import kursi.core.designsystem.generated.resources.role_face_neta
import kursi.core.designsystem.generated.resources.role_face_patrakaar
import kursi.core.designsystem.generated.resources.role_face_vakil
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// ═══════════════════════════════════════════════════════════════════════════════
//  Design spec §7.4 (asset layer) / §9 (art policy) — the art LOADING SEAM.
//
//  This file is the only thing that changes when real art lands: swap the
//  placeholder file at composeResources/drawable/<name>.png for the approved
//  image (same name), add its ArtSlot to GaddiArtRegistry.readySlots, done.
//  No call site elsewhere in the app needs to change — every consumer already
//  routes through GaddiRoleFace / GaddiPersonaPortrait / GaddiHeroMoment below,
//  which fall back to the existing Canvas RoleGlyph / SeatAvatar / a minimal
//  programmatic drawing whenever a slot isn't ready. Until Track 5 (art
//  production, §9) lands approved pieces, GaddiArtRegistry.readySlots is empty,
//  so every call site renders exactly what it renders today.
// ═══════════════════════════════════════════════════════════════════════════════

/** Hero-moment art slots — full-bleed brand moments, not per-role/per-persona. */
enum class HeroMoment { CREST, TIPPED_CHAIR }

/** One resolvable art slot: a persona portrait, a role card face, or a hero moment. */
sealed interface ArtSlot {
    data class PersonaPortrait(
        val personaId: String,
    ) : ArtSlot

    data class RoleFace(
        val role: Role,
    ) : ArtSlot

    data class Moment(
        val moment: HeroMoment,
    ) : ArtSlot
}

/** Resolution outcome for one [ArtSlot]. */
enum class ArtResolution { Asset, Fallback }

/**
 * Global kill-switch for the whole asset track — the "no-asset path" call out in §7.4
 * (web / size-budget). Every [resolveArt] call honors this before consulting
 * [GaddiArtRegistry]. Defaults on; a platform entry point can flip it off before first
 * composition (e.g. web under a load-size budget) to force every call site onto its
 * Canvas/programmatic fallback regardless of what's registered.
 */
object GaddiArtPolicy {
    var assetsEnabled: Boolean = true
}

/**
 * The loading seam itself: the set of [ArtSlot]s that have real, style-lock-approved art
 * (docs/art/STYLE_LOCK.md) checked in. Placeholder files exist today for every slot under
 * composeResources/drawable/ so the pipeline compiles and resolves end to end, but this set
 * stays EMPTY until a piece passes the style-lock QA gate — until then every call site below
 * renders its existing fallback, so the game looks exactly as it does today. See the file
 * header for what wiring a real piece in later requires.
 */
object GaddiArtRegistry {
    val readySlots: Set<ArtSlot> = emptySet()
}

/**
 * Pure resolution logic — no Compose/platform dependency, unit-testable standalone.
 * [readySlots] defaults to the real [GaddiArtRegistry]; tests pass a hypothetical set to
 * exercise the asset-present branch without mutating the (immutable, empty-until-art-lands)
 * production registry.
 */
fun resolveArt(
    slot: ArtSlot,
    readySlots: Set<ArtSlot> = GaddiArtRegistry.readySlots,
): ArtResolution =
    if (GaddiArtPolicy.assetsEnabled && slot in readySlots) {
        ArtResolution.Asset
    } else {
        ArtResolution.Fallback
    }

/** The 10 launch persona ids — must match [com.kursi.ai.PersonaPrompts] persona ids exactly. */
object GaddiPersonaIds {
    const val NETAJI_VACHAN = "netaji_vachan"
    const val BHAI_TEJA = "bhai_teja"
    const val BABU_FILEWALA = "babu_filewala"
    const val JUGAADU_CHHOTU = "jugaadu_chhotu"
    const val VAKIL_LOOPHOLE = "vakil_loophole"
    const val INSPECTOR_DAMAAD = "inspector_damaad"
    const val SETH_KHOKHAWALA = "seth_khokhawala"
    const val MADAM_SARPANCH = "madam_sarpanch"
    const val DALLA_TIWARI = "dalla_tiwari"
    const val MAAJI_ANNA = "maaji_anna"

    val all: List<String> =
        listOf(
            NETAJI_VACHAN,
            BHAI_TEJA,
            BABU_FILEWALA,
            JUGAADU_CHHOTU,
            VAKIL_LOOPHOLE,
            INSPECTOR_DAMAAD,
            SETH_KHOKHAWALA,
            MADAM_SARPANCH,
            DALLA_TIWARI,
            MAAJI_ANNA,
        )
}

private fun roleFaceDrawable(role: Role): DrawableResource =
    when (role) {
        Role.NETA -> Res.drawable.role_face_neta
        Role.BHAI -> Res.drawable.role_face_bhai
        Role.BABU -> Res.drawable.role_face_babu
        Role.JUGAADU -> Res.drawable.role_face_jugaadu
        Role.VAKIL -> Res.drawable.role_face_vakil
        Role.PATRAKAAR -> Res.drawable.role_face_patrakaar
    }

private fun personaPortraitDrawable(personaId: String): DrawableResource =
    when (personaId) {
        GaddiPersonaIds.NETAJI_VACHAN -> Res.drawable.portrait_netaji_vachan
        GaddiPersonaIds.BHAI_TEJA -> Res.drawable.portrait_bhai_teja
        GaddiPersonaIds.BABU_FILEWALA -> Res.drawable.portrait_babu_filewala
        GaddiPersonaIds.JUGAADU_CHHOTU -> Res.drawable.portrait_jugaadu_chhotu
        GaddiPersonaIds.VAKIL_LOOPHOLE -> Res.drawable.portrait_vakil_loophole
        GaddiPersonaIds.INSPECTOR_DAMAAD -> Res.drawable.portrait_inspector_damaad
        GaddiPersonaIds.SETH_KHOKHAWALA -> Res.drawable.portrait_seth_khokhawala
        GaddiPersonaIds.MADAM_SARPANCH -> Res.drawable.portrait_madam_sarpanch
        GaddiPersonaIds.DALLA_TIWARI -> Res.drawable.portrait_dalla_tiwari
        GaddiPersonaIds.MAAJI_ANNA -> Res.drawable.portrait_maaji_anna
        else -> error("Unknown persona id \"$personaId\" is in GaddiArtRegistry.readySlots but has no drawable mapping")
    }

private fun momentDrawable(moment: HeroMoment): DrawableResource =
    when (moment) {
        HeroMoment.CREST -> Res.drawable.moment_crest
        HeroMoment.TIPPED_CHAIR -> Res.drawable.moment_tipped_chair
    }

/**
 * Role card face art (§7.4 asset layer): the AI-generated in-style card face for [role] when
 * style-lock-approved art is registered, else the existing hand-struck [RoleGlyph] intaglio
 * mark — today's look, unchanged, until real art lands.
 */
@Composable
fun GaddiRoleFace(
    role: Role,
    modifier: Modifier = Modifier,
    tint: Color = GaddiColors.forRole(role).color,
) {
    when (resolveArt(ArtSlot.RoleFace(role))) {
        ArtResolution.Asset ->
            Image(painter = painterResource(roleFaceDrawable(role)), contentDescription = null, modifier = modifier)
        ArtResolution.Fallback -> RoleGlyph(role = role, modifier = modifier, tint = tint)
    }
}

/**
 * Persona portrait art (§7.4 asset layer): the AI-generated in-style portrait for [personaId]
 * when approved art is registered, else the existing brass [SeatAvatar] initial roundel.
 */
@Composable
fun GaddiPersonaPortrait(
    personaId: String,
    initial: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    when (resolveArt(ArtSlot.PersonaPortrait(personaId))) {
        ArtResolution.Asset ->
            Image(
                painter = painterResource(personaPortraitDrawable(personaId)),
                contentDescription = null,
                modifier = modifier,
            )
        ArtResolution.Fallback -> SeatAvatar(initial = initial, color = color, modifier = modifier)
    }
}

/**
 * Hero-moment art (§7.4 asset layer / §9 art policy): the GADDI crest or the tipped-chair
 * elimination moment. Falls back to a small self-contained Canvas placeholder — a gold ring
 * for CREST, a simple chair silhouette for TIPPED_CHAIR — deliberately independent of the
 * existing overlay-specific moment primitives (which need anchors/progress/seat state this
 * generic slot doesn't have).
 */
@Composable
fun GaddiHeroMoment(
    moment: HeroMoment,
    modifier: Modifier = Modifier,
    tint: Color = BrandTokens.GoldAntique,
) {
    when (resolveArt(ArtSlot.Moment(moment))) {
        ArtResolution.Asset ->
            Image(painter = painterResource(momentDrawable(moment)), contentDescription = null, modifier = modifier)
        ArtResolution.Fallback ->
            Canvas(modifier = modifier.fillMaxSize()) {
                when (moment) {
                    HeroMoment.CREST -> drawCrestFallback(tint)
                    HeroMoment.TIPPED_CHAIR -> drawChairFallback(tint)
                }
            }
    }
}

private fun DrawScope.drawCrestFallback(tint: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val r = minOf(size.width, size.height) * 0.32f
    drawCircle(tint.copy(alpha = 0.18f), radius = r, center = center)
    drawCircle(tint, radius = r, center = center, style = Stroke(2.dp.toPx()))
}

private const val CHAIR_BACK_HALF_WIDTH = 0.4f
private const val CHAIR_BACK_TOP_OFFSET = 0.5f
private const val CHAIR_BACK_ARC_WIDTH = 0.8f
private const val CHAIR_BACK_ARC_HEIGHT = 0.35f
private const val CHAIR_LEG_HALF_WIDTH = 0.3f
private const val CHAIR_LEG_LENGTH = 0.45f

private fun DrawScope.drawChairFallback(tint: Color) {
    val w = size.width * 0.5f
    val h = size.height * 0.6f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val sw = 3.dp.toPx()
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - w * CHAIR_BACK_HALF_WIDTH, cy - h * CHAIR_BACK_TOP_OFFSET),
        size = Size(w * CHAIR_BACK_ARC_WIDTH, h * CHAIR_BACK_ARC_HEIGHT),
        style = Stroke(sw),
    )
    drawLine(tint, Offset(cx - w * CHAIR_BACK_HALF_WIDTH, cy), Offset(cx + w * CHAIR_BACK_HALF_WIDTH, cy), sw)
    drawLine(
        tint,
        Offset(cx - w * CHAIR_LEG_HALF_WIDTH, cy),
        Offset(cx - w * CHAIR_LEG_HALF_WIDTH, cy + h * CHAIR_LEG_LENGTH),
        sw,
    )
    drawLine(
        tint,
        Offset(cx + w * CHAIR_LEG_HALF_WIDTH, cy),
        Offset(cx + w * CHAIR_LEG_HALF_WIDTH, cy + h * CHAIR_LEG_LENGTH),
        sw,
    )
}
