package com.kursi.feature.game.overlays

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.BrandTokens
import com.kursi.designsystem.GaddiDimens
import com.kursi.designsystem.GaddiRadii
import com.kursi.designsystem.GaddiType
import com.kursi.designsystem.Squircle
import com.kursi.feature.game.LocalGaddiVoice

/**
 * BEAT GATE tap-to-continue affordance (spec §5) — rendered whenever [com.kursi.feature.game.PendingBeat]
 * is held (FOCUS/GUIDED only; ANALYST never sets it, see [com.kursi.feature.game.GameUiState.pendingBeat]).
 * Tap, click, or Space (desktop) advances past the held beat. The pulse collapses to a static glow
 * under [reducedMotion] (spec §10) — nothing here is time-critical, so a player who never notices
 * the pulse loses nothing but has to look for the prompt.
 */
@Composable
internal fun ContinueBeatPrompt(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    val voice = LocalGaddiVoice.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val pulse: Float =
        if (reducedMotion) {
            0.85f
        } else {
            val transition = rememberInfiniteTransition(label = "continuePulse")
            val animated by transition.animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "continuePulseAlpha",
            )
            animated
        }

    Row(
        modifier =
            modifier
                .wrapContentWidth()
                .clip(Squircle(GaddiRadii.md))
                .background(BrandTokens.TeakDark.copy(alpha = 0.92f))
                .border(1.5.dp, BrandTokens.GoldAntique.copy(alpha = pulse), Squircle(GaddiRadii.md))
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Spacebar) {
                        onContinue()
                        true
                    } else {
                        false
                    }
                }.focusRequester(focusRequester)
                .focusable()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onContinue,
                ).semantics(mergeDescendants = true) {
                    role = Role.Button
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = voice.continuePrompt
                }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_sm),
    ) {
        Text(
            text = voice.continuePrompt,
            style = GaddiType.label_sm.copy(letterSpacing = 1.sp),
            color = BrandTokens.GoldAntique.copy(alpha = pulse),
        )
    }
}
