package com.kursi.shared.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.feature.game.HubPhase
import com.kursi.feature.game.LobbyState
import com.kursi.feature.game.OnlineHubUiState
import com.kursi.shared.strings.LocalGaddiStrings
import com.siddharth.kmp.network.LanHost
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.a11y_lan_host_row
import kursi.core.designsystem.generated.resources.cta_join_short
import kursi.core.designsystem.generated.resources.game_you_monogram
import kursi.core.designsystem.generated.resources.label_boli_stamp
import kursi.core.designsystem.generated.resources.label_muhar_stamp
import org.jetbrains.compose.resources.stringResource

/**
 * S-ONLINE — ONLINE MEHFIL hub (Sarkari Noir): the front desk where a player hosts a PRIVATE
 * kamra, JOINs by code, finds a public QUICK-MATCH, or BROWSEs the LAN. A server-address field
 * (default localhost for desktop testing) sits at the top; below it the four requisition modes,
 * rebuilt as engraved sections + hairline lists resting on the lit ground — no bordered panels.
 * When a connection opens the screen flips to a WAITING ROOM (who has joined + the share code)
 * until the match starts.
 *
 * Pure renderer over [OnlineHubUiState] — every action is a callback the app layer wires to the
 * `OnlineHubController` in `:feature:game`. The controller owns all the networking.
 */
@Composable
fun OnlineHubScreen(
    state: OnlineHubUiState,
    onBack: () -> Unit,
    onCreatePrivate: (host: String, port: Int, players: Int) -> Unit,
    onJoinByCode: (host: String, port: Int, code: String) -> Unit,
    onQuickMatch: (host: String, port: Int, players: Int) -> Unit,
    onStartLanBrowse: () -> Unit,
    onStopLanBrowse: () -> Unit,
    onJoinLanHost: (LanHost) -> Unit,
    onLeaveLobby: () -> Unit,
    modifier: Modifier = Modifier,
    initialHost: String = "localhost",
    initialPort: Int = 8080,
) {
    val s = LocalGaddiStrings.current

    var serverField by remember { mutableStateOf(if (initialPort == 8080) initialHost else "$initialHost:$initialPort") }
    var joinCode by remember { mutableStateOf("") }
    var playerCount by remember { mutableIntStateOf(4) }

    fun parsedHost(): String = serverField.substringBefore(':').trim().ifEmpty { "localhost" }

    fun parsedPort(): Int = serverField.substringAfter(':', "").trim().toIntOrNull() ?: 8080

    Column(modifier = modifier.fillMaxSize().litGround()) {
        EngravedNavHeader(
            title = s.onlineHubHeader,
            onBack = onBack,
            backLabel = s.back,
            modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
        )

        // The lobby (waiting room) takes over the body once a connection opens.
        if (state.phase == HubPhase.Lobby && state.lobby != null) {
            WaitingRoom(
                lobby = state.lobby!!,
                onLeave = onLeaveLobby,
                modifier = Modifier.weight(1f),
            )
        } else {
            val scroll = rememberScrollState()
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
                    modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // Status pip (error / working) — an accent dot + text, never a bordered banner.
                    state.error?.let { err -> StatusPip(text = err, color = BrandTokens.StampRed, assertive = true) }
                    val working = state.phase == HubPhase.Working
                    if (working) StatusPip(text = s.onlineWorking, color = BrandTokens.PendingAmber, italic = true)

                    // Server address (default localhost for desktop testing).
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        EngravedHeader(eyebrow = s.onlineHubServerLabel)
                        Text(
                            s.onlineHubServerHint,
                            style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                            color = GaddiNeutrals.TextSecondary,
                        )
                        EngravedField(
                            value = serverField,
                            onValueChange = { serverField = it },
                            placeholder = s.onlineHubServerHint,
                            singleLine = true,
                        )
                    }

                    // Player count (used by CREATE + QUICK-MATCH).
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        EngravedHeader(eyebrow = s.onlinePlayersLabel)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("2", style = GaddiType.caption, color = GaddiNeutrals.TextMuted)
                            Text(
                                "⊙ $playerCount",
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

                    // ── CREATE PRIVATE ROOM (hero) ──
                    StampButton(
                        label = s.onlineCreateLabel,
                        sublabel = s.onlineCreateSub,
                        style = StampStyle.Primary,
                        enabled = !working,
                        onClick = { onCreatePrivate(parsedHost(), parsedPort(), playerCount) },
                        modifier = Modifier.fillMaxWidth(),
                        trailing = { StampTag(text = stringResource(Res.string.label_muhar_stamp), dark = true) },
                    )

                    // ── JOIN BY CODE ──
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        EngravedHeader(eyebrow = s.onlineJoinLabel)
                        Text(
                            s.onlineJoinSub,
                            style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                            color = GaddiNeutrals.TextSecondary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Box(Modifier.weight(1f)) {
                                EngravedField(
                                    value = joinCode,
                                    onValueChange = { joinCode = it.uppercase().take(8) },
                                    placeholder = s.onlineJoinCodeHint,
                                    singleLine = true,
                                    capitalization = KeyboardCapitalization.Characters,
                                    monospace = true,
                                )
                            }
                            StampButton(
                                label = s.onlineJoinCta,
                                enabled = !working && joinCode.isNotBlank(),
                                onClick = { onJoinByCode(parsedHost(), parsedPort(), joinCode) },
                                style = StampStyle.Secondary,
                            )
                        }
                    }

                    // ── QUICK-MATCH (public) ──
                    StampButton(
                        label = s.onlineQuickLabel,
                        sublabel = s.onlineQuickSub,
                        style = StampStyle.Secondary,
                        enabled = !working,
                        onClick = { onQuickMatch(parsedHost(), parsedPort(), playerCount) },
                        modifier = Modifier.fillMaxWidth(),
                        trailing = { StampTag(text = stringResource(Res.string.label_boli_stamp), dark = false) },
                    )

                    // ── LAN BROWSE ──
                    LanBrowseSection(
                        browsing = state.lanBrowsing,
                        hosts = state.lanHosts,
                        onBrowse = onStartLanBrowse,
                        onStop = onStopLanBrowse,
                        onJoin = onJoinLanHost,
                        enabled = !working,
                    )

                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }

    // Stop LAN discovery when this screen leaves composition (back / route change).
    DisposableEffect(Unit) {
        onDispose { onStopLanBrowse() }
    }
}

// ─────────────────────────── Waiting room ───────────────────────────

@Composable
private fun WaitingRoom(
    lobby: LobbyState,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalGaddiStrings.current
    val statusText =
        when {
            lobby.isLost -> s.onlineLobbyLost
            lobby.isWaiting -> s.onlineLobbyWaiting
            else -> s.onlineLobbyConnecting
        }
    val statusColor =
        when {
            lobby.isLost -> BrandTokens.StampRed
            lobby.isWaiting -> GaddiSemantics.Success
            else -> BrandTokens.PendingAmber
        }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                s.onlineLobbyHeader,
                style = GaddiType.display.rozha().copy(fontSize = 20.sp, letterSpacing = 1.sp),
                color = BrandTokens.GoldAntique,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // Status pip + text (live region so a screen reader announces connect → waiting).
            StatusPip(text = statusText, color = statusColor, assertive = lobby.isLost)

            // Share-code plaque — a raised lit surface (shadow + gradient), the one focal point
            // of the waiting room. No outline border framing it (non-negotiable #1).
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .shadow(14.dp, Squircle(GaddiRadii.lg), clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                        .clip(Squircle(GaddiRadii.lg))
                        .background(
                            Brush.verticalGradient(
                                listOf(BrandTokens.BrassAged.copy(alpha = 0.9f), BrandTokens.TeakDark),
                            ),
                        ).padding(24.dp)
                        .semantics { contentDescription = "${s.onlineLobbyShareLabel}: ${lobby.code}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    s.onlineLobbyShareLabel,
                    style = GaddiType.label_sm.dmMono().copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                    color = BrandTokens.TeakInk,
                )
                Text(
                    lobby.code,
                    style = GaddiType.numeric.copy(fontSize = 38.sp, letterSpacing = 9.sp),
                    color = BrandTokens.TeakInk,
                )
                Text(
                    "${lobby.host}:${lobby.port}",
                    style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                    color = BrandTokens.TeakInk.copy(alpha = 0.7f),
                )
            }

            // Roster line — seated count + my seat confirmation.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    s.onlineLobbyRoster(lobby.joinedSeats, lobby.seatCount),
                    style = GaddiType.title.copy(fontSize = 15.sp),
                    color = GaddiNeutrals.TextPrimary,
                )
                lobby.mySeat?.let { seat ->
                    Text(
                        s.onlineLobbySeated(seat),
                        style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                        color = BrandTokens.GoldAntique,
                    )
                }
            }

            // Seat tokens — brass-rimmed discs, the same avatar idiom as the rest of the app.
            if (lobby.seatCount in 2..10) {
                SeatTokens(joined = lobby.joinedSeats, total = lobby.seatCount, mySeat = lobby.mySeat)
            }

            Spacer(Modifier.height(4.dp))

            StampButton(
                label = s.onlineLobbyLeave,
                onClick = onLeave,
                style = StampStyle.Ghost,
                modifier =
                    Modifier.semantics {
                        role = Role.Button
                        contentDescription = s.onlineLobbyLeave
                    },
            )
        }
    }
}

@Composable
private fun SeatTokens(
    joined: Int,
    total: Int,
    mySeat: Int?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.weight(1f))
        repeat(total) { i ->
            val filled = i < joined
            val isMe = mySeat == i
            BrassToken(
                monogram =
                    if (isMe) {
                        stringResource(Res.string.game_you_monogram)
                    } else if (filled) {
                        "✓"
                    } else {
                        "${i + 1}"
                    },
                fill =
                    when {
                        isMe -> BrandTokens.GoldAntique
                        filled -> BrandTokens.BrassAged.copy(alpha = 0.7f)
                        else -> BrandTokens.TeakDark
                    },
                size = 32.dp,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

// ─────────────────────────── LAN browse ───────────────────────────

@Composable
private fun LanBrowseSection(
    browsing: Boolean,
    hosts: List<LanHost>,
    onBrowse: () -> Unit,
    onStop: () -> Unit,
    onJoin: (LanHost) -> Unit,
    enabled: Boolean,
) {
    val s = LocalGaddiStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EngravedHeader(eyebrow = s.onlineLanLabel)
        Text(
            s.onlineLanSub,
            style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
            color = GaddiNeutrals.TextSecondary,
        )
        StampButton(
            label = if (browsing) s.onlineLanSearching else s.onlineLanBrowseCta,
            enabled = enabled,
            onClick = { if (browsing) onStop() else onBrowse() },
            style = StampStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        if (hosts.isEmpty()) {
            Text(
                if (browsing) s.onlineLanSearching else s.onlineLanEmpty,
                style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                color = GaddiNeutrals.TextMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        } else {
            Column {
                hosts.forEachIndexed { i, h ->
                    LanHostRow(h, onJoin = { onJoin(h) }, showDivider = i != hosts.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun LanHostRow(
    host: LanHost,
    onJoin: () -> Unit,
    showDivider: Boolean,
) {
    val lanHostDesc = stringResource(Res.string.a11y_lan_host_row, host.name, host.host, host.port, host.payload)
    HairlineRow(
        onClick = onJoin,
        showDivider = showDivider,
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = lanHostDesc
            },
    ) {
        BrassToken(monogram = host.name, fill = BrandTokens.BrassAged.copy(alpha = 0.7f), size = 38.dp)
        Column(Modifier.weight(1f)) {
            Text(
                host.name,
                style = GaddiType.name.copy(fontSize = 13.sp),
                color = GaddiNeutrals.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${host.host}:${host.port} · ${host.payload}",
                style = GaddiType.caption.copy(fontSize = 9.sp),
                color = GaddiNeutrals.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stringResource(Res.string.cta_join_short),
            style = GaddiType.label_sm.dmMono().copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = BrandTokens.GoldAntique,
        )
    }
}

// ─────────────────────────── Shared primitives ───────────────────────────

/** A small crafted stamp badge — the same tag idiom as "APPROVED" / "FORM 1 · TAIYAAR" elsewhere. */
@Composable
private fun StampTag(
    text: String,
    dark: Boolean,
) {
    Box(
        modifier =
            Modifier
                .clip(Squircle(GaddiRadii.xs))
                .background(if (dark) BrandTokens.TeakInk.copy(alpha = 0.18f) else BrandTokens.BrassAged.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = GaddiType.label_micro.dmMono().copy(letterSpacing = 1.sp, fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = if (dark) BrandTokens.TeakInk else BrandTokens.GoldAntique,
        )
    }
}

/** Status affordance — a colour-coded dot + text, replacing bordered error/working banners
 *  (non-negotiable #1). The oxblood dot is reserved for the connection-lost / error state
 *  (non-negotiable #7); amber for in-flight, green for the healthy waiting state. */
@Composable
private fun StatusPip(
    text: String,
    color: Color,
    italic: Boolean = false,
    assertive: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier.fillMaxWidth().semantics {
                liveRegion = if (assertive) LiveRegionMode.Assertive else LiveRegionMode.Polite
                contentDescription = text
            },
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(
            text,
            style = GaddiType.body.copy(fontSize = 12.sp, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal),
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
