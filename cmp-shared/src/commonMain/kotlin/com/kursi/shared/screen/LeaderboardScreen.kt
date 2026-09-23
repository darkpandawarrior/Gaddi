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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.kursi.core.prefs.DailyStanding
import com.kursi.core.prefs.RankedStanding
import com.kursi.core.prefs.SarkariRank
import com.kursi.designsystem.*
import com.kursi.shared.strings.LocalGaddiStrings
import com.kursi.shared.strings.rankName
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.a11y_rank_plaque
import kursi.core.designsystem.generated.resources.a11y_ranked_strip
import kursi.core.designsystem.generated.resources.a11y_rating_history
import kursi.core.designsystem.generated.resources.a11y_standing_row
import org.jetbrains.compose.resources.stringResource

/**
 * M6d — Leaderboard / Darja-Suchi: the LOCAL standings screen. Shows the ranked ELO standing (rating
 * + sarkari rank tier + rating-history spark-line), best results, and the daily-challenge streak.
 * Server-backed standings are deferred to the M7 online tie-in; this reads only persisted AppPrefs
 * behind a clean seam (the footer notes the pending online table).
 *
 * AAA pass: lit ground, engraved nav chrome, ONE raised focal card (the rank plaque) above
 * hairline-row sections underneath — the same "Sarkari Noir" hierarchy as Career/Gauntlet/Home.
 */
@Composable
fun LeaderboardScreen(
    ranked: RankedStanding,
    daily: DailyStanding,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * M7 ONLINE TIE-IN — the live online-standings seam. When non-null it renders an ONLINE
     * DARJA-SUCHI card reading the server-backed rows; when the list is empty it shows the connected
     * "ranking is forming" sub-line. Null keeps the original local-only screen with the pending footer.
     */
    onlineStandings: OnlineStandings? = null,
) {
    val s = LocalGaddiStrings.current
    val scroll = rememberScrollState()
    Column(modifier = modifier.fillMaxSize().litGround()) {
        EngravedNavHeader(
            title = s.leaderboardHeader,
            onBack = onBack,
            backLabel = s.back,
            modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
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
                if (ranked.isProvisional && !daily.hasPlayed) {
                    LeaderboardEmpty()
                } else {
                    // The one raised focal card of the screen — everything below reads on the ground.
                    RankPlaque(ranked)
                    RatingHistoryCard(ranked)
                    if (daily.hasPlayed) DailyStandingCard(daily)
                }
                // M7 — the live online standings seam. Shown only when connected to a server;
                // otherwise the local screen falls back to the honest "pending" footer below.
                if (onlineStandings != null) {
                    OnlineStandingsCard(onlineStandings)
                } else {
                    Text(
                        s.leaderboardServerPending,
                        style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                        color = GaddiNeutrals.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardEmpty() {
    val s = LocalGaddiStrings.current
    Text(
        s.leaderboardEmpty,
        style = GaddiType.body.copy(fontSize = 12.sp),
        color = GaddiNeutrals.TextSecondary,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp)
                .semantics { contentDescription = s.leaderboardEmpty },
    )
}

/** The headline rank plaque — the one raised focal card (non-negotiable #5): rating roundel + sarkari
 *  rank name + a tier-progress bar. Real cast shadow, no outline framing the block. */
@Composable
private fun RankPlaque(ranked: RankedStanding) {
    val s = LocalGaddiStrings.current
    val rank = ranked.rank
    val rankColor = rankColor(rank)
    val rankPlaqueDesc = stringResource(Res.string.a11y_rank_plaque, s.rankName(rank), ranked.rating, ranked.peak, ranked.games)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(10.dp, Squircle(GaddiRadii.md), clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                .clip(Squircle(GaddiRadii.md))
                .background(
                    Brush.verticalGradient(
                        listOf(rankColor.copy(alpha = 0.22f), BrandTokens.BrassDark.copy(alpha = 0.32f)),
                    ),
                ).padding(horizontal = 20.dp, vertical = 20.dp)
                .semantics {
                    contentDescription = rankPlaqueDesc
                },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            s.leaderboardRankSection,
            style = GaddiType.label_sm.dmMono().copy(fontSize = 11.sp, letterSpacing = 2.sp),
            color = BrandTokens.BrassAged,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                modifier =
                    Modifier
                        .size(76.dp)
                        .shadow(6.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(rankColor.copy(alpha = 0.28f), BrandTokens.TeakDark)))
                        .border(2.dp, rankColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("${ranked.rating}", style = GaddiType.display.copy(fontSize = 20.sp), color = rankColor)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    s.rankName(rank),
                    style = GaddiType.display.rozha().copy(fontSize = 20.sp),
                    color = GaddiNeutrals.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val nextHint =
                    rank.next?.let { nx ->
                        s.rankedToNext((nx.floor - ranked.rating).coerceAtLeast(0), s.rankName(nx))
                    } ?: s.rankedTopTier
                Text(
                    nextHint,
                    style = GaddiType.body.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic),
                    color = GaddiNeutrals.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Tier-progress bar
        TierProgressBar(progress = ranked.tierProgress, color = rankColor)
        // Peak / games row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            MiniStat(s.leaderboardPeakLabel, "${ranked.peak}", Modifier.weight(1f))
            MiniStat(s.leaderboardGamesLabel, "${ranked.games}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun TierProgressBar(
    progress: Float,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(BrandTokens.TeakInk.copy(alpha = 0.6f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color))),
        )
    }
}

/** A bare stat readout — DM Mono numeral over a caption label, no box (non-negotiable #1 + #5). */
@Composable
private fun MiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.semantics { contentDescription = "$label $value" }) {
        Text(value, style = GaddiType.numeric.copy(fontSize = 18.sp), color = BrandTokens.GoldAntique)
        Text(label, style = GaddiType.caption.copy(fontSize = 9.sp), color = GaddiNeutrals.TextMuted)
    }
}

/** Rating spark-line over the retained history window. */
@Composable
private fun RatingHistoryCard(ranked: RankedStanding) {
    val s = LocalGaddiStrings.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EngravedHeader(eyebrow = s.leaderboardHistorySection)
        val pts = ranked.history
        if (pts.size < 2) {
            Text(
                s.leaderboardHistoryEmpty,
                style = GaddiType.body.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic),
                color = GaddiNeutrals.TextSecondary,
            )
        } else {
            val ratingHistoryDesc = stringResource(Res.string.a11y_rating_history, pts.size, pts.first(), pts.last())
            RatingSparkline(
                points = pts,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .semantics {
                            contentDescription = ratingHistoryDesc
                        },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${pts.min()}", style = GaddiType.caption.copy(fontSize = 9.sp), color = GaddiNeutrals.TextMuted)
                Text("${pts.max()}", style = GaddiType.caption.copy(fontSize = 9.sp), color = BrandTokens.GoldAntique)
            }
        }
    }
}

@Composable
private fun RatingSparkline(
    points: List<Int>,
    modifier: Modifier = Modifier,
) {
    val lo = points.min()
    val hi = points.max()
    val span = (hi - lo).coerceAtLeast(1)
    // Drawn directly on the lit ground — a chart, not a bordered content panel (non-negotiable #1).
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val stepX = w / (points.size - 1)

        fun yAt(v: Int): Float = h - ((v - lo).toFloat() / span) * h
        // Baseline rule
        drawLine(
            color = BrandTokens.BrassDark.copy(alpha = 0.35f),
            start = Offset(0f, h),
            end = Offset(w, h),
            strokeWidth = 1f,
        )
        // Spark-line
        for (i in 0 until points.size - 1) {
            drawLine(
                color = BrandTokens.GoldAntique,
                start = Offset(i * stepX, yAt(points[i])),
                end = Offset((i + 1) * stepX, yAt(points[i + 1])),
                strokeWidth = 2f,
            )
        }
        // Last point marker
        val lastX = (points.size - 1) * stepX
        drawCircle(
            color = BrandTokens.GoldAntique,
            radius = 3f,
            center = Offset(lastX, yAt(points.last())),
        )
        drawCircle(
            color = BrandTokens.GoldAntique,
            radius = 5f,
            center = Offset(lastX, yAt(points.last())),
            style = Stroke(width = 1f),
        )
    }
}

/** The daily-challenge standing card: streak + best streak + lifetime daily wins. */
@Composable
private fun DailyStandingCard(daily: DailyStanding) {
    val s = LocalGaddiStrings.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EngravedHeader(eyebrow = s.leaderboardDailySection)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            MiniStat(s.leaderboardStreakLabel, "${daily.streak}", Modifier.weight(1f))
            MiniStat(s.leaderboardBestStreakLabel, "${daily.bestStreak}", Modifier.weight(1f))
            MiniStat(s.leaderboardDailyWinsLabel, "${daily.won}/${daily.played}", Modifier.weight(1f))
        }
    }
}

// ─────────────────────────── M7 online standings seam ─────────────────────────

/**
 * The server-backed online standings, surfaced when the app is connected to a Gaddi server. This is the
 * clean SEAM the M6d footer promised: the screen reads these rows when present and otherwise stays the
 * local-only standings. The server today has no standings endpoint, so the app supplies [rows] from the
 * live ranked ladder (the player's own standing) and flips [connected] off when no server is reachable —
 * the moment the server gains a `/standings` route, only the data source changes, not this screen.
 *
 * @param connected whether a server connection backs these rows right now.
 * @param rows      the standings rows, best-first (empty while the online ranking is still forming).
 */
data class OnlineStandings(
    val connected: Boolean,
    val rows: List<OnlineStandingRow> = emptyList(),
)

/** One online-standings row: a rank position, a display name, and a rating. */
data class OnlineStandingRow(
    val position: Int,
    val name: String,
    val rating: Int,
    /** True for the local player's own row (highlighted). */
    val isMe: Boolean = false,
)

@Composable
private fun OnlineStandingsCard(standings: OnlineStandings) {
    val s = LocalGaddiStrings.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = if (standings.connected) s.leaderboardOnlineConnectedSub else s.leaderboardOnlineOfflineSub
                },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EngravedHeader(eyebrow = s.leaderboardOnlineSection) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (standings.connected) GaddiSemantics.Success else BrandTokens.BrassDark),
            )
        }
        Text(
            if (standings.connected) s.leaderboardOnlineConnectedSub else s.leaderboardOnlineOfflineSub,
            style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
            color = GaddiNeutrals.TextSecondary,
        )
        if (standings.rows.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                standings.rows.forEachIndexed { i, row -> OnlineStandingRowView(row, showDivider = i != standings.rows.lastIndex) }
            }
        }
    }
}

@Composable
private fun OnlineStandingRowView(
    row: OnlineStandingRow,
    showDivider: Boolean,
) {
    val standingRowDesc = stringResource(Res.string.a11y_standing_row, row.position, row.name, row.rating)
    HairlineRow(
        showDivider = showDivider,
        verticalPadding = 10.dp,
        modifier = Modifier.semantics { contentDescription = standingRowDesc },
    ) {
        BrassToken(
            monogram = "${row.position}",
            fill = if (row.isMe) BrandTokens.GoldAntique else BrandTokens.TeakDark,
            size = 30.dp,
        )
        Text(
            row.name,
            style = GaddiType.name.copy(fontSize = 13.sp),
            color = if (row.isMe) BrandTokens.GoldAntique else GaddiNeutrals.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("${row.rating}", style = GaddiType.numeric.copy(fontSize = 13.sp), color = BrandTokens.GoldAntique)
    }
}

/** Per-tier accent colour — climbs from brass through gold to the success green at the top. */
private fun rankColor(rank: SarkariRank): Color =
    when (rank) {
        SarkariRank.CLERK -> BrandTokens.BrassAged
        SarkariRank.SECTION_OFFICER -> BrandTokens.BrassAged
        SarkariRank.UNDER_SECRETARY -> BrandTokens.GoldAntique
        SarkariRank.DEPUTY_SECRETARY -> BrandTokens.GoldAntique
        SarkariRank.JOINT_SECRETARY -> BrandTokens.GoldAntique
        SarkariRank.SECRETARY -> GaddiSemantics.Success
        SarkariRank.CABINET_SECRETARY -> GaddiSemantics.Success
    }

// ─────────────────────────── Home strip + daily entry (reused) ────────────────

/**
 * Compact ranked strip for the Home hub — a single-line rank + rating summary that taps through to
 * the [LeaderboardScreen]. Always rendered (even provisional) so the player sees their starting rank.
 */
@Composable
fun RankedStrip(
    ranked: RankedStanding,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalGaddiStrings.current
    val rank = ranked.rank
    val rankColor = rankColor(rank)
    val rankedStripDesc = stringResource(Res.string.a11y_ranked_strip, s.rankName(rank), ranked.rating)
    HairlineRow(
        onClick = onOpen,
        verticalPadding = 11.dp,
        modifier =
            modifier.semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = rankedStripDesc
            },
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(rankColor.copy(alpha = 0.14f))
                    .border(1.5.dp, rankColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("${ranked.rating}", style = GaddiType.numeric.copy(fontSize = 11.sp), color = rankColor)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    s.rankedStripTag,
                    style = GaddiType.caption.copy(fontSize = 8.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                    color = BrandTokens.BrassAged,
                )
                Text(
                    s.rankName(rank),
                    style = GaddiType.title.copy(fontSize = 13.sp),
                    color = GaddiNeutrals.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val sub =
                if (ranked.isProvisional) {
                    s.rankedProvisional
                } else {
                    rank.next?.let { nx ->
                        s.rankedToNext((nx.floor - ranked.rating).coerceAtLeast(0), s.rankName(nx))
                    } ?: s.rankedTopTier
                }
            Text(
                sub,
                style = GaddiType.caption.copy(fontSize = 9.sp, fontStyle = FontStyle.Italic),
                color = GaddiNeutrals.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("›", style = GaddiType.title.copy(fontSize = 16.sp), color = GaddiNeutrals.TextMuted)
    }
}

/**
 * Home "Aaj ki Chunauti" daily-challenge entry strip. Shows whether today's daily is still open (with
 * a stamp badge) and the current streak. Tapping starts (or, when done, re-opens) today's challenge.
 */
@Composable
fun DailyChallengeStrip(
    daily: DailyStanding,
    todayDone: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalGaddiStrings.current
    val accent = if (todayDone) BrandTokens.BrassAged else BrandTokens.StampRed
    HairlineRow(
        onClick = onStart,
        verticalPadding = 11.dp,
        modifier =
            modifier.semantics(mergeDescendants = true) {
                contentDescription =
                    if (todayDone) "${s.dailyCta}. ${s.dailyDoneSub}" else "${s.dailyCta}. ${s.dailyCtaSub}"
            },
    ) {
        Text("✦", style = GaddiType.title.copy(fontSize = 16.sp), color = accent)
        Column(modifier = Modifier.weight(1f)) {
            Text(s.dailyCta, style = GaddiType.title.copy(fontSize = 14.sp), color = GaddiNeutrals.TextPrimary)
            Text(
                if (todayDone) s.dailyDoneSub else s.dailyCtaSub,
                style = GaddiType.caption.copy(fontSize = 9.sp, fontStyle = FontStyle.Italic),
                color = GaddiNeutrals.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (daily.streak > 0) {
                Text(
                    s.dailyStreakLabel(daily.streak),
                    style = GaddiType.caption.copy(fontSize = 9.sp),
                    color = BrandTokens.GoldAntique,
                )
            }
        }
        // Stamp badge — a small crafted tag, allowed to keep a hairline rim (non-negotiable #4).
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent.copy(alpha = 0.15f))
                    .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text(
                if (todayDone) s.dailyDoneBadge else s.dailyOpenBadge,
                style = GaddiType.caption.copy(fontSize = 8.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold),
                color = accent,
            )
        }
    }
}
