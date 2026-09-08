package com.kursi.desktop

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kursi.ai.BotMemory
import com.kursi.ai.OpponentInsight
import com.kursi.ai.Policy
import com.kursi.ai.advisor.MoveAdvisor
import com.kursi.ai.persona.BotDifficulty
import com.kursi.ai.persona.PersonaAssigner
import com.kursi.core.network.ConnectionState
import com.kursi.core.prefs.AppPrefs
import com.kursi.core.prefs.DailyStanding
import com.kursi.core.prefs.DecisionLedger
import com.kursi.core.prefs.GauntletProgress
import com.kursi.core.prefs.PersonaRecord
import com.kursi.core.prefs.RankedStanding
import com.kursi.core.prefs.StatsLedger
import com.kursi.designsystem.BrandTokens
import com.kursi.designsystem.KursiRoleHues
import com.kursi.designsystem.KursiTheme
import com.kursi.designsystem.KursiType
import com.kursi.designsystem.moment.ActionMomentOverlay
import com.kursi.designsystem.moment.KursiMoment
import com.kursi.designsystem.moment.MomentHost
import com.kursi.designsystem.moment.TableAnchors
import com.kursi.engine.*
import com.kursi.feature.game.ChitContent
import com.kursi.feature.game.DensityLayer
import com.kursi.feature.game.Difficulty
import com.kursi.feature.game.GamePhase
import com.kursi.feature.game.GameScreen
import com.kursi.feature.game.GameUiState
import com.kursi.feature.game.HubPhase
import com.kursi.feature.game.LobbyKind
import com.kursi.feature.game.LobbyState
import com.kursi.feature.game.OnlineHubUiState
import com.kursi.feature.game.OpponentPersona
import com.kursi.feature.game.coach.riskBluffConf
import com.kursi.feature.game.narrative.ArcId
import com.kursi.feature.game.narrative.ChatActionKind
import com.kursi.feature.game.narrative.ChatKind
import com.kursi.feature.game.narrative.ChatMessage
import com.kursi.feature.game.narrative.ChatSuggestion
import com.kursi.feature.game.narrative.MessageTone
import com.kursi.shared.nav.MatchDecisionSummary
import com.kursi.shared.nav.MatchSummary
import com.kursi.shared.screen.CareerScreen
import com.kursi.shared.screen.GauntletScreen
import com.kursi.shared.screen.HomeScreen
import com.kursi.shared.screen.LeaderboardScreen
import com.kursi.shared.screen.LobbyScreen
import com.kursi.shared.screen.OnlineHubScreen
import com.kursi.shared.screen.OnlineStandingRow
import com.kursi.shared.screen.OnlineStandings
import com.kursi.shared.screen.ProfileSetupScreen
import com.kursi.shared.screen.RecentMatchesList
import com.kursi.shared.screen.ResultsScreen
import com.kursi.shared.screen.ReviewScreen
import com.kursi.shared.screen.SettingsScreen
import com.kursi.shared.screen.SetupScreen
import com.kursi.shared.screen.StoryScreen
import com.kursi.shared.screen.TutorialScreen
import com.siddharth.kmp.network.LanHost
import kotlinx.coroutines.runBlocking
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
//  Headless screenshot harness — renders GameScreen to PNG files via
//  ImageComposeScene (Compose Desktop off-screen renderer).
//
//  Run: ./gradlew :cmp-desktop:renderScreens --console=plain
//  Output: cmp-desktop/build/shots/*.png
// ─────────────────────────────────────────────────────────────────────────────

fun main() {
    val outDir = File(System.getProperty("kursi.shots.dir", "build/shots")).also { it.mkdirs() }

    val shots = buildFixtures()
    println("Rendering ${shots.size} screenshot(s) to ${outDir.absolutePath}")

    for ((name, state, lp) in shots) {
        renderToPng(state, outDir, name, lp)
        val file = File(outDir, "$name.png")
        println("  wrote ${file.name}  (${file.length()} bytes)")
    }

    // Phone-width FOCUS render — exercises PhoneLayout (below the 840.dp breakpoint) so the primary
    // mobile composition is captured, not just DesktopLayout at 1440px.
    val (phoneFocusBase, _) = buildMidClaimState()
    renderToPng(
        phoneFocusBase.copy(densityLayer = DensityLayer.FOCUS),
        outDir,
        "4p_focus_phone",
        width = 400,
        height = 880,
        density = 3f,
    )
    println("  wrote 4p_focus_phone.png")

    // 4p_coach_action_phone — reuse the ANALYST coach-action fixture already built in buildFixtures()
    // rather than re-running its seed search, just at phone dimensions.
    shots.firstOrNull { it.first == "4p_coach_action" }?.let { (_, state, lp) ->
        renderToPng(state, outDir, "4p_coach_action_phone", lp, width = 400, height = 880, density = 3f)
        println("  wrote 4p_coach_action_phone.png")
    }

    // ── Chit-OPEN shots: verify the long-press inspect chits render rich + fit ──
    // Reuse the mid-claim state (human turn, opponents with standing claims).
    val (chitState, _) = buildMidClaimState()
    val p1 = PlayerId(1)
    val p1Persona = chitState.opponentPersonas[p1]
    val p1Id = p1Persona?.name?.lowercase()?.replace(" ", "_") ?: ""
    val voice =
        com.kursi.feature.game
            .KursiVoice(com.kursi.feature.game.Language.ENGLISH)

    fun actionLabel(a: Action): String =
        when (a) {
            Action.Income -> "DEHAADI"
            Action.ForeignAid -> "FDI"
            Action.Tax -> "GHOTALA"
            Action.Exchange -> "SETTING"
            is Action.Steal -> "VASOOLI"
            is Action.Investigate -> "JAANCH"
            is Action.Assassinate -> "SUPARI"
            is Action.Coup -> "KHELA"
            Action.BailPe -> "BAIL PE"
            Action.Sabotage -> "BALI KHEL"
            is Action.Hawala -> "HAWALA"
            Action.Emergency -> "ADHYADESH"
        }
    val legalAgainstP1 =
        chitState.legalIntents
            .filterIsInstance<Intent.DeclareAction>()
            .filter { Rules.targetOf(it.action) == p1 }
            .map { actionLabel(it.action) }
            .distinct()

    val p1Intel =
        chitState.insightFor(p1)?.let {
            com.kursi.feature.game.DossierIntel
                .from(it)
        }
    renderToPng(
        chitState,
        outDir,
        "4p_chit_dossier",
        null,
        initialChit =
            ChitContent.Dossier(
                opponentName = p1Persona?.name ?: "P1",
                opponentCoins =
                    chitState.view.players
                        .first { it.id == p1 }
                        .coins,
                faceDownCount =
                    chitState.view.players
                        .first { it.id == p1 }
                        .faceDownCount,
                faceUpRoles =
                    chitState.view.players
                        .first { it.id == p1 }
                        .faceUpRoles,
                lastActionText = "GHOTALA",
                personaDossierLine = voice.personaBio(p1Id),
                personaRivalryLine = voice.personaRivalry(p1Id),
                legalMovesAgainst = legalAgainstP1,
                intel = p1Intel,
            ),
    )
    println("  wrote 4p_chit_dossier.png")

    renderToPng(
        chitState,
        outDir,
        "4p_chit_risk",
        null,
        initialChit =
            ChitContent.RiskAction(
                action = Action.Tax,
                myCoins = chitState.view.myCoins,
                bluffConf = riskBluffConf(Action.Tax, chitState),
            ),
    )
    println("  wrote 4p_chit_risk.png")

    // ── Home screen ── entrance animations gated on LaunchedEffect; needs 2-frame pump.
    // home.png — fresh install, all 8 mode tiles visible, seal + persona on right panel.
    renderComposableAnimated(outDir, "home") {
        HomeScreen(onNewGame = {}, onGazette = {}, onSettings = {}, onOnlineTap = {}, launchIndex = 3)
    }
    println("  wrote home.png")

    // Phone-portrait — portfolio device-wall capture (see 4p_focus_phone above for the pattern).
    renderComposableAnimated(outDir, "home_phone", width = 400, height = 880, density = 3f) {
        HomeScreen(onNewGame = {}, onGazette = {}, onSettings = {}, onOnlineTap = {}, launchIndex = 3)
    }
    println("  wrote home_phone.png")

    // home_mode_gauntlet.png — GAUNTLET tile pre-selected; right panel shows description + ENTER.
    renderComposableAnimated(outDir, "home_mode_gauntlet") {
        HomeScreen(
            onNewGame = {},
            onGazette = {},
            onSettings = {},
            onOnlineTap = {},
            launchIndex = 1,
            gauntlet = GauntletProgress(clearedRung = 1, wins = 7),
            gauntletRungCount = 5,
            initialSelectedKey = "gauntlet",
        )
    }
    println("  wrote home_mode_gauntlet.png")

    // home_mode_story.png — KISSA tile pre-selected; right panel shows KISSA description.
    renderComposableAnimated(outDir, "home_mode_story") {
        HomeScreen(
            onNewGame = {},
            onGazette = {},
            onSettings = {},
            onOnlineTap = {},
            launchIndex = 7,
            initialSelectedKey = "story",
        )
    }
    println("  wrote home_mode_story.png")

    // M6a: the Niyam Gazette — DARBAR (roles) tab now includes the 6th role, PATRAKAAR.
    // Dialog/Popup content needs a few pumped frames to settle (same reasoning as
    // renderComposableAnimated's other animated-entrance fixtures) — a single-frame
    // capture caught the popup mid-fade, rendering the whole gazette at ~20% opacity.
    renderComposableAnimated(outDir, "gazette_roles") {
        com.kursi.feature.game
            .NiyamGazette(onDismiss = {}, onReplayPrimer = {}, initialTab = 0)
    }
    println("  wrote gazette_roles.png")

    renderComposableAnimated(outDir, "gazette_roles_phone", width = 400, height = 880, density = 3f) {
        com.kursi.feature.game
            .NiyamGazette(onDismiss = {}, onReplayPrimer = {}, initialTab = 0)
    }
    println("  wrote gazette_roles_phone.png")

    renderComposable(outDir, "setup") {
        SetupScreen(
            onBack = {},
            onNext = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
            initialPlayers = 4,
            initialDifficulty = Difficulty.Medium,
        )
    }
    println("  wrote setup.png")

    renderComposable(outDir, "setup_phone", width = 400, height = 880, density = 3f) {
        SetupScreen(
            onBack = {},
            onNext = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
            initialPlayers = 4,
            initialDifficulty = Difficulty.Medium,
        )
    }
    println("  wrote setup_phone.png")

    // ── M6e GAUNTLET — the escalating promotion ladder (mid-run: rungs 0-1 cleared) ──
    renderComposable(outDir, "gauntlet") {
        GauntletScreen(
            progress =
                com.kursi.core.prefs
                    .GauntletProgress(clearedRung = 1, wins = 3),
            onPlayRung = {},
            onBack = {},
        )
    }
    println("  wrote gauntlet.png")

    // ── M6e TEAM KHEL — Setup with the Team Khel toggle ON, scrolled into view ──
    // The 1-D Team Khel row sits below the fold, so a single static frame of the default form shows
    // only the play-mode options. We render the form with the toggle seeded ON and an owned
    // ScrollState, pump a first frame to lay out (so scrollState.maxValue is known), scroll the body
    // to the bottom (where the team row + footer live), then render the second frame for capture.
    renderTeamSetup(outDir)
    println("  wrote setup_teams.png")

    // ── M6e TEAM KHEL — the in-game table with team badges on the plates ──
    run {
        val (state, _) = buildTeamTableState()
        renderToPng(state, outDir, "team_table", null)
        println("  wrote team_table.png")
    }

    // ── M6e TAMASHA / DEMO — the watch-only spectator banner on a live table ──
    run {
        val (state, _) = buildMidClaimState()
        renderToPng(state, outDir, "spectator_demo", null, spectator = true)
        println("  wrote spectator_demo.png")
    }

    // ── DARBAR — the KISSA story-mode hub ──
    renderComposable(outDir, "story") {
        StoryScreen(onBack = {}, onStart = { _, _, _, _ -> })
    }
    println("  wrote story.png")

    // ── DARBAR — a live narrative table: bot speech bubbles + the Darbar toggle with unread ──
    run {
        val (base, _) = buildMidClaimState()
        // A snapshot of an AFWAAH arc unfolding against seat 2 (Babu), with the table piling on.
        val feed =
            listOf(
                ChatMessage(
                    1,
                    senderSeat = 0,
                    targetSeat = 2,
                    body = "Suna? Babu ke paas Patrakaar chhupa hai. Sambhal ke.",
                    tone = MessageTone.SLY,
                    kind = ChatKind.ARC,
                    arc = ArcId.AFWAAH,
                    fromPlayer = true,
                ),
                ChatMessage(
                    2,
                    senderSeat = -1,
                    targetSeat = 2,
                    body = "Darbar mein khusur-phusur shuru… Babu ke khilaaf.",
                    tone = MessageTone.SYSTEM,
                    kind = ChatKind.SYSTEM,
                    arc = ArcId.AFWAAH,
                ),
                ChatMessage(
                    3,
                    senderSeat = 1,
                    targetSeat = 2,
                    body = "Toh yeh baat hai! Main pehle hi maarunga.",
                    tone = MessageTone.HOSTILE,
                    kind = ChatKind.TABLE,
                ),
                ChatMessage(
                    4,
                    senderSeat = 2,
                    body = "Mere against kyun? Maine toh sirf file rok rakhi thi.",
                    tone = MessageTone.PANICKED,
                    kind = ChatKind.TABLE,
                ),
                ChatMessage(5, senderSeat = 3, targetSeat = 2, body = "Babu pehle. Baaki baad mein.", tone = MessageTone.HOSTILE, kind = ChatKind.TABLE),
            )
        val suggestions =
            listOf(
                ChatSuggestion(
                    "start.gathbandhan.1",
                    "Gathbandhan: Bhai Teja",
                    ChatActionKind.ARC_START,
                    ArcId.GATHBANDHAN,
                    1,
                    "Bhai Teja",
                    "Secret pact — coordinate, then betray",
                ),
                ChatSuggestion("afwaah.fuel.2", "Aur hawa do", ChatActionKind.ARC_REPLY, ArcId.AFWAAH, 2, "Babu", "Twist the knife — more heat on Babu"),
                ChatSuggestion("talk.taunt.3", "Taunt Jugaadu", ChatActionKind.TAUNT, null, 3, "Jugaadu", "Heat them up"),
            )
        val narrativeState =
            base.copy(
                narrativeEnabled = true,
                chatFeed = feed,
                chatSuggestions = suggestions,
                activeArcs = listOf(ArcId.AFWAAH),
                unreadChat = 3,
            )
        renderToPng(narrativeState, outDir, "darbar_table", null, initialDarbarOpen = true)
        println("  wrote darbar_table.png")

        renderToPng(narrativeState, outDir, "darbar_table_phone", null, initialDarbarOpen = true, width = 400, height = 880, density = 3f)
        println("  wrote darbar_table_phone.png")
    }

    // ── M5 ONBOARD: interactive tutorial (Pehli Hazri) ──
    // Beat 1 — the teaching intro over the scripted table.
    renderComposable(outDir, "tutorial_intro") {
        TutorialScreen(onDone = {}, initialStep = 0)
    }
    println("  wrote tutorial_intro.png")

    // Beat 7 (index 6) — the GUARANTEED bluff-caught teaching moment: the bluffed NETA claim is
    // flipped face-up to JHOOTH (it was BHAI), the challenge banner reads the verdict, and the card
    // wears the red stamp. This is the spine of the lesson.
    renderComposable(outDir, "tutorial_bluff_caught") {
        TutorialScreen(onDone = {}, initialStep = 6)
    }
    println("  wrote tutorial_bluff_caught.png")

    // Beats 8-10 (indices 7-9) — the guided-funnel mechanic-at-a-time beats added per spec §6: BLOCK
    // (VAKIL stops a SUPARI), COUP (KHELA — unblockable), EXCHANGE (SETTING). Each renders in its
    // un-acted "prompting" state, matching tutorial_intro's before-interaction convention.
    renderComposable(outDir, "tutorial_block") {
        TutorialScreen(onDone = {}, initialStep = 7)
    }
    println("  wrote tutorial_block.png")

    renderComposable(outDir, "tutorial_coup") {
        TutorialScreen(onDone = {}, initialStep = 8)
    }
    println("  wrote tutorial_coup.png")

    renderComposable(outDir, "tutorial_coup_phone", width = 400, height = 880, density = 3f) {
        TutorialScreen(onDone = {}, initialStep = 8)
    }
    println("  wrote tutorial_coup_phone.png")

    renderComposable(outDir, "tutorial_exchange") {
        TutorialScreen(onDone = {}, initialStep = 9)
    }
    println("  wrote tutorial_exchange.png")

    // Lobby derives a real deterministic roster internally via PersonaAssigner.assign(seed, ...).
    // We pass a concrete seed/players/difficulty so the Hazri Register renders a full house.
    renderComposable(outDir, "lobby") {
        LobbyScreen(
            seed = 42L,
            players = 6,
            difficulty = Difficulty.Hard,
            onBack = {},
            onDealIn = { _, _, _ -> },
        )
    }
    println("  wrote lobby.png")

    // Results: a sample MatchSummary read from the real nav model shape — human-won verdict.
    renderComposable(outDir, "results") {
        ResultsScreen(
            summary = sampleMatchSummary(),
            onRematch = {},
            onNewGame = {},
            onHome = {},
            onShare = {},
        )
    }
    println("  wrote results.png")

    renderComposable(outDir, "results_phone", width = 400, height = 880, density = 3f) {
        ResultsScreen(
            summary = sampleMatchSummary(),
            onRematch = {},
            onNewGame = {},
            onHome = {},
            onShare = {},
        )
    }
    println("  wrote results_phone.png")

    renderComposable(outDir, "settings") {
        SettingsScreen(
            prefs = AppPrefs(),
            onBack = {},
            onReplayPrimer = {},
            onGazette = {},
        )
    }
    println("  wrote settings.png")

    renderComposable(outDir, "settings_phone", width = 400, height = 880, density = 3f) {
        SettingsScreen(
            prefs = AppPrefs(),
            onBack = {},
            onReplayPrimer = {},
            onGazette = {},
        )
    }
    println("  wrote settings_phone.png")

    // ── PEHLI HAZRI — first-run profile setup (name, avatar, seat colour) ──
    // Seeded with a populated identity so the live-preview monogram + selected avatar/swatch
    // render filled-in rather than the blank default state.
    renderComposable(outDir, "profile_setup") {
        val prefs =
            AppPrefs().apply {
                playerName = "Siddharth"
                playerAvatarIdx = 1
                playerColorArgb = 0xFFE63946L
            }
        ProfileSetupScreen(prefs = prefs, onDone = {})
    }
    println("  wrote profile_setup.png")

    // M3 §4 — honest "record expired" empty state (MatchSummary cache miss → null).
    renderComposable(outDir, "results_expired") {
        ResultsScreen(summary = null, onRematch = {}, onNewGame = {}, onHome = {})
    }
    println("  wrote results_expired.png")

    // M3 §3 — career / Roznamcha register with a sample populated ledger.
    renderComposable(outDir, "career") {
        CareerScreen(
            ledger =
                StatsLedger(
                    games = 14,
                    wins = 9,
                    bluffsHeld = 21,
                    bluffsCaught = 6,
                    headToHead =
                        mapOf(
                            "netaji_vachan" to PersonaRecord(played = 8, wins = 5),
                            "bhai_teja" to PersonaRecord(played = 6, wins = 2),
                            "babu_filewala" to PersonaRecord(played = 5, wins = 4),
                        ),
                ),
            // M6b: a populated decision-quality dossier (grades SHARP per the tiering).
            decisionLedger =
                DecisionLedger(
                    decisions = 168,
                    matchedBest = 121, // ~72% best-move match
                    evLostMilli = 6720L, // ~4% avg win-prob bled (6720 / 1000 / 168)
                    challenges = 24,
                    challengesGood = 17, // ~71% challenge accuracy
                    bluffsTried = 31,
                    bluffsOk = 22, // ~71% bluff success
                ),
            // M6d — ranked standing surfaced at the top of the career file.
            ranked = sampleRanked(),
            onBack = {},
        )
    }
    println("  wrote career.png")

    renderComposable(outDir, "career_phone", width = 400, height = 880, density = 3f) {
        CareerScreen(
            ledger =
                StatsLedger(
                    games = 14,
                    wins = 9,
                    bluffsHeld = 21,
                    bluffsCaught = 6,
                    headToHead =
                        mapOf(
                            "netaji_vachan" to PersonaRecord(played = 8, wins = 5),
                            "bhai_teja" to PersonaRecord(played = 6, wins = 2),
                            "babu_filewala" to PersonaRecord(played = 5, wins = 4),
                        ),
                ),
            decisionLedger =
                DecisionLedger(
                    decisions = 168,
                    matchedBest = 121,
                    evLostMilli = 6720L,
                    challenges = 24,
                    challengesGood = 17,
                    bluffsTried = 31,
                    bluffsOk = 22,
                ),
            ranked = sampleRanked(),
            onBack = {},
        )
    }
    println("  wrote career_phone.png")

    // M6d §1+§3 — the local leaderboard / standings: rank plaque + rating spark-line + daily streak.
    renderComposable(outDir, "leaderboard") {
        LeaderboardScreen(
            ranked = sampleRanked(),
            daily = DailyStanding(lastDay = 20_001L, lastWon = true, streak = 5, bestStreak = 9, played = 22, won = 14),
            onBack = {},
        )
    }
    println("  wrote leaderboard.png")

    // M7 — leaderboard with the live ONLINE DARJA-SUCHI seam connected (server-backed rows).
    renderComposable(outDir, "leaderboard_online") {
        LeaderboardScreen(
            ranked = sampleRanked(),
            daily = DailyStanding(lastDay = 20_001L, lastWon = true, streak = 5, bestStreak = 9, played = 22, won = 14),
            onBack = {},
            onlineStandings =
                OnlineStandings(
                    connected = true,
                    rows =
                        listOf(
                            OnlineStandingRow(1, "Netaji Vachan", 1342),
                            OnlineStandingRow(2, "Aap", 1185, isMe = true),
                            OnlineStandingRow(3, "Madam Sarpanch", 1120),
                            OnlineStandingRow(4, "Seth Khokhawala", 1071),
                        ),
                ),
        )
    }
    println("  wrote leaderboard_online.png")

    // ── M7 ONLINE HUB: create / join / quick-match / LAN-browse mode picker ──
    renderComposable(outDir, "online_hub") {
        OnlineHubScreen(
            state = OnlineHubUiState(phase = HubPhase.Idle),
            onBack = {},
            onCreatePrivate = { _, _, _ -> },
            onJoinByCode = { _, _, _ -> },
            onQuickMatch = { _, _, _ -> },
            onStartLanBrowse = {},
            onStopLanBrowse = {},
            onJoinLanHost = {},
            onLeaveLobby = {},
        )
    }
    println("  wrote online_hub.png")

    // ── M7 ONLINE HUB: LAN browse populated with discovered hosts ──
    renderComposable(outDir, "online_hub_lan") {
        OnlineHubScreen(
            state =
                OnlineHubUiState(
                    phase = HubPhase.Idle,
                    lanBrowsing = true,
                    lanHosts =
                        listOf(
                            LanHost(host = "192.168.1.21", port = 8080, payload = "TEAK", name = "Sid ki mez"),
                            LanHost(host = "192.168.1.34", port = 8080, payload = "BRSS", name = "Daftar #2"),
                        ),
                ),
            onBack = {},
            onCreatePrivate = { _, _, _ -> },
            onJoinByCode = { _, _, _ -> },
            onQuickMatch = { _, _, _ -> },
            onStartLanBrowse = {},
            onStopLanBrowse = {},
            onJoinLanHost = {},
            onLeaveLobby = {},
        )
    }
    println("  wrote online_hub_lan.png")

    // ── M7 ONLINE HUB: waiting room (connected, seated, awaiting players) ──
    renderComposable(outDir, "online_lobby") {
        OnlineHubScreen(
            state =
                OnlineHubUiState(
                    phase = HubPhase.Lobby,
                    lobby =
                        LobbyState(
                            host = "localhost",
                            port = 8080,
                            code = "TEAK",
                            kind = LobbyKind.PrivateHost,
                            seatCount = 4,
                            connection = ConnectionState.Connected(seat = 0),
                            mySeat = 0,
                            joinedSeats = 2,
                        ),
                ),
            onBack = {},
            onCreatePrivate = { _, _, _ -> },
            onJoinByCode = { _, _, _ -> },
            onQuickMatch = { _, _, _ -> },
            onStartLanBrowse = {},
            onStopLanBrowse = {},
            onJoinLanHost = {},
            onLeaveLobby = {},
        )
    }
    println("  wrote online_lobby.png")

    // ── M7 ONLINE HUB: connection-status fixture — reconnect/lost banner in the waiting room ──
    renderComposable(outDir, "online_lobby_lost") {
        OnlineHubScreen(
            state =
                OnlineHubUiState(
                    phase = HubPhase.Lobby,
                    error = null,
                    lobby =
                        LobbyState(
                            host = "10.0.0.7",
                            port = 8080,
                            code = "BRSS",
                            kind = LobbyKind.JoinByCode,
                            seatCount = 4,
                            connection = ConnectionState.Dropped(cause = "socket closed"),
                            mySeat = 1,
                            joinedSeats = 3,
                        ),
                ),
            onBack = {},
            onCreatePrivate = { _, _, _ -> },
            onJoinByCode = { _, _, _ -> },
            onQuickMatch = { _, _, _ -> },
            onStartLanBrowse = {},
            onStopLanBrowse = {},
            onJoinLanHost = {},
            onLeaveLobby = {},
        )
    }
    println("  wrote online_lobby_lost.png")

    // home_ranked.png — ranked strip + daily challenge + career stats + gauntlet progress.
    renderComposableAnimated(outDir, "home_ranked") {
        HomeScreen(
            onNewGame = {},
            onGazette = {},
            onSettings = {},
            onOnlineTap = {},
            launchIndex = 0,
            ranked = sampleRanked(),
            daily = DailyStanding(lastDay = 20_000L, lastWon = true, streak = 5, bestStreak = 9, played = 22, won = 14),
            todayDailyDone = false,
            ledger =
                StatsLedger(
                    games = 14,
                    wins = 9,
                    bluffsHeld = 21,
                    bluffsCaught = 6,
                    headToHead =
                        mapOf(
                            "netaji_vachan" to PersonaRecord(8, 5),
                            "bhai_teja" to PersonaRecord(4, 2),
                        ),
                ),
            gauntlet = GauntletProgress(clearedRung = 2, wins = 9),
            gauntletRungCount = 5,
        )
    }
    println("  wrote home_ranked.png")

    // home_resume.png — shows in-progress match resume strip + all progression data.
    renderComposableAnimated(outDir, "home_resume") {
        HomeScreen(
            onNewGame = {},
            onGazette = {},
            onSettings = {},
            onOnlineTap = {},
            launchIndex = 5,
            resumeLabel = "6-Player Hard · Rung 3 · Turn 14",
            ranked = sampleRanked(),
            daily = DailyStanding(lastDay = 20_001L, lastWon = false, streak = 3, bestStreak = 9, played = 22, won = 14),
            todayDailyDone = false,
            ledger =
                StatsLedger(
                    games = 14,
                    wins = 9,
                    bluffsHeld = 21,
                    bluffsCaught = 6,
                    headToHead =
                        mapOf(
                            "netaji_vachan" to PersonaRecord(8, 5),
                            "jugaadu_chhotu" to PersonaRecord(5, 3),
                        ),
                ),
            gauntlet = GauntletProgress(clearedRung = 1, wins = 5),
            gauntletRungCount = 5,
        )
    }
    println("  wrote home_resume.png")

    // ── M5 pass-and-play handoff guard ──
    // A 2-human pass-and-play state where control rests on human seat 1 ("Khiladi 2"); the harness
    // forces the guard visible so the "pass the device" full-screen occlusion is captured.
    run {
        val (state, _) = buildPassAndPlayHandoffState()
        renderToPng(state, outDir, "passandplay_handoff", null, forceHandoff = true)
        println("  wrote passandplay_handoff.png")
    }

    // ── reduced_motion_frames: per-moment static end-frames (Tenet 6) ──
    // A 3×3 gallery, each cell mounting the ActionMomentOverlay in reducedMotion=true
    // with one queued KursiMoment so its TAILORED frozen frame renders (held stamp,
    // verdict card, tipped chair, KURSI crest, coin-row, etc.) — proving reduced motion
    // is no longer a uniform TickerSlip.
    renderComposable(outDir, "reduced_motion_frames") {
        ReducedMotionGallery()
    }
    println("  wrote reduced_motion_frames.png")

    // ── M6c REVIEW: replay scrubber + advisor annotation, and the recent-matches list ──
    run {
        val record = buildCompletedMatchFixture()
        // Pre-build the deterministic ReplaySession synchronously (the live screen does this off-thread).
        val replay =
            com.kursi.feature.game.session.MatchReplay
                .replaySessionFor(record)
        // Open on the first human decision that carries an annotation (the teach-by-review beat).
        val annotatedStep =
            replay.humanDecisionIndices.firstOrNull { replay.annotationAt(it) != null }
                ?: replay.humanDecisionIndices.firstOrNull()
                ?: 0
        renderComposable(outDir, "review_replay") {
            ReviewScreen(
                match = record,
                language = com.kursi.feature.game.Language.ENGLISH,
                onBack = {},
                prebuilt = replay,
                initialStep = annotatedStep,
            )
        }
        println("  wrote review_replay.png")

        // The recent-matches list (the Career entry point into Review).
        renderComposable(outDir, "review_recent_list") {
            Box(Modifier.fillMaxSize().background(BrandTokens.TeakInk).padding(24.dp)) {
                Box(Modifier.fillMaxWidth(0.6f).align(Alignment.TopCenter)) {
                    RecentMatchesList(
                        matches =
                            listOf(
                                record,
                                record.copy(winnerSeat = 1, seed = record.seed + 7),
                                record.copy(winnerSeat = 0, seed = record.seed + 13, players = 6),
                            ),
                        onReview = {},
                    )
                }
            }
        }
        println("  wrote review_recent_list.png")
    }

    println("Done.")
}

/**
 * Drive a real deterministic [com.kursi.feature.game.session.GameSession] to game-over with a fixed
 * "human" policy, then capture it as a [com.kursi.feature.game.session.CompletedMatch] — exactly the
 * record the live VM persists on game-over. The replay + annotations are reconstructed from this.
 */
private fun buildCompletedMatchFixture(): com.kursi.feature.game.session.CompletedMatch {
    val seed = 71L
    val players = 4
    val personasMap = buildPersonaMap(players, seed)
    val bots: Map<PlayerId, Policy> =
        run {
            val assignments =
                PersonaAssigner.assign(
                    seatCount = players - 1,
                    difficulty = BotDifficulty.MEDIUM,
                    seed = seed,
                )
            assignments.mapIndexed { i, pair -> PlayerId(i + 1) to (pair.second as Policy) }.toMap()
        }
    val session =
        com.kursi.feature.game.session.GameSession(
            config = GameConfig.forPlayers(players),
            seed = seed,
            humanSeat = PlayerId(0),
            bots = bots,
            opponentPersonas = personasMap,
        )
    val human = com.kursi.ai.EasyPolicy(2024L)
    var ui = session.start()
    while (!ui.isGameOver && ui.isHumanTurn && ui.legalIntents.isNotEmpty()) {
        ui = session.submitHuman(human.decide(ui.view, ui.legalIntents))
    }
    val winner = (session.snapshotState().phase as Phase.GameOver).winner.raw
    val personas =
        personasMap.values.sortedBy { it.playerId.raw }.map { p ->
            com.kursi.feature.game.session.SnapPersona(
                seat = p.playerId.raw,
                name = p.name,
                monogram = p.monogram,
                seatColorArgb = p.seatColorArgb,
                isHuman = p.playerId.raw == 0,
            )
        } +
            com.kursi.feature.game.session.SnapPersona(
                seat = 0,
                name = "Aap",
                monogram = "A",
                seatColorArgb = 0xFF009E73L,
                isHuman = true,
            )
    return com.kursi.feature.game.session.CompletedMatch.of(
        seed = seed,
        players = players,
        difficulty = Difficulty.Medium,
        humanLog = session.humanActionLog(),
        winnerSeat = winner,
        personas = personas.sortedBy { it.seat },
    )
}

/**
 * M6e — Setup screen surfacing the Team Khel toggle (vs-AI, 4 players → eligible), with the toggle
 * seeded ON and the form scrolled to the 1-D Team row so the static shot actually demonstrates the
 * team-assignment control rather than the above-the-fold play-mode options.
 *
 * A static [ImageComposeScene] does not animate a scroll, so we drive it manually: render frame 1 to
 * lay the form out (which populates [scrollState.maxValue]), scroll the body to the bottom, then
 * render frame 2 for capture. The scroll runs on the scene's own dispatcher via [scene.render] frame
 * pumping; [scrollState.dispatchRawDelta] applies synchronously so the second frame reflects it.
 */
private fun renderTeamSetup(dir: File) {
    val scrollState = ScrollState(initial = 0)
    val scene =
        ImageComposeScene(width = 1440, height = 900, density = Density(1f)) {
            KursiTheme {
                SetupScreen(
                    onBack = {},
                    onNext = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    initialPlayers = 4,
                    initialDifficulty = Difficulty.Medium,
                    initialTeamPlay = true,
                    scrollState = scrollState,
                )
            }
        }
    // Frame 1: lay out so maxValue is known.
    scene.render()
    // Scroll the form body to the bottom — the Team Khel (1-D) row + footer sit there.
    scrollState.dispatchRawDelta(scrollState.maxValue.toFloat())
    // Frame 2: capture with the team row in view.
    val data = scene.render().encodeToData() ?: error("encode null for setup_teams")
    File(dir, "setup_teams.png").writeBytes(data.bytes)
    scene.close()
}

/**
 * M6e TEAM KHEL — a 4-seat 2v2 team table on the human's turn, so the opponent plates carry their
 * faction badge (team membership is public). Built off a real team [GameConfig] so [OpponentView.team]
 * is populated by [redact].
 */
private fun buildTeamTableState(): Pair<GameUiState, List<GameEvent>> {
    val seed = 42L
    val cfg = GameConfig.forPlayers(4).copy(teams = mapOf(0 to 0, 1 to 1, 2 to 0, 3 to 1))
    var cur = initialState(cfg, seed = seed)
    val ev = mutableListOf<GameEvent>()
    repeat(60) {
        val ph = cur.phase
        if (ph is Phase.AwaitingAction && ph.actorSeat == 0 && cur.phase !is Phase.GameOver) return@repeat
        val who = whoActsNext(cur) ?: return@repeat
        val intents = legalIntents(cur, who)
        if (intents.isEmpty()) return@repeat
        val out = applyIntent(cur, intents.first())
        if (out is ApplyOutcome.Accepted) {
            cur = out.state
            ev += out.events
        }
    }
    val personas = buildPersonaMap(seatCount = 4, seed = seed)
    val ui = buildUiState(cur, viewerSeat = 0, ev.takeLast(GameUiState.MAX_EVENTS), personas)
    return ui to ev
}

/** A grid of reduced-motion static frames, one per representative moment. */
@Composable
private fun ReducedMotionGallery() {
    // Each cell gets seat anchors local to its own box (cells are ~470×290).
    val anchors =
        TableAnchors(
            seatCenters =
                mapOf(
                    0 to Offset(235f, 150f),
                    1 to Offset(110f, 90f),
                    2 to Offset(360f, 90f),
                ),
            treasuryCenter = Offset(235f, 150f),
        )
    val hue = KursiRoleHues
    val moments: List<Pair<String, KursiMoment>> =
        listOf(
            "Income (coins)" to KursiMoment.Income(actorSeat = 0),
            "Tax (held stamp)" to KursiMoment.Tax(actorSeat = 0, roleHue = hue.Neta),
            "Steal (yank)" to KursiMoment.Steal(actorSeat = 0, victim = 1, roleHue = hue.Babu),
            "Reveal JHOOTH" to KursiMoment.Reveal(actorSeat = 1, claimant = 1, claimedRole = "BABU", truthful = false, roleHue = hue.Babu),
            "Reveal SACH" to KursiMoment.Reveal(actorSeat = 2, claimant = 2, claimedRole = "NETA", truthful = true, roleHue = hue.Neta),
            "Influence lost" to KursiMoment.InfluenceLoss(actorSeat = 0, lostRole = "VAKIL", roleHue = hue.Vakil),
            "Elimination" to KursiMoment.Elimination(actorSeat = 1),
            "Coup (crest)" to KursiMoment.Coup(actorSeat = 0, target = 1),
            "Win (KURSI)" to KursiMoment.Win(actorSeat = 0),
        )
    Box(Modifier.fillMaxSize().background(BrandTokens.TeakInk).padding(8.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            moments.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { (label, moment) ->
                        ReducedMotionCell(label = label, moment = moment, anchors = anchors, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun ReducedMotionCell(
    label: String,
    moment: KursiMoment,
    anchors: TableAnchors,
    modifier: Modifier = Modifier,
) {
    val host = remember(moment) { MomentHost().also { it.play(moment) } }
    Box(
        modifier =
            modifier
                .background(BrandTokens.TeakMid, RoundedCornerShape(8.dp))
                .border(1.dp, BrandTokens.BrassDark, RoundedCornerShape(8.dp)),
    ) {
        ActionMomentOverlay(host = host, anchors = anchors, reducedMotion = true)
        BasicText(
            text = label,
            style = KursiType.label_micro.copy(color = BrandTokens.GoldAntique),
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        )
    }
}

/** A representative ranked standing — Under Secretary tier with a climbing rating spark-line. */
private fun sampleRanked(): RankedStanding =
    RankedStanding(
        rating = 1185,
        peak = 1240,
        games = 18,
        history = listOf(1000, 1018, 1002, 1031, 1057, 1042, 1078, 1095, 1120, 1108, 1142, 1166, 1151, 1185),
    )

/** A representative human-won verdict, shaped from the real MatchSummary nav model. */
private fun sampleMatchSummary(): MatchSummary =
    MatchSummary(
        matchId = "match_sample",
        seed = 42L,
        players = 6,
        difficulty = Difficulty.Hard,
        winnerSeat = 0,
        winnerName = "Aap",
        winnerMonogram = "AAP",
        winnerColor = 0xFFE63946L,
        humanWon = true,
        turnsTotal = 37,
        bluffsHeld = 5,
        bluffsCaught = 2,
        recentEvents =
            listOf(
                "Aap ne GHOTALA stamp kiya — +3 Khokha.",
                "Babu Filewala challenged. Rangey haath pakda gaya.",
                "Inspector Damaad ki kursi gayi.",
            ),
        finalStandings =
            listOf(
                "Aap",
                "Madam Sarpanch",
                "Seth Khokhawala",
                "Vakil Loophole",
            ),
        bestMomentPersonaId = null,
        // M6b — the per-game decision-quality recap line on the results certificate.
        decisionSummary =
            MatchDecisionSummary(
                decisions = 18,
                accuracyPct = 78,
                avgEvLostPct = 3,
                challenges = 3,
                challengeAccuracyPct = 67,
            ),
    )

private fun renderComposable(
    dir: File,
    name: String,
    width: Int = 1440,
    height: Int = 900,
    density: Float = 1f,
    content: @Composable () -> Unit,
) {
    val scene =
        ImageComposeScene(
            width = (width * density).toInt(),
            height = (height * density).toInt(),
            density = Density(density),
        ) {
            KursiTheme { content() }
        }
    val data = scene.render().encodeToData() ?: error("encode null for $name")
    File(dir, "$name.png").writeBytes(data.bytes)
    scene.close()
}

/**
 * Multi-frame render for screens with LaunchedEffect-gated entrance animations.
 *
 * Why looping is necessary:
 *   Frame 0 → initial composition, visible=false, LaunchedEffect REGISTERED
 *   Frame 1 → LaunchedEffect body runs (visible=true), recomposition triggered
 *   Frame 2 → recomposition with visible=true; animations START (alpha begins at 0f)
 *   Frames 3-60 → animation clock advances; max tween is 260ms delay + 420ms = 680ms
 *   Frame 60 (60×16ms = 960ms from start) → all tweens settled at 1f
 *
 * A two-frame call (render at t=0 then t=800ms) fails because the animation STARTS on
 * frame 2, not completes: the animation clock is 800ms when the target first becomes 1f,
 * so it still reads 0f progress at that capture time.
 */
private fun renderComposableAnimated(
    dir: File,
    name: String,
    width: Int = 1440,
    height: Int = 900,
    density: Float = 1f,
    content: @Composable () -> Unit,
) {
    val scene =
        ImageComposeScene(
            width = (width * density).toInt(),
            height = (height * density).toInt(),
            density = Density(density),
        ) {
            KursiTheme { content() }
        }
    val frameNs = 16_000_000L // 16 ms per frame
    for (frame in 0L..60L) {
        scene.render(nanoTime = frame * frameNs)
    }
    val data =
        scene
            .render(nanoTime = 61L * frameNs)
            .encodeToData() ?: error("encode null for $name")
    File(dir, "$name.png").writeBytes(data.bytes)
    scene.close()
}

// ─────────────────────────── Renderer ────────────────────────────────────────

private fun renderToPng(
    state: GameUiState,
    dir: File,
    name: String,
    localPhase: GamePhase? = null,
    initialChit: ChitContent? = null,
    forceHandoff: Boolean? = null,
    spectator: Boolean = false,
    width: Int = 1440,
    height: Int = 900,
    initialDarbarOpen: Boolean = false,
    density: Float = 1f,
) {
    val scene =
        ImageComposeScene(
            width = (width * density).toInt(),
            height = (height * density).toInt(),
            density = Density(density),
        ) {
            KursiTheme {
                // Suppress the first-run "Your Certificates" SwearingInPrimer coachmark so the
                // real in-game table renders un-dimmed for portfolio capture (otherwise the whole
                // game UI sits at ~30% opacity behind the centered onboarding modal).
                GameScreen(
                    state = state,
                    onAction = {},
                    initialLocalPhase = localPhase,
                    showPrimerOverride = false,
                    initialChit = initialChit,
                    forceHandoffOverride = forceHandoff,
                    spectator = spectator,
                    initialDarbarOpen = initialDarbarOpen,
                )
            }
        }
    val image = scene.render()
    val data = image.encodeToData() ?: error("encodeToData() returned null for $name")
    File(dir, "$name.png").writeBytes(data.bytes)
    scene.close()
}

// ─────────────────────────── Engine helpers ───────────────────────────────────

private fun evolve(
    state: GameState,
    steps: Int,
    seed: Long = 99L,
): Pair<GameState, List<GameEvent>> {
    var cur = state
    val events = mutableListOf<GameEvent>()
    var s = seed
    repeat(steps) {
        val who = whoActsNext(cur) ?: return@repeat
        val intents = legalIntents(cur, who)
        if (intents.isEmpty()) return@repeat
        s = s * 6364136223846793005L + 1442695040888963407L
        val intent = intents[((s ushr 33) % intents.size).toInt().and(0x7fffffff) % intents.size]
        val outcome = applyIntent(cur, intent)
        if (outcome is ApplyOutcome.Accepted) {
            cur = outcome.state
            events += outcome.events
        }
    }
    return cur to events
}

private fun buildUiState(
    state: GameState,
    viewerSeat: Int,
    events: List<GameEvent>,
    opponentPersonas: Map<PlayerId, OpponentPersona> = emptyMap(),
): GameUiState {
    val viewer = PlayerId(viewerSeat)
    val view = redact(state, viewer)
    val intents = if (whoActsNext(state) == viewer) legalIntents(state, viewer) else emptyList()
    val isOver = state.phase is Phase.GameOver
    val winnerSeat = (state.phase as? Phase.GameOver)?.winner?.raw
    return GameUiState(
        view = view,
        legalIntents = intents,
        recentEvents = events.takeLast(GameUiState.MAX_EVENTS),
        isHumanTurn = whoActsNext(state) == viewer,
        isGameOver = isOver,
        winnerSeat = winnerSeat,
        opponentPersonas = opponentPersonas,
    )
}

/**
 * Run the real DECISION-COACH brain on [state] for [viewer] and fold the ranked advice into
 * [ui]. This is exactly what the live ViewModel does asynchronously — here we do it inline so the
 * static screenshot captures the coach badges/odds/recommended marker fully populated.
 */
private fun withCoachAdvice(
    state: GameState,
    viewer: PlayerId,
    ui: GameUiState,
    seed: Long = 42L,
): GameUiState {
    if (state.phase is Phase.GameOver || whoActsNext(state) != viewer) return ui
    val legal = legalIntents(state, viewer)
    if (legal.isEmpty()) return ui
    // ponytail: advise() is suspend (MoveAdvisor is now cancellable mid-search for the live
    // coach). This screenshot tool runs once, synchronously, at startup with no cancellation
    // moment — runBlocking here is zero behavior change from when advise() was a plain function.
    val advice = runBlocking { MoveAdvisor(seed = seed).advise(state, viewer, legal) }
    return ui.copy(advice = advice)
}

/**
 * Build PUBLIC-info opponent dossiers for [viewer] by replaying [events] into a fresh [BotMemory]
 * (exactly as the live [com.kursi.feature.game.session.GameSession] feeds its coach) and folding the
 * accumulated belief into [com.kursi.ai.OpponentInsight] for every opponent. This populates the
 * dossier posterior / claim-counts / bluff-caught / bluffRate so the static shots render the real
 * intelligence. SECRECY-safe: only the public [events] are observed — never hidden cards.
 */
private fun buildInsights(
    state: GameState,
    viewer: PlayerId,
    events: List<GameEvent>,
): List<OpponentInsight> {
    val memory = BotMemory()
    events.forEach { memory.observe(it, state.turnNumber) }
    val view = redact(state, viewer)
    return OpponentInsight.forAll(view, memory, includeEliminated = true)
}

/** Fold real PUBLIC-info dossiers into [ui] so the dossier/suspicion surfaces render populated. */
private fun withInsights(
    state: GameState,
    viewer: PlayerId,
    events: List<GameEvent>,
    ui: GameUiState,
): GameUiState = ui.copy(opponentInsights = buildInsights(state, viewer, events))

/** Build a deterministic persona map for N bot seats (seats 1..n-1). */
private fun buildPersonaMap(
    seatCount: Int,
    seed: Long,
): Map<PlayerId, OpponentPersona> {
    val botCount = seatCount - 1
    if (botCount <= 0) return emptyMap()
    val assignments =
        PersonaAssigner.assign(
            seatCount = botCount,
            difficulty = BotDifficulty.MEDIUM,
            seed = seed,
        )
    return assignments
        .mapIndexed { index: Int, pair: Pair<com.kursi.ai.persona.BotPersona, com.kursi.ai.persona.PersonaPolicy> ->
            val persona = pair.first
            PlayerId(index + 1) to
                OpponentPersona(
                    playerId = PlayerId(index + 1),
                    name = persona.name,
                    monogram = persona.monogram,
                    seatColorArgb = persona.seatColorArgb,
                )
        }.toMap()
}

// ─────────────────────────── Fixtures ────────────────────────────────────────

/**
 * Mid-game state on the HUMAN's turn where the three opponents carry standing role-claims
 * (P1 claims NETA ×2, P2's BABU claim was caught, P3 claims BHAI). Reused both for the
 * 4p_mid_claim plate shot AND the chit-OPEN shots so the dossier/risk chits render over a
 * believable table.
 */
private fun buildMidClaimState(): Pair<GameUiState, List<GameEvent>> {
    val cfg = GameConfig.forPlayers(4)
    val seed = 42L
    val base = initialState(cfg, seed = seed)
    var cur = base
    var ev = mutableListOf<GameEvent>()
    repeat(60) {
        val ph = cur.phase
        if (ph is Phase.AwaitingAction && ph.actorSeat == 0 && cur.phase !is Phase.GameOver) return@repeat
        val who = whoActsNext(cur) ?: return@repeat
        val intents = legalIntents(cur, who)
        if (intents.isEmpty()) return@repeat
        val out = applyIntent(cur, intents.first())
        if (out is ApplyOutcome.Accepted) {
            cur = out.state
            ev += out.events
        }
    }
    val p1 = PlayerId(1)
    val p2 = PlayerId(2)
    val p3 = PlayerId(3)
    val crafted =
        listOf(
            GameEvent.ActionDeclared(p1, Action.Tax, Role.NETA),
            GameEvent.ActionDeclared(p1, Action.Tax, Role.NETA),
            GameEvent.ActionDeclared(p2, Action.Steal(target = PlayerId(0)), Role.BABU),
            GameEvent.Challenged(challenger = PlayerId(0), target = p2, claimedRole = Role.BABU),
            GameEvent.ChallengeRevealed(player = p2, card = CardId(0), role = Role.NETA, hadRole = false),
            GameEvent.ActionDeclared(p3, Action.Assassinate(target = PlayerId(0)), Role.BHAI),
        )
    val evWithClaims = (ev + crafted).takeLast(GameUiState.MAX_EVENTS)
    val personas = buildPersonaMap(seatCount = 4, seed = seed)
    // Fold REAL public-info dossiers (posterior / claim-counts / bluff-caught / bluffRate) so the
    // plate suspicion chips + dossier chit lead with intelligence, not just event-scraping.
    val ui = withInsights(cur, PlayerId(0), evWithClaims, buildUiState(cur, viewerSeat = 0, evWithClaims, personas))
    return ui to evWithClaims
}

/**
 * M5 pass-and-play: a 4-seat, 2-human (seats 0 & 1) state evolved until control rests on human seat
 * 1. The redacted view + active seat are seat 1's, and the personas label both humans ("Khiladi 1/2")
 * so the handoff guard can name the next player. isPassAndPlay=true trips the guard in the live app;
 * the harness forces it visible via forceHandoff.
 */
private fun buildPassAndPlayHandoffState(): Pair<GameUiState, List<GameEvent>> {
    val cfg = GameConfig.forPlayers(4)
    val seed = 314L
    var cur = initialState(cfg, seed = seed)
    val ev = mutableListOf<GameEvent>()
    // Advance until it is seat 1's action (a human) — seat 1 must choose, so the guard would show.
    repeat(400) {
        val ph = cur.phase
        if (ph is Phase.AwaitingAction && ph.actorSeat == 1) return@repeat
        if (cur.phase is Phase.GameOver) return@repeat
        val who = whoActsNext(cur) ?: return@repeat
        val intents = legalIntents(cur, who)
        if (intents.isEmpty()) return@repeat
        val out = applyIntent(cur, intents.first())
        if (out is ApplyOutcome.Accepted) {
            cur = out.state
            ev += out.events
        }
    }
    // Personas: humans on seats 0 & 1, bots on 2 & 3.
    val botPersonas =
        buildPersonaMap(seatCount = 4, seed = seed)
            .filterKeys { it.raw >= 2 }
    val humanColors = longArrayOf(0xFF009E73L, 0xFFD55E00L)
    val personas = (
        botPersonas +
            mapOf(
                PlayerId(0) to OpponentPersona(PlayerId(0), "Khiladi 1", "K1", humanColors[0]),
                PlayerId(1) to OpponentPersona(PlayerId(1), "Khiladi 2", "K2", humanColors[1]),
            )
    )
    val viewer = PlayerId(1)
    val view = redact(cur, viewer)
    val ui =
        GameUiState(
            view = view,
            legalIntents = if (whoActsNext(cur) == viewer) legalIntents(cur, viewer) else emptyList(),
            recentEvents = ev.takeLast(GameUiState.MAX_EVENTS),
            isHumanTurn = whoActsNext(cur) == viewer,
            isGameOver = false,
            winnerSeat = null,
            opponentPersonas = personas,
            activeSeat = 1,
            isPassAndPlay = true,
        )
    return ui to ev
}

private fun buildFixtures(): List<Triple<String, GameUiState, GamePhase?>> =
    buildList {
        // ── 4p_pick_action ────────────────────────────────────────────────────────
        run {
            val cfg = GameConfig.forPlayers(4)
            val seed = 42L
            val base = initialState(cfg, seed = seed)
            val (state, events) = evolve(base, steps = 12, seed = 1L)
            var cur = state
            var ev = events.toMutableList()
            repeat(40) {
                if (cur.phase is Phase.AwaitingAction &&
                    (cur.phase as Phase.AwaitingAction).actorSeat == 0 &&
                    cur.phase !is Phase.GameOver
                ) {
                    return@repeat
                }
                val who = whoActsNext(cur) ?: return@repeat
                val intents = legalIntents(cur, who)
                if (intents.isEmpty()) return@repeat
                val intent = intents.first()
                val out = applyIntent(cur, intent)
                if (out is ApplyOutcome.Accepted) {
                    cur = out.state
                    ev += out.events
                }
            }
            val personas = buildPersonaMap(seatCount = 4, seed = seed)
            add(Triple("4p_pick_action", buildUiState(cur, viewerSeat = 0, ev, personas), null))
        }

        // ── 4p_pick_target ────────────────────────────────────────────────────────
        // Real PickTarget localPhase — opponent chips show ValidTarget green glow.
        run {
            val cfg = GameConfig.forPlayers(4)
            var cur = initialState(cfg, seed = 77L)
            var ev = mutableListOf<GameEvent>()
            repeat(80) {
                val ph = cur.phase
                if (ph is Phase.AwaitingAction &&
                    ph.actorSeat == 0 &&
                    cur.player(PlayerId(0)).coins >= 2
                ) {
                    return@repeat
                }
                val who = whoActsNext(cur) ?: return@repeat
                val intents = legalIntents(cur, who)
                if (intents.isEmpty()) return@repeat
                val out = applyIntent(cur, intents.first())
                if (out is ApplyOutcome.Accepted) {
                    cur = out.state
                    ev += out.events
                }
            }
            // Splice public claim/reveal events so opponents carry a real READ (suspicion pips +
            // dossiers) even on the target-pick dock, and the weakest/richest tags read off live coins.
            val p1 = PlayerId(1)
            val p2 = PlayerId(2)
            val p3 = PlayerId(3)
            val crafted =
                listOf(
                    GameEvent.ActionDeclared(p1, Action.Tax, Role.NETA),
                    GameEvent.ActionDeclared(p1, Action.Tax, Role.NETA),
                    GameEvent.ActionDeclared(p2, Action.Steal(target = PlayerId(0)), Role.BABU),
                    GameEvent.Challenged(challenger = PlayerId(0), target = p2, claimedRole = Role.BABU),
                    GameEvent.ChallengeRevealed(player = p2, card = CardId(0), role = Role.NETA, hadRole = false),
                    GameEvent.ActionDeclared(p3, Action.Assassinate(target = PlayerId(0)), Role.BHAI),
                )
            val evT = (ev + crafted).takeLast(GameUiState.MAX_EVENTS)
            val personas = buildPersonaMap(seatCount = 4, seed = 77L)
            // Seed a real PickTarget localPhase so opponent chips render with ValidTarget state
            val ui = withInsights(cur, PlayerId(0), evT, buildUiState(cur, viewerSeat = 0, evT, personas))
            add(Triple("4p_pick_target", ui, GamePhase.PickTarget(Action.Steal(PlayerId(0)))))
        }

        // ── 4p_confirm ────────────────────────────────────────────────────────────
        // Confirm localPhase on a CLAIM-BEARING action (Vasooli / Steal, claims BABU) targeting P1,
        // so the dock's at-the-moment-of-declaring DECISION-COACH read is visible: the REAL/BLUFF badge
        // + the P(it flies) odds pill + the brass recommended star when the advisor backs the move.
        // (Coup/Khela makes no claim and correctly shows no read — this fixture proves the claim path.)
        run {
            val cfg = GameConfig.forPlayers(4)
            var cur = initialState(cfg, seed = 55L)
            var ev = mutableListOf<GameEvent>()
            repeat(200) {
                val ph = cur.phase
                if (ph is Phase.AwaitingAction && ph.actorSeat == 0) return@repeat
                if (cur.phase is Phase.GameOver) return@repeat
                val who = whoActsNext(cur) ?: return@repeat
                val intents = legalIntents(cur, who)
                if (intents.isEmpty()) return@repeat
                val out = applyIntent(cur, intents.first())
                if (out is ApplyOutcome.Accepted) {
                    cur = out.state
                    ev += out.events
                }
            }
            // Splice public claims so the plates above the confirm dock carry a real READ.
            val p1 = PlayerId(1)
            val p2 = PlayerId(2)
            val p3 = PlayerId(3)
            val crafted =
                listOf(
                    GameEvent.ActionDeclared(p1, Action.Tax, Role.NETA),
                    GameEvent.ActionDeclared(p1, Action.Tax, Role.NETA),
                    GameEvent.ActionDeclared(p2, Action.Steal(target = PlayerId(0)), Role.BABU),
                    GameEvent.Challenged(challenger = PlayerId(0), target = p2, claimedRole = Role.BABU),
                    GameEvent.ChallengeRevealed(player = p2, card = CardId(0), role = Role.NETA, hadRole = false),
                    GameEvent.ActionDeclared(p3, Action.Assassinate(target = PlayerId(0)), Role.BHAI),
                )
            val evC = (ev + crafted).takeLast(GameUiState.MAX_EVENTS)
            val personas = buildPersonaMap(seatCount = 4, seed = 55L)
            val ui = withInsights(cur, PlayerId(0), evC, buildUiState(cur, viewerSeat = 0, evC, personas))
            // Fold in real coach advice so the confirm dock's badge/odds/star can resolve.
            val uiCoached = withCoachAdvice(cur, PlayerId(0), ui, 55L)
            add(Triple("4p_confirm", uiCoached, GamePhase.Confirm(Action.Steal(PlayerId(1)), PlayerId(1))))
        }

        // ── 4p_mid_claim ───────────────────────────────────────────────────────────
        // Phase TILES showcase: it is the HUMAN's turn (no live pending claim), yet the
        // opponent plates must STILL show each rival's standing role-claim + a live bluff-odds
        // chip derived from the public event history. We evolve to a human PickAction state,
        // then splice deterministic public ActionDeclared / ChallengeRevealed events so the
        // plates read "claimed NETA ×2", "claimed BABU ✗ (caught)", etc., with odds chips.
        run {
            val (state, _) = buildMidClaimState()
            add(Triple("4p_mid_claim", state, null))
        }

        // ── 4p_focus ──────────────────────────────────────────────────────────────
        // DensityLayer.FOCUS (spec §3) — the same mid-claim table, gated down to the clean board:
        // turn indicator, one headline line, hand, legal-action dock only. No pips, coach badges,
        // dossier chits, log, or Darbar. Proves the FOCUS gate renders (ANALYST fixtures above are
        // untouched by it — see 4p_mid_claim).
        run {
            val (state, _) = buildMidClaimState()
            add(Triple("4p_focus", state.copy(densityLayer = DensityLayer.FOCUS), null))
        }

        // ── 4p_reaction ───────────────────────────────────────────────────────────
        // Real engine AwaitingReactions where seat 0 must respond. Use a for-loop
        // with break so we don't overshoot into game-over.
        run {
            val cfg = GameConfig.forPlayers(4)
            var cur = initialState(cfg, seed = 123L)
            var ev = mutableListOf<GameEvent>()
            var seed2 = 999L
            for (i in 0 until 2000) {
                val ph = cur.phase
                if (ph is Phase.AwaitingReactions && whoActsNext(cur) == PlayerId(0)) break
                if (cur.phase is Phase.GameOver) break
                val who = whoActsNext(cur) ?: break
                val intents = legalIntents(cur, who)
                if (intents.isEmpty()) break
                seed2 = seed2 * 6364136223846793005L + 1442695040888963407L
                val intent = intents[((seed2 ushr 33).toInt().and(0x7fffffff)) % intents.size]
                val out = applyIntent(cur, intent)
                if (out is ApplyOutcome.Accepted) {
                    cur = out.state
                    ev += out.events
                }
            }
            val personas = buildPersonaMap(seatCount = 4, seed = 123L)
            // Real reaction window — fold insights so the dossier read is live, and coach advice so the
            // chips carry truthful/bluff + odds. The belief banner reads off public card-accounting.
            val ui = withInsights(cur, PlayerId(0), ev, buildUiState(cur, viewerSeat = 0, ev, personas))
            add(Triple("4p_reaction", withCoachAdvice(cur, PlayerId(0), ui, 123L), null))
        }

        // ── 4p_reaction_block ───────────────────────────────────────────────────────
        // The BLOCK step of a reaction window (seat 0 deciding whether to BLOCK an opponent's
        // action), specifically so the rendered shot PROVES the block-step safe-vs-bluff marking:
        // each 🛡 BLOCK chip carries the coach's REAL/BLUFF badge + a P(it flies) odds pill, with the
        // advisor's pick starred. The existing 4p_reaction lands on CHALLENGE_ACTION, so this fixture
        // searches seeds for a real engine AwaitingReactions where seat 0's step == BLOCK.
        run {
            val cfg = GameConfig.forPlayers(4)
            var chosen: Triple<GameState, MutableList<GameEvent>, Long>? = null
            for (s in longArrayOf(42L, 7L, 123L, 55L, 99L, 200L, 321L, 777L, 1234L, 4242L, 31337L, 90210L, 1L, 88L, 2024L)) {
                var cur = initialState(cfg, seed = s)
                var ev = mutableListOf<GameEvent>()
                var seed2 = s xor 0x5DEECE66DL
                var hit: GameState? = null
                for (i in 0 until 4000) {
                    val ph = cur.phase
                    if (ph is Phase.AwaitingReactions &&
                        ph.ctx.step == ReactionStep.BLOCK &&
                        whoActsNext(cur) == PlayerId(0)
                    ) {
                        hit = cur
                        break
                    }
                    if (cur.phase is Phase.GameOver) break
                    val who = whoActsNext(cur) ?: break
                    val intents = legalIntents(cur, who)
                    if (intents.isEmpty()) break
                    seed2 = seed2 * 6364136223846793005L + 1442695040888963407L
                    val intent = intents[((seed2 ushr 33).toInt().and(0x7fffffff)) % intents.size]
                    val out = applyIntent(cur, intent)
                    if (out is ApplyOutcome.Accepted) {
                        cur = out.state
                        ev += out.events
                    }
                }
                if (hit != null) {
                    chosen = Triple(hit, ev, s)
                    break
                }
            }
            if (chosen != null) {
                val (state, ev, seed) = chosen
                val personas = buildPersonaMap(seatCount = 4, seed = seed)
                val ui = withInsights(state, PlayerId(0), ev, buildUiState(state, viewerSeat = 0, ev, personas))
                add(Triple("4p_reaction_block", withCoachAdvice(state, PlayerId(0), ui, seed), null))
            }
        }

        // ── 4p_lose_influence ─────────────────────────────────────────────────────
        run {
            val cfg = GameConfig.forPlayers(4)
            var cur = initialState(cfg, seed = 200L)
            var ev = mutableListOf<GameEvent>()
            repeat(500) {
                if (cur.phase is Phase.AwaitingInfluenceLoss &&
                    (cur.phase as Phase.AwaitingInfluenceLoss).loser == PlayerId(0)
                ) {
                    return@repeat
                }
                if (cur.phase is Phase.GameOver) return@repeat
                val who = whoActsNext(cur) ?: return@repeat
                val intents = legalIntents(cur, who)
                if (intents.isEmpty()) return@repeat
                val coup =
                    intents
                        .filterIsInstance<Intent.DeclareAction>()
                        .firstOrNull { it.action is Action.Coup && (it.action as Action.Coup).target == PlayerId(0) }
                val intent = coup ?: intents.first()
                val out = applyIntent(cur, intent)
                if (out is ApplyOutcome.Accepted) {
                    cur = out.state
                    ev += out.events
                }
            }
            // Personas so the LossReason cause line names the aggressor ("Lost to <persona>'s Khela.").
            val personas = buildPersonaMap(seatCount = 4, seed = 200L)
            val ui = withInsights(cur, PlayerId(0), ev, buildUiState(cur, viewerSeat = 0, ev, personas))
            add(Triple("4p_lose_influence", ui, null))
        }

        // ── 4p_exchange ───────────────────────────────────────────────────────────
        // Drive P0 to an AwaitingExchange decision (Exchange declared + reactions passed) so the dock
        // renders a POPULATED keep-decision: real two-role keep options, drawn-vs-hand tags, and the
        // advisor's recommended keep starred (folded in via withCoachAdvice).
        run {
            val cfg = GameConfig.forPlayers(4)
            val seed321 = 321L
            var cur = initialState(cfg, seed = seed321)
            var ev = mutableListOf<GameEvent>()
            repeat(2000) {
                if (cur.phase is Phase.AwaitingExchange &&
                    (cur.phase as Phase.AwaitingExchange).actor == PlayerId(0)
                ) {
                    return@repeat
                }
                if (cur.phase is Phase.GameOver) return@repeat
                val who = whoActsNext(cur) ?: return@repeat
                val intents = legalIntents(cur, who)
                if (intents.isEmpty()) return@repeat
                // Bias P0 toward declaring Exchange; everyone else passes/plays the first legal move
                // (which passes reactions so the Exchange resolves into AwaitingExchange for P0).
                val exc =
                    if (who == PlayerId(0)) {
                        intents
                            .filterIsInstance<Intent.DeclareAction>()
                            .firstOrNull { it.action == Action.Exchange }
                    } else {
                        null
                    }
                val intent = exc ?: intents.first()
                val out = applyIntent(cur, intent)
                if (out is ApplyOutcome.Accepted) {
                    cur = out.state
                    ev += out.events
                }
            }
            val personas = buildPersonaMap(seatCount = 4, seed = seed321)
            val ui = buildUiState(cur, viewerSeat = 0, ev, personas)
            add(Triple("4p_exchange", withCoachAdvice(cur, PlayerId(0), ui, seed321), null))
        }

        // ── 4p_game_over ─────────────────────────────────────────────────────────
        run {
            val cfg = GameConfig.forPlayers(4)
            var cur = initialState(cfg, seed = 7L)
            var ev = mutableListOf<GameEvent>()
            var seed = 42L
            repeat(5000) {
                if (cur.phase is Phase.GameOver) return@repeat
                val who = whoActsNext(cur) ?: return@repeat
                val intents = legalIntents(cur, who)
                if (intents.isEmpty()) return@repeat
                seed = seed * 6364136223846793005L + 1442695040888963407L
                val intent = intents[((seed ushr 33).toInt().and(0x7fffffff)) % intents.size]
                val out = applyIntent(cur, intent)
                if (out is ApplyOutcome.Accepted) {
                    cur = out.state
                    ev += out.events
                }
            }
            val personas = buildPersonaMap(seatCount = 4, seed = 7L)
            add(Triple("4p_game_over", buildUiState(cur, viewerSeat = 0, ev, personas), null))
        }

        // ── 2p_pick_action ────────────────────────────────────────────────────────
        run {
            val cfg = GameConfig.forPlayers(2)
            val (state, events) = evolve(initialState(cfg, seed = 11L), steps = 4, seed = 2L)
            var cur = state
            var ev = events.toMutableList()
            repeat(40) {
                if (cur.phase is Phase.AwaitingAction &&
                    (cur.phase as Phase.AwaitingAction).actorSeat == 0
                ) {
                    return@repeat
                }
                val who = whoActsNext(cur) ?: return@repeat
                val intents = legalIntents(cur, who)
                if (intents.isEmpty()) return@repeat
                val out = applyIntent(cur, intents.first())
                if (out is ApplyOutcome.Accepted) {
                    cur = out.state
                    ev += out.events
                }
            }
            add(Triple("2p_pick_action", buildUiState(cur, viewerSeat = 0, ev), null))
        }

        // ── 10p_pick_action ───────────────────────────────────────────────────────
        run {
            val cfg = GameConfig.forPlayers(10)
            val (state, events) = evolve(initialState(cfg, seed = 999L), steps = 6, seed = 3L)
            var cur = state
            var ev = events.toMutableList()
            repeat(40) {
                if (cur.phase is Phase.AwaitingAction &&
                    (cur.phase as Phase.AwaitingAction).actorSeat == 0
                ) {
                    return@repeat
                }
                val who = whoActsNext(cur) ?: return@repeat
                val intents = legalIntents(cur, who)
                if (intents.isEmpty()) return@repeat
                val out = applyIntent(cur, intents.first())
                if (out is ApplyOutcome.Accepted) {
                    cur = out.state
                    ev += out.events
                }
            }
            add(Triple("10p_pick_action", buildUiState(cur, viewerSeat = 0, ev), null))
        }

        // ── 4p_pick_action_nocoach ───────────────────────────────────────────────────
        // Coach OFF: same human-turn fixture as 4p_pick_action but with coachEnabled=false so we can
        // visually confirm that recommended stars, odds pills, and REAL/BLUFF badges are absent while
        // the chip structure is unchanged. Also folds in real advice (so the comparison is apples-to-apples)
        // but coachEnabled=false must suppress all visible guidance at render time.
        run {
            val cfg = GameConfig.forPlayers(4)
            val seed = 88L
            var cur = initialState(cfg, seed = seed)
            var ev = mutableListOf<GameEvent>()
            repeat(400) {
                val ph = cur.phase
                if (ph is Phase.AwaitingAction &&
                    ph.actorSeat == 0 &&
                    cur.player(PlayerId(0)).coins >= 3
                ) {
                    return@repeat
                }
                if (cur.phase is Phase.GameOver) return@repeat
                val who = whoActsNext(cur) ?: return@repeat
                val intents = legalIntents(cur, who)
                if (intents.isEmpty()) return@repeat
                val out = applyIntent(cur, intents.first())
                if (out is ApplyOutcome.Accepted) {
                    cur = out.state
                    ev += out.events
                }
            }
            val personas = buildPersonaMap(seatCount = 4, seed = seed)
            val ui = buildUiState(cur, viewerSeat = 0, ev, personas)
            // Advice is computed (same as the ON shot) but coachEnabled=false hides it in the render.
            val uiCoached = withCoachAdvice(cur, PlayerId(0), ui, seed).copy(coachEnabled = false)
            add(Triple("4p_pick_action_nocoach", uiCoached, null))
        }

        // ── 4p_coach_action ─────────────────────────────────────────────────────────
        // DECISION-COACH on the ACTION dock: it is the human's turn, and the real MoveAdvisor
        // brain has ranked every action chip. Each role-claim chip carries a truthful/bluff badge,
        // the recommended chip wears a brass star + gold rim. We evolve to a human PickAction state
        // (with enough coins that Supari/Khela are live so the disrupt chips also show a verdict),
        // then fold in real advice via withCoachAdvice.
        run {
            val cfg = GameConfig.forPlayers(4)
            val seed = 88L
            var cur = initialState(cfg, seed = seed)
            var ev = mutableListOf<GameEvent>()
            repeat(400) {
                val ph = cur.phase
                if (ph is Phase.AwaitingAction &&
                    ph.actorSeat == 0 &&
                    cur.player(PlayerId(0)).coins >= 3
                ) {
                    return@repeat
                }
                if (cur.phase is Phase.GameOver) return@repeat
                val who = whoActsNext(cur) ?: return@repeat
                val intents = legalIntents(cur, who)
                if (intents.isEmpty()) return@repeat
                val out = applyIntent(cur, intents.first())
                if (out is ApplyOutcome.Accepted) {
                    cur = out.state
                    ev += out.events
                }
            }
            val personas = buildPersonaMap(seatCount = 4, seed = seed)
            val ui = buildUiState(cur, viewerSeat = 0, ev, personas)
            add(Triple("4p_coach_action", withCoachAdvice(cur, PlayerId(0), ui, seed), null))
        }

        // ── 4p_coach_reaction ───────────────────────────────────────────────────────
        // DECISION-COACH on the REACTION screen ("Block? Or let it pass?"): a real engine reaction
        // window where seat 0 must respond to an opponent's action. The advisor tags each option —
        // a TRUTHFUL block (green ✓, "you really hold it, safe to back up") vs a risky CHALLENGE
        // (oxblood, with the "~% they're bluffing" odds) — and stars its pick. We search seeds for a
        // BLOCK-step reaction where seat 0 actually holds the blocking role, so the truthful-vs-risky
        // contrast is unmistakable in the shot.
        run {
            val cfg = GameConfig.forPlayers(4)
            var chosen: Triple<GameState, MutableList<GameEvent>, Long>? = null
            // Prefer a BLOCK step where the human holds a blocking role (truthful block available).
            for (s in longArrayOf(123L, 7L, 42L, 55L, 99L, 200L, 321L, 777L, 1234L, 4242L, 31337L, 90210L)) {
                var cur = initialState(cfg, seed = s)
                var ev = mutableListOf<GameEvent>()
                var seed2 = s xor 0x5DEECE66DL
                var hit: GameState? = null
                for (i in 0 until 4000) {
                    val ph = cur.phase
                    if (ph is Phase.AwaitingReactions && whoActsNext(cur) == PlayerId(0)) {
                        val legal = legalIntents(cur, PlayerId(0))
                        val blocks = legal.filterIsInstance<Intent.Block>()
                        val myRoles = redact(cur, PlayerId(0)).myInfluence
                        val truthfulBlock = blocks.any { it.role in myRoles }
                        val hasChallenge = legal.any { it is Intent.Challenge }
                        if (truthfulBlock && hasChallenge) {
                            hit = cur
                            break
                        }
                    }
                    if (cur.phase is Phase.GameOver) break
                    val who = whoActsNext(cur) ?: break
                    val intents = legalIntents(cur, who)
                    if (intents.isEmpty()) break
                    seed2 = seed2 * 6364136223846793005L + 1442695040888963407L
                    val intent = intents[((seed2 ushr 33).toInt().and(0x7fffffff)) % intents.size]
                    val out = applyIntent(cur, intent)
                    if (out is ApplyOutcome.Accepted) {
                        cur = out.state
                        ev += out.events
                    }
                }
                if (hit != null) {
                    chosen = Triple(hit, ev, s)
                    break
                }
            }
            // Fallback: ANY seat-0 reaction window (still shows challenge/pass advice with odds).
            if (chosen == null) {
                var cur = initialState(cfg, seed = 123L)
                var ev = mutableListOf<GameEvent>()
                var seed2 = 999L
                for (i in 0 until 4000) {
                    val ph = cur.phase
                    if (ph is Phase.AwaitingReactions && whoActsNext(cur) == PlayerId(0)) {
                        chosen = Triple(cur, ev, 123L)
                        break
                    }
                    if (cur.phase is Phase.GameOver) break
                    val who = whoActsNext(cur) ?: break
                    val intents = legalIntents(cur, who)
                    if (intents.isEmpty()) break
                    seed2 = seed2 * 6364136223846793005L + 1442695040888963407L
                    val intent = intents[((seed2 ushr 33).toInt().and(0x7fffffff)) % intents.size]
                    val out = applyIntent(cur, intent)
                    if (out is ApplyOutcome.Accepted) {
                        cur = out.state
                        ev += out.events
                    }
                }
            }
            val (state, ev, seed) = chosen!!
            val personas = buildPersonaMap(seatCount = 4, seed = seed)
            val ui = buildUiState(state, viewerSeat = 0, ev, personas)
            add(Triple("4p_coach_reaction", withCoachAdvice(state, PlayerId(0), ui, seed), null))
        }
    }
