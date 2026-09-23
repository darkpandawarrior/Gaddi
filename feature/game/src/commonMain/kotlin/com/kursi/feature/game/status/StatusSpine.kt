package com.kursi.feature.game.status

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.engine.*
import com.kursi.feature.game.*
import com.kursi.feature.game.overlays.*
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.a11y_niyam_rules_gazette
import kursi.core.designsystem.generated.resources.game_bot_fallback_name
import kursi.core.designsystem.generated.resources.game_recap_label_pichla_dav
import org.jetbrains.compose.resources.stringResource

// ─────────────────────────── WHAT-JUST-HAPPENED recap (Clarity, Tenet 1) ──────
// A compact, plain-language recap of the most-recent resolved beat, shown near the
// spine on EVERY phase. ALWAYS shown — NOT gated under the coach (this is
// comprehension, not advice). Routes all copy through GaddiVoice (bilingual).

/** Returns true for events that merit a plain-language recap line. */
internal fun isRecapWorthy(ev: GameEvent): Boolean =
    when (ev) {
        is GameEvent.ActionDeclared, is GameEvent.Blocked, is GameEvent.Challenged,
        is GameEvent.ChallengeRevealed, is GameEvent.InfluenceLost,
        is GameEvent.PlayerEliminated, is GameEvent.CoinsTransferred,
        is GameEvent.Exchanged, is GameEvent.GameEnded,
        -> true
        else -> false
    }

/** The most recent event worth recapping in plain words (skips bookkeeping noise). */
internal fun mostRecentRecapEvent(state: GameUiState): GameEvent? = state.recentEvents.lastOrNull { isRecapWorthy(it) }

/** The last [n] recap-worthy events, oldest first. Used by IdleDock feed. */
internal fun lastNRecapEvents(
    state: GameUiState,
    n: Int,
): List<GameEvent> = state.recentEvents.filter { isRecapWorthy(it) }.takeLast(n)

/** Resolve the primary + secondary display names a recap line needs for [event]. */
internal fun recapNames(
    event: GameEvent,
    state: GameUiState,
): Pair<String, String?> {
    fun n(id: PlayerId) = personaNameOrDefault(id, state)
    return when (event) {
        is GameEvent.ActionDeclared -> n(event.actor) to null
        is GameEvent.Blocked -> n(event.blocker) to null
        is GameEvent.Challenged -> n(event.challenger) to n(event.target)
        is GameEvent.ChallengeRevealed -> n(event.player) to null
        is GameEvent.InfluenceLost -> n(event.player) to null
        is GameEvent.PlayerEliminated -> n(event.player) to null
        is GameEvent.CoinsTransferred -> n(event.from) to n(event.to)
        is GameEvent.Exchanged -> n(event.actor) to null
        is GameEvent.GameEnded -> n(event.winner) to null
        else -> "" to null
    }
}

/**
 * Top-of-screen recap strip — always a single compact line to preserve hand visibility.
 *
 * During PickAction with narrative mode on, swaps to show the last bot chat message so
 * the player can't miss what was said to them while bots were taking turns. Falls back
 * to the normal PICHLA DAV game-event recap when there's no chat. Full thread is in DARBAR.
 *
 * This MUST stay single-line: any vertical expansion here directly reduces the weight(1f)
 * middle Column, squishing YourHandPanel to zero height on small phones.
 */
@Composable
internal fun RecapRail(
    state: GameUiState,
    gamePhase: GamePhase,
    modifier: Modifier = Modifier,
) {
    val voice = LocalGaddiVoice.current
    val isPickAction = gamePhase is GamePhase.PickAction

    // During the player's action phase in narrative mode, surface the last bot chat first —
    // the player may have been distracted during bot turns and missed what was said to them.
    val lastBotMsg =
        if (isPickAction && state.narrativeEnabled) {
            state.chatFeed.lastOrNull { !it.fromPlayer && !it.isNarrator }
        } else {
            null
        }

    if (lastBotMsg != null) {
        val senderPlayer = state.view.players.firstOrNull { it.seatIndex == lastBotMsg.senderSeat }
        val senderName = senderPlayer?.let { state.opponentPersonas[it.id]?.name } ?: stringResource(Res.string.game_bot_fallback_name)
        val chatLine = "\"${lastBotMsg.body}\""
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .clip(Squircle(GaddiRadii.sm))
                    .background(BrandTokens.TeakDark.copy(alpha = 0.85f))
                    .border(
                        GaddiDimens.stroke_hairline,
                        GaddiNeutrals.TextMuted.copy(alpha = 0.6f),
                        Squircle(GaddiRadii.sm),
                    ).padding(horizontal = GaddiDimens.space_sm, vertical = 5.dp)
                    .semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Darbar — $senderName: ${lastBotMsg.body}"
                    },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_sm),
        ) {
            Box(
                modifier =
                    Modifier
                        .clip(Squircle(GaddiRadii.xs))
                        .background(GaddiNeutrals.TextMuted.copy(alpha = 0.25f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "💬 $senderName",
                    style = GaddiType.label_micro.copy(letterSpacing = 0.6.sp),
                    color = BrandTokens.GoldAntique,
                    maxLines = 1,
                )
            }
            Text(
                text = chatLine,
                style = GaddiType.label_sm,
                color = GaddiNeutrals.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    // No bot chat to surface — show the normal game-event recap.
    val event = mostRecentRecapEvent(state) ?: return
    val (actor, other) = recapNames(event, state)
    val line = voice.recap(event, actor, other) ?: return
    val label = if (isPickAction) stringResource(Res.string.game_recap_label_pichla_dav) else voice.recapLabel

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(Squircle(GaddiRadii.sm))
                .background(BrandTokens.TeakDark.copy(alpha = 0.85f))
                .border(
                    GaddiDimens.stroke_hairline,
                    BrandTokens.BrassDark.copy(alpha = 0.5f),
                    Squircle(GaddiRadii.sm),
                ).padding(horizontal = GaddiDimens.space_sm, vertical = 5.dp)
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "$label: $line"
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_sm),
    ) {
        Box(
            modifier =
                Modifier
                    .clip(Squircle(GaddiRadii.xs))
                    .background(BrandTokens.BrassDark.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = label,
                style = GaddiType.label_micro.copy(letterSpacing = 0.6.sp),
                color = BrandTokens.GoldAntique,
                maxLines = 1,
            )
        }
        Text(
            text = line,
            style = GaddiType.label_sm,
            color = GaddiNeutrals.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ─────────────────────────── StatusSpine ───────────────────────────

@Composable
internal fun StatusSpineBar(
    state: GameUiState,
    gamePhase: GamePhase,
    modifier: Modifier = Modifier,
) {
    val humanSeat = state.view.viewer
    val voice = LocalGaddiVoice.current
    val (text, tone) = deriveSpineTextAndTone(state, gamePhase, humanSeat, voice)
    // A11y (M3 §1): the spine is the running narration of the table — whose turn it is and what
    // just happened. Mark it a polite live region so a screen reader announces each change without
    // the player having to hunt for it, and carry the text as the spoken description.
    StatusSpine(
        text = text,
        tone = tone,
        modifier =
            modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
    )
}

// ─────────────────────────── EngravedTurnHeader (AAA FOCUS rebuild) ───────────
// FOCUS/GUIDED chrome: the fat gold StatusSpine bar dissolves into a single engraved
// small-caps mono turn line + a hairline gradient rule — no filled bar, no box. A
// brass coin readout (the player's own treasury) and a subtle menu affordance (opens
// the NIYAM rules gazette) sit at the trailing edge.

@Composable
internal fun EngravedTurnHeader(
    state: GameUiState,
    gamePhase: GamePhase,
    onOpenGazette: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val humanSeat = state.view.viewer
    val voice = LocalGaddiVoice.current
    val (text, tone) = deriveSpineTextAndTone(state, gamePhase, humanSeat, voice)
    val accent =
        when (tone) {
            SpineTone.Info -> BrandTokens.BrassAged
            SpineTone.Pending -> BrandTokens.PendingAmber
            SpineTone.Danger -> BrandTokens.StampRed
            SpineTone.Gold -> BrandTokens.GoldAntique
        }
    val niyamGazetteDesc = stringResource(Res.string.a11y_niyam_rules_gazette)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = text
                    },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text.uppercase(),
                style =
                    GaddiType.numeral_sm.dmMono().copy(
                        letterSpacing = 2.5.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Player's own treasury — the one persistent number FOCUS still needs visible.
                Text(
                    text = "${state.view.myCoins} ◈",
                    style = GaddiType.numeral_sm,
                    color = BrandTokens.GoldAntique.copy(alpha = 0.85f),
                    maxLines = 1,
                )
                Box(
                    modifier =
                        Modifier
                            .size(26.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(BrandTokens.GoldAntique.copy(alpha = 0.16f), Color.Transparent),
                                ),
                            ).clickable(
                                indication = null,
                                interactionSource =
                                    remember {
                                        androidx.compose.foundation.interaction
                                            .MutableInteractionSource()
                                    },
                                onClick = onOpenGazette,
                            ).semantics { contentDescription = niyamGazetteDesc },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("☰", style = GaddiType.label_sm.copy(fontSize = 13.sp), color = BrandTokens.BrassAged)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, BrandTokens.GoldAntique.copy(alpha = 0.5f), Color.Transparent),
                        ),
                    ),
        )
    }
}

internal fun deriveSpineTextAndTone(
    state: GameUiState,
    gamePhase: GamePhase,
    humanSeat: PlayerId,
    voice: GaddiVoice,
): Pair<String, SpineTone> =
    when (gamePhase) {
        is GamePhase.Idle -> {
            val actorId =
                when (val phase = state.view.phase) {
                    is PhaseView.Turn -> phase.actor
                    is PhaseView.Reactions -> phase.actor
                    is PhaseView.InfluenceLoss -> phase.loser
                    is PhaseView.Exchange -> phase.actor
                    else -> null
                }
            val actorName = actorId?.let { personaNameOrDefault(it, state) } ?: actorName(state)
            voice.opponentActing(actorName) to SpineTone.Info
        }
        is GamePhase.PickAction -> {
            if (state.view.myCoins >= state.view.config.forcedCoupThreshold) {
                "${voice.forcedCoup} (${state.view.myCoins} coins)" to SpineTone.Danger
            } else {
                voice.yourTurn to SpineTone.Gold
            }
        }
        is GamePhase.PickTarget -> {
            val verb = targetVerb(gamePhase.action)
            val role = Rules.claimedRole(gamePhase.action)
            val roleStr = if (role != null) " (claiming ${roleLabel(role)})" else ""
            "${actionName(gamePhase.action)} — choose who to $verb.$roleStr" to SpineTone.Gold
        }
        is GamePhase.Confirm -> {
            val targetName = gamePhase.target?.let { personaNameOrDefault(it, state, voice.selfName) }
            val summary = confirmSummary(gamePhase.action, gamePhase.target, targetName)
            "$summary — Declare or Cancel." to SpineTone.Gold
        }
        is GamePhase.ReactionWindow -> {
            when (gamePhase.step) {
                ReactionStep.CHALLENGE_ACTION -> {
                    "${voice.challengePrompt} — ${actionName(gamePhase.action)}" to SpineTone.Pending
                }
                ReactionStep.BLOCK -> {
                    voice.blockPrompt to SpineTone.Pending
                }
                ReactionStep.CHALLENGE_BLOCK -> {
                    val blockerName = gamePhase.blocker?.let { personaNameOrDefault(it, state) } ?: "Someone"
                    "$blockerName blocks. ${voice.challengePrompt}" to SpineTone.Pending
                }
            }
        }
        is GamePhase.LoseInfluence -> {
            voice.loseInfluence to SpineTone.Danger
        }
        is GamePhase.Exchange -> {
            voice.exchange to SpineTone.Gold
        }
        is GamePhase.InvestigatePeek -> {
            val targetName = personaNameOrDefault(gamePhase.target, state)
            val seen = gamePhase.peekedRole?.let { " You saw ${roleLabel(it)}." } ?: ""
            "JAANCH — $targetName's card.$seen Spike it or leave it?" to SpineTone.Gold
        }
        is GamePhase.GameOver -> {
            val humanSeatIdx = humanSeat.raw
            if (gamePhase.winnerSeat == humanSeatIdx) {
                voice.youWin to SpineTone.Gold
            } else {
                val winnerName = personaNameOrDefault(com.kursi.engine.PlayerId(gamePhase.winnerSeat), state)
                voice.opponentWins(winnerName) to SpineTone.Info
            }
        }
    }
