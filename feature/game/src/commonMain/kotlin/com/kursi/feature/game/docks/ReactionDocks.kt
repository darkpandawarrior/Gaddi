package com.kursi.feature.game.docks

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.engine.*
import com.kursi.feature.game.*
import com.kursi.feature.game.coach.*
import com.kursi.feature.game.overlays.*
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.game_jaanch_decide_redraw
import kursi.core.designsystem.generated.resources.game_jaanch_peeked_prompt
import kursi.core.designsystem.generated.resources.game_jaanch_target_header
import kursi.core.designsystem.generated.resources.game_keep_none
import kursi.core.designsystem.generated.resources.game_keep_roles_label
import kursi.core.designsystem.generated.resources.game_leave_it
import kursi.core.designsystem.generated.resources.game_new_game_4p
import kursi.core.designsystem.generated.resources.game_play_again
import kursi.core.designsystem.generated.resources.game_players_count
import kursi.core.designsystem.generated.resources.game_spike_it
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ReactionDock(
    reactionWindow: GamePhase.ReactionWindow,
    humanSeat: PlayerId,
    state: GameUiState,
    onAction: (GameAction) -> Unit,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
) {
    val voice = LocalKursiVoice.current
    val alertRed = Color(0xFF8E2B22)
    val brassColor = BrandTokens.BrassAged
    val verdigris = Color(0xFF3F6B5E)

    // Map a reaction option to its coach advice (null until advisor finishes, coach is OFF, or FOCUS).
    val challengeAdvice = if (state.coachGuidanceVisible) adviceFor(state, Intent.Challenge(humanSeat)) else null
    val passAdvice = if (state.coachGuidanceVisible) adviceFor(state, Intent.Pass(humanSeat)) else null

    // The CLAIM under scrutiny for a challenge: the actor's action-claim on a CHALLENGE_ACTION /
    // CHALLENGE_BLOCK-of-the-action, the blocker's block-role on a CHALLENGE_BLOCK. Used to build the
    // coach's belief-grounded "is this a bluff?" read (public card-accounting only).
    // Gated: the belief banner is proactive guidance — only shown when coach is ON.
    val scrutinisedRole: Role? =
        when (reactionWindow.step) {
            ReactionStep.CHALLENGE_BLOCK -> reactionWindow.blockRole
            else -> reactionWindow.claimedRole
        }
    val scrutinisedLabel: String =
        when (reactionWindow.step) {
            ReactionStep.CHALLENGE_BLOCK -> "block"
            else -> actionName(reactionWindow.action)
        }
    val challengeBelief: String? =
        if (state.coachGuidanceVisible) {
            scrutinisedRole?.let { challengeBeliefLine(state, it, scrutinisedLabel) }
        } else {
            null
        }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ── The READ, made visible: the coach's belief-grounded line about the claim on the table,
        // so the player sees WHY a challenge is favourable (or a gamble) before they even long-press.
        if (challengeBelief != null) {
            val favourable = challengeBelief.contains("favourable") || challengeBelief.contains("real odds")
            val readAccent = if (favourable) KursiSemantics.Success else BrandTokens.GoldAntique
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(Squircle(KursiRadii.sm))
                        .background(readAccent.copy(alpha = 0.14f))
                        .border(KursiDimens.stroke_hairline, readAccent.copy(alpha = 0.55f), Squircle(KursiRadii.sm))
                        .padding(horizontal = KursiDimens.space_sm, vertical = KursiDimens.space_xs),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(text = "🔎", style = KursiType.label_sm)
                Text(
                    text = challengeBelief,
                    style = KursiType.label_sm,
                    color = KursiNeutrals.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        when (reactionWindow.step) {
            ReactionStep.CHALLENGE_ACTION -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KursiDimens.space_sm, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    ReactionChip(
                        label = "⚡ ${voice.challengeBtn.uppercase()}",
                        familyColor = alertRed,
                        advice = challengeAdvice,
                        onClick = { onAction(GameAction.Submit(Intent.Challenge(humanSeat))) },
                        onShowChit = onShowChit,
                        beliefLine = challengeBelief,
                        consequence = voice.reactionChallengeConsequence,
                    )
                    ReactionChip(
                        label = "↩ ${voice.passChallenge.uppercase()}",
                        familyColor = brassColor,
                        advice = passAdvice,
                        onClick = { onAction(GameAction.Submit(Intent.Pass(humanSeat))) },
                        onShowChit = onShowChit,
                        consequence = voice.reactionPassConsequence,
                    )
                }
                Text(
                    text = voice.reactionHintChallenge,
                    style = KursiType.caption.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = KursiNeutrals.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ReactionStep.BLOCK -> {
                val blockRoles = Rules.rolesThatBlock(reactionWindow.action)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KursiDimens.space_sm, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    blockRoles.forEach { role ->
                        ReactionChip(
                            label = "🛡 BLOCK (${roleLabel(role)})",
                            familyColor = verdigris,
                            advice = if (state.coachGuidanceVisible) adviceFor(state, Intent.Block(humanSeat, role)) else null,
                            onClick = { onAction(GameAction.Submit(Intent.Block(humanSeat, role))) },
                            onShowChit = onShowChit,
                            consequence = voice.reactionBlockConsequence(role),
                        )
                    }
                    if (reactionWindow.myLegalResponses.any { it is Intent.Challenge }) {
                        ReactionChip(
                            label = "⚡ ${voice.challengeBtn.uppercase()}",
                            familyColor = alertRed,
                            advice = challengeAdvice,
                            onClick = { onAction(GameAction.Submit(Intent.Challenge(humanSeat))) },
                            onShowChit = onShowChit,
                            beliefLine = challengeBelief,
                            consequence = voice.reactionChallengeConsequence,
                        )
                    }
                    ReactionChip(
                        label = "↩ ${voice.passBlock.uppercase()}",
                        familyColor = brassColor,
                        advice = passAdvice,
                        onClick = { onAction(GameAction.Submit(Intent.Pass(humanSeat))) },
                        onShowChit = onShowChit,
                        consequence = voice.reactionPassConsequence,
                    )
                }
                Text(
                    text = voice.reactionHintBlock,
                    style = KursiType.caption.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = KursiNeutrals.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ReactionStep.CHALLENGE_BLOCK -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KursiDimens.space_sm, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    ReactionChip(
                        label = "⚡ ${voice.challengeBtn.uppercase()}",
                        familyColor = alertRed,
                        advice = challengeAdvice,
                        onClick = { onAction(GameAction.Submit(Intent.Challenge(humanSeat))) },
                        onShowChit = onShowChit,
                        beliefLine = challengeBelief,
                        consequence = voice.reactionChallengeConsequence,
                    )
                    ReactionChip(
                        label = "↩ ${voice.passBlock.uppercase()}",
                        familyColor = brassColor,
                        advice = passAdvice,
                        onClick = { onAction(GameAction.Submit(Intent.Pass(humanSeat))) },
                        onShowChit = onShowChit,
                        consequence = voice.reactionPassConsequence,
                    )
                }
                Text(
                    text = voice.reactionHintChallengeBlock,
                    style = KursiType.caption.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = KursiNeutrals.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * A reaction option chip with the DECISION-COACH read baked in:
 *  - truthful/bluff TINT (green / oxblood) on the chip border + fill when the move claims a role,
 *  - a compact safety badge + odds pill beneath the label,
 *  - a brass RECOMMENDED star on the advisor's pick (with a rim glow),
 *  - long-press opens the full coach chit (rationale + odds + win chance).
 * Falls back to the plain familyColor styling until advice arrives (or for no-claim moves).
 */
@Composable
internal fun ReactionChip(
    label: String,
    familyColor: Color,
    advice: com.kursi.ai.advisor.MoveAdvice?,
    onClick: () -> Unit,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    // Belief-grounded read shown in the long-press coach chit (e.g. on a CHALLENGE chip).
    beliefLine: String? = null,
    // CLARITY (Tenet 1) — always-shown plain "what this does + its risk" line. Ungated.
    consequence: String? = null,
) {
    val tone = advice?.let { coachTone(it.truthful, it.bluff) } ?: CoachTone.Neutral
    val recommended = advice?.recommended == true
    // Tint the chip by coaching tone when the move makes a role claim; otherwise keep familyColor.
    val chipColor = if (advice != null && tone != CoachTone.Neutral) coachAccent(tone) else familyColor
    val ringColor = if (recommended) BrandTokens.GoldAntique else chipColor.copy(alpha = 0.6f)
    val ringWidth = if (recommended) 2.dp else KursiDimens.stroke_ring_idle

    // Is this a challenge (odds = P(opponent bluffing)) or a bluff move (odds = P(safe))?
    val isChallenge = advice?.intent is Intent.Challenge

    var chipBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier =
                Modifier
                    .onGloballyPositioned { chipBounds = it.boundsInRoot() }
                    .heightIn(min = 52.dp)
                    .widthIn(min = 96.dp, max = 200.dp)
                    .clip(Squircle(KursiDimens.r_md))
                    .background(chipColor.copy(alpha = 0.15f))
                    .border(ringWidth, ringColor, Squircle(KursiDimens.r_md))
                    .reactionChipSemantics(label = label, recommended = recommended)
                    .inspectable(
                        onClick = onClick,
                        onLongClick = { if (advice != null) onShowChit(coachChitOf(advice, beliefLine), chipBounds) },
                        pressShape = Squircle(KursiDimens.r_md),
                    ).padding(horizontal = KursiDimens.space_sm, vertical = KursiDimens.space_xs),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (recommended) RecommendedStar()
                AutoSizeText(
                    text = label,
                    style = KursiType.label_sm,
                    color = KursiNeutrals.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    minSize = 8.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        // Coach read strip under the chip: safety badge + odds pill.
        if (advice != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val claimedName = (advice.intent as? Intent.Block)?.role?.let { roleLabel(it) }
                if (tone != CoachTone.Neutral) {
                    CoachBadge(truthful = advice.truthful, bluff = advice.bluff, claimedRoleName = claimedName)
                }
                advice.successOdds?.let { odds ->
                    CoachOddsPill(isChallenge = isChallenge, successOdds = odds)
                }
            }
        }
        // CLARITY (Tenet 1) — ALWAYS-SHOWN what-now line: what this reaction does + its risk.
        // Ungated by the coach; pure comprehension.
        if (consequence != null) {
            Text(
                text = consequence,
                style = KursiType.label_micro,
                color = KursiNeutrals.TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
    }
}

@Composable
internal fun LoseInfluenceDock(state: GameUiState) {
    val voice = LocalKursiVoice.current
    val cause = loseInfluenceCause(state)
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // WHY this card is being sacrificed — the clear cause line.
        Box(
            modifier =
                Modifier
                    .clip(Squircle(KursiRadii.sm))
                    .background(KursiSemantics.Danger.copy(alpha = 0.14f))
                    .border(KursiDimens.stroke_hairline, KursiSemantics.Danger.copy(alpha = 0.6f), Squircle(KursiRadii.sm))
                    .padding(horizontal = KursiDimens.space_sm, vertical = KursiDimens.space_xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(text = "💀", style = KursiType.label_sm)
                Text(
                    text = cause,
                    style = KursiType.label_md,
                    color = KursiSemantics.Danger,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            text = voice.centerPrompt(CenterPrompt.LoseInfluence),
            style = KursiType.body,
            color = KursiNeutrals.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The clear "why am I losing a card" cause line for the human's influence-loss prompt — folding the
 * engine's [com.kursi.engine.LossReason] (carried PUBLIC-safe on [PhaseView.InfluenceLoss]) together
 * with the aggressor reconstructed from the recent public event stream, so the player reads
 * "Lost to Bhai Teja's Supari" instead of a bare "choose a card".
 */
internal fun loseInfluenceCause(state: GameUiState): String {
    val phase =
        state.view.phase as? PhaseView.InfluenceLoss
            ?: return "You must give up a card."
    val events = state.recentEvents
    // Most-recent declared action = candidate Supari/Khela aggressor; most-recent challenge = the
    // challenger who exposed your bluff. Mirrors GameSession.routeGrudges' causality reconstruction.
    val lastDeclared = events.filterIsInstance<GameEvent.ActionDeclared>().lastOrNull()
    val lastChallenge = events.filterIsInstance<GameEvent.Challenged>().lastOrNull()

    fun name(id: PlayerId) = personaNameOrDefault(id, state)
    return when (phase.reason) {
        com.kursi.engine.LossReason.ASSASSINATED -> {
            val who = lastDeclared?.actor?.let { name(it) }
            if (who != null) "Lost to $who's Supari." else "Lost to a Supari hit."
        }
        com.kursi.engine.LossReason.COUPED -> {
            val who = lastDeclared?.actor?.let { name(it) }
            if (who != null) "Lost to $who's Khela." else "Lost to a Khela."
        }
        com.kursi.engine.LossReason.LOST_CHALLENGE -> {
            val who = lastChallenge?.challenger?.let { name(it) }
            if (who != null) {
                "Your bluff was caught — $who challenged and you couldn't prove it."
            } else {
                "Your claim was challenged and didn't hold."
            }
        }
        com.kursi.engine.LossReason.LOST_BLOCK_CHALLENGE -> {
            val who = lastChallenge?.challenger?.let { name(it) }
            if (who != null) {
                "Your block was challenged by $who — and it didn't hold."
            } else {
                "Your block was challenged and didn't hold."
            }
        }
        com.kursi.engine.LossReason.SABOTAGED -> "You played Bali Khel — sacrificed an influence for coins."
        com.kursi.engine.LossReason.EMERGENCY_COUPED -> {
            val who = lastDeclared?.actor?.let { name(it) }
            if (who != null) "Lost to $who's ADHYADESH." else "Lost to an Emergency Coup."
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExchangeDock(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
) {
    val voice = LocalKursiVoice.current
    val exchangeIntents = state.legalIntents.filterIsInstance<Intent.ChooseExchange>()

    // Resolve every CardId the human can see during the exchange to its real role.
    // SECRECY-SAFE: both sources are the viewer's OWN cards only — myCards (face-down hand +
    // face-up reveals) and PhaseView.Exchange.drawn (populated ONLY for the exchanging actor's
    // own view). We never touch an opponent's hidden card.
    val drawnCards: List<OwnCard> = (state.view.phase as? PhaseView.Exchange)?.drawn.orEmpty()
    val drawnIds: Set<CardId> = drawnCards.map { it.id }.toSet()
    val cardById: Map<CardId, OwnCard> =
        (state.view.myCards + drawnCards).associateBy { it.id }

    // The advisor's recommended keep, if advice has arrived. advice covers ChooseExchange intents
    // (one entry per legal intent, exactly one recommended). Star that option in the UI.
    val recommendedKeep: Set<CardId>? =
        state.advice
            .firstOrNull { it.recommended }
            ?.let { (it.intent as? Intent.ChooseExchange)?.keep?.toSet() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = voice.centerPrompt(CenterPrompt.Exchange),
            style = KursiType.body,
            color = KursiNeutrals.TextSecondary,
        )
        // Lay the keep-options out in a 2-wide wrapping grid so they fill the wide dock
        // panel instead of crowding a thin left column with a large empty void to the right.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2,
        ) {
            exchangeIntents.forEach { intent ->
                val keepCards = intent.keep.mapNotNull { cardById[it] }
                val recommended = recommendedKeep != null && intent.keep.toSet() == recommendedKeep
                ExchangeKeepOption(
                    keepCards = keepCards,
                    drawnIds = drawnIds,
                    recommended = recommended,
                    onClick = { onAction(GameAction.Submit(intent)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One keep-choice in the exchange dock: shows each card the option would KEEP as a mini RoleCard
 * with a clear "Keep NETA + BHAI" label, tags each card as DRAWN vs HAND, and stars the advisor's
 * recommended keep. Replaces the old blind "Keep option N".
 */
@Composable
internal fun ExchangeKeepOption(
    keepCards: List<OwnCard>,
    drawnIds: Set<CardId>,
    recommended: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label =
        if (keepCards.isEmpty()) {
            stringResource(Res.string.game_keep_none)
        } else {
            stringResource(Res.string.game_keep_roles_label, keepCards.joinToString(" + ") { roleLabel(it.role) })
        }
    val accent = if (recommended) KursiSemantics.Success else BrandTokens.BrassAged

    Column(
        modifier =
            modifier
                .clip(Squircle(KursiRadii.md))
                .background(KursiFeltColors.Surface3)
                .border(
                    width = if (recommended) 2.dp else 1.dp,
                    color = accent.copy(alpha = if (recommended) 1f else 0.5f),
                    shape = Squircle(KursiRadii.md),
                ).keepOptionSemantics(
                    roleNames = keepCards.map { roleLabelA11y(it.role) },
                    recommended = recommended,
                ).clickable(onClick = onClick)
                .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (recommended) RecommendedStar()
            Text(
                text = label,
                style = KursiType.label_sm,
                color = if (recommended) KursiSemantics.Success else KursiNeutrals.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            keepCards.forEach { card ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    RoleCard(role = card.role, size = CardSize.Small)
                    Text(
                        text = if (card.id in drawnIds) "DRAWN" else "HAND",
                        style = KursiType.label_sm.copy(letterSpacing = 1.sp),
                        color = if (card.id in drawnIds) BrandTokens.GoldAntique else KursiNeutrals.TextMuted,
                    )
                }
            }
        }
    }
}

/**
 * Jaanch follow-up dock — after the PATRAKAAR examiner privately peeks one of the target's hidden
 * cards, they decide whether to SPIKE it (force the target to shuffle it back and redraw) or LEAVE it.
 * SECRECY: the peeked role is shown ONLY here, in the examiner's own dock (it arrives via the
 * examiner-only PhaseView.InvestigatePeek.examinedCard); no other seat ever sees it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InvestigatePeekDock(
    phase: GamePhase.InvestigatePeek,
    state: GameUiState,
    onAction: (GameAction) -> Unit,
) {
    val redrawIntent =
        state.legalIntents
            .filterIsInstance<Intent.ResolveInvestigate>()
            .firstOrNull { it.forceRedraw }
    val keepIntent =
        state.legalIntents
            .filterIsInstance<Intent.ResolveInvestigate>()
            .firstOrNull { !it.forceRedraw }
    val targetName = personaNameOrDefault(phase.target, state)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.game_jaanch_target_header, targetName),
            style = KursiType.label_sm.copy(letterSpacing = 1.sp),
            color = BrandTokens.GoldAntique,
        )
        if (phase.peekedRole != null) {
            RoleCard(role = phase.peekedRole, size = CardSize.Small)
            Text(
                text = stringResource(Res.string.game_jaanch_peeked_prompt, roleLabel(phase.peekedRole)),
                style = KursiType.body,
                color = KursiNeutrals.TextSecondary,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = stringResource(Res.string.game_jaanch_decide_redraw, targetName),
                style = KursiType.body,
                color = KursiNeutrals.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (redrawIntent != null) {
                InvestigateChoice(
                    label = stringResource(Res.string.game_spike_it),
                    accent = Color(0xFF8E2B22),
                    onClick = { onAction(GameAction.Submit(redrawIntent)) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (keepIntent != null) {
                InvestigateChoice(
                    label = stringResource(Res.string.game_leave_it),
                    accent = BrandTokens.BrassAged,
                    onClick = { onAction(GameAction.Submit(keepIntent)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun InvestigateChoice(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(Squircle(KursiRadii.md))
                .background(accent.copy(alpha = 0.15f))
                .border(1.dp, accent.copy(alpha = 0.6f), Squircle(KursiRadii.md))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = KursiType.label_sm.copy(letterSpacing = 1.sp),
            color = KursiNeutrals.TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun GameOverDock(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
) {
    val winnerSeat = state.winnerSeat ?: 0
    val winnerColor = KursiSeatColors[winnerSeat]
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KursiActionButton(
            label = stringResource(Res.string.game_play_again),
            sublabel = stringResource(Res.string.game_players_count, state.view.config.seatCount),
            roleAccent = KursiSemantics.Success,
            enabled = true,
            onClick = { onAction(GameAction.NewGame(playerCount = state.view.config.seatCount)) },
        )
        KursiActionButton(
            label = stringResource(Res.string.game_new_game_4p),
            enabled = true,
            onClick = { onAction(GameAction.NewGame(playerCount = 4)) },
        )
    }
}
