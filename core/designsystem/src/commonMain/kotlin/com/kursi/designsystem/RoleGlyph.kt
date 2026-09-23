package com.kursi.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.kursi.engine.Role

// ═══════════════════════════════════════════════════════════════════════════════
//  M4 §5 — BESPOKE INTAGLIO ROLE GLYPHS
//
//  Five hand-struck role marks drawn entirely on Canvas (NO Material icons, NO
//  AGSL/RuntimeShader) — multiplatform-safe across jvm/android/ios/wasmJs.
//
//    NETA     — speaker's chair on a podium dais (the seat of power)
//    BHAI     — vermillion blade crossing a clenched fist
//    BABU     — stacked file folder with a pressed round stamp
//    JUGAADU  — crossed adjustable wrench + zip-tie loop (the fixer's kit)
//    VAKIL    — balance scales over a gavel
//    PATRAKAAR— a clipped PRESS card with a pen-nib (the journalist / Inquisitor)
//
//  Each is rendered "intaglio" (engraved/deboss): the mark is incised into the
//  surface, so it carries
//    1. a recessed shadow offset down-right (the cut wall in shadow),
//    2. a lit highlight offset up-left (the bevelled lip catching light),
//    3. the role-hue ink stroke/fill on the engraving floor.
//  This reads as struck metal at any size and stays legible as a silhouette.
//
//  Geometry is authored on a normalised 100×100 box and scaled to the draw size,
//  so the same path is crisp from a 10dp chit pip to a 96dp role-card centre.
// ═══════════════════════════════════════════════════════════════════════════════

/** One stroked path of a glyph, with its weight as a multiple of the glyph's base stroke. */
private data class GlyphStroke(
    val path: Path,
    val weight: Float,
)

/** One filled dot of a glyph, on the same normalised 0..100 canvas as the paths. */
private data class GlyphDot(
    val cx: Float,
    val cy: Float,
    val radius: Float,
)

/**
 * Drawing recipe for one role glyph on a normalised 0..100 canvas.
 *
 * [GlyphStroke] and [GlyphDot] replace the `Pair<Path, Float>` and `Triple<Float, Float, Float>`
 * these used to be. `crown to 0.7f` and `Triple(64f, 62f, 2.4f)` gave the reader no way to tell a
 * stroke weight from a radius or an x from a y; `GlyphStroke(crown, weight = 0.7f)` does.
 */
private data class GlyphSpec(
    /** Strokes, each with its relative weight (x base). */
    val strokes: List<GlyphStroke> = emptyList(),
    /** Solid fills (e.g. the blade body, the stamp disc). */
    val fills: List<Path> = emptyList(),
    /** Filled dots. */
    val dots: List<GlyphDot> = emptyList(),
)

private const val UNIT = 100f

// ─────────────────────────── Path builders (per role) ─────────────────────────

private fun netaSpec(): GlyphSpec {
    // Speaker's chair seated on a stepped dais — the "gaddi" itself.
    val seatBack =
        Path().apply {
            // tall ornamented back
            moveTo(38f, 20f)
            lineTo(62f, 20f)
            lineTo(62f, 52f)
            lineTo(38f, 52f)
            close()
        }
    val crown =
        Path().apply {
            // small finials / crown atop the back
            moveTo(38f, 20f)
            lineTo(34f, 13f)
            lineTo(42f, 18f)
            moveTo(62f, 20f)
            lineTo(66f, 13f)
            lineTo(58f, 18f)
            moveTo(46f, 18f)
            lineTo(50f, 11f)
            lineTo(54f, 18f)
        }
    val seat =
        Path().apply {
            moveTo(32f, 52f)
            lineTo(68f, 52f) // seat slab
        }
    val arms =
        Path().apply {
            moveTo(32f, 52f)
            lineTo(32f, 60f)
            moveTo(68f, 52f)
            lineTo(68f, 60f)
        }
    val legs =
        Path().apply {
            moveTo(38f, 60f)
            lineTo(38f, 70f)
            moveTo(62f, 60f)
            lineTo(62f, 70f)
        }
    val dais =
        Path().apply {
            // two-step podium dais
            moveTo(28f, 70f)
            lineTo(72f, 70f)
            moveTo(24f, 80f)
            lineTo(76f, 80f)
            moveTo(28f, 70f)
            lineTo(24f, 80f)
            moveTo(72f, 70f)
            lineTo(76f, 80f)
        }
    return GlyphSpec(
        fills = listOf(seatBack),
        strokes =
            listOf(
                GlyphStroke(crown, weight = 0.7f),
                GlyphStroke(seat, weight = 1.15f),
                GlyphStroke(arms, weight = 0.9f),
                GlyphStroke(legs, weight = 1.0f),
                GlyphStroke(dais, weight = 1.0f),
            ),
    )
}

private fun bhaiSpec(): GlyphSpec {
    // A vermillion blade crossing a clenched fist — the don's supari mark.
    val blade =
        Path().apply {
            // a curved kataar-style blade, tip upper-right
            moveTo(40f, 64f)
            lineTo(74f, 24f) // spine to tip
            lineTo(80f, 30f) // tip
            lineTo(50f, 70f) // cutting edge back down
            close()
        }
    val hilt =
        Path().apply {
            moveTo(40f, 64f)
            lineTo(34f, 70f) // pommel
            moveTo(36f, 60f)
            lineTo(48f, 72f) // cross-guard
        }
    val fist =
        Path().apply {
            // a blocky clenched fist lower-left, knuckles up
            moveTo(20f, 58f)
            lineTo(20f, 78f)
            lineTo(44f, 80f)
            lineTo(46f, 62f)
            // knuckle ridges
            moveTo(24f, 58f)
            lineTo(24f, 64f)
            moveTo(30f, 58f)
            lineTo(30f, 64f)
            moveTo(36f, 59f)
            lineTo(36f, 65f)
        }
    return GlyphSpec(
        fills = listOf(blade),
        strokes =
            listOf(
                GlyphStroke(hilt, weight = 1.1f),
                GlyphStroke(fist, weight = 1.0f),
            ),
    )
}

private fun babuSpec(): GlyphSpec {
    // A file folder (the eternal sarkari file) with a pressed round stamp on it.
    val folderTab =
        Path().apply {
            moveTo(24f, 30f)
            lineTo(44f, 30f)
            lineTo(50f, 38f)
            lineTo(24f, 38f)
            close()
        }
    val folderBody =
        Path().apply {
            moveTo(22f, 38f)
            lineTo(78f, 38f)
            lineTo(78f, 74f)
            lineTo(22f, 74f)
            close()
        }
    val lines =
        Path().apply {
            // ruled file lines
            moveTo(30f, 50f)
            lineTo(58f, 50f)
            moveTo(30f, 58f)
            lineTo(54f, 58f)
            moveTo(30f, 66f)
            lineTo(48f, 66f)
        }
    // The round stamp pressed in the lower-right corner.
    val stampRing =
        Path().apply {
            addOval(rectFromCenter(64f, 62f, 13f))
        }
    val stampInner =
        Path().apply {
            addOval(rectFromCenter(64f, 62f, 8f))
        }
    return GlyphSpec(
        fills = listOf(folderTab),
        strokes =
            listOf(
                GlyphStroke(folderBody, weight = 1.15f),
                GlyphStroke(lines, weight = 0.7f),
                GlyphStroke(stampRing, weight = 1.1f),
                GlyphStroke(stampInner, weight = 0.7f),
            ),
        dots = listOf(GlyphDot(cx = 64f, cy = 62f, radius = 2.4f)),
    )
}

private fun jugaaduSpec(): GlyphSpec {
    // Crossed adjustable wrench + a zip-tie loop — the fixer's jugaad kit.
    val wrench =
        Path().apply {
            // shaft
            moveTo(30f, 74f)
            lineTo(60f, 44f)
            // open jaw (C-head) at the top
            moveTo(60f, 44f)
            lineTo(58f, 34f)
            moveTo(60f, 44f)
            lineTo(70f, 42f)
            moveTo(58f, 34f)
            lineTo(66f, 30f)
            moveTo(70f, 42f)
            lineTo(72f, 34f)
            moveTo(66f, 30f)
            lineTo(72f, 34f)
        }
    val wrenchHandleEnd =
        Path().apply {
            moveTo(30f, 74f)
            lineTo(24f, 80f)
            moveTo(30f, 74f)
            lineTo(36f, 80f)
        }
    // zip-tie: a loop with a ratchet head, lower-left, crossing the shaft.
    val zipLoop =
        Path().apply {
            // an open teardrop loop
            moveTo(26f, 40f)
            cubicTo(14f, 36f, 14f, 58f, 30f, 58f)
            cubicTo(44f, 58f, 40f, 40f, 30f, 40f)
        }
    val zipHead =
        Path().apply {
            moveTo(26f, 38f)
            lineTo(34f, 38f)
            lineTo(34f, 46f)
            lineTo(26f, 46f)
            close()
        }
    return GlyphSpec(
        fills = listOf(zipHead),
        strokes =
            listOf(
                GlyphStroke(wrench, weight = 1.25f),
                GlyphStroke(wrenchHandleEnd, weight = 1.1f),
                GlyphStroke(zipLoop, weight = 1.05f),
            ),
    )
}

private fun vakilSpec(): GlyphSpec {
    // Balance scales above a gavel — the lawyer's mark.
    val beam =
        Path().apply {
            moveTo(22f, 30f)
            lineTo(78f, 30f) // cross-beam
            moveTo(50f, 22f)
            lineTo(50f, 46f) // central column
        }
    val column =
        Path().apply {
            moveTo(42f, 46f)
            lineTo(58f, 46f) // base of column
            moveTo(50f, 22f)
            addOval(rectFromCenter(50f, 20f, 4f)) // finial knob
        }
    // left pan: chains + a shallow bowl
    val leftPan =
        Path().apply {
            moveTo(22f, 30f)
            lineTo(16f, 44f)
            moveTo(22f, 30f)
            lineTo(28f, 44f)
            // bowl
            moveTo(14f, 44f)
            cubicTo(14f, 52f, 30f, 52f, 30f, 44f)
        }
    val rightPan =
        Path().apply {
            moveTo(78f, 30f)
            lineTo(72f, 44f)
            moveTo(78f, 30f)
            lineTo(84f, 44f)
            moveTo(70f, 44f)
            cubicTo(70f, 52f, 86f, 52f, 86f, 44f)
        }
    // gavel below the scales
    val gavelHead =
        Path().apply {
            moveTo(40f, 64f)
            lineTo(60f, 64f)
            lineTo(60f, 74f)
            lineTo(40f, 74f)
            close()
        }
    val gavelHandle =
        Path().apply {
            moveTo(58f, 74f)
            lineTo(70f, 84f)
        }
    val gavelBlock =
        Path().apply {
            moveTo(32f, 86f)
            lineTo(56f, 86f) // sounding block
        }
    return GlyphSpec(
        fills = listOf(gavelHead),
        strokes =
            listOf(
                GlyphStroke(beam, weight = 1.1f),
                GlyphStroke(column, weight = 0.9f),
                GlyphStroke(leftPan, weight = 0.85f),
                GlyphStroke(rightPan, weight = 0.85f),
                GlyphStroke(gavelHandle, weight = 1.2f),
                GlyphStroke(gavelBlock, weight = 1.2f),
            ),
    )
}

private fun patrakaarSpec(): GlyphSpec {
    // A PRESS pass card hung on a lanyard clip, with a pen-nib struck across it —
    // the journalist's mark (PATRAKAAR = Inquisitor: she who examines and exposes).
    val card =
        Path().apply {
            // the rectangular press card, slightly tilted feel via straight edges
            moveTo(22f, 30f)
            lineTo(72f, 30f)
            lineTo(72f, 64f)
            lineTo(22f, 64f)
            close()
        }
    val clipSlot =
        Path().apply {
            // lanyard clip slot at the top edge
            moveTo(40f, 30f)
            lineTo(40f, 24f)
            lineTo(54f, 24f)
            lineTo(54f, 30f)
        }
    val headline =
        Path().apply {
            // a bold "PRESS" rule + a portrait box, suggesting the credential
            moveTo(28f, 40f)
            lineTo(50f, 40f) // PRESS bar
            moveTo(28f, 48f)
            lineTo(44f, 48f) // sub-line
            moveTo(28f, 56f)
            lineTo(40f, 56f) // sub-line
        }
    val portrait =
        Path().apply {
            // small portrait window on the card's right
            moveTo(56f, 44f)
            lineTo(66f, 44f)
            lineTo(66f, 58f)
            lineTo(56f, 58f)
            close()
        }
    // The pen-nib struck diagonally across the lower-right (the reporter's pen).
    val nib =
        Path().apply {
            moveTo(60f, 60f) // shoulder
            lineTo(86f, 86f) // tip, lower-right
            lineTo(80f, 88f)
            lineTo(56f, 64f)
            close()
        }
    val nibSlit =
        Path().apply {
            // the central ink slit running up the nib toward the tip
            moveTo(64f, 66f)
            lineTo(82f, 84f)
        }
    return GlyphSpec(
        fills = listOf(card, nib),
        strokes =
            listOf(
                GlyphStroke(clipSlot, weight = 1.0f),
                GlyphStroke(headline, weight = 0.7f),
                GlyphStroke(portrait, weight = 0.9f),
                GlyphStroke(nibSlit, weight = 0.7f),
            ),
        // the nib's vent-hole (the breather of a real pen-nib)
        dots = listOf(GlyphDot(cx = 66f, cy = 68f, radius = 2.2f)),
    )
}

private fun rectFromCenter(
    cx: Float,
    cy: Float,
    r: Float,
) = androidx.compose.ui.geometry
    .Rect(cx - r, cy - r, cx + r, cy + r)

private fun specFor(role: Role): GlyphSpec =
    when (role) {
        Role.NETA -> netaSpec()
        Role.BHAI -> bhaiSpec()
        Role.BABU -> babuSpec()
        Role.JUGAADU -> jugaaduSpec()
        Role.VAKIL -> vakilSpec()
        Role.PATRAKAAR -> patrakaarSpec()
    }

// ─────────────────────────── DrawScope renderer ───────────────────────────────

/**
 * Draws the bespoke intaglio role mark for [role] inside the current [DrawScope]
 * bounds (centered, square-fit). Engraved look: shadow offset + highlight offset +
 * role-hue ink. Use [inkColor] to override the ink (defaults to the role hue).
 *
 * @param weight base stroke weight multiplier (1f ≈ a tasteful incised line at the
 *               glyph's size). Bumped up automatically at tiny sizes for legibility.
 * @param deboss when true (default) the engraved shadow/highlight relief is drawn;
 *               set false for a flat single-color silhouette (e.g. on dark crests
 *               where the relief would muddy at small sizes).
 */
fun DrawScope.drawRoleGlyph(
    role: Role,
    inkColor: Color = GaddiColors.forRole(role).color,
    weight: Float = 1f,
    deboss: Boolean = true,
) {
    val spec = specFor(role)
    val side = size.minDimension
    val s = side / UNIT
    // Center the 100×100 art in the (possibly non-square) draw box.
    val ox = (size.width - side) / 2f
    val oy = (size.height - side) / 2f

    // Tiny-size legibility: thicken the line as the glyph shrinks.
    val sizeBoost =
        when {
            side < 18f -> 1.7f
            side < 28f -> 1.35f
            side < 44f -> 1.12f
            else -> 1f
        }
    val baseStroke = side * 0.052f * weight * sizeBoost

    val shadowCol = BrandTokens.TeakInk.copy(alpha = 0.42f)
    val highlightCol = Color.White.copy(alpha = 0.30f)
    val off = (side * 0.018f).coerceAtLeast(0.6f)

    translate(ox, oy) {
        scale(s, s, pivot = Offset.Zero) {
            // 1. recessed shadow (down-right) — the cut wall
            if (deboss) {
                translate(off / s, off / s) {
                    paintSpec(spec, shadowCol, baseStroke / s, fillAlpha = 0.55f)
                }
                // 2. lit lip (up-left)
                translate(-off / s, -off / s) {
                    paintSpec(spec, highlightCol, baseStroke / s, fillAlpha = 0.35f)
                }
            }
            // 3. role-hue ink on the engraving floor
            paintSpec(spec, inkColor, baseStroke / s, fillAlpha = 1f)
        }
    }
}

private fun DrawScope.paintSpec(
    spec: GlyphSpec,
    color: Color,
    stroke: Float,
    fillAlpha: Float,
) {
    spec.fills.forEach { path ->
        drawPath(path, color = color.copy(alpha = color.alpha * fillAlpha), style = Fill)
    }
    spec.strokes.forEach { (path, weight) ->
        drawPath(
            path,
            color = color,
            style =
                Stroke(
                    width = stroke * weight,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
        )
    }
    spec.dots.forEach { (cx, cy, radius) ->
        drawCircle(color, radius = radius, center = Offset(cx, cy))
    }
}

// ─────────────────────────── Composable wrapper ───────────────────────────────

/**
 * The bespoke engraved role glyph as a drop-in composable. Replaces the former
 * `Icon(imageVector = visual.glyph, …)` call sites. Size it via [modifier]
 * (`Modifier.size(…)`); the mark fits the box square and centered.
 *
 * @param tint ink color. Defaults to the role's Okabe-Ito hue. Pass a cream/brass
 *             tint on dark enamel crests for contrast.
 * @param weight stroke-weight multiplier.
 * @param deboss engraved relief on/off (see [drawRoleGlyph]).
 */
@Composable
fun RoleGlyph(
    role: Role,
    modifier: Modifier = Modifier,
    tint: Color = GaddiColors.forRole(role).color,
    weight: Float = 1f,
    deboss: Boolean = true,
) {
    Canvas(modifier = modifier) {
        drawRoleGlyph(role = role, inkColor = tint, weight = weight, deboss = deboss)
    }
}
