package com.kursi.shared.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.core.prefs.AppPrefs
import com.kursi.designsystem.*
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.a11y_avatar_option
import kursi.core.designsystem.generated.resources.a11y_seat_color
import kursi.core.designsystem.generated.resources.cta_confirm_stamp
import kursi.core.designsystem.generated.resources.profile_avatar_label
import kursi.core.designsystem.generated.resources.profile_back_label
import kursi.core.designsystem.generated.resources.profile_eyebrow_first_run
import kursi.core.designsystem.generated.resources.profile_name_label
import kursi.core.designsystem.generated.resources.profile_name_placeholder
import kursi.core.designsystem.generated.resources.profile_seat_color_label
import kursi.core.designsystem.generated.resources.profile_skip_link
import kursi.core.designsystem.generated.resources.profile_title_settings
import kursi.core.designsystem.generated.resources.profile_welcome_title
import org.jetbrains.compose.resources.stringResource

// ══════════════════════════════════════════════════════════════════════════════
// ProfileSetupScreen — shown after the primer (first run) or from Settings.
//
// Sarkari Noir rebuild: engraved chrome on the lit ground (no floating cream card), a live
// preview [BrassToken] avatar, [EngravedField] for the name, avatar/colour pickers as brass-rimmed
// tokens (selected = gold ring + lift, not a filled swatch box), and the commit CTA as a gold
// [StampButton] — matching the Home/Setup/Gauntlet standard (design-language.md #1, #3, #4).
//
// The player fills out three fields:
//   1. Display name (text field, max 18 chars, defaults to "Khiladi")
//   2. Avatar emoji (12-option grid from AVATAR_ROSTER)
//   3. Seat accent color (10 swatches from GaddiSeatColors)
//
// onDone() is called once the player taps MUHAR LAGAO (confirm stamp).
// ══════════════════════════════════════════════════════════════════════════════

/** Canonical avatar emoji roster — political/strategy theme, 12 options. */
val AVATAR_ROSTER =
    listOf(
        "🪑", // The Chair itself
        "👑", // Crown
        "⚖️", // Scales — law/vakil
        "🎯", // Precision
        "🃏", // Joker card
        "🎭", // Masks — bluff/drama
        "🏛️", // Institution
        "📜", // Official scroll
        "🔑", // Keys to power
        "🎪", // Tamasha
        "🦁", // Sher — lion
        "🦊", // Siyaar — cunning fox
    )

@Composable
fun ProfileSetupScreen(
    prefs: AppPrefs,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** True when accessed from Settings (shows "WAPAS" back button instead of "skip"). */
    fromSettings: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(prefs.playerName.ifBlank { "" }) }
    var avatarIdx by remember { mutableIntStateOf(prefs.playerAvatarIdx.coerceAtLeast(0)) }
    var colorArgb by remember {
        mutableLongStateOf(
            prefs.playerColorArgb.takeIf { it != 0L }
                ?: 0xFFE63946L, // default warm red
        )
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val previewColor = Color(colorArgb)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .litGround()
                .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        if (fromSettings && onBack != null) {
            EngravedNavHeader(
                title = stringResource(Res.string.profile_title_settings),
                onBack = onBack,
                backLabel = stringResource(Res.string.profile_back_label),
                modifier = Modifier.padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 4.dp),
            )
        } else {
            EngravedHeader(
                eyebrow = stringResource(Res.string.profile_eyebrow_first_run),
                title = stringResource(Res.string.profile_welcome_title),
                modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 4.dp),
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // ── Live preview token ───────────────────────────────────────────
            val previewEmoji = AVATAR_ROSTER.getOrNull(avatarIdx) ?: "🪑"
            val previewMonogram =
                name
                    .trim()
                    .take(2)
                    .uppercase()
                    .ifBlank { "KH" }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(84.dp)
                            .shadow(14.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(previewColor.copy(alpha = 0.98f), previewColor, previewColor.copy(alpha = 0.75f)),
                                ),
                            ).border(2.dp, BrandTokens.BrassAged.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = previewEmoji, fontSize = 36.sp)
                }
                Text(
                    text = previewMonogram,
                    style = GaddiType.label_sm.dmMono().copy(letterSpacing = 2.sp),
                    color = BrandTokens.BrassAged.copy(alpha = 0.75f),
                )
            }

            // ── FORM FIELD 1 — Display name ────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EngravedHeader(eyebrow = stringResource(Res.string.profile_name_label))
                EngravedField(
                    value = name,
                    onValueChange = { if (it.length <= 18) name = it },
                    placeholder = stringResource(Res.string.profile_name_placeholder),
                    singleLine = true,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                )
                Text(
                    text = "${name.length}/18",
                    style = GaddiType.label_micro.dmMono().copy(fontSize = 10.sp),
                    color = GaddiNeutrals.TextMuted,
                    modifier = Modifier.align(Alignment.End),
                )
            }

            // ── FORM FIELD 2 — Avatar emoji ────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EngravedHeader(eyebrow = stringResource(Res.string.profile_avatar_label))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().height(120.dp), // 2 rows × (52dp + 10dp gap)
                    userScrollEnabled = false,
                ) {
                    itemsIndexed(AVATAR_ROSTER) { idx, emoji ->
                        val selected = idx == avatarIdx
                        val avatarDesc = stringResource(Res.string.a11y_avatar_option, emoji)
                        val ringColor by animateColorAsState(
                            if (selected) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.55f),
                            animationSpec = tween(180),
                            label = "avatarRing",
                        )
                        Box(
                            modifier =
                                Modifier
                                    .size(52.dp)
                                    .then(
                                        if (selected) {
                                            Modifier.shadow(
                                                6.dp,
                                                CircleShape,
                                                clip = false,
                                                ambientColor = Color.Black,
                                                spotColor = BrandTokens.TeakInk,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ).clip(CircleShape)
                                    .background(if (selected) BrandTokens.TeakMid else BrandTokens.TeakDark.copy(alpha = 0.6f))
                                    .border(if (selected) 2.dp else 1.5.dp, ringColor, CircleShape)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { avatarIdx = idx }
                                    .semantics { contentDescription = avatarDesc },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }

            // ── FORM FIELD 3 — Seat color ──────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EngravedHeader(eyebrow = stringResource(Res.string.profile_seat_color_label))
                val seatColorDesc = stringResource(Res.string.a11y_seat_color)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GaddiSeatColors.all.forEach { swatch ->
                        val swatchArgb = swatch.value.toLong()
                        val selected = swatchArgb == colorArgb
                        val ringColor by animateColorAsState(
                            if (selected) BrandTokens.GoldAntique else BrandTokens.BrassAged.copy(alpha = 0.6f),
                            animationSpec = tween(180),
                            label = "swatchRing",
                        )
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .then(
                                        if (selected) {
                                            Modifier.shadow(
                                                5.dp,
                                                CircleShape,
                                                clip = false,
                                                ambientColor = Color.Black,
                                                spotColor = BrandTokens.TeakInk,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ).clip(CircleShape)
                                    .background(swatch)
                                    .border(if (selected) 2.5.dp else 1.5.dp, ringColor, CircleShape)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { colorArgb = swatchArgb }
                                    .semantics { contentDescription = seatColorDesc },
                        )
                    }
                }
            }

            // ── MUHAR LAGAO — commit button ────────────────────────────────────
            StampButton(
                label = stringResource(Res.string.cta_confirm_stamp),
                onClick = {
                    prefs.playerName = name.trim().ifBlank { "Khiladi" }
                    prefs.playerAvatarIdx = avatarIdx
                    prefs.playerColorArgb = colorArgb
                    onDone()
                },
                style = StampStyle.Primary,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Skip link (first-run only) ─────────────────────────────────────
            if (!fromSettings) {
                Text(
                    text = stringResource(Res.string.profile_skip_link),
                    style = GaddiType.label_sm.dmMono().copy(letterSpacing = 1.sp),
                    color = BrandTokens.BrassAged.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .wrapContentHeight()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                // Save defaults even on skip so hasPlayerProfile triggers only on explicit set.
                                if (prefs.playerName.isBlank()) prefs.playerName = "Khiladi"
                                if (prefs.playerAvatarIdx < 0) prefs.playerAvatarIdx = 0
                                if (prefs.playerColorArgb == 0L) prefs.playerColorArgb = 0xFFE63946L
                                onDone()
                            },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
