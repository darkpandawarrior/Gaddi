package com.kursi.shared.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.designsystem.moment.ActionMomentOverlay
import com.kursi.designsystem.moment.KursiMoment
import com.kursi.designsystem.moment.TableAnchors
import com.kursi.designsystem.moment.rememberMomentHost
import com.kursi.feature.game.LocalKursiVoice
import com.kursi.shared.nav.MatchSummary
import com.kursi.shared.strings.LocalKursiStrings
import com.siddharth.kmp.designsystem.capturable
import com.siddharth.kmp.designsystem.rememberCaptureController
import kotlinx.coroutines.launch
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.a11y_results_expired
import kursi.core.designsystem.generated.resources.a11y_winner
import kursi.core.designsystem.generated.resources.label_expired_stamp
import kursi.core.designsystem.generated.resources.label_unknown
import kursi.core.designsystem.generated.resources.results_bluffs_caught_value
import kursi.core.designsystem.generated.resources.results_bluffs_held_value
import kursi.core.designsystem.generated.resources.results_expired_body
import kursi.core.designsystem.generated.resources.results_expired_home_cta
import kursi.core.designsystem.generated.resources.results_expired_new_game_cta
import kursi.core.designsystem.generated.resources.results_expired_new_game_sub
import kursi.core.designsystem.generated.resources.results_expired_title
import kursi.core.designsystem.generated.resources.results_home_sub
import kursi.core.designsystem.generated.resources.results_new_game_sub
import kursi.core.designsystem.generated.resources.results_promote_cta
import kursi.core.designsystem.generated.resources.results_promote_sub
import kursi.core.designsystem.generated.resources.results_share_cta
import kursi.core.designsystem.generated.resources.results_share_sub
import kursi.core.designsystem.generated.resources.results_turns_value
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the Win confetti and the verdict stamp land. The results screen has no real table, so both
 * the (single) seat anchor and the treasury anchor point at the same mid-screen spot.
 */
private val ResultsScreenCentre = Offset(720f, 360f)

/**
 * ResultsScreen — Faisla (S5) from 17_app_plan.md §4.
 *
 * Sarkari Noir rebuild: the winner medallion is the ONE raised focal point (design-language.md #5),
 * the ROZNAMCHA recap reads as bare stat lines + hairline standing rows (no bordered "certificate"
 * card), and every exit is a stamp — matching the Home/Setup/Gauntlet standard.
 *
 * - Verdict medallion: winner monogram + "KURSI HAASIL" stamp
 * - ROZNAMCHA recap stats + final standings
 * - Exits: REMATCH / REVIEW / SHARE / NAYA KHEL / DAFTAR
 */
@Composable
fun ResultsScreen(
    summary: MatchSummary?,
    onRematch: () -> Unit,
    onNewGame: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * M6c — open the Review screen on this just-finished match (the most-recent recorded game). Null
     * (default) hides the Review CTA — e.g. the expired-record state where there is nothing to replay.
     */
    onReview: (() -> Unit)? = null,
    /**
     * Gauntlet progression — navigate to the next rung on the Tarakki ladder. Non-null only when this
     * was a gauntlet game, the human won, and a next rung exists. Null hides the PROMOTE CTA.
     */
    onNextGauntletRung: (() -> Unit)? = null,
    /**
     * Share the verdict. Receives the certificate rendered to an [ImageBitmap], or null when the
     * capture failed, so the caller can fall back to sharing text.
     */
    onShare: ((ImageBitmap?) -> Unit)? = null,
) {
    // M3 §4 — honest empty state. On a MatchSummaryStore cache miss (process death cleared the
    // in-memory store) we do NOT fabricate a 0-turn certificate; we show a "record expired" notice.
    if (summary == null) {
        ResultsExpired(onNewGame = onNewGame, onHome = onHome, modifier = modifier)
        return
    }
    val s = LocalKursiStrings.current
    val voice = LocalKursiVoice.current
    val scrollState = rememberScrollState()
    val winnerColor = Color(summary.winnerColor)
    val stampColor = if (summary.humanWon) BrandTokens.GoldAntique else BrandTokens.StampRed

    // ── KURSI HAASIL win moment on entry ──────────────────────────────────────
    // The verdict certificate stamps itself in: a Win moment plays once when the
    // screen first composes, celebrating the result before the static card settles.
    val momentHost = rememberMomentHost()
    // Share-as-image: the certificate Column below is the captured subtree, deliberately NOT the
    // whole screen — the footer CTAs are chrome, and nobody wants REMATCH burned into the image
    // they post. Capture is a suspend read-back off the GPU, hence the scope.
    val captureController = rememberCaptureController()
    val captureScope = rememberCoroutineScope()
    val tableAnchors = remember { resultsAnchors() }
    LaunchedEffect(summary.matchId) {
        momentHost.play(KursiMoment.Win(actorSeat = summary.winnerSeat ?: 0))
    }

    Box(modifier = modifier.fillMaxSize().litGround()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                // Centre the certificate in the available height: when the report and bark are
                // shorter than the viewport (the common case) the slack distributes evenly above
                // and below instead of pooling into a dead band between the card and the footer.
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().capturable(captureController),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    EngravedHeader(eyebrow = s.resultsTitle)

                    // ── Verdict medallion — the one focal point of the screen ──────────
                    VerdictMedallion(
                        summary = summary,
                        winnerColor = winnerColor,
                        stampColor = stampColor,
                    )

                    // Winner bark — plain italic text on the ground, no bordered card.
                    val winBark =
                        when {
                            summary.humanWon -> voice.youWin
                            summary.winnerName != null -> {
                                val personaId =
                                    summary.bestMomentPersonaId
                                        ?: summary.winnerName.lowercase().replace(" ", "_")
                                voice.personaBark(personaId, "win").ifEmpty { voice.youWin }
                            }
                            else -> voice.youWin
                        }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "“$winBark”",
                            style = KursiType.title.copy(fontSize = 15.sp, fontStyle = FontStyle.Italic),
                            color = KursiNeutrals.TextPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s.resultsVerdictSub,
                            style = KursiType.caption.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic),
                            color = KursiNeutrals.TextMuted,
                            textAlign = TextAlign.Center,
                        )
                    }

                    // ── ROZNAMCHA Recap ────────────────────────────────────────────────
                    RoznamchaRecap(summary = summary, winnerColor = winnerColor)
                }
            }

            // ── Footer Actions — on the ground, separated by a hairline rule ──────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, BrandTokens.BrassAged.copy(alpha = 0.4f), Color.Transparent),
                                ),
                            ),
                )
                Column(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // PROMOTE — shown only for gauntlet wins with a next rung available
                    if (onNextGauntletRung != null) {
                        StampChit(
                            label = stringResource(Res.string.results_promote_cta),
                            sublabel = stringResource(Res.string.results_promote_sub),
                            isHero = true,
                            onClick = onNextGauntletRung,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // REMATCH hero CTA — downgraded to non-hero when PROMOTE is present
                    StampChit(
                        label = s.resultsRematch,
                        sublabel = s.resultsRematchSub,
                        isHero = onNextGauntletRung == null,
                        onClick = onRematch,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // M6c — REVIEW THIS GAME (teach-by-review). Only when a replayable record exists.
                    if (onReview != null) {
                        StampChit(
                            label = s.reviewCta,
                            sublabel = s.reviewCtaSub,
                            onClick = onReview,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (onShare != null) {
                        StampChit(
                            label = stringResource(Res.string.results_share_cta),
                            sublabel = stringResource(Res.string.results_share_sub),
                            onClick = {
                                captureScope.launch {
                                    onShare(runCatching { captureController.captureAsImageBitmap() }.getOrNull())
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StampButton(
                            label = s.resultsNewGame,
                            sublabel = stringResource(Res.string.results_new_game_sub),
                            onClick = onNewGame,
                            style = StampStyle.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                        StampButton(
                            label = s.resultsHome,
                            sublabel = stringResource(Res.string.results_home_sub),
                            onClick = onHome,
                            style = StampStyle.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ── Win moment overlay — KURSI HAASIL stamp on entry ────────────────────
        ActionMomentOverlay(
            host = momentHost,
            anchors = tableAnchors,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Centre-anchored geometry: the Win confetti + stamp land mid-screen. */
private fun resultsAnchors(): TableAnchors =
    TableAnchors(
        seatCenters = mapOf(0 to ResultsScreenCentre),
        treasuryCenter = ResultsScreenCentre,
    )

// ─────────────────────────── Verdict medallion ────────────────────────────────

/**
 * The winner medallion — a raised struck-coin surface (real drop shadow + radial gradient +
 * brass/sweep rim, never a flat outline box) with a small stamp tag overlapping its base. The one
 * focal point of the screen (design-language.md #5); everything else recedes to the ground.
 */
@Composable
private fun VerdictMedallion(
    summary: MatchSummary,
    winnerColor: Color,
    stampColor: Color,
) {
    val unknownLabel = stringResource(Res.string.label_unknown)
    val winnerDesc = stringResource(Res.string.a11y_winner, summary.winnerName ?: unknownLabel)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(bottom = 14.dp)) {
        Box(
            modifier =
                Modifier
                    .size(176.dp)
                    .shadow(24.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(winnerColor.copy(alpha = 0.55f), BrandTokens.TeakDark, BrandTokens.TeakInk),
                            center = Offset(0.35f, 0.3f),
                        ),
                    ).border(2.5.dp, Brush.sweepGradient(listOf(stampColor, BrandTokens.BrassAged, stampColor)), CircleShape)
                    .drawBehind {
                        val cx = size.width / 2
                        val cy = size.height / 2
                        val r = size.width * 0.48f
                        val rays = 16
                        for (i in 0 until rays) {
                            val angle = (i * 2 * PI / rays).toFloat()
                            drawLine(
                                stampColor.copy(alpha = 0.18f),
                                Offset(cx + r * 0.74f * cos(angle), cy + r * 0.74f * sin(angle)),
                                Offset(cx + r * 0.95f * cos(angle), cy + r * 0.95f * sin(angle)),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                        drawCircle(stampColor.copy(alpha = 0.15f), r * 0.7f, Offset(cx, cy), style = Stroke(0.8.dp.toPx()))
                    }.semantics {
                        contentDescription = winnerDesc
                    },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = summary.winnerMonogram ?: "?",
                    style = KursiType.display.rozha().copy(fontSize = 32.sp),
                    color = winnerColor.copy(alpha = 0.95f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summary.winnerName ?: "Unknown",
                    style = KursiType.name.copy(fontSize = 13.sp),
                    color = KursiNeutrals.TextPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Win/loss stamp tag — the same crafted badge idiom as "APPROVED" / "MUHAR" tags elsewhere.
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 12.dp)
                    .shadow(4.dp, Squircle(KursiRadii.xs), clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                    .clip(Squircle(KursiRadii.xs))
                    .background(stampColor)
                    .padding(horizontal = 16.dp, vertical = 5.dp),
        ) {
            Text(
                LocalKursiStrings.current.resultsWinStamp,
                style = KursiType.label_sm.dmMono().copy(fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                color = KursiNeutrals.Cream,
            )
        }
    }
}

// ─────────────────────────── ROZNAMCHA Recap ──────────────────────────────────

@Composable
private fun RoznamchaRecap(
    summary: MatchSummary,
    winnerColor: Color,
) {
    val s = LocalKursiStrings.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EngravedHeader(eyebrow = s.resultsRecapHeader)
        Spacer(Modifier.height(4.dp))
        RecapRow(s.resultsRecapSurvived, stringResource(Res.string.results_turns_value, summary.turnsTotal))
        RecapRow(s.resultsRecapBluffsLanded, stringResource(Res.string.results_bluffs_held_value, summary.bluffsHeld))
        RecapRow(s.resultsRecapBluffsCaught, stringResource(Res.string.results_bluffs_caught_value, summary.bluffsCaught))
        // M6b — per-game decision-quality line (omitted when no gradeable decisions were recorded).
        summary.decisionSummary?.let { dq ->
            RecapRow(s.resultsRecapDecision, s.resultsDecisionValue(dq.accuracyPct, dq.avgEvLostPct))
        }

        if (summary.finalStandings.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                s.resultsRecapStandings,
                style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                color = KursiNeutrals.TextMuted,
            )
            Column {
                summary.finalStandings.forEachIndexed { i, name ->
                    HairlineRow(showDivider = i != summary.finalStandings.lastIndex, verticalPadding = 10.dp) {
                        BrassToken(
                            monogram = name,
                            fill = if (i == 0) winnerColor else KursiSeatColors[i],
                            size = 34.dp,
                        )
                        Text(
                            text = name,
                            style = KursiType.body.copy(fontSize = 13.sp),
                            color = if (i == 0) BrandTokens.GoldAntique else KursiNeutrals.TextSecondary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (i == 0) {
                            Text(
                                s.resultsRecapWinnerSuffix.removePrefix(" ←").trim(),
                                style = KursiType.label_micro.dmMono().copy(letterSpacing = 0.8.sp, fontSize = 9.sp),
                                color = BrandTokens.GoldAntique,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            s.resultsRecapSeal,
            style = KursiType.label_sm.dmMono().copy(fontSize = 10.sp, letterSpacing = 2.sp),
            color = BrandTokens.BrassAged.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────── Expired (cache-miss) empty state ────────────────

/**
 * Honest "record expired" state (M3 §4). Shown when the MatchSummary is gone (process death
 * cleared the in-memory store) — instead of a fabricated 0-turn certificate, we own the gap with
 * an in-character "file mislaid" notice, composed directly on the lit ground (no floating bordered
 * card) and route the player back to a real action.
 */
@Composable
private fun ResultsExpired(
    onNewGame: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expiredDesc = stringResource(Res.string.a11y_results_expired)
    Box(modifier = modifier.fillMaxSize().litGround()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 440.dp)
                        .fillMaxWidth()
                        .semantics { contentDescription = expiredDesc },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Faded "RAD" (cancelled) stamp roundel — raised, oxblood rim, no flat box.
                Box(
                    modifier =
                        Modifier
                            .size(92.dp)
                            .shadow(10.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(BrandTokens.StampRed.copy(alpha = 0.12f), BrandTokens.TeakDark)))
                            .border(2.dp, BrandTokens.StampRed.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(Res.string.label_expired_stamp),
                        style = KursiType.display.copy(fontSize = 20.sp, letterSpacing = 3.sp),
                        color = BrandTokens.StampRed.copy(alpha = 0.7f),
                    )
                }
                Text(
                    stringResource(Res.string.results_expired_title),
                    style = KursiType.display.rozha().copy(fontSize = 20.sp),
                    color = BrandTokens.GoldAntique,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(Res.string.results_expired_body),
                    style = KursiType.body.copy(fontSize = 13.sp),
                    color = KursiNeutrals.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                StampChit(
                    label = stringResource(Res.string.results_expired_new_game_cta),
                    sublabel = stringResource(Res.string.results_expired_new_game_sub),
                    isHero = true,
                    onClick = onNewGame,
                    modifier = Modifier.fillMaxWidth(),
                )
                StampButton(
                    label = stringResource(Res.string.results_expired_home_cta),
                    onClick = onHome,
                    style = StampStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RecapRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = KursiType.body.copy(fontSize = 12.sp),
            color = KursiNeutrals.TextSecondary,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
        Text(
            value,
            style = KursiType.numeric.copy(fontSize = 12.sp),
            color = BrandTokens.GoldAntique,
        )
    }
}
