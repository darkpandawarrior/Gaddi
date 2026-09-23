package com.kursi.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.engine.Role

// ─────────────────────────── OpponentSeatToken (AAA FOCUS rebuild) ────────────

/**
 * FOCUS/GUIDED opponent seat — a brass-rimmed circular portrait token resting directly on the
 * felt, no plate/box. Replaces [OpponentPlate]'s enamel nameplate for the low-density boards
 * (spec: "dissolve all boxes ... elements rest on one continuous lit felt with cast shadows,
 * never borders"). Same state contract (claim/pips/target ring) as [OpponentPlate] so callers can
 * switch leaf visuals without touching their click/data wiring.
 */
@Composable
fun OpponentSeatToken(
    name: String,
    seatColor: Color,
    roleColor: Color?, // null if unknown (face-down)
    role: Role?,
    // influenceAlive is deliberately absent: the pip row renders from influenceLost and the slot
    // total, and the spoken "N influence remaining" comes from opponentPlateSemantics.
    influenceLost: Int,
    claim: String?, // LIVE/pending claim, or null
    state: ChipState,
    modifier: Modifier = Modifier,
    claimSummary: String? = null,
    claimCaught: Boolean = false,
    claimProven: Boolean = false,
    /** The centre seat in an arc nudges up so the row reads as a gentle curve, not a flat line. */
    nudgedUp: Boolean = false,
    tokenSize: Dp = 56.dp,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    val brassColor = BrandTokens.BrassAged
    val brassDim = BrandTokens.BrassDark
    val alertRed = Color(0xFF8E2B22)
    val verdigris = Color(0xFF3F6B5E)
    val eliminated = state == ChipState.Eliminated
    val acting = state == ChipState.Acting

    val pulseAlpha by if (acting) {
        rememberInfiniteTransition(label = "seatPulse").animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "seatPulseAlpha",
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    val ringColor: Color =
        when (state) {
            ChipState.Acting -> brassColor.copy(alpha = pulseAlpha)
            ChipState.Responding -> verdigris
            ChipState.ValidTarget -> alertRed
            ChipState.Eliminated -> brassDim.copy(alpha = 0.4f)
            ChipState.Idle -> Color(0xFFFFF0C4).copy(alpha = 0.5f)
        }
    val targetScale =
        if (acting) {
            1.08f
        } else if (state == ChipState.ValidTarget) {
            1.05f
        } else {
            1f
        }
    val animScale by animateFloatAsState(targetScale, tween(220), label = "seatScale")
    val liftY by animateFloatAsState(
        targetValue =
            if (acting) {
                -4f
            } else if (state == ChipState.Responding) {
                -2f
            } else {
                0f
            },
        animationSpec = tween(240),
        label = "seatLiftY",
    )
    val fill = roleColor ?: seatColor

    Column(
        modifier = modifier.widthIn(max = 136.dp).offset(y = if (nudgedUp) (-8).dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SeatTokenCircle(
            name = name,
            fill = fill,
            role = role,
            eliminated = eliminated,
            ringColor = ringColor,
            acting = acting,
            animScale = animScale,
            liftY = liftY,
            tokenSize = tokenSize,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        AutoSizeText(
            text = name,
            style = GaddiType.label_md,
            color = GaddiNeutrals.TextPrimary.copy(alpha = if (eliminated) 0.45f else 1f),
            maxLines = 1,
            minSize = 9.sp,
            modifier = Modifier.widthIn(max = 136.dp),
        )
        if (!eliminated) {
            SeatTokenFooter(
                influenceLost = influenceLost,
                claim = claim,
                claimSummary = claimSummary,
                claimCaught = claimCaught,
                claimProven = claimProven,
                brassColor = brassColor,
                brassDim = brassDim,
            )
        }
    }
}

@Composable
private fun SeatTokenCircle(
    name: String,
    fill: Color,
    role: Role?,
    eliminated: Boolean,
    ringColor: Color,
    acting: Boolean,
    animScale: Float,
    liftY: Float,
    tokenSize: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val monogram = name.filter { it.isLetter() }.take(1).uppercase()
    Box(
        modifier =
            Modifier
                .graphicsLayer { translationY = liftY * density }
                .scale(animScale)
                .size(tokenSize)
                .alpha(if (eliminated) 0.4f else 1f)
                // Real cast shadow — the token's only depth cue, never a border-box.
                .shadow(if (acting) 14.dp else 8.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(fill.copy(alpha = 0.95f).lighten(), fill, fill.darken()),
                        center = Offset(0.38f * tokenSize.value, 0.30f * tokenSize.value),
                        radius = tokenSize.value * 0.95f,
                    ),
                ).border(2.dp, ringColor, CircleShape)
                .drawBehind {
                    // Inner highlight arc (top-left) + inner shadow (bottom-right) — a
                    // struck-coin curvature so the token reads domed, not a flat sticker.
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(size.width * 0.34f, size.height * 0.28f),
                            radius = size.minDimension * 0.55f,
                        ),
                    )
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)),
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = size.minDimension * 0.75f,
                        ),
                    )
                }.inspectable(onClick = onClick, onLongClick = onLongClick, enabled = !eliminated, pressShape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (eliminated) {
            Text("✕", style = GaddiType.title_sm, color = GaddiNeutrals.Cream)
        } else if (role != null) {
            RoleGlyph(role = role, tint = GaddiNeutrals.Cream, deboss = false, modifier = Modifier.size(tokenSize * 0.42f))
        } else {
            Text(monogram, style = GaddiType.title_sm.rozha(), color = Color(0xFF120C06))
        }
    }
}

/** Influence pips + the standing-claim pill — the seat's secondary-info footer. */
@Composable
private fun SeatTokenFooter(
    influenceLost: Int,
    claim: String?,
    claimSummary: String?,
    claimCaught: Boolean,
    claimProven: Boolean,
    brassColor: Color,
    brassDim: Color,
) {
    // Influence pips — brass beads, no slot/box.
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val totalSlots = 2
        val lostCount = influenceLost.coerceAtMost(totalSlots)
        val aliveCount = (totalSlots - lostCount).coerceAtLeast(0)
        repeat(aliveCount) {
            Box(
                modifier =
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(BrandTokens.GoldAntique, brassDim)))
                        .shadow(1.dp, CircleShape, clip = false),
            )
        }
        repeat(lostCount) {
            Box(
                modifier = Modifier.size(7.dp).clip(CircleShape).border(1.dp, brassDim.copy(alpha = 0.5f), CircleShape),
            )
        }
    }
    // Claim — a subtle inset pill (hairline only, never a filled panel).
    val standingText = claim ?: claimSummary
    if (standingText != null) {
        val suffix =
            if (claimProven) {
                " ✓"
            } else if (claimCaught) {
                " ✗"
            } else {
                ""
            }
        val borderColor = (if (claimCaught) Color(0xFFC1272D) else brassColor).copy(alpha = if (claimCaught) 0.5f else 0.32f)
        Text(
            text = standingText + suffix,
            style = GaddiType.label_micro,
            color = if (claimCaught) Color(0xFFF0C0B8) else GaddiNeutrals.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .widthIn(max = 132.dp)
                    .clip(Squircle(9.dp))
                    .background(Color(0xFF140C06).copy(alpha = 0.5f))
                    .border(0.75.dp, borderColor, Squircle(9.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            textAlign = TextAlign.Center,
        )
    }
}

private fun Color.lighten(amount: Float = 0.18f): Color =
    Color(
        red = (red + (1f - red) * amount).coerceIn(0f, 1f),
        green = (green + (1f - green) * amount).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * amount).coerceIn(0f, 1f),
        alpha = alpha,
    )

private fun Color.darken(amount: Float = 0.35f): Color =
    Color(
        red = (red * (1f - amount)).coerceIn(0f, 1f),
        green = (green * (1f - amount)).coerceIn(0f, 1f),
        blue = (blue * (1f - amount)).coerceIn(0f, 1f),
        alpha = alpha,
    )
