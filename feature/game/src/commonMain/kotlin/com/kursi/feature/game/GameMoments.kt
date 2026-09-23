package com.kursi.feature.game

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.kursi.designsystem.GaddiRoleHues
import com.kursi.designsystem.audio.rememberGaddiSoundPlayer
import com.kursi.designsystem.moment.ActionMomentOverlay
import com.kursi.designsystem.moment.GaddiMoment
import com.kursi.designsystem.moment.LocalTableAnchorRegistry
import com.kursi.designsystem.moment.MomentHost
import com.kursi.designsystem.moment.SeatId
import com.kursi.designsystem.moment.TableAnchors
import com.kursi.designsystem.moment.rememberSoundPlayer
import com.kursi.designsystem.moment.reportOverlayBounds
import com.kursi.engine.Action
import com.kursi.engine.GameEvent
import com.kursi.engine.PlayerId
import com.kursi.engine.Role
import com.kursi.engine.Rules
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════════
// GameMoments.kt — bridges the engine event stream to the designsystem "moment"
// stamp-theatre framework. Pure presentation glue; does NOT touch :engine / :ai logic.
//
//  - mapEventToMoment(): engine GameEvent  →  GaddiMoment (License Raj stamp theatre).
//  - rememberSeatIdResolver(): engine PlayerId  →  SeatId (index in view.players).
//  - GameMomentLayer(): a BoxScope overlay that owns a MomentHost, builds proportional
//    TableAnchors from the host Box size, and fires moments as new events arrive.
//
// Mount inside the SAME Box that holds the felt table — layered ABOVE the felt surface
// but BELOW the action dock so the dock stays tappable.
// ═══════════════════════════════════════════════════════════════════════════════

// ─────────────────────────── PlayerId → SeatId ───────────────────────────────

/**
 * Resolves an engine [PlayerId] to a [SeatId] (an Int the moment framework keys seats by).
 *
 * The contract from the task: PlayerId → SeatId via *index in state.view.players*.
 * `view.players` is seat-ordered, so the list index is the canonical seat slot used by
 * the proportional anchor ellipse below.
 */
internal class SeatIdResolver(
    private val playerIdToIndex: Map<PlayerId, Int>,
) {
    /** Index of [id] in view.players, or -1 if unknown (eliminated/redacted). */
    fun seatOf(id: PlayerId): SeatId = playerIdToIndex[id] ?: -1

    val seatCount: Int get() = playerIdToIndex.size
}

@Composable
internal fun rememberSeatIdResolver(state: GameUiState): SeatIdResolver {
    // Key on the ordered player-id list so the map is rebuilt only when the seating changes.
    val orderedIds = state.view.players.map { it.id }
    return remember(orderedIds) {
        SeatIdResolver(orderedIds.withIndex().associate { (i, id) -> id to i })
    }
}

// ─────────────────────────── Role hue lookup ─────────────────────────────────

/** Role → Okabe-Ito hue from the single :core:designsystem source of truth. */
internal fun roleHueOf(role: Role?): Color =
    when (role) {
        Role.NETA -> GaddiRoleHues.Neta
        Role.BHAI -> GaddiRoleHues.Bhai
        Role.BABU -> GaddiRoleHues.Babu
        Role.JUGAADU -> GaddiRoleHues.Jugaadu
        Role.VAKIL -> GaddiRoleHues.Vakil
        Role.PATRAKAAR -> GaddiRoleHues.Patrakaar
        null -> GaddiRoleHues.Neta // safe default; only used where a hue is structurally required
    }

// ─────────────────────────── Event → Moment mapping ──────────────────────────

/**
 * Maps a single engine [GameEvent] to the matching [GaddiMoment], or null when the event
 * carries no on-screen theatre of its own (e.g. coin bookkeeping that a parent beat already
 * animates, or transient card-replacement plumbing).
 *
 * Seat ids are resolved through [resolve]; player names come from [state.opponentPersonas]
 * so beats can show "Rahul stole from Priya" instead of raw seat indexes.
 */
internal fun mapEventToMoment(
    event: GameEvent,
    resolve: SeatIdResolver,
    state: GameUiState,
    humanDisplayName: String = "Khiladi",
): GaddiMoment? {
    // Helper: display name for a PlayerId (bot persona name, or player's display name for the human seat).
    fun nameOf(id: PlayerId): String =
        if (id == state.view.viewer) {
            humanDisplayName
        } else {
            state.opponentPersonas[id]?.name ?: "?"
        }

    return when (event) {
        is GameEvent.ActionDeclared ->
            when (val a = event.action) {
                Action.Income ->
                    GaddiMoment.Income(
                        actorSeat = resolve.seatOf(event.actor),
                        actorName = nameOf(event.actor),
                    )
                Action.ForeignAid ->
                    GaddiMoment.ForeignAid(
                        actorSeat = resolve.seatOf(event.actor),
                        actorName = nameOf(event.actor),
                    )
                Action.Tax ->
                    GaddiMoment.Tax(
                        actorSeat = resolve.seatOf(event.actor),
                        roleHue = roleHueOf(Role.NETA),
                        actorName = nameOf(event.actor),
                    )
                Action.Exchange ->
                    GaddiMoment.Exchange(
                        actorSeat = resolve.seatOf(event.actor),
                        roleHue = roleHueOf(Role.JUGAADU),
                        actorName = nameOf(event.actor),
                    )
                is Action.Steal ->
                    GaddiMoment.Steal(
                        actorSeat = resolve.seatOf(event.actor),
                        victim = resolve.seatOf(a.target),
                        roleHue = roleHueOf(Role.BABU),
                        actorName = nameOf(event.actor),
                        victimName = nameOf(a.target),
                    )
                is Action.Assassinate ->
                    GaddiMoment.Assassinate(
                        actorSeat = resolve.seatOf(event.actor),
                        target = resolve.seatOf(a.target),
                        roleHue = roleHueOf(Role.BHAI),
                        actorName = nameOf(event.actor),
                        targetName = nameOf(a.target),
                    )
                is Action.Coup ->
                    GaddiMoment.Coup(
                        actorSeat = resolve.seatOf(event.actor),
                        target = resolve.seatOf(a.target),
                        actorName = nameOf(event.actor),
                        targetName = nameOf(a.target),
                    )
                // Jaanch (Investigate) has no bespoke table-theatre overlay of its own — the private
                // peek is a secrecy-bounded fact, narrated in the log/recap via GaddiVoice rather than
                // shown as a public moment. Fold it into the neighbouring beat (no standalone moment).
                is Action.Investigate -> null
                // Variant actions — no distinct table-theatre overlay (folded into log narration).
                Action.BailPe, Action.Sabotage, is Action.Hawala, Action.Emergency -> null
            }

        is GameEvent.Blocked ->
            GaddiMoment.Block(
                actorSeat = resolve.seatOf(event.blocker),
                blockedSeat = resolve.seatOf(blockedActorOf(event.action) ?: event.blocker),
                roleHue = roleHueOf(event.role),
                blockerName = nameOf(event.blocker),
                blockedName = blockedActorOf(event.action)?.let { nameOf(it) } ?: "",
            )

        is GameEvent.Challenged ->
            GaddiMoment.Challenge(
                actorSeat = resolve.seatOf(event.challenger),
                claimant = resolve.seatOf(event.target),
                challengerName = nameOf(event.challenger),
                claimantName = nameOf(event.target),
            )

        is GameEvent.ChallengeRevealed ->
            GaddiMoment.Reveal(
                actorSeat = resolve.seatOf(event.player),
                claimant = resolve.seatOf(event.player),
                claimedRole = event.role.name,
                truthful = event.hadRole,
                roleHue = roleHueOf(event.role),
                playerName = nameOf(event.player),
            )

        is GameEvent.InfluenceLost ->
            GaddiMoment.InfluenceLoss(
                actorSeat = resolve.seatOf(event.player),
                lostRole = event.role.name,
                roleHue = roleHueOf(event.role),
                playerName = nameOf(event.player),
            )

        is GameEvent.PlayerEliminated ->
            GaddiMoment.Elimination(
                actorSeat = resolve.seatOf(event.player),
                playerName = nameOf(event.player),
            )

        is GameEvent.TurnAdvanced ->
            GaddiMoment.TurnHandoff(
                actorSeat = event.toSeat,
                nextSeat = event.toSeat,
                nextName =
                    state.view.players
                        .firstOrNull { it.seatIndex == event.toSeat }
                        ?.let { p -> if (p.id == state.view.viewer) "Aap" else state.opponentPersonas[p.id]?.name ?: "" }
                        ?: "",
            )

        is GameEvent.GameEnded ->
            GaddiMoment.Win(
                actorSeat = resolve.seatOf(event.winner),
                winnerName = nameOf(event.winner),
            )

        // Events with no standalone moment — their effect is folded into a neighbouring beat.
        // Investigated / InvestigateRedraw are secrecy-bounded (the peeked role is never public),
        // so they get no public overlay — only log/recap narration via GaddiVoice.
        is GameEvent.ActionResolved,
        is GameEvent.ActionNegated,
        is GameEvent.CoinsChanged,
        is GameEvent.CoinsTransferred,
        is GameEvent.CardReplaced,
        is GameEvent.Exchanged,
        is GameEvent.Investigated,
        is GameEvent.InvestigateRedraw,
        // Variant events — narrated in the log; no table-theatre overlay.
        is GameEvent.InfluenceRestored,
        is GameEvent.CoinsGifted,
        is GameEvent.EmergencyDeclared,
        is GameEvent.KhazanaWon,
        is GameEvent.DarjaReached,
        -> null
    }
}

/** The action's original actor (the one being blocked) for a Block beat anchor, if derivable. */
private fun blockedActorOf(action: Action): PlayerId? = Rules.targetOf(action)

// ─────────────────────────── Proportional anchors ────────────────────────────

/**
 * Derives reasonable [TableAnchors] from the overlay box size: seats arranged around an
 * ellipse with the human (seat 0) anchored at the bottom-centre, opponents fanned across
 * the upper arc, and the treasury at the table centre. Layout-agnostic first pass that
 * compiles and never crashes; precise onGloballyPositioned measurement can replace it later.
 */
internal fun proportionalAnchors(
    widthPx: Float,
    heightPx: Float,
    seatCount: Int,
): TableAnchors {
    val cx = widthPx / 2f
    val cy = heightPx / 2f
    val rx = widthPx * 0.36f
    val ry = heightPx * 0.34f

    val n = seatCount.coerceAtLeast(1)
    val seats = HashMap<SeatId, Offset>(n)
    // Seat 0 (human) sits at the bottom (angle = +90°, i.e. PI/2 from centre downward).
    // Remaining seats fan clockwise across the upper arc.
    for (i in 0 until n) {
        val frac = i.toFloat() / n.toFloat()
        // Start at the bottom and sweep around the ellipse.
        val angle = (PI / 2.0) + frac * 2.0 * PI
        val x = cx + rx * cos(angle).toFloat()
        val y = cy + ry * sin(angle).toFloat()
        seats[i] = Offset(x, y)
    }

    return TableAnchors(
        seatCenters = seats,
        treasuryCenter = Offset(cx, cy),
    )
}

// ─────────────────────────── Overlay layer ───────────────────────────────────

/**
 * Mounts the [ActionMomentOverlay] for one game-table Box and fires moments as new engine
 * events arrive. Call this INSIDE the felt-table [Box], after the felt surface content but
 * before the action dock, so the dock stays tappable.
 *
 * The overlay fills the box. A [LaunchedEffect] diffs [GameUiState.recentEvents] (a capped,
 * sliding window) against the previously-seen list and plays the newly-appended suffix in order.
 */
@Composable
internal fun BoxScope.GameMomentLayer(
    state: GameUiState,
    widthPx: Float,
    heightPx: Float,
    soundEnabled: Boolean,
    reducedMotion: Boolean,
    humanDisplayName: String = "Khiladi",
    modifier: Modifier = Modifier,
) {
    val host = remember { MomentHost() }
    val resolver = rememberSeatIdResolver(state)
    // Proportional ellipse is now only the FALLBACK used before real measurement lands.
    val fallback =
        remember(widthPx, heightPx, resolver.seatCount) {
            proportionalAnchors(widthPx, heightPx, resolver.seatCount)
        }
    // MEASURED geometry: every opponent plate, the human hand and the treasury medallion
    // report their on-screen bounds into this registry (via Modifier.reportAnchor). We
    // rebase those into the overlay's local space so coin-trails / stamps / turn-handoffs
    // fire at the REAL seat. Until a slot is measured, that slot uses the ellipse fallback.
    val registry = LocalTableAnchorRegistry.current
    val anchors = registry.measuredAnchors(resolver.seatCount, fallback) ?: fallback
    // Platform haptic sink (SFX now goes through gaddiSoundPlayer below), released on dispose.
    // Only actually emits when soundEnabled is true (the overlay applies the gate per-moment).
    val soundPlayer = rememberSoundPlayer()
    // Fine-grained CC0 SFX player (docs/experience-assets.md §3) — fires on the SAME GameEvents
    // that drive the moment theatre below, gated by [soundEnabled] at the call site so the
    // screenshot/test harness (which passes soundEnabled = false) stays silent.
    val gaddiSoundPlayer = rememberGaddiSoundPlayer()

    // Track the last list of events we've already turned into moments. The window slides,
    // so we diff by finding the newly-appended suffix rather than trusting a raw count.
    var seen by remember { mutableStateOf<List<GameEvent>>(emptyList()) }

    LaunchedEffect(state.recentEvents) {
        val current = state.recentEvents
        val fresh = newlyAppended(previous = seen, current = current)
        seen = current
        fresh.forEach { event ->
            mapEventToMoment(event, resolver, state, humanDisplayName)?.let { host.play(it) }
            if (soundEnabled) event.toGaddiSound()?.let { gaddiSoundPlayer.play(it) }
        }
    }

    ActionMomentOverlay(
        host = host,
        anchors = anchors,
        reducedMotion = reducedMotion,
        soundEnabled = soundEnabled,
        soundPlayer = soundPlayer,
        // Report the overlay's own root bounds — the frame the registry rebases every
        // measured seat/hand/treasury anchor against (root coords → overlay-local coords).
        modifier = modifier.reportOverlayBounds(),
    )
}

/**
 * Returns the suffix of [current] that was appended since [previous].
 *
 * [GameUiState.recentEvents] is a sliding window (takeLast). The newly-appended events are
 * everything after the longest overlap where a prefix of [current] is a suffix of [previous].
 * Robust both while the window is still filling and once it has saturated and begun sliding.
 */
internal fun newlyAppended(
    previous: List<GameEvent>,
    current: List<GameEvent>,
): List<GameEvent> {
    if (previous.isEmpty()) return current
    if (current.isEmpty()) return emptyList()

    // Largest k such that current.take(k) == previous.takeLast(k) (i.e. the windows overlap).
    val maxK = minOf(previous.size, current.size)
    for (k in maxK downTo 1) {
        var match = true
        for (j in 0 until k) {
            if (current[j] != previous[previous.size - k + j]) {
                match = false
                break
            }
        }
        if (match) return current.drop(k)
    }
    // No overlap at all → treat the whole current window as new.
    return current
}
