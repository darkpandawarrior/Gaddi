package com.kursi.shared.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.BrandTokens
import com.kursi.designsystem.BrassToken
import com.kursi.designsystem.KursiNeutrals
import com.kursi.designsystem.KursiRoleHues
import com.kursi.designsystem.KursiType
import com.kursi.designsystem.RoleGlyph
import com.kursi.designsystem.litGround
import com.kursi.engine.Role
import com.kursi.shared.strings.LocalKursiStrings
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.label_jhooth_stamp
import kursi.core.designsystem.generated.resources.label_move_dehaadi_fdi
import kursi.core.designsystem.generated.resources.tutorial_action_ghotala
import kursi.core.designsystem.generated.resources.tutorial_action_khela
import kursi.core.designsystem.generated.resources.tutorial_action_setting
import kursi.core.designsystem.generated.resources.tutorial_action_vakil
import kursi.core.designsystem.generated.resources.tutorial_coin_tally
import kursi.core.designsystem.generated.resources.tutorial_persona_netaji_vachan
import kursi.core.designsystem.generated.resources.tutorial_persona_vakil_loophole
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The three post-claim mechanics taught as their own tappable beats (spec §6: claim → challenge →
 *  block → coup → exchange — claim/challenge/reveal are beats 4-6; these are 7-9). */
private enum class Mechanic { BLOCK, COUP, EXCHANGE }

/**
 * PEHLI HAZRI — the interactive tutorial (M5 ONBOARD §1; reworked per spec §6 into a
 * mechanic-at-a-time sequence).
 *
 * A guided "training round" that teaches by DOING rather than by reading. A seeded, scripted table
 * sits behind a coach chit; the Advisor (Salahkaar) narrates ten beats, bilingually, via
 * [LocalKursiStrings] + [LocalKursiVoice], one mechanic per screen. The spine of the lesson is a
 * GUARANTEED bluff-caught teaching beat: the learner stamps GHOTALA (claiming NETA without holding
 * it), a scripted rival (Babu Filewala) challenges, and the card flips to reveal JHOOTH — the learner
 * watches a bluff get caught and an influence lost, the single most important rule loop in the game
 * (claim → challenge → verdict). Three further tappable beats then illustrate BLOCK (VAKIL stops a
 * SUPARI), COUP (KHELA — unblockable, unchallengeable), and EXCHANGE (SETTING — refresh your hand)
 * against their own scripted setups before graduation.
 *
 * This is intentionally a SELF-CONTAINED scripted overlay, not a live [com.kursi.feature.game.GameSession]:
 * a real match can't be forced to deal a specific bluff or guarantee a specific challenge without
 * fighting the deterministic engine, and a scripted beat sheet is the robust way to make each
 * teaching moment land every time. The visuals reuse the License Raj Deco tokens so it reads as the
 * same office, not a separate tutorial skin.
 *
 * @param onDone Called when the learner finishes (or skips) — the app routes them onward.
 */
@Composable
fun TutorialScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** Render-harness only: start the flow on a given beat so static shots can capture a specific
     *  teaching moment without driving the UI. Defaults to 0 (the live entry). */
    initialStep: Int = 0,
) {
    val s = LocalKursiStrings.current

    // The ten beats, paired (title, body) from the localized string table. One concept per screen.
    val beats =
        remember(s) {
            listOf(
                s.tut1Title to s.tut1Body,
                s.tut2Title to s.tut2Body,
                s.tut3Title to s.tut3Body,
                s.tut4Title to s.tut4Body,
                s.tut5Title to s.tut5Body, // YOU act: GHOTALA (claim)
                s.tut6Title to s.tut6Body, // rival challenges
                s.tut7Title to s.tut7Body, // reveal — JHOOTH (bluff caught)
                s.tutBlockTitle to s.tutBlockBody, // YOU act: VAKIL blocks SUPARI
                s.tutCoupTitle to s.tutCoupBody, // YOU act: KHELA (coup)
                s.tutExchangeTitle to s.tutExchangeBody, // YOU act: SETTING (exchange)
                s.tut8Title to s.tut8Body, // graduation
            )
        }

    var step by remember { mutableStateOf(initialStep.coerceIn(0, beats.lastIndex)) }
    val isLast = step == beats.lastIndex
    val (title, body) = beats[step]

    // Scripted table-state flags for the claim → challenge → reveal spine, derived from the beat:
    //  4 = the learner is being prompted to ACT (GHOTALA chip pulses, "DO IT" CTA shown).
    //  5 = Babu Filewala has challenged (challenge banner over the human seat).
    //  6 = the card has flipped to reveal JHOOTH (the bluff is caught, one card face-up + lost).
    val promptingAction = step == 4
    val challenged = step >= 5
    val revealed = step >= 6

    // The three mechanic beats (7-9) each stay on ONE screen: tap the chip, watch it resolve in
    // place, then continue — [mechanicActed] is local to the beat (keyed on [step], so it resets on
    // Back/replay) and gates whether the primary button performs the move or advances to the next beat.
    val mechanic =
        when (step) {
            7 -> Mechanic.BLOCK
            8 -> Mechanic.COUP
            9 -> Mechanic.EXCHANGE
            else -> null
        }
    var mechanicActed by remember(step) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().litGround()) {
        // ── Scripted table backdrop (always present; mutates with the beat) ─────
        if (mechanic != null) {
            MechanicTable(mechanic = mechanic, acted = mechanicActed)
        } else {
            ScriptedTable(
                challenged = challenged,
                revealed = revealed,
                promptingAction = promptingAction,
                challengerName = ScriptedChallengerName,
            )
        }

        // ── Header strip ────────────────────────────────────────────────────────
        Column(Modifier.fillMaxSize()) {
            TutorialHeader(
                header = s.tutorialHeader,
                badge = s.tutorialBadge,
                stepLabel = s.tutorialStepLabel,
                step = step,
                total = beats.size,
                onSkip = onDone,
                skipLabel = s.tutorialSkip,
            )
            Spacer(Modifier.weight(1f))

            // ── Coach chit (the narrator), docked low so the table reads above it ──
            CoachChit(
                coachTag = s.tutorialCoachTag,
                title = title,
                body = body,
                step = step,
                total = beats.size,
                primaryLabel =
                    when {
                        // Beat 5 (index 4) makes the primary CTA the "stamp GHOTALA" action so the learner
                        // performs the move; advancing reveals the challenge + bluff-caught beats.
                        promptingAction -> s.tutorialDoIt
                        // The BLOCK/COUP/EXCHANGE beats prompt their own action until tapped once.
                        mechanic != null && !mechanicActed -> mechanicDoLabel(mechanic, s)
                        isLast -> s.tutorialFinish
                        else -> s.tutorialNext
                    },
                backLabel = s.tutorialBack,
                canGoBack = step > 0,
                onBack = { if (step > 0) step -= 1 },
                onPrimary = {
                    when {
                        mechanic != null && !mechanicActed -> mechanicActed = true
                        isLast -> onDone()
                        else -> step += 1
                    }
                },
            )
        }
    }
}

/** The primary-CTA label for a mechanic beat's un-acted (prompting) state. */
private fun mechanicDoLabel(
    mechanic: Mechanic,
    s: com.kursi.shared.strings.KursiStrings,
): String =
    when (mechanic) {
        Mechanic.BLOCK -> s.tutorialDoBlock
        Mechanic.COUP -> s.tutorialDoCoup
        Mechanic.EXCHANGE -> s.tutorialDoExchange
    }

/** The scripted rival who delivers the guaranteed challenge. One source of truth for the name. */
private const val ScriptedChallengerName = "Babu Filewala"

// ─────────────────────────── First-run offer dialog ───────────────────────────

/**
 * The one-time post-primer offer: "Take your first day?" Shown over Home on the first landing after
 * the swearing-in primer. Accept routes to [TutorialScreen]; decline dismisses. Either choice marks
 * the offer seen so it never nags again.
 */
@Composable
fun TutorialOfferDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val s = LocalKursiStrings.current
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
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 380.dp)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged, BrandTokens.BrassDark)))
                    .border(
                        2.dp,
                        Brush.sweepGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark, BrandTokens.GoldAntique)),
                        RoundedCornerShape(18.dp),
                    ).padding(2.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BrandTokens.PaperCream)
                    .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Seal mark
            Box(
                modifier =
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark)))
                        .border(1.5.dp, BrandTokens.BrassAged, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("✦", style = KursiType.title.copy(fontSize = 20.sp), color = BrandTokens.TeakDark)
            }
            Text(
                s.tutorialOfferTitle,
                style = KursiType.title_md.copy(fontSize = 18.sp),
                color = BrandTokens.CreamInk,
                textAlign = TextAlign.Center,
            )
            Text(
                s.tutorialOfferBody,
                style = KursiType.body.copy(fontSize = 13.sp),
                color = BrandTokens.CreamInk.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            // Accept (hero) — stamp
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.horizontalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged)))
                        .border(1.dp, BrandTokens.BrassDark, RoundedCornerShape(10.dp))
                        .clickable(onClick = onAccept)
                        .semantics {
                            role = androidx.compose.ui.semantics.Role.Button
                            contentDescription = s.tutorialOfferAccept
                        }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.tutorialOfferAccept,
                    style = KursiType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = BrandTokens.TeakDark,
                )
            }
            Text(
                s.tutorialOfferDecline,
                style = KursiType.label.copy(fontSize = 11.sp),
                color = BrandTokens.BrassDark.copy(alpha = 0.8f),
                modifier =
                    Modifier
                        .clickable(onClick = onDecline)
                        .semantics {
                            role = androidx.compose.ui.semantics.Role.Button
                            contentDescription = s.tutorialOfferDecline
                        },
            )
        }
    }
}

// ─────────────────────────── Header ───────────────────────────────────────────

@Composable
private fun TutorialHeader(
    header: String,
    badge: String,
    stepLabel: String,
    step: Int,
    total: Int,
    onSkip: () -> Unit,
    skipLabel: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(header, style = KursiType.title.copy(fontSize = 15.sp, letterSpacing = 1.sp), color = KursiNeutrals.TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(BrandTokens.StampRed.copy(alpha = 0.12f))
                                .border(0.7.dp, BrandTokens.StampRed.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            badge,
                            style = KursiType.caption.copy(fontSize = 9.sp, letterSpacing = 0.6.sp),
                            color = BrandTokens.StampRed.copy(alpha = 0.8f),
                        )
                    }
                    Text("$stepLabel ${step + 1}/$total", style = KursiType.caption.copy(fontSize = 9.sp), color = KursiNeutrals.TextMuted)
                }
            }
            Text(
                text = skipLabel,
                style = KursiType.caption.copy(fontSize = 11.sp),
                color = BrandTokens.BrassAged,
                modifier =
                    Modifier
                        .clickable(onClick = onSkip)
                        .semantics {
                            role = androidx.compose.ui.semantics.Role.Button
                            contentDescription = skipLabel
                        },
            )
        }
        // Engraved chrome — a hairline gold rule, not a filled/bordered bar (non-negotiable #3).
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, BrandTokens.GoldAntique.copy(alpha = 0.4f), Color.Transparent),
                        ),
                    ),
        )
    }
}

// ─────────────────────────── Coach chit (narrator) ────────────────────────────

@Composable
private fun CoachChit(
    coachTag: String,
    title: String,
    body: String,
    step: Int,
    total: Int,
    primaryLabel: String,
    backLabel: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged, BrandTokens.BrassDark)),
                    ).border(
                        2.dp,
                        Brush.sweepGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark, BrandTokens.GoldAntique)),
                        RoundedCornerShape(16.dp),
                    ).padding(2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BrandTokens.PaperCream)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Narrator nameplate + step dots
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BrandTokens.BrassDark.copy(alpha = 0.18f))
                            .border(0.8.dp, BrandTokens.BrassDark, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("▸ $coachTag", style = KursiType.label.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = BrandTokens.BrassDark)
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(total) { idx ->
                        Box(
                            modifier =
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (idx <= step) BrandTokens.BrassDark else BrandTokens.BrassDark.copy(alpha = 0.2f)),
                        )
                    }
                }
            }

            Text(
                text = title,
                style = KursiType.title_md.copy(fontSize = 18.sp),
                color = BrandTokens.CreamInk,
            )
            // Live region so screen readers announce each new teaching beat.
            Text(
                text = body,
                style = KursiType.body.copy(fontSize = 13.sp),
                color = BrandTokens.CreamInk.copy(alpha = 0.86f),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            Spacer(Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canGoBack) {
                    Box(
                        modifier =
                            Modifier
                                .defaultMinSize(minWidth = 64.dp, minHeight = 52.dp)
                                .semantics(mergeDescendants = true) {
                                    role = androidx.compose.ui.semantics.Role.Button
                                    contentDescription = backLabel
                                }.clickable(onClick = onBack)
                                .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = backLabel,
                            style = KursiType.label.copy(fontSize = 11.sp),
                            color = BrandTokens.BrassDark.copy(alpha = 0.8f),
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.horizontalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged)))
                            .border(1.dp, BrandTokens.BrassDark, RoundedCornerShape(8.dp))
                            .clickable(onClick = onPrimary)
                            .semantics {
                                role = androidx.compose.ui.semantics.Role.Button
                                contentDescription = primaryLabel
                            }.padding(horizontal = 18.dp, vertical = 9.dp),
                ) {
                    Text(
                        primaryLabel,
                        style = KursiType.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = BrandTokens.TeakDark,
                    )
                }
            }
        }
    }
}

// ─────────────────────────── Scripted table backdrop ──────────────────────────

/**
 * A static-but-reactive teaching table: one human seat (you), three rivals, your two hand cards,
 * a coin tally, and a single action dock. It mutates with the beat — pulsing the GHOTALA chip when
 * the learner is prompted to act, showing the challenge banner once challenged, and flipping the
 * bluffed card face-up to reveal JHOOTH once the bluff is caught.
 */
@Composable
private fun ScriptedTable(
    challenged: Boolean,
    revealed: Boolean,
    promptingAction: Boolean,
    challengerName: String,
) {
    val s = LocalKursiStrings.current
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .drawBehind { drawFeltGuilloche() },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 84.dp, start = 24.dp, end = 24.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Rivals row — the scripted challenger is highlighted once they challenge.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RivalPlate(
                    name = challengerName,
                    monogram = "BF",
                    hue = KursiRoleHues.Babu,
                    active = challenged,
                    modifier = Modifier.weight(1f),
                )
                RivalPlate(
                    name = stringResource(Res.string.tutorial_persona_netaji_vachan),
                    monogram = "NV",
                    hue = KursiRoleHues.Neta,
                    active = false,
                    modifier = Modifier.weight(1f),
                )
                RivalPlate(
                    name = stringResource(Res.string.tutorial_persona_vakil_loophole),
                    monogram = "VL",
                    hue = KursiRoleHues.Vakil,
                    active = false,
                    modifier = Modifier.weight(1f),
                )
            }

            // Challenge banner — drops in when Babu challenges.
            AnimatedVisibility(visible = challenged, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut()) {
                ChallengeBanner(text = "$challengerName: ${if (revealed) RevealVerdictLine else ChallengeLine}", revealed = revealed)
            }

            Spacer(Modifier.weight(1f))

            // YOUR seat: two hand cards (one is the bluffed NETA claim → flips to reveal JHOOTH).
            HandRow(revealed = revealed)

            // Coin tally
            CoinTally(coins = 5)

            // Action dock — the GHOTALA chip pulses when prompting the learner to act.
            val (ghotalaLabel, ghotalaSub) = stringResource(Res.string.tutorial_action_ghotala).split(" / ", limit = 2)
            ActionDock(
                label = ghotalaLabel,
                sub = ghotalaSub,
                pulse = promptingAction,
                spent = challenged, // once acted, the dock is dimmed (the move was made)
            )
        }
    }
}

@Composable
private fun RivalPlate(
    name: String,
    monogram: String,
    hue: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    /** True once this rival has lost an influence in the scripted scene (the COUP beat) — shows one
     *  pip instead of two. */
    pipsLost: Boolean = false,
) {
    // AAA polish: no bordered seat card — the rival rests directly on the ground; a soft circular
    // glow behind the token (not a rectangular box) marks the active/challenging seat (non-negotiable #1).
    Column(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (active) {
                Box(
                    modifier =
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(hue.copy(alpha = 0.38f), Color.Transparent))),
                )
            }
            BrassToken(monogram = monogram, fill = hue, size = 36.dp)
        }
        Text(name, style = KursiType.name.copy(fontSize = 11.sp), color = KursiNeutrals.TextPrimary, maxLines = 1)
        // Face-down influence pips — one fewer once this rival has lost an influence (COUP beat).
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(if (pipsLost) 1 else 2) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 14.dp, height = 18.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(BrandTokens.TeakInk)
                            .border(0.8.dp, BrandTokens.BrassDark.copy(alpha = 0.6f), RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

/** The "what's-happening" headline — plain italic text on the ground, matching the game board's
 *  narration line (e.g. "Netaji Vachan ne Supari ka elaan kiya."), not a bordered banner box
 *  (non-negotiable #1). Shifts to the oxblood accent once a bluff/verdict lands (non-negotiable #7). */
@Composable
private fun ChallengeBanner(
    text: String,
    revealed: Boolean,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KursiType.body.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic),
            color = if (revealed) BrandTokens.StampRed else BrandTokens.GoldAntique,
            textAlign = TextAlign.Center,
        )
    }
}

/** The scripted challenge the rival delivers in the guided beat. */
private const val ChallengeLine = "\"You hold NETA? Show me.\""

/** The scripted verdict shown once the bluffed card flips face-up. */
private const val RevealVerdictLine = "JHOOTH! No NETA — the bluff is caught."

/** Your two cards: a hidden card + the bluffed NETA card that flips face-up to JHOOTH when caught. */
@Composable
private fun HandRow(revealed: Boolean) {
    val flip = remember { Animatable(0f) }
    LaunchedEffect(revealed) {
        flip.animateTo(if (revealed) 1f else 0f, animationSpec = tween(420, easing = FastOutSlowInEasing))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        // Card 1 — your genuine, hidden influence (stays face-down).
        HandCard(faceUp = false, role = Role.BABU, caught = false)
        // Card 2 — the BLUFFED claim. Face-down until the challenge; flips to reveal it was BHAI,
        // NOT the NETA you claimed → bluff caught, this card is the one you lose. The displayed FACE
        // is driven by [revealed] directly (so a single static render is always correct); the
        // Animatable only animates the live flip rotation between the two faces.
        // A subtle scale "tip" stands in for the flip without mirroring the face text: at mid-flip
        // the card narrows, then the revealed face swaps in. (A true rotationY flip would mirror the
        // glyph/label, so we drive the FACE off [revealed] and use the Animatable for a width pinch.)
        val pinch = 1f - 0.18f * (1f - kotlin.math.abs(flip.value - 0.5f) * 2f)
        Box(modifier = Modifier.graphicsLayer { scaleX = pinch }) {
            HandCard(faceUp = revealed, role = Role.BHAI, caught = revealed)
        }
    }
}

@Composable
private fun HandCard(
    faceUp: Boolean,
    role: Role,
    caught: Boolean,
) {
    val hue = roleHue(role)
    Box(
        modifier =
            Modifier
                .size(width = 78.dp, height = 104.dp)
                .shadow(5.dp, RoundedCornerShape(10.dp), clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                .clip(RoundedCornerShape(10.dp))
                .background(if (faceUp) BrandTokens.PaperCream else BrandTokens.TeakDark)
                .border(
                    if (caught) 2.dp else 1.2.dp,
                    if (caught) BrandTokens.StampRed else BrandTokens.BrassAged.copy(alpha = 0.7f),
                    RoundedCornerShape(10.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (faceUp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RoleGlyph(role = role, modifier = Modifier.size(40.dp), tint = hue)
                Text(roleName(role), style = KursiType.label.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = BrandTokens.CreamInk)
                if (caught) {
                    Box(
                        modifier =
                            Modifier
                                .rotate(-8f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(BrandTokens.StampRed.copy(alpha = 0.9f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            stringResource(Res.string.label_jhooth_stamp),
                            style = KursiType.caption.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = KursiNeutrals.Cream,
                        )
                    }
                }
            }
        } else {
            // Face-down crest
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(BrandTokens.BrassAged.copy(alpha = 0.4f), BrandTokens.BrassDark.copy(alpha = 0.6f)),
                            ),
                        ).border(1.dp, BrandTokens.BrassDark, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("✦", style = KursiType.title.copy(fontSize = 16.sp), color = BrandTokens.GoldAntique.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun CoinTally(coins: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier =
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark)))
                    .border(1.dp, BrandTokens.BrassAged, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("₹", style = KursiType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = BrandTokens.TeakDark)
        }
        Text(
            stringResource(Res.string.tutorial_coin_tally, coins),
            style = KursiType.label.copy(fontSize = 12.sp, letterSpacing = 1.sp),
            color = BrandTokens.GoldAntique,
        )
    }
}

@Composable
private fun ActionDock(
    label: String,
    sub: String,
    pulse: Boolean,
    spent: Boolean,
    /** The role glyph shown on the highlighted chip; null for a claim-free move (KHELA has no claim). */
    iconRole: Role? = Role.NETA,
    iconTint: Color = KursiRoleHues.Neta,
) {
    val infinite = rememberInfiniteTransition(label = "dockPulse")
    val glow by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (pulse) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The highlighted chip — the move the learner performs this beat. A raised stamp (non-negotiable
        // #4): real cast shadow when live, flat once dimmed/spent.
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .then(
                        if (!spent) {
                            Modifier.shadow(
                                if (pulse) 5.dp else 3.dp,
                                RoundedCornerShape(10.dp),
                                clip = false,
                                ambientColor = Color.Black,
                                spotColor = BrandTokens.TeakInk,
                            )
                        } else {
                            Modifier
                        },
                    ).clip(RoundedCornerShape(10.dp))
                    .background(
                        if (pulse) BrandTokens.GoldAntique.copy(alpha = 0.20f + glow * 0.18f) else BrandTokens.TeakDark.copy(alpha = 0.55f),
                    ).border(
                        if (pulse) 2.dp else 1.dp,
                        if (pulse) BrandTokens.GoldAntique.copy(alpha = 0.7f + glow * 0.3f) else BrandTokens.BrassDark.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp),
                    ).alpha(if (spent) 0.5f else 1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (iconRole != null) {
                    RoleGlyph(role = iconRole, modifier = Modifier.size(26.dp), tint = iconTint)
                } else {
                    // KHELA claims no role — a plain stamp mark stands in for the missing glyph.
                    Text("✦", style = KursiType.title.copy(fontSize = 20.sp), color = BrandTokens.GoldAntique)
                }
                Column {
                    Text(label, style = KursiType.name.copy(fontSize = 13.sp), color = KursiNeutrals.TextPrimary)
                    Text(sub, style = KursiType.caption.copy(fontSize = 9.sp), color = KursiNeutrals.TextMuted)
                }
            }
        }
        // Two dimmed sibling chips (context — there are always more moves).
        val (dehaadiLabel, fdiLabel) = stringResource(Res.string.label_move_dehaadi_fdi).split(" / ", limit = 2)
        repeat(2) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandTokens.TeakDark.copy(alpha = 0.4f))
                        .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .alpha(0.55f)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    if (it ==
                        0
                    ) {
                        dehaadiLabel
                    } else {
                        fdiLabel
                    },
                    style = KursiType.name.copy(fontSize = 12.sp),
                    color = KursiNeutrals.TextSecondary,
                )
            }
        }
    }
}

// ─────────────────────── BLOCK / COUP / EXCHANGE scripted scenes ──────────────────────

/**
 * The scripted table for the three post-claim mechanic beats (spec §6). One compact scene reused for
 * all three — [mechanic] picks which rival is highlighted, which chip pulses, and which banner line
 * shows — so a tap resolves the move in place (ponytail: a full 4-drawn-card exchange UI is out of
 * scope; the banner line carries that concept, matching the existing challenge/reveal banner idiom).
 */
@Composable
private fun MechanicTable(
    mechanic: Mechanic,
    acted: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize().drawBehind { drawFeltGuilloche() }) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 84.dp, start = 24.dp, end = 24.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RivalPlate(
                    name = "Babu Filewala",
                    monogram = "BF",
                    hue = KursiRoleHues.Babu,
                    active = false,
                    modifier = Modifier.weight(1f),
                )
                RivalPlate(
                    name = "Netaji Vachan",
                    monogram = "NV",
                    hue = KursiRoleHues.Neta,
                    active = mechanic == Mechanic.BLOCK,
                    modifier = Modifier.weight(1f),
                )
                RivalPlate(
                    name = "Vakil Loophole",
                    monogram = "VL",
                    hue = KursiRoleHues.Vakil,
                    active = mechanic == Mechanic.COUP,
                    pipsLost = mechanic == Mechanic.COUP && acted,
                    modifier = Modifier.weight(1f),
                )
            }

            AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn(initialScale = 0.9f)) {
                ChallengeBanner(text = mechanicLine(mechanic, acted), revealed = acted)
            }

            Spacer(Modifier.weight(1f))

            // Your hand never reveals in a successful block, a coup, or an exchange — it stays face-down
            // throughout all three beats.
            HandRow(revealed = false)

            CoinTally(coins = if (mechanic == Mechanic.COUP) 7 else 5)

            when (mechanic) {
                Mechanic.BLOCK -> {
                    val (vakilLabel, vakilSub) = stringResource(Res.string.tutorial_action_vakil).split(" / ", limit = 2)
                    ActionDock(
                        label = vakilLabel,
                        sub = vakilSub,
                        pulse = !acted,
                        spent = acted,
                        iconRole = Role.VAKIL,
                        iconTint = KursiRoleHues.Vakil,
                    )
                }
                Mechanic.COUP -> {
                    val (khelaLabel, khelaSub) = stringResource(Res.string.tutorial_action_khela).split(" / ", limit = 2)
                    ActionDock(
                        label = khelaLabel,
                        sub = khelaSub,
                        pulse = !acted,
                        spent = acted,
                        iconRole = null,
                    )
                }
                Mechanic.EXCHANGE -> {
                    val (settingLabel, settingSub) = stringResource(Res.string.tutorial_action_setting).split(" / ", limit = 2)
                    ActionDock(
                        label = settingLabel,
                        sub = settingSub,
                        pulse = !acted,
                        spent = acted,
                        iconRole = Role.JUGAADU,
                        iconTint = KursiRoleHues.Jugaadu,
                    )
                }
            }
        }
    }
}

/** Before/after flavor line for a [MechanicTable] beat. Hardcoded (not localized) like the existing
 *  [ChallengeLine]/[RevealVerdictLine] scripted banners this mirrors. */
private fun mechanicLine(
    mechanic: Mechanic,
    acted: Boolean,
): String =
    when (mechanic) {
        Mechanic.BLOCK ->
            if (acted) {
                "VAKIL ROKA! Supari fail — koi nuksaan nahi."
            } else {
                "Netaji Vachan: \"SUPARI — ek influence tumhara jayega.\""
            }
        Mechanic.COUP ->
            if (acted) {
                "KHELA laga! Vakil Loophole ek influence khoyi."
            } else {
                "7 khokha jama — KHELA ab khareeda ja sakta hai."
            }
        Mechanic.EXCHANGE ->
            if (acted) {
                "2 card khinche, best do rakhe — baaki laut diye."
            } else {
                "SETTING se apna haath taaza kijiye."
            }
    }

// ─────────────────────────── Helpers ──────────────────────────────────────────

private fun roleHue(role: Role): Color =
    when (role) {
        Role.NETA -> KursiRoleHues.Neta
        Role.BHAI -> KursiRoleHues.Bhai
        Role.BABU -> KursiRoleHues.Babu
        Role.JUGAADU -> KursiRoleHues.Jugaadu
        Role.VAKIL -> KursiRoleHues.Vakil
        Role.PATRAKAAR -> KursiRoleHues.Patrakaar
    }

private fun roleName(role: Role): String =
    when (role) {
        Role.NETA -> "NETA"
        Role.BHAI -> "BHAI"
        Role.BABU -> "BABU"
        Role.JUGAADU -> "JUGAADU"
        Role.VAKIL -> "VAKIL"
        Role.PATRAKAAR -> "PATRAKAAR"
    }

/** A faint guilloche felt texture behind the scripted table (reuses the Home depth motif idiom). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFeltGuilloche() {
    val cx = size.width * 0.5f
    val cy = size.height * 0.42f
    val baseR = minOf(size.width, size.height) * 0.5f
    listOf(0.05f, 0.035f, 0.025f).forEachIndexed { i, a ->
        drawCircle(
            color = BrandTokens.BrassAged.copy(alpha = a),
            radius = baseR * (1f - i * 0.18f),
            center =
                androidx.compose.ui.geometry
                    .Offset(cx, cy),
            style = Stroke(width = 1f),
        )
    }
    val rays = 36
    for (i in 0 until rays) {
        val angle = (i * 2.0 * PI / rays).toFloat()
        drawLine(
            color = BrandTokens.BrassAged.copy(alpha = 0.04f),
            start =
                androidx.compose.ui.geometry
                    .Offset(cx + baseR * 0.85f * cos(angle), cy + baseR * 0.85f * sin(angle)),
            end =
                androidx.compose.ui.geometry
                    .Offset(cx + baseR * cos(angle), cy + baseR * sin(angle)),
            strokeWidth = 0.8f,
        )
    }
}
