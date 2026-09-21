package com.kursi.feature.game.board

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.designsystem.moment.reportAnchor
import com.kursi.engine.*
import com.kursi.feature.game.*
import com.kursi.feature.game.coach.*
import com.kursi.feature.game.overlays.*

// ─────────────────────────── Opponent Arc (FlowRow on the felt) ────────────
// Opponents are compact chips (~130-170dp wide) arranged in a centered FlowRow.
// Wraps for 5+ players. At 2p, one chip centered; at 10p, two rows of smaller chips.

@Composable
internal fun OpponentArc(
    state: GameUiState,
    gamePhase: GamePhase,
    onLocalPhase: (GamePhase?) -> Unit,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val opponents = state.view.players.filter { it.id != state.view.viewer }

    // Use FlowRow to wrap: wraps naturally for any player count
    // FlowRow is experimental in compose; use a wrapping Row simulation with chunked rows
    val chipCount = opponents.size
    // For very large player counts (9 opponents), split into rows of max 5
    val chunkSize =
        when {
            chipCount <= 4 -> chipCount
            chipCount <= 8 -> (chipCount + 1) / 2
            else -> 5
        }
    val rows = opponents.chunked(chunkSize)
    val perRow = rows.maxOfOrNull { it.size } ?: 1
    val gap = 8.dp
    // Larger vertical gutter between stacked rows: the acting-plate scale (1.045x),
    // its warm rim-glow (drawn 6dp beyond bounds) and the contact shadow all bleed
    // outside a plate's box. An 8dp gap let a row-1 footer collide with the row-2 top
    // border; this clears that overlap so the two rows read as distinct bands.
    val rowGap = 18.dp

    BoxWithConstraints(modifier = modifier) {
        // Responsive plate width: divide the felt width evenly among the widest row,
        // clamp to a generous-but-legible band. 2p → wide & roomy, 10p → compact.
        // Sparse tables (1–2 opponents) get a wider ceiling so a lone plate doesn't
        // float tiny in a large felt; dense tables stay compact.
        val available = maxWidth
        val rawWidth = (available - gap * (perRow - 1)) / perRow
        val maxPlate = if (chipCount <= 2) 300.dp else 232.dp
        val plateWidth = rawWidth.coerceIn(116.dp, maxPlate)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            rows.forEach { rowOpponents ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowOpponents.forEach { opp ->
                        // Every plate gets a fixed slot width so a short last row (e.g. 4
                        // plates under a row of 5 at 10p) column-aligns under the first row
                        // instead of re-centering at a different stagger.
                        Box(modifier = Modifier.width(plateWidth)) {
                            OpponentChipItem(
                                opp = opp,
                                state = state,
                                gamePhase = gamePhase,
                                onLocalPhase = onLocalPhase,
                                onShowChit = onShowChit,
                                plateWidth = plateWidth,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────── Opponent Seat Arc (AAA FOCUS rebuild) ────────────
// FOCUS/GUIDED: opponents sit directly on the felt as brass token seats in a single
// space-between row — the mockup's "seats", not the ANALYST enamel-plate grid. The
// middle seat nudges up so the row reads as a gentle arc. ANALYST is untouched —
// this simply delegates to the existing [OpponentArc] plate grid for that layer.

@Composable
internal fun OpponentSeatArc(
    state: GameUiState,
    gamePhase: GamePhase,
    onLocalPhase: (GamePhase?) -> Unit,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (state.densityLayer == DensityLayer.ANALYST) {
        OpponentArc(state = state, gamePhase = gamePhase, onLocalPhase = onLocalPhase, onShowChit = onShowChit, modifier = modifier)
        return
    }
    val opponents = state.view.players.filter { it.id != state.view.viewer }
    val midIdx = if (opponents.size in 2..4) (opponents.size - 1) / 2 else -1
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        opponents.forEachIndexed { idx, opp ->
            OpponentChipItem(
                opp = opp,
                state = state,
                gamePhase = gamePhase,
                onLocalPhase = onLocalPhase,
                onShowChit = onShowChit,
                seatStyle = true,
                nudgedUp = idx == midIdx,
            )
        }
    }
}

/** Convert a persona display name to its ID (e.g. "Netaji Vachan" → "netaji_vachan"). */
internal fun findPersonaIdByName(name: String): String = name.lowercase().replace(" ", "_")

@Composable
internal fun OpponentChipItem(
    opp: OpponentView,
    state: GameUiState,
    gamePhase: GamePhase,
    onLocalPhase: (GamePhase?) -> Unit,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    plateWidth: androidx.compose.ui.unit.Dp = 176.dp,
    /** AAA FOCUS rebuild: an unboxed brass-rimmed circular seat token instead of the enamel
     * plate — dissolves the box while reusing every bit of data/click wiring below. */
    seatStyle: Boolean = state.densityLayer != DensityLayer.ANALYST,
    nudgedUp: Boolean = false,
) {
    val phase = state.view.phase

    // Derive lastAction from events
    val lastActionDeclared =
        state.recentEvents
            .filterIsInstance<GameEvent.ActionDeclared>()
            .lastOrNull { it.actor == opp.id }
    val lastAction: String? = lastActionDeclared?.let { actionName(it.action) }

    fun roleGlyphChar(role: Role): String =
        when (role) {
            Role.NETA -> "⚖"
            Role.BHAI -> "🔪"
            Role.BABU -> "🗄"
            Role.JUGAADU -> "🔧"
            Role.VAKIL -> "§"
            Role.PATRAKAAR -> "📰"
        }

    // ── LIVE/pending claim ────────────────────────────────────────────
    // A claim is "live" only while THIS seat is the actor in an in-flight reaction
    // window (someone may still challenge it). Outside that, the most-recent declared
    // claim becomes part of the STANDING summary below rather than a pulsing live claim.
    val isActorInReactions = phase is PhaseView.Reactions && phase.actor == opp.id
    val liveClaimedRole: Role? = if (isActorInReactions) phase.claimedRole else null
    val claim: String? = liveClaimedRole?.let { "${roleGlyphChar(it)} claims ${roleLabel(it)}" }

    // ── STANDING claim summary (persists across the whole game) ───────
    val claimSummary =
        remember(state.recentEvents, opp.id) {
            deriveClaimSummary(opp, state.recentEvents)
        }
    val standingRole = liveClaimedRole ?: claimSummary.standingRole
    val claimTrail: String? =
        claimSummary.standingRole?.let { role ->
            "${roleGlyphChar(role)} ${claimSummary.trail ?: "claimed ${roleLabel(role)}"}"
        }

    // ── Suspicion / bluff-odds on the standing claim ─────────────────
    // Blend the deck-math BluffOdds with this seat's INFERRED bluffRate (from the coach's
    // public-info belief) so a serial-bluffer's claim reads hotter than the cards alone imply.
    val oppBluffRate = state.insightFor(opp.id)?.bluffRate
    // DENSITY GATE (spec §3): suspicion pips are ANALYST-only — hidden in FOCUS and GUIDED.
    val suspicion =
        if (state.densityLayer == DensityLayer.ANALYST) {
            remember(state.view, opp.id, standingRole, oppBluffRate) {
                deriveSuspicion(opp, standingRole, state.view, oppBluffRate)
            }
        } else {
            null
        }

    // Derive ChipState
    val chipState: ChipState =
        when {
            opp.eliminated -> ChipState.Eliminated
            gamePhase is GamePhase.PickTarget -> {
                if (isValidTarget(opp, gamePhase.action, state)) ChipState.ValidTarget else ChipState.Idle
            }
            phase is PhaseView.Turn && phase.actor == opp.id -> ChipState.Acting
            phase is PhaseView.Reactions && phase.toRespond == opp.id -> ChipState.Responding
            else -> ChipState.Idle
        }

    val alphaValue = if (gamePhase is GamePhase.PickTarget && chipState != ChipState.ValidTarget && !opp.eliminated) 0.4f else 1.0f

    // Persona data
    val persona = state.opponentPersonas[opp.id]
    val plateName = personaNameOrDefault(opp.id, state, LocalKursiVoice.current.selfName)
    val plateColor = if (persona != null) Color(persona.seatColorArgb) else KursiSeatColors[opp.seatIndex]

    // ── Speech ribbon state ──────────────────────────────────────────
    var ribbonText by remember { mutableStateOf<String?>(null) }
    val personaId = persona?.name?.let { findPersonaIdByName(it) }

    // ── Darbar ephemeral chat bubble: last message from this seat (bot only) ──
    val lastChatForSeat =
        state.chatFeed
            .lastOrNull { msg -> msg.senderSeat == opp.seatIndex && !msg.fromPlayer && !msg.isNarrator }
    var chatBubbleText by remember { mutableStateOf<String?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    LaunchedEffect(lastChatForSeat?.id) {
        if (lastChatForSeat != null) {
            chatBubbleText = lastChatForSeat.body
            isSpeaking = true
            kotlinx.coroutines.delay(5500)
            chatBubbleText = null
            isSpeaking = false
        }
    }

    val lastEventForOpp =
        state.recentEvents
            .lastOrNull { event ->
                when (event) {
                    is GameEvent.ActionDeclared -> event.actor == opp.id
                    is GameEvent.PlayerEliminated -> event.player == opp.id
                    is GameEvent.Challenged -> event.target == opp.id
                    is GameEvent.ChallengeRevealed -> event.player == opp.id
                    else -> false
                }
            }

    val voice = LocalKursiVoice.current
    LaunchedEffect(lastEventForOpp) {
        if (lastEventForOpp != null && personaId != null) {
            val barkEvent =
                when (lastEventForOpp) {
                    is GameEvent.ActionDeclared -> "act"
                    is GameEvent.PlayerEliminated -> "lose"
                    is GameEvent.Challenged -> "challenged"
                    is GameEvent.ChallengeRevealed -> if (lastEventForOpp.hadRole) "act" else "bluff"
                    else -> null
                }
            if (barkEvent != null) {
                val bark = voice.personaBark(personaId, barkEvent)
                if (bark.isNotEmpty()) {
                    ribbonText = bark
                    kotlinx.coroutines.delay(1500)
                    ribbonText = null
                }
            }
        }
    }

    // Role visual for crest (null if we don't know their role — they're face-down)
    val roleVisual: RoleVisual? = opp.faceUpRoles.firstOrNull()?.let { KursiColors.forRole(it) }

    // P9: capture the plate's bounds in root coordinates so the dossier popover can
    // anchor to the tapped plate with a caret instead of floating dead-centre.
    var plateBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    // Build + show this opponent's dossier chit (richer bio + rivalry + legal moves).
    val showDossier: () -> Unit = {
        if (!opp.eliminated) {
            val pid = persona?.name?.let { findPersonaIdByName(it) }
            val dossierLine = pid?.let { voice.personaBio(it) } ?: ""
            val rivalryLine = pid?.let { voice.personaRivalry(it) } ?: ""
            val legalMoves =
                state.legalIntents
                    .filterIsInstance<Intent.DeclareAction>()
                    .filter { Rules.targetOf(it.action) == opp.id }
                    .map { actionHindiName(it.action) }
                    .distinct()
            // Lead with the real intelligence from the decision-coach's PUBLIC-info belief.
            val intel = state.insightFor(opp.id)?.let { DossierIntel.from(it) }
            onShowChit(
                ChitContent.Dossier(
                    opponentName = plateName,
                    opponentCoins = opp.coins,
                    faceDownCount = opp.faceDownCount,
                    faceUpRoles = opp.faceUpRoles,
                    lastActionText = lastAction,
                    personaDossierLine = dossierLine,
                    personaRivalryLine = rivalryLine,
                    legalMovesAgainst = legalMoves,
                    intel = intel,
                ),
                plateBounds,
            )
        }
    }

    Box(
        modifier =
            Modifier
                .onGloballyPositioned { coords -> plateBounds = coords.boundsInRoot() }
                // M4 §1: report this plate's measured bounds so moments fire at the REAL seat.
                .reportAnchor(
                    com.kursi.designsystem.moment.AnchorKey
                        .Seat(opp.seatIndex),
                ).opponentPlateSemantics(
                    name = plateName,
                    coins = opp.coins,
                    influenceAlive = opp.faceDownCount,
                    influenceLost = opp.faceUpRoles.size,
                    claim = claimTrail ?: claim,
                    eliminated = opp.eliminated,
                    isValidTarget = chipState == ChipState.ValidTarget,
                ),
    ) {
        val onSeatClick = {
            if (gamePhase is GamePhase.PickTarget && chipState == ChipState.ValidTarget) {
                // During target-select, a TAP picks this plate as the target.
                onLocalPhase(GamePhase.Confirm(gamePhase.action, opp.id))
            } else {
                // Outside target-select, a tap also opens the dossier (long-press is the
                // canonical inspect, but a tap here is intuitive when there's no target duty).
                showDossier()
            }
        }
        if (seatStyle) {
            OpponentSeatToken(
                name = plateName,
                seatColor = plateColor,
                roleColor = roleVisual?.color,
                role = roleVisual?.role,
                influenceAlive = opp.faceDownCount,
                influenceLost = opp.faceUpRoles.size,
                claim = claim,
                claimSummary = claimTrail,
                claimCaught = claimSummary.caught,
                claimProven = claimSummary.proven,
                state = chipState,
                nudgedUp = nudgedUp,
                modifier =
                    Modifier
                        .alpha(alphaValue)
                        .speakingSeatGlow(accent = plateColor, active = isSpeaking),
                onClick = onSeatClick,
                onLongClick = { showDossier() },
            )
        } else {
            OpponentPlate(
                name = plateName,
                seatColor = plateColor,
                roleColor = roleVisual?.color,
                role = roleVisual?.role,
                coins = opp.coins,
                influenceAlive = opp.faceDownCount,
                influenceLost = opp.faceUpRoles.size,
                claim = claim,
                claimSummary = claimTrail,
                claimCaught = claimSummary.caught,
                claimProven = claimSummary.proven,
                suspicionPips = suspicion?.first,
                suspicionLabel = suspicion?.second,
                lastAction = lastAction,
                state = chipState,
                modifier =
                    Modifier
                        .alpha(alphaValue)
                        // DARBAR: speaking glow — active while chat bubble is showing for this seat
                        .speakingSeatGlow(accent = plateColor, active = isSpeaking),
                plateWidth = plateWidth,
                onClick = onSeatClick,
                // Long-press = ALWAYS inspect the dossier, even mid target-select.
                onLongClick = { showDossier() },
            )
        }
        // DARBAR: ephemeral chat bubble — shown above the plate for ~3.2s, clears automatically.
        // Rendered above the bark ribbon so chat text reads first; bark ribbon stays below.
        // DENSITY GATE: Darbar chat is ANALYST-only (spec §3), same as the panel/FAB.
        val chatBubble = chatBubbleText
        if (chatBubble != null && state.narrativeEnabled && state.densityLayer == DensityLayer.ANALYST) {
            val chatPersonaMonogram = persona?.monogram
            SpeechBubble(
                speakerName = plateName,
                text = chatBubble,
                accent = plateColor,
                fromPlayer = false,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-52).dp)
                        .widthIn(max = 200.dp),
                monogram = chatPersonaMonogram,
                emphatic =
                    lastChatForSeat?.tone in
                        listOf(
                            com.kursi.feature.game.narrative.MessageTone.HOSTILE,
                            com.kursi.feature.game.narrative.MessageTone.BOAST,
                            com.kursi.feature.game.narrative.MessageTone.PANICKED,
                        ),
            )
        }
        // Speech ribbon overlay
        val ribbon = ribbonText
        if (ribbon != null) {
            SpeechRibbon(
                text = ribbon,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-8).dp),
            )
        }
        // M6e TEAM KHEL — a faction badge on the plate corner (only in the TEAMS variant).
        val teamId = opp.team
        if (teamId != null) {
            TeamBadgeChip(
                label = voice.teamBadge(teamId),
                teamId = teamId,
                modifier = Modifier.align(Alignment.TopStart).offset(x = 4.dp, y = (-6).dp),
            )
        }
    }
}

/** M6e — a small faction badge chip overlaid on an opponent plate in the TEAMS variant. */
@Composable
internal fun TeamBadgeChip(
    label: String,
    teamId: Int,
    modifier: Modifier = Modifier,
) {
    val hue = if (teamId == 0) BrandTokens.GoldAntique else BrandTokens.StampRed
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(3.dp))
                .background(BrandTokens.TeakDark.copy(alpha = 0.92f))
                .border(0.8.dp, hue.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = label,
            style = KursiType.caption.copy(fontSize = 7.sp, letterSpacing = 0.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = hue,
        )
    }
}

@Composable
internal fun SpeechRibbon(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .widthIn(max = 200.dp)
                .clip(Squircle(KursiDimens.r_sm))
                .background(BrandTokens.BrassAged.copy(alpha = 0.95f))
                .border(KursiDimens.stroke_hairline, BrandTokens.GoldAntique, Squircle(KursiDimens.r_sm))
                .padding(horizontal = KursiDimens.space_sm, vertical = KursiDimens.space_xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "\"$text\"",
            style = KursiType.label_micro.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
            color = BrandTokens.TeakDark,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
