// ─────────────────────────────────────────────────────────────────────────────
// DesignSystemPreviews.kt — the reusable tokens of :core:designsystem, one sheet.
//
// WHY THIS FILE IS SMALL, AND WHY THAT IS THE POINT
//   This repo already has the better half of the problem solved. `:cmp-desktop:renderScreens`
//   (cmp-desktop/src/jvmMain/kotlin/com/kursi/desktop/Screenshots.kt) renders ~40 whole scenes
//   headlessly on the JVM into docs/screenshots/ and screenshots.yml commits them on every push,
//   so the README always shows the CURRENT app. What that harness cannot show is a single token on
//   its own: a CoinPill at 0 vs 12, an OutcomeTag in all six kinds, a RoleCard in its lost state.
//   Those never appear in isolation in any scene, and they are exactly what somebody editing this
//   module is looking at. So this file previews the TOKENS and leaves the SCENES to renderScreens.
//
//   It deliberately does NOT preview the 322 composables in :feature:game and the app shells.
//   Those are screens with real GameState behind them; renderScreens already photographs them with
//   real fixtures, and a @Preview of one would be a worse copy of a shot that already exists.
//
// WHICH @Preview ANNOTATION
//   androidx.compose.ui.tooling.preview.Preview — from org.jetbrains.compose.ui:ui-tooling-preview.
//   Since Compose Multiplatform 1.10 the AndroidX annotation IS the multiplatform one and works
//   directly in commonMain; org.jetbrains.compose.ui.tooling.preview.Preview and the old
//   components-ui-tooling-preview artifact are the deprecated path. config/detekt/detekt.yml
//   already lists both FQNs plus the bare name in every relevant ignoreAnnotated, and its comment
//   at MagicNumber says why — do not "correct" androidx out of that list.
//
// WHAT A PREVIEW HERE PROVES
//   The IDE draws common previews with the ANDROID renderer, so this shows Android metrics for
//   shared code. Layout, spacing, state permutations and colour are platform-independent and it
//   catches all of them; iOS/wasm pixel fidelity it cannot. For that, run the app.
//
// MagicNumber is suppressed file-wide for the same reason Screenshots.kt suppresses it: every
// literal here is a FIXTURE and the literal IS the datum. `CoinPill(count = 12)` is "twelve coins".
// ─────────────────────────────────────────────────────────────────────────────
@file:Suppress("MagicNumber")

package com.kursi.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kursi.engine.Role

/**
 * Every preview renders through the real [KursiTheme], because these components read
 * `LocalKursiFonts` and the Deco colour scheme from it.
 *
 * [LocalReducedMotion] is forced on, and that is load-bearing rather than polite: [CoinPill]'s
 * arrival spring, [CountdownBar] and the holo rims all settle to their resting frame instead of
 * being caught mid-animation, so a preview is a still of the FINAL state and is the same still
 * every time.
 */
@Composable
private fun PreviewShell(content: @Composable () -> Unit) {
    KursiTheme {
        CompositionLocalProvider(LocalReducedMotion provides true) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(BrandTokens.TeakInk)
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

// ── Role identity ────────────────────────────────────────────────────────────

/**
 * All six roles at Large. PATRAKAAR is last on purpose: it is the only role outside `baseRoles`,
 * so it is the one whose enamel and glyph are easiest to leave un-updated after a palette change.
 */
@Preview(widthDp = 360, heightDp = 560)
@Composable
private fun RoleCardAllRolesPreview() {
    PreviewShell {
        Role.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { RoleCard(role = it, size = CardSize.Small) }
            }
        }
    }
}

/** The three sizes plus the `lost` treatment — the states a card is actually drawn in. */
@Preview(widthDp = 360, heightDp = 320)
@Composable
private fun RoleCardStatesPreview() {
    PreviewShell {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            RoleCard(role = Role.NETA, size = CardSize.Small)
            RoleCard(role = Role.NETA, size = CardSize.Medium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoleCard(role = Role.BHAI, size = CardSize.Small, lost = true)
            RoleCard(role = Role.BHAI, size = CardSize.Small, lifted = true)
        }
    }
}

/** The bare glyphs, off the card. A glyph that only ever reads on its own enamel hides mistakes. */
@Preview(widthDp = 360, heightDp = 140)
@Composable
private fun RoleGlyphPreview() {
    PreviewShell {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Role.entries.forEach { RoleGlyph(role = it, modifier = Modifier.size(40.dp)) }
        }
    }
}

// ── Seat and resource tokens ─────────────────────────────────────────────────

@Preview(widthDp = 360, heightDp = 180)
@Composable
private fun SeatTokensPreview() {
    PreviewShell {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeatAvatar(initial = "AP", color = KursiColors.forRole(Role.NETA).color)
            SeatAvatar(initial = "RJ", color = KursiColors.forRole(Role.VAKIL).color)
            ChatAvatar(monogram = "AP", color = BrandTokens.GoldAntique)
            WaxSeal()
        }
        // 0 / 2 / 12: empty, the common case, and two digits — where a fixed-width pill breaks.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CoinPill(count = 0)
            CoinPill(count = 2)
            CoinPill(count = 12)
        }
        // Two influence alive, then one alive with a revealed loss — the pip row's whole job.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfluencePips(alive = 2, lost = emptyList())
            InfluencePips(alive = 1, lost = listOf(Role.BABU))
        }
    }
}

// ── Claim and read-out chips ─────────────────────────────────────────────────

/** Verified vs unverified is a one-character difference ("VAKIL" vs "VAKIL?"). Show both. */
@Preview(widthDp = 360, heightDp = 200)
@Composable
private fun ChipsPreview() {
    PreviewShell {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClaimChip(text = "VAKIL", color = KursiColors.forRole(Role.VAKIL).color, verified = true)
            ClaimChip(text = "NETA", color = KursiColors.forRole(Role.NETA).color, verified = false)
        }
        // 1-2 green, 3 amber, 4-5 red: the whole colour ladder in one render.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SuspicionChip(pips = 1, label = "calm")
            SuspicionChip(pips = 3, label = "watching")
            SuspicionChip(pips = 5, label = "certain")
        }
    }
}

/** Every OutcomeKind. A new entry added without a colour would show up here as a duplicate tag. */
@Preview(widthDp = 360, heightDp = 180)
@Composable
private fun OutcomeTagAllKindsPreview() {
    PreviewShell {
        OutcomeKind.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { OutcomeTag(kind = it) }
            }
        }
    }
}

// ── Controls ─────────────────────────────────────────────────────────────────

@Preview(widthDp = 360, heightDp = 320)
@Composable
private fun KursiActionButtonPreview() {
    PreviewShell {
        KursiActionButton(label = "DEHAADI", sublabel = "+1 coin, unchallengeable")
        KursiActionButton(
            label = "LAGAAN",
            sublabel = "+3 coins",
            roleAccent = KursiColors.forRole(Role.BABU).color,
        )
        KursiActionButton(
            label = "TAKHTAPALAT",
            sublabel = "costs 7",
            enabled = false,
            disabledReason = "Need 7 coins",
        )
    }
}

/** The bar flips from amber to StampRed under 0.25 — the exact threshold is the thing to see. */
@Preview(widthDp = 360, heightDp = 160)
@Composable
private fun CountdownBarPreview() {
    PreviewShell {
        CountdownBar(fraction = 1f)
        CountdownBar(fraction = 0.26f)
        CountdownBar(fraction = 0.24f)
        CountdownBar(fraction = 0f)
    }
}

// ── Table chatter ────────────────────────────────────────────────────────────

@Preview(widthDp = 360, heightDp = 320)
@Composable
private fun SpeechBubblePreview() {
    PreviewShell {
        Column(modifier = Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeechBubble(
                speakerName = "Aap",
                text = "Lagaan. Babu hoon main.",
                accent = KursiColors.forRole(Role.BABU).color,
                fromPlayer = true,
            )
            SpeechBubble(
                speakerName = "Munshi",
                text = "Jhooth! Patrakaar ke paas proof hai.",
                accent = KursiColors.forRole(Role.PATRAKAAR).color,
                fromPlayer = false,
                emphatic = true,
            )
        }
    }
}
