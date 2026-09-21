package com.kursi.feature.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.engine.*
import com.kursi.feature.game.board.*
import com.kursi.feature.game.docks.*
import com.kursi.feature.game.overlays.*
import com.kursi.feature.game.status.*
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.a11y_darbar_close
import kursi.core.designsystem.generated.resources.a11y_darbar_open
import kursi.core.designsystem.generated.resources.game_actor_claims
import org.jetbrains.compose.resources.stringResource

// ─────────────────────────── deriveGamePhase ───────────────────────────

fun deriveGamePhase(
    uiState: GameUiState,
    localPhase: GamePhase?,
): GamePhase {
    if (uiState.isGameOver) {
        return GamePhase.GameOver(uiState.winnerSeat ?: 0)
    }

    val phase = uiState.view.phase

    if (phase is PhaseView.Over) {
        return GamePhase.GameOver(phase.winner.raw)
    }

    if (phase is PhaseView.InfluenceLoss && uiState.isHumanTurn) {
        return GamePhase.LoseInfluence
    }

    if (phase is PhaseView.Exchange && uiState.isHumanTurn) {
        return GamePhase.Exchange(drawn = emptyList())
    }

    if (phase is PhaseView.InvestigatePeek && uiState.isHumanTurn) {
        // The examiner's private peek (examinedCard) is non-null only in the examiner's own view.
        return GamePhase.InvestigatePeek(
            target = phase.target,
            peekedRole = phase.examinedCard?.role,
        )
    }

    if (phase is PhaseView.Reactions && uiState.isHumanTurn) {
        return GamePhase.ReactionWindow(
            step = phase.step,
            actor = phase.actor,
            action = phase.action,
            claimedRole = phase.claimedRole,
            blocker = phase.blocker,
            blockRole = phase.blockRole,
            myLegalResponses = uiState.legalIntents,
        )
    }

    if (uiState.isHumanTurn && phase is PhaseView.Turn) {
        // Keep localPhase if it's still relevant
        if (localPhase is GamePhase.PickTarget || localPhase is GamePhase.Confirm) {
            return localPhase
        }
        return GamePhase.PickAction
    }

    return GamePhase.Idle
}

// ─────────────────────────── GameScreen ───────────────────────────

@Composable
fun GameScreen(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
    initialLocalPhase: GamePhase? = null,
    // First-run onboarding override. In the real app this is null, so the
    // SwearingInPrimer coachmark fires off PrimerPrefs.hasSeenPrimer. The static
    // render harness passes `false` so the actual in-game table (turn spine,
    // opponent chips, hand, action bar, Roznamcha) is captured un-dimmed instead
    // of being washed out behind the centered primer modal.
    showPrimerOverride: Boolean? = null,
    // Render-harness only: pre-open a WhisperChit (floating, un-anchored) so a screenshot
    // can verify chit/dossier content renders rich and fits. Null in the real app.
    initialChit: ChitContent? = null,
    // DECISION-COACH TOGGLE. Wired to GameViewModel.toggleCoach() in the real app; null in
    // the render harness (the toggle is then invisible so the fixture is deterministic).
    onToggleCoach: (() -> Unit)? = null,
    // M3 FEEDBACK. Master sound/haptics gate (AppPrefs.soundFlow). When false the moment
    // overlay stays silent. Defaults false so the static render harness never tries to play.
    soundEnabled: Boolean = false,
    // M3 REDUCED-MOTION. AppPrefs.reducedMotionFlow. When true, moments collapse to the
    // overlay's static/120ms path instead of the full stamp theatre.
    reducedMotion: Boolean = false,
    // Render-harness only: force the pass-and-play handoff guard visible so a screenshot can verify
    // it. Null in the real app (the guard shows itself on a genuine seat handoff).
    forceHandoffOverride: Boolean? = null,
    // M6e TAMASHA / DEMO. When true the table shows a "watch only" banner (the advisor is auto-playing
    // the human seat, so the action bar is informational — the player is a spectator).
    spectator: Boolean = false,
    // The human player's chosen display name — shown in moment beats and chat.
    humanDisplayName: String = "Khiladi",
    // Render-harness only: pre-open the DARBAR chat overlay so a screenshot can verify its
    // content renders correctly. False in the real app (the panel opens via the chat FAB).
    initialDarbarOpen: Boolean = false,
) {
    var localPhase by remember { mutableStateOf<GamePhase?>(initialLocalPhase) }
    val gamePhase = deriveGamePhase(state, localPhase)

    // Reset localPhase when engine moves away from Turn phase
    LaunchedEffect(state.view.phase) {
        if (state.view.phase !is PhaseView.Turn) {
            localPhase = null
        }
    }

    val humanSeat = state.view.viewer

    // ── Informatics overlay state ─────────────────────────────────────────────
    var showGazette by remember { mutableStateOf(false) }
    var gazetteInitialTab by remember { mutableStateOf(0) }
    var showChit by remember { mutableStateOf<ChitContent?>(initialChit) }
    var chitAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var showPrimer by remember { mutableStateOf(showPrimerOverride ?: !PrimerPrefs.hasSeenPrimer) }
    // DARBAR narrative chat panel
    var showDarbar by remember { mutableStateOf(initialDarbarOpen) }

    // ── M5 PASS-AND-PLAY handoff guard ────────────────────────────────────────
    // In a multi-human (hot-seat) game the active seat changes between DIFFERENT humans. To stop one
    // player seeing another's hand, we blank the table behind a full-screen guard whenever control
    // hands off to a new human seat, until they tap "ready". [revealedSeat] is the seat the current
    // player has acknowledged; while activeSeat != revealedSeat the guard is up. Single-human vs-AI
    // never trips this (the active seat is always 0).
    var revealedSeat by remember { mutableStateOf(state.activeSeat) }
    val handoffPending =
        forceHandoffOverride ?: (
            state.isPassAndPlay &&
                state.isHumanTurn &&
                state.activeSeat != null &&
                state.activeSeat != revealedSeat
        )

    val onOpenGazette: () -> Unit = { showGazette = true }
    val onOpenGazetteMatrix: () -> Unit = {
        gazetteInitialTab = 3
        showGazette = true
    }

    // DENSITY GATE (spec §3) — the "dossier/whisper chits" popover system. FOCUS hides every chit
    // (it isn't in the FOCUS whitelist); GUIDED keeps everything except the heavy opponent Dossier
    // chit (spec: "still hide ... dossier"); ANALYST shows all of it, unchanged.
    val onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { content, anchor ->
        val allowed =
            when (state.densityLayer) {
                DensityLayer.FOCUS -> false
                DensityLayer.GUIDED -> content !is ChitContent.Dossier
                DensityLayer.ANALYST -> true
            }
        if (allowed) {
            showChit = content
            chitAnchor = anchor
        }
    }

    // MEASURED moment anchors (M4 §1): one registry per game screen. Opponent plates, the
    // human hand and the treasury medallion report their on-screen bounds into it; the moment
    // overlay rebases those to fire coin-trails / stamps / handoffs at the REAL seat instead of
    // a proportional ellipse guess. Provided here so every descendant layout writes into one store.
    val anchorRegistry =
        remember {
            com.kursi.designsystem.moment
                .TableAnchorRegistry()
        }

    CompositionLocalProvider(
        com.kursi.designsystem.moment.LocalTableAnchorRegistry provides anchorRegistry,
        LocalReducedMotion provides reducedMotion,
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val isDesktop = maxWidth >= 840.dp

            if (isDesktop) {
                DesktopLayout(
                    state = state,
                    gamePhase = gamePhase,
                    humanSeat = humanSeat,
                    onLocalPhase = { localPhase = it },
                    onAction = onAction,
                    onOpenGazette = onOpenGazette,
                    onShowChit = onShowChit,
                    onToggleCoach = onToggleCoach,
                    soundEnabled = soundEnabled,
                    reducedMotion = reducedMotion,
                )
            } else {
                PhoneLayout(
                    state = state,
                    gamePhase = gamePhase,
                    humanSeat = humanSeat,
                    onLocalPhase = { localPhase = it },
                    onAction = onAction,
                    onOpenGazette = onOpenGazette,
                    onShowChit = onShowChit,
                    onToggleCoach = onToggleCoach,
                    soundEnabled = soundEnabled,
                    reducedMotion = reducedMotion,
                    humanDisplayName = humanDisplayName,
                )
            }

            // M6e TAMASHA — a watch-only banner pinned to the top while the demo auto-plays.
            if (spectator) {
                val voice = LocalKursiVoice.current
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BrandTokens.StampRed.copy(alpha = 0.9f))
                            .border(1.dp, BrandTokens.Oxblood, RoundedCornerShape(6.dp))
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = voice.spectatorBanner,
                        style =
                            KursiType.caption.copy(
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            ),
                        color = KursiNeutrals.Cream,
                    )
                }
            }

            // ── DARBAR toggle FAB — brass button, top-end, clear of SpectatorBanner ──
            // DENSITY GATE: the Darbar chat panel/FAB is ANALYST-only (spec §3) — FOCUS/GUIDED hide it.
            if (state.narrativeEnabled && state.densityLayer == DensityLayer.ANALYST) {
                val darbarChatDesc =
                    if (showDarbar) {
                        stringResource(Res.string.a11y_darbar_close)
                    } else {
                        val unreadSuffix = if (state.unreadChat > 0) ", ${state.unreadChat} unread" else ""
                        stringResource(Res.string.a11y_darbar_open, unreadSuffix)
                    }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = if (spectator) 40.dp else 8.dp, end = 10.dp)
                            .size(44.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                if (showDarbar) {
                                    BrandTokens.GoldAntique
                                } else {
                                    BrandTokens.TeakDark.copy(alpha = 0.92f)
                                },
                            ).border(
                                1.5.dp,
                                BrandTokens.GoldAntique.copy(alpha = if (showDarbar) 0.0f else 0.8f),
                                androidx.compose.foundation.shape.CircleShape,
                            ).clickable(
                                indication = null,
                                interactionSource =
                                    remember {
                                        androidx.compose.foundation.interaction
                                            .MutableInteractionSource()
                                    },
                            ) {
                                showDarbar = !showDarbar
                                if (showDarbar) onAction(GameAction.MarkChatRead)
                            }.semantics {
                                contentDescription = darbarChatDesc
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "💬",
                        style = KursiType.label_sm.copy(fontSize = 18.sp),
                    )
                    // Unread badge
                    if (state.unreadChat > 0 && !showDarbar) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .size(16.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(BrandTokens.StampRed),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (state.unreadChat > 9) "9+" else state.unreadChat.toString(),
                                style = KursiType.caption.copy(fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                color = KursiNeutrals.Cream,
                            )
                        }
                    }
                }
            }

            // ── Overlay layers (drawn on top of the layout) ───────────────────────
            val chit = showChit
            if (chit != null) {
                WhisperChit(
                    content = chit,
                    onDismiss = { showChit = null },
                    anchorBounds = chitAnchor,
                    containerSize =
                        androidx.compose.ui.unit.IntSize(
                            constraints.maxWidth,
                            constraints.maxHeight,
                        ),
                )
            }

            if (showGazette) {
                NiyamGazette(
                    onDismiss = { showGazette = false },
                    onReplayPrimer = {
                        showGazette = false
                        PrimerPrefs.reset()
                        showPrimer = true
                    },
                    initialTab = gazetteInitialTab,
                )
            }

            if (showPrimer) {
                SwearingInPrimer(
                    onDone = { showPrimer = false },
                )
            }

            // DARBAR narrative chat panel — drawn above primer, below handoff guard.
            // DENSITY GATE: ANALYST-only (spec §3), same reasoning as the FAB above.
            if (state.narrativeEnabled && state.densityLayer == DensityLayer.ANALYST && showDarbar) {
                DarbarPanel(
                    state = state,
                    onAction = onAction,
                    onClose = { showDarbar = false },
                )
            }

            // BEAT GATE (spec §5) — tap-to-continue prompt while the paced bot round holds on a
            // meaningful beat. FOCUS/GUIDED only; ANALYST never sets pendingBeat (see GameUiState).
            val pending = state.pendingBeat
            if (pending != null) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                ) {
                    ContinueBeatPrompt(
                        onContinue = { onAction(GameAction.ContinueBeat) },
                        reducedMotion = reducedMotion,
                    )
                }
            }

            // PASS-AND-PLAY handoff guard — drawn last so it fully occludes the table (secrecy).
            if (handoffPending) {
                val seatName =
                    state.activeSeat
                        ?.let { state.opponentPersonas[PlayerId(it)]?.name }
                        ?: LocalKursiVoice.current.selfName
                HandoffGuard(
                    nextSeatName = seatName,
                    onReady = { revealedSeat = state.activeSeat },
                )
            }
        }
    }
}

// ─────────────────────────── Desktop Layout ───────────────────────────
// Hero surface: the FELT TABLE fills the center. Opponents arc across the top of
// the felt; deck+treasury sit in the middle; hand+dock below; log on the right rail.

@Composable
internal fun DesktopLayout(
    state: GameUiState,
    gamePhase: GamePhase,
    humanSeat: PlayerId,
    onLocalPhase: (GamePhase?) -> Unit,
    onAction: (GameAction) -> Unit,
    onOpenGazette: () -> Unit = {},
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    onToggleCoach: (() -> Unit)? = null,
    soundEnabled: Boolean = false,
    reducedMotion: Boolean = false,
) {
    // Full-window dark ink background
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(KursiFeltColors.Ink),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Top chrome: ANALYST keeps the fat gold Status Spine bar; FOCUS/GUIDED
            //    dissolve it into an engraved turn line + hairline rule (AAA rebuild).
            if (state.densityLayer == DensityLayer.ANALYST) {
                StatusSpineBar(
                    state = state,
                    gamePhase = gamePhase,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                EngravedTurnHeader(
                    state = state,
                    gamePhase = gamePhase,
                    onOpenGazette = onOpenGazette,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Hint Rail (coach guidance + NIYAM button) ──
            // DENSITY GATE: not in the FOCUS whitelist (spec §3) — GUIDED/ANALYST only.
            if (state.densityLayer != DensityLayer.FOCUS) {
                HintRail(
                    gamePhase = gamePhase,
                    state = state,
                    onOpenGazette = onOpenGazette,
                    modifier = Modifier.fillMaxWidth(),
                    onToggleCoach = onToggleCoach,
                    onPlayBestMove = { onAction(GameAction.PlayBestMove) },
                )
            }

            // ── What-just-happened: ANALYST keeps today's RecapRail. FOCUS/GUIDED move the
            // headline down onto the felt (an engraved caption under the pot) — see
            // FeltCenterTokens — so the top chrome stays a single clean turn line.
            if (state.densityLayer == DensityLayer.ANALYST) {
                RecapRail(state = state, gamePhase = gamePhase, modifier = Modifier.fillMaxWidth())
            }

            // ── Main body: felt table + log rail ──────────────────────
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── FELT TABLE COLUMN (the hero surface) ──────────────
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Felt surface wraps: opponents + deck+treasury.
                    // BoxWithConstraints gives us the felt px-size so the moment overlay
                    // can build proportional table anchors and layer ABOVE the felt but
                    // BELOW the action dock (which is a sibling row below this Column).
                    BoxWithConstraints(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                    ) {
                        val feltWidthPx = constraints.maxWidth.toFloat()
                        val feltHeightPx = constraints.maxHeight.toFloat()

                        FeltTableSurface(
                            modifier = Modifier.fillMaxSize(),
                            bordered = state.densityLayer == DensityLayer.ANALYST,
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // ── Opponents arc across top of the felt — CAPPED so a sparse
                                //    table doesn't let the strip eat the felt and starve the heart.
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 248.dp),
                                ) {
                                    OpponentSeatArc(
                                        state = state,
                                        gamePhase = gamePhase,
                                        onLocalPhase = onLocalPhase,
                                        onShowChit = onShowChit,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                // ── Deck / treasury / reaction spotlight — the table heart now
                                //    OWNS the remaining felt (incl. the formerly-dead bottom). A
                                //    radial pedestal glow roots the medallion and fills the lower felt.
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .drawBehind { drawHeartPedestal() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FeltCenterTokens(state = state, gamePhase = gamePhase, onShowChit = onShowChit)
                                }
                            }
                        }

                        // ── Action-moment stamp theatre: above the felt surface, below the dock ──
                        GameMomentLayer(
                            state = state,
                            widthPx = feltWidthPx,
                            heightPx = feltHeightPx,
                            soundEnabled = soundEnabled,
                            reducedMotion = reducedMotion,
                        )
                    }

                    // ── Bottom: AAA rebuild stacks a full-width, centered stamp-chip dock
                    //    ABOVE centered held cards for FOCUS/GUIDED (the mockup's thumb-zone
                    //    composition). ANALYST keeps the boxed hand+dock side by side.
                    if (state.densityLayer == DensityLayer.ANALYST) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            YourHandPanel(
                                state = state,
                                gamePhase = gamePhase,
                                humanSeat = humanSeat,
                                onAction = onAction,
                                onShowChit = onShowChit,
                                modifier = Modifier.weight(0.40f),
                            )
                            ActionDock(
                                state = state,
                                gamePhase = gamePhase,
                                humanSeat = humanSeat,
                                onLocalPhase = onLocalPhase,
                                onAction = onAction,
                                onShowChit = onShowChit,
                                modifier = Modifier.weight(0.60f),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ActionDock(
                                state = state,
                                gamePhase = gamePhase,
                                humanSeat = humanSeat,
                                onLocalPhase = onLocalPhase,
                                onAction = onAction,
                                onShowChit = onShowChit,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            YourHandPanel(
                                state = state,
                                gamePhase = gamePhase,
                                humanSeat = humanSeat,
                                onAction = onAction,
                                onShowChit = onShowChit,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // ── LOG RAIL (right, ~290dp) ──────────────────────────
                // DENSITY GATE: the teleprinter log is ANALYST-only (spec §3) — the Row simply
                // drops this child in FOCUS/GUIDED, letting the felt column take the full width.
                if (state.densityLayer == DensityLayer.ANALYST) {
                    Box(
                        modifier =
                            Modifier
                                .width(290.dp)
                                .fillMaxHeight(),
                    ) {
                        GameLog(state = state, onShowChit = onShowChit)
                    }
                }
            }
        }
    }
}

// ─────────────────────────── decoPanel (P3 depth surface) ──────────────────
// A tactile teak panel with layered contact shadow and a brass emboss bevel —
// used for the hand, action dock and Roznamcha log so they read as raised
// instruments on the table, not flat fills. AAA rebuild (design-language.md #1):
// depth comes from shadow + the drawn-on emboss bevel alone, never an outline
// framing the block — the old 1-1.5dp brass border is gone. [lifted] raises
// elevation when the panel owns the player's attention (their turn / a pending
// reaction).

@Composable
internal fun Modifier.decoPanel(
    radius: androidx.compose.ui.unit.Dp = KursiRadii.md,
    lifted: Boolean = false,
): Modifier {
    val shape =
        androidx.compose.foundation.shape
            .RoundedCornerShape(radius)
    return this
        .tableDepth(shape, elevation = 7.dp, lifted = lifted)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    KursiFeltColors.Surface3.copy(alpha = 0.96f),
                    KursiFeltColors.Surface2,
                    BrandTokens.TeakDark,
                ),
            ),
        ).embossEdge(radius, highlight = BrandTokens.GoldAntique.copy(alpha = if (lifted) 0.7f else 0.45f))
}

@Composable
internal fun WinnerBanner(
    gamePhase: GamePhase.GameOver,
    state: GameUiState,
) {
    val voice = LocalKursiVoice.current
    val winnerPersona = state.opponentPersonas[com.kursi.engine.PlayerId(gamePhase.winnerSeat)]
    val winnerColor =
        if (winnerPersona != null) {
            Color(winnerPersona.seatColorArgb)
        } else {
            KursiSeatColors[gamePhase.winnerSeat]
        }
    val winnerLabel = personaNameOrDefault(com.kursi.engine.PlayerId(gamePhase.winnerSeat), state, voice.selfName)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Brass winner roundel
        Box(
            modifier =
                Modifier
                    .size(80.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged, BrandTokens.BrassDark),
                        ),
                    ).border(2.dp, BrandTokens.GoldAntique, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "I",
                style = KursiType.display.copy(fontSize = 36.sp).rozha(),
                color = BrandTokens.TeakDark,
                textAlign = TextAlign.Center,
            )
        }
        val isHumanWinner = gamePhase.winnerSeat == state.view.viewer.raw
        Text(
            text = if (isHumanWinner) voice.youWin else voice.opponentWins(winnerLabel),
            style = KursiType.display.copy(fontSize = 28.sp).rozha(),
            color = winnerColor,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier =
                Modifier
                    .clip(Squircle(KursiRadii.md))
                    .background(BrandTokens.BrassDark.copy(alpha = 0.70f))
                    .border(
                        1.5.dp,
                        Brush.horizontalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged)),
                        Squircle(KursiRadii.md),
                    ).padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = voice.gameEndSub,
                style = KursiType.title.copy(letterSpacing = 2.sp),
                color = BrandTokens.GoldAntique,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun ReactionSpotlight(
    gamePhase: GamePhase.ReactionWindow,
    state: GameUiState,
) {
    val claimedRole = gamePhase.claimedRole
    val actorName = personaNameOrDefault(gamePhase.actor, state)

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseAlpha",
    )

    // P4 reaction theatre: stage the claim as a focal event ON the table's heart —
    // a debossed brass disc behind the claimed certificate, lit by a role-hued glow,
    // framed like a stamp about to be ruled on. (Coordinates with, does not duplicate,
    // the moment overlay's Challenge/Reveal beats.)
    val roleHue = claimedRole?.let { KursiColors.forRole(it).color } ?: BrandTokens.GoldAntique
    Box(contentAlignment = Alignment.Center) {
        // Focal backdrop disc — brass medallion staging
        Box(
            modifier =
                Modifier
                    .size(300.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .drawBehind {
                        val c =
                            androidx.compose.ui.geometry
                                .Offset(size.width / 2f, size.height / 2f)
                        val r = size.minDimension / 2f
                        // Debossed brass well
                        drawCircle(
                            Brush.radialGradient(
                                listOf(
                                    BrandTokens.BrassDark.copy(alpha = 0.28f),
                                    BrandTokens.TeakInk.copy(alpha = 0.0f),
                                ),
                                center = c,
                                radius = r,
                            ),
                        )
                        // Role-hued focal glow rising behind the card
                        drawCircle(
                            Brush.radialGradient(
                                listOf(
                                    roleHue.copy(alpha = 0.30f * pulseAlpha),
                                    Color.Transparent,
                                ),
                                center = c,
                                radius = r * 0.75f,
                            ),
                        )
                        drawCircle(
                            BrandTokens.GoldAntique.copy(alpha = 0.4f),
                            r * 0.92f,
                            c,
                            style =
                                androidx.compose.ui.graphics.drawscope
                                    .Stroke(1.5.dp.toPx()),
                        )
                    },
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.game_actor_claims, actorName),
                style = KursiType.label,
                color = KursiNeutrals.TextSecondary,
            )
            if (claimedRole != null) {
                // RETRO-FUTURIST §2 — the live claim medallion gets a holographic
                // brass↔role-hue↔cyan rim glint that travels the bezel as the claim
                // sits under judgement, marking it as the one LIVE focal instrument.
                val holoPhase by rememberInfiniteTransition(label = "claimHolo").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
                    label = "claimHoloPhase",
                )
                Box(
                    modifier =
                        Modifier
                            .scale(0.96f + 0.06f * pulseAlpha)
                            .holoRimLight(
                                accent = roleHue,
                                phase = holoPhase,
                                cornerRadius = KursiRadii.xl,
                                intensity = 0.55f + 0.45f * pulseAlpha,
                            ).shadow(
                                16.dp,
                                Squircle(KursiRadii.xl),
                                clip = false,
                                ambientColor = roleHue,
                                spotColor = roleHue.copy(alpha = 0.7f),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    RoleCard(role = claimedRole, size = CardSize.Medium, lifted = true)
                }
                Text(
                    text = roleLabel(claimedRole),
                    style = KursiType.cardRole.rozha(),
                    color = KursiColors.forRole(claimedRole).color,
                )
            }
        }
    }
}

// ─────────────────────────── Phone Layout ───────────────────────────

@Composable
internal fun PhoneLayout(
    state: GameUiState,
    gamePhase: GamePhase,
    humanSeat: PlayerId,
    onLocalPhase: (GamePhase?) -> Unit,
    onAction: (GameAction) -> Unit,
    onOpenGazette: () -> Unit = {},
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    onToggleCoach: (() -> Unit)? = null,
    soundEnabled: Boolean = false,
    reducedMotion: Boolean = false,
    humanDisplayName: String = "Khiladi",
) {
    // HintRail is only relevant when the human has something to decide. During bot
    // turns (Idle) it just repeats the StatusSpine — save the vertical space.
    val isPlayerTurn =
        gamePhase is GamePhase.PickAction ||
            gamePhase is GamePhase.PickTarget ||
            gamePhase is GamePhase.Confirm ||
            gamePhase is GamePhase.ReactionWindow ||
            gamePhase is GamePhase.LoseInfluence ||
            gamePhase is GamePhase.Exchange ||
            gamePhase is GamePhase.InvestigatePeek

    val focusStyle = state.densityLayer != DensityLayer.ANALYST
    FeltTableBackground(modifier = Modifier.fillMaxSize(), bordered = !focusStyle) {
        // Box so we can layer the full-screen moment overlay ABOVE the UI Column without
        // changing the Column's layout contract (no verticalScroll, all heights fixed/weighted).
        Box(modifier = Modifier.fillMaxSize()) {
            // No verticalScroll — everything must be on screen at once so the action
            // dock is always reachable without scrolling. Heights are fixed/weighted
            // so the layout adapts to any phone size without overflow.
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // ── TOP: ANALYST keeps the fat gold bar + RecapRail; FOCUS/GUIDED dissolve
                //    into a single engraved turn line (the headline moves onto the felt).
                if (!focusStyle) {
                    StatusSpineBar(state = state, gamePhase = gamePhase, modifier = Modifier.fillMaxWidth())
                    RecapRail(state = state, gamePhase = gamePhase, modifier = Modifier.fillMaxWidth())
                } else {
                    EngravedTurnHeader(
                        state = state,
                        gamePhase = gamePhase,
                        onOpenGazette = onOpenGazette,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── MIDDLE: opponents + felt + hand (fills remaining space) ──────────
                val opponentCount = state.view.players.count { it.id != state.view.viewer }
                val columns = if (opponentCount <= 2) 2 else 3

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (focusStyle) {
                        // Unboxed brass seat tokens resting on the felt — no grid, no plates.
                        OpponentSeatArc(
                            state = state,
                            gamePhase = gamePhase,
                            onLocalPhase = onLocalPhase,
                            onShowChit = onShowChit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        // Measure the available width to compute the exact plate width that fits the
                        // grid cells. OpponentPlate uses .size(width=plateWidth) so we must pass the
                        // actual cell width — the default 176.dp overflows on phones (cell ≈ 169dp).
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val gapTotal = 6.dp * (columns - 1)
                            val cellWidth = (maxWidth - gapTotal) / columns
                            // Clamp to OpponentPlate's safe range so widthFactor stays in [0.78, 1.30]
                            val plateWidth = cellWidth.coerceIn(136.dp, 228.dp)

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(if (opponentCount <= 3) 104.dp else 172.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val opponents = state.view.players.filter { it.id != state.view.viewer }
                                items(opponents) { opp ->
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

                    // Felt heart: deck/treasury pot (+ engraved beat caption in FOCUS/GUIDED).
                    // The moment overlay is full-screen (sibling Box below), so it's not here.
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(if (focusStyle) Modifier.weight(1f) else Modifier.height(80.dp))
                                .drawBehind { if (focusStyle) drawHeartPedestal() },
                        contentAlignment = Alignment.Center,
                    ) {
                        FeltCenterTokens(state = state, gamePhase = gamePhase, onShowChit = onShowChit)
                    }

                    // ANALYST: hand panel takes all remaining middle space, same as before.
                    // FOCUS/GUIDED: the hand moves OUT of this weighted column below — it anchors
                    // to the true bottom edge (below the action dock) instead of floating here
                    // between the pot and the actions.
                    if (!focusStyle) {
                        YourHandPanel(
                            state = state,
                            gamePhase = gamePhase,
                            humanSeat = humanSeat,
                            onAction = onAction,
                            onShowChit = onShowChit,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ── BOTTOM: hint (only on player turn) + action dock ────────────────
                // HintRail lives here so it's paired visually with the action dock
                // rather than buried above the table where the eye doesn't look.
                // DENSITY GATE: not in the FOCUS whitelist (spec §3) — GUIDED/ANALYST only.
                if (isPlayerTurn && state.densityLayer != DensityLayer.FOCUS) {
                    HintRail(
                        gamePhase = gamePhase,
                        state = state,
                        onOpenGazette = onOpenGazette,
                        modifier = Modifier.fillMaxWidth(),
                        onToggleCoach = onToggleCoach,
                        onPlayBestMove = { onAction(GameAction.PlayBestMove) },
                    )
                }

                ActionDock(
                    state = state,
                    gamePhase = gamePhase,
                    humanSeat = humanSeat,
                    onLocalPhase = onLocalPhase,
                    onAction = onAction,
                    onShowChit = onShowChit,
                    compact = true,
                )

                // ── HAND (FOCUS/GUIDED only): pinned to the true bottom edge, below the
                // actions — held cards fanned/overlapping, peeking up (mockup composition).
                // ANALYST keeps its hand inside the weighted middle column above (unchanged).
                if (focusStyle) {
                    YourHandPanel(
                        state = state,
                        gamePhase = gamePhase,
                        humanSeat = humanSeat,
                        onAction = onAction,
                        onShowChit = onShowChit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── ROZNAMCHA / DARBAR — collapsible bottom drawer with two tabs ──
                // DENSITY GATE: the log drawer (+ its Darbar tab) is ANALYST-only (spec §3).
                if (state.densityLayer == DensityLayer.ANALYST) {
                    CollapsibleLogDrawer(state = state, onShowChit = onShowChit, onAction = onAction)
                }
            }

            // Full-screen moment overlay — sits ABOVE the Column but never blocks input when idle.
            // Must be here (not inside the 80dp felt strip) so coin trails and stamps span the
            // full game area: opponent plates at the top to the human hand at the bottom.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                GameMomentLayer(
                    state = state,
                    widthPx = constraints.maxWidth.toFloat(),
                    heightPx = constraints.maxHeight.toFloat(),
                    soundEnabled = soundEnabled,
                    reducedMotion = reducedMotion,
                    humanDisplayName = humanDisplayName,
                )
            }
        } // end wrapper Box
    }
}

// ─────────────────────────── Action Dock ───────────────────────────

@Composable
internal fun ActionDock(
    state: GameUiState,
    gamePhase: GamePhase,
    humanSeat: PlayerId,
    onLocalPhase: (GamePhase?) -> Unit,
    onAction: (GameAction) -> Unit,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    // When true (phone layout) suppresses per-chip consequence text to save vertical space.
    compact: Boolean = false,
) {
    // P2: the dock lifts when it's the player's turn or a reaction is on them.
    val docLifted =
        gamePhase is GamePhase.PickAction ||
            gamePhase is GamePhase.PickTarget ||
            gamePhase is GamePhase.Confirm ||
            gamePhase is GamePhase.ReactionWindow ||
            gamePhase is GamePhase.LoseInfluence ||
            gamePhase is GamePhase.Exchange ||
            gamePhase is GamePhase.InvestigatePeek
    // AAA FOCUS rebuild: FOCUS/GUIDED drop the decoPanel box — the chips become raised
    // "stamp" tokens sitting directly on the felt (each chip carries its own shadow now),
    // not a panel of chips. ANALYST keeps the boxed dock.
    val boxed = state.densityLayer == DensityLayer.ANALYST
    Box(modifier = modifier.fillMaxWidth().then(if (boxed) Modifier.decoPanel(lifted = docLifted) else Modifier)) {
        Box(modifier = Modifier.padding(if (boxed) 12.dp else 4.dp)) {
            when (gamePhase) {
                is GamePhase.PickAction ->
                    PickActionDock(
                        state = state,
                        humanSeat = humanSeat,
                        onLocalPhase = onLocalPhase,
                        onAction = onAction,
                        onShowChit = onShowChit,
                        compact = compact,
                    )
                is GamePhase.PickTarget ->
                    PickTargetDock(
                        action = gamePhase.action,
                        state = state,
                        onLocalPhase = onLocalPhase,
                    )
                is GamePhase.Confirm ->
                    ConfirmDock(
                        action = gamePhase.action,
                        target = gamePhase.target,
                        humanSeat = humanSeat,
                        state = state,
                        onLocalPhase = onLocalPhase,
                        onAction = onAction,
                    )
                is GamePhase.ReactionWindow ->
                    ReactionDock(
                        reactionWindow = gamePhase,
                        humanSeat = humanSeat,
                        state = state,
                        onAction = onAction,
                        onShowChit = onShowChit,
                    )
                is GamePhase.LoseInfluence -> LoseInfluenceDock(state = state)
                is GamePhase.Exchange ->
                    ExchangeDock(
                        state = state,
                        onAction = onAction,
                    )
                is GamePhase.InvestigatePeek ->
                    InvestigatePeekDock(
                        phase = gamePhase,
                        state = state,
                        onAction = onAction,
                    )
                is GamePhase.Idle -> IdleDock(state = state)
                is GamePhase.GameOver -> GameOverDock(state = state, onAction = onAction)
            }
        }
    }
}

internal fun targetVerb(action: Action): String =
    when (action) {
        is Action.Coup -> "Khela"
        is Action.Assassinate -> "assassinate"
        is Action.Steal -> "steal from"
        is Action.Investigate -> "investigate"
        else -> "target"
    }

internal fun confirmSummary(
    action: Action,
    target: PlayerId?,
    targetName: String? = null,
): String {
    val targetStr = target?.let { " ${targetName ?: "Seat ${it.raw}"}" } ?: ""
    return when (action) {
        is Action.Coup -> "Khela$targetStr (pay 7)"
        is Action.Assassinate -> "Supari$targetStr (pay 3, claims BHAI)"
        is Action.Steal -> "Vasooli from$targetStr (claims BABU)"
        is Action.Investigate -> "Jaanch on$targetStr (claims PATRAKAAR)"
        Action.Tax -> "Ghotala +3 (claims NETA)"
        Action.ForeignAid -> "FDI +2"
        Action.Income -> "Dehaadi +1"
        Action.Exchange -> "Setting (claims JUGAADU)"
        Action.BailPe -> "Bail Pe Bahar (restore influence)"
        Action.Sabotage -> "Bali Khel (sacrifice influence for coins)"
        is Action.Hawala -> "Hawala (gift coins to$targetStr)"
        Action.Emergency -> "ADHYADESH — mass-Coup all opponents"
    }
}
