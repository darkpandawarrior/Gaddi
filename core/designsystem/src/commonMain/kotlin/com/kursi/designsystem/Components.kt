package com.kursi.designsystem

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.shader.feltMaterial
import com.kursi.engine.Role
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════════
// §8 LICENSE RAJ DECO — "teak-and-brass council chamber" components.
// Design: brass bezel + cream certificate paper + enamel role badges.
// All textures drawn via Compose Canvas — multiplatform-safe (no AGSL/RuntimeShader
// in commonMain). Guilloché is sine-wave line pattern drawn via drawLines on Canvas.
// ═══════════════════════════════════════════════════════════════════════════════

// ─────────────────────────── Uniform "inspect" gesture ────────────────────────

/**
 * The one press-and-hold inspect gesture used across the whole table: a TAP fires the
 * element's PRIMARY action, a LONG-PRESS fires [onLongClick] (always "inspect — read the
 * chit"). While the finger is down the element dips slightly (scale + a touch of alpha)
 * so a hold feels physically responsive instead of dead. Consistent everywhere — dock
 * chips, hand cards, opponent plates, the medallion and Roznamcha log rows all route
 * through this so the banner's promise ("long-press any action to read the catch") is
 * literally true for every inspectable surface.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.inspectable(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: Boolean = true,
    /**
     * DEPTH3D — when supplied, a finger-down hold casts a deeper contact shadow under the
     * element while it dips, so a press reads as physically pushing the surface into the
     * felt (and lifting it back out on release). Null keeps the cheaper scale-only feel for
     * tiny surfaces (e.g. log rows). The shape clips the cast shadow to the element's corner.
     */
    pressShape: androidx.compose.ui.graphics.Shape? = null,
): Modifier {
    var pressed by remember { mutableStateOf(false) }
    // Crisp spring response (spec §7 juice) rather than a flat tween — a press should feel
    // like it lands, not just fade in. Reduced motion collapses to an instant snap.
    val reducedMotion = LocalReducedMotion.current
    val pressSpec: AnimationSpec<Float> = if (reducedMotion) tween(0) else KursiMotion.snap()
    val pressShadowSpec: AnimationSpec<Dp> = if (reducedMotion) tween(0) else KursiMotion.snap()
    // Slightly deeper dip than before; the hold should feel like the surface gives way.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = pressSpec,
        label = "inspectPressScale",
    )
    // The cast shadow swells while held, then settles back — the "physical hold" cue.
    val pressShadow by animateDpAsState(
        targetValue = if (pressed) 14.dp else 0.dp,
        animationSpec = pressShadowSpec,
        label = "inspectPressShadow",
    )
    return this
        .then(
            if (pressShape != null) {
                Modifier.shadow(
                    elevation = pressShadow,
                    shape = pressShape,
                    ambientColor = BrandTokens.TeakInk,
                    spotColor = Color.Black,
                    clip = false,
                )
            } else {
                Modifier
            },
        ).scale(pressScale)
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
            )
        }.combinedClickable(
            enabled = enabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

// ─────────────────────────── Deco texture drawing ────────────────────────────

/**
 * Canvas-drawn guilloché line pattern (interfering sine waves) for brass bezels.
 * Multiplatform-safe: uses Compose DrawScope, no AGSL/RuntimeShader.
 *
 * Pattern variants per RoleFramePattern:
 *   SolidRing  — single solid stroke ring (no wave lines)
 *   Hatched    — 45° diagonal hatch lines
 *   Dotted     — evenly spaced small circles
 *   Woven      — two offset sine waves (braid look)
 *   DoubleRule — two concentric rule lines
 */
private fun DrawScope.drawGuilloche(
    pattern: RoleFramePattern,
    color: Color,
    alpha: Float = TextureTokens.guillocheLinesAlpha,
) {
    val w = size.width
    val h = size.height
    val paint = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
    val lineColor = color.copy(alpha = alpha)

    when (pattern) {
        RoleFramePattern.SolidRing -> {
            // Two thin concentric rings near the edge
            val inset = 3.dp.toPx()
            drawRect(
                color = lineColor,
                topLeft = Offset(inset, inset),
                size = Size(w - inset * 2, h - inset * 2),
                style = Stroke(width = 0.8.dp.toPx()),
            )
        }
        RoleFramePattern.Hatched -> {
            // 45° diagonal lines from top-left to bottom-right
            val step = 8.dp.toPx()
            var x = -h
            while (x < w) {
                drawLine(lineColor, Offset(x, 0f), Offset(x + h, h), strokeWidth = 0.7.dp.toPx())
                x += step
            }
        }
        RoleFramePattern.Dotted -> {
            // Evenly spaced dots grid
            val step = 9.dp.toPx()
            val dotR = 1.2.dp.toPx()
            var cy = step
            while (cy < h) {
                var cx = step
                while (cx < w) {
                    drawCircle(lineColor, dotR, Offset(cx, cy))
                    cx += step
                }
                cy += step
            }
        }
        RoleFramePattern.Woven -> {
            // Two sine waves offset by half phase (braid look)
            val amp = 4.dp.toPx()
            val freq = 2 * PI.toFloat() / (30.dp.toPx())
            val lineWidth = 0.7.dp.toPx()
            for (wave in 0..1) {
                val phaseOffset = if (wave == 0) 0f else PI.toFloat()
                val path = Path()
                var first = true
                var x = 0f
                while (x <= w) {
                    val y = h / 2f + amp * sin(freq * x + phaseOffset)
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                    x += 1.dp.toPx()
                }
                drawPath(path, lineColor, style = Stroke(lineWidth))
            }
        }
        RoleFramePattern.DoubleRule -> {
            // Two concentric rectangles with more gap
            val inset1 = 2.dp.toPx()
            val inset2 = 5.dp.toPx()
            drawRect(
                lineColor,
                Offset(inset1, inset1),
                Size(w - inset1 * 2, h - inset1 * 2),
                style = Stroke(0.8.dp.toPx()),
            )
            drawRect(
                lineColor,
                Offset(inset2, inset2),
                Size(w - inset2 * 2, h - inset2 * 2),
                style = Stroke(0.8.dp.toPx()),
            )
        }
        RoleFramePattern.Ticked -> {
            // PATRAKAAR — a single ring with radial tick marks along the edge (a press /
            // registration bezel). Distinct non-color CVD channel: a ruled, ticked border.
            val inset = 3.dp.toPx()
            drawRect(
                color = lineColor,
                topLeft = Offset(inset, inset),
                size = Size(w - inset * 2, h - inset * 2),
                style = Stroke(width = 0.8.dp.toPx()),
            )
            val tickStep = 9.dp.toPx()
            val tickLen = 3.dp.toPx()
            val sw = 0.7.dp.toPx()
            // top & bottom edge ticks
            var x = tickStep
            while (x < w - tickStep) {
                drawLine(lineColor, Offset(x, inset), Offset(x, inset + tickLen), strokeWidth = sw)
                drawLine(lineColor, Offset(x, h - inset), Offset(x, h - inset - tickLen), strokeWidth = sw)
                x += tickStep
            }
            // left & right edge ticks
            var y = tickStep
            while (y < h - tickStep) {
                drawLine(lineColor, Offset(inset, y), Offset(inset + tickLen, y), strokeWidth = sw)
                drawLine(lineColor, Offset(w - inset, y), Offset(w - inset - tickLen, y), strokeWidth = sw)
                y += tickStep
            }
        }
    }
}

/**
 * Canvas deboss: inner shadow on cream card face to simulate card sitting inside a bezel.
 * Multiplatform-safe — uses drawRect with alpha gradient simulation.
 */
private fun DrawScope.drawDeboss(
    shadowColor: Color = Color.Black,
    alpha: Float = 0.12f,
) {
    val w = size.width
    val h = size.height
    val depth = 6.dp.toPx()
    // Top edge darker (shadow from above)
    drawRect(
        brush =
            Brush.verticalGradient(
                listOf(shadowColor.copy(alpha = alpha), Color.Transparent),
                startY = 0f,
                endY = depth,
            ),
        size = Size(w, depth),
    )
    // Left edge
    drawRect(
        brush =
            Brush.horizontalGradient(
                listOf(shadowColor.copy(alpha = alpha * 0.6f), Color.Transparent),
                startX = 0f,
                endX = depth,
            ),
        size = Size(depth, h),
    )
}

/**
 * Subtle paper grain overlay: faint random-looking cross-hatch at very low alpha.
 * Approximated with two overlapping diagonal lines sets (no noise shader needed).
 */
private fun DrawScope.drawPaperGrain(color: Color = BrandTokens.CreamInk) {
    val step = 14.dp.toPx()
    val alpha = TextureTokens.paperGrainAlpha
    var x = 0f
    while (x < size.width) {
        drawLine(
            color.copy(alpha = alpha),
            Offset(x, 0f),
            Offset(x + size.height * 0.3f, size.height),
            strokeWidth = 0.5.dp.toPx(),
        )
        x += step
    }
}

/**
 * Chair-in-sunburst emblem: a stylized chair glyph with radiating lines,
 * drawn via Canvas at very low alpha as a center watermark.
 */
private fun DrawScope.drawChairEmblem(color: Color = BrandTokens.GoldAntique) {
    val alpha = TextureTokens.emblomAlpha
    val cx = size.width / 2
    val cy = size.height / 2
    val r = minOf(size.width, size.height) * 0.30f

    // Radiating sunburst lines
    val rays = 16
    for (i in 0 until rays) {
        val angle = (i * 2 * PI / rays).toFloat()
        val innerR = r * 0.45f
        drawLine(
            color.copy(alpha = alpha),
            Offset(cx + innerR * cos(angle), cy + innerR * sin(angle)),
            Offset(cx + r * cos(angle), cy + r * sin(angle)),
            strokeWidth = 1.2.dp.toPx(),
        )
    }
    // Outer circle
    drawCircle(color.copy(alpha = alpha), r, Offset(cx, cy), style = Stroke(1.dp.toPx()))
    // Inner circle (seat back)
    drawCircle(color.copy(alpha = alpha), r * 0.28f, Offset(cx, cy * 0.86f), style = Stroke(1.2.dp.toPx()))
    // Seat legs — two downward lines
    val legWidth = r * 0.22f
    drawLine(color.copy(alpha = alpha), Offset(cx - legWidth, cy * 0.96f), Offset(cx - legWidth, cy + r * 0.35f), strokeWidth = 1.5.dp.toPx())
    drawLine(color.copy(alpha = alpha), Offset(cx + legWidth, cy * 0.96f), Offset(cx + legWidth, cy + r * 0.35f), strokeWidth = 1.5.dp.toPx())
    // Seat beam
    drawLine(color.copy(alpha = alpha), Offset(cx - legWidth * 1.3f, cy + r * 0.22f), Offset(cx + legWidth * 1.3f, cy + r * 0.22f), strokeWidth = 1.2.dp.toPx())
}

// ─────────────────────────── Brass specular animated sweep ───────────────────

/**
 * Animated brass specular highlight sweep modifier.
 * A diagonal gold gradient that drifts across the element to simulate light
 * catching a polished brass surface. Multiplatform-safe — uses Compose Brush.
 */
@Composable
private fun Modifier.brassSpecular(): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "brassSpecular")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "brassHighlight",
    )
    return drawBehind {
        val w = size.width
        val h = size.height
        // Diagonal specular band
        val bandWidth = w * TextureTokens.brassSpecularWidth
        val startX = -bandWidth + progress * (w + bandWidth)
        drawRect(
            brush =
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        BrandTokens.GoldAntique.copy(alpha = 0.12f),
                        BrandTokens.GoldAntique.copy(alpha = 0.22f),
                        BrandTokens.GoldAntique.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                    startX = startX,
                    endX = startX + bandWidth,
                ),
            size = size,
        )
    }
}

// ─────────────────────────── RoleCard (§3 "YOUR HAND" hero card) ──────────────

/** Size variants for [RoleCard]. Large = hand card, Medium = claim spotlight, Small = inline chip. */
enum class CardSize { Large, Medium, Small }

/**
 * License Raj Deco influence card — cream certificate paper inside a brass bezel.
 *
 * Front face: cream paper (#EDE3CC) with:
 *   - top-left enamel badge roundel (role color inside brass ring)
 *   - role name in display serif
 *   - role glyph as engraved line mark
 *   - action/block lines as small-caps
 *   - faint serial number bottom-right
 *   - guilloché border drawn via Canvas (pattern varies by role)
 *   - deboss inner shadow so paper sits inside the bezel
 *
 * [lost] → desaturated grey header, 35% alpha background, "REVEALED" pill.
 * [lifted] → elevated surface with role-colored glow.
 * [size]: Large (hand), Medium (claim spotlight), Small (log inline).
 */
@Composable
fun RoleCard(
    role: Role,
    modifier: Modifier = Modifier,
    size: CardSize = CardSize.Large,
    lost: Boolean = false,
    lifted: Boolean = false,
    /** Tap = primary action (e.g. reveal during influence-loss). Null → no tap behaviour. */
    onClick: (() -> Unit)? = null,
    /** Long-press = inspect (open the Identity chit). Null → no long-press behaviour. */
    onLongClick: (() -> Unit)? = null,
) {
    val visual = KursiColors.forRole(role)
    val radius = KursiRadii.xl

    val cardW: Dp
    val cardH: Dp
    val headerPad: Dp
    val iconSize: Dp
    val badgeSize: Dp
    when (size) {
        CardSize.Large -> {
            cardW = 160.dp
            cardH = 220.dp
            headerPad = 12.dp
            iconSize = 44.dp
            badgeSize = 28.dp
        }
        CardSize.Medium -> {
            cardW = 120.dp
            cardH = 164.dp
            headerPad = 8.dp
            iconSize = 32.dp
            badgeSize = 22.dp
        }
        CardSize.Small -> {
            cardW = 64.dp
            cardH = 96.dp
            headerPad = 6.dp
            iconSize = 18.dp
            badgeSize = 16.dp
        }
    }

    // When lost/revealed: mute everything
    val bezzelColor = if (lost) Color(0xFF6A6A6A) else BrandTokens.BrassAged
    val paperColor = if (lost) Color(0xFF888880).copy(alpha = 0.55f) else BrandTokens.PaperCream
    val inkColor = if (lost) Color(0xFF888880) else BrandTokens.CreamInk
    val enamleColor = if (lost) Color(0xFF666666) else visual.color
    val borderWidth = if (lifted) 2.5.dp else 2.dp
    val elevation =
        if (lifted) {
            10.dp
        } else if (size == CardSize.Large) {
            5.dp
        } else {
            3.dp
        }

    // Outer brass bezel box
    Box(
        modifier =
            modifier
                .size(width = cardW, height = cardH)
                // Token drop-shadow (spec §7.1) — [elevation] above was computed but never applied;
                // wiring it in gives the card real contact weight against the felt (more when lifted).
                .shadow(
                    elevation,
                    Squircle(radius),
                    clip = false,
                    ambientColor = Color.Black,
                    spotColor = BrandTokens.TeakInk,
                )
                // Brass bezel: gradient top-to-bottom (gold highlight → brass → dark brass shadow)
                .clip(Squircle(radius))
                .background(
                    brush =
                        Brush.verticalGradient(
                            listOf(
                                BrandTokens.GoldAntique.copy(alpha = if (lost) 0.3f else 1f),
                                BrandTokens.BrassAged.copy(alpha = if (lost) 0.3f else 1f),
                                BrandTokens.BrassDark.copy(alpha = if (lost) 0.3f else 1f),
                            ),
                        ),
                ).brassSpecular()
                // Thin inner dark rim inside the brass
                .border(borderWidth, bezzelColor, Squircle(radius))
                .then(
                    if (onLongClick != null) {
                        Modifier.inspectable(
                            onClick = onClick ?: {},
                            onLongClick = onLongClick,
                            pressShape = Squircle(radius),
                        )
                    } else {
                        Modifier
                    },
                ),
    ) {
        // Inner paper inset (2.5dp inset from bezel edges)
        val inset = if (size == CardSize.Small) 2.dp else 3.dp
        Box(
            modifier =
                Modifier
                    .padding(inset)
                    .fillMaxSize()
                    .clip(Squircle(KursiRadii.lg))
                    .background(paperColor)
                    .drawBehind {
                        // Guilloché border pattern for this role
                        if (!lost && size != CardSize.Small) {
                            drawGuilloche(visual.framePattern, visual.color)
                        }
                        // Deboss inner shadow
                        drawDeboss()
                        // Paper grain
                        if (size == CardSize.Large) drawPaperGrain()
                    },
        ) {
            if (size == CardSize.Small) {
                // Compact: just role initial + enamel badge
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier =
                                Modifier
                                    .size(badgeSize)
                                    .clip(CircleShape)
                                    .background(enamleColor)
                                    .border(1.dp, BrandTokens.BrassAged, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            RoleGlyph(
                                role = role,
                                tint = KursiNeutrals.Cream,
                                deboss = false,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = role.name.take(3),
                            style = KursiType.caption.copy(fontSize = 8.sp),
                            color = inkColor,
                        )
                        if (lost) {
                            Text(
                                "LOST",
                                style = KursiType.caption.copy(fontSize = 6.sp),
                                color = BrandTokens.Oxblood,
                            )
                        }
                    }
                }
            } else {
                // Full certificate layout. The LIVE/LOST pill is overlaid in the top-end
                // corner (not inline in the header Row) so the role TITLE + flavor subtitle
                // own the full header width and never get squeezed into "JUGAA…"/"Netaji Vac…".
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // ── Top header: enamel badge left + role name (full width) ──────
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = headerPad, end = headerPad, top = headerPad * 0.7f, bottom = headerPad * 0.7f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Enamel badge roundel — role color inside brass ring
                            Box(
                                modifier =
                                    Modifier
                                        .size(badgeSize)
                                        .clip(CircleShape)
                                        .background(enamleColor)
                                        .border(
                                            1.5.dp,
                                            Brush.radialGradient(
                                                listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark),
                                            ),
                                            CircleShape,
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                RoleGlyph(
                                    role = role,
                                    tint = KursiNeutrals.Cream,
                                    deboss = false,
                                    modifier = Modifier.size(if (size == CardSize.Medium) 13.dp else 15.dp),
                                )
                            }

                            // Reserve a little room on the right for the overlaid LIVE/LOST pill.
                            // Pill is short; a small reserve keeps the title clear of it while
                            // leaving the title nearly the full header width.
                            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                // Role TITLE — length-driven base size so the longest role
                                // (JUGAADU, 7 chars) fits the header band in full without clipping
                                // to "JUGAA…". AutoSizeText still shrinks further for longer
                                // localized names, but the base already fits the known set.
                                val titleBase =
                                    when {
                                        size == CardSize.Medium -> if (role.name.length >= 6) 11.sp else 13.sp
                                        role.name.length >= 7 -> 11.sp // JUGAADU
                                        role.name.length >= 5 -> 13.sp // BABU/BHAI/NETA + headroom
                                        else -> 14.sp
                                    }
                                AutoSizeText(
                                    text = role.name,
                                    style = KursiType.cardRole.copy(fontSize = titleBase).rozha(),
                                    color = inkColor,
                                    maxLines = 1,
                                    minSize = 6.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (size == CardSize.Large) {
                                    // Flavor SUBTITLE — fit-to-width over up to 2 lines so
                                    // "Netaji Vachan" / "Babu Filewala" read in full instead of
                                    // "Netaji Vac…"/"Babu File…".
                                    AutoSizeText(
                                        text = visual.characterName,
                                        style = KursiType.caption.copy(fontSize = 9.sp, lineHeight = 11.sp),
                                        color = inkColor.copy(alpha = 0.55f),
                                        maxLines = 2,
                                        minSize = 7.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        // Thin brass rule below header
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                BrandTokens.BrassDark.copy(alpha = 0.3f),
                                                BrandTokens.GoldAntique.copy(alpha = 0.8f),
                                                BrandTokens.BrassDark.copy(alpha = 0.3f),
                                            ),
                                        ),
                                    ),
                        )

                        // ── Centre glyph panel ────────────────────────────────────────
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(
                                        // Very subtle role tint over cream
                                        visual.color.copy(alpha = if (lost) 0.04f else 0.07f),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Bespoke intaglio role mark — engraved into the cream paper.
                            RoleGlyph(
                                role = role,
                                tint =
                                    if (lost) {
                                        inkColor.copy(alpha = 0.22f)
                                    } else {
                                        visual.color.copy(alpha = 0.62f)
                                    },
                                deboss = !lost,
                                weight = 1.05f,
                                modifier = Modifier.size(iconSize),
                            )
                        }

                        // Thin brass rule above power lines
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                BrandTokens.BrassDark.copy(alpha = 0.3f),
                                                BrandTokens.GoldAntique.copy(alpha = 0.8f),
                                                BrandTokens.BrassDark.copy(alpha = 0.3f),
                                            ),
                                        ),
                                    ),
                        )

                        // ── Power lines (certificate small-caps style) ────────────────
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = headerPad, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            if (size == CardSize.Medium) {
                                // Medium spotlight medallion: the narrow claim band can't hold the
                                // full "ACTION · Tax +3 Khokhas" and TextAutoSize does NOT reliably
                                // shrink in the live app (it clipped to "…Tax +3 Kh…"). Use the
                                // pre-abbreviated deterministic short claim at a FIXED small font,
                                // wrapped to 2 lines — guaranteed to fit, never clipped.
                                Text(
                                    text = visual.claimLineShort,
                                    style =
                                        KursiType.body.copy(
                                            fontSize = 9.sp,
                                            lineHeight = 11.sp,
                                            letterSpacing = 0.2.sp,
                                        ),
                                    color = if (lost) inkColor.copy(alpha = 0.35f) else inkColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Visible,
                                    softWrap = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                AutoSizeText(
                                    // The longest power line — BHAI's "Assassinate −3 (target loses
                                    // influence)". Allow up to 3 lines and a smaller floor so the full
                                    // catch text reads instead of "…loses influe…".
                                    text = visual.actionLine,
                                    style =
                                        KursiType.body.copy(
                                            fontSize = 11.sp,
                                            lineHeight = 13.sp,
                                            letterSpacing = 0.2.sp,
                                        ),
                                    color = if (lost) inkColor.copy(alpha = 0.35f) else inkColor,
                                    maxLines = 3,
                                    minSize = 7.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (size == CardSize.Medium) {
                                // Deterministic short block line for the narrow spotlight band —
                                // "BLOCKS · Foreign Aid" overflows; shorten the prefix to "Blk:" and
                                // allow a 2-line wrap at a fixed font so it never clips.
                                val blockShort = visual.blockLine.replace("BLOCKS · ", "Blk: ")
                                Text(
                                    text = blockShort,
                                    style =
                                        KursiType.body.copy(
                                            fontSize = 8.5.sp,
                                            lineHeight = 10.sp,
                                            letterSpacing = 0.2.sp,
                                        ),
                                    color = if (lost) inkColor.copy(alpha = 0.25f) else inkColor.copy(alpha = 0.65f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Visible,
                                    softWrap = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                AutoSizeText(
                                    text = visual.blockLine,
                                    style =
                                        KursiType.body.copy(
                                            fontSize = 10.sp,
                                            lineHeight = 12.sp,
                                            letterSpacing = 0.2.sp,
                                        ),
                                    color = if (lost) inkColor.copy(alpha = 0.25f) else inkColor.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    minSize = 7.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            // Faint serial number — certificate feel
                            if (size == CardSize.Large) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "No. ${role.ordinal + 1}/${KursiColors.roles.size}",
                                    style = KursiType.numeric.copy(fontSize = 8.sp),
                                    color = inkColor.copy(alpha = 0.28f),
                                    modifier = Modifier.align(Alignment.End),
                                )
                            }
                        }
                    }
                    // LIVE / LOST pill — overlaid top-end so it never steals title width.
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(horizontal = headerPad, vertical = headerPad * 0.7f),
                    ) {
                        AlivePill(lost = lost)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlivePill(lost: Boolean) {
    val label = if (lost) "LOST" else "LIVE"
    val bg = if (lost) BrandTokens.Oxblood.copy(alpha = 0.85f) else BrandTokens.BrassDark.copy(alpha = 0.85f)
    val textColor = if (lost) KursiNeutrals.Cream else BrandTokens.GoldAntique
    Box(
        modifier =
            Modifier
                .clip(Squircle(KursiRadii.xs))
                .background(bg)
                .border(0.5.dp, BrandTokens.BrassAged.copy(alpha = 0.6f), Squircle(KursiRadii.xs))
                .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = KursiType.caption.copy(fontSize = 8.sp),
            color = textColor,
            maxLines = 1,
        )
    }
}

// ─────────────────────────── ChipState (§4 opponent chip state ring) ──────────

// ─────────────────────────── AutoSizeText (fit-to-width) ─────────────────────

/**
 * Single-line text that shrinks to fit its width instead of clipping or ellipsizing.
 * Used for labels that must show their real text in full — opponent persona names,
 * action-chip names, reaction chips — where copy length varies by language.
 *
 * Falls back to [minSize] and then ellipsis only if the text genuinely cannot fit
 * even at the smallest step (extreme edge case). Prefers fitting the real text.
 */
@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    minSize: TextUnit = 8.sp,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style =
            style.merge(
                TextStyle(
                    color = color,
                    textAlign = textAlign ?: TextAlign.Unspecified,
                ),
            ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize =
            TextAutoSize.StepBased(
                minFontSize = minSize,
                maxFontSize = style.fontSize.takeIf { it != TextUnit.Unspecified } ?: 16.sp,
                stepSize = 0.5.sp,
            ),
    )
}

/** State ring signals for an opponent chip — spec §4. */
enum class ChipState {
    Idle,
    Acting, // their turn — thick gold glow
    Responding, // can respond — amber pulsing ring
    ValidTarget, // green reticle glow + slight scale
    Eliminated, // 42% dim, "OUT"
}

// ─────────────────────────── OpponentPlate (Step 1) ──────────────────────────

/**
 * Fixed 176×96dp enamel nameplate for an opponent seat.
 *
 * Every slot ALWAYS renders — empty states show a dim placeholder, never collapse —
 * so all plates are exactly the same size regardless of game state (the fix for
 * "don't look uniform").
 *
 * State ring encodes turn/threat as the plate's rim color+weight:
 *  idle → thin brass; acting → thick brass+pulse; challengeable → verdigris;
 *  targetable → alert_red; eliminated → desaturated plate.
 */
@Composable
fun OpponentPlate(
    name: String,
    seatColor: Color,
    roleColor: Color?, // null if unknown (face-down)
    role: Role?, // null if unknown (face-down) — drives the bespoke crest glyph
    coins: Int,
    influenceLost: Int, // 0..2 — lost (hollow ◇). The alive count is derived from the slot total.
    // The alive count is NOT a parameter: it is opponentPlateSemantics' job (see :feature:game
    // A11y.kt), and the pip row here renders from influenceLost alone.
    claim: String?, // e.g. "⚖ claims NETA" or null — the LIVE/pending claim
    lastAction: String?, // e.g. "↳ FDI +2" or null
    state: ChipState,
    modifier: Modifier = Modifier,
    /**
     * Standing claim trail derived from the public event history — e.g. "claimed NETA ×2"
     * or "last: BABU". Shown in the claim slot whenever there is no LIVE [claim] so the
     * plate reflects what this seat has been claiming across the game instead of
     * collapsing to "— no claim —". Null only when this seat has never claimed a role.
     */
    claimSummary: String? = null,
    /** True when one of this seat's role-claims was caught bluffing (challenge revealed no role). */
    claimCaught: Boolean = false,
    /** True when one of this seat's role-claims was proven (challenge revealed they held it). */
    claimProven: Boolean = false,
    /**
     * Suspicion / bluff-odds read on this seat's STANDING claim, as raw presentation data
     * (1..5 pips + short label) so :core:designsystem stays free of an :ai dependency.
     * Null when there is no standing claim to assess. Rendered as a compact brass odds chip.
     */
    suspicionPips: Int? = null,
    suspicionLabel: String? = null,
    /**
     * Responsive plate width. Driven by seat count + felt width upstream so the
     * arc stays balanced: generous at 2p, compact (but legible) at 10p. Height
     * and inner typography track the width. Defaults to the original 176dp.
     */
    plateWidth: Dp = 176.dp,
    onClick: () -> Unit = {},
    /** Long-press = always inspect (open the dossier), independent of [onClick]'s target-select duty. */
    onLongClick: () -> Unit = onClick,
) {
    val enamelBase = Color(0xFF14110D)
    val brassColor = BrandTokens.BrassAged
    val brassDim = BrandTokens.BrassDark
    val alertRed = Color(0xFF8E2B22)
    val verdigris = Color(0xFF3F6B5E)

    val eliminated = state == ChipState.Eliminated
    val plateAlpha = if (eliminated) 0.35f else 1f

    // Animated pulse for Acting state
    val pulseAlpha by if (state == ChipState.Acting) {
        rememberInfiniteTransition(label = "platePulse").animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "platePulseAlpha",
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    // RETRO-FUTURIST §2 — holographic rim sweep phase for the LIVE (acting) plate.
    val holoPhase by if (state == ChipState.Acting) {
        rememberInfiniteTransition(label = "plateHolo").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
            label = "plateHoloPhase",
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // Ring color + width from state. AAA rebuild (design-language.md #1): Idle — the
    // plate's default, majority-of-the-time state — drops to a near-invisible rim so the
    // plate reads as a shadow-depth surface (tableDepth + embossEdge below), sharing
    // OpponentSeatToken's no-hard-border material. Acting/Responding/ValidTarget/Eliminated
    // keep a real ring: that's a live state SIGNAL, not decorative framing.
    val ringColor: Color =
        when (state) {
            ChipState.Acting -> brassColor.copy(alpha = pulseAlpha)
            ChipState.Responding -> verdigris
            ChipState.ValidTarget -> alertRed
            ChipState.Eliminated -> brassDim.copy(alpha = 0.4f)
            ChipState.Idle -> brassColor.copy(alpha = 0.22f)
        }
    val ringWidth: androidx.compose.ui.unit.Dp =
        when (state) {
            ChipState.Acting, ChipState.ValidTarget -> KursiDimens.stroke_ring_active
            ChipState.Responding -> KursiDimens.stroke_ring_active
            else -> KursiDimens.stroke_ring_idle
        }

    // Responsive metrics: height + crest track the plate width so dense tables
    // (10p) stay compact without cramping, and sparse tables (2p) feel generous.
    val widthFactor = (plateWidth / 176.dp).coerceIn(0.78f, 1.30f)
    // Floor is high enough that all three internal rows (crest+name, claim+last-action,
    // affordance footer) always fit even on the most compact 10p plate — otherwise the
    // SpaceBetween column overflowed a too-short plate and clipped the "tap for dossier"
    // footer on the denser row.
    val plateHeight = (96.dp * widthFactor).coerceIn(92.dp, 120.dp)
    val crestSize = (24.dp * widthFactor).coerceIn(20.dp, 30.dp)
    val nameStyle = if (widthFactor < 0.9f) KursiType.label_sm else KursiType.label_md

    // ── P2 active-actor spotlight: the acting seat lifts + scales + warm rim glow.
    val acting = state == ChipState.Acting
    val targetScale =
        when {
            acting -> 1.045f
            state == ChipState.ValidTarget -> 1.025f
            else -> 1f
        }
    val animScale by animateFloatAsState(targetScale, tween(220), label = "plateScale")
    // Non-focal seats recede (P2: exactly one focal point).
    val recede = !acting && state != ChipState.ValidTarget && state != ChipState.Responding && !eliminated
    val contentDim = if (recede) 0.86f else 1f

    // DEPTH3D — the acting plate physically rises off the felt: a real translationY lift
    // (paired with the deeper lifted shadow below) so it stacks ABOVE its neighbours
    // instead of merely scaling in place. The lift settles smoothly as turns pass.
    val liftY by animateFloatAsState(
        targetValue =
            if (acting) {
                -5f
            } else if (state == ChipState.Responding) {
                -2.5f
            } else {
                0f
            },
        animationSpec = tween(240),
        label = "plateLiftY",
    )

    val plateShape = Squircle(KursiDimens.r_md)

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    translationY = liftY * density
                }.scale(animScale)
                // P3 depth: layered contact shadow, lifted when this seat is acting.
                .tableDepth(plateShape, elevation = 5.dp, lifted = acting)
                // Acting seats get a premium holographic rim-light: a soft brass bloom plus
                // a thin brass↔cyan glint travelling the bezel (RETRO-FUTURIST §2). The
                // role hue tints it when known, else aged brass.
                .then(
                    if (acting) {
                        Modifier.holoRimLight(
                            accent = (roleColor ?: brassColor),
                            phase = holoPhase,
                            cornerRadius = KursiDimens.r_md,
                            intensity = 0.55f + 0.45f * pulseAlpha,
                        )
                    } else {
                        Modifier
                    },
                ).size(width = plateWidth, height = plateHeight)
                .clip(plateShape)
                // Brass-tinted enamel ground with a subtle vertical sheen (lit top).
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1E1812),
                            enamelBase,
                            BrandTokens.TeakInk,
                        ),
                    ),
                ).border(ringWidth, ringColor, plateShape)
                .embossEdge(KursiDimens.r_md)
                .inspectable(onClick = onClick, onLongClick = onLongClick, enabled = !eliminated, pressShape = plateShape),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = KursiDimens.space_sm, vertical = KursiDimens.space_xs)
                    .alpha(plateAlpha * contentDim),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ── Row 1: crest + name + coins + influence pips ──────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KursiDimens.space_xs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Crest medallion — scales with plate width
                val crestColor = roleColor ?: seatColor
                Box(
                    modifier =
                        Modifier
                            .size(crestSize)
                            .clip(CircleShape)
                            .background(crestColor.copy(alpha = 0.85f))
                            .border(KursiDimens.stroke_hairline, brassColor.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (eliminated) {
                        Text("✕", style = KursiType.label_micro, color = BrandTokens.StampRed)
                    } else if (role != null) {
                        RoleGlyph(
                            role = role,
                            tint = KursiNeutrals.Cream,
                            deboss = false,
                            modifier = Modifier.size((crestSize.value * 0.62f).dp),
                        )
                    } else {
                        Text(
                            text = name.filter { it.isLetter() }.take(1).uppercase(),
                            style = KursiType.label_micro,
                            color = KursiNeutrals.Cream,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // Name — fit-to-width so the real persona name shows in full and
                // shrinks gracefully instead of clipping ("Netaji Vachan", etc.).
                AutoSizeText(
                    text = name,
                    style = nameStyle,
                    color = KursiNeutrals.TextPrimary,
                    maxLines = 1,
                    minSize = 9.sp,
                    modifier = Modifier.weight(1f),
                )

                // Coins — numeral_sm, tabular brass
                Text(
                    text = coins.toString(),
                    style = KursiType.numeral_sm,
                    color = BrandTokens.GoldAntique,
                )

                // Influence pips — ALWAYS exactly 2 slots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val totalSlots = 2
                    val lostCount = influenceLost.coerceAtMost(totalSlots)
                    val aliveCount = (totalSlots - lostCount).coerceAtLeast(0)
                    repeat(aliveCount) {
                        Box(
                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(brassColor.copy(alpha = 0.85f))
                                    .border(0.5.dp, BrandTokens.GoldAntique.copy(alpha = 0.6f), CircleShape),
                        )
                    }
                    repeat(lostCount) {
                        Box(
                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .border(1.dp, brassDim.copy(alpha = 0.5f), CircleShape),
                        )
                    }
                }
            }

            // ── Row 2: claim slot + last-action slot ──────────────────
            // Priority: a LIVE/pending [claim] reads most prominently. With no live claim,
            // fall back to the STANDING [claimSummary] derived from the public event log
            // (e.g. "claimed NETA ×2") so the plate keeps reflecting what this seat has been
            // claiming across the whole game instead of collapsing to "— no claim —".
            val hasLiveClaim = claim != null
            val standingText: String? = claim ?: claimSummary
            val slotFilled = standingText != null
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KursiDimens.space_xs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Claim slot — ALWAYS present
                val claimText = standingText ?: "— no claim yet —"
                // Caught-bluffing tints the slot oxblood; a live/standing claim reads brass
                // (verdigris while this seat is mid-reaction). Empty stays dim.
                val claimColor =
                    when {
                        !slotFilled -> brassDim
                        claimCaught -> BrandTokens.StampRed
                        hasLiveClaim && state == ChipState.Responding -> verdigris
                        else -> brassColor
                    }
                val slotBg =
                    when {
                        !slotFilled -> enamelBase
                        claimCaught -> BrandTokens.Oxblood.copy(alpha = 0.18f)
                        hasLiveClaim -> brassColor.copy(alpha = 0.16f)
                        else -> brassColor.copy(alpha = 0.07f)
                    }
                val slotBorder =
                    when {
                        !slotFilled -> brassDim.copy(alpha = 0.2f)
                        claimCaught -> BrandTokens.StampRed.copy(alpha = 0.5f)
                        hasLiveClaim -> brassColor.copy(alpha = 0.55f)
                        else -> brassColor.copy(alpha = 0.3f)
                    }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(Squircle(KursiDimens.r_sm))
                            .background(slotBg)
                            .border(KursiDimens.stroke_hairline, slotBorder, Squircle(KursiDimens.r_sm))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    val suffix =
                        when {
                            hasLiveClaim && state == ChipState.Responding -> " ?"
                            claimCaught -> " ✗"
                            claimProven -> " ✓"
                            else -> ""
                        }
                    Text(
                        text = "$claimText$suffix",
                        style =
                            if (slotFilled) {
                                KursiType.label_micro.copy(fontStyle = FontStyle.Normal)
                            } else {
                                KursiType.label_micro.copy(fontStyle = FontStyle.Italic)
                            },
                        color = claimColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Last-action slot — ALWAYS present
                val lastText = if (lastAction != null) "↳ $lastAction" else "↳ —"
                Text(
                    text = lastText,
                    style = KursiType.label_micro,
                    color = if (lastAction != null) KursiNeutrals.TextMuted else brassDim.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── Row 3: suspicion chip (left) + affordance hint (right) ─
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KursiDimens.space_xs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!eliminated && suspicionPips != null && suspicionLabel != null) {
                    SuspicionChip(pips = suspicionPips, label = suspicionLabel)
                }
                Text(
                    text =
                        when {
                            eliminated -> "eliminated — no longer in play"
                            state == ChipState.ValidTarget -> "▸ tap to target · hold for dossier"
                            else -> "▸ hold for dossier"
                        },
                    style = KursiType.label_micro,
                    color = brassDim.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

// ─────────────────────────── SuspicionChip (§ odds read) ─────────────────────

/**
 * Compact brass odds chip showing a 1..5 suspicion read on an opponent's standing
 * claim. Mirrors the Informatics `OddsChip` brass-on-teak styling (pip row + label)
 * but takes raw presentation data so :core:designsystem stays free of an :ai dependency.
 *
 * [pips]  1 = likely honest … 5 = very likely bluffing.
 * [label] short human read, e.g. "coin-flip", "long shot".
 */
@Composable
fun SuspicionChip(
    pips: Int,
    label: String,
) {
    val clamped = pips.coerceIn(1, 5)
    val pipColor =
        when (clamped) {
            1, 2 -> KursiSemantics.Success
            3 -> BrandTokens.PendingAmber
            else -> KursiSemantics.Danger
        }
    Row(
        modifier =
            Modifier
                .clip(Squircle(KursiRadii.sm))
                .background(BrandTokens.TeakDark.copy(alpha = 0.90f))
                .border(KursiDimens.stroke_hairline, BrandTokens.BrassAged.copy(alpha = 0.5f), Squircle(KursiRadii.sm))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(5) { idx ->
            Box(
                modifier =
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (idx < clamped) pipColor else KursiNeutrals.TextDisabled),
            )
        }
        Spacer(Modifier.width(2.dp))
        Text(
            text = label,
            style = KursiType.label_micro,
            color = KursiNeutrals.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Middle-ellipsis helper: if [text] exceeds [maxLen] chars, renders "first…last". */
private fun middleEllipsis(
    text: String,
    maxLen: Int,
): String {
    if (text.length <= maxLen) return text
    val half = (maxLen - 1) / 2
    return text.take(half) + "…" + text.takeLast(half)
}

// ─────────────────────────── SeatAvatar ──────────────────────────────────────

/**
 * Brass-roundel avatar showing the player's seat-color initial.
 */
@Composable
fun SeatAvatar(
    initial: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(Squircle(KursiRadii.md))
                .background(
                    brush =
                        Brush.radialGradient(
                            listOf(BrandTokens.GoldAntique, color, BrandTokens.BrassDark),
                        ),
                ).border(1.dp, BrandTokens.BrassAged, Squircle(KursiRadii.md)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = KursiType.name,
            color = BrandTokens.TeakDark,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────── CoinPill ──────────────────────────────────────

/**
 * Brass Khokha coin roundel — spec §8.
 * A brass roundel with coin emblem + numeral. No emoji.
 */
@Composable
fun CoinPill(
    count: Int,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    // Coin/token juice (spec §7): a small spring bump when the count changes, so a
    // Tax/Steal/Income coin landing reads as a physical arrival, not just a text swap.
    // `mounted` skips the bump on first composition — only real count changes pop.
    val reducedMotion = LocalReducedMotion.current
    val bump = remember { Animatable(1f) }
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(count) {
        if (mounted && !reducedMotion) {
            bump.snapTo(1.22f)
            bump.animateTo(1f, KursiMotion.snap())
        }
        mounted = true
    }
    Row(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = bump.value
                    scaleY = bump.value
                }.clip(Squircle(KursiRadii.sm))
                .background(
                    brush =
                        Brush.horizontalGradient(
                            listOf(
                                BrandTokens.BrassDark.copy(alpha = 0.7f * alpha),
                                BrandTokens.BrassAged.copy(alpha = 0.85f * alpha),
                                BrandTokens.BrassDark.copy(alpha = 0.7f * alpha),
                            ),
                        ),
                ).border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(BrandTokens.GoldAntique.copy(alpha = alpha), BrandTokens.BrassDark.copy(alpha = alpha)),
                    ),
                    Squircle(KursiRadii.sm),
                ).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Brass coin roundel emblem
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        brush =
                            Brush.radialGradient(
                                listOf(BrandTokens.GoldAntique.copy(alpha = alpha), BrandTokens.BrassDark.copy(alpha = alpha)),
                            ),
                    ).border(0.8.dp, BrandTokens.BrassDark.copy(alpha = alpha), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "K",
                style = KursiType.caption.copy(fontSize = 6.sp),
                color = BrandTokens.TeakDark.copy(alpha = alpha),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = count.toString(),
            style = KursiType.numeric.copy(fontSize = 12.sp),
            color = BrandTokens.GoldAntique.copy(alpha = alpha),
        )
    }
}

// ─────────────────────────── InfluencePips ───────────────────────────────────

/**
 * Influence pip row — spec §4.
 * Alive pips are brass-tinted circles; lost pips are role-colored.
 */
@Composable
fun InfluencePips(
    alive: Int,
    lost: List<Role>,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(alive) {
            Box(
                modifier =
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(BrandTokens.BrassAged.copy(alpha = 0.8f * alpha))
                        .border(0.5.dp, BrandTokens.GoldAntique.copy(alpha = 0.6f * alpha), CircleShape),
            )
        }
        lost.forEach { role ->
            val roleColor = KursiColors.forRole(role).color
            Box(
                modifier =
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(roleColor.copy(alpha = 0.60f * alpha))
                        .border(0.5.dp, BrandTokens.BrassDark.copy(alpha = 0.4f * alpha), CircleShape),
            )
        }
    }
}

// ─────────────────────────── ClaimChip ───────────────────────────────────────

/**
 * Stamped claim tab — spec §4.
 * Looks like a rubber-stamp chit in the seat color with a brass border.
 */
@Composable
fun ClaimChip(
    text: String,
    color: Color,
    verified: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = if (verified) text else "$text?"
    Box(
        modifier =
            modifier
                .clip(Squircle(KursiRadii.sm))
                .background(color.copy(alpha = 0.20f))
                .border(
                    0.8.dp,
                    Brush.horizontalGradient(
                        listOf(BrandTokens.BrassAged.copy(alpha = 0.7f), color.copy(alpha = 0.6f)),
                    ),
                    Squircle(KursiRadii.sm),
                ).padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = KursiType.label.copy(fontSize = 10.sp),
            color = color,
        )
    }
}

// ─────────────────────────── KursiActionButton ───────────────────────────────

/**
 * Brass-bezeled action button — spec §9.
 *
 * - [enabled] = false → dim teak surface, grey border, muted text.
 * - [roleAccent] tints the enamel border when this action requires a role claim.
 */
@Composable
fun KursiActionButton(
    label: String,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    roleAccent: Color? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onClick: () -> Unit = {},
) {
    val radius = KursiRadii.xl
    val bg =
        when {
            !enabled -> BrandTokens.TeakDark
            roleAccent != null -> roleAccent.copy(alpha = 0.14f)
            else -> BrandTokens.TeakMid
        }
    val borderBrush =
        when {
            !enabled -> Brush.horizontalGradient(listOf(BrandTokens.BrassDark.copy(alpha = 0.4f), BrandTokens.BrassDark.copy(alpha = 0.4f)))
            roleAccent != null -> Brush.horizontalGradient(listOf(roleAccent, BrandTokens.BrassAged.copy(alpha = 0.7f)))
            else ->
                Brush.horizontalGradient(
                    listOf(BrandTokens.GoldAntique.copy(alpha = 0.9f), BrandTokens.BrassAged.copy(alpha = 0.7f), BrandTokens.BrassDark.copy(alpha = 0.5f)),
                )
        }
    val contentAlpha = if (enabled) 1f else 0.40f

    Box(
        modifier =
            modifier
                .clip(Squircle(radius))
                .background(bg)
                .border(1.5.dp, borderBrush, Squircle(radius))
                .then(if (enabled) Modifier.brassSpecular() else Modifier)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (leadingIcon != null) leadingIcon()
                Text(
                    text = label,
                    style = KursiType.title,
                    color = KursiNeutrals.TextPrimary.copy(alpha = contentAlpha),
                    textAlign = TextAlign.Center,
                )
            }
            if (sublabel != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sublabel,
                    style = KursiType.caption,
                    color = KursiNeutrals.TextSecondary.copy(alpha = contentAlpha),
                    textAlign = TextAlign.Center,
                )
            }
            if (!enabled && disabledReason != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = disabledReason,
                    style = KursiType.caption,
                    color = KursiSemantics.Danger.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ─────────────────────────── StatusSpine ─────────────────────────────────────

/** Tone for the [StatusSpine] background / accent. */
enum class SpineTone { Info, Pending, Danger, Gold }

/**
 * ANALYST top chrome — the running turn verdict (spec §6).
 *
 * AAA rebuild (design-language.md #1/#3): the old full-width filled-gold-gradient bar +
 * 1.5dp border is gone. This is now a shadow-depth raised surface — the same [tableDepth]
 * + emboss-bevel material as [decoPanel] one hairline rule underneath, matching every
 * other engraved header in the app (non-negotiable #3: "engraved chrome, not fat bars").
 * The verdict text carries the tone as color, not the plate as a gold fill.
 */
@Composable
fun StatusSpine(
    text: String,
    tone: SpineTone = SpineTone.Info,
    trailingTimer: String? = null,
    modifier: Modifier = Modifier,
) {
    val accentColor: Color =
        when (tone) {
            SpineTone.Info -> BrandTokens.BrassAged
            SpineTone.Pending -> BrandTokens.PendingAmber
            SpineTone.Danger -> BrandTokens.StampRed
            SpineTone.Gold -> BrandTokens.GoldAntique
        }
    val shape = Squircle(KursiRadii.lg)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .tableDepth(shape, elevation = 6.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            KursiFeltColors.Surface3.copy(alpha = 0.96f),
                            KursiFeltColors.Surface2,
                            BrandTokens.TeakDark,
                        ),
                    ),
                ).embossEdge(KursiRadii.lg, highlight = accentColor.copy(alpha = 0.4f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                // M4 §3: status-spine verdict is in-game HERO text — render in the real
                // Rozha One display serif, not the placeholder system serif.
                style = KursiType.title.rozha(),
                color = accentColor,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingTimer != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = trailingTimer,
                    style = KursiType.numeric,
                    color = accentColor,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, accentColor.copy(alpha = 0.55f), Color.Transparent),
                        ),
                    ),
        )
    }
}

// ─────────────────────────── OutcomeTag ──────────────────────────────────────

/** Color-coded outcome tag — spec §7. */
enum class OutcomeKind {
    Neutral,
    Success,
    ChallengeFail,
    BluffCaught,
    Block,
    Eliminated,
}

/**
 * Small color-coded log result chip — spec §7.
 */
@Composable
fun OutcomeTag(
    kind: OutcomeKind,
    modifier: Modifier = Modifier,
) {
    val (label, color) =
        when (kind) {
            OutcomeKind.Neutral -> "OK" to KursiNeutrals.TextMuted
            OutcomeKind.Success -> "TRUE" to KursiSemantics.Success
            OutcomeKind.ChallengeFail -> "PROVED" to KursiSemantics.Block
            OutcomeKind.BluffCaught -> "BLUFF" to KursiSemantics.Danger
            OutcomeKind.Block -> "BLOCKED" to KursiSemantics.Pending
            OutcomeKind.Eliminated -> "OUT" to KursiSemantics.Danger
        }
    Box(
        modifier =
            modifier
                .clip(Squircle(KursiRadii.sm))
                .background(color.copy(alpha = 0.15f))
                .border(
                    0.5.dp,
                    Brush.horizontalGradient(listOf(BrandTokens.BrassAged.copy(alpha = 0.5f), color.copy(alpha = 0.5f))),
                    Squircle(KursiRadii.sm),
                ).padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = KursiType.caption.copy(fontSize = 8.5.sp, letterSpacing = 0.3.sp),
            color = color,
            maxLines = 1,
        )
    }
}

// ─────────────────────────── CountdownBar ────────────────────────────────────

/**
 * Reaction timer drain bar — spec §8 motion notes.
 * [fraction] 1.0 = full, 0.0 = empty.
 */
@Composable
fun CountdownBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val color = if (fraction < 0.25f) BrandTokens.StampRed else BrandTokens.PendingAmber
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(Squircle(KursiRadii.xs))
                .background(BrandTokens.TeakDark),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(Squircle(KursiRadii.xs))
                    .background(
                        brush =
                            Brush.horizontalGradient(
                                listOf(color, color.copy(alpha = 0.7f)),
                            ),
                    ),
        )
    }
}

// ─────────────────────────── FeltTableBackground ─────────────────────────────

/**
 * Teak-and-brass council table surface — spec §8 (License Raj Deco).
 * Replaces the old dark-green felt. Warm teak radial gradient with a
 * thin brass inlay border and a ghosted chair-in-sunburst emblem at center.
 */
@Composable
fun FeltTableBackground(
    modifier: Modifier = Modifier,
    /**
     * AAA FOCUS rebuild: the framed "card in a void" brass sweep border reads as a boxed panel
     * floating in the ink background. FOCUS/GUIDED want the felt to BE the whole screen — one
     * continuous lit surface, no frame. Set false to drop the border (ANALYST/default keeps it).
     */
    bordered: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .clip(Squircle(KursiRadii.xxl))
                .background(
                    brush =
                        Brush.radialGradient(
                            // Warm teak: slightly lighter at centre, darker at edges
                            colors =
                                listOf(
                                    BrandTokens.TeakMid,
                                    BrandTokens.TeakDark,
                                ),
                        ),
                ).drawBehind {
                    // Ghosted chair emblem
                    drawChairEmblem()
                    // Faint engraved hatch on teak panels
                    val hatchAlpha = TextureTokens.teakHatchAlpha
                    val step = 18.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            BrandTokens.GoldAntique.copy(alpha = hatchAlpha),
                            Offset(x, 0f),
                            Offset(x, size.height),
                            strokeWidth = 0.5.dp.toPx(),
                        )
                        x += step
                    }
                    // Lamplit-desk key light + vignette (spec §7.1) — phone felt was missing the
                    // warm centre / shadowed rim that the desktop FeltTableSurface already has.
                    drawTableVignette(centerWarmth = 0.14f, rimDarkness = 0.52f)
                }.then(
                    if (bordered) {
                        Modifier.border(
                            2.dp,
                            // Brass inlay border: gradient to simulate engraved edge
                            Brush.sweepGradient(
                                listOf(
                                    BrandTokens.GoldAntique,
                                    BrandTokens.BrassAged,
                                    BrandTokens.BrassDark,
                                    BrandTokens.BrassAged,
                                    BrandTokens.GoldAntique,
                                ),
                            ),
                            Squircle(KursiRadii.xxl),
                        )
                    } else {
                        Modifier
                    },
                ).feltMaterial(), // default bloomCenterYFraction matches drawTableVignette's cy above
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ─────────────────────────── Backward-compat aliases ──────────────────────────
// CardFace was removed with RoleCard's dead `faceUp` parameter: it was the only caller and had
// no callers of its own.

/** Back-compat: [KursiActionButton] row under the old [ActionBar] name. */
@Composable
fun ActionBar(
    actions: List<String>,
    onAction: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(Squircle(KursiRadii.lg))
                .background(BrandTokens.TeakMid)
                .border(
                    1.dp,
                    Brush.horizontalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark)),
                    Squircle(KursiRadii.lg),
                ).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, label ->
            KursiActionButton(
                label = label,
                enabled = true,
                onClick = { onAction(index) },
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// §P3 DEPTH PRIMITIVES — shared tactile/emboss helpers.
// All multiplatform-safe (graphicsLayer / shadow / Canvas; no AGSL).
// These give every felt object a layered contact shadow + a brass/teak bevel edge
// so it reads as an object sitting ON the table, not a flat fill.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Layered tactile depth: a tight warm contact shadow under the element plus an
 * ambient softer one, clipped to [shape]. Use on plates, dock, log, hand.
 *
 * [lifted] raises the elevation and warms the contact shadow (active-actor / hover).
 */
fun Modifier.tableDepth(
    shape: androidx.compose.ui.graphics.Shape,
    elevation: Dp = 6.dp,
    lifted: Boolean = false,
): Modifier {
    // DEPTH3D — a lifted surface throws a markedly wider ambient shadow plus a tighter,
    // darker contact shadow, so raised plates/panels read as floating distinctly above
    // the felt (real elevation layering) rather than just brighter-bordered.
    val ambient = if (lifted) (elevation + 9.dp) else elevation
    val contact = if (lifted) (elevation * 0.75f) else (elevation * 0.45f)
    return this
        // Ambient soft shadow (cool, wide)
        .shadow(
            elevation = ambient,
            shape = shape,
            ambientColor = Color(0xFF000000).copy(alpha = 0.55f),
            spotColor = Color(0xFF000000).copy(alpha = 0.55f),
            clip = false,
        )
        // Tight warm contact shadow (close, offset down)
        .shadow(
            elevation = contact,
            shape = shape,
            ambientColor = BrandTokens.TeakInk,
            spotColor = BrandTokens.TeakInk,
            clip = false,
        )
}

/**
 * Brass/teak bevel edge drawn ON the surface: a lit top-left highlight and a
 * shadowed bottom-right, giving a 2-tone embossed rim. Draw AFTER the background,
 * clipped to [shape]'s corner radius via [cornerRadius].
 */
fun Modifier.embossEdge(
    cornerRadius: Dp,
    highlight: Color = BrandTokens.GoldAntique.copy(alpha = 0.55f),
    shadow: Color = BrandTokens.TeakInk.copy(alpha = 0.65f),
    strokeWidth: Dp = 1.2.dp,
): Modifier =
    this.drawWithContent {
        drawContent()
        val r = cornerRadius.toPx()
        val sw = strokeWidth.toPx()
        val inset = sw / 2f
        // Top + left = lit highlight
        drawRoundRect(
            brush =
                Brush.linearGradient(
                    colors = listOf(highlight, highlight.copy(alpha = 0f)),
                    start = Offset(0f, 0f),
                    end = Offset(size.width * 0.7f, size.height * 0.7f),
                ),
            topLeft = Offset(inset, inset),
            size = Size(size.width - sw, size.height - sw),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(r, r),
            style = Stroke(width = sw),
        )
        // Bottom + right = shadow
        drawRoundRect(
            brush =
                Brush.linearGradient(
                    colors = listOf(shadow.copy(alpha = 0f), shadow),
                    start = Offset(size.width * 0.3f, size.height * 0.3f),
                    end = Offset(size.width, size.height),
                ),
            topLeft = Offset(inset, inset),
            size = Size(size.width - sw, size.height - sw),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(r, r),
            style = Stroke(width = sw),
        )
    }

// ═══════════════════════════════════════════════════════════════════════════════
//  M4 §2 — RETRO-FUTURIST LIVE ACCENTS
//
//  Tasteful glow / holographic rim-light + terminal scanline motifs reserved for
//  LIVE / ACTIVE table elements (the acting plate, the live claim medallion, the
//  recommended move). Premium brushed-brass-over-glass, never gaudy: a single thin
//  holographic hairline that breathes, a soft outer bloom, and the faintest phosphor
//  raster extended from the Roznamcha log onto the active surface.
//
//  Multiplatform-safe: pure Compose DrawScope + gradients, NO AGSL/RuntimeShader.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Holographic rim-light for a LIVE element. Lays, behind the content:
 *   1. a soft outer bloom in [accent] (the element "emits" light onto the felt),
 *   2. a thin holographic hairline on the edge that sweeps hue brass↔accent↔cyan
 *      as [phase] advances, reading as a precision-machined glass/brass rim.
 * Drive [phase] from a shared pulse so it stays in sync with the element's pulse;
 * pass a constant for a static (reduced-motion / harness) frame.
 *
 * @param accent the live element's signature hue (role hue, or brass for the actor).
 * @param phase 0..1 sweep position of the holographic glint.
 * @param cornerRadius corner radius of the framed element.
 * @param intensity 0..1 overall strength (dim it on dense/small elements).
 */
fun Modifier.holoRimLight(
    accent: Color,
    phase: Float,
    cornerRadius: Dp,
    intensity: Float = 1f,
): Modifier =
    this.drawBehind {
        val r = cornerRadius.toPx()
        val cr =
            androidx.compose.ui.geometry
                .CornerRadius(r, r)
        // 1. Outer bloom — a halo bleeding past the element edge.
        val bloom = (10.dp.toPx())
        drawRoundRect(
            brush =
                Brush.radialGradient(
                    listOf(
                        accent.copy(alpha = 0.34f * intensity),
                        accent.copy(alpha = 0.10f * intensity),
                        Color.Transparent,
                    ),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.maxDimension * 0.62f,
                ),
            topLeft = Offset(-bloom, -bloom),
            size = Size(size.width + bloom * 2, size.height + bloom * 2),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(r + bloom, r + bloom),
        )
        // 2. Holographic hairline — a brass→accent→cyan sweep along the rim. A sweep
        // gradient rotated by phase gives the "glint travelling around the bezel" read.
        val holo =
            Brush.sweepGradient(
                colors =
                    listOf(
                        BrandTokens.GoldAntique.copy(alpha = 0.85f * intensity),
                        accent.copy(alpha = 0.9f * intensity),
                        Color(0xFF7FE3D6).copy(alpha = 0.7f * intensity), // cool holographic cyan
                        BrandTokens.GoldAntique.copy(alpha = 0.85f * intensity),
                        accent.copy(alpha = 0.9f * intensity),
                    ),
                center =
                    Offset(
                        size.width * (0.5f + 0.5f * cosUnit(phase)),
                        size.height * (0.5f + 0.5f * sinUnit(phase)),
                    ),
            )
        drawRoundRect(
            brush = holo,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = cr,
            style = Stroke(width = 1.4.dp.toPx()),
        )
    }

private fun cosUnit(t: Float): Float = cos(t * 2f * PI.toFloat())

private fun sinUnit(t: Float): Float = sin(t * 2f * PI.toFloat())

/**
 * Faint phosphor scanline sheen — the same terminal raster as the Roznamcha log,
 * laid at very low alpha over a LIVE surface so the active element shares the
 * "sarkari teleprinter" motif. Draw OVER the surface content (use in drawWithContent
 * after drawContent, or drawBehind on a transparent overlay box).
 *
 * @param tint scanline color (defaults to a cool phosphor cyan).
 * @param alpha peak line alpha (keep ≤ 0.06 — this must whisper, not shout).
 * @param spacingDp vertical line spacing.
 */
fun DrawScope.drawScanlineSheen(
    tint: Color = Color(0xFF8FE7DA),
    alpha: Float = 0.05f,
    spacingDp: Float = 3f,
) {
    val step = spacingDp.dp.toPx().coerceAtLeast(2f)
    var y = 0f
    while (y < size.height) {
        drawLine(
            tint.copy(alpha = alpha),
            Offset(0f, y),
            Offset(size.width, y),
            strokeWidth = 0.75.dp.toPx(),
        )
        y += step
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  M4 §1 — DECO POPOVER TREATMENT
//
//  Every chit / dossier / popover routes through this surface so they read as an
//  aged-brass-framed cream document dropped on the teak table — never a Material-flat
//  sheet. Composition (outer → inner):
//    1. teak scrim drop-shadow (the card sits ON the table, casting onto it)
//    2. aged-brass frame: a brushed gradient bezel with a guilloché-style hairline
//    3. cream paper face with a faint diagonal paper-grain + a debossed inner rule
//  A wax-seal accent ([WaxSeal]) is laid by the caller over the top-end corner.
//
//  Multiplatform-safe: pure Compose DrawScope + gradients, no AGSL/RuntimeShader.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * The aged-brass-framed cream document surface for popovers. Apply to the popover's
 * outer container; it clips, frames, fills, grains and rules in one pass. Pad the
 * content yourself after this (it adds only the frame inset).
 *
 * @param radius corner radius of the card + frame.
 */
fun Modifier.decoPopoverPaper(radius: Dp = KursiRadii.md): Modifier {
    val shape = Squircle(radius)
    return this
        // 1. Teak contact shadow — the document rests on the table.
        .tableDepth(shape, elevation = 10.dp, lifted = true)
        // 2. Aged-brass brushed frame.
        .clip(shape)
        .background(
            Brush.linearGradient(
                listOf(
                    BrandTokens.GoldAntique,
                    BrandTokens.BrassAged,
                    BrandTokens.BrassDark,
                    BrandTokens.BrassAged,
                ),
            ),
        )
        // 2b. Bright outer brass hairline.
        .border(0.75.dp, BrandTokens.GoldAntique.copy(alpha = 0.9f), shape)
        // 2c. Frame thickness (the brass bezel width).
        .padding(2.dp)
        // 3. Cream paper face.
        .clip(shape)
        .background(BrandTokens.PaperCream)
        // 3b. Paper-grain + debossed inner brass rule, drawn over the cream.
        .drawWithContent {
            drawContent()
            // Faint diagonal paper-grain.
            val step = 13.dp.toPx()
            val grain = BrandTokens.CreamInk.copy(alpha = 0.05f)
            var gx = -size.height
            while (gx < size.width) {
                drawLine(
                    grain,
                    Offset(gx, 0f),
                    Offset(gx + size.height * 0.32f, size.height),
                    strokeWidth = 0.5.dp.toPx(),
                )
                gx += step
            }
            // Debossed inner brass-hairline rule, inset from the paper edge.
            val inset = 4.dp.toPx()
            val rr = (radius.toPx() - inset).coerceAtLeast(2f)
            drawRoundRect(
                color = BrandTokens.BrassDark.copy(alpha = 0.30f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius =
                    androidx.compose.ui.geometry
                        .CornerRadius(rr, rr),
                style = Stroke(width = 0.75.dp.toPx()),
            )
            // Top inner highlight on the rule for the embossed read.
            drawRoundRect(
                color = BrandTokens.PaperCream.copy(alpha = 0.6f),
                topLeft = Offset(inset, inset - 0.75.dp.toPx()),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius =
                    androidx.compose.ui.geometry
                        .CornerRadius(rr, rr),
                style = Stroke(width = 0.5.dp.toPx()),
            )
        }
}

/**
 * Wax-seal accent — a small oxblood wax medallion with a brass rim and an embossed
 * mark, the official stamp on a deco document. Lay it over the popover's top-end
 * corner via Box alignment + a small negative offset.
 *
 * @param mark single glyph embossed into the wax (e.g. a role initial or "✦").
 */
@Composable
fun WaxSeal(
    mark: String = "✦",
    size: Dp = 28.dp,
    color: Color = BrandTokens.StampRed,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .drawBehind {
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    val r = this.size.minDimension / 2f
                    // Soft drop under the wax blob.
                    drawCircle(BrandTokens.TeakInk.copy(alpha = 0.45f), r, c.copy(y = c.y + 1.5.dp.toPx()))
                    // Wax body — domed red wax with a lit top edge.
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                listOf(Color(0xFFE0464B), color, BrandTokens.Oxblood),
                                center = c.copy(y = c.y - r * 0.32f),
                                radius = r * 1.25f,
                            ),
                        radius = r,
                        center = c,
                    )
                    // Irregular wax skirt — three small lobes for a hand-pressed feel.
                    for (i in 0 until 8) {
                        val a = (i / 8f) * 2f * PI.toFloat()
                        val lobeR = r * (0.92f + 0.10f * sin(a * 3f))
                        drawCircle(
                            color.copy(alpha = 0.85f),
                            r * 0.18f,
                            Offset(c.x + lobeR * cos(a), c.y + lobeR * sin(a)),
                        )
                    }
                    // Brass rim.
                    drawCircle(
                        BrandTokens.GoldAntique.copy(alpha = 0.8f),
                        r * 0.86f,
                        c,
                        style = Stroke(0.8.dp.toPx()),
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = mark,
            style = KursiType.label_md.copy(fontSize = (size.value * 0.42f).sp),
            color = BrandTokens.GoldAntique.copy(alpha = 0.92f),
            maxLines = 1,
        )
    }
}

/**
 * Soft warm table vignette: a radial darkening from a warm lit centre to a deeply
 * shadowed rim. Draw behind felt content so the centre medallion reads as lit and
 * the periphery recedes (P3 / P6 value-widening).
 */
fun DrawScope.drawTableVignette(
    centerWarmth: Float = 0.10f,
    rimDarkness: Float = 0.55f,
) {
    val cx = size.width / 2f
    val cy = size.height * 0.46f
    val maxR = maxOf(size.width, size.height) * 0.85f
    // Warm centre lift
    drawRect(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        BrandTokens.GoldAntique.copy(alpha = centerWarmth),
                        Color.Transparent,
                    ),
                center = Offset(cx, cy),
                radius = maxR * 0.55f,
            ),
    )
    // Shadowed rim
    drawRect(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.Transparent,
                        Color.Transparent,
                        BrandTokens.TeakInk.copy(alpha = rimDarkness),
                    ),
                center = Offset(cx, cy),
                radius = maxR,
            ),
    )
}

// ─────────────────────────── Radial guilloché (medallion) ─────────────────────

/**
 * Engine-turned guilloché ring: interfering rosette curves drawn radially, the
 * same security-print language as the certificate cards. Multiplatform-safe Canvas.
 */
private fun DrawScope.drawGuillocheRosette(
    color: Color,
    petals: Int = 36,
    innerFrac: Float = 0.40f,
    outerFrac: Float = 0.92f,
    alpha: Float = 0.22f,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val baseR = minOf(size.width, size.height) / 2f
    val rIn = baseR * innerFrac
    val rOut = baseR * outerFrac
    val c = color.copy(alpha = alpha)
    val sw = 0.8.dp.toPx()
    // Two concentric guide rings
    drawCircle(c, rOut, Offset(cx, cy), style = Stroke(sw))
    drawCircle(c, rIn, Offset(cx, cy), style = Stroke(sw))
    // Rosette: a wave riding the radius, sampled around the circle
    val path = Path()
    val steps = petals * 16
    val amp = (rOut - rIn) * 0.5f
    val mid = (rOut + rIn) / 2f
    var first = true
    for (i in 0..steps) {
        val t = i.toFloat() / steps.toFloat()
        val ang = (t * 2f * PI).toFloat()
        val r = mid + amp * sin(petals * ang)
        val x = cx + r * cos(ang)
        val y = cy + r * sin(ang)
        if (first) {
            path.moveTo(x, y)
            first = false
        } else {
            path.lineTo(x, y)
        }
    }
    drawPath(path, c, style = Stroke(0.7.dp.toPx()))
    // Inner counter-rosette (offset phase) for the interference look
    val path2 = Path()
    first = true
    for (i in 0..steps) {
        val t = i.toFloat() / steps.toFloat()
        val ang = (t * 2f * PI).toFloat()
        val r = mid + amp * 0.6f * sin(petals * ang + PI.toFloat() / 2f)
        val x = cx + r * cos(ang)
        val y = cy + r * sin(ang)
        if (first) {
            path2.moveTo(x, y)
            first = false
        } else {
            path2.lineTo(x, y)
        }
    }
    drawPath(path2, c.copy(alpha = alpha * 0.7f), style = Stroke(0.6.dp.toPx()))
}

// ─────────────────────────── BrassMedallion (P1) ──────────────────────────────

/**
 * The heart of the table — a premium embossed brass medallion with a debossed
 * guilloché well. The deck renders as a tactile card-stack on the left, the
 * treasury as stacked khokha coins on the right. Anchors the whole composition.
 *
 * Multiplatform-safe: radial/sweep brushes + Canvas guilloché + layered shadow.
 */
@Composable
fun BrassMedallion(
    deckCount: Int,
    treasuryCoins: Int,
    turnNumber: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 248.dp,
    /** Long-press = inspect the table heart (current claim / table detail). Null → inert. */
    onLongClick: (() -> Unit)? = null,
) {
    val dPx = diameter.value
    val scale = (diameter / 248.dp).coerceIn(0.6f, 1.2f)
    // DEPTH3D — struck-coin parallax: the medallion breathes on a slow figure-of-eight tilt
    // so the brass catches the light from shifting angles and reads as a domed, struck coin
    // rather than a flat disc. Tiny amplitude keeps it tasteful and never disturbs the tokens.
    val domeTransition = rememberInfiniteTransition(label = "medallionDome")
    val domeTiltX by domeTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "domeTiltX",
    )
    val domeTiltY by domeTransition.animateFloat(
        initialValue = 4f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(tween(6800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "domeTiltY",
    )
    Box(
        modifier =
            modifier
                .then(
                    if (onLongClick != null) {
                        Modifier.inspectable(onClick = {}, onLongClick = onLongClick, pressShape = CircleShape)
                    } else {
                        Modifier
                    },
                ).size(diameter)
                .graphicsLayer {
                    rotationX = domeTiltX
                    rotationY = domeTiltY
                    cameraDistance = 16f * density
                }
                // Deep, crisp contact shadow so the medallion sits proud of the felt like a
                // struck object dropped onto it, not a sticker.
                .shadow(
                    elevation = 28.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.7f),
                    spotColor = BrandTokens.TeakInk,
                    clip = false,
                ).clip(CircleShape)
                // Brass body — struck-metal radial sheen: a hot highlight top-left burning down
                // through brass into a true dark rim (was Gold→Brass→BrassDark→#5E481B, a shallow
                // fall that never left "flat gold disc"; now Cream(hot)→Gold→Brass→BrassDark→
                // TeakInk, tighter radius, so the coin reads as struck metal with real volume).
                .background(
                    Brush.radialGradient(
                        colors =
                            listOf(
                                KursiNeutrals.Cream,
                                BrandTokens.GoldAntique,
                                BrandTokens.BrassAged,
                                BrandTokens.BrassDark,
                                BrandTokens.TeakInk,
                            ),
                        center = Offset(0.36f * dPx, 0.30f * dPx),
                        radius = dPx * 0.80f,
                    ),
                ).brassSpecular()
                // Outer engine-turned bezel + debossed well
                .drawBehind {
                    // Outer raised brass bezel rings
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val rr = minOf(size.width, size.height) / 2f
                    // Directional rim shadow — darkens the lower-right arc opposite the highlight
                    // so the disc reads as lit from one side (a struck object), not an evenly-glowing
                    // flat circle.
                    drawCircle(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, BrandTokens.TeakInk.copy(alpha = 0.45f)),
                            center = Offset(cx * 1.18f, cy * 1.28f),
                            radius = rr * 1.35f,
                        ),
                        rr,
                        Offset(cx, cy),
                    )
                    drawCircle(
                        BrandTokens.GoldAntique.copy(alpha = 0.85f),
                        rr - 2.dp.toPx(),
                        Offset(cx, cy),
                        style = Stroke(2.5.dp.toPx()),
                    )
                    drawCircle(
                        BrandTokens.TeakInk.copy(alpha = 0.5f),
                        rr - 6.dp.toPx(),
                        Offset(cx, cy),
                        style = Stroke(1.5.dp.toPx()),
                    )
                    // Debossed inner well: dark recessed disc with guilloché
                    drawCircle(
                        Brush.radialGradient(
                            listOf(
                                BrandTokens.TeakInk.copy(alpha = 0.45f),
                                BrandTokens.BrassDark.copy(alpha = 0.10f),
                            ),
                            center = Offset(cx, cy),
                            radius = rr * 0.72f,
                        ),
                        rr * 0.72f,
                        Offset(cx, cy),
                    )
                    drawGuillocheRosette(BrandTokens.GoldAntique, petals = 30, alpha = 0.16f)
                    // Inner brass rim of the well
                    drawCircle(
                        BrandTokens.GoldAntique.copy(alpha = 0.5f),
                        rr * 0.72f,
                        Offset(cx, cy),
                        style = Stroke(1.5.dp.toPx()),
                    )
                }.border(
                    2.dp,
                    Brush.sweepGradient(
                        listOf(
                            BrandTokens.GoldAntique,
                            BrandTokens.BrassDark,
                            BrandTokens.GoldAntique,
                            BrandTokens.BrassDark,
                            BrandTokens.GoldAntique,
                        ),
                    ),
                    CircleShape,
                )
                // DEPTH3D — domed specular: a soft hot-spot that slides with the parallax tilt,
                // so the coin's crown reads as a curved, struck surface catching a moving light.
                .drawBehind {
                    val rr = minOf(size.width, size.height) / 2f
                    // The highlight drifts opposite to the tilt — light source feels fixed.
                    val hx = size.width / 2f - domeTiltY / 4f * rr
                    val hy = size.height * 0.30f - domeTiltX / 4f * rr
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                listOf(
                                    BrandTokens.GoldAntique.copy(alpha = 0.42f),
                                    Color.White.copy(alpha = 0.10f),
                                    Color.Transparent,
                                ),
                                center = Offset(hx, hy),
                                radius = rr * 0.62f,
                            ),
                        radius = rr * 0.62f,
                        center = Offset(hx, hy),
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp * scale),
        ) {
            // GADDI mark — the seat of power, struck INTO the brass: a dark drop-shadow under
            // bright gold ink reads as debossed engraving instead of text printed on a flat disc.
            Text(
                text = "GADDI",
                style =
                    KursiType.display
                        .copy(
                            fontSize = (17 * scale).sp,
                            letterSpacing = (3 * scale).sp,
                            shadow = Shadow(BrandTokens.TeakInk.copy(alpha = 0.75f), Offset(0f, 1.5f), 1.5f),
                        ).rozha(),
                color = BrandTokens.GoldAntique.copy(alpha = 0.92f),
            )
            Box(
                modifier =
                    Modifier
                        .width(46.dp * scale)
                        .height(1.dp)
                        .background(BrandTokens.GoldAntique.copy(alpha = 0.4f)),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp * scale),
            ) {
                CardStackToken(count = deckCount)
                CoinStackToken(coins = treasuryCoins)
            }
            Text(
                text = "TURN $turnNumber",
                style =
                    KursiType.label_micro.copy(
                        letterSpacing = (2 * scale).sp,
                        shadow = Shadow(BrandTokens.TeakInk.copy(alpha = 0.7f), Offset(0f, 1f), 1f),
                    ),
                color = BrandTokens.GoldAntique.copy(alpha = 0.65f),
            )
        }
    }
}

/** Tactile card-stack: 3 offset brass card backs, the deck pile on the medallion. */
@Composable
private fun CardStackToken(count: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(width = 52.dp, height = 70.dp),
            contentAlignment = Alignment.Center,
        ) {
            // back-most two cards peek out (offset) to read as a stack
            repeat(2) { i ->
                val off = (2 - i)
                Box(
                    modifier =
                        Modifier
                            .size(width = 44.dp, height = 64.dp)
                            .offset { IntOffset(off * 3, off * -3) }
                            .shadow(4.dp, Squircle(KursiRadii.sm), clip = false)
                            .clip(Squircle(KursiRadii.sm))
                            .background(
                                Brush.verticalGradient(
                                    listOf(BrandTokens.BrassDark, Color(0xFF5E481B)),
                                ),
                            ).border(1.dp, BrandTokens.BrassDark, Squircle(KursiRadii.sm)),
                )
            }
            // top card — engraved chair seal
            Box(
                modifier =
                    Modifier
                        .size(width = 44.dp, height = 64.dp)
                        .shadow(6.dp, Squircle(KursiRadii.sm), clip = false)
                        .clip(Squircle(KursiRadii.sm))
                        .background(
                            Brush.verticalGradient(
                                listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged, BrandTokens.BrassDark),
                            ),
                        ).drawBehind { drawChairEmblem(BrandTokens.TeakDark) }
                        .border(1.2.dp, BrandTokens.GoldAntique, Squircle(KursiRadii.sm)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "K",
                    style = KursiType.display.copy(fontSize = 22.sp),
                    color = BrandTokens.TeakDark.copy(alpha = 0.85f),
                )
            }
        }
        Text(
            text = "DECK $count",
            style = KursiType.label_micro.copy(letterSpacing = 1.sp),
            color = BrandTokens.GoldAntique.copy(alpha = 0.7f),
        )
    }
}

/** Stacked khokha coins: an isometric pile of brass coins for the treasury. */
@Composable
private fun CoinStackToken(coins: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(width = 60.dp, height = 70.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // pile of coin edges, stacked upward
            val coinW = 56.dp
            val coinH = 14.dp
            val layers = 4
            repeat(layers) { i ->
                Box(
                    modifier =
                        Modifier
                            .offset { IntOffset(0, (-(i) * 9.5f * density).toInt()) }
                            .size(width = coinW, height = coinH)
                            .shadow(3.dp, CircleShape, clip = false)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged, BrandTokens.BrassDark),
                                ),
                            ).border(0.8.dp, BrandTokens.BrassDark, CircleShape),
                )
            }
            // crown coin face on top with the count
            Box(
                modifier =
                    Modifier
                        .offset { IntOffset(0, (-(layers) * 9.5f * density).toInt()) }
                        .size(48.dp)
                        .shadow(5.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged, BrandTokens.BrassDark),
                                center = Offset(0.35f * 48f, 0.30f * 48f),
                            ),
                        ).border(
                            1.4.dp,
                            Brush.sweepGradient(
                                listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark, BrandTokens.GoldAntique),
                            ),
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = coins.toString(),
                    style = KursiType.numeric.copy(fontSize = 18.sp),
                    color = BrandTokens.TeakDark,
                )
            }
        }
        Text(
            text = "KHAZANA",
            style = KursiType.label_micro.copy(letterSpacing = 1.sp),
            color = BrandTokens.GoldAntique.copy(alpha = 0.7f),
        )
    }
}
