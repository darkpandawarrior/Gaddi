package com.kursi.designsystem

// ═══════════════════════════════════════════════════════════════════════════════
// Chat bubble components — License Raj Deco identity.
//
// Implements the three public contracts required by the narrative layer:
//   SpeechBubble   — persona-styled cream-paper chat bubble
//   ChatAvatar     — small circular brass-rimmed crest
//   speakingSeatGlow — @Composable Modifier extension for the animated holo rim
//
// Multiplatform-safe: pure Compose Canvas + gradients, no AGSL / java.* .
// ═══════════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────── ChatAvatar ──────────────────────────────────────

/**
 * Small circular brass-rimmed crest for chat participants.
 *
 * A sibling of [SeatAvatar]: [size] circular, [color]-filled, with a radial
 * brass gradient bezel and the [monogram] centred in cream. Used as the leading
 * (or trailing, for the human player) crest inside [SpeechBubble].
 *
 * Contract: ChatAvatar(monogram:String, color:Color, modifier:Modifier=Modifier, size:Dp=28.dp)
 */
@Composable
fun ChatAvatar(
    monogram: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    brush =
                        Brush.radialGradient(
                            // Brass highlight → seat color → brass shadow: identical layering to SeatAvatar
                            listOf(
                                BrandTokens.GoldAntique.copy(alpha = 0.55f),
                                color,
                                BrandTokens.BrassDark.copy(alpha = 0.75f),
                            ),
                        ),
                ).border(
                    width = 1.dp,
                    brush =
                        Brush.radialGradient(
                            listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark),
                        ),
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = monogram.take(2).uppercase(),
            style = GaddiType.label_micro.copy(fontSize = (size.value * 0.38f).sp),
            color = BrandTokens.PaperCream,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

// ─────────────────────────── SpeechBubble ────────────────────────────────────

/**
 * Persona-styled chat bubble rendered in the License Raj Deco language.
 *
 * Layout:
 *   [fromPlayer]=false (opponent / narrator):
 *     [ChatAvatar] leading | bubble body (left-aligned, cream paper + accent rail)
 *   [fromPlayer]=true (human):
 *     bubble body (right-aligned, warmer gold fill) | [ChatAvatar] trailing
 *
 * The bubble uses [Modifier.decoPopoverPaper] for the aged-brass-framed cream face.
 * The left accent rail (or gold fill for the player) signals speaker identity via
 * the seat [accent] color — same visual grammar as [OpponentPlate]'s ring.
 *
 * [emphatic]=true (HOSTILE / BOAST tone) applies [Modifier.holoRimLight] for a
 * stronger brass rim that pulses once (static phase=0.25f — no separate transition
 * needed; the glow itself is the signal).
 *
 * Contract: SpeechBubble(speakerName:String, text:String, accent:Color,
 *   fromPlayer:Boolean, modifier:Modifier=Modifier, monogram:String?=null,
 *   emphatic:Boolean=false)
 */
@Composable
fun SpeechBubble(
    speakerName: String,
    text: String,
    accent: androidx.compose.ui.graphics.Color,
    fromPlayer: Boolean,
    modifier: Modifier = Modifier,
    monogram: String? = null,
    emphatic: Boolean = false,
) {
    // Player bubbles use a warm gold-tinted fill; opponent bubbles use cream paper.
    // Both route through decoPopoverPaper so the brass frame is consistent — the
    // player side simply has a stronger gold tint beneath the cream.
    val displayName = if (fromPlayer) "Aap" else speakerName
    val avatarColor = if (fromPlayer) BrandTokens.GoldAntique else accent
    val avatarMonogram = monogram ?: displayName.filter { it.isLetter() }.take(2).uppercase()

    // Emphatic bubbles (HOSTILE / BOAST) get a static holo rim at moderate intensity —
    // enough to read as agitated / boastful without the full animated pulse.
    val emphaticMod =
        if (emphatic) {
            Modifier.holoRimLight(
                accent = accent,
                phase = 0.25f,
                cornerRadius = GaddiRadii.md,
                intensity = 0.65f,
            )
        } else {
            Modifier
        }

    Row(
        modifier = modifier,
        horizontalArrangement = if (fromPlayer) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        // Leading avatar for opponent / narrator messages
        if (!fromPlayer) {
            ChatAvatar(
                monogram = avatarMonogram,
                color = avatarColor,
                size = 28.dp,
            )
            Spacer(Modifier.width(6.dp))
        }

        // Bubble body
        Box(
            modifier =
                Modifier
                    .widthIn(min = 80.dp, max = 300.dp)
                    .then(emphaticMod)
                    .decoPopoverPaper(radius = GaddiRadii.md)
                    .then(
                        // Player bubble: warm gold wash behind the cream paper
                        if (fromPlayer) {
                            Modifier.background(
                                Brush.horizontalGradient(
                                    listOf(
                                        accent.copy(alpha = 0.08f),
                                        BrandTokens.GoldAntique.copy(alpha = 0.10f),
                                    ),
                                ),
                            )
                        } else {
                            Modifier
                        },
                    ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                // Speaker name line — small Rozha / caps, tinted accent
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (fromPlayer) Arrangement.End else Arrangement.Start,
                ) {
                    if (!fromPlayer) {
                        // Accent rail: a tiny 3dp wide dot/stripe of the seat hue
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 3.dp, height = 10.dp)
                                    .clip(Squircle(GaddiRadii.xs))
                                    .background(accent.copy(alpha = 0.85f)),
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = displayName,
                        style =
                            GaddiType.label_micro
                                .copy(
                                    fontSize = 9.sp,
                                    letterSpacing = 0.5.sp,
                                ).rozha(),
                        color = if (fromPlayer) BrandTokens.GoldAntique else accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(3.dp))
                // Body text — DM Mono body style for the sarkari-teleprinter register
                Text(
                    text = text,
                    style =
                        GaddiType.body
                            .copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 0.1.sp,
                            ).dmMono(),
                    color = BrandTokens.CreamInk,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Trailing avatar for the human player's own messages
        if (fromPlayer) {
            Spacer(Modifier.width(6.dp))
            ChatAvatar(
                monogram = avatarMonogram,
                color = avatarColor,
                size = 28.dp,
            )
        }
    }
}

// ─────────────────────────── speakingSeatGlow ────────────────────────────────

/**
 * @Composable Modifier extension that, when [active], drives an infinite phase
 * 0..1 (tween ~2200ms, LinearEasing, RepeatMode.Restart) and applies
 * [Modifier.holoRimLight] so the speaking opponent's plate gets a live glowing rim.
 * When !active it returns the receiver unchanged — no animation is started.
 *
 * Contract: fun Modifier.speakingSeatGlow(accent:Color, active:Boolean,
 *   cornerRadius:Dp=18.dp): Modifier
 */
@Composable
fun Modifier.speakingSeatGlow(
    accent: androidx.compose.ui.graphics.Color,
    active: Boolean,
    cornerRadius: Dp = 18.dp,
): Modifier {
    if (!active) return this

    val transition = rememberInfiniteTransition(label = "speakingSeatGlow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "speakingSeatGlowPhase",
    )
    return this.holoRimLight(
        accent = accent,
        phase = phase,
        cornerRadius = cornerRadius,
        intensity = 0.75f,
    )
}
