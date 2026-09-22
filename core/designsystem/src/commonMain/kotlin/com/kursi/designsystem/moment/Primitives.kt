package com.kursi.designsystem.moment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.BrandTokens
import com.siddharth.kmp.common.easeInQuart
import com.siddharth.kmp.common.easeOutBack
import com.siddharth.kmp.common.easeOutCubic
import com.siddharth.kmp.common.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════════
// Primitives.kt — the 5 reusable animation primitives + HalftoneBurst.
// Design: kursi-plan/docs/15c_action_moments.md §1.3
//
// ALL animation math is driven by a single 0→1 Float progress. Callers pass a
// sub-range remapped to 0..1. No Animatable here; ownership is in the overlay/beats.
//
// Multiplatform-safe: Compose Canvas, graphicsLayer, no AGSL/RuntimeShader.
// ═══════════════════════════════════════════════════════════════════════════════

// ─────────────────────────── Easing helpers ──────────────────────────────────
// lerp/easeInQuart/easeOutCubic/easeOutBack now come from :common (Backlog #25) —
// pure Float math with zero Compose dependency, deduped with PaymentsLab's ShieldPulse.kt copy.

// ─────────────────────────── 1. RubberStamp ──────────────────────────────────

/**
 * THE Kursi rubber-stamp — the single gesture that appears in nearly every moment.
 * Tune this once; the whole game's feel changes coherently.
 *
 * Timeline for [progress] 0→1:
 *   Phase A (0.00–0.55): glyph descends from 1.8× scale, 0→1 alpha, rotated [startRotationDeg].
 *   Phase B (0.55–0.78): OVERSHOOT to 0.92× (the press); [HalftoneBurst] fires on impact.
 *   Phase C (0.78–1.00): settle to 1.0× via EaseOutBack; ink-bleed edge + 1px channel offset lock in.
 *
 * @param glyphText  Stamp word to render (e.g. "GHOTALA", "EXPOSED", "JHOOTH!", "KURSI").
 * @param tint       Ink colour — brass / stamp-red / role-hue / verdigris per moment.
 * @param progress   0→1 float, remapped by the caller from the moment's full timeline.
 * @param impactCenter Absolute Offset where the halftone burst rings outward from.
 * @param startRotationDeg Entry rotation. Default 6° per spec.
 */
@Composable
fun RubberStamp(
    glyphText: String,
    tint: Color,
    progress: Float,
    impactCenter: Offset,
    startRotationDeg: Float = 6f,
    modifier: Modifier = Modifier,
) {
    // ── Scale per phase ──
    val scale =
        when {
            progress < 0.55f -> lerp(1.8f, 1.0f, easeInQuart(progress / 0.55f))
            progress < 0.78f -> lerp(1.0f, 0.92f, (progress - 0.55f) / 0.23f)
            else -> lerp(0.92f, 1.0f, easeOutBack((progress - 0.78f) / 0.22f))
        }
    // ── Alpha ramps up over the first 40% then stays full ──
    val alpha = (progress / 0.40f).coerceAtMost(1f)
    // ── Rotation straightens as it lands ──
    val rot = lerp(startRotationDeg, 0f, easeOutCubic(progress))
    // ── Ink-bleed: a subtle channel-offset hue shift blooms from Phase C ──
    val inkBleed = if (progress > 0.78f) (progress - 0.78f) / 0.22f else 0f

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rot
                    this.alpha = alpha
                    // 1px channel-offset simulated via a tiny translationX on the shadow layer
                    // (pure graphicsLayer, no AGSL)
                    translationX = if (inkBleed > 0f) inkBleed * 1.5f else 0f
                },
        contentAlignment = Alignment.Center,
    ) {
        // Shadow layer (ink-bleed ghost) — slightly offset, lower alpha
        if (inkBleed > 0.1f) {
            Text(
                text = glyphText,
                style =
                    TextStyle(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = tint.copy(alpha = alpha * inkBleed * 0.25f),
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp,
                    ),
                modifier = Modifier.offset { IntOffset((-2f * inkBleed).roundToInt(), 0) },
            )
        }
        // Primary stamp word
        Text(
            text = glyphText,
            style =
                TextStyle(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = tint.copy(alpha = alpha),
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp,
                ),
        )
    }

    // Halftone burst fires during the press and early settle (Phase B + early C)
    if (progress in 0.55f..0.95f) {
        val burstProgress = (progress - 0.55f) / 0.40f
        HalftoneBurst(center = impactCenter, progress = burstProgress, tint = tint)
    }
}

// ─────────────────────────── 2. HalftoneBurst ────────────────────────────────

/**
 * Expanding ring of riso dots — the impact confirmation of a rubber-stamp.
 * Canvas-drawn, no bitmap. 18 dots, radius 8dp→64dp, fading out.
 *
 * @param center   Absolute Offset in the parent's coordinate space.
 * @param progress 0→1; 0=compact, 1=fully expanded and invisible.
 * @param tint     Dot fill color.
 */
@Composable
fun HalftoneBurst(
    center: Offset,
    progress: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val minR = 8.dp.toPx()
        val maxR = 64.dp.toPx()
        val r = lerp(minR, maxR, easeOutCubic(progress))
        val dotAlpha = (1f - progress) * 0.6f
        val dotRadius = lerp(3.dp.toPx(), 1.dp.toPx(), progress)
        val dots = 18
        repeat(dots) { i ->
            val ang = (i.toFloat() / dots.toFloat()) * 2f * PI.toFloat()
            val p = center + Offset(cos(ang) * r, sin(ang) * r)
            drawCircle(
                color = tint.copy(alpha = dotAlpha),
                radius = dotRadius,
                center = p,
            )
        }
    }
}

// ─────────────────────────── 3. CoinTrail ────────────────────────────────────

/**
 * Gold disc arc from [from] → [to] with a counter badge.
 *
 * Each coin travels along a curved arc path. Multiple coins can be staggered
 * by the beat composable by calling this multiple times with offset [progress] sub-ranges.
 *
 * @param from      Start Offset in overlay coordinates.
 * @param to        End Offset in overlay coordinates.
 * @param progress  0→1; coin arc completes at 1.0.
 * @param count     The "+N" delta text to show (rendered near the destination on arrival).
 * @param coinSize  Coin disc diameter.
 */
@Composable
fun CoinTrail(
    from: Offset,
    to: Offset,
    progress: Float,
    count: Int = 1,
    coinSize: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    val easedT = easeOutCubic(progress.coerceIn(0f, 1f))

    // Arc midpoint is lifted above the straight line (control point for quadratic)
    val mid =
        Offset(
            x = lerp(from.x, to.x, 0.5f),
            y = lerp(from.y, to.y, 0.5f) - 60f, // arc height in px
        )

    // Quadratic Bézier position
    val coinX = quadBezier(from.x, mid.x, to.x, easedT)
    val coinY = quadBezier(from.y, mid.y, to.y, easedT)

    // Trail alpha: fade in early, full during flight, fade out last 10%
    val alpha =
        when {
            progress < 0.15f -> progress / 0.15f
            progress > 0.90f -> (1f - progress) / 0.10f
            else -> 1f
        }

    // Counter "+N" only visible on arrival (last 20%)
    val counterAlpha = if (progress > 0.80f) (progress - 0.80f) / 0.20f else 0f

    Canvas(modifier = modifier.fillMaxSize()) {
        // Gold disc
        drawCircle(
            color = BrandTokens.GoldAntique.copy(alpha = alpha),
            radius = (coinSize / 2).toPx(),
            center = Offset(coinX, coinY),
        )
        // Brass rim
        drawCircle(
            color = BrandTokens.BrassDark.copy(alpha = alpha * 0.8f),
            radius = (coinSize / 2).toPx(),
            center = Offset(coinX, coinY),
            style = Stroke(width = 1.dp.toPx()),
        )
    }

    // Counter label (rendered via Text so it benefits from font subsystem)
    if (counterAlpha > 0f) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .alpha(counterAlpha),
        ) {
            Text(
                text = "+$count",
                style =
                    TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandTokens.GoldAntique,
                    ),
                modifier =
                    Modifier.offset {
                        IntOffset(to.x.roundToInt(), (to.y - 30f).roundToInt())
                    },
            )
        }
    }
}

/** Quadratic Bézier scalar. */
private fun quadBezier(
    p0: Float,
    p1: Float,
    p2: Float,
    t: Float,
): Float {
    val mt = 1f - t
    return mt * mt * p0 + 2f * mt * t * p1 + t * t * p2
}

// ─────────────────────────── 4. CardFlip ─────────────────────────────────────

/**
 * 3D rotationY card flip. A [frontContent] composable is shown as it flips.
 * The back is a plain teak-brass card-back surface. On full flip (progress ≥ 0.5),
 * a 1-3px riso channel-offset shimmer fires (color misregistration).
 *
 * [progress] 0 = face-down, 1 = fully face-up.
 */
@Composable
fun CardFlip(
    progress: Float,
    modifier: Modifier = Modifier,
    frontContent: @Composable () -> Unit,
) {
    val easedProgress = easeOutCubic(progress.coerceIn(0f, 1f))
    val faceUp = easedProgress >= 0.5f

    // rotationY: 0° → −90° (face-down side goes away) → 0° (face-up arrives)
    val rotY =
        if (!faceUp) {
            lerp(0f, -90f, easedProgress / 0.5f)
        } else {
            lerp(90f, 0f, (easedProgress - 0.5f) / 0.5f)
        }

    // Riso channel-offset: fires during reveal window (0.45–0.65)
    val risoShimmer =
        when {
            progress < 0.45f -> 0f
            progress < 0.65f -> (progress - 0.45f) / 0.20f
            progress < 0.80f -> 1f - (progress - 0.65f) / 0.15f
            else -> 0f
        }
    val channelOffset = (risoShimmer * 3f).roundToInt() // 0..3 px

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    rotationY = rotY
                    cameraDistance = 8f * density // prevents extreme perspective distortion
                },
        contentAlignment = Alignment.Center,
    ) {
        if (faceUp) {
            // Front face — counter-rotate text so it isn't mirrored
            Box(
                modifier =
                    Modifier.graphicsLayer {
                        // Text is already correct because rotationY already flipped it back to 0°
                        // The channel offset: draw a ghost copy shifted by channelOffset pixels
                        translationX = channelOffset.toFloat()
                    },
                contentAlignment = Alignment.Center,
            ) {
                frontContent()
            }
        } else {
            // Card back — brass teak surface
            CardBackSurface(modifier = Modifier.fillMaxSize())
        }
    }
}

/** Simple card-back placeholder drawn with Canvas. */
@Composable
private fun CardBackSurface(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        // Deep teak fill
        drawRect(BrandTokens.TeakDark)
        // Brass border ring
        drawRect(
            color = BrandTokens.BrassAged.copy(alpha = 0.7f),
            style = Stroke(width = 2.dp.toPx()),
        )
        // Ghosted "K" centre — radiating lines as the chair-emblem simplification
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) * 0.28f
        drawCircle(
            color = BrandTokens.GoldAntique.copy(alpha = 0.12f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(1.dp.toPx()),
        )
    }
}

// ─────────────────────────── 5. ChairTip ─────────────────────────────────────

/**
 * The eliminated-player chair tips and falls.
 * The logo "wobble" made literal — the chair pivots from its base, over-rotates,
 * and drifts downward out of frame.
 *
 * @param progress 0→1; at 1.0 the chair is fully off-screen bottom.
 * @param tint     Tint of the chair glyph (usually seat color or teak-muted for elimination).
 * @param seatCenter Absolute offset where the chair is anchored in the overlay.
 */
@Composable
fun ChairTip(
    progress: Float,
    tint: Color,
    seatCenter: Offset,
    modifier: Modifier = Modifier,
) {
    // Phase A (0–0.40): tip to 45° with anticipation hold at peak
    // Phase B (0.40–0.70): continue tipping to 90°
    // Phase C (0.70–1.00): full 110° + drift down off-screen
    val rotZ =
        when {
            progress < 0.40f -> lerp(0f, 45f, easeOutCubic(progress / 0.40f))
            progress < 0.70f -> lerp(45f, 90f, (progress - 0.40f) / 0.30f)
            else -> lerp(90f, 110f, (progress - 0.70f) / 0.30f)
        }
    val dropY =
        if (progress > 0.55f) {
            lerp(0f, 120f, easeInQuart((progress - 0.55f) / 0.45f))
        } else {
            0f
        }

    val alpha = if (progress > 0.80f) lerp(1f, 0f, (progress - 0.80f) / 0.20f) else 1f

    Canvas(modifier = modifier.fillMaxSize()) {
        translate(left = seatCenter.x, top = seatCenter.y + dropY) {
            rotate(degrees = rotZ, pivot = Offset(0f, 0f)) {
                // Chair glyph — drawn as simple geometric chair (canvas, no bitmap)
                drawChairGlyph(tint.copy(alpha = alpha))
            }
        }
    }
}

/**
 * The Kursi chair glyph, drawn around the current origin.
 *
 * ONE copy. MomentStaticFrames.kt carried a byte-identical second one (drawStaticChair) with the
 * same nine fractions typed out again, so the animated moment and its frozen frame could silently
 * drift apart. Every fraction below is named: they are proportions of the glyph box, not tuning.
 */
private object ChairGlyph {
    val Width = 40.dp
    val Height = 48.dp
    val StrokeWidth = 3.dp

    /** Back rest: a half-ellipse spanning 80% of the width, 35% of the height, sitting above the seat. */
    const val BackRestHalfWidthFraction = 0.4f
    const val BackRestTopFraction = 0.5f
    const val BackRestWidthFraction = 0.8f
    const val BackRestHeightFraction = 0.35f

    /** Seat beam spans the full back-rest width; the legs are inset from it. */
    const val LegInsetFraction = 0.3f
    const val LegLengthFraction = 0.45f

    /** Foot rail sits just above the leg ends and is drawn lighter than the frame. */
    const val FootRailHeightFraction = 0.38f
    const val FootRailStrokeScale = 0.7f

    const val BackRestStartAngle = 180f
    const val BackRestSweepAngle = 180f
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChairGlyph(color: Color) {
    val w = ChairGlyph.Width.toPx()
    val h = ChairGlyph.Height.toPx()
    val sw = ChairGlyph.StrokeWidth.toPx()
    val halfW = w * ChairGlyph.BackRestHalfWidthFraction
    val legX = w * ChairGlyph.LegInsetFraction

    // Back rest (top arc)
    drawArc(
        color = color,
        startAngle = ChairGlyph.BackRestStartAngle,
        sweepAngle = ChairGlyph.BackRestSweepAngle,
        useCenter = false,
        topLeft = Offset(-halfW, -h * ChairGlyph.BackRestTopFraction),
        size =
            androidx.compose.ui.geometry
                .Size(w * ChairGlyph.BackRestWidthFraction, h * ChairGlyph.BackRestHeightFraction),
        style = Stroke(sw),
    )
    // Seat beam
    drawLine(color = color, start = Offset(-halfW, 0f), end = Offset(halfW, 0f), strokeWidth = sw)
    // Legs
    drawLine(color, Offset(-legX, 0f), Offset(-legX, h * ChairGlyph.LegLengthFraction), sw)
    drawLine(color, Offset(legX, 0f), Offset(legX, h * ChairGlyph.LegLengthFraction), sw)
    // Foot rail
    drawLine(
        color = color,
        start = Offset(-halfW, h * ChairGlyph.FootRailHeightFraction),
        end = Offset(halfW, h * ChairGlyph.FootRailHeightFraction),
        strokeWidth = sw * ChairGlyph.FootRailStrokeScale,
    )
}

// ─────────────────────────── 6. TickerSlip ───────────────────────────────────

/**
 * Torn-paper log slip that slides in from the right.
 * Every resolved action emits one — the permanent accessible floor.
 *
 * @param glyphText  The stamped symbol (action initial or glyph word).
 * @param effectText The effect line (+3, "steal 2", "EXPOSED").
 * @param tint       Stamp ink and border tint in the role hue.
 * @param progress   0→1; 0=off-screen right, 1=settled in position.
 */
@Composable
fun TickerSlip(
    glyphText: String,
    effectText: String,
    tint: Color,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val slideT = easeOutCubic(progress.coerceIn(0f, 1f))
    val alpha = (progress / 0.20f).coerceAtMost(1f)

    // Slides in from right: offsetX goes from +200 to 0
    val slideOffsetX = lerp(200f, 0f, slideT)

    Box(
        modifier =
            modifier
                .offset { IntOffset(slideOffsetX.roundToInt(), 0) }
                .alpha(alpha),
    ) {
        Canvas(modifier = Modifier.size(width = 180.dp, height = 40.dp)) {
            val w = size.width
            val h = size.height

            // Paper body — cream slip
            val paperPath =
                Path().apply {
                    moveTo(12.dp.toPx(), 0f)
                    // Torn left edge (jagged)
                    lineTo(8.dp.toPx(), 4.dp.toPx())
                    lineTo(11.dp.toPx(), 8.dp.toPx())
                    lineTo(7.dp.toPx(), 12.dp.toPx())
                    lineTo(10.dp.toPx(), h)
                    lineTo(w, h)
                    lineTo(w, 0f)
                    close()
                }
            drawPath(paperPath, BrandTokens.PaperCream.copy(alpha = 0.90f))

            // Role-hue tint strip on left edge
            drawRect(
                color = tint.copy(alpha = 0.5f),
                topLeft = Offset(0f, 0f),
                size =
                    androidx.compose.ui.geometry
                        .Size(6.dp.toPx(), h),
            )

            // Bottom border hairline
            drawLine(
                color = BrandTokens.BrassDark.copy(alpha = 0.4f),
                start = Offset(12.dp.toPx(), h - 0.5.dp.toPx()),
                end = Offset(w, h - 0.5.dp.toPx()),
                strokeWidth = 0.5.dp.toPx(),
            )
        }

        // Text layer — glyph + effect overlaid on the slip
        androidx.compose.foundation.layout.Row(
            modifier =
                Modifier
                    .size(width = 180.dp, height = 40.dp)
                    .offset { IntOffset(14.dp.roundToPx(), 0) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Stamped glyph (small, rotated ~−4°)
            Box(
                modifier =
                    Modifier
                        .size(32.dp, 32.dp)
                        .graphicsLayer { rotationZ = -4f },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = glyphText,
                    style =
                        TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = tint,
                            letterSpacing = 0.5.sp,
                        ),
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = effectText,
                style =
                    TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandTokens.CreamInk,
                    ),
                maxLines = 1,
            )
        }
    }
}
