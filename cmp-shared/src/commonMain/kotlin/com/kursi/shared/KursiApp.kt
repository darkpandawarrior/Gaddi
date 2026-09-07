package com.kursi.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kursi.ai.createMunshiNarrator
import com.kursi.core.network.fetchStandings
import com.kursi.core.prefs.AppPrefs
import com.kursi.core.prefs.DecisionTally
import com.kursi.designsystem.BrandTokens
import com.kursi.designsystem.KursiTheme
import com.kursi.feature.game.DailyChallenge
import com.kursi.feature.game.DensityLayer
import com.kursi.feature.game.Difficulty
import com.kursi.feature.game.DraftPresets
import com.kursi.feature.game.Elo
import com.kursi.feature.game.GameAction
import com.kursi.feature.game.GameScreen
import com.kursi.feature.game.GameViewModel
import com.kursi.feature.game.GauntletLadder
import com.kursi.feature.game.KursiVoice
import com.kursi.feature.game.Language
import com.kursi.feature.game.LocalKursiVoice
import com.kursi.feature.game.NiyamGazette
import com.kursi.feature.game.OnlineHubController
import com.kursi.feature.game.SwearingInPrimer
import com.kursi.feature.game.evaluateDensityGraduation
import com.kursi.feature.game.session.CompletedMatch
import com.kursi.feature.game.session.MatchSnapshot
import com.kursi.shared.nav.MatchSummaryStore
import com.kursi.shared.nav.Route
import com.kursi.shared.nav.Today
import com.kursi.shared.nav.toMatchSummary
import com.kursi.shared.screen.CareerScreen
import com.kursi.shared.screen.GauntletScreen
import com.kursi.shared.screen.HomeScreen
import com.kursi.shared.screen.LeaderboardScreen
import com.kursi.shared.screen.LobbyScreen
import com.kursi.shared.screen.OnlineHubScreen
import com.kursi.shared.screen.OnlineStandingRow
import com.kursi.shared.screen.OnlineStandings
import com.kursi.shared.screen.ProfileSetupScreen
import com.kursi.shared.screen.ResultsScreen
import com.kursi.shared.screen.ReviewScreen
import com.kursi.shared.screen.SettingsScreen
import com.kursi.shared.screen.SetupScreen
import com.kursi.shared.screen.StoryScreen
import com.kursi.shared.screen.TutorialOfferDialog
import com.kursi.shared.screen.TutorialScreen
import com.kursi.shared.strings.KursiStrings
import com.kursi.shared.strings.LocalKursiStrings
import com.siddharth.kmp.designsystem.toPngBytes
import com.siddharth.kmp.feedback.canShareImage
import com.siddharth.kmp.feedback.shareImage
import com.siddharth.kmp.feedback.shareText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.nav_resume_label
import org.jetbrains.compose.resources.stringResource

/**
 * Root Compose entry point for Kursi, shared across all platforms (android / ios / desktop / wasm).
 *
 * The whole app flow is a type-safe [NavHost] over [Route]:
 *
 *   Boot ──▶ (hasSeenPrimer?) ──▶ Primer ──▶ Home
 *                               └────────────▶ Home
 *   Home ──▶ Setup ──▶ Lobby ──▶ Game ──▶ Results ──▶ Home
 *   Home ──▶ Gazette / Settings   (leaf overlays)
 *
 * [GameViewModel] is route-scoped to each [Route.Game] entry (a fresh deterministic match per
 * seed). On game-over the table lingers briefly so the win stamp plays, then routes to Results
 * with a serialized [MatchSummary] snapshot (never the live VM).
 */
@Composable
fun KursiApp() {
    KursiTheme {
        val navController = rememberNavController()
        val prefs = remember { AppPrefs() }
        // Rotates the Home "on-duty" persona on each visit to the hub.
        val homeVisit = remember { intArrayOf(kotlin.random.Random.nextInt(1000)) }

        val languageTag by prefs.languageFlow.collectAsState()
        val language = if (languageTag == "ENGLISH") Language.ENGLISH else Language.HINGLISH
        val strings = if (language == Language.ENGLISH) KursiStrings.English else KursiStrings.Hinglish
        val voice = remember(language) { KursiVoice(language) }

        CompositionLocalProvider(
            LocalKursiStrings provides strings,
            LocalKursiVoice provides voice,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(BrandTokens.TeakInk)
                        .safeDrawingPadding(),
            ) {
                NavHost(navController = navController, startDestination = Route.Boot) {
                    // S0 — splash decision: primer (first run) or straight to the hub.
                    composable<Route.Boot> {
                        LaunchedEffect(Unit) {
                            val dest: Route =
                                when {
                                    !prefs.hasSeenPrimer -> Route.Primer
                                    !prefs.hasPlayerProfile -> Route.ProfileSetup()
                                    // GUIDED FUNNEL (spec §6) — a brand-new player (see AppPrefs.hasSeenFunnel
                                    // for the upgrade-safety default) lands in the interactive tutorial before
                                    // ever seeing Home. Returning players skip straight through, unaffected.
                                    !prefs.hasSeenFunnel -> Route.Tutorial
                                    else -> Route.Home
                                }
                            navController.navigate(dest) { popUpTo(Route.Boot) { inclusive = true } }
                        }
                        TeakVoid()
                    }

                    // S8 — first-run swearing-in primer.
                    composable<Route.Primer> {
                        SwearingInPrimer(onDone = {
                            prefs.hasSeenPrimer = true
                            // Route to profile setup if the player hasn't named themselves yet; otherwise
                            // into the guided funnel (first-run only — see Route.Boot above).
                            val next =
                                when {
                                    !prefs.hasPlayerProfile -> Route.ProfileSetup()
                                    !prefs.hasSeenFunnel -> Route.Tutorial
                                    else -> Route.Home
                                }
                            navController.navigate(next) { popUpTo(Route.Primer) { inclusive = true } }
                        })
                    }

                    // PEHLI HAZRI — player profile setup (name, avatar, seat color).
                    composable<Route.ProfileSetup> { backEntry ->
                        val fromSettings = backEntry.toRoute<Route.ProfileSetup>().fromSettings
                        ProfileSetupScreen(
                            prefs = prefs,
                            fromSettings = fromSettings,
                            onBack =
                                if (fromSettings) {
                                    { navController.popBackStack() }
                                } else {
                                    null
                                },
                            onDone = {
                                // GUIDED FUNNEL (spec §6) — only the first-run path (never an edit-from-Settings
                                // visit) routes into the tutorial; see Route.Boot above.
                                val next = if (!fromSettings && !prefs.hasSeenFunnel) Route.Tutorial else Route.Home
                                navController.navigate(next) {
                                    popUpTo(Route.ProfileSetup(fromSettings)) { inclusive = true }
                                }
                            },
                        )
                    }

                    // S1 — Home / Daftar hub.
                    composable<Route.Home> {
                        val idx = remember { homeVisit[0]++ }
                        val ledger by prefs.ledgerFlow.collectAsState()
                        // M6d — ranked + daily standings, and today's daily-done flag (read off the platform date).
                        val ranked by prefs.rankedFlow.collectAsState()
                        val daily by prefs.dailyFlow.collectAsState()
                        val gauntlet by prefs.gauntletFlow.collectAsState()
                        val todayDay = remember(idx) { Today.epochDay() }
                        val todayDailyDone = daily.isDoneFor(todayDay)
                        val snapRaw by prefs.matchSnapshotFlow.collectAsState()
                        val resumeSnap = remember(snapRaw) { MatchSnapshot.decode(snapRaw) }
                        val resumeLabel =
                            resumeSnap?.let {
                                stringResource(Res.string.nav_resume_label, it.players, it.difficultyEnum.name, it.humanLog.size)
                            }
                        // M5 ONBOARD — one-time post-primer offer to take the interactive tutorial. Shows on
                        // the FIRST landing on Home after the primer; dismiss or accept marks it seen forever.
                        var showTutorialOffer by remember { mutableStateOf(!prefs.hasSeenTutorialOffer) }
                        Box(Modifier.fillMaxSize()) {
                            HomeScreen(
                                onNewGame = { navController.navigate(Route.Setup) },
                                onGazette = { navController.navigate(Route.Gazette) },
                                onSettings = { navController.navigate(Route.Settings) },
                                onOnlineTap = { navController.navigate(Route.OnlineHub) },
                                onStory = { navController.navigate(Route.Story) },
                                onTutorial = { navController.navigate(Route.Tutorial) },
                                launchIndex = idx,
                                ledger = ledger,
                                onCareer = { navController.navigate(Route.Career) },
                                ranked = ranked,
                                onLeaderboard = { navController.navigate(Route.Leaderboard) },
                                daily = daily,
                                todayDailyDone = todayDailyDone,
                                gauntlet = gauntlet,
                                gauntletRungCount = GauntletLadder.RUNGS.size,
                                onGauntlet = { navController.navigate(Route.Gauntlet) },
                                onSpectate = {
                                    // TAMASHA — a deterministic watch-only demo on the real table. A fixed seed +
                                    // a lively mid-tier table; spectator=true makes the advisor auto-play seat 0.
                                    navController.navigate(
                                        Route.Game(
                                            seed = Today.epochDay() * 2654435761L + 1009L,
                                            players = 4,
                                            difficulty = Difficulty.Hard.name,
                                            humanCount = 1,
                                            spectator = true,
                                        ),
                                    ) { popUpTo(Route.Home) }
                                },
                                onDaily = {
                                    // Aaj ki Chunauti — deterministic per calendar date: the SAME day yields the
                                    // SAME (seed, lineup, difficulty) for everyone. Route straight to the table,
                                    // tagging dailyDay so game-over records the day's win/loss + streak.
                                    val ch = DailyChallenge.forDay(todayDay)
                                    navController.navigate(
                                        Route.Game(
                                            seed = ch.seed,
                                            players = ch.players,
                                            difficulty = ch.difficulty.name,
                                            humanCount = 1,
                                            dailyDay = todayDay,
                                        ),
                                    ) { popUpTo(Route.Home) }
                                },
                                resumeLabel = resumeLabel,
                                onResume = {
                                    resumeSnap?.let { snap ->
                                        navController.navigate(
                                            Route.Game(
                                                seed = snap.seed,
                                                players = snap.players,
                                                difficulty = snap.difficulty,
                                                humanCount = snap.humanCount,
                                            ),
                                        ) { popUpTo(Route.Home) }
                                    }
                                },
                            )
                            if (showTutorialOffer) {
                                TutorialOfferDialog(
                                    onAccept = {
                                        prefs.hasSeenTutorialOffer = true
                                        showTutorialOffer = false
                                        navController.navigate(Route.Tutorial)
                                    },
                                    onDecline = {
                                        prefs.hasSeenTutorialOffer = true
                                        showTutorialOffer = false
                                    },
                                )
                            }
                        }
                    }

                    // PEHLI HAZRI — M5 interactive tutorial, and (spec §6) the guided-funnel entry: a
                    // brand-new player lands here straight from Boot/Primer/ProfileSetup before ever seeing
                    // Home. [firstRun] is read BEFORE flipping hasSeenFunnel — true only on that first pass
                    // (the back stack is then exactly [Tutorial], so popUpTo(Tutorial) safely clears it and
                    // seeds FOCUS for their first real match, per spec §3/§6). A later replay from Home (the
                    // offer dialog / a Home button) has hasSeenFunnel already true, so onDone just pops back.
                    composable<Route.Tutorial> {
                        TutorialScreen(onDone = {
                            val firstRun = !prefs.hasSeenFunnel
                            prefs.hasSeenFunnel = true
                            prefs.hasSeenTutorialOffer = true
                            if (firstRun) {
                                prefs.densityLayerName = DensityLayer.FOCUS.name
                                navController.navigate(Route.Home) { popUpTo(Route.Tutorial) { inclusive = true } }
                            } else {
                                navController.popBackStack()
                            }
                        })
                    }

                    // M6e GAUNTLET — Tarakki ki Seedhi: the escalating promotion ladder.
                    composable<Route.Gauntlet> {
                        val gauntlet by prefs.gauntletFlow.collectAsState()
                        GauntletScreen(
                            progress = gauntlet,
                            onPlayRung = { rungIndex ->
                                val rung = GauntletLadder.rungAt(rungIndex)
                                navController.navigate(
                                    Route.Game(
                                        seed = rung.seed,
                                        players = rung.players,
                                        difficulty = rung.difficulty.name,
                                        humanCount = 1,
                                        gauntletRung = rung.index,
                                    ),
                                ) { popUpTo(Route.Gauntlet) }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }

                    // Career / Roznamcha register — persisted lifetime ledger.
                    composable<Route.Career> {
                        val ledger by prefs.ledgerFlow.collectAsState()
                        val decisionLedger by prefs.decisionLedgerFlow.collectAsState()
                        val recentRaw by prefs.recentMatchesFlow.collectAsState()
                        val recentMatches = remember(recentRaw) { CompletedMatch.decodeAll(recentRaw) }
                        val ranked by prefs.rankedFlow.collectAsState()
                        CareerScreen(
                            ledger = ledger,
                            decisionLedger = decisionLedger,
                            recentMatches = recentMatches,
                            onReview = { index -> navController.navigate(Route.Review(matchIndex = index)) },
                            ranked = ranked,
                            onLeaderboard = { navController.navigate(Route.Leaderboard) },
                            onBack = { navController.popBackStack() },
                        )
                    }

                    // M6d STANDINGS — local leaderboard: ranked ELO + rank tier + rating spark-line + daily
                    // streak. Reads only persisted AppPrefs (clean seam for the deferred M7 online table).
                    composable<Route.Leaderboard> {
                        val ranked by prefs.rankedFlow.collectAsState()
                        val daily by prefs.dailyFlow.collectAsState()
                        // M7 ONLINE TIE-IN — the server now serves GET /standings (:server Routing.kt), so this is
                        // wired LIVE: on entry we best-effort fetch the ladder. fetchStandings never throws and
                        // returns an empty list when offline / unreachable, in which case we keep `null` and the
                        // screen shows the honest "ranking pending" footer. A reachable server fills the connected
                        // OnlineStandingsCard (the connected, multi-row state already proven by the render harness).
                        val standings = remember { mutableStateOf<OnlineStandings?>(null) }
                        LaunchedEffect(Unit) {
                            val rows = fetchStandings(host = "127.0.0.1", port = 8080) // dev default; a deployment sets the real host
                            if (rows.isNotEmpty()) {
                                standings.value =
                                    OnlineStandings(
                                        connected = true,
                                        rows = rows.map { OnlineStandingRow(position = it.position, name = it.name, rating = it.rating) },
                                    )
                            }
                        }
                        LeaderboardScreen(
                            ranked = ranked,
                            daily = daily,
                            onBack = { navController.popBackStack() },
                            onlineStandings = standings.value,
                        )
                    }

                    // M6c REVIEW — replay a recorded match on the real table with a scrubber + advisor
                    // annotations. The route carries only the recent-matches index; we decode the record here.
                    composable<Route.Review> { entry ->
                        val r = entry.toRoute<Route.Review>()
                        val recentRaw by prefs.recentMatchesFlow.collectAsState()
                        val match =
                            remember(recentRaw, r.matchIndex) {
                                CompletedMatch.decode(recentRaw.getOrNull(r.matchIndex))
                            }
                        ReviewScreen(
                            match = match,
                            language = language,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    // S7 — Niyam Gazette (rules / who-beats-whom), a dialog leaf.
                    composable<Route.Gazette> {
                        NiyamGazette(
                            onDismiss = { navController.popBackStack() },
                            onReplayPrimer = { navController.navigate(Route.Primer) },
                        )
                    }

                    // S6 — Settings / Daftari.
                    composable<Route.Settings> {
                        SettingsScreen(
                            prefs = prefs,
                            onBack = { navController.popBackStack() },
                            onReplayPrimer = { navController.navigate(Route.Primer) },
                            onGazette = { navController.navigate(Route.Gazette) },
                            onEditProfile = { navController.navigate(Route.ProfileSetup(fromSettings = true)) },
                        )
                    }

                    // S2 — Setup / Requisition Form.
                    composable<Route.Setup> {
                        SetupScreen(
                            onBack = { navController.popBackStack() },
                            onNext = {
                                seed,
                                players,
                                difficulty,
                                humanCount,
                                teamCount,
                                narrative,
                                anarchy,
                                draftCode,
                                bail,
                                sabotage,
                                hawala,
                                emergency,
                                khazana,
                                khazanaTarget,
                                inflation,
                                scarcity,
                                ->
                                navController.navigate(
                                    Route.Lobby(
                                        seed = seed,
                                        players = players,
                                        difficulty = difficulty.name,
                                        humanCount = humanCount,
                                        teamCount = teamCount,
                                        narrativeEnabled = narrative,
                                        anarchy = anarchy,
                                        draftCode = draftCode,
                                        bailEnabled = bail,
                                        sabotageEnabled = sabotage,
                                        hawalaEnabled = hawala,
                                        emergencyEnabled = emergency,
                                        khazanaEnabled = khazana,
                                        khazanaTarget = khazanaTarget,
                                        inflationEnabled = inflation,
                                        scarcityEnabled = scarcity,
                                    ),
                                )
                            },
                            // M5 ONBOARD — quick-match + curated presets route through the SAME Lobby → Game
                            // path (1 human, vs-AI) so the deal stays deterministic and the roster previews.
                            onStartPreset = { seed, players, difficulty ->
                                navController.navigate(
                                    Route.Lobby(
                                        seed = seed,
                                        players = players,
                                        difficulty = difficulty.name,
                                        humanCount = 1,
                                    ),
                                )
                            },
                            // M7 — the three online modes route into the Online Mehfil hub.
                            onOnline = { navController.navigate(Route.OnlineHub) },
                            initialPlayers = prefs.defaultPlayerCount,
                            initialDifficulty = difficultyOf(prefs.defaultDifficulty),
                        )
                    }

                    // S-ONLINE — M7 Online Mehfil hub: create/join/quick-match/LAN-browse + waiting room.
                    // The OnlineHubController owns the connection; when the match starts it drives the in-game
                    // table through the OnlineGameAdapter (the Bridge), rendered by the SAME GameScreen.
                    composable<Route.OnlineHub> {
                        val scope = rememberCoroutineScope()
                        val controller = remember { OnlineHubController(scope = scope) }
                        DisposableEffect(Unit) { onDispose { controller.close() } }
                        val hubState by controller.uiState.collectAsState()

                        if (hubState.started && controller.adapter != null) {
                            // Match started — render the online table from the bridge adapter.
                            val adapter = controller.adapter!!
                            val onlineState by adapter.state.collectAsState()
                            val soundEnabled by prefs.soundFlow.collectAsState()
                            val reducedMotion by prefs.reducedMotionFlow.collectAsState()
                            val os = onlineState
                            if (os == null) {
                                TeakVoid()
                            } else {
                                GameScreen(
                                    state = os,
                                    onAction = adapter::onAction,
                                    soundEnabled = soundEnabled,
                                    reducedMotion = reducedMotion,
                                    showPrimerOverride = false,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else {
                            OnlineHubScreen(
                                state = hubState,
                                onBack = { navController.popBackStack() },
                                onCreatePrivate = { host, port, players -> controller.createPrivateRoom(host, port, players) },
                                onJoinByCode = { host, port, code -> controller.joinByCode(host, port, code) },
                                onQuickMatch = { host, port, players -> controller.quickMatch(host, port, players) },
                                onStartLanBrowse = { controller.startLanBrowse() },
                                onStopLanBrowse = { controller.stopLanBrowse() },
                                onJoinLanHost = { lan -> controller.joinLanHost(lan) },
                                onLeaveLobby = { controller.leaveLobby() },
                            )
                        }
                    }

                    // S3 — Lobby / Hazri Register (persona preview for this seed).
                    composable<Route.Lobby> { entry ->
                        val r = entry.toRoute<Route.Lobby>()
                        LobbyScreen(
                            seed = r.seed,
                            players = r.players,
                            difficulty = difficultyOf(r.difficulty),
                            humanCount = r.humanCount,
                            teamCount = r.teamCount,
                            onBack = { navController.popBackStack() },
                            onDealIn = { seed, players, difficulty ->
                                navController.navigate(
                                    Route.Game(
                                        seed = seed,
                                        players = players,
                                        difficulty = difficulty.name,
                                        humanCount = r.humanCount,
                                        teamCount = r.teamCount,
                                        narrativeEnabled = r.narrativeEnabled,
                                        draftCode = r.draftCode,
                                        anarchy = r.anarchy,
                                        bailEnabled = r.bailEnabled,
                                        sabotageEnabled = r.sabotageEnabled,
                                        hawalaEnabled = r.hawalaEnabled,
                                        emergencyEnabled = r.emergencyEnabled,
                                        khazanaEnabled = r.khazanaEnabled,
                                        khazanaTarget = r.khazanaTarget,
                                        inflationEnabled = r.inflationEnabled,
                                        scarcityEnabled = r.scarcityEnabled,
                                    ),
                                ) { popUpTo(Route.Home) }
                            },
                        )
                    }

                    // S4 — In-game / Mez. VM is route-scoped; one deterministic match per seed.
                    composable<Route.Game> { entry ->
                        val r = entry.toRoute<Route.Game>()
                        val vmScope = rememberCoroutineScope()
                        val vm =
                            remember(
                                r.seed,
                                r.players,
                                r.difficulty,
                                r.humanCount,
                                r.teamCount,
                                r.spectator,
                                r.narrativeEnabled,
                                r.storyArc,
                                r.draftCode,
                                r.anarchy,
                            ) {
                                // PROGRESSIVE-DISCLOSURE: map the enum-free prefs String flow to a typed
                                // DensityLayer flow here (the app layer), so feature/game stays free of a
                                // core:prefs dependency (mirrors the coachEnabledFlow wiring above).
                                GameViewModel(
                                    coachEnabledFlow = prefs.coachEnabledFlow,
                                    onCoachEnabledChange = { v -> prefs.coachEnabled = v },
                                    // BYOK: reads whichever cloud-provider key Settings has saved into
                                    // SecureKeyStore, so the narrator's cloud tier is actually reachable
                                    // (see com.kursi.ai.createMunshiNarrator's own kdoc).
                                    munshi = createMunshiNarrator(prefs.aiConsentGiven, prefs.aiSelectedProviderName),
                                    densityLayerFlow =
                                        prefs.densityLayerFlow
                                            .map { DensityLayer.fromName(it) }
                                            .stateIn(
                                                vmScope,
                                                SharingStarted.Eagerly,
                                                DensityLayer.fromName(prefs.densityLayerName),
                                            ),
                                    onDensityLayerChange = { v ->
                                        // A density change through this seam is always a deliberate player
                                        // choice (Settings, or any future in-game override) — flag it so the
                                        // graduation evaluator (spec §3) never fights it, per the "Settings can
                                        // always override" rule.
                                        prefs.densityLayerName = v.name
                                        prefs.densityLayerManuallySet = true
                                    },
                                    // M6e: a watch-only TAMASHA demo is NOT resumable — never persist its snapshot.
                                    onSnapshot = if (r.spectator) null else ({ snap: String? -> prefs.matchSnapshot = snap }),
                                    turnSpeedFlow = prefs.turnSpeedMultiplierFlow,
                                    autoPassFlow = prefs.autoPassFlow,
                                    autoPlayForcedFlow = prefs.autoPlayForcedFlow,
                                    // M6c REPLAY: record the finished match into the capped recent-matches store
                                    // so it can be reviewed/replayed deterministically afterward.
                                    onCompletedMatch = { match -> prefs.addRecentMatch(match.encode()) },
                                    // M6b: fold the per-game decision-quality tally into the lifetime ledger.
                                    onDecisionTally = { t ->
                                        prefs.recordDecisionTally(
                                            DecisionTally(
                                                decisions = t.decisions,
                                                matchedBest = t.matchedBest,
                                                evLostMilli = t.evLostMilli,
                                                challenges = t.challenges,
                                                challengesGood = t.challengesGood,
                                                bluffsTried = t.bluffsTried,
                                                bluffsOk = t.bluffsOk,
                                            ),
                                        )
                                    },
                                )
                            }
                        val state by vm.state.collectAsState()

                        LaunchedEffect(r) {
                            // RESUME: if a persisted snapshot matches THIS exact match (same seed/players),
                            // replay its human action log; otherwise start fresh. The route fully identifies
                            // the deal, so a stale snapshot for a different match is ignored. A spectator demo
                            // never resumes (it isn't persisted).
                            val snap =
                                if (r.spectator) {
                                    null
                                } else {
                                    MatchSnapshot
                                        .decode(prefs.matchSnapshot)
                                        ?.takeIf { it.seed == r.seed && it.players == r.players && it.humanCount == r.humanCount }
                                }
                            vm.onAction(
                                GameAction.NewGame(
                                    playerCount = r.players,
                                    difficulty = difficultyOf(r.difficulty),
                                    seed = r.seed,
                                    playerName = prefs.displayName,
                                    resumeLog = snap?.humanLog,
                                    humanCount = r.humanCount,
                                    teamCount = r.teamCount,
                                    spectator = r.spectator,
                                    narrativeEnabled = r.narrativeEnabled,
                                    storyArc = r.storyArc.ifEmpty { null },
                                    draftRoles = DraftPresets.rolesOf(r.draftCode.ifEmpty { null }),
                                    anarchy = r.anarchy,
                                    bailEnabled = r.bailEnabled,
                                    sabotageEnabled = r.sabotageEnabled,
                                    hawalaEnabled = r.hawalaEnabled,
                                    emergencyEnabled = r.emergencyEnabled,
                                    khazanaEnabled = r.khazanaEnabled,
                                    khazanaTarget = r.khazanaTarget,
                                    inflationEnabled = r.inflationEnabled,
                                    scarcityEnabled = r.scarcityEnabled,
                                ),
                            )
                        }

                        val s = state
                        if (s == null) {
                            TeakVoid()
                        } else {
                            // On game-over, let the win stamp play, then hand off to Results.
                            LaunchedEffect(s.isGameOver) {
                                if (s.isGameOver) {
                                    // In-progress snapshot is no longer valid — drop it so Home stops
                                    // offering a resume for a finished match.
                                    prefs.clearMatchSnapshot()
                                    val humanWon = s.winnerSeat == 0
                                    // M6e: a TAMASHA demo is a watch-only exhibition — it must NOT touch career
                                    // stats, ELO, the replay store, or the daily/gauntlet ladders. Just play the
                                    // win stamp and bounce back Home.
                                    if (r.spectator) {
                                        delay(1800)
                                        navController.navigate(Route.Home) { popUpTo(Route.Home) { inclusive = true } }
                                        return@LaunchedEffect
                                    }
                                    // M6e: a gauntlet bout records its rung result (promotion on a win).
                                    if (r.gauntletRung >= 0) {
                                        prefs.recordGauntletResult(rung = r.gauntletRung, won = humanWon)
                                    }
                                    // Fold the finished game into the lifetime career ledger (M3 §3).
                                    val opponentIds =
                                        s.opponentPersonas.values
                                            .map { personaIdOf(it.name) }
                                    prefs.recordGame(
                                        humanWon = humanWon,
                                        bluffsHeld = s.toMatchSummary(r.seed, r.players, difficultyOf(r.difficulty)).bluffsHeld,
                                        bluffsCaught = s.toMatchSummary(r.seed, r.players, difficultyOf(r.difficulty)).bluffsCaught,
                                        opponentIds = opponentIds,
                                    )
                                    // GRADUATION (spec §3) — after each real match, offer the pure evaluator the
                                    // fresh games-played + decision-quality read; it no-ops once the player has
                                    // manually chosen a density layer, and never advances past ANALYST.
                                    val dl = prefs.readDecisionLedger()
                                    val graduated =
                                        evaluateDensityGraduation(
                                            current = DensityLayer.fromName(prefs.densityLayerName),
                                            manuallySet = prefs.densityLayerManuallySet,
                                            gamesPlayed = prefs.readLedger().games,
                                            decisions = dl.decisions,
                                            accuracyPct = dl.accuracyPct,
                                            avgEvLostPct = dl.avgEvLostPct,
                                        )
                                    if (graduated.name != prefs.densityLayerName) {
                                        prefs.densityLayerName = graduated.name
                                    }
                                    // M6d — fold the result into the ranked ELO ladder: a 1-v-table bout where
                                    // the difficulty implies the opponent rating. Win always nudges up, loss down.
                                    val curRating = prefs.readRankedStanding().rating
                                    prefs.recordRankedResult(
                                        Elo.stepForGame(curRating, difficultyOf(r.difficulty), humanWon),
                                    )
                                    // M6d — if this was the Daily Challenge, record the day's win/loss + streak.
                                    if (r.dailyDay >= 0) {
                                        prefs.recordDailyResult(epochDay = r.dailyDay, won = humanWon)
                                    }
                                    delay(1800)
                                    // M6b: attach this game's decision-quality summary (from the live VM tally,
                                    // already reconciled + emitted by the VM at game-over) to the recap.
                                    val t = vm.decisionTally
                                    val dq =
                                        if (t.isEmpty) {
                                            null
                                        } else {
                                            com.kursi.shared.nav.MatchDecisionSummary(
                                                decisions = t.decisions,
                                                accuracyPct = t.accuracyPct,
                                                avgEvLostPct = if (t.decisions == 0) 0 else ((t.evLostMilli / 10.0) / t.decisions).toInt(),
                                                challenges = t.challenges,
                                                challengeAccuracyPct = if (t.challenges == 0) 0 else (t.challengesGood * 100 / t.challenges),
                                            )
                                        }
                                    val summary =
                                        s
                                            .toMatchSummary(r.seed, r.players, difficultyOf(r.difficulty))
                                            .copy(decisionSummary = dq, gauntletRung = r.gauntletRung)
                                    val id = MatchSummaryStore.put(summary)
                                    navController.navigate(Route.Results(id)) { popUpTo(Route.Home) }
                                }
                            }
                            val soundEnabled by prefs.soundFlow.collectAsState()
                            val reducedMotion by prefs.reducedMotionFlow.collectAsState()
                            GameScreen(
                                state = s,
                                onAction = vm::onAction,
                                onToggleCoach = vm::toggleCoach,
                                soundEnabled = soundEnabled,
                                reducedMotion = reducedMotion,
                                spectator = r.spectator,
                                humanDisplayName = vm.humanDisplayName,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    // KISSA — Story / narrative mode hub.
                    composable<Route.Story> {
                        StoryScreen(
                            onBack = { navController.popBackStack() },
                            onStart = { seed, players, difficulty, arc ->
                                navController.navigate(
                                    Route.Game(
                                        seed = seed,
                                        players = players,
                                        difficulty = difficulty.name,
                                        humanCount = 1,
                                        narrativeEnabled = true,
                                        storyArc = arc,
                                    ),
                                ) { popUpTo(Route.Home) }
                            },
                        )
                    }

                    // S5 — Results / Faisla (reads a serialized snapshot, not the live VM).
                    composable<Route.Results> { entry ->
                        val r = entry.toRoute<Route.Results>()
                        val summary = remember(r.matchId) { MatchSummaryStore.get(r.matchId) }
                        // M6c — the just-finished game is the most-recent recorded match (index 0). Offer Review
                        // only when such a record actually exists (it won't for the expired/empty state).
                        val recentRaw by prefs.recentMatchesFlow.collectAsState()
                        val hasReplayable = recentRaw.isNotEmpty()
                        ResultsScreen(
                            summary = summary,
                            onShare =
                                summary?.let { sm ->
                                    { certificate: ImageBitmap? ->
                                        val caption =
                                            buildShareText(sm.winnerName, sm.humanWon, sm.turnsTotal, sm.bluffsHeld)
                                        // Share the certificate itself when the platform can carry an image and
                                        // the capture came back; otherwise the text, which is what shipped before.
                                        // canShareImage() is checked rather than assumed: shareImage() is a no-op
                                        // on iOS, desktop and web today, and a share button that silently does
                                        // nothing is worse than one that shares a line of text.
                                        val png = certificate?.toPngBytes()
                                        if (png != null && canShareImage()) {
                                            shareImage(png, "kursi-faisla-${sm.matchId}", caption)
                                        } else {
                                            shareText(caption)
                                        }
                                    }
                                },
                            onReview =
                                if (hasReplayable) {
                                    { navController.navigate(Route.Review(matchIndex = 0)) }
                                } else {
                                    null
                                },
                            onRematch = {
                                // Only reachable when a summary exists (the expired state hides Rematch).
                                summary?.let { sm ->
                                    navController.navigate(
                                        Route.Game(
                                            seed = sm.seed + 1,
                                            players = sm.players,
                                            difficulty = sm.difficulty.name,
                                        ),
                                    ) { popUpTo(Route.Home) }
                                }
                            },
                            onNewGame = { navController.navigate(Route.Setup) { popUpTo(Route.Home) } },
                            onHome = {
                                navController.navigate(Route.Home) { popUpTo(Route.Home) { inclusive = true } }
                            },
                            onNextGauntletRung =
                                summary?.let { sm ->
                                    val nextIndex = sm.gauntletRung + 1
                                    if (sm.gauntletRung >= 0 && sm.humanWon && nextIndex <= GauntletLadder.lastIndex) {
                                        {
                                            val rung = GauntletLadder.rungAt(nextIndex)
                                            navController.navigate(
                                                Route.Game(
                                                    seed = rung.seed,
                                                    players = rung.players,
                                                    difficulty = rung.difficulty.name,
                                                    humanCount = 1,
                                                    gauntletRung = rung.index,
                                                ),
                                            ) { popUpTo(Route.Home) }
                                        }
                                    } else {
                                        null
                                    }
                                },
                        )
                    }
                }
            } // NavHost + Box
        } // CompositionLocalProvider
    }
}

/** Tolerant parse of a persisted difficulty name back to the enum. */
private fun difficultyOf(name: String): Difficulty = Difficulty.entries.firstOrNull { it.name == name } ?: Difficulty.Medium

/** Persona display name → stable id ("Netaji Vachan" → "netaji_vachan"), the ledger H2H key. */
private fun personaIdOf(name: String): String = name.lowercase().replace(" ", "_")

/** Empty teak field shown for the splash + the brief pre-state-ready game frame. */
@Composable
private fun TeakVoid() {
    Box(Modifier.fillMaxSize().background(BrandTokens.TeakInk))
}

private fun buildShareText(
    winnerName: String?,
    humanWon: Boolean,
    turns: Int,
    bluffsHeld: Int,
): String =
    buildString {
        if (humanWon) {
            append("Mujhe Kursi mil gayi! 🏆 ")
        } else {
            append("${winnerName ?: "Babu"} ne Kursi jeet li! ")
        }
        append("($turns turns, $bluffsHeld bluffs held)")
        append("\n#KursiGame")
    }
