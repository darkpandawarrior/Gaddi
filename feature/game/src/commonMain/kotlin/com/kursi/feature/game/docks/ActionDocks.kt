package com.kursi.feature.game.docks

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.engine.*
import com.kursi.feature.game.*
import com.kursi.feature.game.coach.*
import com.kursi.feature.game.overlays.*
import com.kursi.feature.game.status.*
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.game_cancel
import kursi.core.designsystem.generated.resources.game_declare
import kursi.core.designsystem.generated.resources.game_recommended_target
import kursi.core.designsystem.generated.resources.game_section_attack
import kursi.core.designsystem.generated.resources.game_section_earn
import kursi.core.designsystem.generated.resources.game_section_special
import org.jetbrains.compose.resources.stringResource

/** A small label row used in the compact mobile action dock to separate grouped sections. */
@Composable
internal fun ActionSectionLabel(
    label: String,
    color: Color,
) {
    Text(
        text = label.uppercase(),
        style =
            GaddiType.label_micro.copy(
                letterSpacing = 1.2.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            ),
        color = color.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PickActionDock(
    state: GameUiState,
    humanSeat: PlayerId,
    onLocalPhase: (GamePhase?) -> Unit,
    onAction: (GameAction) -> Unit,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    compact: Boolean = false,
) {
    // AAA FOCUS rebuild: FOCUS/GUIDED chips carry no consequence subtext (mockup: bare stamp
    // chips — "hierarchy: secondary info recedes"), independent of [compact] (which selects the
    // grouped-section MOBILE layout vs the flat desktop FlowRow, not the subtext).
    val showConsequence = !compact && state.densityLayer == DensityLayer.ANALYST
    // Build a RiskAction inspect chit for [action] and route it through onShowChit with
    // the chip's captured [bounds]. Tap still declares; long-press reads the catch.
    val showRiskChit: (Action, androidx.compose.ui.geometry.Rect?) -> Unit = { action, bounds ->
        onShowChit(
            ChitContent.RiskAction(
                action = action,
                myCoins = state.view.myCoins,
                bluffConf = riskBluffConf(action, state),
            ),
            bounds,
        )
    }
    // Long-press on a coached chip opens the richer DECISION-COACH chit instead.
    val showCoachChit: (com.kursi.ai.advisor.MoveAdvice, androidx.compose.ui.geometry.Rect?) -> Unit = { advice, bounds ->
        onShowChit(coachChitOf(advice), bounds)
    }
    // When COACH is OFF (or in FOCUS, spec §3) suppress all proactive advice — chips revert to
    // plain family-color styling.
    val coachActive = state.coachGuidanceVisible
    val myCoins = state.view.myCoins
    val mustCoup = myCoins >= 10
    val opponents = state.view.players.filter { it.id != state.view.viewer && !it.eliminated }
    val hasStealTarget =
        state.legalIntents
            .filterIsInstance<Intent.DeclareAction>()
            .any { it.action is Action.Steal }
    // JAANCH (Investigate) only exists on big tables where PATRAKAAR is in the deck — surface the
    // chip only when the engine actually offers a legal Investigate intent.
    val hasInvestigateTarget =
        state.legalIntents
            .filterIsInstance<Intent.DeclareAction>()
            .any { it.action is Action.Investigate }
    val hasAssassinateTarget = opponents.isNotEmpty()
    val hasCoupTarget = opponents.isNotEmpty()

    val brassColor = BrandTokens.BrassAged
    val disruptColor = Color(0xFF8E2B22) // alert_red
    val defendColor = Color(0xFF3F6B5E) // verdigris

    if (mustCoup) {
        // ── Forced coup — single chip, no other options shown ──────────────
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CompactActionChip(
                icon = "💥",
                name = "KHELA",
                cost = "MAJBOORI",
                familyColor = disruptColor,
                enabled = hasCoupTarget,
                forced = true,
                action = Action.Coup(PlayerId(0)),
                onInspect = showRiskChit,
                advice = if (coachActive) adviceForActionChip(state, Action.Coup(PlayerId(0))) else null,
                onShowCoach = showCoachChit,
                showConsequence = showConsequence,
                onClick = { onLocalPhase(GamePhase.PickTarget(Action.Coup(PlayerId(0)))) },
            )
        }
    } else if (compact) {
        // ── MOBILE: grouped layout with section labels ───────────────────────
        // Three clear categories eliminate option paralysis. Each section is a
        // small labelled block; chips never wrap unexpectedly within a section.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ① EARN — no role claim, always available
            // Row with equal weights: each chip fills exactly 1/3 of available width so
            // names and costs never overflow or wrap unevenly across different phone sizes.
            ActionSectionLabel(label = stringResource(Res.string.game_section_earn), color = brassColor)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    CompactActionChip(
                        icon = "🪙",
                        name = "DEHAADI",
                        cost = "+1",
                        familyColor = brassColor,
                        enabled = true,
                        forced = false,
                        action = Action.Income,
                        onInspect = showRiskChit,
                        advice = if (coachActive) adviceForActionChip(state, Action.Income) else null,
                        onShowCoach = showCoachChit,
                        showConsequence = false,
                        onClick = { onAction(GameAction.Submit(Intent.DeclareAction(humanSeat, Action.Income))) },
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    CompactActionChip(
                        icon = "💵",
                        name = "FDI",
                        cost = "+2",
                        familyColor = brassColor,
                        enabled = true,
                        forced = false,
                        action = Action.ForeignAid,
                        onInspect = showRiskChit,
                        advice = if (coachActive) adviceForActionChip(state, Action.ForeignAid) else null,
                        onShowCoach = showCoachChit,
                        showConsequence = false,
                        onClick = { onAction(GameAction.Submit(Intent.DeclareAction(humanSeat, Action.ForeignAid))) },
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    CompactActionChip(
                        icon = "💰",
                        name = "GHOTALA",
                        cost = "+3",
                        familyColor = brassColor,
                        enabled = true,
                        forced = false,
                        action = Action.Tax,
                        onInspect = showRiskChit,
                        advice = if (coachActive) adviceForActionChip(state, Action.Tax) else null,
                        onShowCoach = showCoachChit,
                        showConsequence = false,
                        onClick = { onAction(GameAction.Submit(Intent.DeclareAction(humanSeat, Action.Tax))) },
                    )
                }
            }

            // ② ATTACK — costs coins, targets opponents
            ActionSectionLabel(label = stringResource(Res.string.game_section_attack), color = disruptColor)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    CompactActionChip(
                        icon = "🔪",
                        name = "SUPARI",
                        cost = "−3",
                        familyColor = disruptColor,
                        enabled = myCoins >= 3 && hasAssassinateTarget,
                        forced = false,
                        action = Action.Assassinate(PlayerId(0)),
                        onInspect = showRiskChit,
                        advice = if (coachActive) adviceForActionChip(state, Action.Assassinate(PlayerId(0))) else null,
                        onShowCoach = showCoachChit,
                        showConsequence = false,
                        onClick = { onLocalPhase(GamePhase.PickTarget(Action.Assassinate(PlayerId(0)))) },
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    CompactActionChip(
                        icon = "💥",
                        name = "KHELA",
                        cost = if (myCoins >= 7) "−7" else "7+",
                        familyColor = disruptColor,
                        enabled = myCoins >= 7 && hasCoupTarget,
                        forced = myCoins >= 7,
                        action = Action.Coup(PlayerId(0)),
                        onInspect = showRiskChit,
                        advice = if (coachActive) adviceForActionChip(state, Action.Coup(PlayerId(0))) else null,
                        onShowCoach = showCoachChit,
                        showConsequence = false,
                        onClick = { onLocalPhase(GamePhase.PickTarget(Action.Coup(PlayerId(0)))) },
                    )
                }
                // Empty weight placeholder keeps HAMLA section always 2 chips (no layout shift)
                Spacer(modifier = Modifier.weight(1f))
            }

            // ③ SPECIAL — steal, swap cards, investigate
            ActionSectionLabel(label = stringResource(Res.string.game_section_special), color = defendColor)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    CompactActionChip(
                        icon = "🤝",
                        name = "SETTING",
                        cost = "⇄",
                        familyColor = defendColor,
                        enabled = true,
                        forced = false,
                        action = Action.Exchange,
                        onInspect = showRiskChit,
                        advice = if (coachActive) adviceForActionChip(state, Action.Exchange) else null,
                        onShowCoach = showCoachChit,
                        showConsequence = false,
                        onClick = { onAction(GameAction.Submit(Intent.DeclareAction(humanSeat, Action.Exchange))) },
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    CompactActionChip(
                        icon = "🪤",
                        name = "VASOOLI",
                        cost = "+2",
                        familyColor = defendColor,
                        enabled = hasStealTarget,
                        forced = false,
                        action = Action.Steal(PlayerId(0)),
                        onInspect = showRiskChit,
                        advice = if (coachActive) adviceForActionChip(state, Action.Steal(PlayerId(0))) else null,
                        onShowCoach = showCoachChit,
                        showConsequence = false,
                        onClick = { onLocalPhase(GamePhase.PickTarget(Action.Steal(PlayerId(0)))) },
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (hasInvestigateTarget) {
                        CompactActionChip(
                            icon = "🔍",
                            name = "JAANCH",
                            cost = "peek",
                            familyColor = defendColor,
                            enabled = true,
                            forced = false,
                            action = Action.Investigate(PlayerId(0)),
                            onInspect = showRiskChit,
                            advice = if (coachActive) adviceForActionChip(state, Action.Investigate(PlayerId(0))) else null,
                            onShowCoach = showCoachChit,
                            showConsequence = false,
                            onClick = { onLocalPhase(GamePhase.PickTarget(Action.Investigate(PlayerId(0)))) },
                        )
                    }
                }
            }
        }
    } else {
        // ── DESKTOP: flat FlowRow (all chips in one flow, consequence text shown) ──
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FlowRow(
                modifier =
                    Modifier
                        .widthIn(max = 560.dp)
                        .padding(vertical = GaddiDimens.space_xs),
                horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_sm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(GaddiDimens.space_sm),
            ) {
                CompactActionChip(
                    icon = "🪙",
                    name = "DEHAADI",
                    cost = "+1",
                    familyColor = brassColor,
                    enabled = true,
                    forced = false,
                    action = Action.Income,
                    onInspect = showRiskChit,
                    advice = if (coachActive) adviceForActionChip(state, Action.Income) else null,
                    onShowCoach = showCoachChit,
                    showConsequence = showConsequence,
                    onClick = { onAction(GameAction.Submit(Intent.DeclareAction(humanSeat, Action.Income))) },
                )
                CompactActionChip(
                    icon = "💵",
                    name = "FDI",
                    cost = "+2",
                    familyColor = brassColor,
                    enabled = true,
                    forced = false,
                    action = Action.ForeignAid,
                    onInspect = showRiskChit,
                    advice = if (coachActive) adviceForActionChip(state, Action.ForeignAid) else null,
                    onShowCoach = showCoachChit,
                    showConsequence = showConsequence,
                    onClick = { onAction(GameAction.Submit(Intent.DeclareAction(humanSeat, Action.ForeignAid))) },
                )
                CompactActionChip(
                    icon = "💰",
                    name = "GHOTALA",
                    cost = "+3",
                    familyColor = brassColor,
                    enabled = true,
                    forced = false,
                    action = Action.Tax,
                    onInspect = showRiskChit,
                    advice = if (coachActive) adviceForActionChip(state, Action.Tax) else null,
                    onShowCoach = showCoachChit,
                    showConsequence = showConsequence,
                    onClick = { onAction(GameAction.Submit(Intent.DeclareAction(humanSeat, Action.Tax))) },
                )
                CompactActionChip(
                    icon = "🔪",
                    name = "SUPARI",
                    cost = "−3",
                    familyColor = disruptColor,
                    enabled = myCoins >= 3 && hasAssassinateTarget,
                    forced = false,
                    action = Action.Assassinate(PlayerId(0)),
                    onInspect = showRiskChit,
                    advice = if (coachActive) adviceForActionChip(state, Action.Assassinate(PlayerId(0))) else null,
                    onShowCoach = showCoachChit,
                    showConsequence = showConsequence,
                    onClick = { onLocalPhase(GamePhase.PickTarget(Action.Assassinate(PlayerId(0)))) },
                )
                CompactActionChip(
                    icon = "🤝",
                    name = "SETTING",
                    cost = "⇄",
                    familyColor = defendColor,
                    enabled = true,
                    forced = false,
                    action = Action.Exchange,
                    onInspect = showRiskChit,
                    advice = if (coachActive) adviceForActionChip(state, Action.Exchange) else null,
                    onShowCoach = showCoachChit,
                    showConsequence = showConsequence,
                    onClick = { onAction(GameAction.Submit(Intent.DeclareAction(humanSeat, Action.Exchange))) },
                )
                CompactActionChip(
                    icon = "🪤",
                    name = "VASOOLI",
                    cost = "+2",
                    familyColor = defendColor,
                    enabled = hasStealTarget,
                    forced = false,
                    action = Action.Steal(PlayerId(0)),
                    onInspect = showRiskChit,
                    advice = if (coachActive) adviceForActionChip(state, Action.Steal(PlayerId(0))) else null,
                    onShowCoach = showCoachChit,
                    showConsequence = showConsequence,
                    onClick = { onLocalPhase(GamePhase.PickTarget(Action.Steal(PlayerId(0)))) },
                )
                if (hasInvestigateTarget) {
                    CompactActionChip(
                        icon = "🔍",
                        name = "JAANCH",
                        cost = "peek",
                        familyColor = defendColor,
                        enabled = true,
                        forced = false,
                        action = Action.Investigate(PlayerId(0)),
                        onInspect = showRiskChit,
                        advice = if (coachActive) adviceForActionChip(state, Action.Investigate(PlayerId(0))) else null,
                        onShowCoach = showCoachChit,
                        showConsequence = showConsequence,
                        onClick = { onLocalPhase(GamePhase.PickTarget(Action.Investigate(PlayerId(0)))) },
                    )
                }
                CompactActionChip(
                    icon = "💥",
                    name = "KHELA",
                    cost = if (myCoins >= 7) "−7" else "need 7",
                    familyColor = disruptColor,
                    enabled = myCoins >= 7 && hasCoupTarget,
                    forced = myCoins >= 7,
                    action = Action.Coup(PlayerId(0)),
                    onInspect = showRiskChit,
                    advice = if (coachActive) adviceForActionChip(state, Action.Coup(PlayerId(0))) else null,
                    onShowCoach = showCoachChit,
                    showConsequence = showConsequence,
                    onClick = { onLocalPhase(GamePhase.PickTarget(Action.Coup(PlayerId(0)))) },
                )
            }
        }
    }
}

/**
 * Compact 44dp-tall pill chip for the action dock.
 * [forced] = true → solid filled background (KHELA at 7+ coins or forced coup).
 * [enabled] = false → chip dims to 45%, cost turns alert_red, untappable.
 */
@Composable
internal fun CompactActionChip(
    icon: String,
    name: String,
    cost: String,
    familyColor: Color,
    enabled: Boolean,
    forced: Boolean,
    onClick: () -> Unit,
    // The action this chip declares — used to build the long-press RiskAction inspect chit.
    action: Action? = null,
    onInspect: (Action, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    // DECISION-COACH read for this action (null until the advisor finishes / for no-claim moves).
    advice: com.kursi.ai.advisor.MoveAdvice? = null,
    onShowCoach: (com.kursi.ai.advisor.MoveAdvice, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    // On phone (compact=true) the per-chip consequence text is suppressed to save vertical space.
    // The HintRail above the dock already surfaces contextual guidance.
    showConsequence: Boolean = true,
) {
    val alertRed = Color(0xFF8E2B22)
    val chipAlpha = if (enabled) 1f else 0.45f

    // Coach styling only applies on an enabled chip with a role-claim verdict.
    val tone = if (enabled) advice?.let { coachTone(it.truthful, it.bluff) } ?: CoachTone.Neutral else CoachTone.Neutral
    val recommended = enabled && advice?.recommended == true
    val coachAccentColor = if (tone != CoachTone.Neutral) coachAccent(tone) else null

    // AAA FOCUS rebuild: the primary/recommended chip is a gold-filled raised "stamp" (mockup
    // `.act.pri`); everything else is a dark raised chip with a brass hairline + gold value —
    // never a flat panel fill. `forced` (mandatory Khela) keeps its alert-family fill so danger
    // still reads distinct from "recommended".
    val bgBrush: Brush =
        when {
            !enabled -> Brush.verticalGradient(listOf(BrandTokens.TeakDark, BrandTokens.TeakInk))
            recommended -> Brush.verticalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged))
            forced -> Brush.verticalGradient(listOf(familyColor.copy(alpha = 0.95f), familyColor.copy(alpha = 0.65f)))
            coachAccentColor != null ->
                Brush.verticalGradient(
                    listOf(coachAccentColor.copy(alpha = 0.22f), coachAccentColor.copy(alpha = 0.08f)),
                )
            else -> Brush.verticalGradient(listOf(BrandTokens.TeakMid, BrandTokens.TeakDark))
        }
    val borderColor =
        when {
            !enabled -> BrandTokens.BrassDark.copy(alpha = 0.3f)
            recommended -> BrandTokens.GoldAntique
            forced -> familyColor
            coachAccentColor != null -> coachAccentColor.copy(alpha = 0.7f)
            else -> familyColor.copy(alpha = 0.6f)
        }
    val borderWidth = if (recommended) 2.dp else GaddiDimens.stroke_ring_idle

    // Capture the chip's bounds so the inspect chit anchors with a caret to the chip.
    var chipBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    // RETRO-FUTURIST §2 — the recommended move is the one LIVE call-to-action in the
    // dock, so it earns a restrained holographic gold rim glint (only when coach-on).
    val recHolo by if (recommended) {
        rememberInfiniteTransition(label = "recHolo").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
            label = "recHoloPhase",
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier =
                Modifier
                    .onGloballyPositioned { chipBounds = it.boundsInRoot() }
                    .heightIn(min = 52.dp)
                    .widthIn(min = 96.dp, max = 168.dp)
                    // A real cast shadow — the chip is a raised stamp resting on the felt, not a
                    // flat panel. Dims for disabled/unaffordable chips (they sit flush, not raised).
                    .shadow(
                        if (enabled) (if (recommended) 8.dp else 4.dp) else 1.dp,
                        Squircle(GaddiDimens.r_md),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.55f),
                        spotColor = if (recommended) BrandTokens.GoldAntique else BrandTokens.TeakInk,
                    ).then(
                        if (recommended) {
                            Modifier.holoRimLight(
                                accent = BrandTokens.GoldAntique,
                                phase = recHolo,
                                cornerRadius = GaddiDimens.r_md,
                                intensity = 0.6f,
                            )
                        } else {
                            Modifier
                        },
                    ).clip(Squircle(GaddiDimens.r_md))
                    .background(bgBrush)
                    .border(borderWidth, borderColor, Squircle(GaddiDimens.r_md))
                    .actionChipSemantics(
                        name = name,
                        cost = cost,
                        action = action,
                        enabled = enabled,
                        recommended = recommended,
                    )
                    // Tap declares (when enabled). Long-press ALWAYS inspects — it prefers the
                    // richer DECISION-COACH chit (with truthful/bluff + odds + win chance) when the
                    // advisor has weighed in, else falls back to the static RiskAction chit (so you
                    // can still read why a locked move is locked).
                    .inspectable(
                        onClick = { if (enabled) onClick() },
                        onLongClick = {
                            if (enabled && advice != null) {
                                onShowCoach(advice, chipBounds)
                            } else if (action != null) {
                                onInspect(action, chipBounds)
                            }
                        },
                        pressShape = Squircle(GaddiDimens.r_md),
                    ).padding(horizontal = GaddiDimens.space_sm, vertical = GaddiDimens.space_xs),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (recommended) RecommendedStar()
                // Icon (text emoji as icon — 15sp)
                Text(
                    text = icon,
                    style = GaddiType.label_sm.copy(fontSize = 15.sp),
                    color = if (recommended || forced) GaddiNeutrals.Cream.copy(alpha = chipAlpha) else familyColor.copy(alpha = chipAlpha),
                    maxLines = 1,
                )
                // Name — fit-to-width CAPS so labels never clip across languages
                AutoSizeText(
                    text = name,
                    style = GaddiType.label_sm,
                    color =
                        when {
                            recommended -> BrandTokens.TeakDark.copy(alpha = chipAlpha)
                            forced -> GaddiNeutrals.Cream.copy(alpha = chipAlpha)
                            else -> GaddiNeutrals.TextPrimary.copy(alpha = chipAlpha)
                        },
                    maxLines = 1,
                    minSize = 8.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // Cost pill — used to have a role glyph appended ("+3 ⚖") but on a phone-width
                // chip that second emoji ate the width the AutoSizeText name needed, forcing
                // ellipsis even at minSize (e.g. "GHO… +3"). The leading [icon] already carries
                // the action's identity; the cost pill stays a bare value.
                Text(
                    text = cost,
                    style = GaddiType.numeral_sm,
                    color =
                        if (!enabled) {
                            alertRed.copy(alpha = chipAlpha)
                        } else if (recommended) {
                            BrandTokens.TeakDark.copy(alpha = chipAlpha)
                        } else if (forced) {
                            GaddiNeutrals.Cream.copy(alpha = chipAlpha)
                        } else {
                            familyColor.copy(alpha = chipAlpha)
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Coach read strip under the chip: truthful/bluff badge for role-claim actions.
        if (enabled && advice != null && tone != CoachTone.Neutral) {
            val claimedName = (advice.intent as? Intent.DeclareAction)?.action?.let { Rules.claimedRole(it)?.let { r -> roleLabel(r) } }
            CoachBadge(truthful = advice.truthful, bluff = advice.bluff, claimedRoleName = claimedName)
        }
        // CLARITY (Tenet 1) — what this action does + its cost/risk.
        // Suppressed on phone (compact=true): the HintRail above the dock covers context,
        // and hiding per-chip text reduces the dock's height significantly.
        if (showConsequence && action != null) {
            val voice = LocalGaddiVoice.current
            Text(
                text = voice.actionConsequence(action),
                style = GaddiType.label_micro,
                color = GaddiNeutrals.TextMuted.copy(alpha = if (enabled) 1f else 0.55f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.widthIn(max = 168.dp),
            )
        }
    }
}

@Composable
internal fun PickTargetDock(
    action: Action,
    state: GameUiState,
    onLocalPhase: (GamePhase?) -> Unit,
) {
    val voice = LocalGaddiVoice.current
    // The explicit weakest/most-suspicious recommendation — so the player isn't left to read the
    // per-plate suspicion pips unaided. PUBLIC-info only (coach's pick, else weakest public seat).
    // Only shown when coach guidance is visible (coach ON, and not FOCUS — spec §3).
    val recommended =
        if (state.coachGuidanceVisible) {
            remember(state.advice, state.view, action) { recommendedTarget(action, state) }
        } else {
            null
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Recommended-target callout: a brass star + the seat the coach would aim at ──
        // Gated: only shown when coach is ON.
        if (recommended != null) {
            val (targetId, reason) = recommended
            val targetName = personaNameOrDefault(targetId, state, voice.selfName)
            Row(
                modifier =
                    Modifier
                        .clip(Squircle(GaddiRadii.sm))
                        .background(BrandTokens.GoldAntique.copy(alpha = 0.14f))
                        .border(GaddiDimens.stroke_hairline, BrandTokens.GoldAntique.copy(alpha = 0.55f), Squircle(GaddiRadii.sm))
                        .padding(horizontal = GaddiDimens.space_sm, vertical = GaddiDimens.space_xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                RecommendedStar()
                Text(
                    text = stringResource(Res.string.game_recommended_target, targetName, reason),
                    style = GaddiType.label_sm,
                    color = GaddiNeutrals.TextPrimary,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = voice.centerPrompt(CenterPrompt.PickTarget),
            style = GaddiType.body,
            color = GaddiNeutrals.TextSecondary,
            textAlign = TextAlign.Center,
        )
        GaddiActionButton(
            label = stringResource(Res.string.game_cancel),
            enabled = true,
            onClick = { onLocalPhase(GamePhase.PickAction) },
        )
    }
}

@Composable
internal fun ConfirmDock(
    action: Action,
    target: PlayerId?,
    humanSeat: PlayerId,
    state: GameUiState,
    onLocalPhase: (GamePhase?) -> Unit,
    onAction: (GameAction) -> Unit,
) {
    val voice = LocalGaddiVoice.current
    val role = Rules.claimedRole(action)
    val roleStr = role?.let { "${roleLabel(it)} → " } ?: ""
    val targetStr = target?.let { "from ${personaNameOrDefault(it, state, voice.selfName)}" } ?: ""
    val summary = "$roleStr${actionName(action)} $targetStr".trim()

    // ── DECISION-COACH read at the moment of declaring ───────────────────────
    // For a claim-bearing action (Tax/Steal/Supari/Setting) the player deserves an exposure read
    // BEFORE they commit: the truthful/bluff verdict + the P(it flies) odds, plus the brass star
    // when this is the advisor's recommended move. We resolve the concrete (action+target) advice
    // first, then fall back to the action-type entry (verdict + odds are target-independent — they
    // turn on the CLAIMED ROLE and public card-accounting, never on hidden cards). Coup/Khela makes
    // no claim, so it carries no verdict/odds — correct, and the strip simply renders nothing.
    val finalAction = buildActionWithTarget(action, target)
    // Coach guidance (recommended star, badge, odds) only shown when visible (spec §3).
    val advice =
        if (state.coachGuidanceVisible) {
            adviceFor(state, Intent.DeclareAction(humanSeat, finalAction))
                ?: adviceForActionChip(state, action)
        } else {
            null
        }
    val tone = advice?.let { coachTone(it.truthful, it.bluff) } ?: CoachTone.Neutral
    val recommended = advice?.recommended == true
    val claimedName = role?.let { roleLabel(it) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_xs),
        ) {
            // Recommended star only shown when coach is ON.
            if (recommended) RecommendedStar()
            Text(
                text = summary,
                style = GaddiType.title,
                color = GaddiNeutrals.TextPrimary,
            )
        }
        // The exposure read strip — truthful/bluff badge + odds pill — only for claim-bearing moves.
        // Gated: only shown when coach is ON (advice is null when coach is OFF).
        if (advice != null && (tone != CoachTone.Neutral || advice.successOdds != null)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tone != CoachTone.Neutral) {
                    CoachBadge(truthful = advice.truthful, bluff = advice.bluff, claimedRoleName = claimedName)
                }
                advice.successOdds?.let { odds ->
                    // A DeclareAction is never a "challenge" — odds read as P(it flies) safe.
                    CoachOddsPill(isChallenge = false, successOdds = odds)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaddiActionButton(
                label = "Cancel",
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = { onLocalPhase(GamePhase.PickAction) },
            )
            GaddiActionButton(
                label = stringResource(Res.string.game_declare),
                roleAccent = GaddiSemantics.Success,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = {
                    onAction(GameAction.Submit(Intent.DeclareAction(humanSeat, finalAction)))
                    onLocalPhase(null)
                },
            )
        }
    }
}

internal fun buildActionWithTarget(
    action: Action,
    target: PlayerId?,
): Action =
    when (action) {
        is Action.Coup -> if (target != null) Action.Coup(target) else action
        is Action.Assassinate -> if (target != null) Action.Assassinate(target) else action
        is Action.Steal -> if (target != null) Action.Steal(target) else action
        else -> action
    }

@Composable
internal fun IdleDock(state: GameUiState) {
    val voice = LocalGaddiVoice.current
    val actorName = actorName(state)

    // Resolve the acting seat's color so the card is visually tied to that player.
    val actorId: PlayerId? =
        when (val p = state.view.phase) {
            is PhaseView.Turn -> p.actor
            is PhaseView.Reactions -> p.actor
            is PhaseView.InfluenceLoss -> p.loser
            else -> null
        }
    val actorColor = actorId?.let { GaddiSeatColors[it.raw] } ?: BrandTokens.BrassAged

    // Last 3 meaningful events, oldest first — shows the full chain (e.g.
    // "Babu used Tax → Netaji challenged → Babu revealed → Netaji lost").
    val feedEvents = lastNRecapEvents(state, 3)

    // Pulsing live-dot so the player knows the game is progressing.
    val pulse by rememberInfiniteTransition(label = "idlePulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec =
            infiniteRepeatable(
                tween(900, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
        label = "idlePulseAlpha",
    )

    // AAA rebuild (design-language.md #1): a shadow-depth raised surface tinted by the
    // acting seat's color, not a bordered box — same tableDepth + embossEdge material as
    // decoPanel, so the "what's happening" summary reads as an instrument on the table.
    val idleShape = Squircle(GaddiRadii.sm)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .tableDepth(idleShape, elevation = 4.dp)
                .clip(idleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(actorColor.copy(alpha = 0.14f), BrandTokens.TeakDark.copy(alpha = 0.92f)),
                    ),
                ).embossEdge(GaddiRadii.sm, highlight = actorColor.copy(alpha = 0.4f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ── "Whose turn" header ──────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(actorColor.copy(alpha = pulse)),
            )
            Text(
                text = voice.opponentActing(actorName),
                style = GaddiType.label_sm.copy(fontSize = 13.sp, letterSpacing = 0.5.sp),
                color = actorColor,
            )
        }

        // ── Recent event feed: last 3 events, oldest dimmed, newest bright ──
        // This makes challenge chains legible:
        //   (dim)   Babu claimed VAKIL via GHOTALA
        //   (mid)   Netaji challenged the claim
        //   (bright) Babu revealed — TRUE, Netaji lost influence
        if (feedEvents.isNotEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(actorColor.copy(alpha = 0.2f)),
            )
            val count = feedEvents.size
            feedEvents.forEachIndexed { idx, ev ->
                val (evActor, evOther) = recapNames(ev, state)
                val line = voice.recap(ev, evActor, evOther) ?: return@forEachIndexed
                // Oldest event = most dimmed; newest = full brightness
                val alpha =
                    when (count - idx) {
                        1 -> 1.0f // newest — full brightness
                        2 -> 0.60f // one back — medium
                        else -> 0.35f // oldest — dim
                    }
                // Newest event gets a slightly larger text size to draw the eye
                val textSize = if (idx == count - 1) 13.sp else 12.sp
                Text(
                    text = line,
                    style = GaddiType.body.copy(fontSize = textSize),
                    color = GaddiNeutrals.TextPrimary.copy(alpha = alpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.semantics {
                            liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                        },
                )
            }
            // ── Last bot chat: what the acting bot just said ─────────────────────
            // Shows only in narrative mode so the player can read what the bot said
            // without depending on seeing the 5-second speech bubble on the plate.
            val actorChatLine =
                if (state.narrativeEnabled) {
                    val actorIdx = actorId?.raw
                    if (actorIdx != null) {
                        state.chatFeed.lastOrNull { !it.fromPlayer && !it.isNarrator && it.senderSeat == actorIdx }
                    } else {
                        null
                    }
                } else {
                    null
                }
            if (actorChatLine != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(actorColor.copy(alpha = 0.15f)),
                )
                val senderPlayer = state.view.players.firstOrNull { it.seatIndex == actorChatLine.senderSeat }
                val senderName = senderPlayer?.let { state.opponentPersonas[it.id]?.name } ?: actorName
                Text(
                    text = "💬 $senderName: \"${actorChatLine.body}\"",
                    style = GaddiType.body.copy(fontSize = 12.sp),
                    color = BrandTokens.GoldAntique.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
