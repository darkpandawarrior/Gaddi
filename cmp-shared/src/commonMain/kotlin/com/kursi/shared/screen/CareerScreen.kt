package com.kursi.shared.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.ai.persona.PersonaRoster
import com.kursi.core.prefs.DecisionGrade
import com.kursi.core.prefs.DecisionLedger
import com.kursi.core.prefs.PersonaRecord
import com.kursi.core.prefs.StatsLedger
import com.kursi.designsystem.*
import com.kursi.shared.strings.KursiStrings
import com.kursi.shared.strings.LocalKursiStrings
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.a11y_avg_ev_lost_suffix
import kursi.core.designsystem.generated.resources.a11y_best_move_match_suffix
import kursi.core.designsystem.generated.resources.a11y_bluff_success_suffix
import kursi.core.designsystem.generated.resources.a11y_bluffs_caught_suffix
import kursi.core.designsystem.generated.resources.a11y_bluffs_held_suffix
import kursi.core.designsystem.generated.resources.a11y_career_empty
import kursi.core.designsystem.generated.resources.a11y_career_headline
import kursi.core.designsystem.generated.resources.a11y_career_strip
import kursi.core.designsystem.generated.resources.a11y_challenge_accuracy_suffix
import kursi.core.designsystem.generated.resources.a11y_grade_summary
import kursi.core.designsystem.generated.resources.a11y_h2h_row
import kursi.core.designsystem.generated.resources.career_bluffs_caught_label
import kursi.core.designsystem.generated.resources.career_bluffs_held_label
import kursi.core.designsystem.generated.resources.career_empty_body
import kursi.core.designsystem.generated.resources.career_empty_title
import kursi.core.designsystem.generated.resources.career_games_losses
import kursi.core.designsystem.generated.resources.career_h2h_header
import kursi.core.designsystem.generated.resources.career_register_badge
import kursi.core.designsystem.generated.resources.career_strip_summary
import kursi.core.designsystem.generated.resources.career_title
import kursi.core.designsystem.generated.resources.career_wins_stamp
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * CareerScreen — Roznamcha career register (M3 §3). A persisted lifetime ledger surfaced as a
 * stamped office file: total cases (games), wins, win-rate, bluffs held/caught, and a per-persona
 * head-to-head table. Reachable from Home and Settings.
 *
 * AAA pass: lit ground, engraved nav chrome, ONE raised focal card (the win headline) above
 * hairline-row sections underneath — the same "Sarkari Noir" hierarchy as Gauntlet/Setup/Home.
 */
@Composable
fun CareerScreen(
    ledger: StatsLedger,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** M6b — lifetime decision-quality dossier. Empty (default) hides the dossier section. */
    decisionLedger: DecisionLedger = DecisionLedger(),
    /** M6c — recorded finished matches (most-recent first) for the Review entry list. */
    recentMatches: List<com.kursi.feature.game.session.CompletedMatch> = emptyList(),
    /** M6c — open the Review screen on the recent-match at this index (most-recent = 0). */
    onReview: (index: Int) -> Unit = {},
    /** M6d — ranked ELO standing surfaced as a compact rank strip; taps through to the leaderboard. */
    ranked: com.kursi.core.prefs.RankedStanding =
        com.kursi.core.prefs
            .RankedStanding(),
    /** M6d — open the local leaderboard / standings screen. */
    onLeaderboard: () -> Unit = {},
) {
    val s = LocalKursiStrings.current
    val scroll = rememberScrollState()
    Column(modifier = modifier.fillMaxSize().litGround()) {
        EngravedNavHeader(
            title = stringResource(Res.string.career_title),
            onBack = onBack,
            backLabel = s.back,
            modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
            trailing = {
                Text(
                    stringResource(Res.string.career_register_badge),
                    style = KursiType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                    color = KursiNeutrals.TextMuted,
                )
            },
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // M6d — ranked rank/rating strip at the top of the career file (always shown so the
                // player's sarkari rank is visible even before the first ranked result lands).
                RankedStrip(ranked = ranked, onOpen = onLeaderboard, modifier = Modifier.fillMaxWidth())
                if (ledger.games == 0) {
                    CareerEmpty()
                } else {
                    // The one raised focal card of the file — everything below reads on the ground.
                    CareerHeadline(ledger)
                    CareerStatGrid(ledger)
                    if (!decisionLedger.isEmpty) DecisionDossier(decisionLedger)
                    CareerH2H(ledger)
                }
                // M6c — recent finished matches, each reviewable on the real table.
                if (recentMatches.isNotEmpty()) {
                    RecentMatchesList(matches = recentMatches, onReview = onReview)
                }
            }
        }
    }
}

@Composable
private fun CareerEmpty() {
    val emptyDesc = stringResource(Res.string.a11y_career_empty)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp)
                .semantics { contentDescription = emptyDesc },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.career_empty_title),
            style = KursiType.display.rozha().copy(fontSize = 20.sp),
            color = BrandTokens.GoldAntique,
        )
        Text(
            stringResource(Res.string.career_empty_body),
            style = KursiType.body.copy(fontSize = 12.sp),
            color = KursiNeutrals.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The one raised focal card (non-negotiable #5): win-rate roundel + headline win count. Real cast
 *  shadow, no outline framing the block. */
@Composable
private fun CareerHeadline(ledger: StatsLedger) {
    val winPct = (ledger.winRate * 100).roundToInt()
    val headlineDesc = stringResource(Res.string.a11y_career_headline, ledger.wins, ledger.games, winPct)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(10.dp, Squircle(KursiRadii.md), clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                .clip(Squircle(KursiRadii.md))
                .background(
                    Brush.verticalGradient(
                        listOf(BrandTokens.GoldAntique.copy(alpha = 0.22f), BrandTokens.BrassDark.copy(alpha = 0.32f)),
                    ),
                ).padding(horizontal = 20.dp, vertical = 20.dp)
                .semantics {
                    contentDescription = headlineDesc
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(76.dp)
                    .shadow(6.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(BrandTokens.GoldAntique.copy(alpha = 0.28f), BrandTokens.TeakDark)))
                    .border(2.dp, BrandTokens.GoldAntique, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$winPct%", style = KursiType.display.copy(fontSize = 20.sp), color = BrandTokens.GoldAntique)
        }
        Column {
            Text(
                stringResource(Res.string.career_wins_stamp, ledger.wins),
                style = KursiType.display.rozha().copy(fontSize = 22.sp),
                color = KursiNeutrals.TextPrimary,
            )
            Text(
                stringResource(Res.string.career_games_losses, ledger.games, ledger.losses),
                style = KursiType.body.copy(fontSize = 12.sp, fontStyle = FontStyle.Italic),
                color = KursiNeutrals.TextSecondary,
            )
        }
    }
}

@Composable
private fun CareerStatGrid(ledger: StatsLedger) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        StatReadout(
            stringResource(Res.string.career_bluffs_held_label),
            "${ledger.bluffsHeld}",
            stringResource(Res.string.a11y_bluffs_held_suffix),
            Modifier.weight(1f),
        )
        StatReadout(
            stringResource(Res.string.career_bluffs_caught_label),
            "${ledger.bluffsCaught}",
            stringResource(Res.string.a11y_bluffs_caught_suffix),
            Modifier.weight(1f),
        )
    }
}

/** A bare stat readout on the lit ground — DM Mono numeral over a caption label, no box (non-negotiable
 *  #1 + #5 "stat readouts in DM Mono"). Reused by [CareerStatGrid] and [DecisionDossier]. */
@Composable
private fun StatReadout(
    label: String,
    value: String,
    a11y: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.semantics { contentDescription = "$value $a11y" }) {
        Text(value, style = KursiType.numeric.copy(fontSize = 24.sp), color = BrandTokens.GoldAntique)
        Text(label, style = KursiType.caption.copy(fontSize = 10.sp, letterSpacing = 0.5.sp), color = KursiNeutrals.TextMuted)
    }
}

// ─────────────────────────── M6b Decision-Quality dossier ─────────────────────

/**
 * M6b — the decision-quality dossier: an engraved section eyebrow, a stamped grade chip (Sharp Babu /
 * Steady / Reckless) with a sub-line naming the sample size, then the four headline readouts — best-move
 * match %, avg win-probability bled, challenge discipline, and bluff survival. Bilingual via
 * [LocalKursiStrings].
 */
@Composable
private fun DecisionDossier(dl: DecisionLedger) {
    val s = LocalKursiStrings.current
    val grade = dl.grade
    val (gradeName, gradeSub) = gradeStrings(grade, s)
    val gradeColor =
        when (grade) {
            DecisionGrade.SHARP -> KursiSemantics.Success
            DecisionGrade.STEADY -> BrandTokens.GoldAntique
            DecisionGrade.RECKLESS -> BrandTokens.StampRed
            DecisionGrade.UNRATED -> BrandTokens.BrassAged
        }
    val gradeSummaryDesc = stringResource(Res.string.a11y_grade_summary, gradeName, dl.accuracyPct, dl.decisions)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        EngravedHeader(eyebrow = s.dqHeader)
        // Grade stamp row — a small crafted chip (allowed to keep a hairline rim; non-negotiable #4).
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = gradeSummaryDesc
                    },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .shadow(4.dp, RoundedCornerShape(6.dp), clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                        .clip(RoundedCornerShape(6.dp))
                        .background(gradeColor.copy(alpha = 0.16f))
                        .border(1.5.dp, gradeColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    gradeName,
                    style = KursiType.title.copy(fontSize = 15.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = gradeColor,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    gradeSub,
                    style = KursiType.body.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic),
                    color = KursiNeutrals.TextSecondary,
                )
                Text(s.dqSampleSub(dl.decisions), style = KursiType.caption.copy(fontSize = 9.sp), color = KursiNeutrals.TextMuted)
            }
        }
        // 2×2 readouts — bare DM Mono numerals on the ground, no boxes.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            StatReadout(
                s.dqAccuracyLabel,
                "${dl.accuracyPct}%",
                stringResource(Res.string.a11y_best_move_match_suffix),
                Modifier.weight(1f),
            )
            StatReadout(
                s.dqEvLostLabel,
                "${dl.avgEvLostPct}%",
                stringResource(Res.string.a11y_avg_ev_lost_suffix),
                Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            StatReadout(
                s.dqChallengeLabel,
                if (dl.challenges == 0) "—" else "${dl.challengeAccuracyPct}%",
                stringResource(Res.string.a11y_challenge_accuracy_suffix, dl.challenges),
                Modifier.weight(1f),
            )
            StatReadout(
                s.dqBluffLabel,
                if (dl.bluffsTried == 0) "—" else "${dl.bluffSuccessPct}%",
                stringResource(Res.string.a11y_bluff_success_suffix, dl.bluffsTried),
                Modifier.weight(1f),
            )
        }
    }
}

/** Resolve the bilingual grade name + flavour sub-line for [grade]. */
private fun gradeStrings(
    grade: DecisionGrade,
    s: KursiStrings,
): Pair<String, String> =
    when (grade) {
        DecisionGrade.SHARP -> s.dqGradeSharp to s.dqGradeSharpSub
        DecisionGrade.STEADY -> s.dqGradeSteady to s.dqGradeSteadySub
        DecisionGrade.RECKLESS -> s.dqGradeReckless to s.dqGradeRecklessSub
        DecisionGrade.UNRATED -> s.dqGradeUnrated to s.dqGradeUnratedSub
    }

@Composable
private fun CareerH2H(ledger: StatsLedger) {
    if (ledger.headToHead.isEmpty()) return
    // Order rivals by games faced (most-encountered first), resolving display names from the roster.
    val rows = ledger.headToHead.entries.sortedByDescending { it.value.played }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EngravedHeader(eyebrow = stringResource(Res.string.career_h2h_header))
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { i, entry ->
                H2HRow(personaId = entry.key, record = entry.value, showDivider = i != rows.lastIndex)
            }
        }
    }
}

@Composable
private fun H2HRow(
    personaId: String,
    record: PersonaRecord,
    showDivider: Boolean,
) {
    val persona = PersonaRoster.ALL.firstOrNull { it.id == personaId }
    val name = persona?.name ?: personaId.replace("_", " ").replaceFirstChar { it.uppercase() }
    val color = persona?.seatColorArgb?.let { Color(it) } ?: BrandTokens.BrassAged
    val pct = if (record.played == 0) 0 else (record.wins * 100 / record.played)
    val h2hDesc = stringResource(Res.string.a11y_h2h_row, name, record.played, record.wins, pct)
    HairlineRow(
        showDivider = showDivider,
        verticalPadding = 10.dp,
        modifier =
            Modifier.semantics {
                contentDescription = h2hDesc
            },
    ) {
        BrassToken(monogram = persona?.monogram ?: "?", fill = color, size = 32.dp)
        Text(
            name,
            style = KursiType.body.copy(fontSize = 13.sp),
            color = KursiNeutrals.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${record.wins}/${record.played}  ($pct%)",
            style = KursiType.numeric.copy(fontSize = 12.sp),
            color = if (pct >= 50) KursiSemantics.Success else KursiNeutrals.TextSecondary,
        )
    }
}

/**
 * Compact career strip for the Home hub — a single-line lifetime summary that taps through to the
 * full [CareerScreen]. Hidden (returns nothing visible) when no games have been recorded yet.
 */
@Composable
fun CareerStrip(
    ledger: StatsLedger,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ledger.games == 0) return
    val winPct = (ledger.winRate * 100).roundToInt()
    val stripDesc = stringResource(Res.string.a11y_career_strip, ledger.wins, ledger.games, winPct)
    HairlineRow(
        onClick = onOpen,
        verticalPadding = 11.dp,
        modifier =
            modifier.semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = stripDesc
            },
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(BrandTokens.GoldAntique.copy(alpha = 0.14f))
                    .border(1.5.dp, BrandTokens.GoldAntique, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$winPct", style = KursiType.numeric.copy(fontSize = 11.sp), color = BrandTokens.GoldAntique)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "ROZNAMCHA",
                style = KursiType.caption.copy(fontSize = 8.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                color = BrandTokens.BrassAged,
            )
            Text(
                stringResource(Res.string.career_strip_summary, ledger.wins, ledger.losses, winPct),
                style = KursiType.numeric.copy(fontSize = 13.sp),
                color = BrandTokens.GoldAntique,
            )
        }
        Text("›", style = KursiType.title.copy(fontSize = 16.sp), color = KursiNeutrals.TextMuted)
    }
}
