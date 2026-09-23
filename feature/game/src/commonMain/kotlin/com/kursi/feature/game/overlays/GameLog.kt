package com.kursi.feature.game.overlays

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.engine.*
import com.kursi.feature.game.*
import kotlinx.coroutines.launch
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.game_darbar_empty
import org.jetbrains.compose.resources.stringResource

/**
 * Phone: a tap-to-expand drawer at the bottom of the game screen.
 *
 * In narrative mode it shows two tabs — ROZNAMCHA (game event log) and DARBAR (bot chat
 * history). The DARBAR tab is the fix for "I didn't notice what the bots were saying" —
 * the full conversation is always one tap away, no hidden 💬 button needed.
 *
 * In non-narrative mode the DARBAR tab is hidden and the drawer behaves as before.
 */
@Composable
internal fun CollapsibleLogDrawer(
    state: GameUiState,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    onAction: (GameAction) -> Unit = {},
) {
    val voice = LocalGaddiVoice.current
    var expanded by remember { mutableStateOf(false) }
    // 0 = ROZNAMCHA, 1 = DARBAR
    var activeTab by remember { mutableStateOf(0) }
    val eventCount = state.recentEvents.size
    val chatCount = state.chatFeed.size
    val unread = state.unreadChat
    val hasNarrative = state.narrativeEnabled

    // Auto-switch to DARBAR tab when new unread chat arrives and drawer is already open
    LaunchedEffect(chatCount) {
        if (expanded && hasNarrative && unread > 0) {
            activeTab = 1
            onAction(GameAction.MarkChatRead)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Handle row ──────────────────────────────────────────────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(Squircle(GaddiRadii.sm))
                    .background(Color(0xFF120D07))
                    .border(
                        1.dp,
                        (if (unread > 0 && !expanded) BrandTokens.StampRed else BrandTokens.GoldAntique).copy(alpha = 0.6f),
                        Squircle(GaddiRadii.sm),
                    ).inspectable(onClick = { expanded = !expanded }, onLongClick = {})
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(GaddiSemantics.Success),
            )
            // ROZNAMCHA label
            Text(
                text = voice.logPanelHeader,
                style =
                    GaddiType.label_sm.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        letterSpacing = 2.sp,
                    ),
                color = if (activeTab == 0 && expanded) BrandTokens.GoldAntique else BrandTokens.GoldAntique.copy(alpha = 0.55f),
            )
            // DARBAR tab label — only when narrative mode is on
            if (hasNarrative) {
                Text(
                    text = "·",
                    color = BrandTokens.BrassDark,
                    style = GaddiType.label_sm,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier =
                        Modifier
                            .clip(Squircle(GaddiRadii.xs))
                            .clickable(
                                indication = null,
                                interactionSource =
                                    remember {
                                        androidx.compose.foundation.interaction
                                            .MutableInteractionSource()
                                    },
                            ) {
                                activeTab = 1
                                if (!expanded) expanded = true
                                onAction(GameAction.MarkChatRead)
                            }.padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "DARBAR",
                        style =
                            GaddiType.label_sm.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                letterSpacing = 2.sp,
                            ),
                        color =
                            if (activeTab == 1 && expanded) {
                                BrandTokens.GoldAntique
                            } else if (unread > 0) {
                                BrandTokens.StampRed
                            } else {
                                BrandTokens.GoldAntique.copy(alpha = 0.55f)
                            },
                    )
                    // Unread badge — shows even when drawer is closed so player knows new chat arrived
                    if (unread > 0 && !expanded) {
                        Box(
                            modifier =
                                Modifier
                                    .size(14.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(BrandTokens.StampRed),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (unread > 9) "9+" else unread.toString(),
                                style = GaddiType.label_micro.copy(fontSize = 7.sp),
                                color = GaddiNeutrals.Cream,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (expanded) "▾" else "▴ $eventCount",
                style =
                    GaddiType.label_micro.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                color = BrandTokens.BrassAged,
            )
        }

        // ── Expanded body ───────────────────────────────────────────────────────
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tab selector bar — only shown in narrative mode
                if (hasNarrative) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, start = 4.dp, end = 4.dp)
                                .clip(Squircle(GaddiRadii.xs))
                                .background(Color(0xFF120D07).copy(alpha = 0.7f)),
                    ) {
                        listOf("ROZNAMCHA" to 0, "DARBAR" to 1).forEach { (label, idx) ->
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .clickable(
                                            indication = null,
                                            interactionSource =
                                                remember {
                                                    androidx.compose.foundation.interaction
                                                        .MutableInteractionSource()
                                                },
                                        ) {
                                            activeTab = idx
                                            if (idx == 1) onAction(GameAction.MarkChatRead)
                                        }.background(
                                            if (activeTab == idx) {
                                                BrandTokens.BrassDark.copy(alpha = 0.5f)
                                            } else {
                                                Color.Transparent
                                            },
                                        ).padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style =
                                        GaddiType.label_micro.copy(
                                            letterSpacing = 1.5.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        ),
                                    color =
                                        if (activeTab == idx) {
                                            BrandTokens.GoldAntique
                                        } else {
                                            BrandTokens.BrassAged.copy(alpha = 0.5f)
                                        },
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(280.dp).padding(top = 4.dp)) {
                    if (!hasNarrative || activeTab == 0) {
                        GameLog(state = state, onShowChit = onShowChit)
                    } else {
                        // ── DARBAR tab: inline chat feed so the player can review what bots said ──
                        DarbarLogInline(state = state)
                    }
                }
            }
        }
    }
}

/**
 * Inline chat log for the DARBAR tab inside CollapsibleLogDrawer.
 * Shows the full Darbar chat history in a scrollable list — same content as the full
 * DarbarPanel overlay, but embedded in the existing bottom drawer so players don't need
 * to discover the hidden 💬 FAB. Newest message auto-scrolls into view.
 */
@Composable
internal fun DarbarLogInline(state: GameUiState) {
    fun nameForSeat(
        senderSeat: Int,
        isNarrator: Boolean,
        fromPlayer: Boolean,
    ): String =
        when {
            isNarrator -> "Sutradhar"
            fromPlayer -> "Aap"
            else -> state.opponentPersonas[PlayerId(senderSeat)]?.name ?: "Seat $senderSeat"
        }

    fun accentForSeat(
        senderSeat: Int,
        isNarrator: Boolean,
        fromPlayer: Boolean,
    ): Color =
        when {
            isNarrator -> BrandTokens.GoldAntique
            fromPlayer -> BrandTokens.BrassAged
            else ->
                state.opponentPersonas[PlayerId(senderSeat)]
                    ?.let { Color(it.seatColorArgb) }
                    ?: if (senderSeat >= 0) GaddiSeatColors[senderSeat] else BrandTokens.BrassAged
        }

    val listState = rememberLazyListState()
    val feedSize = state.chatFeed.size
    LaunchedEffect(feedSize) {
        if (feedSize > 0) listState.animateScrollToItem(feedSize - 1)
    }

    if (state.chatFeed.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(Res.string.game_darbar_empty),
                style =
                    GaddiType.caption.copy(
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    ),
                color = GaddiNeutrals.TextMuted,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(state.chatFeed, key = { it.id }) { msg ->
            val name = nameForSeat(msg.senderSeat, msg.isNarrator, msg.fromPlayer)
            val accent = accentForSeat(msg.senderSeat, msg.isNarrator, msg.fromPlayer)
            val emphatic =
                msg.tone in
                    listOf(
                        com.kursi.feature.game.narrative.MessageTone.HOSTILE,
                        com.kursi.feature.game.narrative.MessageTone.BOAST,
                        com.kursi.feature.game.narrative.MessageTone.PANICKED,
                    )
            SpeechBubble(
                speakerName = name,
                text = msg.body,
                accent = accent,
                fromPlayer = msg.fromPlayer,
                modifier = Modifier.fillMaxWidth(),
                monogram = null,
                emphatic = emphatic,
            )
        }
    }
}

// ─────────────────────────── Game Log ───────────────────────────
// Modern voiced log: ROZNAMCHA header, grouped rows, icon-led entries.

/**
 * ROZNAMCHA — the sarkari-teleprinter terminal (M4 §3). The primary "what-just-happened"
 * surface. Events grouped by turn into FILE blocks, persona-colored, outcome-badged, each row
 * long-pressable into a full chit. A retro CRT / inked-ribbon texture (scanlines + phosphor
 * vignette + brass masthead) reads premium-futurist. Auto-scrolls to the freshest line; when the
 * reader scrolls up to inspect history, a JUMP-TO-LATEST control surfaces. A sticky current-turn
 * header keeps the active FILE pinned. Multiplatform-safe Canvas/graphics only.
 */
@Composable
internal fun GameLog(
    state: GameUiState,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val voice = LocalGaddiVoice.current
    val listState = rememberLazyListState()
    val groups =
        remember(state.recentEvents) {
            groupLogByTurn(collapseLogEvents(state.recentEvents, state, voice.selfName))
        }
    // Flatten groups → header + rows so the LazyColumn can sticky-header per FILE block.
    val rows = remember(groups) { flattenLogGroups(groups) }

    // Auto-scroll to the freshest line as new events land.
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(rows.size - 1)
    }

    // "Scrolled away from the bottom" → reveal the jump-to-latest control.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= rows.size - 1
        }
    }

    // Current active turn (for the sticky header): the latest FILE in the feed.
    val currentTurn = groups.lastOrNull()

    LogTerminalShell(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Masthead ──
                LogMasthead(voice = voice)

                // ── Sticky current-turn (ACTIVE FILE) header ──
                if (currentTurn != null) {
                    LogActiveFileHeader(
                        text = voice.logCurrentTurn(currentTurn.turn, currentTurn.actorName),
                        accent = currentTurn.actorColor,
                    )
                }

                if (rows.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = voice.logEmpty,
                            style =
                                GaddiType.caption.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                ),
                            color = GaddiNeutrals.TextMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        items(rows.size) { idx ->
                            when (val r = rows[idx]) {
                                is LogFlatRow.Group ->
                                    LogFileBanner(
                                        text = voice.logTurnGroup(r.turn, r.actorName),
                                        accent = r.actorColor,
                                    )
                                is LogFlatRow.Entry ->
                                    CollapsedLogEntry(
                                        entry = r.entry,
                                        state = state,
                                        onShowChit = onShowChit,
                                    )
                            }
                        }
                    }
                }
            }

            // ── Jump-to-latest control — only when scrolled up into history ──
            androidx.compose.animation.AnimatedVisibility(
                visible = !atBottom && rows.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            ) {
                JumpToLatestPill(
                    label = voice.logJumpToLatest,
                    onClick = {},
                    listState = listState,
                    targetIndex = rows.size - 1,
                )
            }
        }
    }
}

// ─────────────────────────── Teleprinter terminal chrome (M4 §3) ────────────

/**
 * The CRT / inked-ribbon terminal shell: dark phosphor body, scanlines + vignette. AAA
 * rebuild (design-language.md #1): depth is [tableDepth]'s cast shadow + [embossEdge]'s
 * drawn-on bevel — the old two-stop gold↔brass gradient border is gone, replaced with a
 * single low-alpha brass hairline (engraved, not a bordered box).
 */
@Composable
internal fun LogTerminalShell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape =
        androidx.compose.foundation.shape
            .RoundedCornerShape(GaddiRadii.sm)
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .tableDepth(shape, elevation = 8.dp, lifted = true)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF120D07), // deep ink top
                            Color(0xFF1A130B),
                            Color(0xFF0E0A06), // shadowed bottom
                        ),
                    ),
                ).drawBehind { drawTeleprinterTexture() }
                .embossEdge(GaddiRadii.sm, highlight = BrandTokens.GoldAntique.copy(alpha = 0.5f)),
    ) {
        content()
    }
}

/** Phosphor scanlines + a soft green-amber CRT vignette. Multiplatform-safe Canvas. */
internal fun DrawScope.drawTeleprinterTexture() {
    // Horizontal scanlines — the inked-ribbon / CRT raster.
    val gap = 3.dp.toPx()
    var y = 0f
    val lineColor = Color.Black.copy(alpha = 0.18f)
    while (y < size.height) {
        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += gap
    }
    // Warm phosphor glow at the top where fresh print lands.
    drawRect(
        brush =
            Brush.verticalGradient(
                colors =
                    listOf(
                        BrandTokens.GoldAntique.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                startY = 0f,
                endY = size.height * 0.4f,
            ),
    )
    // Corner vignette so the tube edges fall into shadow.
    drawRect(
        brush =
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = maxOf(size.width, size.height) * 0.75f,
            ),
    )
}

/** Masthead: ROZNAMCHA wordmark + a sarkari live-feed tag and a blinking phosphor cursor dot. */
@Composable
internal fun LogMasthead(voice: GaddiVoice) {
    val pulse = rememberInfiniteTransition(label = "logCursor")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "logDot",
    )
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(GaddiSemantics.Success.copy(alpha = dotAlpha)),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = voice.logPanelHeader,
                style =
                    GaddiType.label_sm.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        letterSpacing = 3.sp,
                    ),
                color = BrandTokens.GoldAntique,
            )
        }
        Text(
            text = voice.logTeleprinterTag,
            style =
                GaddiType.label_micro.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 1.5.sp,
                    fontSize = 8.sp,
                ),
            color = BrandTokens.BrassAged.copy(alpha = 0.65f),
            modifier = Modifier.padding(start = 13.dp, top = 1.dp),
        )
    }
    // Perforated tear-strip divider under the masthead.
    Canvas(modifier = Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 8.dp)) {
        val r = 1.dp.toPx()
        val step = 5.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawCircle(BrandTokens.GoldAntique.copy(alpha = 0.30f), r, Offset(x, size.height / 2f))
            x += step
        }
    }
}

/** Sticky ACTIVE-FILE header pinned at the top of the feed — persona-accented brass strip. */
@Composable
internal fun LogActiveFileHeader(
    text: String,
    accent: Color,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .clip(Squircle(GaddiRadii.xs))
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.05f)),
                    ),
                ).border(
                    0.5.dp,
                    accent.copy(alpha = 0.5f),
                    Squircle(GaddiRadii.xs),
                ).padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style =
                GaddiType.label_micro.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    fontSize = 9.sp,
                ),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A per-turn FILE banner inside the scrolling body (grouped-by-turn divider). */
@Composable
internal fun LogFileBanner(
    text: String,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .height(11.dp)
                    .background(accent.copy(alpha = 0.85f)),
        )
        Text(
            text = text,
            style =
                GaddiType.label_micro.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    fontSize = 8.5.sp,
                ),
            color = accent.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.weight(1f).height(0.5.dp).background(accent.copy(alpha = 0.20f)))
    }
}

/** Jump-to-latest pill — scrolls the feed to the freshest line on tap. */
@Composable
internal fun JumpToLatestPill(
    label: String,
    onClick: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    targetIndex: Int,
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier =
            Modifier
                .clip(
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(50),
                ).background(
                    Brush.horizontalGradient(
                        listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged),
                    ),
                ).border(
                    0.5.dp,
                    BrandTokens.GoldAntique,
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(50),
                ).inspectable(
                    onClick = {
                        onClick()
                        scope.launch { listState.animateScrollToItem(targetIndex.coerceAtLeast(0)) }
                    },
                    onLongClick = {},
                ).padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style =
                GaddiType.label_micro.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                    fontSize = 9.sp,
                ),
            color = BrandTokens.TeakInk,
            maxLines = 1,
        )
    }
}

// ─────────────────────────── Log grouping (M4 §3) ───────────────────────────

/** One turn's block of log entries, with the seat that owns the turn for persona accenting. */
internal data class LogTurnGroup(
    val turn: Int,
    val actorName: String,
    val actorColor: Color,
    val entries: List<LogEntry>,
)

/** Flattened stream the LazyColumn renders: a FILE banner followed by its entry rows. */
internal sealed interface LogFlatRow {
    data class Group(
        val turn: Int,
        val actorName: String,
        val actorColor: Color,
    ) : LogFlatRow

    data class Entry(
        val entry: LogEntry,
    ) : LogFlatRow
}

/**
 * Groups the collapsed entry stream into per-turn FILE blocks. [LogEntry.RoundDivider] marks a
 * new turn boundary; entries before the first divider land in a synthetic opening block.
 */
internal fun groupLogByTurn(entries: List<LogEntry>): List<LogTurnGroup> {
    val groups = mutableListOf<LogTurnGroup>()
    var curTurn = 1
    var curName = ""
    var curColor = BrandTokens.BrassAged
    var bucket = mutableListOf<LogEntry>()

    fun flush() {
        if (bucket.isNotEmpty() || groups.isEmpty()) {
            groups.add(LogTurnGroup(curTurn, curName, curColor, bucket.toList()))
        }
        bucket = mutableListOf()
    }

    for (e in entries) {
        if (e is LogEntry.RoundDivider) {
            flush()
            curTurn = e.turn
            curName = e.actorName
            // RoundDivider doesn't carry color directly; the entries below resolve their own
            // persona color. Keep brass for the header band unless a colored marker exists.
            curColor = BrandTokens.BrassAged
        } else {
            bucket.add(e)
        }
    }
    flush()
    // Drop a leading empty synthetic block if real turns exist.
    return groups.filter { it.entries.isNotEmpty() || groups.size == 1 }
}

/** Turn groups → flat row stream with FILE banners. */
internal fun flattenLogGroups(groups: List<LogTurnGroup>): List<LogFlatRow> {
    val out = mutableListOf<LogFlatRow>()
    for (g in groups) {
        if (g.actorName.isNotEmpty()) {
            out.add(LogFlatRow.Group(g.turn, g.actorName, g.actorColor))
        }
        g.entries.forEach { out.add(LogFlatRow.Entry(it)) }
    }
    return out
}

// Collapsed log entry — either a single event or a merged safe-action row
sealed interface LogEntry {
    data class Single(
        val event: GameEvent,
    ) : LogEntry

    data class SafeAction(
        val actor: PlayerId,
        val action: Action,
    ) : LogEntry

    data class RoundDivider(
        val turn: Int,
        val actorName: String,
    ) : LogEntry
}

internal fun collapseLogEvents(
    events: List<GameEvent>,
    state: GameUiState,
    self: String = DEFAULT_SELF_NAME,
): List<LogEntry> {
    val result = mutableListOf<LogEntry>()
    var i = 0
    while (i < events.size) {
        val ev = events[i]
        // Collapse ActionDeclared + ActionResolved for safe actions (Income, ForeignAid)
        if (ev is GameEvent.ActionDeclared &&
            i + 1 < events.size &&
            events[i + 1] is GameEvent.ActionResolved
        ) {
            val resolved = events[i + 1] as GameEvent.ActionResolved
            val isSafe = ev.action == Action.Income || ev.action == Action.ForeignAid
            if (isSafe && ev.actor == resolved.actor) {
                result.add(LogEntry.SafeAction(actor = ev.actor, action = ev.action))
                i += 2
                continue
            }
        }
        if (ev is GameEvent.TurnAdvanced) {
            result.add(LogEntry.RoundDivider(ev.turnNumber, personaNameOrDefault(PlayerId(ev.toSeat), state, self)))
        } else {
            result.add(LogEntry.Single(ev))
        }
        i++
    }
    return result
}

/** Return the persona color for a PlayerId if available, otherwise TextSecondary. */
internal fun personaColorOrDefault(
    id: PlayerId,
    state: GameUiState,
): Color = state.opponentPersonas[id]?.let { Color(it.seatColorArgb) } ?: GaddiNeutrals.TextSecondary

@Composable
internal fun CollapsedLogEntry(
    entry: LogEntry,
    state: GameUiState,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
) {
    val voice = LocalGaddiVoice.current
    when (entry) {
        is LogEntry.SafeAction -> {
            val actorName = personaNameOrDefault(entry.actor, state)
            val actorColor = personaColorOrDefault(entry.actor, state)
            val icon =
                when (entry.action) {
                    Action.Income -> "🪙"
                    Action.ForeignAid -> "💵"
                    else -> "💰"
                }
            val voicedVerb = voice.logActorVerb(actorName, entry.action)
            val declared = GameEvent.ActionDeclared(entry.actor, entry.action, Rules.claimedRole(entry.action))
            LogRow(
                icon = icon,
                text = voicedVerb,
                textColor = BrandTokens.BrassAged.copy(alpha = 0.80f),
                outcome = OutcomeKind.Success,
                onInspect = { bounds ->
                    onShowChit(
                        ChitContent.LogEvent(
                            title = voicedVerb,
                            narration = voice.logNarration(declared, actorName),
                            icon = icon,
                        ),
                        bounds,
                    )
                },
            )
        }
        is LogEntry.RoundDivider -> {
            // Thin brass hairline divider with voiced text centered on it
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BrandTokens.BrassAged.copy(alpha = 0.25f)),
                )
                Box(
                    modifier =
                        Modifier
                            .background(GaddiFeltColors.Surface1.copy(alpha = 0.90f))
                            .padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = voice.logRoundDivider(entry.turn, entry.actorName),
                        style = GaddiType.caption,
                        color = BrandTokens.BrassAged.copy(alpha = 0.70f),
                    )
                }
            }
        }
        is LogEntry.Single -> {
            GameLogEntry(event = entry.event, state = state, onShowChit = onShowChit)
        }
    }
}

/**
 * Shared Roznamcha log row. Icon and outcome badge stay pinned to the top so the
 * voiced text can wrap to two lines and read in full — copy length varies a lot by
 * language and persona name, so single-line + ellipsis was clipping real sentences.
 */
@Composable
internal fun LogRow(
    icon: String,
    text: String,
    textColor: Color,
    outcome: OutcomeKind? = null,
    // Long-press the row → expand this event's deadpan narration in a chit. Receives the
    // row's captured root bounds so the chit anchors with a caret. Null → row is inert.
    onInspect: ((androidx.compose.ui.geometry.Rect?) -> Unit)? = null,
) {
    var rowBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 30.dp)
                .padding(vertical = 2.dp)
                .then(
                    if (onInspect != null) {
                        Modifier
                            .onGloballyPositioned { rowBounds = it.boundsInRoot() }
                            .inspectable(onClick = {}, onLongClick = { onInspect(rowBounds) })
                    } else {
                        Modifier
                    },
                ),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = icon,
            style = GaddiType.caption,
            modifier = Modifier.padding(top = 1.dp),
        )
        Text(
            text = text,
            style = GaddiType.caption.copy(lineHeight = 13.sp),
            color = textColor,
            modifier = Modifier.weight(1f).padding(top = 1.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (outcome != null) {
            OutcomeTag(kind = outcome, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
internal fun GameLogEntry(
    event: GameEvent,
    state: GameUiState,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
) {
    val voice = LocalGaddiVoice.current

    // Build the long-press inspect lambda for a row: opens a LogEvent chit with this
    // event's deadpan narration. [actor]/[other] feed the narrator the right names.
    fun inspect(
        title: String,
        icon: String,
        actor: String,
        other: String? = null,
    ): (androidx.compose.ui.geometry.Rect?) -> Unit =
        { bounds ->
            onShowChit(
                ChitContent.LogEvent(
                    title = title,
                    narration = voice.logNarration(event, actor, other),
                    icon = icon,
                ),
                bounds,
            )
        }
    when (event) {
        is GameEvent.ActionDeclared -> {
            val actorName = personaNameOrDefault(event.actor, state)
            val icon = actionIcon(event.action)
            val target =
                (event.action as? Action.Steal)?.target
                    ?: (event.action as? Action.Assassinate)?.target
                    ?: (event.action as? Action.Coup)?.target
            val voicedText =
                if (target != null) {
                    voice.logActorVerbWithTarget(actorName, event.action, personaNameOrDefault(target, state))
                } else {
                    voice.logActorVerb(actorName, event.action)
                }
            LogRow(
                icon = icon,
                text = voicedText,
                textColor = BrandTokens.BrassAged.copy(alpha = 0.80f),
                outcome = OutcomeKind.Neutral,
                onInspect = inspect(voicedText, icon, actorName, target?.let { personaNameOrDefault(it, state) }),
            )
        }
        is GameEvent.ActionResolved -> {
            val actorName = personaNameOrDefault(event.actor, state)
            val txt = voice.logActorVerb(actorName, event.action)
            LogRow(
                icon = actionIcon(event.action),
                text = txt,
                textColor = GaddiNeutrals.TextPrimary,
                outcome = OutcomeKind.Success,
                onInspect = inspect(txt, actionIcon(event.action), actorName),
            )
        }
        is GameEvent.ActionNegated -> {
            val actorName = personaNameOrDefault(event.actor, state)
            val txt = voice.logActorVerb(actorName, event.action)
            LogRow(
                icon = "🚫",
                text = txt,
                textColor = GaddiNeutrals.TextMuted,
                outcome = OutcomeKind.Block,
                onInspect = inspect(txt, "🚫", actorName),
            )
        }
        is GameEvent.Challenged -> {
            val challengerName = personaNameOrDefault(event.challenger, state)
            val targetName = personaNameOrDefault(event.target, state)
            val txt = voice.logChallenged(challengerName, targetName)
            LogRow(
                icon = "⚡",
                text = txt,
                textColor = GaddiSemantics.Danger,
                onInspect = inspect(txt, "⚡", targetName, challengerName),
            )
        }
        is GameEvent.ChallengeRevealed -> {
            val playerName = personaNameOrDefault(event.player, state)
            val voicedText =
                if (event.hadRole) {
                    voice.logBluffTrue(playerName)
                } else {
                    voice.logBluffCaught(playerName)
                }
            LogRow(
                icon = if (event.hadRole) "✓" else "✗",
                text = voicedText,
                textColor = GaddiNeutrals.TextSecondary,
                outcome = if (event.hadRole) OutcomeKind.ChallengeFail else OutcomeKind.BluffCaught,
                onInspect = inspect(voicedText, if (event.hadRole) "✓" else "✗", playerName),
            )
        }
        is GameEvent.Blocked -> {
            val blockerName = personaNameOrDefault(event.blocker, state)
            val txt = voice.logBlocked(blockerName, roleLabel(event.role))
            LogRow(
                icon = "🛡",
                text = txt,
                textColor = Color(0xFF3F6B5E),
                outcome = OutcomeKind.Block,
                onInspect = inspect(txt, "🛡", blockerName, roleLabel(event.role)),
            )
        }
        is GameEvent.InfluenceLost -> {
            val playerName = personaNameOrDefault(event.player, state)
            val txt = voice.logInfluenceLost(playerName, roleLabel(event.role))
            LogRow(
                icon = "💀",
                text = txt,
                textColor = GaddiSemantics.Danger,
                outcome = OutcomeKind.Eliminated,
                onInspect = inspect(txt, "💀", playerName),
            )
        }
        is GameEvent.PlayerEliminated -> {
            val playerName = personaNameOrDefault(event.player, state)
            val txt = voice.logEliminated(playerName)
            LogRow(
                icon = "🪦",
                text = txt,
                textColor = GaddiSemantics.Danger,
                outcome = OutcomeKind.Eliminated,
                onInspect = inspect(txt, "🪦", playerName),
            )
        }
        is GameEvent.CoinsChanged -> {
            // Skip — noise
        }
        is GameEvent.CoinsTransferred -> {
            val fromName = personaNameOrDefault(event.from, state)
            val toName = personaNameOrDefault(event.to, state)
            val txt = "$fromName → $toName: ${event.amount} Khokhas"
            LogRow(
                icon = "🪙",
                text = txt,
                textColor = GaddiNeutrals.TextMuted,
                onInspect = inspect(txt, "🪙", fromName, toName),
            )
        }
        is GameEvent.TurnAdvanced -> {
            // handled by RoundDivider in collapse pass — skip individual rendering
        }
        is GameEvent.GameEnded -> {
            val winnerName = personaNameOrDefault(event.winner, state)
            Text(
                text = voice.opponentWins(winnerName),
                style = GaddiType.title,
                color = GaddiFeltColors.GoldCoin,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                textAlign = TextAlign.Center,
            )
        }
        is GameEvent.Exchanged -> {
            val actorName = personaNameOrDefault(event.actor, state)
            val txt = voice.logActorVerb(actorName, Action.Exchange)
            LogRow(
                icon = "🔄",
                text = txt,
                textColor = GaddiNeutrals.TextMuted,
                onInspect = inspect(txt, "🔄", actorName),
            )
        }
        else -> {
            // Other events — skip silently (CardReplaced, etc. are implementation noise)
        }
    }
}

internal fun actionIcon(action: Action): String =
    when (action) {
        Action.Income -> "🪙"
        Action.ForeignAid -> "💵"
        Action.Tax -> "💰"
        Action.Exchange -> "🔄"
        is Action.Steal -> "🪤"
        is Action.Investigate -> "🔍"
        is Action.Assassinate -> "🔪"
        is Action.Coup -> "💥"
        Action.BailPe -> "🛡️"
        Action.Sabotage -> "💸"
        is Action.Hawala -> "🤝"
        Action.Emergency -> "⚡"
    }

// ─────────────────────────── Helpers ───────────────────────────

internal fun actorName(
    state: GameUiState,
    self: String = DEFAULT_SELF_NAME,
): String =
    when (val phase = state.view.phase) {
        is PhaseView.Turn -> personaNameOrDefault(phase.actor, state, self)
        is PhaseView.Reactions -> personaNameOrDefault(phase.actor, state, self)
        is PhaseView.InfluenceLoss -> personaNameOrDefault(phase.loser, state, self)
        is PhaseView.Exchange -> personaNameOrDefault(phase.actor, state, self)
        is PhaseView.InvestigatePeek -> personaNameOrDefault(phase.examiner, state, self)
        is PhaseView.Over -> personaNameOrDefault(phase.winner, state, self)
    }

/** Fallback self-label used by non-composable helpers that have no [GaddiVoice] in scope. */
internal const val DEFAULT_SELF_NAME = "Aap"

/**
 * The persona name for [id]: "Aap"/"You" ([self]) for the human viewer, the locked persona name
 * for a bot, and — only as a last resort when no persona is registered — a seat tag. NEVER a raw
 * "P{seat}" for a known seat: the viewer always resolves to [self], and bots always have a persona.
 */
internal fun personaNameOrDefault(
    id: PlayerId,
    state: GameUiState,
    self: String = DEFAULT_SELF_NAME,
): String =
    when {
        id == state.view.viewer -> self
        else -> state.opponentPersonas[id]?.name ?: "Seat ${id.raw}"
    }

internal fun actionName(action: Action): String =
    when (action) {
        Action.Income -> "Dehaadi"
        Action.ForeignAid -> "FDI"
        Action.Tax -> "Ghotala"
        Action.Exchange -> "Setting"
        is Action.Coup -> "Khela"
        is Action.Assassinate -> "Supari"
        is Action.Steal -> "Vasooli"
        is Action.Investigate -> "Jaanch"
        Action.BailPe -> "Bail Pe Bahar"
        Action.Sabotage -> "Bali Khel"
        is Action.Hawala -> "Hawala"
        Action.Emergency -> "Adhyadesh"
    }

internal fun roleLabel(role: Role): String =
    when (role) {
        Role.NETA -> "NETA"
        Role.BHAI -> "BHAI"
        Role.BABU -> "BABU"
        Role.JUGAADU -> "JUGAADU"
        Role.VAKIL -> "VAKIL"
        Role.PATRAKAAR -> "PATRAKAAR"
    }
