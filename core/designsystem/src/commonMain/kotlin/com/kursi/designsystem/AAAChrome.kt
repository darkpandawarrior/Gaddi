package com.kursi.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════════════════
// AAA polish pass — shared "no boxes, engraved chrome, crafted elements" primitives.
// Used by HomeScreen / SetupScreen / LobbyScreen (design-language.md non-negotiables
// #1, #3, #4). Reuse instead of another local bordered-box helper — see
// docs/design-language.md "Reuse, don't reinvent".
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Engraved section chrome: a small-caps DM Mono eyebrow + a hairline gold rule underneath —
 * the AAA-rebuild replacement for fat gold gradient title bars (non-negotiable #3). [title]
 * adds a sparing Rozha display line above the eyebrow; use rarely (one focal point/screen).
 */
@Composable
fun EngravedHeader(
    eyebrow: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = KursiType.display.rozha(),
                color = KursiNeutrals.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = eyebrow.uppercase(),
                style = KursiType.label_sm.dmMono().copy(letterSpacing = 2.5.sp),
                color = BrandTokens.BrassAged,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                trailing()
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, BrandTokens.GoldAntique.copy(alpha = 0.5f), Color.Transparent),
                        ),
                    ),
        )
    }
}

/**
 * A lit "nav bar" chrome for sub-screens: a back affordance, a centred engraved title, and an
 * optional trailing badge — resting directly on the ground with a hairline rule underneath
 * instead of a filled/bordered bar (non-negotiable #1 and #3). Shared by every screen with a
 * back destination (Setup, Lobby).
 */
@Composable
fun EngravedNavHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String = "Back",
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .defaultMinSize(minWidth = 64.dp, minHeight = 52.dp)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = backLabel
                        }.clickable(onClick = onBack)
                        .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("← $backLabel".uppercase(), style = KursiType.label_sm.dmMono(), color = BrandTokens.BrassAged)
            }
            Text(
                text = title.uppercase(),
                style = KursiType.label.dmMono().copy(letterSpacing = 2.sp),
                color = KursiNeutrals.TextPrimary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(modifier = Modifier.defaultMinSize(minWidth = 64.dp), contentAlignment = Alignment.CenterEnd) {
                if (trailing != null) {
                    Box(modifier = Modifier.padding(end = 20.dp)) { trailing() }
                }
            }
        }
        Spacer(Modifier.height(9.dp))
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

/** Hot lamplit-core colour for [litGround] — matches FeltTableSurface's warm centre. */
private val LitGroundCore = Color(0xFF4A2C1B)

/**
 * The shared "one lit material" ground (non-negotiable #2): a warm radial lamp pool high-centre
 * falling to a vignetted rim, on the same teak/brass palette as the game felt. Use as the root
 * background for every app-flow screen instead of a flat colour fill.
 */
fun Modifier.litGround(): Modifier =
    this
        .background(
            Brush.radialGradient(
                colors =
                    listOf(
                        LitGroundCore,
                        BrandTokens.TeakMid,
                        BrandTokens.TeakDark,
                        BrandTokens.TeakInk,
                    ),
            ),
        ).drawBehind {
            drawTableVignette(centerWarmth = 0.12f, rimDarkness = 0.5f)
        }

/** Visual weight of a [StampButton] — gold-fill focal, dark raised secondary, or a bare ghost tap target. */
enum class StampStyle { Primary, Secondary, Ghost }

/**
 * A raised "stamp" button (non-negotiable #4): gold-fill with dark ink for [StampStyle.Primary],
 * a dark raised surface with a brass hairline for [StampStyle.Secondary]. Disabled dims to 40%.
 * Always ≥48dp tall (non-negotiable #9).
 */
@Composable
fun StampButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: StampStyle = StampStyle.Secondary,
    sublabel: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = Squircle(KursiRadii.md)
    val contentAlpha = if (enabled) 1f else 0.4f
    val bg: Brush
    val textColor: Color
    val subColor: Color
    val borderBrush: Brush
    val borderWidth: Dp
    when {
        !enabled -> {
            bg = Brush.verticalGradient(listOf(BrandTokens.TeakDark, BrandTokens.TeakInk))
            textColor = KursiNeutrals.TextDisabled
            subColor = KursiNeutrals.TextDisabled
            borderBrush = Brush.linearGradient(listOf(BrandTokens.BrassDark.copy(alpha = 0.3f), BrandTokens.BrassDark.copy(alpha = 0.3f)))
            borderWidth = KursiDimens.stroke_hairline
        }
        style == StampStyle.Primary -> {
            bg = Brush.verticalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged))
            textColor = BrandTokens.TeakInk
            subColor = BrandTokens.TeakInk.copy(alpha = 0.7f)
            borderBrush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent))
            borderWidth = 1.dp
        }
        style == StampStyle.Ghost -> {
            bg = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
            textColor = KursiNeutrals.TextSecondary
            subColor = KursiNeutrals.TextMuted
            borderBrush =
                Brush.horizontalGradient(listOf(BrandTokens.BrassDark.copy(alpha = 0.5f), BrandTokens.BrassDark.copy(alpha = 0.5f)))
            borderWidth = KursiDimens.stroke_hairline
        }
        else -> {
            bg = Brush.verticalGradient(listOf(BrandTokens.TeakMid, BrandTokens.TeakDark))
            textColor = KursiNeutrals.TextPrimary
            subColor = KursiNeutrals.TextSecondary
            borderBrush =
                Brush.horizontalGradient(listOf(BrandTokens.GoldAntique.copy(alpha = 0.55f), BrandTokens.BrassDark.copy(alpha = 0.55f)))
            borderWidth = KursiDimens.stroke_ring_idle
        }
    }
    val elevation =
        if (style == StampStyle.Primary) {
            8.dp
        } else if (style == StampStyle.Ghost) {
            0.dp
        } else {
            5.dp
        }

    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = 48.dp)
                .then(
                    if (enabled && elevation > 0.dp) {
                        Modifier.shadow(elevation, shape, clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                    } else {
                        Modifier
                    },
                ).clip(shape)
                .background(bg)
                .border(borderWidth, borderBrush, shape)
                .then(
                    if (enabled) {
                        Modifier.inspectable(onClick = onClick, onLongClick = onClick, pressShape = shape)
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (leading != null) leading()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = KursiType.title.copy(fontSize = 15.sp),
                    color = textColor.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sublabel != null) {
                    Text(
                        text = sublabel,
                        style = KursiType.caption,
                        color = subColor.copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) trailing()
        }
    }
}

/**
 * A row resting directly on the lit ground, separated from its neighbour by a hairline rule —
 * the list idiom that replaces a stack of bordered cards (non-negotiable #4). Always ≥48dp tall.
 */
@Composable
fun HairlineRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    verticalPadding: Dp = 14.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
        }
        if (showDivider) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, BrandTokens.BrassDark.copy(alpha = 0.4f), Color.Transparent),
                            ),
                        ),
            )
        }
    }
}

/**
 * A brass-rimmed circular avatar/monogram token resting on the ground (non-negotiable #4) —
 * the generic form of the game board's opponent seat token, for chrome outside a live match
 * (roster rows, the on-duty persona). Real cast shadow, never a border-box background.
 */
@Composable
fun BrassToken(
    monogram: String,
    fill: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .shadow(6.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(fill.copy(alpha = 0.98f), fill, fill.copy(alpha = 0.75f)),
                    ),
                ).border(1.5.dp, BrandTokens.BrassAged.copy(alpha = 0.75f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = monogram.take(2).uppercase(),
            style = KursiType.name.copy(fontSize = (size.value * 0.3f).sp),
            color = KursiNeutrals.Cream,
        )
    }
}

/**
 * A hairline gold-gradient rule fading in from both edges — the engraved-chrome divider
 * (non-negotiable #3). Use between sections instead of a filled bar or a full-alpha line.
 */
@Composable
fun HairlineRule(
    modifier: Modifier = Modifier,
    alpha: Float = 0.5f,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, BrandTokens.GoldAntique.copy(alpha = alpha), Color.Transparent),
                    ),
                ),
    )
}

/**
 * Column-header labels for a ledger-style list (name/effect/rules columns) — small-caps DM Mono
 * over a [HairlineRule], never a filled bar (non-negotiable #3). [columns] pairs a label with its
 * `Row.weight`, matching the data rows below it.
 */
@Composable
fun LedgerHeaderRow(columns: List<Pair<String, Float>>) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        columns.forEach { (label, weight) ->
            Text(
                text = label,
                style = KursiType.label_sm.dmMono().copy(letterSpacing = 1.sp),
                color = BrandTokens.GoldAntique,
                modifier = Modifier.weight(weight),
            )
        }
    }
    HairlineRule(alpha = 0.35f)
}

/**
 * A text input that reads as a stamped ledger line, not a filled/bordered box (non-negotiable #1):
 * text sits directly on the ground with a hairline rule underneath that brightens to gold on focus.
 * Shared by any screen collecting a short line of text (server address, join code, display name) —
 * used by ≥2 screens (Online Hub, Profile Setup), so it earns extraction per
 * docs/design-language.md "Reuse, don't reinvent".
 */
@Composable
fun EngravedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    monospace: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val textStyle =
        (if (monospace) KursiType.numeric else KursiType.body).copy(
            fontSize = if (monospace) 18.sp else 15.sp,
            color = KursiNeutrals.TextPrimary,
            letterSpacing = if (monospace) 4.sp else 0.sp,
        )
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(BrandTokens.GoldAntique),
            interactionSource = interactionSource,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = capitalization,
                    imeAction = imeAction,
                ),
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 28.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = textStyle.copy(color = KursiNeutrals.TextMuted, letterSpacing = 0.sp, fontStyle = FontStyle.Italic),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            },
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(if (focused) BrandTokens.GoldAntique else BrandTokens.BrassAged.copy(alpha = 0.4f)),
        )
    }
}
