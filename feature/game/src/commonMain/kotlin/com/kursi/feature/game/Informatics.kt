package com.kursi.feature.game

// ═══════════════════════════════════════════════════════════════════════════════
// Informatics.kt — GADDI Help / Onboarding layer (Steps 5–7 of refine spec)
//
// Delivers:
//   A. NiyamGazette — full-screen 4-tab rules reference (DARBAR/DHANDHA/DASTUR/HISAAB)
//   B. WhisperChit — per-element info popover (3 variants: role, action, opponent)
//   C. HintRail — always-on "what can I do" strip + bluffOdds chip during reactions
//   D. SwearingInPrimer — shown-once 5-step coachmark onboarding; persisted flag
//
// Multiplatform-safe: Compose Popup / Dialog / overlay Box — no position:fixed.
// No engine/ai mutations — BluffOdds is a pure read-only helper.
// ═══════════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kursi.ai.BluffOdds
import com.kursi.ai.OpponentInsight
import com.kursi.designsystem.*
import com.kursi.engine.*
import com.kursi.feature.game.docks.loseInfluenceCause
import com.kursi.feature.game.overlays.*
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.a11y_close_gazette
import kursi.core.designsystem.generated.resources.a11y_decision_coach_toggle
import kursi.core.designsystem.generated.resources.game_block_chain_header
import kursi.core.designsystem.generated.resources.game_coach_off
import kursi.core.designsystem.generated.resources.game_coach_on
import kursi.core.designsystem.generated.resources.game_darbar_tab_footnote
import kursi.core.designsystem.generated.resources.game_dastur_tab_footnote
import kursi.core.designsystem.generated.resources.game_dhandha_tab_footnote
import kursi.core.designsystem.generated.resources.game_gazette_footer_quote
import kursi.core.designsystem.generated.resources.game_gazette_subtitle
import kursi.core.designsystem.generated.resources.game_gazette_title
import kursi.core.designsystem.generated.resources.game_hisaab_tab_footnote
import kursi.core.designsystem.generated.resources.game_niyam_label
import kursi.core.designsystem.generated.resources.game_primer_done
import kursi.core.designsystem.generated.resources.game_primer_skip
import kursi.core.designsystem.generated.resources.game_replay_primer
import kursi.core.designsystem.generated.resources.game_tab_darbar
import org.jetbrains.compose.resources.stringResource

// ─────────────────────────── Primer persistence (in-memory for multiplatform) ──
// Using a simple Kotlin object singleton rather than DataStore/Settings to keep
// multi-target safe without adding a Settings dependency. The flag resets on app
// restart (acceptable for an in-process onboarding flow; a real persistence layer
// can be swapped in by injecting a PrimerPrefs interface here).

object PrimerPrefs {
    private var _hasSeenPrimer: Boolean = false
    val hasSeenPrimer: Boolean get() = _hasSeenPrimer

    fun markSeen() {
        _hasSeenPrimer = true
    }

    fun reset() {
        _hasSeenPrimer = false
    }
}

// ─────────────────────────── Shared brass popover surface ────────────────────

/**
 * The shared deco popover surface — an aged-brass-framed cream document on the teak
 * table (M4 §1). Routes every chit through [Modifier.decoPopoverPaper] (teak scrim +
 * brushed-brass frame + brass-hairline + paper-grain + debossed inner rule), then lays
 * a [WaxSeal] over the top-end corner so each popover reads as a stamped instrument,
 * never a Material-flat sheet.
 *
 * @param sealMark the glyph pressed into the wax (defaults to a deco asterisk); callers
 *        pass a role/context initial where it adds meaning.
 */
@Composable
private fun BrassParchmentSurface(
    modifier: Modifier = Modifier,
    sealMark: String = "✦",
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .decoPopoverPaper(GaddiRadii.md)
                    .padding(GaddiDimens.space_md)
                    // Reserve room so content never collides with the wax seal.
                    .padding(top = 6.dp, end = 10.dp),
            content = content,
        )
        // Wax-seal accent over the top-end corner.
        WaxSeal(
            mark = sealMark,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = (-8).dp),
        )
    }
}

// ─────────────────────────── Seal dot (role color) ───────────────────────────

@Composable
private fun RoleSealDot(
    role: Role,
    size: androidx.compose.ui.unit.Dp = 12.dp,
) {
    val color = GaddiColors.forRole(role).color
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.6f), CircleShape),
    )
}

// ─────────────────────────── Brass divider ───────────────────────────────────

@Composable
private fun BrassDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            BrandTokens.BrassDark.copy(alpha = 0.3f),
                            BrandTokens.GoldAntique.copy(alpha = 0.8f),
                            BrandTokens.BrassDark.copy(alpha = 0.3f),
                        ),
                    ),
                ),
    )
}

// ─────────────────────────── B. WHISPER CHIT ─────────────────────────────────
// Three variants: IdentityChit (role card), RiskChit (action), DossierChit (opponent).
// Shown as an overlay Box anchored near the tapped element.
// Multiplatform: we use a Box overlay within the layout tree rather than Popup
// (avoids platform-specific Popup positioning issues on desktop/web).

sealed interface ChitContent {
    data class Identity(
        val role: Role,
    ) : ChitContent

    data class RiskAction(
        val action: Action,
        val myCoins: Int,
        val bluffConf: BluffOdds.Confidence?,
    ) : ChitContent

    data class Dossier(
        val opponentName: String,
        val opponentCoins: Int,
        val faceDownCount: Int,
        val faceUpRoles: List<Role>,
        val lastActionText: String?,
        val personaDossierLine: String,
        /** A second deadpan line — the persona's rivalry / standing reputation. */
        val personaRivalryLine: String,
        val legalMovesAgainst: List<String>,
        /**
         * The PUBLIC-info READ this dossier leads with — posterior over likely roles, per-role claim
         * counts, bluff-caught tally and the inferred bluffRate. Null only for a pre-insight fixture;
         * when present the chit shows the real intelligence above the persona flavour.
         * SECRECY: derived entirely from table-visible claims/blocks/reveals — never hidden cards.
         */
        val intel: DossierIntel? = null,
    ) : ChitContent

    /**
     * The "table heart" detail, shown when the centre claim card / medallion is held.
     * Either there is a LIVE claim on the table (someone is mid-reaction) or we report
     * the standing state of the round (deck, treasury, turn).
     */
    data class ClaimDetail(
        val claimedRole: Role?,
        val actorName: String?,
        val deckCount: Int,
        val treasuryCoins: Int,
        val turnNumber: Int,
    ) : ChitContent

    /** A single Roznamcha log event, expanded with its deadpan one-line narration. */
    data class LogEvent(
        val title: String,
        val narration: String,
        val icon: String,
    ) : ChitContent

    /**
     * The DECISION-COACH detail for one move, shown when an action/reaction chip is long-pressed.
     * Carries the full read: truthful vs bluff, the odds, the recommended flag, and the in-voice
     * rationale — the deep detail that the compact on-chip badge only hints at.
     */
    data class Coach(
        val moveLabel: String,
        /** Whether the player holds the claimed role: true=real, false=bluff, null=no claim. */
        val truthful: Boolean?,
        val bluff: Boolean,
        /** Challenge → P(opponent bluffing); bluff move → P(not caught); else null. */
        val successOdds: Double?,
        val winProb: Double,
        val recommended: Boolean,
        val rationale: String,
        /**
         * A belief-grounded read of the CLAIM under scrutiny — e.g.
         * "All 3 NETA are accounted for — this Ghotala is a bluff; challenge is favourable."
         * Derived from the public card-accounting (eliminated + your hand vs total copies). Null when
         * the move makes no claim to reason about (e.g. a plain Income), so the chit just shows odds.
         */
        val beliefLine: String? = null,
    ) : ChitContent
}

// ─────────────────────────── Dossier intelligence (UI model) ─────────────────

/**
 * The PUBLIC-info READ shown at the top of an opponent's dossier — a UI-shaped projection of
 * [com.kursi.ai.OpponentInsight] so the composable layer renders the real intelligence without
 * reaching back into :ai data shapes per-field. Every value is a function of table-visible
 * claims/blocks/reveals only. Build via [DossierIntel.from].
 */
data class DossierIntel(
    /** Most-likely face-down role (posterior argmax) and its probability, or null if flat. */
    val topRole: Role?,
    val topRoleProb: Double,
    /** Posterior over each role (sorted desc), for the read-bar. Sums to ~1.0. */
    val posterior: List<Pair<Role, Double>>,
    /** Per-role claim tally: (role, claims, caughtBluffing) for roles this seat actually claimed. */
    val claimRows: List<Triple<Role, Int, Int>>,
    /** Headline counters. */
    val totalClaims: Int,
    val bluffsCaught: Int,
    /** Inferred P(next claim is a bluff), 0..1. */
    val bluffRate: Double,
) {
    /** A 1..5 "shady-meter" pip reading derived from the inferred bluffRate. */
    val bluffPips: Int
        get() =
            when {
                bluffRate < 0.18 -> 1
                bluffRate < 0.32 -> 2
                bluffRate < 0.48 -> 3
                bluffRate < 0.65 -> 4
                else -> 5
            }

    /** Short human read of the bluffRate, e.g. "straight shooter" … "serial bluffer". */
    val bluffLabel: String
        get() =
            when (bluffPips) {
                1 -> "straight shooter"
                2 -> "mostly honest"
                3 -> "plays the odds"
                4 -> "loves a bluff"
                else -> "serial bluffer"
            }

    companion object {
        fun from(insight: OpponentInsight): DossierIntel {
            val sorted =
                insight.posterior.entries
                    .sortedByDescending { it.value }
                    .map { it.key to it.value }
            val top = sorted.firstOrNull()
            val claimRows =
                insight.claimStats
                    .filter { it.claims > 0 }
                    .sortedByDescending { it.claims }
                    .map { Triple(it.role, it.claims, it.caughtBluffing) }
            return DossierIntel(
                topRole = top?.first,
                topRoleProb = top?.second ?: 0.0,
                posterior = sorted,
                claimRows = claimRows,
                totalClaims = insight.totalClaims,
                bluffsCaught = insight.bluffsCaught,
                bluffRate = insight.bluffRate,
            )
        }
    }
}

@Composable
fun WhisperChit(
    content: ChitContent,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    // P9: when supplied, the popover anchors to [anchorBounds] (root coordinates)
    // with a caret pointing at the source, instead of floating top-centre.
    anchorBounds: androidx.compose.ui.geometry.Rect? = null,
    containerSize: androidx.compose.ui.unit.IntSize? = null,
) {
    // Scrim to capture outside taps
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource =
                        remember {
                            androidx.compose.foundation.interaction
                                .MutableInteractionSource()
                        },
                    onClick = onDismiss,
                ),
    ) {
        if (anchorBounds != null && containerSize != null) {
            AnchoredChit(
                anchor = anchorBounds,
                container = containerSize,
                content = content,
                modifier = modifier,
            )
        } else {
            Box(
                modifier =
                    modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp, start = 16.dp, end = 16.dp),
            ) {
                when (content) {
                    is ChitContent.Identity -> IdentityChitContent(content)
                    is ChitContent.RiskAction -> RiskChitContent(content)
                    is ChitContent.Dossier -> DossierChitContent(content)
                    is ChitContent.ClaimDetail -> ClaimDetailChitContent(content)
                    is ChitContent.LogEvent -> LogEventChitContent(content)
                    is ChitContent.Coach -> CoachChitContent(content)
                }
            }
        }
    }
}

/**
 * P9 anchored popover: places the chit just below the tapped element (flipping above
 * when there's no room), horizontally centred on the anchor and clamped to the
 * container, with a small brass caret/tail pointing back at the source — so the eye
 * stays tethered and the popover never reads as a detached debug overlay.
 */
@Composable
private fun AnchoredChit(
    anchor: androidx.compose.ui.geometry.Rect,
    container: androidx.compose.ui.unit.IntSize,
    content: ChitContent,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val chitWidth = 300.dp
    val chitWidthPx = with(density) { chitWidth.toPx() }
    val margin = with(density) { 12.dp.toPx() }
    val gap = with(density) { 10.dp.toPx() }

    // Below by default; flip above if the anchor sits in the lower 55% of the screen.
    val placeBelow = anchor.bottom < container.height * 0.55f
    // Horizontal: centre the chit on the anchor, clamp inside the container.
    val rawX = anchor.center.x - chitWidthPx / 2f
    val clampedX = rawX.coerceIn(margin, (container.width - chitWidthPx - margin).coerceAtLeast(margin))
    // Caret x relative to the chit's left edge.
    val caretX = (anchor.center.x - clampedX).coerceIn(20f, chitWidthPx - 20f)

    Box(
        modifier =
            modifier
                .offset {
                    val y = if (placeBelow) anchor.bottom + gap else 0f // y computed after measure for above
                    androidx.compose.ui.unit
                        .IntOffset(clampedX.toInt(), y.toInt())
                }.then(if (!placeBelow) Modifier else Modifier),
    ) {
        // We need the chit height to place it above; use a sub-layout via Column with
        // caret on the correct side. For "above", anchor the bottom of the chit to the
        // anchor top using a translation measured from the chit's own height.
        ChitWithCaret(
            caretXPx = caretX,
            caretOnTop = placeBelow,
            placeBelowAnchor = placeBelow,
            anchorTopPx = anchor.top,
            gapPx = gap,
            content = content,
            widthDp = chitWidth,
        )
    }
}

@Composable
private fun ChitWithCaret(
    caretXPx: Float,
    caretOnTop: Boolean,
    placeBelowAnchor: Boolean,
    anchorTopPx: Float,
    gapPx: Float,
    content: ChitContent,
    widthDp: androidx.compose.ui.unit.Dp,
) {
    val density = LocalDensity.current
    var chitHeightPx by remember { mutableStateOf(0) }
    // When placing above, shift the whole chit up by its own height + the anchor offset.
    val yShift = if (placeBelowAnchor) 0 else (anchorTopPx - gapPx - chitHeightPx).toInt()

    val caretSize = 9.dp
    val caretColor = BrandTokens.PaperDeep

    Column(
        modifier =
            Modifier
                .offset {
                    androidx.compose.ui.unit
                        .IntOffset(0, yShift)
                }.width(widthDp)
                .onGloballyPositioned { chitHeightPx = it.size.height },
        horizontalAlignment = Alignment.Start,
    ) {
        if (caretOnTop) {
            CaretTriangle(caretXPx = caretXPx, pointUp = true, color = caretColor, size = caretSize)
        }
        Box {
            when (content) {
                is ChitContent.Identity -> IdentityChitContent(content)
                is ChitContent.RiskAction -> RiskChitContent(content)
                is ChitContent.Dossier -> DossierChitContent(content)
                is ChitContent.ClaimDetail -> ClaimDetailChitContent(content)
                is ChitContent.LogEvent -> LogEventChitContent(content)
                is ChitContent.Coach -> CoachChitContent(content)
            }
        }
        if (!caretOnTop) {
            CaretTriangle(caretXPx = caretXPx, pointUp = false, color = caretColor, size = caretSize)
        }
    }
}

@Composable
private fun CaretTriangle(
    caretXPx: Float,
    pointUp: Boolean,
    color: Color,
    size: androidx.compose.ui.unit.Dp,
) {
    val density = LocalDensity.current
    Canvas(
        modifier =
            Modifier
                .offset {
                    androidx.compose.ui.unit
                        .IntOffset((caretXPx - with(density) { size.toPx() }).toInt(), 0)
                }.size(width = size * 2, height = size),
    ) {
        val w = this.size.width
        val h = this.size.height
        val path =
            androidx.compose.ui.graphics.Path().apply {
                if (pointUp) {
                    moveTo(w / 2f, 0f)
                    lineTo(0f, h)
                    lineTo(w, h)
                } else {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w / 2f, h)
                }
                close()
            }
        drawPath(path, color)
        drawPath(
            path,
            BrandTokens.BrassAged.copy(alpha = 0.6f),
            style =
                androidx.compose.ui.graphics.drawscope
                    .Stroke(1f),
        )
    }
}

@Composable
private fun IdentityChitContent(c: ChitContent.Identity) {
    val voice = LocalGaddiVoice.current
    val visual = GaddiColors.forRole(c.role)
    BrassParchmentSurface(modifier = Modifier.widthIn(max = 300.dp), sealMark = c.role.name.take(1)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_sm)) {
            RoleSealDot(c.role, 18.dp)
            Text(
                text = "${c.role.name} · ${visual.title}",
                style = GaddiType.title_sm,
                color = BrandTokens.CreamInk,
            )
        }
        Spacer(Modifier.height(GaddiDimens.space_sm))
        BrassDivider()
        Spacer(Modifier.height(GaddiDimens.space_sm))
        ChitLine(label = "Claims:", text = visual.actionLine)
        ChitLine(label = "Defends:", text = visual.blockLine)
        ChitLine(label = "Vulnerable to:", text = "A challenge — if you don't hold ${c.role.name}, you lose a card.")
        Spacer(Modifier.height(GaddiDimens.space_xs))
        Text(
            text = "\"${voice.roleTagline(c.role)}\"",
            style = GaddiType.label_sm.copy(fontStyle = FontStyle.Italic),
            color = BrandTokens.BrassDark,
        )
    }
}

@Composable
private fun RiskChitContent(c: ChitContent.RiskAction) {
    val voice = LocalGaddiVoice.current
    val action = c.action
    val claimedRole = Rules.claimedRole(action)
    val challengeable = Rules.isChallengeable(action)
    val blockedBy = Rules.rolesThatBlock(action)

    BrassParchmentSurface(modifier = Modifier.widthIn(max = 320.dp)) {
        Text(
            text = "${actionHindiName(action)} · ${actionCostSummary(action)}",
            style = GaddiType.title_sm,
            color = BrandTokens.CreamInk,
        )
        Spacer(Modifier.height(GaddiDimens.space_sm))
        BrassDivider()
        Spacer(Modifier.height(GaddiDimens.space_sm))

        if (claimedRole != null) {
            ChitLine(label = "Claims:", text = "You're declaring you hold ${claimedRole.name}.")
            if (challengeable) {
                RiskLine(
                    positive = true,
                    text = "If challenged & you ARE ${claimedRole.name}: challenger loses a card. You profit.",
                )
                RiskLine(
                    positive = false,
                    text = "If challenged & you're NOT: you lose a card. Ouch.",
                )
            }
        } else {
            ChitLine(label = "No claim:", text = "Safe. Nobody can challenge this.")
        }

        if (blockedBy.isNotEmpty()) {
            ChitLine(label = "Blocked by:", text = blockedBy.joinToString(", ") { it.name })
        }

        // Live bluff odds
        val conf = c.bluffConf
        if (conf != null) {
            Spacer(Modifier.height(GaddiDimens.space_xs))
            BrassDivider()
            Spacer(Modifier.height(GaddiDimens.space_xs))
            OddsChip(conf = conf, compact = false)
        }

        Spacer(Modifier.height(GaddiDimens.space_xs))
        Text(
            text = voice.actionBark(action),
            style = GaddiType.label_sm.copy(fontStyle = FontStyle.Italic),
            color = BrandTokens.BrassDark,
        )
    }
}

@Composable
private fun DossierChitContent(c: ChitContent.Dossier) {
    BrassParchmentSurface(modifier = Modifier.widthIn(max = 340.dp)) {
        // ── Header: name + a one-line headline READ (lead with intel) ──────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_xs)) {
            Text(
                text = c.opponentName,
                style = GaddiType.title_sm,
                color = BrandTokens.CreamInk,
                modifier = Modifier.weight(1f),
            )
            val intel = c.intel
            if (intel != null) {
                // Shady-meter badge — the headline bluff read.
                ShadyMeter(pips = intel.bluffPips, label = intel.bluffLabel)
            }
        }

        val intel = c.intel
        if (intel != null) {
            Spacer(Modifier.height(GaddiDimens.space_sm))
            // ── THE READ: posterior bar over likely roles ──────────────────────
            Text(
                text = "LIKELY HOLDING",
                style = GaddiType.label_micro.copy(letterSpacing = 1.sp),
                color = BrandTokens.BrassDark,
            )
            Spacer(Modifier.height(2.dp))
            PosteriorBar(posterior = intel.posterior)
            if (intel.topRole != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Most likely ${intel.topRole.name} (~${(intel.topRoleProb * 100).toInt()}%)",
                    style = GaddiType.label_micro,
                    color = BrandTokens.BrassDark,
                )
            }

            Spacer(Modifier.height(GaddiDimens.space_sm))
            BrassDivider()
            Spacer(Modifier.height(GaddiDimens.space_sm))

            // ── Claim record: per-role claim counts + bluff-caught ─────────────
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_xs)) {
                Text(
                    text = "CLAIM RECORD",
                    style = GaddiType.label_micro.copy(letterSpacing = 1.sp),
                    color = BrandTokens.BrassDark,
                    modifier = Modifier.weight(1f),
                )
                // bluff-rate inferred read
                Text(
                    text = "bluffs ${(intel.bluffRate * 100).toInt()}% of the time",
                    style = GaddiType.label_micro.copy(fontStyle = FontStyle.Italic),
                    color = if (intel.bluffPips >= 4) GaddiSemantics.Danger else BrandTokens.BrassDark,
                )
            }
            Spacer(Modifier.height(2.dp))
            if (intel.claimRows.isEmpty()) {
                Text(
                    text = "No claims on record yet — a blank slate.",
                    style = GaddiType.label_sm.copy(fontStyle = FontStyle.Italic),
                    color = BrandTokens.BrassDark,
                )
            } else {
                intel.claimRows.forEach { (role, claims, caught) ->
                    ClaimRecordRow(role = role, claims = claims, caught = caught)
                }
                if (intel.bluffsCaught > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Caught bluffing ${intel.bluffsCaught}× across ${intel.totalClaims} claims.",
                        style = GaddiType.label_micro,
                        color = GaddiSemantics.Danger,
                    )
                }
            }
        }

        Spacer(Modifier.height(GaddiDimens.space_sm))
        BrassDivider()
        Spacer(Modifier.height(GaddiDimens.space_sm))

        // Cards / influence
        val cardsText =
            if (c.faceDownCount == 0) {
                "No cards left — eliminated."
            } else {
                "${c.faceDownCount} face-down" +
                    if (c.faceUpRoles.isNotEmpty()) {
                        " · ${c.faceUpRoles.joinToString { it.name }} revealed"
                    } else {
                        ""
                    }
            }
        ChitLine(label = "Cards:", text = cardsText)

        // Coins + threat read
        val threat =
            when {
                c.opponentCoins >= 10 -> "MUST Khela you. Immediate threat."
                c.opponentCoins >= 7 -> "Can Khela (eliminate) you. Watch out."
                c.opponentCoins >= 3 -> "Can Supari (assassinate). Be careful."
                else -> "Broke for now. Harmless… probably."
            }
        ChitLine(label = "Coins:", text = "${c.opponentCoins} — $threat")

        if (c.lastActionText != null) {
            ChitLine(label = "Last move:", text = c.lastActionText)
        }

        // ── Persona flavour — kept, but now a closing garnish under the read ──
        if (c.personaDossierLine.isNotEmpty()) {
            Spacer(Modifier.height(GaddiDimens.space_xs))
            Text(
                text = "\"${c.personaDossierLine}\"",
                style = GaddiType.label_micro.copy(fontStyle = FontStyle.Italic),
                color = BrandTokens.BrassDark.copy(alpha = 0.9f),
            )
        }
        if (c.personaRivalryLine.isNotEmpty()) {
            Text(
                text = c.personaRivalryLine,
                style = GaddiType.label_micro.copy(fontStyle = FontStyle.Italic),
                color = BrandTokens.BrassDark.copy(alpha = 0.75f),
            )
        }

        // Legal moves against this opponent
        if (c.legalMovesAgainst.isNotEmpty()) {
            Spacer(Modifier.height(GaddiDimens.space_sm))
            BrassDivider()
            Spacer(Modifier.height(GaddiDimens.space_sm))
            Text(
                text = "WHAT YOU CAN DO",
                style = GaddiType.label_sm.copy(letterSpacing = 1.sp),
                color = BrandTokens.BrassDark,
            )
            c.legalMovesAgainst.forEach { move ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(BrandTokens.BrassAged))
                    Spacer(Modifier.width(GaddiDimens.space_xs))
                    Text(text = move, style = GaddiType.label_sm, color = BrandTokens.CreamInk)
                }
            }
        }
    }
}

/**
 * The posterior read-bar — a single horizontal stacked bar whose segments are sized by P(role) and
 * tinted with each role's hue, with inline labels on the dominant segments. Reads at a glance as
 * "this seat is probably NETA, maybe BABU". PUBLIC-info only.
 */
@Composable
private fun PosteriorBar(posterior: List<Pair<Role, Double>>) {
    val total = posterior.sumOf { it.second }.coerceAtLeast(1e-6)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(Squircle(GaddiRadii.sm))
                .border(GaddiDimens.stroke_hairline, BrandTokens.BrassDark.copy(alpha = 0.5f), Squircle(GaddiRadii.sm)),
    ) {
        posterior.forEach { (role, p) ->
            val frac = (p / total).toFloat()
            if (frac <= 0.001f) return@forEach
            val hue = GaddiColors.forRole(role).color
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(frac)
                        .background(hue.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                // Label only the segments wide enough to hold text at a legible size.
                // (Raised from 8sp→9.5sp and 0.16→0.18 so the inline "NETA 30" labels sit one
                //  step above the small-end-of-legible after the 1440×900 upscale.)
                if (frac >= 0.18f) {
                    Text(
                        text = "${role.name.take(4)} ${(p * 100).toInt()}",
                        style = GaddiType.label_micro.copy(fontSize = 9.5.sp),
                        color = BrandTokens.CreamInk,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** One per-role claim row: a seal dot, the role, its claim count, and a bluff-caught stamp. */
@Composable
private fun ClaimRecordRow(
    role: Role,
    claims: Int,
    caught: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_xs),
    ) {
        RoleSealDot(role, 10.dp)
        Text(
            text = role.name,
            style = GaddiType.label_sm,
            color = BrandTokens.CreamInk,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "claimed ×$claims",
            style = GaddiType.label_micro,
            color = BrandTokens.BrassDark,
        )
        if (caught > 0) {
            Spacer(Modifier.width(2.dp))
            Box(
                modifier =
                    Modifier
                        .clip(Squircle(GaddiRadii.sm))
                        .background(GaddiSemantics.Danger.copy(alpha = 0.18f))
                        .border(GaddiDimens.stroke_hairline, GaddiSemantics.Danger.copy(alpha = 0.7f), Squircle(GaddiRadii.sm))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "✗ caught ×$caught",
                    style = GaddiType.label_micro,
                    color = GaddiSemantics.Danger,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** A compact 1..5 "shady-meter" badge — the headline bluff read on a dossier. */
@Composable
private fun ShadyMeter(
    pips: Int,
    label: String,
) {
    val clamped = pips.coerceIn(1, 5)
    val pipColor =
        when (clamped) {
            1, 2 -> GaddiSemantics.Success
            3 -> BrandTokens.PendingAmber
            else -> GaddiSemantics.Danger
        }
    Row(
        modifier =
            Modifier
                .clip(Squircle(GaddiRadii.sm))
                .background(BrandTokens.TeakDark.copy(alpha = 0.92f))
                .border(GaddiDimens.stroke_hairline, pipColor.copy(alpha = 0.6f), Squircle(GaddiRadii.sm))
                .padding(horizontal = GaddiDimens.space_xs, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(5) { idx ->
            Box(
                modifier =
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (idx < clamped) pipColor else GaddiNeutrals.TextDisabled),
            )
        }
        Spacer(Modifier.width(2.dp))
        Text(
            text = label,
            style = GaddiType.label_micro,
            color = pipColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ClaimDetailChitContent(c: ChitContent.ClaimDetail) {
    val voice = LocalGaddiVoice.current
    BrassParchmentSurface(modifier = Modifier.widthIn(max = 320.dp)) {
        if (c.claimedRole != null) {
            val visual = GaddiColors.forRole(c.claimedRole)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_sm)) {
                RoleSealDot(c.claimedRole, 18.dp)
                Text(
                    text = voice.claimDetailTitle(c.actorName ?: "Someone", c.claimedRole.name),
                    style = GaddiType.title_sm,
                    color = BrandTokens.CreamInk,
                )
            }
            Spacer(Modifier.height(GaddiDimens.space_sm))
            BrassDivider()
            Spacer(Modifier.height(GaddiDimens.space_sm))
            ChitLine(label = "On the table:", text = visual.actionLine)
            ChitLine(label = "If it holds:", text = "The claim stands and the action resolves.")
            ChitLine(label = "If challenged:", text = "They must prove ${c.claimedRole.name} — or lose a card.")
            Spacer(Modifier.height(GaddiDimens.space_xs))
            Text(
                text = "\"${voice.claimDetailWhisper(c.claimedRole)}\"",
                style = GaddiType.label_sm.copy(fontStyle = FontStyle.Italic),
                color = BrandTokens.BrassDark,
            )
        } else {
            Text(
                text = voice.tableHeartTitle,
                style = GaddiType.title_sm,
                color = BrandTokens.CreamInk,
            )
            Spacer(Modifier.height(GaddiDimens.space_sm))
            BrassDivider()
            Spacer(Modifier.height(GaddiDimens.space_sm))
            ChitLine(label = "Turn:", text = c.turnNumber.toString())
            ChitLine(label = "Deck:", text = "${c.deckCount} cards left to draw.")
            ChitLine(label = "Treasury:", text = "${c.treasuryCoins} Khokhas in the till.")
            Spacer(Modifier.height(GaddiDimens.space_xs))
            Text(
                text = "\"${voice.tableHeartWhisper}\"",
                style = GaddiType.label_sm.copy(fontStyle = FontStyle.Italic),
                color = BrandTokens.BrassDark,
            )
        }
    }
}

@Composable
private fun LogEventChitContent(c: ChitContent.LogEvent) {
    BrassParchmentSurface(modifier = Modifier.widthIn(max = 320.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_sm)) {
            Text(text = c.icon, style = GaddiType.title_sm)
            Text(
                text = c.title,
                style = GaddiType.title_sm,
                color = BrandTokens.CreamInk,
            )
        }
        Spacer(Modifier.height(GaddiDimens.space_sm))
        BrassDivider()
        Spacer(Modifier.height(GaddiDimens.space_sm))
        Text(
            text = c.narration,
            style = GaddiType.label_sm.copy(fontStyle = FontStyle.Italic),
            color = BrandTokens.BrassDark,
        )
    }
}

@Composable
private fun ChitLine(
    label: String,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_xs),
    ) {
        Text(
            text = label,
            style = GaddiType.label_sm.copy(fontStyle = FontStyle.Normal),
            color = BrandTokens.BrassDark,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        Text(text = text, style = GaddiType.label_sm, color = BrandTokens.CreamInk, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RiskLine(
    positive: Boolean,
    text: String,
) {
    val color = if (positive) GaddiSemantics.Success else GaddiSemantics.Danger
    val prefix = if (positive) "✓" else "✗"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_xs),
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = prefix, style = GaddiType.label_sm, color = color)
        Text(text = text, style = GaddiType.label_sm, color = BrandTokens.CreamInk, modifier = Modifier.weight(1f))
    }
}

// ─────────────────────────── Odds chip ───────────────────────────────────────

@Composable
fun OddsChip(
    conf: BluffOdds.Confidence,
    compact: Boolean = true,
) {
    val pipColor =
        when (conf.pips) {
            1, 2 -> GaddiSemantics.Success
            3 -> BrandTokens.PendingAmber
            else -> GaddiSemantics.Danger
        }
    Row(
        modifier =
            Modifier
                .clip(Squircle(GaddiRadii.sm))
                .background(BrandTokens.TeakDark.copy(alpha = 0.90f))
                .border(GaddiDimens.stroke_hairline, BrandTokens.BrassAged.copy(alpha = 0.5f), Squircle(GaddiRadii.sm))
                .padding(horizontal = GaddiDimens.space_sm, vertical = GaddiDimens.space_xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_xs),
    ) {
        // Pip row
        repeat(5) { idx ->
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (idx < conf.pips) pipColor else GaddiNeutrals.TextDisabled),
            )
        }
        Spacer(Modifier.width(2.dp))
        if (compact) {
            Text(
                text = conf.label,
                style = GaddiType.label_micro,
                color = GaddiNeutrals.TextSecondary,
                maxLines = 1,
            )
        } else {
            Column {
                Text(text = conf.label, style = GaddiType.label_sm, color = GaddiNeutrals.TextPrimary)
                Text(text = conf.whisper, style = GaddiType.label_micro, color = GaddiNeutrals.TextSecondary)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  DECISION COACH — at-the-moment-of-choice read on each option.
//
//  The MoveAdvisor (AI brain) ranks every legal move and tags it truthful / bluff,
//  with odds + a recommended pick. These primitives render that read ON the chips,
//  immersive + uncluttered (License Raj Deco): a tasteful truthful/bluff badge, a
//  compact odds pill, and one brass star on the recommended move. The deep detail
//  lives in the long-press CoachChit.
// ═══════════════════════════════════════════════════════════════════════════════

// Oxblood / amber accents reused across the coach surface.
private val CoachOxblood = Color(0xFF8E2B22)
private val CoachAmber = BrandTokens.PendingAmber

/** The three coaching tints for a move's safety, derived from truthful/bluff. */
internal enum class CoachTone { Truthful, Bluff, Neutral }

internal fun coachTone(
    truthful: Boolean?,
    bluff: Boolean,
): CoachTone =
    when {
        bluff -> CoachTone.Bluff
        truthful == true -> CoachTone.Truthful
        else -> CoachTone.Neutral
    }

/** Accent colour for a coach tone, used for chip tints + badge fills. */
internal fun coachAccent(tone: CoachTone): Color =
    when (tone) {
        CoachTone.Truthful -> GaddiSemantics.Success
        CoachTone.Bluff -> CoachOxblood
        CoachTone.Neutral -> BrandTokens.BrassAged
    }

/**
 * In-voice odds phrasing for a move.
 *  - Challenge: successOdds = P(opponent bluffing) → "~65% they're bluffing".
 *  - Bluff move: successOdds = P(not caught) → "~70% it flies".
 */
private fun coachOddsText(
    isChallenge: Boolean,
    successOdds: Double,
): String {
    val pct = (successOdds * 100).toInt().coerceIn(0, 100)
    return if (isChallenge) "~$pct% bluff" else "~$pct% safe"
}

/**
 * A compact safety badge for one move — the unmistakable "real & safe" vs "risky bluff" read.
 * Truthful → green ✓, Bluff → oxblood ⚠. No-claim moves render nothing (caller skips).
 */
@Composable
internal fun CoachBadge(
    truthful: Boolean?,
    bluff: Boolean,
    claimedRoleName: String?,
    modifier: Modifier = Modifier,
) {
    val tone = coachTone(truthful, bluff)
    if (tone == CoachTone.Neutral) return
    val accent = coachAccent(tone)
    val icon = if (tone == CoachTone.Truthful) "✓" else "⚠"
    val text =
        when (tone) {
            CoachTone.Truthful -> if (claimedRoleName != null) "REAL · ${claimedRoleName.uppercase()}" else "REAL"
            else -> "BLUFF"
        }
    Row(
        modifier =
            modifier
                .clip(Squircle(GaddiRadii.sm))
                .background(accent.copy(alpha = 0.18f))
                .border(GaddiDimens.stroke_hairline, accent.copy(alpha = 0.75f), Squircle(GaddiRadii.sm))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = icon, style = GaddiType.label_micro, color = accent, maxLines = 1)
        Text(
            text = text,
            style = GaddiType.label_micro.copy(letterSpacing = 0.5.sp),
            color = accent,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** A compact odds pill — "~65% bluff" (challenge) or "~70% safe" (bluff move). */
@Composable
internal fun CoachOddsPill(
    isChallenge: Boolean,
    successOdds: Double,
    modifier: Modifier = Modifier,
) {
    val pct = (successOdds * 100).toInt().coerceIn(0, 100)
    // For a challenge, high P(bluff) = good. For a bluff, high P(safe) = good.
    val good = pct >= 55
    val mid = pct in 40..54
    val tint =
        when {
            good -> GaddiSemantics.Success
            mid -> CoachAmber
            else -> CoachOxblood
        }
    Row(
        modifier =
            modifier
                .clip(Squircle(GaddiRadii.sm))
                .background(BrandTokens.TeakDark.copy(alpha = 0.92f))
                .border(GaddiDimens.stroke_hairline, tint.copy(alpha = 0.6f), Squircle(GaddiRadii.sm))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = coachOddsText(isChallenge, successOdds),
            style = GaddiType.label_micro,
            color = tint,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The brass star that marks the single recommended move — small, with a soft rim glow. */
@Composable
internal fun RecommendedStar(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged),
                    ),
                ).border(GaddiDimens.stroke_hairline, BrandTokens.GoldAntique, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "★",
            style = GaddiType.label_micro.copy(fontSize = 9.sp),
            color = BrandTokens.CreamInk,
            maxLines = 1,
        )
    }
}

/**
 * The full DECISION-COACH read for one move (long-press detail). Truthful/bluff verdict,
 * odds, win-prob and the in-voice rationale — everything the compact on-chip badge hints at.
 */
@Composable
private fun CoachChitContent(c: ChitContent.Coach) {
    val tone = coachTone(c.truthful, c.bluff)
    val accent = coachAccent(tone)
    val isChallenge = c.successOdds != null && c.truthful == null && !c.bluff
    BrassParchmentSurface(modifier = Modifier.widthIn(max = 320.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_sm)) {
            Text(text = c.moveLabel, style = GaddiType.title_sm, color = BrandTokens.CreamInk, modifier = Modifier.weight(1f))
            if (c.recommended) RecommendedStar()
        }
        Spacer(Modifier.height(GaddiDimens.space_sm))
        BrassDivider()
        Spacer(Modifier.height(GaddiDimens.space_sm))

        // Verdict line — the safety read.
        when (tone) {
            CoachTone.Truthful -> RiskLine(positive = true, text = "Natural counter — you really hold this. Safe to back up.")
            CoachTone.Bluff -> RiskLine(positive = false, text = "Bluff — you don't hold it. A card is at stake if challenged.")
            CoachTone.Neutral -> ChitLine(label = "Read:", text = "No role claim — nobody can challenge this.")
        }

        // Odds line.
        if (c.successOdds != null) {
            val pct = (c.successOdds * 100).toInt().coerceIn(0, 100)
            val oddsLabel = if (isChallenge) "Opponent bluffing:" else "Survives unchallenged:"
            ChitLine(label = oddsLabel, text = "~$pct%")
        }
        ChitLine(label = "Win chance:", text = "${(c.winProb * 100).toInt()}%")

        // The READ — a belief-grounded line the coach leads with (card-accounting, not just a bare %).
        if (c.beliefLine != null) {
            Spacer(Modifier.height(GaddiDimens.space_xs))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(Squircle(GaddiRadii.sm))
                        .background(BrandTokens.GoldAntique.copy(alpha = 0.16f))
                        .border(GaddiDimens.stroke_hairline, BrandTokens.GoldAntique.copy(alpha = 0.5f), Squircle(GaddiRadii.sm))
                        .padding(horizontal = GaddiDimens.space_sm, vertical = GaddiDimens.space_xs),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "🔎", style = GaddiType.label_sm)
                    Text(
                        text = c.beliefLine,
                        style = GaddiType.label_sm,
                        color = BrandTokens.CreamInk,
                    )
                }
            }
        }

        if (c.recommended) {
            Spacer(Modifier.height(GaddiDimens.space_xs))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_xs)) {
                RecommendedStar()
                Text(
                    text = "The advisor's pick.",
                    style = GaddiType.label_sm.copy(letterSpacing = 0.5.sp),
                    color = BrandTokens.BrassDark,
                )
            }
        }

        Spacer(Modifier.height(GaddiDimens.space_xs))
        BrassDivider()
        Spacer(Modifier.height(GaddiDimens.space_xs))
        Text(
            text = "\"${c.rationale}\"",
            style = GaddiType.label_sm.copy(fontStyle = FontStyle.Italic),
            color = accent,
        )
    }
}

// ─────────────────────────── C. HINT RAIL ────────────────────────────────────
// Always-on 28dp brass strip above the action grid.
// Shows a 1-line situational hint + odds chip during reactions.

/**
 * Compact brass COACH ON / OFF toggle chip — lives in the HintRail right of the hint text, left
 * of the NIYAM button. Tapping it flips [coachEnabled] live AND persists via [onToggleCoach].
 * Styled as a pill with a gold rim when ON and a dimmed teak fill when OFF.
 */
@Composable
private fun PlayBestMoveChip(onClick: () -> Unit) {
    val voice = LocalGaddiVoice.current
    Box(
        modifier =
            Modifier
                .height(24.dp)
                .widthIn(min = 72.dp)
                .clip(Squircle(GaddiDimens.r_sm))
                .background(BrandTokens.GoldAntique.copy(alpha = 0.20f))
                .border(GaddiDimens.stroke_hairline, BrandTokens.GoldAntique, Squircle(GaddiDimens.r_sm))
                .semantics(mergeDescendants = true) {
                    role = androidx.compose.ui.semantics.Role.Button
                    contentDescription = voice.playBestMove
                }.clickable(onClick = onClick)
                .padding(horizontal = GaddiDimens.space_sm, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "★ ${voice.playBestMove}",
            style = GaddiType.label_micro.copy(letterSpacing = 0.8.sp),
            color = BrandTokens.GoldAntique,
            maxLines = 1,
        )
    }
}

@Composable
private fun CoachToggleChip(
    coachEnabled: Boolean,
    onToggle: () -> Unit,
) {
    val accentColor = if (coachEnabled) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.55f)
    val bgColor = if (coachEnabled) BrandTokens.BrassAged.copy(alpha = 0.22f) else BrandTokens.TeakDark.copy(alpha = 0.85f)
    val label = if (coachEnabled) stringResource(Res.string.game_coach_on) else stringResource(Res.string.game_coach_off)
    val toggleDesc = stringResource(Res.string.a11y_decision_coach_toggle)
    Box(
        modifier =
            Modifier
                .height(24.dp)
                .widthIn(min = 72.dp)
                .clip(Squircle(GaddiDimens.r_sm))
                .background(bgColor)
                .border(GaddiDimens.stroke_hairline, accentColor, Squircle(GaddiDimens.r_sm))
                // A11y: a toggle — role Switch, on/off state spoken, single merged node.
                .semantics(mergeDescendants = true) {
                    role = androidx.compose.ui.semantics.Role.Switch
                    toggleableState = if (coachEnabled) ToggleableState.On else ToggleableState.Off
                    stateDescription = if (coachEnabled) "On" else "Off"
                    contentDescription = toggleDesc
                }.clickable(onClick = onToggle)
                .padding(horizontal = GaddiDimens.space_sm, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = GaddiType.label_micro.copy(letterSpacing = 0.8.sp),
            color = accentColor,
            maxLines = 1,
        )
    }
}

@Composable
fun HintRail(
    gamePhase: GamePhase,
    state: GameUiState,
    onOpenGazette: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleCoach: (() -> Unit)? = null,
    // M5 ASSISTANT one-tap "play best move". Shown only when coach is on AND it is the human's live
    // decision. Null in the render harness / when not wired.
    onPlayBestMove: (() -> Unit)? = null,
) {
    val voice = LocalGaddiVoice.current
    val (hintText, hintTone) = deriveHintText(gamePhase, state, voice)

    // Derive bluff odds if in a reaction window with a claimed role
    val oddsConf: BluffOdds.Confidence? =
        if (gamePhase is GamePhase.ReactionWindow && gamePhase.claimedRole != null) {
            val role = gamePhase.claimedRole
            val cfg = state.view.config
            val allFaceUp = state.view.players.flatMap { it.faceUpRoles } + state.view.myFaceUp
            val eliminatedForRole = allFaceUp.count { it == role }
            val myHandHasRole = state.view.myInfluence.count { it == role }
            val oppFaceDown =
                state.view.players
                    .firstOrNull { it.id == gamePhase.actor }
                    ?.faceDownCount ?: 1
            BluffOdds.estimate(
                claimedRole = role,
                copiesPerRole = cfg.copiesPerRole,
                deckSize = cfg.deckSize,
                eliminatedRolesForClaimedRole = eliminatedForRole,
                myHandContainsClaimedRole = myHandHasRole,
                opponentFaceDownCount = oppFaceDown,
                totalVisibleCards = allFaceUp.size,
            )
        } else {
            null
        }

    val borderColor =
        when (hintTone) {
            HintTone.Warning -> GaddiSemantics.Danger
            HintTone.Gold -> BrandTokens.GoldAntique
            else -> BrandTokens.BrassAged
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(Squircle(GaddiRadii.sm))
                .background(BrandTokens.TeakDark.copy(alpha = 0.92f))
                .border(
                    GaddiDimens.stroke_hairline,
                    Brush.horizontalGradient(
                        listOf(borderColor.copy(alpha = 0.6f), borderColor.copy(alpha = 0.4f), borderColor.copy(alpha = 0.6f)),
                    ),
                    Squircle(GaddiRadii.sm),
                ).padding(horizontal = GaddiDimens.space_sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_sm),
    ) {
        // Hint text
        Text(
            text = hintText,
            style = GaddiType.label_micro,
            color =
                when (hintTone) {
                    HintTone.Warning -> GaddiSemantics.Danger
                    HintTone.Gold -> BrandTokens.GoldAntique
                    else -> GaddiNeutrals.TextSecondary
                },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Odds chip during reactions
        if (oddsConf != null) {
            OddsChip(conf = oddsConf, compact = true)
        }

        // M5 one-tap PLAY BEST MOVE — only when it's the human's live decision and guidance is visible.
        if (onPlayBestMove != null && state.isHumanTurn && state.coachGuidanceVisible) {
            PlayBestMoveChip(onClick = onPlayBestMove)
        }

        // COACH ON/OFF toggle — always present in the rail; tap flips coach guidance live
        if (onToggleCoach != null) {
            CoachToggleChip(coachEnabled = state.coachEnabled, onToggle = onToggleCoach)
        }

        // NIYAM '?' button — always present
        NiyamButton(onClick = onOpenGazette)
    }
}

private enum class HintTone { Normal, Gold, Warning }

private fun deriveHintText(
    gamePhase: GamePhase,
    state: GameUiState,
    voice: GaddiVoice,
): Pair<String, HintTone> =
    when (gamePhase) {
        is GamePhase.PickAction -> {
            if (state.view.myCoins >= state.view.config.forcedCoupThreshold) {
                voice.phaseHint(PhaseHint.CoinCapKhela) to HintTone.Warning
            } else {
                voice.phaseHint(PhaseHint.PickAction) to HintTone.Gold
            }
        }
        is GamePhase.PickTarget -> {
            voice.phaseHint(PhaseHint.PickTarget) to HintTone.Gold
        }
        is GamePhase.Confirm -> {
            voice.phaseHint(PhaseHint.Confirm) to HintTone.Gold
        }
        is GamePhase.ReactionWindow -> {
            val actorName = personaNameOrDefault(gamePhase.actor, state)
            val actionStr = actionHindiName(gamePhase.action)
            when (gamePhase.step) {
                ReactionStep.CHALLENGE_ACTION ->
                    voice.phaseHint(PhaseHint.ChallengeAction(actorName, actionStr)) to HintTone.Warning
                ReactionStep.BLOCK ->
                    voice.phaseHint(PhaseHint.Block) to HintTone.Warning
                ReactionStep.CHALLENGE_BLOCK ->
                    voice.phaseHint(PhaseHint.ChallengeBlock(actorName)) to HintTone.Warning
            }
        }
        is GamePhase.LoseInfluence ->
            voice.phaseHint(PhaseHint.LoseInfluence(loseInfluenceCause(state))) to HintTone.Warning
        is GamePhase.Exchange ->
            voice.phaseHint(PhaseHint.Exchange) to HintTone.Gold
        is GamePhase.InvestigatePeek ->
            voice.phaseHint(PhaseHint.InvestigatePeek) to HintTone.Gold
        is GamePhase.Idle -> {
            val actorId =
                when (val phase = state.view.phase) {
                    is PhaseView.Turn -> phase.actor
                    is PhaseView.Reactions -> phase.actor
                    else -> null
                }
            val actorName = actorId?.let { personaNameOrDefault(it, state) }
            voice.phaseHint(PhaseHint.Thinking(actorName)) to HintTone.Normal
        }
        is GamePhase.GameOver ->
            voice.phaseHint(PhaseHint.GameOver) to HintTone.Normal
    }

// ─────────────────────────── NIYAM roundel button ────────────────────────────

@Composable
fun NiyamButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged, BrandTokens.BrassDark),
                    ),
                ).border(1.5.dp, BrandTokens.BrassDark, CircleShape)
                .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "?", style = GaddiType.label_md, color = BrandTokens.TeakDark, textAlign = TextAlign.Center)
        Text(
            text = stringResource(Res.string.game_niyam_label),
            style = GaddiType.label_micro.copy(letterSpacing = 0.5.sp),
            color = BrandTokens.TeakDark.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

// ─────────────────────────── A. NIYAM GAZETTE ────────────────────────────────

@Composable
fun NiyamGazette(
    onDismiss: () -> Unit,
    onReplayPrimer: () -> Unit,
    initialTab: Int = 0,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(BrandTokens.TeakInk.copy(alpha = 0.80f))
                    .clickable(
                        indication = null,
                        interactionSource =
                            remember {
                                androidx.compose.foundation.interaction
                                    .MutableInteractionSource()
                            },
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            // Dialog card — AAA rebuild (design-language.md #1/#2): the old brass-fill +
            // sweep-gradient-border card is gone. The gazette now sits on the same warm lit
            // ground every screen shares, lifted off the scrim by a real cast shadow — no
            // outline framing the document. Stops click propagation to the scrim.
            val cardShape = Squircle(GaddiRadii.xl)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth(0.94f)
                        .fillMaxHeight(0.92f)
                        .tableDepth(cardShape, elevation = 14.dp, lifted = true)
                        .clip(cardShape)
                        .litGround()
                        .clickable(
                            indication = null,
                            interactionSource =
                                remember {
                                    androidx.compose.foundation.interaction
                                        .MutableInteractionSource()
                                },
                            onClick = {},
                        ),
            ) {
                GazetteContent(onDismiss = onDismiss, onReplayPrimer = onReplayPrimer, initialTab = initialTab)
            }
        }
    }
}

@Composable
private fun GazetteContent(
    onDismiss: () -> Unit,
    onReplayPrimer: () -> Unit,
    initialTab: Int,
) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    val tabs = listOf(stringResource(Res.string.game_tab_darbar), "DHANDHA", "DASTUR", "HISAAB")
    val closeGazetteDesc = stringResource(Res.string.a11y_close_gazette)

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Masthead — engraved, not a filled brass bar (design-language.md #3) ────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GaddiDimens.space_lg, vertical = GaddiDimens.space_md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = stringResource(Res.string.game_gazette_title),
                    style = GaddiType.display.copy(letterSpacing = 1.sp).rozha(),
                    color = BrandTokens.GoldAntique,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.game_gazette_subtitle),
                    style = GaddiType.label_micro.copy(fontStyle = FontStyle.Italic),
                    color = GaddiNeutrals.TextMuted,
                )
            }
            // Close affordance — a soft radial ghost circle, same idiom as EngravedTurnHeader's
            // gazette-open button. No fill, no border framing it.
            Box(
                modifier =
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(listOf(BrandTokens.GoldAntique.copy(alpha = 0.18f), Color.Transparent)),
                        ).clickable(onClick = onDismiss)
                        .semantics { contentDescription = closeGazetteDesc },
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", style = GaddiType.label_md, color = BrandTokens.BrassAged)
            }
        }
        HairlineRule()

        // ── Tab rail — DM Mono labels, active tab reads via a hairline underline ──
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GaddiDimens.space_lg, vertical = GaddiDimens.space_sm),
            horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_lg),
        ) {
            tabs.forEachIndexed { idx, tab ->
                val active = idx == selectedTab
                Column(
                    modifier =
                        Modifier
                            .clickable(
                                indication = null,
                                interactionSource =
                                    remember {
                                        androidx.compose.foundation.interaction
                                            .MutableInteractionSource()
                                    },
                            ) { selectedTab = idx }
                            .padding(vertical = GaddiDimens.space_xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tab,
                        style = GaddiType.label_sm.dmMono().copy(letterSpacing = 1.5.sp),
                        color = if (active) BrandTokens.GoldAntique else GaddiNeutrals.TextMuted,
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier =
                            Modifier
                                .height(2.dp)
                                .width(if (active) 22.dp else 0.dp)
                                .background(BrandTokens.GoldAntique),
                    )
                }
            }
        }
        HairlineRule(alpha = 0.3f)

        // ── Tab content ───────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> DarbarTab()
                1 -> DhandhaTab()
                2 -> DasturTab()
                3 -> HisaabTab()
            }
        }

        // ── Footer — a hairline rule, not a filled brass bar ─────────────────────
        HairlineRule(alpha = 0.3f)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GaddiDimens.space_lg, vertical = GaddiDimens.space_sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.game_gazette_footer_quote),
                style = GaddiType.label_micro.copy(fontStyle = FontStyle.Italic),
                color = GaddiNeutrals.TextMuted,
            )
            Text(
                text = stringResource(Res.string.game_replay_primer),
                style = GaddiType.label_sm,
                color = BrandTokens.BrassAged,
                modifier = Modifier.clickable(onClick = onReplayPrimer),
            )
        }
    }
}

// ─────────────────────────── TAB 1 — DARBAR (the 5 roles) ────────────────────

@Composable
private fun DarbarTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = GaddiDimens.space_lg),
    ) {
        items(Role.entries) { role ->
            RoleReferenceCard(role = role)
        }
        item {
            Spacer(Modifier.height(GaddiDimens.space_sm))
            Text(
                text = stringResource(Res.string.game_darbar_tab_footnote),
                style = GaddiType.label_sm.copy(fontStyle = FontStyle.Italic),
                color = BrandTokens.BrassAged,
                modifier = Modifier.fillMaxWidth().padding(vertical = GaddiDimens.space_sm),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A role's reference entry — a crafted paper row resting on the gazette's lit ground,
 * separated from its neighbour by a hairline (design-language.md #4), not a bordered box.
 */
@Composable
private fun RoleReferenceCard(role: Role) {
    val voice = LocalGaddiVoice.current
    val visual = GaddiColors.forRole(role)
    HairlineRow(verticalPadding = 12.dp) {
        // Seal — a brass-rimmed, role-hued disc (the same token material as OpponentSeatToken).
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .shadow(4.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                    .clip(CircleShape)
                    .background(visual.color)
                    .border(1.5.dp, BrandTokens.BrassAged.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            com.kursi.designsystem.RoleGlyph(
                role = role,
                tint = GaddiNeutrals.Cream,
                deboss = false,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${role.name} · ${visual.title}",
                style = GaddiType.label_md,
                color = GaddiNeutrals.TextPrimary,
            )
            Text(text = visual.actionLine, style = GaddiType.label_sm, color = BrandTokens.BrassAged)
            Text(text = visual.blockLine, style = GaddiType.label_sm, color = GaddiNeutrals.TextSecondary)
            Text(
                text = voice.roleBlurb(role),
                style = GaddiType.label_micro,
                color = GaddiNeutrals.TextMuted,
            )
        }
    }
}

// ─────────────────────────── TAB 2 — DHANDHA (general actions) ───────────────

@Composable
private fun DhandhaTab() {
    val actions =
        listOf(
            Triple(Action.Income, "DEHAADI", "No claim — honest. Nobody blocks. The grind."),
            Triple(Action.ForeignAid, "FDI", "No claim. NETA can block it."),
            Triple(Action.Tax, "GHOTALA", "Claims NETA. Challengeable."),
            Triple(Action.Exchange, "SETTING", "Claims JUGAADU. Challengeable."),
            Triple(Action.Steal(PlayerId(0)), "VASOOLI", "Claims BABU. Challengeable. BABU/JUGAADU block."),
            Triple(
                Action.Investigate(PlayerId(0)),
                "JAANCH",
                "Claims PATRAKAAR. Peek a card, optionally force a redraw. Challengeable. Unblockable. (Big tables only.)",
            ),
            Triple(Action.Assassinate(PlayerId(0)), "SUPARI", "Claims BHAI. Challengeable. VAKIL blocks."),
            Triple(Action.Coup(PlayerId(0)), "KHELA", "No claim. Unblockable, unstoppable. Mandatory at 10+."),
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = GaddiDimens.space_lg),
    ) {
        item { LedgerHeaderRow(listOf("ACTION" to 1.2f, "EFFECT" to 0.6f, "RULES" to 2f)) }
        items(actions) { (action, name, rules) ->
            HairlineRow(verticalPadding = 10.dp) {
                Text(name, style = GaddiType.label_sm, color = GaddiNeutrals.TextPrimary, modifier = Modifier.weight(1.2f))
                Text(
                    actionCostSummary(action),
                    style = GaddiType.label_sm,
                    color = BrandTokens.GoldAntique,
                    modifier = Modifier.weight(0.6f),
                )
                Text(rules, style = GaddiType.label_micro, color = GaddiNeutrals.TextSecondary, modifier = Modifier.weight(2f))
            }
        }
        item {
            Spacer(Modifier.height(GaddiDimens.space_sm))
            Text(
                text = stringResource(Res.string.game_dhandha_tab_footnote),
                style = GaddiType.label_sm.copy(fontStyle = FontStyle.Italic),
                color = BrandTokens.BrassAged,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = GaddiDimens.space_sm),
            )
        }
    }
}

// ─────────────────────────── TAB 3 — DASTUR (the procedure) ─────────────────

@Composable
private fun DasturTab() {
    val steps =
        listOf(
            Triple(
                "1. YOUR TURN — Make a Move",
                "Pick one action. If it claims a role, you are declaring you hold that card. You might be lying.",
                BrandTokens.GoldAntique,
            ),
            Triple(
                "2. THE TABLE REACTS — Anyone Object?",
                "CHALLENGE (\"Saboot dikhao!\"): someone says you're bluffing.\n" +
                    "  • You had it → challenger loses a card. You reshuffle and redraw.\n" +
                    "  • You didn't → you lose a card. The lie costs you.\n" +
                    "BLOCK (\"Yeh nahi chalega\"): someone claims a counter-role to stop you. A block can itself be challenged.",
                BrandTokens.PendingAmber,
            ),
            Triple(
                "3. RESOLVE — Hisaab Baraabar",
                "Cards lost are flipped face-up forever (your secrets, exposed). Last player with a card standing takes the GADDI.",
                GaddiSemantics.Success,
            ),
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = GaddiDimens.space_lg),
    ) {
        itemsIndexed(steps) { idx, (title, body, accent) ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = GaddiDimens.space_sm),
                verticalArrangement = Arrangement.spacedBy(GaddiDimens.space_xs),
            ) {
                Text(text = title, style = GaddiType.title_sm, color = accent)
                Text(text = body, style = GaddiType.label_sm, color = GaddiNeutrals.TextSecondary)
            }
            if (idx < steps.lastIndex) HairlineRule(alpha = 0.25f)
        }
        item {
            Spacer(Modifier.height(GaddiDimens.space_sm))
            Text(
                text = stringResource(Res.string.game_dastur_tab_footnote),
                style = GaddiType.label_sm.copy(fontStyle = FontStyle.Italic),
                color = BrandTokens.BrassAged,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = GaddiDimens.space_sm),
            )
        }
    }
}

// ─────────────────────────── TAB 4 — HISAAB (who-beats-whom matrix) ──────────

@Composable
private fun HisaabTab() {
    data class MatrixRow(
        val actionName: String,
        val effect: String,
        val challengeNote: String, // "safe" or "yes → ROLE"
        val blockedBy: String, // "—" or "ROLE / ROLE"
    )

    val rows =
        listOf(
            MatrixRow("DEHAADI", "+1", "safe", "— (untouchable)"),
            MatrixRow("FDI", "+2", "safe", "NETA"),
            MatrixRow("GHOTALA", "+3", "yes → NETA", "—"),
            MatrixRow("VASOOLI", "steal", "yes → BABU", "BABU · JUGAADU"),
            MatrixRow("JAANCH", "peek", "yes → PATRAKAAR", "— (unblockable)"),
            MatrixRow("SUPARI", "−3 hit", "yes → BHAI", "VAKIL"),
            MatrixRow("SETTING", "swap", "yes → JUGAADU", "—"),
            MatrixRow("KHELA", "−7 coup", "safe", "— (unstoppable)"),
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = GaddiDimens.space_lg),
    ) {
        item {
            LedgerHeaderRow(
                listOf("ACTION" to 1.2f, "EFFECT" to 0.6f, "CHALLENGE?" to 1.3f, "BLOCKED BY" to 1.5f),
            )
        }

        items(rows) { row ->
            val isSafe = row.challengeNote == "safe"
            val isUnstoppable = row.blockedBy.startsWith("—")
            HairlineRow(verticalPadding = 10.dp) {
                Text(row.actionName, style = GaddiType.label_sm, color = GaddiNeutrals.TextPrimary, modifier = Modifier.weight(1.2f))
                Text(row.effect, style = GaddiType.numeral_sm, color = BrandTokens.GoldAntique, modifier = Modifier.weight(0.6f))
                Text(
                    text = row.challengeNote,
                    style = GaddiType.label_micro,
                    color = if (isSafe) GaddiSemantics.Success else BrandTokens.PendingAmber,
                    modifier = Modifier.weight(1.3f),
                )
                Text(
                    text = row.blockedBy,
                    style = GaddiType.label_micro,
                    color = if (isUnstoppable) GaddiNeutrals.TextDisabled else GaddiSemantics.Block,
                    modifier = Modifier.weight(1.5f),
                )
            }
        }

        item {
            Spacer(Modifier.height(GaddiDimens.space_md))
            // Block-chain quick-read — plain ground + hairline header, no bordered box.
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = GaddiDimens.space_sm),
                verticalArrangement = Arrangement.spacedBy(GaddiDimens.space_xs),
            ) {
                Text(
                    text = stringResource(Res.string.game_block_chain_header),
                    style = GaddiType.label_sm.dmMono().copy(letterSpacing = 1.sp),
                    color = BrandTokens.GoldAntique,
                )
                HairlineRule(alpha = 0.3f)
                Spacer(Modifier.height(2.dp))
                val items =
                    listOf(
                        "NETA stops free money (FDI).",
                        "BABU + JUGAADU both guard your wallet (VASOOLI).",
                        "VAKIL is the only thing between you and a SUPARI hit.",
                        "A block is also a claim — and any claim can be challenged.",
                    )
                items.forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(GaddiDimens.space_xs), verticalAlignment = Alignment.Top) {
                        Text("▸", style = GaddiType.label_micro, color = BrandTokens.BrassAged)
                        Text(line, style = GaddiType.label_micro, color = GaddiNeutrals.TextSecondary, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(GaddiDimens.space_sm))
            Text(
                text = stringResource(Res.string.game_hisaab_tab_footnote),
                style = GaddiType.label_sm.copy(fontStyle = FontStyle.Italic),
                color = BrandTokens.BrassAged,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────── D. SWEARING-IN PRIMER ───────────────────────────

data class CoachStep(
    val title: String,
    val body: String,
    val highlightLabel: String,
)

private val primerSteps =
    listOf(
        CoachStep(
            title = "Your Certificates",
            highlightLabel = "HAND",
            body = "These two certificates are your secret identities. Protect them — lose both and you're out.",
        ),
        CoachStep(
            title = "Your Coins",
            highlightLabel = "COINS",
            body = "Coins buy power. 7 buys a hit (KHELA). 10 forces one.",
        ),
        CoachStep(
            title = "The Action Grid",
            highlightLabel = "DOCK",
            body = "Every turn, one move. Some are honest. Most are… flexible.",
        ),
        CoachStep(
            title = "Your Rivals",
            highlightLabel = "OPPONENT",
            body = "Three rivals. Long-press anyone to read their dossier.",
        ),
        CoachStep(
            title = "The Gazette",
            highlightLabel = "NIYAM",
            body = "Lost? The Gazette has every rule. Long-press for the who-beats-whom matrix.",
        ),
    )

@Composable
fun SwearingInPrimer(onDone: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val current = primerSteps[step]
    val isLast = step == primerSteps.lastIndex

    // Full-screen teak scrim
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BrandTokens.TeakDark.copy(alpha = 0.88f))
                .clickable(
                    indication = null,
                    interactionSource =
                        remember {
                            androidx.compose.foundation.interaction
                                .MutableInteractionSource()
                        },
                    onClick = {},
                ),
    ) {
        // Central coach chit
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 360.dp)
                    .clip(Squircle(GaddiRadii.xl))
                    .background(
                        Brush.verticalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged, BrandTokens.BrassDark)),
                    ).border(
                        2.dp,
                        Brush.sweepGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark, BrandTokens.GoldAntique)),
                        Squircle(GaddiRadii.xl),
                    ).padding(2.dp)
                    .clip(Squircle(GaddiRadii.xl))
                    .background(BrandTokens.PaperCream)
                    .padding(GaddiDimens.space_xl),
            verticalArrangement = Arrangement.spacedBy(GaddiDimens.space_md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Step indicator
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(primerSteps.size) { idx ->
                    Box(
                        modifier =
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (idx <= step) BrandTokens.BrassDark else BrandTokens.BrassDark.copy(alpha = 0.2f)),
                    )
                }
            }

            // Highlight label
            Box(
                modifier =
                    Modifier
                        .clip(Squircle(GaddiRadii.sm))
                        .background(BrandTokens.BrassDark.copy(alpha = 0.15f))
                        .border(GaddiDimens.stroke_hairline, BrandTokens.BrassDark, Squircle(GaddiRadii.sm))
                        .padding(horizontal = GaddiDimens.space_md, vertical = GaddiDimens.space_xs),
            ) {
                Text(
                    text = "▸ ${current.highlightLabel}",
                    style = GaddiType.label_sm.copy(letterSpacing = 1.sp),
                    color = BrandTokens.BrassDark,
                )
            }

            Text(
                text = current.title,
                style = GaddiType.title_md,
                color = BrandTokens.CreamInk,
                textAlign = TextAlign.Center,
            )
            Text(
                text = current.body,
                style = GaddiType.body,
                color = BrandTokens.CreamInk.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(GaddiDimens.space_sm))

            // Buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Skip
                Text(
                    text = stringResource(Res.string.game_primer_skip),
                    style = GaddiType.label_sm,
                    color = BrandTokens.BrassDark.copy(alpha = 0.7f),
                    modifier =
                        Modifier.clickable {
                            PrimerPrefs.markSeen()
                            onDone()
                        },
                )

                // Next / Sworn In
                Box(
                    modifier =
                        Modifier
                            .clip(Squircle(GaddiRadii.md))
                            .background(
                                Brush.horizontalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged)),
                            ).border(1.dp, BrandTokens.BrassDark, Squircle(GaddiRadii.md))
                            .clickable {
                                if (isLast) {
                                    PrimerPrefs.markSeen()
                                    onDone()
                                } else {
                                    step += 1
                                }
                            }.padding(horizontal = GaddiDimens.space_lg, vertical = GaddiDimens.space_sm),
                ) {
                    Text(
                        text = if (isLast) stringResource(Res.string.game_primer_done) else "Next",
                        style = GaddiType.label_md,
                        color = BrandTokens.TeakDark,
                    )
                }
            }
        }
    }
}

// ─────────────────────────── Internal helpers ────────────────────────────────

internal fun actionHindiName(action: Action): String =
    when (action) {
        Action.Income -> "DEHAADI"
        Action.ForeignAid -> "FDI"
        Action.Tax -> "GHOTALA"
        Action.Exchange -> "SETTING"
        is Action.Steal -> "VASOOLI"
        is Action.Investigate -> "JAANCH"
        is Action.Assassinate -> "SUPARI"
        is Action.Coup -> "KHELA"
        Action.BailPe -> "BAIL PE"
        Action.Sabotage -> "BALI KHEL"
        is Action.Hawala -> "HAWALA"
        Action.Emergency -> "ADHYADESH"
    }

internal fun actionCostSummary(action: Action): String =
    when (action) {
        Action.Income -> "+1"
        Action.ForeignAid -> "+2"
        Action.Tax -> "+3"
        Action.Exchange -> "swap"
        is Action.Steal -> "+2 steal"
        is Action.Investigate -> "peek"
        is Action.Assassinate -> "−3"
        is Action.Coup -> "−7"
        Action.BailPe -> "−9 restore"
        Action.Sabotage -> "+3 −card"
        is Action.Hawala -> "gift"
        Action.Emergency -> "−all coins"
    }
