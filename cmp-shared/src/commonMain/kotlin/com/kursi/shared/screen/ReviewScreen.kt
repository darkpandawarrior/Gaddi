package com.kursi.shared.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.feature.game.GameScreen
import com.kursi.feature.game.GameUiState
import com.kursi.feature.game.Language
import com.kursi.feature.game.session.CompletedMatch
import com.kursi.feature.game.session.MatchReplay
import com.kursi.feature.game.session.ReplayAnnotation
import com.kursi.feature.game.session.ReplaySession
import com.kursi.shared.strings.LocalGaddiStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.a11y_advisor_read
import kursi.core.designsystem.generated.resources.a11y_review_match_row
import kursi.core.designsystem.generated.resources.a11y_step_number
import org.jetbrains.compose.resources.stringResource

/**
 * ReviewScreen — M6c REVIEW. Replays a recorded finished match on the REAL in-game table
 * ([GameScreen], read-only) with a scrubber + advisor annotations (teach-by-review).
 *
 * # Determinism + secrecy
 * The replay is reconstructed by [MatchReplay] from (seed + the human intent log) — the engine + bots
 * are a pure function of those, so every reviewed [GameUiState] is bit-for-bit the one the human saw
 * live, redacted exactly the same way (a reviewer never sees cards they couldn't see live).
 *
 * # The scrubber
 * Walks the human-decision frames (plus the terminal game-over frame) with: step ◂ / ▸, jump to the
 * previous / next HUMAN decision, a play/pause auto-advance, and a tappable turn-timeline of every
 * step. The replay frames are the stops the live session paused control on — the meaningful review
 * beats.
 *
 * # Annotations
 * At each human-decision step the [ReplaySession] carries the advisor's [ReplayAnnotation] for that
 * exact moment: what was played vs the recommended best, the EV gap, and the voiced belief read
 * ("Teen NETA hisaab mein the — lalkaar faayde ka tha"). Surfaced in a bottom annotation panel.
 *
 * The heavy reconstruction (one ISMCTS read per decision) runs OFF the main thread via [buildReplay];
 * the screen shows a teak void until it lands. The render harness can pass a pre-built [prebuilt]
 * session + [initialStep] so a static fixture captures a mid-replay frame with its annotation.
 *
 * AAA pass: lit ground + engraved nav chrome around the reused live board; the annotation panel and
 * scrubber transport dissolve their bordered boxes into hairline-lifted strips (non-negotiable #1),
 * matching the rest of the "Sarkari Noir" language while keeping the scrubber fully usable.
 */
@Composable
fun ReviewScreen(
    match: CompletedMatch?,
    language: Language,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Render-harness only: a pre-built session so a static fixture renders without async build. */
    prebuilt: ReplaySession? = null,
    /** Render-harness only: the step to open on (clamped). Null in the real app (opens at step 0). */
    initialStep: Int? = null,
) {
    val s = LocalGaddiStrings.current

    // Reconstruct the replay off the main thread (heavy: one advisor read per human decision).
    var replay by remember(match, prebuilt) { mutableStateOf(prebuilt) }
    LaunchedEffect(match, prebuilt) {
        if (prebuilt == null && match != null) {
            replay =
                withContext(kotlinx.coroutines.Dispatchers.Default) {
                    MatchReplay.replaySessionFor(match)
                }
        }
    }

    val r = replay
    if (r == null) {
        // Building (or no record) — lit void. The header still offers a way back.
        Box(modifier.fillMaxSize().litGround()) {
            ReviewHeader(onBack = onBack, modifier = Modifier.align(Alignment.TopCenter))
            Text(
                "…",
                style = GaddiType.display.rozha().copy(fontSize = 28.sp),
                color = BrandTokens.BrassAged.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }

    var step by remember(r) { mutableStateOf((initialStep ?: 0).coerceIn(0, r.stepCount - 1)) }
    var playing by remember(r) { mutableStateOf(false) }

    // Auto-advance while playing. Stops at the terminal frame.
    LaunchedEffect(playing, r) {
        if (playing) {
            while (step < r.stepCount - 1) {
                delay(1400)
                if (step < r.stepCount - 1) step += 1 else break
            }
            playing = false
        }
    }

    val ui: GameUiState = remember(r, step) { r.stepTo(step) }
    val annotation: ReplayAnnotation? = remember(r, step) { r.annotationAt(step) }
    val isTerminal = step == r.stepCount - 1

    Box(modifier.fillMaxSize().litGround()) {
        Column(Modifier.fillMaxSize()) {
            ReviewHeader(onBack = onBack)

            // The REAL in-game table, read-only (every action is swallowed). Reused verbatim so the
            // replay is visually identical to live play.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(step) {
                    GameScreen(
                        state = ui,
                        onAction = { /* read-only review — input is inert */ },
                        showPrimerOverride = false,
                        soundEnabled = false,
                        reducedMotion = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // A faint "REVIEW" watermark so it's unmistakably a replay, not a live game.
                ReviewWatermark(modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp))
            }

            // ── Advisor annotation panel (decision frames only) ──
            AnimatedVisibility(
                visible = annotation != null,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 2 },
            ) {
                annotation?.let { AnnotationPanel(it, language = language) }
            }

            // ── Scrubber transport + timeline ──
            Scrubber(
                replay = r,
                step = step,
                isTerminal = isTerminal,
                playing = playing,
                onTogglePlay = { playing = !playing },
                onStep = { target ->
                    playing = false
                    step = target.coerceIn(0, r.stepCount - 1)
                },
            )
        }
    }
}

// ───────────────────────────── header ─────────────────────────────

@Composable
private fun ReviewHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalGaddiStrings.current
    EngravedNavHeader(
        title = s.reviewHeader,
        onBack = onBack,
        backLabel = s.back,
        modifier = modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
        trailing = {
            // Corner stamp — a small crafted tag, allowed to keep a hairline rim (non-negotiable #4).
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, BrandTokens.StampRed.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    s.reviewBadge,
                    style = GaddiType.label_micro.copy(fontSize = 8.sp, letterSpacing = 1.sp),
                    color = BrandTokens.StampRed.copy(alpha = 0.85f),
                )
            }
        },
    )
}

@Composable
private fun ReviewWatermark(modifier: Modifier = Modifier) {
    val s = LocalGaddiStrings.current
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(BrandTokens.TeakInk.copy(alpha = 0.55f))
                .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            s.reviewBadge,
            style = GaddiType.label_micro.copy(fontSize = 8.sp, letterSpacing = 1.5.sp),
            color = BrandTokens.GoldAntique.copy(alpha = 0.8f),
        )
    }
}

// ───────────────────────────── annotation panel ─────────────────────────────

@Composable
private fun AnnotationPanel(
    a: ReplayAnnotation,
    language: Language,
) {
    val s = LocalGaddiStrings.current
    val accent = verdictColor(a.verdict)
    val verdictWord =
        when (a.verdict) {
            ReplayAnnotation.Verdict.SHARP -> s.reviewVerdictSharp
            ReplayAnnotation.Verdict.FINE -> s.reviewVerdictFine
            ReplayAnnotation.Verdict.LOOSE -> s.reviewVerdictLoose
            ReplayAnnotation.Verdict.COSTLY -> s.reviewVerdictCostly
        }
    val belief = if (language == Language.ENGLISH) a.beliefEnglish else a.beliefHinglish
    val advisorReadDesc = stringResource(Res.string.a11y_advisor_read, a.playedLabel, a.bestLabel, belief)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF1C140E), BrandTokens.TeakInk)))
                .semantics {
                    contentDescription = advisorReadDesc
                },
    ) {
        // A colour-coded hairline rule lifts the panel off the board — not a border framing it
        // (non-negotiable #1); the accent still reads as an "engraved" seam of colour.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.85f), Color.Transparent)),
                    ),
        )
        Column(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header row: advisor tag + verdict badge
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    s.reviewAdvisorHeader,
                    style = GaddiType.title.copy(fontSize = 12.sp, letterSpacing = 1.sp),
                    color = BrandTokens.GoldAntique,
                )
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent.copy(alpha = 0.18f))
                            .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(verdictWord, style = GaddiType.label_micro.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = accent)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    s.reviewAdvisorSub,
                    style = GaddiType.caption.copy(fontSize = 9.sp, fontStyle = FontStyle.Italic),
                    color = GaddiNeutrals.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // The voiced belief read — the heart of the teach-by-review.
            Text(
                "“$belief”",
                style = GaddiType.title.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic),
                color = GaddiNeutrals.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            // Played vs best + EV gap — bare DM Mono/label readouts, no boxes (non-negotiable #1).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                MoveCell(label = s.reviewPlayedLabel, value = a.playedLabel, accent = accent, modifier = Modifier.weight(1f))
                MoveCell(label = s.reviewBestLabel, value = a.bestLabel, accent = BrandTokens.GoldAntique, modifier = Modifier.weight(1f))
            }
            // EV gap line
            if (a.matchedBest) {
                Text(
                    s.reviewMatchedBest,
                    style = GaddiType.caption.copy(fontSize = 11.sp),
                    color = verdictColor(ReplayAnnotation.Verdict.SHARP),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        s.reviewEvGapLabel + ":",
                        style = GaddiType.caption.copy(fontSize = 11.sp),
                        color = GaddiNeutrals.TextMuted,
                    )
                    Text(
                        s.reviewEvGapValue(a.evGapPct),
                        style = GaddiType.title.copy(fontSize = 12.sp),
                        color = accent,
                    )
                }
            }
        }
    }
}

/** A bare played/best readout — label caption over the value in its verdict accent, no box
 *  (non-negotiable #1 + #5). */
@Composable
private fun MoveCell(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(label, style = GaddiType.label_micro.copy(fontSize = 8.sp, letterSpacing = 1.sp), color = GaddiNeutrals.TextMuted)
        Text(value, style = GaddiType.title.copy(fontSize = 12.sp), color = accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun verdictColor(v: ReplayAnnotation.Verdict): Color =
    when (v) {
        ReplayAnnotation.Verdict.SHARP -> BrandTokens.VerdictSharp
        ReplayAnnotation.Verdict.FINE -> BrandTokens.VerdictFine
        ReplayAnnotation.Verdict.LOOSE -> BrandTokens.VerdictLoose
        ReplayAnnotation.Verdict.COSTLY -> BrandTokens.StampRed // red — costly miss
    }

// ───────────────────────────── scrubber ─────────────────────────────

@Composable
private fun Scrubber(
    replay: ReplaySession,
    step: Int,
    isTerminal: Boolean,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onStep: (Int) -> Unit,
) {
    val s = LocalGaddiStrings.current
    val decisionIdx = replay.humanDecisionIndices
    val prevDecision = decisionIdx.lastOrNull { it < step }
    val nextDecision = decisionIdx.firstOrNull { it > step }

    Column(modifier = Modifier.fillMaxWidth()) {
        // A hairline rule lifts the transport off the board — not a filled/bordered bar
        // (non-negotiable #1), the same idiom as the home footer.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, BrandTokens.BrassDark.copy(alpha = 0.5f), Color.Transparent),
                        ),
                    ),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // ── Timeline strip — one pip per step, decision pips brass, terminal pip red. ──
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        s.reviewTimeline,
                        style = GaddiType.label_micro.copy(fontSize = 8.sp, letterSpacing = 1.5.sp),
                        color = GaddiNeutrals.TextMuted,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (i in 0 until replay.stepCount) {
                            val isDecision = i in decisionIdx
                            val isCurrent = i == step
                            val isLast = i == replay.stepCount - 1
                            val pipColor =
                                when {
                                    isCurrent -> BrandTokens.GoldAntique
                                    isLast -> BrandTokens.StampRed.copy(alpha = 0.8f)
                                    isDecision -> BrandTokens.BrassAged.copy(alpha = 0.85f)
                                    else -> BrandTokens.BrassDark.copy(alpha = 0.5f)
                                }
                            val stepDesc = stringResource(Res.string.a11y_step_number, i + 1)
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(if (isCurrent) 16.dp else 10.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(pipColor)
                                        .clickable { onStep(i) }
                                        .semantics { contentDescription = stepDesc },
                            )
                        }
                    }
                }

                // ── Transport row ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransportButton("⏮", s.reviewPrevDecision, enabled = prevDecision != null) { prevDecision?.let(onStep) }
                    TransportButton("◂", s.reviewStepBack, enabled = step > 0) { onStep(step - 1) }
                    // Play / pause — the hero control.
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(BrandTokens.BrassAged)
                                .border(1.dp, BrandTokens.GoldAntique, CircleShape)
                                .clickable(onClick = onTogglePlay)
                                .semantics { contentDescription = if (playing) s.reviewPause else s.reviewPlay },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (playing) "❚❚" else "▶",
                            style = GaddiType.title.copy(fontSize = 16.sp),
                            color = BrandTokens.TeakInk,
                        )
                    }
                    TransportButton("▸", s.reviewStepForward, enabled = step < replay.stepCount - 1) { onStep(step + 1) }
                    TransportButton("⏭", s.reviewNextDecision, enabled = nextDecision != null) { nextDecision?.let(onStep) }

                    Spacer(Modifier.weight(1f))

                    // Step counter / terminal tag.
                    Text(
                        if (isTerminal) s.reviewTerminal else s.reviewStepCounter(step + 1, replay.stepCount),
                        style = GaddiType.caption.copy(fontSize = 11.sp),
                        color = if (isTerminal) BrandTokens.StampRed.copy(alpha = 0.85f) else GaddiNeutrals.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    glyph: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1610))
                .border(1.dp, BrandTokens.BrassAged.copy(alpha = if (enabled) 0.7f else 0.25f), RoundedCornerShape(8.dp))
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .alpha(if (enabled) 1f else 0.4f)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = GaddiType.title.copy(fontSize = 14.sp), color = BrandTokens.GoldAntique)
    }
}

// ───────────────────────────── recent matches list (Career) ─────────────────────────────

/**
 * RecentMatchesList — the Review entry list shown on Career (M6c §1). Renders the persisted
 * [CompletedMatch] records (most-recent first) as tappable hairline rows; a tap opens [ReviewScreen]
 * on that game. Empty list → an honest "no file closed yet" notice.
 */
@Composable
fun RecentMatchesList(
    matches: List<CompletedMatch>,
    onReview: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalGaddiStrings.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EngravedHeader(eyebrow = s.reviewRecentHeader)
        Text(
            s.reviewRecentSub,
            style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
            color = GaddiNeutrals.TextMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
        )
        if (matches.isEmpty()) {
            Text(
                s.reviewRecentEmpty,
                style = GaddiType.caption.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic),
                color = GaddiNeutrals.TextMuted,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                matches.forEachIndexed { index, m ->
                    RecentMatchRow(match = m, onClick = { onReview(index) }, showDivider = index != matches.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun RecentMatchRow(
    match: CompletedMatch,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    val s = LocalGaddiStrings.current
    val won = match.humanWon
    val tagColor = if (won) BrandTokens.GoldAntique else BrandTokens.StampRed
    val reviewRowDesc =
        stringResource(Res.string.a11y_review_match_row, match.players, match.difficulty, if (won) "won" else "lost")
    HairlineRow(
        onClick = onClick,
        showDivider = showDivider,
        verticalPadding = 12.dp,
        modifier =
            Modifier.semantics {
                contentDescription = reviewRowDesc
            },
    ) {
        BrassToken(
            monogram = if (won) s.reviewMatchWon.take(1) else s.reviewMatchLost.take(1),
            fill = tagColor,
            size = 34.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                s.reviewMatchCaption(match.players, match.difficultyEnum.name, match.humanLog.size),
                style = GaddiType.title.copy(fontSize = 13.sp),
                color = GaddiNeutrals.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val winnerName = match.personas.firstOrNull { it.seat == match.winnerSeat }?.name
            Text(
                (if (won) s.reviewMatchWon else s.reviewMatchLost) + (winnerName?.let { " · $it" } ?: ""),
                style = GaddiType.caption.copy(fontSize = 10.sp),
                color = tagColor.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            s.reviewCta,
            style = GaddiType.label_micro.copy(fontSize = 8.sp, letterSpacing = 1.sp),
            color = BrandTokens.GoldAntique.copy(alpha = 0.7f),
        )
    }
}
