package com.kursi.feature.game.overlays

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.BrandTokens
import com.kursi.designsystem.KursiType
import com.kursi.designsystem.marcellus
import com.kursi.engine.GameEvent
import com.kursi.feature.game.GameUiState
import com.kursi.feature.game.KursiVoice
import com.kursi.feature.game.LocalKursiVoice
import com.kursi.feature.game.status.isRecapWorthy
import com.kursi.feature.game.status.recapNames

/**
 * FOCUS/GUIDED "what's happening" headline (spec §6, §8.1) — one calm, plain-language sentence
 * for the most recent meaningful beat, replacing ANALYST's denser [com.kursi.feature.game.status.RecapRail].
 *
 * Templated + deterministic, ALWAYS: it reuses [KursiVoice.recap], the same bilingual copy source
 * ANALYST's recap strip already draws from. Per spec §8.6's latency rule this is the floor that
 * must render instantly — it stays pure and synchronous on purpose, never awaits anything. The AI
 * Munshi narrator (spec §8.1) does not replace this function; see [displayHeadlineFor], which
 * upgrades its result in place with [GameUiState.narrationText] when that async line has landed.
 */
fun headlineFor(
    events: List<GameEvent>,
    state: GameUiState,
    voice: KursiVoice,
): String {
    val event = events.lastOrNull { isRecapWorthy(it) } ?: return voice.opponentActing(actorName(state))
    val (actor, other) = recapNames(event, state)
    return voice.recap(event, actor, other) ?: voice.opponentActing(actorName(state))
}

/**
 * MUNSHI SOURCE (spec §8.1, §8.6) — which line [BeatHeadline] is showing right now, and, for the
 * AI tier, whether the line may still grow. Callers must not silently collapse this to a bare
 * `String` — the whole point (spec §8.1's "a player can tell whether the AI actually spoke") is
 * that the templated floor and a Munshi line, streaming or settled, are three different states,
 * not one interchangeable string.
 */
sealed interface BeatLine {
    val text: String

    /** The deterministic, always-available [headlineFor] line — no AI tier upgraded it. */
    data class Templated(
        override val text: String,
    ) : BeatLine

    /** A Munshi line. [streaming] is true while more tokens may still land for this same beat. */
    data class Ai(
        override val text: String,
        val streaming: Boolean,
    ) : BeatLine
}

/**
 * The line the headline actually shows (spec §8.1, §8.6): [GameUiState.narrationText] when the
 * Munshi has produced one for THIS beat, otherwise the templated [headlineFor] line. Pure — kept
 * separate from [BeatHeadline] so the upgrade-in-place rule is unit-testable without Compose.
 */
fun displayHeadlineFor(
    events: List<GameEvent>,
    state: GameUiState,
    voice: KursiVoice,
): BeatLine {
    val narration = state.narrationText?.trim()?.takeIf { it.isNotBlank() }
    return if (narration != null) {
        BeatLine.Ai(narration, streaming = state.narrationStreaming)
    } else {
        BeatLine.Templated(headlineFor(events, state, voice))
    }
}

/**
 * AAA FOCUS rebuild: an ITALIC ENGRAVED caption — no bar, no fill, no border. Reads as text
 * cut into the felt beneath the pot (mockup: `.beat { font-style: italic; color: gold }`),
 * not a chip. Rendered by [com.kursi.feature.game.board.FeltCenterTokens] directly under the
 * brass medallion for FOCUS/GUIDED — see [com.kursi.feature.game.GameScreen] for the density
 * gate that used to place this in the top chrome.
 */
@Composable
internal fun BeatHeadline(
    state: GameUiState,
    modifier: Modifier = Modifier,
) {
    val voice = LocalKursiVoice.current
    val line = displayHeadlineFor(state.recentEvents, state, voice)
    // A player "can tell whether the AI actually spoke" (spec §8.1): a subtle dip in-progress,
    // full weight once the AI line settles — same weight as the templated line always had.
    val alpha = if (line is BeatLine.Ai && line.streaming) 0.85f else 1f
    Text(
        text = line.text,
        style = KursiType.body.marcellus().copy(fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp),
        color = BrandTokens.GoldAntique.copy(alpha = alpha),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .widthIn(max = 320.dp)
                .padding(horizontal = 12.dp)
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = if (line is BeatLine.Ai) "Munshi: ${line.text}" else line.text
                },
    )
}
