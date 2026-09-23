package com.kursi.shared.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.ai.persona.BarkEvent
import com.kursi.ai.persona.BotDifficulty
import com.kursi.ai.persona.BotPersona
import com.kursi.ai.persona.PersonaAssigner
import com.kursi.designsystem.*
import com.kursi.designsystem.moment.ActionMomentOverlay
import com.kursi.designsystem.moment.GaddiMoment
import com.kursi.designsystem.moment.TableAnchors
import com.kursi.designsystem.moment.rememberMomentHost
import com.kursi.feature.game.Difficulty
import com.kursi.shared.strings.LocalGaddiStrings
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.game_you_monogram
import kursi.core.designsystem.generated.resources.lobby_bark_attribution
import kursi.core.designsystem.generated.resources.lobby_cabinet_eyebrow
import kursi.core.designsystem.generated.resources.lobby_tap_hint
import kursi.core.designsystem.generated.resources.lobby_voice_hint
import org.jetbrains.compose.resources.stringResource

/**
 * LobbyScreen — Hazri Register (S3) from 17_app_plan.md §4.
 *
 * Shows the deterministic roster of bots for the given seed + difficulty.
 * "DOBARA BULAO" re-rolls (seed+1).
 * "MUHAR LAGAO" commits → Game.
 */
@Composable
fun LobbyScreen(
    seed: Long,
    players: Int,
    difficulty: Difficulty,
    onBack: () -> Unit,
    onDealIn: (seed: Long, players: Int, difficulty: Difficulty) -> Unit,
    modifier: Modifier = Modifier,
    /** M5 pass-and-play: number of hot-seat humans (1 = vs-AI). Bots fill players - humanCount. */
    humanCount: Int = 1,
    /** M6e TEAM KHEL: number of teams (last-team-standing). < 2 = classic free-for-all. */
    teamCount: Int = 0,
) {
    var currentSeed by remember { mutableLongStateOf(seed) }
    val botDifficulty = remember(difficulty) { difficulty.toBotDifficulty() }
    val botCount = (players - humanCount).coerceAtLeast(0)

    val roster =
        remember(currentSeed, botCount, botDifficulty) {
            if (botCount <= 0) {
                emptyList()
            } else {
                PersonaAssigner.assign(
                    seatCount = botCount.coerceIn(1, 9),
                    difficulty = botDifficulty,
                    seed = currentSeed,
                )
            }
        }

    val scrollState = rememberScrollState()

    // ── MUHAR LAGAO stamp moment ──────────────────────────────────────────────
    // Pressing commit plays a centred RubberStamp moment over the register, then
    // navigates into the Game once the moment finishes (host goes idle).
    val momentHost = rememberMomentHost()
    var committing by remember { mutableStateOf(false) }
    // Anchors are wired from BoxWithConstraints below; initialised to a safe default.
    var tableAnchors by remember { mutableStateOf(centeredAnchors(1080f, 1920f)) }

    LaunchedEffect(committing, momentHost.isActive) {
        if (committing && !momentHost.isActive) {
            committing = false
            onDealIn(currentSeed, players, difficulty)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().litGround()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        LaunchedEffect(screenWidthPx, screenHeightPx) {
            tableAnchors = centeredAnchors(screenWidthPx, screenHeightPx)
        }
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header
            LobbyHeader(onBack = onBack)

            // Attendance register — centred on both axes so the register reads as a bound
            // ledger page floating on the teak desk, instead of stretching edge-to-edge on
            // wide windows or top-anchoring with a dead band above the footer. The outer
            // column scrolls (content may exceed the viewport at 9 bots); when the roster
            // fits, Arrangement.Center settles the ledger in the optical middle of the canvas.
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                ) {
                    val ls = LocalGaddiStrings.current
                    // Register title — engraved chrome (eyebrow + hairline), not a filled bordered
                    // bar. A sparing Rozha title carries the one focal point at the top of the list.
                    val cabinetNo = (currentSeed % 9999).let { if (it < 0) it + 9999 else it }
                    EngravedHeader(
                        eyebrow = stringResource(Res.string.lobby_cabinet_eyebrow, cabinetNo, players, difficulty.name),
                        title = ls.lobbyHeader,
                    ) {
                        Text(
                            text = stringResource(Res.string.lobby_tap_hint),
                            style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                            color = GaddiNeutrals.TextSecondary,
                        )
                    }
                    Spacer(Modifier.height(6.dp))

                    // M6e TEAM KHEL — alternating seat→team split (seat i → team i % teamCount). The human (seat
                    // 0) anchors team 0; bots fill seats humanCount.. so their seat index is humanCount + i.
                    val teams = teamCount >= 2
                    if (teams) {
                        Text(
                            text = ls.lobbyTeamHeader,
                            style = GaddiType.label.copy(fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                            color = BrandTokens.GoldAntique,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    // Human seat (fixed, seat 0)
                    PersonaRegisterRow(
                        name = ls.humanSeatName,
                        title = ls.humanSeatTitle,
                        archetype = ls.humanSeatArchetype,
                        monogram = stringResource(Res.string.game_you_monogram),
                        seatColor = Color(0xFFE63946),
                        personalityLine = ls.humanSeatPersonality,
                        isHuman = true,
                        sampleBark = ls.humanSeatBark,
                        teamBadge = if (teams) ls.teamBadge(0 % teamCount) else null,
                        teamId = if (teams) 0 % teamCount else -1,
                    )

                    // Bot seats (seat = humanCount + index)
                    roster.forEachIndexed { index, (persona, _) ->
                        val personalityLine = buildPersonalityLine(persona)
                        val sampleBark = persona.barks.lines[BarkEvent.ACT]?.firstOrNull() ?: ""
                        val seatIdx = humanCount + index
                        val tId = if (teams) seatIdx % teamCount else -1
                        PersonaRegisterRow(
                            name = persona.name,
                            title = persona.title,
                            archetype = persona.archetype,
                            monogram = persona.monogram,
                            seatColor = Color(persona.seatColorArgb),
                            personalityLine = personalityLine,
                            sampleBark = sampleBark,
                            teamBadge = if (teams) ls.teamBadge(tId) else null,
                            teamId = tId,
                        )
                    }
                }
            }

            // Footer with re-roll + commit — a hairline rule (not a filled/bordered bar) lifts
            // the footer off the register above it; the two stamps rest directly on the ground.
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .widthIn(max = 860.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, BrandTokens.BrassDark.copy(alpha = 0.5f), Color.Transparent),
                                ),
                            ),
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val lb = LocalGaddiStrings.current
                    // Reroll — a raised secondary stamp, not a flat bordered box.
                    StampButton(
                        label = lb.lobbyReroll,
                        style = StampStyle.Secondary,
                        onClick = { currentSeed += 1L },
                        modifier = Modifier.weight(1f),
                    )

                    // Deal In commit
                    StampChit(
                        label = lb.lobbyDealIn,
                        sublabel = lb.lobbyDealInSub,
                        isHero = true,
                        onClick = {
                            if (!committing) {
                                committing = true
                                momentHost.play(
                                    GaddiMoment.Tax(actorSeat = 0, roleHue = BrandTokens.GoldAntique),
                                )
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                    )
                }
            }
        }

        // ── Stamp moment overlay — above the register, plays MUHAR LAGAO ─────────
        ActionMomentOverlay(
            host = momentHost,
            anchors = tableAnchors,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Anchors that drive every moment to the screen centre — no live table to measure here.
 * [screenWidthPx] and [screenHeightPx] come from BoxWithConstraints so the stamp lands
 * on-screen even on portrait phones (the old hardcoded 720×450 was off-screen on ~360dp
 * wide handsets at 3× density).
 */
private fun centeredAnchors(
    screenWidthPx: Float,
    screenHeightPx: Float,
): TableAnchors {
    val cx = screenWidthPx / 2f
    val cy = screenHeightPx * 0.42f
    return TableAnchors(
        seatCenters = mapOf(0 to Offset(cx, cy)),
        treasuryCenter = Offset(cx, cy),
    )
}

// ─────────────────────────── Helpers ─────────────────────────────────────────

private fun Difficulty.toBotDifficulty(): BotDifficulty =
    when (this) {
        Difficulty.Easy -> BotDifficulty.EASY
        Difficulty.Medium -> BotDifficulty.MEDIUM
        Difficulty.Hard -> BotDifficulty.HARD
        Difficulty.Expert -> BotDifficulty.EXPERT
        Difficulty.Grandmaster -> BotDifficulty.GRANDMASTER
    }

private fun buildPersonalityLine(persona: BotPersona): String {
    val p = persona.personality
    val bluffStr = "bluffs ${(p.bluffRate * 100).toInt()}%"
    val challengeStr =
        when {
            p.challengeAggression > 0.6f -> "challenge-hungry"
            p.challengeAggression < 0.3f -> "lets things slide"
            else -> "moderate challenger"
        }
    val targeting =
        when (p.targetingBias.name) {
            "LEADER" -> "aankh leader pe"
            "WEAKEST" -> "targets weakest"
            "VINDICTIVE" -> "yaad rakhta hai"
            "RANDOM" -> "unpredictable"
            else -> p.targetingBias.name.lowercase()
        }
    return "$bluffStr · $challengeStr · $targeting"
}

// ─────────────────────────── Register Row ─────────────────────────────────────

@Composable
private fun PersonaRegisterRow(
    name: String,
    title: String,
    archetype: String,
    monogram: String,
    seatColor: Color,
    personalityLine: String,
    isHuman: Boolean = false,
    sampleBark: String = "",
    /** M6e TEAM KHEL: faction label + id for the seat, or null in classic free-for-all. */
    teamBadge: String? = null,
    teamId: Int = -1,
) {
    var flipped by remember { mutableStateOf(false) }

    // AAA polish: a row resting on the ground with a hairline rule below — no bordered card.
    // The human seat's only distinction from a bot row is warmer ink on its name, matching
    // "one accent" (non-negotiable #7) rather than a filled/bordered highlight box.
    HairlineRow(onClick = { flipped = !flipped }, verticalPadding = 12.dp) {
        if (flipped && sampleBark.isNotEmpty()) {
            // Flipped — show sample bark
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "\"$sampleBark\"",
                    style = GaddiType.body.copy(fontStyle = FontStyle.Italic, fontSize = 14.sp),
                    color = GaddiNeutrals.TextPrimary,
                )
                Text(
                    stringResource(Res.string.lobby_bark_attribution, name),
                    style = GaddiType.caption.copy(fontSize = 10.sp),
                    color = GaddiNeutrals.TextMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            BrassToken(monogram = monogram, fill = seatColor, size = 44.dp)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name,
                        style = GaddiType.name.copy(fontSize = 14.sp),
                        color = if (isHuman) BrandTokens.GoldAntique else GaddiNeutrals.TextPrimary,
                        fontWeight = if (isHuman) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (teamBadge != null) {
                        val teamHue = if (teamId == 0) BrandTokens.GoldAntique else BrandTokens.StampRed
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(teamHue.copy(alpha = 0.16f))
                                    .border(0.7.dp, teamHue.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                teamBadge,
                                style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold),
                                color = teamHue,
                            )
                        }
                    }
                }
                Text(
                    "$title  ·  $archetype",
                    style = GaddiType.body.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                    color = GaddiNeutrals.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    personalityLine,
                    style = GaddiType.caption.copy(fontSize = 9.sp),
                    color = GaddiNeutrals.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = stringResource(Res.string.lobby_voice_hint),
                style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
                color = BrandTokens.BrassAged.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun LobbyHeader(onBack: () -> Unit) {
    val s = LocalGaddiStrings.current
    // Short nav breadcrumb — the full engraved title (with subtitle) is the register's own
    // focal EngravedHeader below; repeating it here would be a second, competing focal point.
    EngravedNavHeader(
        title = s.lobbyHeader.substringBefore("—").trim(),
        onBack = onBack,
        backLabel = s.back,
        modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
    )
}
