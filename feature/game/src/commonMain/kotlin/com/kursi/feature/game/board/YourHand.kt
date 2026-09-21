package com.kursi.feature.game.board

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.kursi.designsystem.*
import com.kursi.designsystem.moment.reportAnchor
import com.kursi.engine.*
import com.kursi.feature.game.*
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.game_your_hand_label
import org.jetbrains.compose.resources.stringResource

// ─────────────────────────── Your Hand Panel ───────────────────────────

@Composable
internal fun YourHandPanel(
    state: GameUiState,
    gamePhase: GamePhase,
    humanSeat: PlayerId,
    onAction: (GameAction) -> Unit,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cardTilt")
    val tiltAngle by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2000),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "tiltAngle",
    )

    val lossIntents =
        if (gamePhase is GamePhase.LoseInfluence) {
            state.legalIntents.filterIsInstance<Intent.ChooseInfluenceToLose>()
        } else {
            emptyList()
        }

    val claimedRole: Role? =
        when (gamePhase) {
            is GamePhase.PickTarget -> Rules.claimedRole(gamePhase.action)
            is GamePhase.Confirm -> Rules.claimedRole(gamePhase.action)
            else -> null
        }

    val myTurn =
        gamePhase is GamePhase.PickAction ||
            gamePhase is GamePhase.PickTarget ||
            gamePhase is GamePhase.Confirm

    // AAA FOCUS rebuild: FOCUS/GUIDED drop the decoPanel box entirely — the two held cards
    // fan out at the bottom edge with only their own drop shadow for depth (mockup: "real
    // held cards, fanned"), a wider ∓8° splay, tighter overlap, no header row (the coin
    // readout already lives in the engraved top chrome). ANALYST keeps today's boxed panel.
    val focusStyle = state.densityLayer != DensityLayer.ANALYST

    val anchoredModifier =
        modifier
            .fillMaxWidth()
            // M4 §1: the hand is seat 0's anchor + the off-table entry origin for moments.
            .reportAnchor(
                com.kursi.designsystem.moment.AnchorKey
                    .Seat(humanSeat.raw),
            ).reportAnchor(com.kursi.designsystem.moment.AnchorKey.Hand)

    if (focusStyle) {
        Box(modifier = anchoredModifier, contentAlignment = Alignment.BottomCenter) {
            HandCardsRow(
                state = state,
                gamePhase = gamePhase,
                humanSeat = humanSeat,
                onAction = onAction,
                onShowChit = onShowChit,
                claimedRole = claimedRole,
                lossIntents = lossIntents,
                tiltAngle = tiltAngle,
                overlap = true,
                fanDegrees = 8f,
            )
        }
    } else {
        Box(modifier = anchoredModifier.decoPanel(lifted = myTurn)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.game_your_hand_label),
                        style = KursiType.caption,
                        color = KursiNeutrals.TextMuted,
                    )
                    CoinPill(count = state.view.myCoins)
                }
                HandCardsRow(
                    state = state,
                    gamePhase = gamePhase,
                    humanSeat = humanSeat,
                    onAction = onAction,
                    onShowChit = onShowChit,
                    claimedRole = claimedRole,
                    lossIntents = lossIntents,
                    tiltAngle = tiltAngle,
                    overlap = false,
                    fanDegrees = 6f,
                )
            }
        }
    }
}

/**
 * Alive + lost cards, splayed in a fan. Shared by the boxed ANALYST panel and the unboxed
 * FOCUS/GUIDED held-card composition — [overlap]/[fanDegrees] are the only visual knobs that
 * differ; the interaction/data wiring (loss-choice tap, long-press inspect) is identical.
 */
@Composable
private fun HandCardsRow(
    state: GameUiState,
    gamePhase: GamePhase,
    humanSeat: PlayerId,
    onAction: (GameAction) -> Unit,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit,
    claimedRole: Role?,
    lossIntents: List<Intent.ChooseInfluenceToLose>,
    tiltAngle: Float,
    overlap: Boolean,
    fanDegrees: Float,
) {
    Row(
        horizontalArrangement = if (overlap) Arrangement.spacedBy((-28).dp) else Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth().let { if (overlap) it.wrapContentWidth() else it },
    ) {
        // Alive cards — give each card room to breathe at Large size.
        // Long-press = inspect (Identity chit). During LoseInfluence, tap = reveal.
        // DEPTH3D: the hand reads as a held fan — cards splay symmetrically with a
        // slight rotationZ + a perspective rotationY tilt, and the RELEVANT card
        // (the role this turn's claim needs, or a loss-choice) straightens upright
        // and lifts toward the viewer so it pops out of the spread.
        val handCount = state.view.myInfluence.size
        state.view.myInfluence.forEachIndexed { idx, role ->
            val lifted = claimedRole == role
            var cardBounds by remember(role, idx) { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
            val isLossChoice = gamePhase is GamePhase.LoseInfluence && idx < lossIntents.size

            // Fan geometry: spread cards around the row centre.
            val centerIdx = (handCount - 1) / 2f
            val rel = idx - centerIdx
            val fanZ = if (handCount > 1) rel * fanDegrees else 0f
            // The relevant card un-fans, rises and tilts to face the player.
            val relevant = lifted || isLossChoice
            // Spring-physics settle (spec §7 juice) — the relevant card springs into
            // place like a physically held card, not a linear tween. Reduced motion
            // collapses to an instant snap (no time-critical info hides behind motion).
            val reducedMotion = LocalReducedMotion.current
            val fanSpec: AnimationSpec<Float> = if (reducedMotion) tween(0) else KursiMotion.settle()
            val fanTarget by animateFloatAsState(
                targetValue = if (relevant) 0f else fanZ,
                animationSpec = fanSpec,
                label = "handFanZ",
            )
            val liftTarget by animateFloatAsState(
                targetValue = if (relevant) -10f else 0f,
                animationSpec = fanSpec,
                label = "handFanLift",
            )

            val baseGraphics =
                Modifier
                    .onGloballyPositioned { cardBounds = it.boundsInRoot() }
                    .graphicsLayer {
                        cameraDistance = 12f * density
                        // Living breathing perspective tilt + the static fan rotation.
                        rotationY = tiltAngle * 0.5f
                        rotationZ = fanTarget
                        translationY = liftTarget * density
                    }
            val cardModifier =
                if (isLossChoice) {
                    baseGraphics
                        .clip(
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(KursiRadii.sm),
                        ).border(
                            width = 2.dp,
                            color = KursiSemantics.Danger,
                            shape =
                                androidx.compose.foundation.shape
                                    .RoundedCornerShape(KursiRadii.sm),
                        ).loseInfluenceCardSemantics(role)
                } else {
                    baseGraphics.handCardSemantics(role = role, faceUp = false)
                }

            val lossIntent = if (isLossChoice) lossIntents[idx] else null
            RoleCard(
                role = role,
                size = CardSize.Large,
                lost = false,
                lifted = lifted,
                modifier = cardModifier,
                onClick =
                    if (lossIntent != null) {
                        { onAction(GameAction.Submit(Intent.ChooseInfluenceToLose(humanSeat, lossIntent.card))) }
                    } else {
                        null
                    },
                onLongClick = { onShowChit(ChitContent.Identity(role), cardBounds) },
            )
        }

        // Lost/revealed cards
        state.view.myFaceUp.forEach { role ->
            RoleCard(
                role = role,
                size = CardSize.Large,
                lost = true,
            )
        }
    }
}
