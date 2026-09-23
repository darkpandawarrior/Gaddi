package com.kursi.shared.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.core.prefs.GauntletProgress
import com.kursi.designsystem.*
import com.kursi.feature.game.Difficulty
import com.kursi.feature.game.GauntletLadder
import com.kursi.feature.game.GauntletRung
import com.kursi.shared.strings.GaddiStrings
import com.kursi.shared.strings.LocalGaddiStrings
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.a11y_gauntlet_current_rung
import kursi.core.designsystem.generated.resources.a11y_gauntlet_ladder_row
import kursi.core.designsystem.generated.resources.gauntlet_rung_sub
import org.jetbrains.compose.resources.stringResource

/**
 * M6e — GAUNTLET / Tarakki ki Seedhi: the escalating promotion ladder. A vertical bracket of five
 * difficulty rungs (Easy → Grandmaster); the player beats one to be promoted to the next. The current
 * target rung is a raised spotlight card with the Start CTA — the one focal point of the screen
 * (design-language.md #5); cleared and locked rungs are plain hairline rows on the lit ground.
 * Progress comes from [com.kursi.core.prefs.GauntletProgress] (persisted in AppPrefs).
 *
 * [onPlayRung] routes to a gauntlet match for the given 0-based rung index (the app tags the Game route
 * with the rung so game-over records the result + promotes on a win).
 */
@Composable
fun GauntletScreen(
    progress: GauntletProgress,
    onPlayRung: (rungIndex: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalGaddiStrings.current
    val scroll = rememberScrollState()
    val rungs = GauntletLadder.RUNGS
    val targetIndex = progress.targetRung.coerceIn(0, rungs.lastIndex)
    val conquered = progress.isConquered(rungs.lastIndex)

    Column(modifier = modifier.fillMaxSize().litGround()) {
        // ── Header — engraved chrome, not a filled bordered bar ─────────────────
        EngravedNavHeader(
            title = s.gauntletHeader,
            onBack = onBack,
            backLabel = s.back,
            modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
            trailing = {
                Text(s.gauntletBadge, style = GaddiType.caption.copy(fontSize = 9.sp), color = GaddiNeutrals.TextMuted)
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
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Tagline + progress — plain on the ground, no bordered panel.
                Text(
                    text = s.gauntletTagline,
                    style = GaddiType.body.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic),
                    color = GaddiNeutrals.TextSecondary,
                )
                Text(
                    text = s.gauntletStripLabel(progress.clearedCount.coerceIn(0, rungs.size), rungs.size),
                    style = GaddiType.label.copy(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = BrandTokens.GoldAntique,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )

                if (conquered) {
                    ConqueredSpotlight(title = s.gauntletConqueredTitle, body = s.gauntletConqueredBody)
                    Spacer(Modifier.height(12.dp))
                }

                // The ladder, top rung first (most prestigious at the top so it reads as a climb).
                val orderedRungs = rungs.asReversed()
                orderedRungs.forEachIndexed { i, rung ->
                    val state =
                        when {
                            rung.index <= progress.clearedRung -> RungState.CLEARED
                            rung.index == targetIndex && !conquered -> RungState.CURRENT
                            else -> RungState.LOCKED
                        }
                    if (state == RungState.CURRENT) {
                        GauntletCurrentSpotlight(rung = rung, strings = s, onPlay = { onPlayRung(rung.index) })
                        Spacer(Modifier.height(10.dp))
                    } else {
                        GauntletLadderRow(
                            rung = rung,
                            state = state,
                            strings = s,
                            onPlay = { onPlayRung(rung.index) },
                            showDivider = i != orderedRungs.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

private enum class RungState { CLEARED, CURRENT, LOCKED }

/** The one-time "ladder conquered" moment — a raised lit surface (shadow + gradient, no
 *  outline border), the same depth idiom as every other elevated card in the language. */
@Composable
private fun ConqueredSpotlight(
    title: String,
    body: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(8.dp, Squircle(GaddiRadii.md), clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged)))
                .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = GaddiType.display.rozha().copy(fontSize = 19.sp), color = BrandTokens.TeakDark)
        Text(body, style = GaddiType.body.copy(fontSize = 12.sp), color = BrandTokens.TeakDark.copy(alpha = 0.8f))
    }
}

/** The target rung — a raised lit surface with the Start CTA. The single focal point of the ladder
 *  (design-language.md #5); every other rung recedes into a plain hairline row. */
@Composable
private fun GauntletCurrentSpotlight(
    rung: GauntletRung,
    strings: GaddiStrings,
    onPlay: () -> Unit,
) {
    val nameplate = rungNameOf(rung.index, strings)
    val difficultyName = difficultyNameOf(rung.difficulty, strings)
    val currentRungDesc =
        stringResource(Res.string.a11y_gauntlet_current_rung, nameplate, difficultyName, rung.players, strings.gauntletCurrentTag)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(8.dp, Squircle(GaddiRadii.md), clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(BrandTokens.GoldAntique.copy(alpha = 0.20f), BrandTokens.BrassDark.copy(alpha = 0.30f)),
                    ),
                ).padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = currentRungDesc
                },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrassToken(monogram = "${rung.index + 1}", fill = BrandTokens.GoldAntique, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nameplate,
                    style = GaddiType.display.rozha().copy(fontSize = 20.sp),
                    color = BrandTokens.GoldAntique,
                )
                Text(
                    text = stringResource(Res.string.gauntlet_rung_sub, difficultyName, rung.players),
                    style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                    color = GaddiNeutrals.TextSecondary,
                )
            }
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(BrandTokens.GoldAntique.copy(alpha = 0.18f))
                        .border(0.7.dp, BrandTokens.GoldAntique.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = strings.gauntletCurrentTag,
                    style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
                    color = BrandTokens.GoldAntique,
                )
            }
        }
        StampButton(
            label = strings.gauntletStartCta,
            sublabel = strings.gauntletStartCtaSub,
            style = StampStyle.Primary,
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A cleared or locked rung — a plain hairline row on the ground (non-negotiable #4 "lists =
 *  rows"). Cleared rows are tappable to replay; locked rows are dimmed and inert. */
@Composable
private fun GauntletLadderRow(
    rung: GauntletRung,
    state: RungState,
    strings: GaddiStrings,
    onPlay: () -> Unit,
    showDivider: Boolean,
) {
    val nameplate = rungNameOf(rung.index, strings)
    val difficultyName = difficultyNameOf(rung.difficulty, strings)
    val locked = state == RungState.LOCKED
    val cleared = state == RungState.CLEARED
    val tag =
        when (state) {
            RungState.CLEARED -> strings.gauntletClearedTag
            RungState.LOCKED -> strings.gauntletLockedTag
            RungState.CURRENT -> strings.gauntletCurrentTag
        }

    val ladderRowDesc = stringResource(Res.string.a11y_gauntlet_ladder_row, nameplate, difficultyName, rung.players, tag)
    HairlineRow(
        onClick = if (cleared) onPlay else null,
        showDivider = showDivider,
        verticalPadding = 12.dp,
        modifier =
            Modifier.alpha(if (locked) 0.55f else 1f).semantics(mergeDescendants = true) {
                if (cleared) role = androidx.compose.ui.semantics.Role.Button
                contentDescription = ladderRowDesc
            },
    ) {
        BrassToken(
            monogram = if (cleared) "✓" else "${rung.index + 1}",
            fill = if (cleared) BrandTokens.BrassAged else BrandTokens.TeakDark,
            size = 40.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nameplate,
                style = GaddiType.name.copy(fontSize = 14.sp, letterSpacing = 0.5.sp),
                color = if (cleared) GaddiNeutrals.TextPrimary else GaddiNeutrals.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$difficultyName · ${rung.players} gaddiyaan",
                style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                color = GaddiNeutrals.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (cleared) {
            Text(
                text = "${strings.gauntletReplayCta} ▸",
                style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
                color = BrandTokens.BrassAged.copy(alpha = 0.85f),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(BrandTokens.StampRed.copy(alpha = 0.10f))
                        .border(0.7.dp, BrandTokens.StampRed.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = tag,
                    style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
                    color = BrandTokens.StampRed.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun rungNameOf(
    index: Int,
    s: GaddiStrings,
): String =
    listOf(
        s.gauntletRung0Name,
        s.gauntletRung1Name,
        s.gauntletRung2Name,
        s.gauntletRung3Name,
    ).getOrElse(index) { s.gauntletRung4Name }

private fun difficultyNameOf(
    d: Difficulty,
    s: GaddiStrings,
): String =
    when (d) {
        Difficulty.Easy -> s.diffEasyName
        Difficulty.Medium -> s.diffMediumName
        Difficulty.Hard -> s.diffHardName
        Difficulty.Expert -> s.diffExpertName
        Difficulty.Grandmaster -> s.diffGrandmasterName
    }
