package com.kursi.designsystem.moment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.BrandTokens
import com.kursi.designsystem.KursiNeutrals
import com.kursi.designsystem.KursiType
import kotlin.math.roundToInt

/**
 * Where a frozen frame centres itself when no seat anchor has been measured, or when averaging
 * them produced a non-finite result. Slightly above the table centre so the caption pill below
 * the frame still fits on screen.
 */
private val FrameCentreFallback = Offset(500f, 400f)

// ═══════════════════════════════════════════════════════════════════════════════
// MomentStaticFrames.kt — TENET 6: per-moment reduced-motion static end-frames.
//
// Reduced motion must NOT collapse every beat to a uniform TickerSlip. Each
// KursiMoment gets a TAILORED, characterful frozen frame that still reads the beat
// at a glance — held STAMP for declared actions, a JHOOTH/SACH verdict card for
// reveals, a tipped chair for elimination, a frozen KURSI crest for the win, a
// coin-row for income/steal, etc. Every primitive is rendered at its terminal
// (progress = 1f) so motion is gone but the meaning is intact.
//
// Multiplatform-safe: only Canvas / Text / graphicsLayer-free primitives.
// Each frame is anchored at the relevant seat where one exists, and carries a
// small plain-language caption so a cold player can read what happened.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Entry point: the reduced-motion static frame for [moment]. Dispatches to a
 * per-variant frozen end-state. Anchored to seats via [anchors] where relevant.
 */
@Composable
internal fun MomentStaticFrame(
    moment: KursiMoment,
    anchors: TableAnchors,
) {
    when (moment) {
        is KursiMoment.Income -> CoinFrame(anchors.seat(moment.actorSeat), "+1", "DEHAADI", BrandTokens.BrassAged, count = 1)
        is KursiMoment.ForeignAid -> CoinFrame(anchors.seat(moment.actorSeat), "+2", "FDI", BrandTokens.BrassAged, count = 2)
        is KursiMoment.Tax -> StampFrame(anchors.seat(moment.actorSeat), "GHOTALA", moment.roleHue, caption = "+3 · claims NETA")
        is KursiMoment.Steal -> StealFrame(anchors, moment)
        is KursiMoment.Assassinate -> StampFrame(anchors.seat(moment.target), "SUPARI", moment.roleHue, caption = "−3 · target hit")
        is KursiMoment.Exchange -> StampFrame(anchors.seat(moment.actorSeat), "SETTING", moment.roleHue, caption = "cards swapped")
        is KursiMoment.Coup ->
            CrestFrame(
                anchors.seat(moment.target),
                word = "KHELA",
                caption = "−7 · chair toppled",
                tint = BrandTokens.GoldAntique,
            )
        is KursiMoment.Block -> StampFrame(anchors.seat(moment.actorSeat), "ROKA!", moment.roleHue, caption = "action blocked")
        is KursiMoment.Challenge -> ChallengeFrame(anchors, moment)
        is KursiMoment.Reveal -> VerdictFrame(anchors.seat(moment.claimant), moment)
        is KursiMoment.InfluenceLoss ->
            StampFrame(
                anchors.seat(moment.actorSeat),
                "EXPOSED",
                BrandTokens.StampRed,
                caption = "card lost",
                rotationDeg = -12f,
            )
        is KursiMoment.Elimination -> TippedChairFrame(anchors.seat(moment.actorSeat))
        is KursiMoment.TurnHandoff -> HandoffFrame(anchors, moment)
        is KursiMoment.Win ->
            CrestFrame(
                centerOf(anchors),
                word = "KURSI",
                caption = "Kursi aapki!",
                tint = BrandTokens.GoldAntique,
                big = true,
            )
    }
}

private fun centerOf(anchors: TableAnchors): Offset {
    val xs = anchors.seatCenters.values
    if (xs.isEmpty()) return FrameCentreFallback
    val cx = xs.map { it.x }.average().toFloat()
    val cy = xs.map { it.y }.average().toFloat()
    return if (cx.isFinite() && cy.isFinite()) Offset(cx, cy) else FrameCentreFallback
}

// ─────────────────────────── Caption pill ────────────────────────────────────

/** A small cream caption pill placed just below [center] so the beat reads in words. */
@Composable
private fun CaptionPill(
    center: Offset,
    text: String,
    tint: Color,
    yPadDp: Float = 36f,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, 0) },
    ) {
        Box(
            modifier =
                Modifier
                    .offset {
                        IntOffset(
                            (center.x - 70.dp.toPx()).roundToInt(),
                            (center.y + yPadDp.dp.toPx()).roundToInt(),
                        )
                    }.clip(CircleShape)
                    .background(BrandTokens.TeakDark.copy(alpha = 0.9f))
                    .border(1.dp, tint.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = text,
                style = KursiType.label_sm.copy(fontWeight = FontWeight.SemiBold),
                color = KursiNeutrals.TextPrimary,
                maxLines = 1,
            )
        }
    }
}

// ─────────────────────────── Held STAMP frame ────────────────────────────────

/**
 * Declared/claim/block actions: the rubber stamp held at its settled terminal —
 * the press already landed. No descend, no overshoot, just the inked stamp + a
 * one-line caption naming the effect.
 */
@Composable
private fun StampFrame(
    seatCenter: Offset,
    word: String,
    tint: Color,
    caption: String,
    rotationDeg: Float = 0f,
) {
    // Anchor the stamp word centred on the seat. We offset by half its rough text width
    // so it sits over the seat rather than starting at it.
    Box(
        modifier =
            Modifier.fillMaxSize().offset {
                IntOffset(
                    (seatCenter.x - (word.length * 11).dp.toPx() / 2f).roundToInt(),
                    (seatCenter.y - 22.dp.toPx()).roundToInt(),
                )
            },
    ) {
        Text(
            text = word,
            style =
                TextStyle(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    color = tint,
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp,
                ),
            modifier = Modifier.rotateStatic(rotationDeg),
        )
    }
    CaptionPill(seatCenter, caption, tint)
}

// A tiny helper so the stamp word can carry a static tilt (no animation).
private fun Modifier.rotateStatic(deg: Float): Modifier = if (deg == 0f) this else this.then(Modifier.graphicsLayer { rotationZ = deg })

// ─────────────────────────── Coin-row frame ──────────────────────────────────

/**
 * Income / Foreign Aid: a small row of gold coins at the actor seat with the +N
 * delta — the economic beat made still. No arc, just the settled coins.
 */
@Composable
private fun CoinFrame(
    seatCenter: Offset,
    delta: String,
    caption: String,
    tint: Color,
    count: Int,
) {
    Canvas(Modifier.fillMaxSize()) {
        val r = 11.dp.toPx()
        val gap = 26.dp.toPx()
        val startX = seatCenter.x - (count - 1) * gap / 2f
        for (i in 0 until count) {
            val c = Offset(startX + i * gap, seatCenter.y - 8.dp.toPx())
            drawCircle(BrandTokens.GoldAntique, radius = r, center = c)
            drawCircle(BrandTokens.BrassDark.copy(alpha = 0.85f), radius = r, center = c, style = Stroke(1.5.dp.toPx()))
        }
    }
    // +N badge above the coins.
    Box(
        modifier =
            Modifier.fillMaxSize().offset {
                IntOffset((seatCenter.x - 14.dp.toPx()).roundToInt(), (seatCenter.y - 44.dp.toPx()).roundToInt())
            },
    ) {
        Text(
            text = delta,
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black, color = BrandTokens.GoldAntique),
        )
    }
    CaptionPill(seatCenter, caption, tint)
}

// ─────────────────────────── Steal frame ─────────────────────────────────────

/**
 * Vasooli / Steal: a stamp on the victim plus a two-coin yank arrow from victim →
 * actor, frozen at landing — the coins sit on the actor's side.
 */
@Composable
private fun StealFrame(
    anchors: TableAnchors,
    m: KursiMoment.Steal,
) {
    val victim = anchors.seat(m.victim)
    val actor = anchors.seat(m.actorSeat)
    Canvas(Modifier.fillMaxSize()) {
        // taut yank line victim → actor
        drawLine(
            color = m.roleHue.copy(alpha = 0.7f),
            start = victim,
            end = actor,
            strokeWidth = 2.dp.toPx(),
        )
        // two coins landed on the actor side
        val r = 10.dp.toPx()
        listOf(-13f, 13f).forEachIndexed { i, dx ->
            val c = Offset(actor.x + dx.dp.toPx(), actor.y - 6.dp.toPx())
            drawCircle(BrandTokens.GoldAntique, radius = r, center = c)
            drawCircle(BrandTokens.BrassDark.copy(alpha = 0.85f), radius = r, center = c, style = Stroke(1.5.dp.toPx()))
        }
    }
    // VASOOLI stamp on victim
    Box(
        modifier =
            Modifier.fillMaxSize().offset {
                IntOffset((victim.x - 60.dp.toPx()).roundToInt(), (victim.y - 16.dp.toPx()).roundToInt())
            },
    ) {
        Text(
            text = "VASOOLI",
            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black, color = m.roleHue, letterSpacing = 1.sp),
        )
    }
    CaptionPill(actor, "steal 2 · claims BABU", m.roleHue)
}

// ─────────────────────────── Verdict frame (Reveal) ──────────────────────────

/**
 * THE MONEY MOMENT, frozen: a face-up card with the claimed role and a big
 * SACH! / JHOOTH! verdict stamp. Truthful → role hue + bright; bluff → red,
 * desaturated card behind.
 */
@Composable
private fun VerdictFrame(
    seatCenter: Offset,
    m: KursiMoment.Reveal,
) {
    val (word, color) = if (m.truthful) "SACH!" to m.roleHue else "JHOOTH!" to BrandTokens.StampRed
    // The revealed card — cream face with the role name, greyed when a bluff.
    Box(
        modifier =
            Modifier.fillMaxSize().offset {
                IntOffset((seatCenter.x - 38.dp.toPx()).roundToInt(), (seatCenter.y - 54.dp.toPx()).roundToInt())
            },
    ) {
        Box(
            modifier =
                Modifier
                    .size(76.dp, 108.dp)
                    .clip(
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(8.dp),
                    ).background(if (m.truthful) BrandTokens.PaperCream else BrandTokens.PaperCream.copy(alpha = 0.55f))
                    .border(
                        1.5.dp,
                        color.copy(alpha = 0.8f),
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(8.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = m.claimedRole,
                style =
                    TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (m.truthful) m.roleHue else BrandTokens.CreamInk.copy(alpha = 0.6f),
                    ),
                textAlign = TextAlign.Center,
            )
        }
    }
    // The verdict stamp, diagonal for a caught bluff.
    Box(
        modifier =
            Modifier.fillMaxSize().offset {
                IntOffset(
                    (seatCenter.x - (word.length * 12).dp.toPx() / 2f).roundToInt(),
                    (seatCenter.y - 20.dp.toPx()).roundToInt(),
                )
            },
    ) {
        Text(
            text = word,
            style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Black, color = color, letterSpacing = 1.sp),
            modifier = Modifier.rotateStatic(if (m.truthful) 0f else -10f),
        )
    }
    CaptionPill(
        seatCenter,
        if (m.truthful) "claim was real" else "bluff caught",
        color,
        yPadDp = 60f,
    )
}

// ─────────────────────────── Challenge frame ─────────────────────────────────

/**
 * The dare, frozen: a taut torn-paper connector from challenger → claimant with a
 * "?" seal at the claimant, no outcome yet.
 */
@Composable
private fun ChallengeFrame(
    anchors: TableAnchors,
    m: KursiMoment.Challenge,
) {
    val from = anchors.seat(m.actorSeat)
    val to = anchors.seat(m.claimant)
    Canvas(Modifier.fillMaxSize()) {
        drawLine(
            color = BrandTokens.StampRed.copy(alpha = 0.85f),
            start = from,
            end = to,
            strokeWidth = 2.5.dp.toPx(),
        )
        drawCircle(
            color = BrandTokens.PendingAmber.copy(alpha = 0.5f),
            radius = 26.dp.toPx(),
            center = to,
            style = Stroke(2.dp.toPx()),
        )
    }
    Box(
        modifier =
            Modifier.fillMaxSize().offset {
                IntOffset((to.x - 7.dp.toPx()).roundToInt(), (to.y - 18.dp.toPx()).roundToInt())
            },
    ) {
        Text("?", style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Black, color = BrandTokens.PendingAmber))
    }
    CaptionPill(to, "challenge thrown", BrandTokens.StampRed)
}

// ─────────────────────────── Tipped-chair frame (Elimination) ────────────────

/**
 * Elimination, frozen: the chair already tipped past 90°, greyed seat, "KURSI GAYI"
 * band. Wistful, never mocking.
 */
@Composable
private fun TippedChairFrame(seatCenter: Offset) {
    Canvas(Modifier.fillMaxSize()) {
        // grey wipe over the seat
        drawRect(
            color = Color.Gray.copy(alpha = 0.4f),
            topLeft = Offset(seatCenter.x - 56.dp.toPx(), seatCenter.y - 56.dp.toPx()),
            size = Size(112.dp.toPx(), 112.dp.toPx()),
        )
        // tipped chair glyph — frozen at ~100°, dropped a little
        translate(left = seatCenter.x, top = seatCenter.y + 18.dp.toPx()) {
            rotate(degrees = 100f, pivot = Offset(0f, 0f)) {
                drawChairGlyph(BrandTokens.BrassAged.copy(alpha = 0.85f))
            }
        }
    }
    CaptionPill(seatCenter, "out — kursi gayi", KursiNeutrals.TextSecondary, yPadDp = 50f)
}

// ─────────────────────────── Turn handoff frame ──────────────────────────────

/**
 * Turn handoff, frozen: a gold sweep arrow from the old seat to the new, with the
 * destination rim lit. The quietest frame.
 */
@Composable
private fun HandoffFrame(
    anchors: TableAnchors,
    m: KursiMoment.TurnHandoff,
) {
    val from = anchors.seat(m.actorSeat)
    val to = anchors.seat(m.nextSeat)
    Canvas(Modifier.fillMaxSize()) {
        drawLine(BrandTokens.GoldAntique.copy(alpha = 0.7f), start = from, end = to, strokeWidth = 2.5.dp.toPx())
        drawCircle(BrandTokens.GoldAntique, radius = 5.dp.toPx(), center = to)
        drawCircle(BrandTokens.GoldAntique.copy(alpha = 0.6f), radius = 30.dp.toPx(), center = to, style = Stroke(2.dp.toPx()))
    }
}

// ─────────────────────────── Crest frame (Coup / Win) ────────────────────────

/**
 * Coup and Win: a frozen KURSI/KHELA crest — the wordmark stamped dead-centre over
 * a gold ring, with the celebratory/decisive caption. [big] enlarges for the win.
 */
@Composable
private fun CrestFrame(
    center: Offset,
    word: String,
    caption: String,
    tint: Color,
    big: Boolean = false,
) {
    val ringR = if (big) 64f else 46f
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(tint.copy(alpha = 0.18f), radius = ringR.dp.toPx(), center = center)
        drawCircle(tint.copy(alpha = 0.8f), radius = ringR.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
    }
    // Position the wordmark's top-left so its rough text box centres on the ring.
    val fontPx = if (big) 44f else 32f
    val perCharDp = if (big) 26f else 19f // rough advance width per glyph at this weight
    Box(
        modifier =
            Modifier.fillMaxSize().offset {
                IntOffset(
                    (center.x - (word.length * perCharDp).dp.toPx() / 2f).roundToInt(),
                    (center.y - fontPx.dp.toPx() / 2f).roundToInt(),
                )
            },
    ) {
        Text(
            text = word,
            style =
                TextStyle(
                    fontSize = fontPx.sp,
                    fontWeight = FontWeight.Black,
                    color = tint,
                    letterSpacing = 2.sp,
                ),
        )
    }
    CaptionPill(center, caption, tint, yPadDp = ringR + 12f)
}
