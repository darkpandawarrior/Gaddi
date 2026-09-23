package com.kursi.feature.game.overlays

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.engine.*
import com.kursi.feature.game.*

// ─────────────────────────── Handoff guard (M5 pass-and-play) ───────────────────────────

/**
 * Full-screen "pass the device" guard shown between two different hot-seat human players so a
 * player never glimpses another's hand. Occludes the entire table; the next player taps to reveal.
 */
@Composable
internal fun HandoffGuard(
    nextSeatName: String,
    onReady: () -> Unit,
) {
    val voice = LocalGaddiVoice.current
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BrandTokens.TeakInk)
                .clickable(
                    indication = null,
                    interactionSource =
                        remember {
                            androidx.compose.foundation.interaction
                                .MutableInteractionSource()
                        },
                    onClick = onReady,
                ).semantics {
                    contentDescription = "${voice.handoffTitle}. ${voice.handoffPrompt(nextSeatName)} ${voice.handoffReveal}"
                    liveRegion = LiveRegionMode.Assertive
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.widthIn(max = 520.dp).padding(32.dp),
        ) {
            Text(
                text = voice.handoffTitle,
                style = GaddiType.display.copy(fontSize = 22.sp, letterSpacing = 3.sp).rozha(),
                color = BrandTokens.GoldAntique,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(BrandTokens.TeakDark)
                        .border(1.5.dp, BrandTokens.BrassAged.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                Text(
                    text = voice.handoffPrompt(nextSeatName),
                    style = GaddiType.title.copy(fontSize = 18.sp),
                    color = GaddiNeutrals.TextPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = voice.handoffSecrecy,
                style = GaddiType.caption.copy(fontSize = 12.sp),
                color = GaddiNeutrals.TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandTokens.BrassAged.copy(alpha = 0.18f))
                        .border(1.dp, BrandTokens.GoldAntique.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onReady)
                        .padding(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Text(
                    text = voice.handoffReveal,
                    style = GaddiType.name.copy(fontSize = 15.sp, letterSpacing = 1.sp),
                    color = BrandTokens.GoldAntique,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
