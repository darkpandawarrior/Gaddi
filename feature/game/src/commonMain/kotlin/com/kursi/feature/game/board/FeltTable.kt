package com.kursi.feature.game.board

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kursi.designsystem.*
import com.kursi.designsystem.moment.reportAnchor
import com.kursi.designsystem.shader.feltMaterial
import com.kursi.engine.*
import com.kursi.feature.game.*
import com.kursi.feature.game.overlays.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Concentric guilloche rings, as fractions of the table radius: four rings starting at 0.48x and
 * widening by 0.16x each. Fewer rings reads as a plain circle, more fills the felt with noise.
 */
private const val GuillocheRingCount = 4
private const val GuillocheInnermostRingFraction = 0.32f
private const val GuillocheRingStepFraction = 0.16f

// ─────────────────────────── Felt Table Surface ───────────────────────────
// The hero surface: dark green felt with gold rim and inner vignette.

@Composable
internal fun FeltTableSurface(
    modifier: Modifier = Modifier,
    /**
     * AAA FOCUS rebuild: the 2.5dp brass sweep border + deep rim shadow read as a "card
     * floating in a void" — a boxed panel, not a table. FOCUS/GUIDED render full-bleed
     * (false): the felt IS the screen, no frame. ANALYST/default keeps the framed hero card.
     */
    bordered: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    // Teak-and-brass council table: warm teak with engraved-line overlay, brass sweep border,
    // a soft table vignette (lit centre → shadowed rim), and a ghosted chair emblem.
    Box(
        modifier =
            modifier
                // Deep table-rim shadow so the felt reads as a recessed playing surface
                // (skipped when full-bleed: a shadow around an edge-to-edge surface just
                // reads as a dark halo, not a lifted panel).
                .then(
                    if (bordered) {
                        Modifier.shadow(
                            26.dp,
                            Squircle(KursiRadii.xxl),
                            clip = false,
                            ambientColor = Color.Black,
                            spotColor = BrandTokens.TeakInk,
                        )
                    } else {
                        Modifier
                    },
                ).clip(if (bordered) Squircle(KursiRadii.xxl) else androidx.compose.ui.graphics.RectangleShape)
                .background(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color(0xFF6A4025), // hot lamplit core — the light source lands here
                                    Color(0xFF4A2C1B), // warm lit centre
                                    BrandTokens.TeakMid,
                                    BrandTokens.TeakDark,
                                    BrandTokens.TeakInk, // deep shadowed rim
                                ),
                            // Lamp hangs above-centre: light pool sits high, rim falls to shadow.
                            // (radius left at the default — it spans the surface; an explicit
                            // value here is treated as raw px and collapses the gradient.)
                        ),
                ).drawBehind {
                    // M4 §2 / graphics overhaul: the felt textures are drawn to actually READ now
                    // (were ghosted at α0.045) — engine-turned cross-hatch, a concentric guilloché
                    // well + chair-in-sunburst emblem, a directional warm key-light pool, then the
                    // value-widening vignette so the rim falls into shadow (lamplit-desk, not tint).
                    drawFeltHatch()
                    drawFeltGuilloche()
                    drawFeltChairEmblem()
                    drawKeyLightPool()
                    drawTableVignette(centerWarmth = 0.22f, rimDarkness = 0.66f)
                }.then(
                    if (bordered) {
                        Modifier.border(
                            width = 2.5.dp,
                            brush =
                                Brush.sweepGradient(
                                    listOf(
                                        BrandTokens.GoldAntique,
                                        BrandTokens.BrassAged,
                                        BrandTokens.BrassDark,
                                        BrandTokens.BrassAged,
                                        BrandTokens.GoldAntique,
                                    ),
                                ),
                            shape = Squircle(KursiRadii.xxl),
                        )
                    } else {
                        Modifier
                    },
                ).feltMaterial(bloomCenterYFraction = 0.30f), // matches drawKeyLightPool's cy above
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ─────────────────────────── Felt textures (M4 §2) ─────────────────────────
// Multiplatform-safe Canvas only (no AGSL / RuntimeShader). These read as the
// engine-turned security-print language of the felt so it stops looking like a flat fill.

/** Concentric guilloché well centred on the table heart — faint brass rings + a rosette wave. */
internal fun DrawScope.drawFeltGuilloche() {
    val cx = size.width / 2f
    val cy = size.height * 0.50f
    val baseR = minOf(size.width, size.height) * 0.48f
    val c = BrandTokens.GoldAntique.copy(alpha = 0.10f)
    // A few concentric rings widening out from the heart.
    for (k in 1..GuillocheRingCount) {
        drawCircle(
            c,
            baseR * (GuillocheInnermostRingFraction + GuillocheRingStepFraction * k),
            Offset(cx, cy),
            style = Stroke(0.8.dp.toPx()),
        )
    }
    // A rosette wave riding the mid radius — the certificate's interference look.
    val petals = 48
    val steps = petals * 12
    val rOut = baseR * 0.88f
    val rIn = baseR * 0.40f
    val amp = (rOut - rIn) * 0.5f
    val mid = (rOut + rIn) / 2f
    val path = Path()
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
    drawPath(path, BrandTokens.GoldAntique.copy(alpha = 0.085f), style = Stroke(0.7.dp.toPx()))
}

/**
 * Engine-turned cross-hatch over the whole felt — the security-print substrate. Two faint gold
 * grids (vertical + horizontal) so the teak reads as engraved ledger-stock, not a flat fill.
 */
internal fun DrawScope.drawFeltHatch() {
    val step = 22.dp.toPx()
    val c = BrandTokens.GoldAntique.copy(alpha = 0.05f)
    val sw = 0.5.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(c, Offset(x, 0f), Offset(x, size.height), strokeWidth = sw)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(c, Offset(0f, y), Offset(size.width, y), strokeWidth = sw)
        y += step
    }
}

/**
 * Directional warm key-light pool — a bright, tight radial high-centre where the hanging lamp
 * lands on the felt, giving the surface a real lit hotspot above the medallion instead of a flat
 * tint. Additive-warm, fading to nothing well before the rim (the vignette owns the rim).
 */
private const val KEY_LIGHT_GLOW_ALPHA = 0.14f
private const val KEY_LIGHT_MID_ALPHA = 0.05f
private val KeyLightGlowColor = Color(0xFFFFE0A0)
private val KeyLightMidColor = Color(0xFFE8C874)

internal fun DrawScope.drawKeyLightPool() {
    val cx = size.width / 2f
    val cy = size.height * 0.30f // lamp lands high-centre
    val r = maxOf(size.width, size.height) * 0.55f
    drawRect(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        KeyLightGlowColor.copy(alpha = KEY_LIGHT_GLOW_ALPHA), // warm lamp glow
                        KeyLightMidColor.copy(alpha = KEY_LIGHT_MID_ALPHA),
                        Color.Transparent,
                    ),
                center = Offset(cx, cy),
                radius = r,
            ),
    )
}

/** Ghosted chair-in-sunburst emblem at the felt centre — the brand mark embedded in the table. */
internal fun DrawScope.drawFeltChairEmblem() {
    val cx = size.width / 2f
    val cy = size.height * 0.50f
    val r = minOf(size.width, size.height) * 0.20f
    val c = BrandTokens.GoldAntique.copy(alpha = 0.09f)
    // Sunburst rays.
    val rays = 24
    for (i in 0 until rays) {
        val ang = (i.toFloat() / rays * 2f * PI).toFloat()
        val inner = r * 1.15f
        val outer = r * 1.7f
        drawLine(
            c,
            Offset(cx + inner * cos(ang), cy + inner * sin(ang)),
            Offset(cx + outer * cos(ang), cy + outer * sin(ang)),
            strokeWidth = 1.2.dp.toPx(),
        )
    }
    // A simple chair silhouette — back + seat + legs — drawn as strokes.
    val w = r * 0.7f
    val h = r * 1.1f
    val sw = 2.dp.toPx()
    // back uprights
    drawLine(c, Offset(cx - w / 2, cy - h / 2), Offset(cx - w / 2, cy + h / 2), strokeWidth = sw)
    drawLine(c, Offset(cx + w / 2, cy - h / 2), Offset(cx + w / 2, cy + h / 2), strokeWidth = sw)
    // seat
    drawLine(c, Offset(cx - w / 2, cy), Offset(cx + w / 2, cy), strokeWidth = sw)
    // top rail
    drawLine(c, Offset(cx - w / 2, cy - h / 2), Offset(cx + w / 2, cy - h / 2), strokeWidth = sw)
}

/**
 * Radial pedestal glow drawn behind the table heart so the medallion reads as ROOTED in the
 * lower felt instead of floating — this reclaims the formerly-dead bottom void. Warm at the
 * heart, fading to nothing before the rim.
 */
internal fun DrawScope.drawHeartPedestal() {
    val cx = size.width / 2f
    val cy = size.height * 0.52f
    val maxR = maxOf(size.width, size.height) * 0.7f
    drawRect(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        BrandTokens.GoldAntique.copy(alpha = 0.10f),
                        BrandTokens.BrassAged.copy(alpha = 0.04f),
                        Color.Transparent,
                    ),
                center = Offset(cx, cy),
                radius = maxR * 0.6f,
            ),
    )
}

// ─────────────────────────── Felt Center Tokens ───────────────────────────
// Deck token + treasury coin token, centered on the felt.
// During a reaction, show the claimed role spotlight here instead.

@Composable
internal fun FeltCenterTokens(
    state: GameUiState,
    gamePhase: GamePhase,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
) {
    // Challenge flip animation
    val challengeRevealEvents = state.recentEvents.filterIsInstance<GameEvent.ChallengeRevealed>()
    var lastSeenRevealCount by remember { mutableStateOf(0) }
    val flipAngle = remember { Animatable(0f) }
    var challengeResult by remember { mutableStateOf<GameEvent.ChallengeRevealed?>(null) }
    val reducedMotion = LocalReducedMotion.current

    LaunchedEffect(challengeRevealEvents.size) {
        if (challengeRevealEvents.size > lastSeenRevealCount) {
            lastSeenRevealCount = challengeRevealEvents.size
            challengeResult = challengeRevealEvents.last()
            if (reducedMotion) {
                flipAngle.snapTo(180f)
            } else {
                flipAngle.snapTo(0f)
                // Spring-physics reveal (spec §7 juice) — a card landing face-up, not a linear rotate.
                flipAngle.animateTo(targetValue = 180f, animationSpec = KursiMotion.settle())
            }
        }
    }

    when (gamePhase) {
        is GamePhase.ReactionWindow -> {
            // Pending-claim spotlight — replaces deck/treasury
            ReactionSpotlight(gamePhase = gamePhase, state = state)
        }
        is GamePhase.GameOver -> {
            WinnerBanner(gamePhase = gamePhase, state = state)
        }
        else -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── P1: the heart of the table — premium embossed brass medallion ──
                // Scales down as the table fills so it stays the focal heart without
                // crowding the plates at 10p, and reads generous at 2p.
                val seatCount = state.view.players.size
                // M4 §2: the medallion is the table HEART — it now claims more presence so the
                // felt no longer compresses into the top 60% with a dead bottom void. Scaled up
                // across the board, still tapering at 10p so the plates above stay uncrowded.
                val baseMedallionD =
                    when {
                        seatCount <= 3 -> 320.dp
                        seatCount <= 5 -> 288.dp
                        seatCount <= 7 -> 256.dp
                        else -> 224.dp
                    }
                // AAA FOCUS rebuild: at full ANALYST size the medallion reads as a flat gold disc
                // dominating the upper-middle of the felt. FOCUS/GUIDED shrink it ~35% so the
                // (now richer/deeper-struck) pot stays the focal heart without crowding the seats.
                val medallionD = if (state.densityLayer == DensityLayer.ANALYST) baseMedallionD else baseMedallionD * 0.65f
                // Long-press the table heart → current-claim detail (or table state) chit.
                var medallionBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                val liveClaim = (state.view.phase as? PhaseView.Reactions)?.claimedRole
                val liveActor =
                    (state.view.phase as? PhaseView.Reactions)
                        ?.actor
                        ?.let { personaNameOrDefault(it, state) }
                BrassMedallion(
                    deckCount = state.view.deckCount,
                    treasuryCoins = state.view.treasury,
                    turnNumber = state.view.turnNumber,
                    diameter = medallionD,
                    modifier =
                        Modifier
                            .onGloballyPositioned { medallionBounds = it.boundsInRoot() }
                            // M4 §1: the table heart reports its center as the treasury anchor.
                            .reportAnchor(com.kursi.designsystem.moment.AnchorKey.Treasury),
                    onLongClick = {
                        onShowChit(
                            ChitContent.ClaimDetail(
                                claimedRole = liveClaim,
                                actorName = liveActor,
                                deckCount = state.view.deckCount,
                                treasuryCoins = state.view.treasury,
                                turnNumber = state.view.turnNumber,
                            ),
                            medallionBounds,
                        )
                    },
                )

                // AAA FOCUS rebuild: the "what's happening" beat lives here now — an italic
                // engraved caption anchored directly under the pot — instead of a bar in the
                // top chrome (see GameScreen's density-gated top-of-screen slot for ANALYST).
                if (state.densityLayer != DensityLayer.ANALYST) {
                    BeatHeadline(state = state)
                }

                // Challenge flip if recent
                val result = challengeResult
                if (result != null && flipAngle.value > 0f) {
                    val showFront = flipAngle.value <= 90f
                    val flashColor = if (result.hadRole) Color(0xFF1565C0) else Color(0xFFC62828)

                    // Outer container — rotates the whole card
                    Box(
                        modifier =
                            Modifier
                                .size(width = 140.dp, height = 196.dp)
                                .graphicsLayer {
                                    rotationY = flipAngle.value
                                    cameraDistance = 12f * density // prevents perspective crush
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showFront) {
                            // Front face — question mark, visible 0→90°
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .clip(Squircle(KursiDimens.r_md))
                                        .background(flashColor.copy(alpha = 0.2f))
                                        .border(
                                            KursiDimens.stroke_ring_idle,
                                            BrandTokens.BrassAged.copy(alpha = 0.6f),
                                            Squircle(KursiDimens.r_md),
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("?", style = KursiType.display.rozha(), color = KursiNeutrals.TextPrimary)
                            }
                        } else {
                            // Back face — counter-rotate 180° so text reads left-to-right
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { rotationY = 180f } // THE FIX
                                        .clip(Squircle(KursiDimens.r_md))
                                        .background(flashColor.copy(alpha = 0.2f))
                                        .border(
                                            KursiDimens.stroke_ring_idle,
                                            BrandTokens.BrassAged.copy(alpha = 0.6f),
                                            Squircle(KursiDimens.r_md),
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(KursiDimens.space_sm),
                                ) {
                                    RoleCard(role = result.role, size = CardSize.Medium)
                                    Text(
                                        text = if (result.hadRole) "✓ TRUE" else "✗ BLUFF",
                                        style = KursiType.title_sm.rozha(),
                                        color = if (result.hadRole) KursiSemantics.Success else KursiSemantics.Danger,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
