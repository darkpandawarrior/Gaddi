package com.kursi.shared.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.feature.game.Difficulty
import com.kursi.feature.game.narrative.ArcId
import com.kursi.shared.strings.LocalGaddiStrings
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.label_selected_stamp
import kursi.core.designsystem.generated.resources.story_arc_section_header
import kursi.core.designsystem.generated.resources.story_arc_section_sub
import kursi.core.designsystem.generated.resources.story_badge
import kursi.core.designsystem.generated.resources.story_difficulty_header
import kursi.core.designsystem.generated.resources.story_difficulty_sub
import kursi.core.designsystem.generated.resources.story_explainer_body
import kursi.core.designsystem.generated.resources.story_player_count_header
import kursi.core.designsystem.generated.resources.story_player_count_sub
import kursi.core.designsystem.generated.resources.story_start_sublabel
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

// ── Arc metadata ──────────────────────────────────────────────────────────────

private data class ArcMeta(
    val arcId: ArcId?, // null = Free Darbar
    val code: String, // ArcId.name or ""
    val title: String,
    val subtitle: String,
    val blurb: String,
    val glyph: String,
)

private val ARC_METAS =
    listOf(
        ArcMeta(
            arcId = null,
            code = "",
            title = "FREE DARBAR",
            subtitle = "Koi agenda nahi — sab apni-apni chalenge",
            blurb = "No lead arc. Bots will conspire freely. The table decides its own story.",
            glyph = "⊙",
        ),
        ArcMeta(
            arcId = ArcId.GATHBANDHAN,
            code = ArcId.GATHBANDHAN.name,
            title = "GATHBANDHAN",
            subtitle = "Coalition / Milkaa Hua Daftar",
            blurb = "Rivals will whisper alliances. A coalition forms and fractures. You can join — or play both sides.",
            glyph = "🤝",
        ),
        ArcMeta(
            arcId = ArcId.AFWAAH,
            code = ArcId.AFWAAH.name,
            title = "AFWAAH",
            subtitle = "Rumour / Suni-Sunaayi Baat",
            blurb = "Dangerous gossip spreads seat to seat. A target gets painted. Who started the rumour? You choose.",
            glyph = "📣",
        ),
        ArcMeta(
            arcId = ArcId.STING,
            code = ArcId.STING.name,
            title = "PHASAAO",
            subtitle = "Honeytrap / Sting Operation",
            blurb =
                "Someone is being set up. Flattery, false friendship, a trap laid in plain sight. You can pull the strings — or cut " +
                    "them.",
            glyph = "🪤",
        ),
        ArcMeta(
            arcId = ArcId.BADLA,
            code = ArcId.BADLA.name,
            title = "BADLA",
            subtitle = "Vendetta / Hisaab Barabar",
            blurb = "Old grudges surface. One bot has a score to settle and does not hide it. Join the vendetta or shield the target.",
            glyph = "⚔",
        ),
    )

/**
 * StoryScreen — KISSA hub in the Sarkari Noir identity.
 *
 * Briefly explains narrative mode (bots chat, conspire, can be manipulated; four
 * lead arcs available). Shows arc selection rows + Free Darbar, a player-count
 * stepper, a difficulty quick-pick, and a start CTA — all resting on the same lit
 * ground as Home/Setup/Lobby (design-language.md non-negotiables #1-#4).
 *
 * @param onBack       navigate back to Home.
 * @param onStart      (seed, players, difficulty, arcCode) → start a narrative game.
 */
@Composable
fun StoryScreen(
    onBack: () -> Unit,
    onStart: (seed: Long, players: Int, difficulty: Difficulty, arc: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalGaddiStrings.current
    val scroll = rememberScrollState()

    var selectedArcCode by remember { mutableStateOf("") }
    var playerCount by remember { mutableIntStateOf(4) }
    var difficulty by remember { mutableStateOf(Difficulty.Medium) }

    val difficultyMetas =
        listOf(
            Triple(Difficulty.Easy, s.diffEasyName, "NAYA BHARTI"),
            Triple(Difficulty.Medium, s.diffMediumName, "PERMANENT BABU"),
            Triple(Difficulty.Hard, s.diffHardName, "SECTION OFFICER"),
            Triple(Difficulty.Expert, s.diffExpertName, "HEAD CLERK SAAB"),
            Triple(Difficulty.Grandmaster, s.diffGrandmasterName, "CABINET SECRETARY"),
        )

    Column(modifier = modifier.fillMaxSize().litGround()) {
        // ── Header — engraved chrome, not a filled bordered bar ─────────────────
        EngravedNavHeader(
            title = s.storyTitle,
            onBack = onBack,
            backLabel = s.back,
            modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
            trailing = {
                Text(
                    stringResource(Res.string.story_badge),
                    style = GaddiType.caption.copy(fontSize = 9.sp),
                    color = GaddiNeutrals.TextMuted,
                )
            },
        )

        // ── Scrollable body ───────────────────────────────────────────────────
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── Tagline / explainer — plain on the ground, no bordered panel ──
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = s.storyTagline,
                        style = GaddiType.body.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic),
                        color = BrandTokens.GoldAntique,
                    )
                    Text(
                        text = stringResource(Res.string.story_explainer_body),
                        style = GaddiType.body.copy(fontSize = 12.sp),
                        color = GaddiNeutrals.TextSecondary,
                    )
                }

                // ── Arc section ───────────────────────────────────────────────
                EngravedHeader(eyebrow = stringResource(Res.string.story_arc_section_header)) {
                    Text(
                        text = stringResource(Res.string.story_arc_section_sub),
                        style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                        color = GaddiNeutrals.TextSecondary,
                    )
                }
                Column {
                    ARC_METAS.forEachIndexed { index, meta ->
                        ArcRow(
                            meta = meta,
                            selected = selectedArcCode == meta.code,
                            onSelect = { selectedArcCode = meta.code },
                            showDivider = index != ARC_METAS.lastIndex,
                        )
                    }
                }

                // ── Player count ──────────────────────────────────────────────
                EngravedHeader(eyebrow = stringResource(Res.string.story_player_count_header)) {
                    Text(
                        text = stringResource(Res.string.story_player_count_sub),
                        style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                        color = GaddiNeutrals.TextSecondary,
                    )
                }
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("2", style = GaddiType.caption, color = GaddiNeutrals.TextMuted)
                        Text(
                            text = "⊙ $playerCount",
                            style = GaddiType.display.rozha().copy(fontSize = 20.sp),
                            color = BrandTokens.GoldAntique,
                        )
                        Text("10", style = GaddiType.caption, color = GaddiNeutrals.TextMuted)
                    }
                    Slider(
                        value = playerCount.toFloat(),
                        onValueChange = { playerCount = it.toInt() },
                        valueRange = 2f..10f,
                        steps = 7,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = BrandTokens.GoldAntique,
                                activeTrackColor = BrandTokens.BrassAged,
                                inactiveTrackColor = BrandTokens.BrassDark.copy(alpha = 0.4f),
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── Difficulty quick-pick ─────────────────────────────────────
                EngravedHeader(eyebrow = stringResource(Res.string.story_difficulty_header)) {
                    Text(
                        text = stringResource(Res.string.story_difficulty_sub),
                        style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                        color = GaddiNeutrals.TextSecondary,
                    )
                }
                DifficultyQuickPick(
                    metas = difficultyMetas,
                    selected = difficulty,
                    onSelect = { difficulty = it },
                )
            }
        }

        // ── Footer CTA — a hairline rule lifts the stamp off the form above ────
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
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
            Spacer(Modifier.height(10.dp))
            val arcLabel = ARC_METAS.firstOrNull { it.code == selectedArcCode }?.title ?: "FREE DARBAR"
            StampChit(
                label = s.storyStartCta,
                sublabel = stringResource(Res.string.story_start_sublabel, arcLabel, playerCount, difficulty.name),
                isHero = true,
                onClick = {
                    onStart(Random.nextLong(), playerCount, difficulty, selectedArcCode)
                },
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────── Arc row ─────────────────────────────────────────

/** An arc choice resting on the ground with a hairline rule below — the list idiom
 *  (non-negotiable #4) replacing a stack of bordered arc cards. */
@Composable
private fun ArcRow(
    meta: ArcMeta,
    selected: Boolean,
    onSelect: () -> Unit,
    showDivider: Boolean,
) {
    HairlineRow(
        onClick = onSelect,
        showDivider = showDivider,
        verticalPadding = 12.dp,
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.RadioButton
                contentDescription = "${meta.title}. ${meta.subtitle}. ${meta.blurb}"
            },
    ) {
        // Glyph roundel — a brass-rimmed disc, matching the token idiom (non-negotiable #4)
        // instead of a bordered icon tile.
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .shadow(3.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (selected) BrandTokens.GoldAntique.copy(alpha = 0.9f) else BrandTokens.BrassAged.copy(alpha = 0.35f),
                                if (selected) BrandTokens.BrassAged else BrandTokens.BrassDark.copy(alpha = 0.3f),
                            ),
                        ),
                    ).border(1.dp, BrandTokens.BrassAged.copy(alpha = if (selected) 0.9f else 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                meta.glyph,
                style = GaddiType.title.copy(fontSize = 16.sp),
                color = if (selected) BrandTokens.TeakDark else BrandTokens.GoldAntique,
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = meta.title,
                style = GaddiType.name.copy(fontSize = 14.sp, letterSpacing = 0.5.sp),
                color = if (selected) BrandTokens.GoldAntique else GaddiNeutrals.TextPrimary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = meta.subtitle,
                style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                color = GaddiNeutrals.TextSecondary,
            )
            Text(
                text = meta.blurb,
                style = GaddiType.body.copy(fontSize = 10.sp),
                color = GaddiNeutrals.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (selected) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(BrandTokens.BrassAged.copy(alpha = 0.2f))
                        .border(0.7.dp, BrandTokens.GoldAntique.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    stringResource(Res.string.label_selected_stamp),
                    style = GaddiType.caption.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                    color = BrandTokens.GoldAntique,
                )
            }
        }
    }
}

// ─────────────────────────── Difficulty quick-pick ────────────────────────────

/** Five raised stamp chips (non-negotiable #4) — a compact horizontal quick-pick, matching
 *  the shadow+brass-hairline chip language used across Setup's difficulty selector. */
@Composable
private fun DifficultyQuickPick(
    metas: List<Triple<Difficulty, String, String>>,
    selected: Difficulty,
    onSelect: (Difficulty) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        metas.forEach { (tier, name, _) ->
            val isSelected = tier == selected
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .shadow(
                            if (isSelected) 5.dp else 2.dp,
                            Squircle(GaddiRadii.sm),
                            clip = false,
                            ambientColor = Color.Black,
                            spotColor = BrandTokens.TeakInk,
                        ).clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) {
                                Brush.verticalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged))
                            } else {
                                Brush.verticalGradient(listOf(BrandTokens.TeakMid, BrandTokens.TeakDark))
                            },
                        ).border(
                            if (isSelected) 1.5.dp else GaddiDimens.stroke_ring_idle,
                            if (isSelected) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp),
                        ).clickable { onSelect(tier) }
                        .semantics(mergeDescendants = true) {
                            role = androidx.compose.ui.semantics.Role.RadioButton
                            contentDescription = name
                        }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name,
                    style = GaddiType.caption.copy(fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                    color = if (isSelected) BrandTokens.TeakDark else GaddiNeutrals.TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
