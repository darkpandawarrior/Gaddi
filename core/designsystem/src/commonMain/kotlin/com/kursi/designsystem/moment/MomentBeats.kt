package com.kursi.designsystem.moment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.BrandTokens
import com.kursi.designsystem.GaddiNeutrals
import com.kursi.designsystem.GaddiType
import com.siddharth.kmp.common.easeInQuart
import com.siddharth.kmp.common.easeOutCubic
import com.siddharth.kmp.common.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Off-screen entry point for FDI/foreign-incoming moves — far past the top-right of any real
 * table, so the token visibly flies in from outside the board.
 */
private val OffTableEntryDefault = Offset(2000f, -200f)

/**
 * Where a moment plays when the seat it names has no measured anchor yet (first frame, or a seat
 * that has not been laid out). Roughly the middle of the table, so the beat is visible rather than
 * stuck in a corner.
 */
private val TableCentreFallback = Offset(500f, 500f)

// ═══════════════════════════════════════════════════════════════════════════════
// MomentBeats.kt — 13 beat composables, each a composition of the 5 primitives.
// Design: gaddi-plan/docs/15c_action_moments.md §2 (the beat sheet).
//
// Each beat receives:
//   m       — the typed GaddiMoment (for data: seats, roleHue, truthful, etc.)
//   progress — the moment's full 0→1 float (sub-ranges mapped internally).
//   anchors  — TableAnchors with seat centers + treasury center in overlay coords.
//
// Reduced-motion callers never reach these: the overlay collapses to a 120ms
// crossfade + TickerSlip at the moment level.
// ═══════════════════════════════════════════════════════════════════════════════

// ─────────────────────────── TableAnchors ────────────────────────────────────

/**
 * Geometry bag passed into every beat so moments are layout-agnostic.
 * Filled by the game screen from its measured composable positions.
 * All offsets are in the overlay's coordinate space (Box.fillMaxSize()).
 */
data class TableAnchors(
    /** Center of each active seat, keyed by SeatId. */
    val seatCenters: Map<SeatId, Offset>,
    /** Center of the treasury safe. */
    val treasuryCenter: Offset,
    /** Off-screen entry point for FDI/foreign-incoming moves. Defaults to top-right corner. */
    val offTableEntry: Offset = OffTableEntryDefault,
) {
    fun seat(id: SeatId): Offset = seatCenters[id] ?: TableCentreFallback
}

// ─────────────────────────── 1. Income ───────────────────────────────────────

/**
 * Dehaadi / Income (+1): humblest action — single coin arc, no stamp.
 * Deliberate contrast: the absence of a stamp makes bigger moments land harder.
 */
@Composable
internal fun IncomeBeat(
    m: GaddiMoment.Income,
    progress: Float,
    anchors: TableAnchors,
) {
    CoinTrail(
        from = anchors.treasuryCenter,
        to = anchors.seat(m.actorSeat),
        progress = progress,
        count = 1,
    )
}

// ─────────────────────────── 2. ForeignAid ───────────────────────────────────

/**
 * FDI / Foreign Aid (+2): paper-plane arc entry from off-table edge, then 2 coins.
 */
@Composable
internal fun ForeignAidBeat(
    m: GaddiMoment.ForeignAid,
    progress: Float,
    anchors: TableAnchors,
) {
    // Phase 1 (0→0.45): plane arc — represented as a single coin trail from the off-table point
    val planeProgress = (progress / 0.45f).coerceAtMost(1f)
    // Phase 2 (0.45→1.0): second coin
    val coin2Progress = ((progress - 0.45f) / 0.55f).coerceAtLeast(0f)

    CoinTrail(
        from = anchors.offTableEntry,
        to = anchors.seat(m.actorSeat),
        progress = planeProgress,
        count = 1,
    )
    if (progress > 0.45f) {
        CoinTrail(
            from = anchors.offTableEntry,
            to = anchors.seat(m.actorSeat),
            progress = coin2Progress,
            count = 2,
        )
    }
}

// ─────────────────────────── 3. Tax ──────────────────────────────────────────

/**
 * Ghotala / Tax (+3, Neta): stamp THEN 3-coin spill.
 * Claim first, payout second — teaches "stamp = role assertion".
 */
@Composable
internal fun TaxBeat(
    m: GaddiMoment.Tax,
    progress: Float,
    anchors: TableAnchors,
) {
    val seatCenter = anchors.seat(m.actorSeat)

    // Stamp occupies first 55% of the beat
    val stampProgress = (progress / 0.55f).coerceAtMost(1f)

    // 3 coins staggered over the remaining 45%, from the stamp impact point
    val coinStart = 0.45f
    val coinStep = 0.18f

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RubberStamp(
            glyphText = "GHOTALA",
            tint = m.roleHue,
            progress = stampProgress,
            impactCenter = seatCenter,
            startRotationDeg = 6f,
        )
    }

    repeat(3) { i ->
        val coinProgress =
            ((progress - (coinStart + i * coinStep)) / 0.35f)
                .coerceIn(0f, 1f)
        if (coinProgress > 0f) {
            CoinTrail(
                from = seatCenter,
                to = anchors.seat(m.actorSeat),
                progress = coinProgress,
                count = i + 1,
            )
        }
    }
}

// ─────────────────────────── 4. Steal ────────────────────────────────────────

/**
 * Vasooli / Steal (2, Babu): stamp victim seat then yank 2 coins to actor.
 * The yank "resists" — overshoot on the CoinTrail conveys the taking.
 */
@Composable
internal fun StealBeat(
    m: GaddiMoment.Steal,
    progress: Float,
    anchors: TableAnchors,
) {
    val victimCenter = anchors.seat(m.victim)
    val actorCenter = anchors.seat(m.actorSeat)

    // Stamp on victim seat (first 50%)
    val stampProgress = (progress / 0.50f).coerceAtMost(1f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RubberStamp(
            glyphText = "VASOOLI",
            tint = m.roleHue,
            progress = stampProgress,
            impactCenter = victimCenter,
        )
    }

    // 2 coins yank from victim → actor (starting at 40%)
    repeat(2) { i ->
        val coinProgress = ((progress - (0.40f + i * 0.15f)) / 0.45f).coerceIn(0f, 1f)
        if (coinProgress > 0f) {
            CoinTrail(
                from = victimCenter,
                to = actorCenter,
                progress = coinProgress,
                count = -(i + 1), // negative delta on victim (rendered by the beat tracker)
            )
        }
    }
}

// ─────────────────────────── 5. Assassinate ──────────────────────────────────

/**
 * Supari / Assassinate: chit slides actor→target, stamp + hazard flash, fee coins to treasury.
 */
@Composable
internal fun AssassinateBeat(
    m: GaddiMoment.Assassinate,
    progress: Float,
    anchors: TableAnchors,
) {
    val actorCenter = anchors.seat(m.actorSeat)
    val targetCenter = anchors.seat(m.target)

    // TickerSlip as the "chit" — slides in phase (0→0.45)
    val slipProgress = (progress / 0.45f).coerceAtMost(1f)
    TickerSlip(
        glyphText = "SUPARI",
        effectText =
            if (m.actorName.isNotEmpty() && m.targetName.isNotEmpty()) {
                "${m.actorName} → ${m.targetName}"
            } else {
                "supari"
            },
        tint = m.roleHue,
        progress = slipProgress,
    )

    // Stamp on target seat (0.40→0.80)
    val stampProgress = ((progress - 0.40f) / 0.40f).coerceIn(0f, 1f)
    if (stampProgress > 0f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RubberStamp(
                glyphText = "SUPARI",
                tint = m.roleHue,
                progress = stampProgress,
                impactCenter = targetCenter,
            )
        }
    }

    // Hazard flash on target seat (0.50–0.75) — a brief red outline pulse
    if (progress in 0.50f..0.75f) {
        val flashAlpha = 1f - ((progress - 0.50f) / 0.25f)
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = BrandTokens.StampRed.copy(alpha = flashAlpha * 0.5f),
                radius = 48.dp.toPx(),
                center = targetCenter,
                style = Stroke(4.dp.toPx()),
            )
        }
    }

    // 3 fee coins actor→treasury (0.55→1.0)
    repeat(3) { i ->
        val coinProgress = ((progress - (0.55f + i * 0.10f)) / 0.35f).coerceIn(0f, 1f)
        if (coinProgress > 0f) {
            CoinTrail(
                from = actorCenter,
                to = anchors.treasuryCenter,
                progress = coinProgress,
                count = i + 1,
            )
        }
    }
}

// ─────────────────────────── 6. Exchange ─────────────────────────────────────

/**
 * Setting / Exchange: cards fan, riso shimmer shuffle, interlocking-arrows faint stamp.
 * Hidden-info integrity: no role data is shown.
 */
@Composable
internal fun ExchangeBeat(
    m: GaddiMoment.Exchange,
    progress: Float,
    anchors: TableAnchors,
) {
    val seatCenter = anchors.seat(m.actorSeat)

    // Card 1 flip (0→0.50)
    val flip1Progress = (progress / 0.50f).coerceAtMost(1f)
    // Card 2 flip (0.25→0.75)
    val flip2Progress = ((progress - 0.25f) / 0.50f).coerceIn(0f, 1f)

    // Two card flips — front content stays blank (hidden info)
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .offset { IntOffset((seatCenter.x - 30f).roundToInt(), (seatCenter.y - 20f).roundToInt()) }
                    .wrapContentSize(),
        ) {
            CardFlip(
                progress = flip1Progress,
                modifier = Modifier,
                frontContent = { CardBlankFace(m.roleHue) },
            )
        }
        Box(
            modifier =
                Modifier
                    .offset { IntOffset((seatCenter.x + 10f).roundToInt(), (seatCenter.y + 10f).roundToInt()) }
                    .wrapContentSize(),
        ) {
            CardFlip(
                progress = flip2Progress,
                modifier = Modifier,
                frontContent = { CardBlankFace(m.roleHue) },
            )
        }
    }

    // Faint interlocking-arrows stamp (0.65→1.0)
    val stampProgress = ((progress - 0.65f) / 0.35f).coerceIn(0f, 1f)
    if (stampProgress > 0f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RubberStamp(
                glyphText = "⇌",
                tint = m.roleHue.copy(alpha = 0.5f),
                progress = stampProgress,
                impactCenter = seatCenter,
                startRotationDeg = 3f,
            )
        }
    }
}

/** Blank card face — hidden info during exchange. */
@Composable
private fun CardBlankFace(tint: Color) {
    Box(
        modifier =
            Modifier
                .wrapContentSize()
                .padding(8.dp),
    ) {
        Canvas(Modifier.wrapContentSize()) {
            drawRect(
                color = BrandTokens.PaperCream.copy(alpha = 0.85f),
                size = size,
            )
            drawRect(
                color = tint.copy(alpha = 0.25f),
                size = size,
                style = Stroke(1.dp.toPx()),
            )
        }
    }
}

// ─────────────────────────── 7. Coup ─────────────────────────────────────────

/**
 * Khela / Coup (1.4s, HeavyLong): THE hero action.
 * Table dim → striker flick → table-wide shockwave → 150ms slow-mo → ChairTip begins.
 */
@Composable
internal fun CoupBeat(
    m: GaddiMoment.Coup,
    progress: Float,
    anchors: TableAnchors,
) {
    val actorCenter = anchors.seat(m.actorSeat)
    val targetCenter = anchors.seat(m.target)

    // Full-screen vignette dim (0→0.20, holds through 0.80)
    val dimAlpha =
        when {
            progress < 0.10f -> progress / 0.10f * 0.55f
            progress < 0.80f -> 0.55f
            else -> lerp(0.55f, 0f, (progress - 0.80f) / 0.20f)
        }
    if (dimAlpha > 0f) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha)),
        )
    }

    // Striker glyph "⬡" flicks across frame (0.10→0.45)
    val strikerProgress = ((progress - 0.10f) / 0.35f).coerceIn(0f, 1f)
    if (strikerProgress in 0f..1f) {
        val strikerPos =
            Offset(
                x = lerp(actorCenter.x, targetCenter.x, easeInQuart(strikerProgress)),
                y = lerp(actorCenter.y, targetCenter.y, easeInQuart(strikerProgress)),
            )
        val strikerAlpha = if (strikerProgress > 0.85f) lerp(1f, 0f, (strikerProgress - 0.85f) / 0.15f) else 1f
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = BrandTokens.GoldAntique.copy(alpha = strikerAlpha),
                radius = 14.dp.toPx(),
                center = strikerPos,
            )
            drawCircle(
                color = BrandTokens.BrassDark.copy(alpha = strikerAlpha * 0.8f),
                radius = 14.dp.toPx(),
                center = strikerPos,
                style = Stroke(2.dp.toPx()),
            )
        }
    }

    // Table-wide halftone shockwave centered on target (0.40→0.80) — large burst
    if (progress in 0.40f..0.80f) {
        val shockProgress = (progress - 0.40f) / 0.40f
        // Large shockwave: override radius to ~200dp
        Canvas(Modifier.fillMaxSize()) {
            val r = lerp(20.dp.toPx(), 200.dp.toPx(), easeOutCubic(shockProgress))
            val alpha = (1f - shockProgress) * 0.5f
            val dots = 28
            repeat(dots) { i ->
                val ang = (i.toFloat() / dots.toFloat()) * 2f * PI.toFloat()
                val p = targetCenter + Offset(cos(ang) * r, sin(ang) * r)
                drawCircle(
                    BrandTokens.GoldAntique.copy(alpha = alpha),
                    radius = lerp(4.dp.toPx(), 1.5.dp.toPx(), shockProgress),
                    center = p,
                )
            }
        }
    }

    // Oversized KHELA stamp dead-center (0.42→1.0) — the slow-mo hold is baked into progress
    val stampProgress = ((progress - 0.42f) / 0.58f).coerceIn(0f, 1f)
    if (stampProgress > 0f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RubberStamp(
                glyphText = "KHELA",
                tint = BrandTokens.GoldAntique,
                progress = stampProgress,
                impactCenter = targetCenter,
                startRotationDeg = 6f,
            )
        }
    }

    // ChairTip begins at target seat (0.65→1.0) — flows into Elimination moment next
    val chairProgress = ((progress - 0.65f) / 0.35f).coerceIn(0f, 1f)
    if (chairProgress > 0f) {
        ChairTip(
            progress = chairProgress,
            tint = BrandTokens.PaperCream.copy(alpha = 0.7f),
            seatCenter = targetCenter,
        )
    }
}

// ─────────────────────────── 8. Block ────────────────────────────────────────

/**
 * Block: blocker role glyph stamps over the action's ticker slip with a "no-ring".
 * The slip dims beneath it — cause-and-effect physical cancel.
 */
@Composable
internal fun BlockBeat(
    m: GaddiMoment.Block,
    progress: Float,
    anchors: TableAnchors,
) {
    val blockerCenter = anchors.seat(m.actorSeat)

    // TickerSlip: the blocked action's record slides in then dims
    val slipProgress = (progress / 0.40f).coerceAtMost(1f)
    val slipAlpha = if (progress > 0.55f) lerp(1f, 0.35f, (progress - 0.55f) / 0.30f) else 1f
    Box(Modifier.alpha(slipAlpha)) {
        TickerSlip(
            glyphText = "ROKA",
            effectText = if (m.blockerName.isNotEmpty()) "${m.blockerName} blocked" else "blocked",
            tint = m.roleHue.copy(alpha = 0.5f),
            progress = slipProgress,
        )
    }

    // Blocker stamp over the slip (0.35→1.0) with a "no-ring" (cancel circle drawn in Canvas)
    val stampProgress = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
    if (stampProgress > 0f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RubberStamp(
                glyphText = "ROKA!",
                tint = m.roleHue,
                progress = stampProgress,
                impactCenter = blockerCenter,
            )
        }
        // Cancel circle (no-ring)
        val cancelAlpha = if (stampProgress > 0.60f) (stampProgress - 0.60f) / 0.40f else 0f
        if (cancelAlpha > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val r = 30.dp.toPx()
                drawCircle(
                    color = BrandTokens.StampRed.copy(alpha = cancelAlpha * 0.7f),
                    radius = r,
                    center = blockerCenter,
                    style = Stroke(3.dp.toPx()),
                )
                drawLine(
                    color = BrandTokens.StampRed.copy(alpha = cancelAlpha * 0.7f),
                    start = blockerCenter + Offset(-r * 0.7f, r * 0.7f),
                    end = blockerCenter + Offset(r * 0.7f, -r * 0.7f),
                    strokeWidth = 3.dp.toPx(),
                )
            }
        }
    }
}

// ─────────────────────────── 9. Challenge ────────────────────────────────────

/**
 * Challenge thrown — torn paper connector snaps taut from challenger → claimant.
 * Deliberate withholding: no outcome, all suspense.
 */
@Composable
internal fun ChallengeBeat(
    m: GaddiMoment.Challenge,
    progress: Float,
    anchors: TableAnchors,
) {
    val challengerCenter = anchors.seat(m.actorSeat)
    val claimantCenter = anchors.seat(m.claimant)

    // Torn-paper line draws itself along the arc (0→0.65)
    val lineProgress = (progress / 0.65f).coerceAtMost(1f)
    val lineAlpha = if (progress > 0.85f) lerp(1f, 0.3f, (progress - 0.85f) / 0.15f) else 1f

    Canvas(Modifier.fillMaxSize().alpha(lineAlpha)) {
        val endX = lerp(challengerCenter.x, claimantCenter.x, easeOutCubic(lineProgress))
        val endY = lerp(challengerCenter.y, claimantCenter.y, easeOutCubic(lineProgress))

        // Jagged torn-paper path
        val segments = 10
        val path = Path()
        path.moveTo(challengerCenter.x, challengerCenter.y)
        for (s in 1..segments) {
            val t = s.toFloat() / segments
            if (t * lerp(challengerCenter.x, claimantCenter.x, 1f) > endX) break
            val bx = lerp(challengerCenter.x, claimantCenter.x, t)
            val by = lerp(challengerCenter.y, claimantCenter.y, t)
            val jag = if (s % 2 == 0) 6f else -6f
            path.lineTo(bx + jag, by + jag)
        }
        path.lineTo(endX, endY)

        drawPath(
            path = path,
            color = BrandTokens.StampRed.copy(alpha = 0.8f),
            style =
                Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f)),
                ),
        )
    }

    // Claimant seat nervous shiver (0.60→0.85) — tiny oscillating offset
    val shiverPhase = ((progress - 0.60f) / 0.25f).coerceIn(0f, 1f)
    if (shiverPhase > 0f) {
        val shivX = sin(shiverPhase * 6f * PI.toFloat()) * 3f
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = BrandTokens.PendingAmber.copy(alpha = shiverPhase * 0.4f),
                radius = 28.dp.toPx(),
                center = claimantCenter + Offset(shivX, 0f),
                style = Stroke(2.dp.toPx()),
            )
        }
    }
}

// ─────────────────────────── 10. Reveal ──────────────────────────────────────

/**
 * THE MONEY MOMENT — challenge resolves.
 * CardFlip → 180ms hold → verdict stamp.
 */
@Composable
internal fun RevealBeat(
    m: GaddiMoment.Reveal,
    progress: Float,
    anchors: TableAnchors,
) {
    val claimantCenter = anchors.seat(m.claimant)

    // CardFlip (0→0.50): the card turns face-up
    val flipProgress = (progress / 0.50f).coerceAtMost(1f)
    val frontContent: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = m.claimedRole,
                style =
                    TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (m.truthful) m.roleHue else BrandTokens.StampRed,
                        textAlign = TextAlign.Center,
                    ),
            )
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .offset {
                    IntOffset(
                        (claimantCenter.x - 40.dp.toPx()).roundToInt(),
                        (claimantCenter.y - 60.dp.toPx()).roundToInt(),
                    )
                },
    ) {
        CardFlip(
            progress = flipProgress,
            frontContent = frontContent,
        )
    }

    // Hold window (0.50–0.65): card is up, verdict not yet declared — the poker-river moment
    // Nothing fires here intentionally — the silence is the tension.

    // Verdict stamp (0.65→1.0)
    val stampProgress = ((progress - 0.65f) / 0.35f).coerceIn(0f, 1f)
    if (stampProgress > 0f) {
        val (stampWord, stampColor) =
            if (m.truthful) {
                "SACH!" to m.roleHue
            } else {
                "JHOOTH!" to BrandTokens.StampRed
            }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RubberStamp(
                glyphText = stampWord,
                tint = stampColor,
                progress = stampProgress,
                impactCenter = claimantCenter,
                startRotationDeg = if (m.truthful) 4f else -8f, // caught bluff tilts more sharply
            )
        }

        // Truthful: gold halftone burst
        if (m.truthful && stampProgress > 0.40f) {
            HalftoneBurst(
                center = claimantCenter,
                progress = (stampProgress - 0.40f) / 0.60f,
                tint = BrandTokens.GoldAntique,
            )
        }

        // Bluff caught: card desaturates (represented by an overlay fade to grey)
        if (!m.truthful && stampProgress > 0.30f) {
            val greyAlpha = ((stampProgress - 0.30f) / 0.70f) * 0.40f
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    color = Color.Gray.copy(alpha = greyAlpha),
                    topLeft = Offset(claimantCenter.x - 44.dp.toPx(), claimantCenter.y - 62.dp.toPx()),
                    size =
                        androidx.compose.ui.geometry
                            .Size(88.dp.toPx(), 124.dp.toPx()),
                )
            }
        }
    }
}

// ─────────────────────────── 11. InfluenceLoss ───────────────────────────────

/**
 * Influence loss — card flip + EXPOSED stamp + desaturation.
 * The game's universal "you took a hit" punctuation.
 */
@Composable
internal fun InfluenceLossBeat(
    m: GaddiMoment.InfluenceLoss,
    progress: Float,
    anchors: TableAnchors,
) {
    val seatCenter = anchors.seat(m.actorSeat)

    // CardFlip (0→0.40): card turns face-up, revealing the lost role
    val flipProgress = (progress / 0.40f).coerceAtMost(1f)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .offset {
                    IntOffset(
                        (seatCenter.x - 36.dp.toPx()).roundToInt(),
                        (seatCenter.y - 52.dp.toPx()).roundToInt(),
                    )
                },
    ) {
        CardFlip(
            progress = flipProgress,
            frontContent = {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = m.lostRole,
                        style =
                            TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = m.roleHue,
                            ),
                    )
                }
            },
        )
    }

    // EXPOSED stamp — diagonal, ink-bleed bloom (0.35→1.0)
    val stampProgress = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
    if (stampProgress > 0f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RubberStamp(
                glyphText = "EXPOSED",
                tint = BrandTokens.StampRed,
                progress = stampProgress,
                impactCenter = seatCenter,
                startRotationDeg = -12f, // diagonal per spec
            )
        }
    }

    // Desaturate −40%: grey veil over the card (0.60→1.0)
    val greyAlpha = ((progress - 0.60f) / 0.40f).coerceIn(0f, 0.40f)
    if (greyAlpha > 0f) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                color = Color.Gray.copy(alpha = greyAlpha),
                topLeft = Offset(seatCenter.x - 38.dp.toPx(), seatCenter.y - 54.dp.toPx()),
                size =
                    androidx.compose.ui.geometry
                        .Size(76.dp.toPx(), 108.dp.toPx()),
            )
        }
    }
}

// ─────────────────────────── 12. Elimination ─────────────────────────────────

/**
 * Elimination — the chair tips; the logo wobble realized.
 * Wistful, never mocking. Consolation toast.
 */
@Composable
internal fun EliminationBeat(
    m: GaddiMoment.Elimination,
    progress: Float,
    anchors: TableAnchors,
) {
    val seatCenter = anchors.seat(m.actorSeat)

    // ChairTip (full duration)
    ChairTip(
        progress = progress,
        tint = BrandTokens.BrassAged.copy(alpha = 0.8f),
        seatCenter = seatCenter,
    )

    // Grayscale wipe across the seat (0.30→0.70)
    val wipeProgress = ((progress - 0.30f) / 0.40f).coerceIn(0f, 0.55f)
    if (wipeProgress > 0f) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                color = Color.Gray.copy(alpha = wipeProgress),
                topLeft = Offset(seatCenter.x - 80.dp.toPx(), seatCenter.y - 80.dp.toPx()),
                size =
                    androidx.compose.ui.geometry
                        .Size(160.dp.toPx(), 160.dp.toPx()),
            )
        }
    }

    // "GADDI GAYI" band + consolation toast (0.55→1.0)
    val toastAlpha = ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f)
    if (toastAlpha > 0f) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            (seatCenter.x - 80.dp.toPx()).roundToInt(),
                            (seatCenter.y + 50.dp.toPx()).roundToInt(),
                        )
                    }.alpha(toastAlpha),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                    if (m.playerName.isNotEmpty()) {
                        "${m.playerName} ki GADDI GAYI 🪑"
                    } else {
                        "GADDI GAYI\nthand rakh 🪑"
                    },
                style = GaddiType.label_sm.copy(fontSize = 13.sp),
                color = GaddiNeutrals.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─────────────────────────── 13. TurnHandoff ─────────────────────────────────

/**
 * Turn handoff — gold sweep arc from old → new seat.
 * Subliminal orientation, not spectacle. No haptic, no bark.
 */
@Composable
internal fun TurnHandoffBeat(
    m: GaddiMoment.TurnHandoff,
    progress: Float,
    anchors: TableAnchors,
) {
    val fromCenter = anchors.seat(m.actorSeat)
    val toCenter = anchors.seat(m.nextSeat)

    // Gold sweep arc (0→0.70)
    val sweepProgress = (progress / 0.70f).coerceAtMost(1f)
    Canvas(Modifier.fillMaxSize()) {
        val endX = lerp(fromCenter.x, toCenter.x, easeOutCubic(sweepProgress))
        val endY = lerp(fromCenter.y, toCenter.y, easeOutCubic(sweepProgress))
        val alpha = if (sweepProgress > 0.85f) lerp(1f, 0f, (sweepProgress - 0.85f) / 0.15f) else 1f
        drawLine(
            color = BrandTokens.GoldAntique.copy(alpha = 0.75f * alpha),
            start = fromCenter,
            end = Offset(endX, endY),
            strokeWidth = 2.5.dp.toPx(),
        )
        // Moving head dot
        drawCircle(
            color = BrandTokens.GoldAntique.copy(alpha = alpha),
            radius = 5.dp.toPx(),
            center = Offset(endX, endY),
        )
    }

    // Destination seat gold rim pulse (0.60→1.0) + name label
    val rimProgress = ((progress - 0.60f) / 0.40f).coerceIn(0f, 1f)
    if (rimProgress > 0f) {
        val rimAlpha = sin(rimProgress * PI.toFloat()) * 0.7f
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = BrandTokens.GoldAntique.copy(alpha = rimAlpha),
                radius = lerp(24.dp.toPx(), 36.dp.toPx(), easeOutCubic(rimProgress)),
                center = toCenter,
                style = Stroke(2.dp.toPx()),
            )
        }
        if (m.nextName.isNotEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(
                                (toCenter.x - 60.dp.toPx()).roundToInt(),
                                (toCenter.y + 28.dp.toPx()).roundToInt(),
                            )
                        }.alpha(rimAlpha),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = m.nextName,
                    style =
                        TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandTokens.GoldAntique,
                            textAlign = TextAlign.Center,
                        ),
                )
            }
        }
    }
}

// ─────────────────────────── 14. Win ─────────────────────────────────────────

/**
 * Win — the Gaddi is claimed!
 * Confetti falls, GADDI wordmark stamps dead-centre.
 */
@Composable
internal fun WinBeat(
    m: GaddiMoment.Win,
    progress: Float,
    anchors: TableAnchors,
) {
    val victoryCenter =
        Offset(
            x =
                anchors.seatCenters.values
                    .map { it.x }
                    .average()
                    .toFloat(),
            y =
                anchors.seatCenters.values
                    .map { it.y }
                    .average()
                    .toFloat(),
        ).takeIf { it.x.isFinite() } ?: Offset(500f, 400f)

    val victorCenter = anchors.seat(m.actorSeat)

    // Currency-marigold confetti falls (0→1.0)
    Canvas(Modifier.fillMaxSize()) {
        val confettiCount = 40
        repeat(confettiCount) { i ->
            val seed = i.toFloat() / confettiCount.toFloat()
            val x = size.width * seed
            val fallY = lerp(-20f, size.height + 40f, easeInQuart(progress)) * (0.7f + seed * 0.3f)
            val rotAngle = seed * 360f + progress * 180f
            val alpha = if (progress > 0.80f) lerp(1f, 0f, (progress - 0.80f) / 0.20f) else 1f

            // Coin petal — small gold circles
            if (i % 3 == 0) {
                drawCircle(
                    color = BrandTokens.GoldAntique.copy(alpha = alpha * 0.9f),
                    radius = 5.dp.toPx(),
                    center = Offset(x, fallY),
                )
            } else {
                // Torn-manifesto strip — small rect
                drawRect(
                    color = BrandTokens.PaperCream.copy(alpha = alpha * 0.8f),
                    topLeft = Offset(x, fallY),
                    size =
                        androidx.compose.ui.geometry
                            .Size(8.dp.toPx(), 4.dp.toPx()),
                )
            }
        }
    }

    // GADDI wordmark stamp dead-center (0.15→1.0)
    val stampProgress = ((progress - 0.15f) / 0.85f).coerceIn(0f, 1f)
    if (stampProgress > 0f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RubberStamp(
                glyphText = "GADDI",
                tint = BrandTokens.GoldAntique,
                progress = stampProgress,
                impactCenter = victoryCenter,
                startRotationDeg = 6f,
            )
        }
    }

    // "Gaddi aapki!" press-in tagline (0.70→1.0)
    val taglineAlpha = ((progress - 0.70f) / 0.30f).coerceIn(0f, 1f)
    if (taglineAlpha > 0f) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset(victoryCenter.x.roundToInt(), (victoryCenter.y + 60.dp.toPx()).roundToInt()) }
                    .alpha(taglineAlpha),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (m.winnerName.isNotEmpty()) "${m.winnerName} ki Gaddi!" else "Gaddi aapki!",
                style = GaddiType.title.copy(fontSize = 22.sp),
                color = BrandTokens.GoldAntique,
                textAlign = TextAlign.Center,
            )
        }
    }
}
