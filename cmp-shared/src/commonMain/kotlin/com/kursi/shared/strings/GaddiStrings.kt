package com.kursi.shared.strings

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * All user-facing screen copy for Gaddi.
 * Two flavors: Hinglish (branded default) and English.
 */
sealed class GaddiStrings {
    // ── Common ────────────────────────────────────────────────────────────
    abstract val back: String

    // ── HomeScreen ────────────────────────────────────────────────────────
    abstract val homeRosterHeader: String
    abstract val homeTagline: String
    abstract val homeCtaNewGame: String
    abstract val homeCtaNewGameSub: String
    abstract val homeCtaRules: String
    abstract val homeCtaRulesSub: String
    abstract val homeCtaSettings: String
    abstract val homeCtaSettingsSub: String
    abstract val homeCtaMultiplayer: String
    abstract val homeCtaMultiplayerSub: String
    abstract val homeCtaMultiplayerBadge: String
    abstract val homeFooterLeft: String
    abstract val homeFooterRight: String
    abstract val homeResumeLabel: String

    // ── SetupScreen ───────────────────────────────────────────────────────
    abstract val setupTitle: String
    abstract val setupFormTitle: String
    abstract val setupFormBadge: String
    abstract val setupModeLabel: String
    abstract val setupModeSublabel: String

    abstract fun setupPlayerSublabel(count: Int): String

    abstract val setupPlayerSectionLabel: String
    abstract val setupDifficultyLabel: String
    abstract val setupDifficultySublabel: String
    abstract val setupCta: String
    abstract val setupCtaSub: String

    // Mode chip labels
    abstract val modeAILabel: String
    abstract val modeAISub: String
    abstract val modePrivateLabel: String
    abstract val modePrivateSub: String
    abstract val modeOpenLabel: String
    abstract val modeOpenSub: String
    abstract val modeLocalLabel: String
    abstract val modeLocalSub: String
    abstract val modeLanLabel: String
    abstract val modeLanSub: String
    abstract val modePendingBadge: String
    abstract val modeComingSoonBadge: String

    /** M7 — badge on the now-functional online modes ("ONLINE →"). */
    abstract val modeOnlineBadge: String

    // M5 pass-and-play human-count picker
    abstract val setupHumanCountLabel: String

    abstract fun setupHumanCountSublabel(
        humans: Int,
        players: Int,
    ): String

    // Difficulty tab nameplates + voice lines
    abstract val diffEasyName: String
    abstract val diffEasyVoice: String
    abstract val diffMediumName: String
    abstract val diffMediumVoice: String
    abstract val diffHardName: String
    abstract val diffHardVoice: String
    abstract val diffExpertName: String
    abstract val diffExpertVoice: String
    abstract val diffGrandmasterName: String
    abstract val diffGrandmasterVoice: String

    // ── LobbyScreen ───────────────────────────────────────────────────────
    abstract val lobbyHeader: String
    abstract val humanSeatName: String
    abstract val humanSeatTitle: String
    abstract val humanSeatArchetype: String
    abstract val humanSeatPersonality: String
    abstract val humanSeatBark: String
    abstract val lobbyReroll: String
    abstract val lobbyDealIn: String
    abstract val lobbyDealInSub: String

    // ── ResultsScreen ─────────────────────────────────────────────────────
    abstract val resultsTitle: String
    abstract val resultsVerdictSub: String
    abstract val resultsRecapHeader: String
    abstract val resultsRecapSurvived: String
    abstract val resultsRecapBluffsLanded: String
    abstract val resultsRecapBluffsCaught: String
    abstract val resultsRecapStandings: String
    abstract val resultsRecapWinnerSuffix: String
    abstract val resultsRecapSeal: String
    abstract val resultsWinStamp: String
    abstract val resultsRematch: String
    abstract val resultsRematchSub: String
    abstract val resultsNewGame: String
    abstract val resultsHome: String

    // ── M6b Decision-Quality dossier (Career) + Results recap ─────────────

    /** Section header for the decision-quality dossier on the Career screen. */
    abstract val dqHeader: String

    /** Label: % of moves matching the advisor's best. */
    abstract val dqAccuracyLabel: String

    /** Label: average win-probability bled per decision. */
    abstract val dqEvLostLabel: String

    /** Label: challenge discipline (% of challenges that were +EV). */
    abstract val dqChallengeLabel: String

    /** Label: bluff success (% of bluffs that survived uncaught). */
    abstract val dqBluffLabel: String

    /** Sub-line under the grade stamp, e.g. "N faisle taule gaye" / "graded over N decisions". */
    abstract fun dqSampleSub(decisions: Int): String

    /** In-voice grade names (bilingual). */
    abstract val dqGradeSharp: String
    abstract val dqGradeSteady: String
    abstract val dqGradeReckless: String
    abstract val dqGradeUnrated: String

    /** Short flavour line under each grade. */
    abstract val dqGradeSharpSub: String
    abstract val dqGradeSteadySub: String
    abstract val dqGradeRecklessSub: String
    abstract val dqGradeUnratedSub: String

    /** Results recap row label for the per-game decision summary. */
    abstract val resultsRecapDecision: String

    /** Per-game decision summary value, e.g. "72% best · 4% bled". */
    abstract fun resultsDecisionValue(
        accuracyPct: Int,
        evLostPct: Int,
    ): String

    // ── SettingsScreen ────────────────────────────────────────────────────
    abstract val settingsTitle: String
    abstract val settingsSoundSection: String
    abstract val settingsSoundLabel: String
    abstract val settingsSoundSub: String
    abstract val settingsMotionSection: String
    abstract val settingsMotionLabel: String
    abstract val settingsMotionSub: String
    abstract val settingsDefaultsSection: String
    abstract val settingsDefaultDiffLabel: String

    abstract fun settingsDefaultPlayersLabel(n: Int): String

    abstract val settingsLearningSection: String
    abstract val settingsReplayPrimerLabel: String
    abstract val settingsReplayPrimerSub: String
    abstract val settingsRulesLabel: String
    abstract val settingsRulesSub: String
    abstract val settingsAboutSection: String
    abstract val settingsAboutTitle: String
    abstract val settingsAboutDisclaimer: String
    abstract val settingsAboutFooter: String
    abstract val settingsLanguageSection: String
    abstract val settingsLangHinglish: String
    abstract val settingsLangEnglish: String
    abstract val settingsCoachSection: String
    abstract val settingsCoachLabel: String
    abstract val settingsCoachSub: String

    // M5 auto-mode / assistant
    abstract val settingsAutoSection: String
    abstract val settingsTurnSpeedLabel: String
    abstract val settingsSpeedSlow: String
    abstract val settingsSpeedNormal: String
    abstract val settingsSpeedFast: String
    abstract val settingsAutoPassLabel: String
    abstract val settingsAutoPassSub: String
    abstract val settingsAutoForcedLabel: String
    abstract val settingsAutoForcedSub: String

    // ── M5 ONBOARD — Interactive Tutorial (Pehli Hazri) ───────────────────────
    abstract val homeCtaTutorial: String
    abstract val homeCtaTutorialSub: String

    /** First-run offer dialog shown after the primer. */
    abstract val tutorialOfferTitle: String
    abstract val tutorialOfferBody: String
    abstract val tutorialOfferAccept: String
    abstract val tutorialOfferDecline: String
    abstract val tutorialHeader: String
    abstract val tutorialBadge: String
    abstract val tutorialStepLabel: String // e.g. "BEAT" / "PEHLU"
    abstract val tutorialCoachTag: String // narrator nameplate, e.g. "SALAHKAAR"
    abstract val tutorialNext: String
    abstract val tutorialBack: String
    abstract val tutorialSkip: String
    abstract val tutorialFinish: String
    abstract val tutorialDoIt: String // the action-prompt CTA on the bluff (claim) beat
    abstract val tutorialDoBlock: String // the action-prompt CTA on the block beat
    abstract val tutorialDoCoup: String // the action-prompt CTA on the coup beat
    abstract val tutorialDoExchange: String // the action-prompt CTA on the exchange beat

    /** 11 scripted teaching beats: title + body. Bilingual, deadpan sarkari voice. Mechanic-at-a-time
     *  per spec §6: claim (tut5) → challenge (tut6) → reveal (tut7) → block → coup → exchange → tut8. */
    abstract val tut1Title: String
    abstract val tut1Body: String
    abstract val tut2Title: String
    abstract val tut2Body: String
    abstract val tut3Title: String
    abstract val tut3Body: String
    abstract val tut4Title: String
    abstract val tut4Body: String
    abstract val tut5Title: String
    abstract val tut5Body: String // YOU act (Ghotala / claim)
    abstract val tut6Title: String
    abstract val tut6Body: String // rival challenges
    abstract val tut7Title: String
    abstract val tut7Body: String // reveal — JHOOTH (bluff caught)
    abstract val tutBlockTitle: String
    abstract val tutBlockBody: String // YOU act — VAKIL blocks a SUPARI
    abstract val tutCoupTitle: String
    abstract val tutCoupBody: String // YOU act — KHELA (coup)
    abstract val tutExchangeTitle: String
    abstract val tutExchangeBody: String // YOU act — SETTING (exchange)
    abstract val tut8Title: String
    abstract val tut8Body: String // graduation

    // ── M5 ONBOARD — Setup presets / quick-match ──────────────────────────────
    abstract val setupQuickMatchLabel: String
    abstract val setupQuickMatchSub: String
    abstract val setupPresetSectionLabel: String
    abstract val setupPresetSectionSub: String
    abstract val presetCabinetName: String
    abstract val presetCabinetSub: String
    abstract val presetSnakePitName: String
    abstract val presetSnakePitSub: String
    abstract val presetChaosName: String
    abstract val presetChaosSub: String
    abstract val setupPresetRequisition: String // tiny corner stamp on each preset card

    // ── M6e GAUNTLET — escalating promotion ladder (Tarakki ki Seedhi) ─────────
    abstract val homeCtaGauntlet: String
    abstract val homeCtaGauntletSub: String

    /** Home strip when a ladder run is in progress: "Rung X of Y · <nameplate>". */
    abstract fun gauntletStripLabel(
        cleared: Int,
        total: Int,
    ): String

    abstract val gauntletHeader: String
    abstract val gauntletBadge: String
    abstract val gauntletTagline: String // screen sub / mission statement
    abstract val gauntletStartCta: String // play the current target rung
    abstract val gauntletStartCtaSub: String
    abstract val gauntletReplayCta: String // re-run an already-cleared rung
    abstract val gauntletLockedTag: String // a rung not yet reachable
    abstract val gauntletClearedTag: String // a beaten rung
    abstract val gauntletCurrentTag: String // the active target rung
    abstract val gauntletConqueredTitle: String // whole ladder beaten
    abstract val gauntletConqueredBody: String

    /** Rung nameplates (5 sarkari posts), 0-based by ladder index. */
    abstract val gauntletRung0Name: String
    abstract val gauntletRung1Name: String
    abstract val gauntletRung2Name: String
    abstract val gauntletRung3Name: String
    abstract val gauntletRung4Name: String

    // ── M6e SPECTATOR / TAMASHA — watch-only demo game ────────────────────────
    abstract val homeCtaSpectate: String
    abstract val homeCtaSpectateSub: String
    abstract val spectatorBanner: String // in-game banner: "TAMASHA — watch only"

    // ── M6e TEAM KHEL — the teams variant ──────────────────────────────────────
    abstract val setupTeamLabel: String // toggle section label
    abstract val setupTeamSublabel: String
    abstract val setupTeamToggleOn: String // "TEAM KHEL · ON"
    abstract val setupTeamToggleOff: String // classic free-for-all

    /** Team names by team id (0/1). Shown on plates + lobby. */
    abstract val teamNameA: String
    abstract val teamNameB: String
    abstract val lobbyTeamHeader: String // lobby team-roster section header

    abstract fun teamBadge(teamId: Int): String // tiny plate badge text per team

    // ── M6c REVIEW — replay scrubber + advisor annotations (teach-by-review) ───
    abstract val reviewHeader: String // screen title
    abstract val reviewBadge: String // corner stamp

    /** Results / Career CTA into Review. */
    abstract val reviewCta: String
    abstract val reviewCtaSub: String

    /** Recent-matches list section (Career) header + sub. */
    abstract val reviewRecentHeader: String
    abstract val reviewRecentSub: String
    abstract val reviewRecentEmpty: String

    /** One recent-match row caption, e.g. "4 khiladi · Hard · 12 chaal". */
    abstract fun reviewMatchCaption(
        players: Int,
        difficulty: String,
        moves: Int,
    ): String

    /** Win / loss tag on a recent-match row. */
    abstract val reviewMatchWon: String
    abstract val reviewMatchLost: String

    /** Scrubber transport labels (a11y + tooltips). */
    abstract val reviewPlay: String
    abstract val reviewPause: String
    abstract val reviewStepBack: String
    abstract val reviewStepForward: String
    abstract val reviewPrevDecision: String
    abstract val reviewNextDecision: String

    /** Step counter, e.g. "Padaav 3 / 11" / "Step 3 / 11". */
    abstract fun reviewStepCounter(
        current: Int,
        total: Int,
    ): String

    /** Timeline section label. */
    abstract val reviewTimeline: String

    /** Terminal (game-over) frame caption in the scrubber. */
    abstract val reviewTerminal: String

    /** Annotation panel header + sub. */
    abstract val reviewAdvisorHeader: String
    abstract val reviewAdvisorSub: String

    /** Annotation rows: what you played vs the recommended best. */
    abstract val reviewPlayedLabel: String
    abstract val reviewBestLabel: String
    abstract val reviewEvGapLabel: String

    /** EV-gap value, e.g. "−6% jeet ki sambhavna" / "−6% win chance". */
    abstract fun reviewEvGapValue(pct: Int): String

    abstract val reviewMatchedBest: String // shown when the gap is zero

    /** Verdict badge words (bilingual). */
    abstract val reviewVerdictSharp: String
    abstract val reviewVerdictFine: String
    abstract val reviewVerdictLoose: String
    abstract val reviewVerdictCostly: String

    /** Back-to-results / back affordance label on Review. */
    abstract val reviewBack: String

    // ── M6d RANKED ELO ladder + sarkari rank tiers ────────────────────────

    /** Bilingual sarkari rank-tier names, in ladder order (Clerk → Cabinet Secretary). */
    abstract val rankClerk: String
    abstract val rankSectionOfficer: String
    abstract val rankUnderSecretary: String
    abstract val rankDeputySecretary: String
    abstract val rankJointSecretary: String
    abstract val rankSecretary: String
    abstract val rankCabinetSecretary: String

    /** Compact Home ranked-strip leading tag (e.g. "DARJA" / "RANK"). */
    abstract val rankedStripTag: String

    /** "rating" caption word (e.g. "ank" / "rating"). */
    abstract val rankedRatingWord: String

    /** Sub on the ranked strip before any ranked game (provisional). */
    abstract val rankedProvisional: String

    /** "to next rank" hint, e.g. "Secretary tak 40 ank" / "40 to Secretary". */
    abstract fun rankedToNext(
        points: Int,
        nextRank: String,
    ): String

    /** Top-tier hint when already Cabinet Secretary. */
    abstract val rankedTopTier: String

    // ── M6d DAILY CHALLENGE (Aaj ki Chunauti) ─────────────────────────────
    abstract val dailyCta: String
    abstract val dailyCtaSub: String

    /** Sub shown once today's daily is already done. */
    abstract val dailyDoneSub: String

    /** Streak caption, e.g. "🔥 5 din ki laga" / "🔥 5-day streak". */
    abstract fun dailyStreakLabel(streak: Int): String

    /** Badge shown on the daily entry when today is still open. */
    abstract val dailyOpenBadge: String

    /** Badge shown when today's daily is completed. */
    abstract val dailyDoneBadge: String

    // ── M6d LEADERBOARD / STANDINGS screen ────────────────────────────────
    abstract val leaderboardCta: String
    abstract val leaderboardCtaSub: String
    abstract val leaderboardHeader: String
    abstract val leaderboardBack: String
    abstract val leaderboardRankSection: String
    abstract val leaderboardPeakLabel: String
    abstract val leaderboardRatingLabel: String
    abstract val leaderboardGamesLabel: String
    abstract val leaderboardHistorySection: String
    abstract val leaderboardHistoryEmpty: String
    abstract val leaderboardDailySection: String
    abstract val leaderboardStreakLabel: String
    abstract val leaderboardBestStreakLabel: String
    abstract val leaderboardDailyWinsLabel: String
    abstract val leaderboardEmpty: String

    /** Footer noting server standings are pending (the M7 seam). */
    abstract val leaderboardServerPending: String

    /** M7 — the live online-standings header shown when the hub is connected to a server. */
    abstract val leaderboardOnlineSection: String
    abstract val leaderboardOnlineConnectedSub: String
    abstract val leaderboardOnlineOfflineSub: String

    // ── Story / Narrative mode (KISSA) ────────────────────────────────────
    abstract val homeCtaStory: String
    abstract val homeCtaStorySub: String
    abstract val storyTitle: String
    abstract val storyTagline: String
    abstract val storyFreeLabel: String
    abstract val storyStartCta: String

    // ── Setup new toggles (Darbar / Anarchy / Draft) ───────────────────────
    abstract val setupDarbarLabel: String
    abstract val setupDarbarSub: String
    abstract val setupAnarchyLabel: String
    abstract val setupAnarchySub: String
    abstract val setupDraftLabel: String
    abstract val setupDraftSub: String

    // ── OnlineHub (M7) ─────────────────────────────────────────────────────
    abstract val onlineHubTitle: String
    abstract val onlineHubHeader: String
    abstract val onlineHubServerLabel: String
    abstract val onlineHubServerHint: String
    abstract val onlineCreateLabel: String
    abstract val onlineCreateSub: String
    abstract val onlineJoinLabel: String
    abstract val onlineJoinSub: String
    abstract val onlineJoinCodeHint: String
    abstract val onlineJoinCta: String
    abstract val onlineQuickLabel: String
    abstract val onlineQuickSub: String
    abstract val onlineLanLabel: String
    abstract val onlineLanSub: String
    abstract val onlineLanBrowseCta: String
    abstract val onlineLanSearching: String
    abstract val onlineLanEmpty: String
    abstract val onlinePlayersLabel: String
    abstract val onlineWorking: String

    // Lobby / waiting room
    abstract val onlineLobbyHeader: String
    abstract val onlineLobbyShareLabel: String
    abstract val onlineLobbyConnecting: String
    abstract val onlineLobbyWaiting: String
    abstract val onlineLobbyLost: String
    abstract val onlineLobbyLeave: String

    abstract fun onlineLobbySeated(seat: Int): String

    abstract fun onlineLobbyRoster(
        joined: Int,
        total: Int,
    ): String

    // ─────────────────────────────────────────────────────────────────────
    object Hinglish : GaddiStrings() {
        override val back = "← Wapas"
        override val homeRosterHeader = "AAJ KI HAAZRI"
        override val homeTagline = "sab kuch gaddi ka khel hai."
        override val homeCtaNewGame = "TAKE THE CHAIR"
        override val homeCtaNewGameSub = "Single Player · vs. the Cabinet"
        override val homeCtaRules = "PADHIYE NIYAM"
        override val homeCtaRulesSub = "The NIYAM Gazette — who beats whom"
        override val homeCtaSettings = "DAFTAR / SETTINGS"
        override val homeCtaSettingsSub = "Sound, motion, difficulty"
        override val homeCtaMultiplayer = "MULTIPLAYER MEHFIL"
        override val homeCtaMultiplayerSub = "Online + LAN · create ya join karo"
        override val homeCtaMultiplayerBadge = "PENDING SANCTION"
        override val homeFooterLeft = "Pradhan Kaaryalay · Est. whenever convenient."
        override val homeFooterRight = "Saare paatra kaalpanik hain."
        override val homeResumeLabel = "KHEL JAARI RAKHO"

        override val setupTitle = "NAYA KHEL"
        override val setupFormTitle = "FORM 1 — REQUISITION FOR KHEL"
        override val setupFormBadge = "FORM 1-A THROUGH 1-C"
        override val setupModeLabel = "FORM 1-A: SAMPARK KA TARIKA"
        override val setupModeSublabel = "How will you govern today?"

        override fun setupPlayerSublabel(count: Int) = "Aaj $count gaddiyaan: aap + ${count - 1} babu."

        override val setupPlayerSectionLabel = "FORM 1-B: KITNE LOG MEZ PAR"
        override val setupDifficultyLabel = "FORM 1-C: BABU KA TAJURBA"
        override val setupDifficultySublabel = "Choose your adversaries' calibre."
        override val setupCta = "AAGE BADHO →"
        override val setupCtaSub = "Generate roster · proceed to Hazri Register"
        override val modeAILabel = "AKELE BABU KE KHILAAF"
        override val modeAISub = "vs the office — offline AI"
        override val modePrivateLabel = "PRIVATE KAMRA"
        override val modePrivateSub = "dost ko code bhejo — apna kamra"
        override val modeOpenLabel = "KHULI BOLI"
        override val modeOpenSub = "public match — koi bhi mil jaaye"
        override val modeLocalLabel = "EK HI PHONE"
        override val modeLocalSub = "pass & play — ek hi phone, baari-baari"
        override val modeLanLabel = "EK HI LAN"
        override val modeLanSub = "aas-paas ke kamre — wahi network"
        override val modeOnlineBadge = "ONLINE →"
        override val modePendingBadge = "MUHAR PENDING"
        override val modeComingSoonBadge = "JALD AANE WAALA"
        override val setupHumanCountLabel = "KITNE INSAAN?"

        override fun setupHumanCountSublabel(
            humans: Int,
            players: Int,
        ) = "$humans insaan baari-baari, baaki ${players - humans} ko computer sambhaalega."

        override val diffEasyName = "NAYA BHARTI"
        override val diffEasyVoice = "Abhi training mein hai. Form bhar deta hai, bina padhe."
        override val diffMediumName = "PERMANENT BABU"
        override val diffMediumVoice = "10 saal se isi gaddi pe. Rulebook ratta hai."
        override val diffHardName = "SECTION OFFICER"
        override val diffHardVoice = "Jhooth sungh leta hai."
        override val diffExpertName = "HEAD CLERK SAAB"
        override val diffExpertVoice = "Aapki chaal pehle se likh chuka hai."
        override val diffGrandmasterName = "CABINET SECRETARY"
        override val diffGrandmasterVoice = "Aapki aadat jaanta hai — usi se haraayega."

        override val lobbyHeader = "HAZRI REGISTER — aaj ki upasthiti"
        override val humanSeatName = "Aap"
        override val humanSeatTitle = "The Human Player"
        override val humanSeatArchetype = "Unknown quantity"
        override val humanSeatPersonality = "Human · seat 0 · your rules"
        override val humanSeatBark = "Sab kuch main sambhal lunga."
        override val lobbyReroll = "🎲 DOBARA BULAO"
        override val lobbyDealIn = "MUHAR LAGAO ✦"
        override val lobbyDealInSub = "Deal in — stamp the roster"

        override val resultsTitle = "FAISLA — Verdict Certificate"
        override val resultsVerdictSub = "Faisla surakshit. File band."
        override val resultsRecapHeader = "ROZNAMCHA — Khel ki Report"
        override val resultsRecapSurvived = "Aap kitni der tike"
        override val resultsRecapBluffsLanded = "Jhooth jo chal gaya"
        override val resultsRecapBluffsCaught = "Rangey haath pakde gaye"
        override val resultsRecapStandings = "Antim Suchi — Final Standings:"
        override val resultsRecapWinnerSuffix = " ← GADDI HAASIL"
        override val resultsRecapSeal = "✦ FAISLA MUHAR · दर्ज ✦"
        override val resultsWinStamp = "GADDI HAASIL"
        override val resultsRematch = "DUBARA — REMATCH"
        override val resultsRematchSub = "Same roster · new seed · next round"
        override val resultsNewGame = "NAYA KHEL"
        override val resultsHome = "DAFTAR"

        // M6b decision-quality (Hinglish)
        override val dqHeader = "FAISLA TAULA — Decision Quality"
        override val dqAccuracyLabel = "Sahi chaal"
        override val dqEvLostLabel = "Mauka gawaaya"
        override val dqChallengeLabel = "Lalkaar sahi"
        override val dqBluffLabel = "Jhooth chala"

        override fun dqSampleSub(decisions: Int) = "$decisions faisle taule gaye"

        override val dqGradeSharp = "TEZ BABU"
        override val dqGradeSteady = "PAKKA HAATH"
        override val dqGradeReckless = "BEPARWAH"
        override val dqGradeUnrated = "ABHI KACCHA"
        override val dqGradeSharpSub = "Chaalein tez, mauke kam gawaaye."
        override val dqGradeSteadySub = "Theek-thaak — soch ke khelte ho."
        override val dqGradeRecklessSub = "Behtar chaalein chhoot rahi hain."
        override val dqGradeUnratedSub = "Aur khelo — phir grade lagega."
        override val resultsRecapDecision = "Is khel mein faisle"

        override fun resultsDecisionValue(
            accuracyPct: Int,
            evLostPct: Int,
        ) = "$accuracyPct% sahi · $evLostPct% gawaaya"

        override val settingsTitle = "DAFTARI — Settings"
        override val settingsSoundSection = "AAWAZ — Sound"
        override val settingsSoundLabel = "Master Sound"
        override val settingsSoundSub = "Stamps, barks, and effects"
        override val settingsMotionSection = "DIKHAWA — Motion"
        override val settingsMotionLabel = "KAM HARAKAT"
        override val settingsMotionSub = "Reduced motion — disables stamp animations"
        override val settingsDefaultsSection = "KHEL — Defaults"
        override val settingsDefaultDiffLabel = "Default Difficulty"

        override fun settingsDefaultPlayersLabel(n: Int) = "Default Players: $n"

        override val settingsLearningSection = "SEEKH — Learning"
        override val settingsReplayPrimerLabel = "PRIMER DOBARA DIKHAO"
        override val settingsReplayPrimerSub = "Replay the first-run onboarding"
        override val settingsRulesLabel = "NIYAM GAZETTE"
        override val settingsRulesSub = "Full rules and who-beats-whom matrix"
        override val settingsAboutSection = "BAARE MEIN — About"
        override val settingsAboutTitle = "Gaddi — a satirical bluffing game"
        override val settingsAboutDisclaimer =
            "\"Saare paatra kaalpanik hain — All characters are fictional. Any resemblance to actual babus, netas, or " +
                "inspectors is purely coincidental and deeply regrettable.\""
        override val settingsAboutFooter = "Pradhan Kaaryalay · Est. whenever convenient."
        override val settingsLanguageSection = "BHASHA — Language"
        override val settingsLangHinglish = "HINGLISH"
        override val settingsLangEnglish = "ENGLISH"
        override val settingsCoachSection = "MASHWARA — Decision Coach"
        override val settingsCoachLabel = "MASHWARA ON"
        override val settingsCoachSub = "Salahkaar ki salaah + best move dikhaao"
        override val settingsAutoSection = "AUTO-MODE — Khud-ba-khud"
        override val settingsTurnSpeedLabel = "Chaal ki raftaar"
        override val settingsSpeedSlow = "DHEEMA"
        override val settingsSpeedNormal = "AAM"
        override val settingsSpeedFast = "TEZ"
        override val settingsAutoPassLabel = "Auto-pass"
        override val settingsAutoPassSub = "Sirf Pass bacha ho toh khud pass kar do"
        override val settingsAutoForcedLabel = "Majboori auto-khelo"
        override val settingsAutoForcedSub = "Forced chaal (10+ coin pe Khela) khud chal do"

        // ── M5 ONBOARD — Tutorial (Pehli Hazri) ──
        override val homeCtaTutorial = "PEHLI HAZRI"
        override val homeCtaTutorialSub = "Tutorial — seekhiye karke, ek hi baari mein"
        override val tutorialOfferTitle = "Pehli Hazri darj karein?"
        override val tutorialOfferBody =
            "Nayi naukri, nayi gaddi. Ek chhoti training baari — Salahkaar saath chalega, ek jhooth pakda jaayega. Dekh ke " +
                "seekhiye."
        override val tutorialOfferAccept = "HAAN, SIKHAO"
        override val tutorialOfferDecline = "Nahi, seedha khel"
        override val tutorialHeader = "PEHLI HAZRI — Training Baari"
        override val tutorialBadge = "PRASHIKSHAN · GUPT"
        override val tutorialStepLabel = "PEHLU"
        override val tutorialCoachTag = "SALAHKAAR"
        override val tutorialNext = "AAGE →"
        override val tutorialBack = "← Peeche"
        override val tutorialSkip = "Training chhodo →"
        override val tutorialFinish = "HAZRI POORI ✦"
        override val tutorialDoIt = "GHOTALA STAMP KARO"
        override val tutorialDoBlock = "VAKIL ROKO"
        override val tutorialDoCoup = "KHELA STAMP KARO"
        override val tutorialDoExchange = "SETTING KARO"
        override val tut1Title = "Do Certificate, Do Jaan"
        override val tut1Body = "Ye do gupt parchiyaan aapki asli pehchaan hain. Dono gayi, toh aap gaddi se bahar. Inhe chhupa ke rakhiye."
        override val tut2Title = "Khokha Hi Taqat Hai"
        override val tut2Body = "Sikke = power. 7 pe Khela (hit) khareeda jaata hai, 10 pe majboori. Ginti par nazar rakhiye."
        override val tut3Title = "Daava Karo — Sach Ho Ya Na Ho"
        override val tut3Body =
            "Har chaal ek role ka daava hai. Sabse mazedaar baat: aap ke paas wo role ho, ye zaroori nahi. Bluff " +
                "allowed hai."
        override val tut4Title = "Salahkaar Kyun Bataata Hai"
        override val tut4Body = "Mashwara ON ho, toh har option pe ⭐ best move aur 'kitne % chalega' dikhega. Ye aapka babu mukhbir hai."
        override val tut5Title = "Ab Aapki Baari: GHOTALA"
        override val tut5Body = "GHOTALA, NETA hone ka daava — +3 khokha. Sach? Koi nahi jaanta. Stamp lagaiye aur dekhiye kya hota hai."
        override val tut6Title = "Babu Filewala ne Challenge Kiya!"
        override val tut6Body =
            "\"Tumhare paas NETA hai? Dikhao.\" Challenge ka matlab: card khol ke saabit karo. Jhooth pakda gaya toh " +
                "card jaayega."
        override val tut7Title = "JHOOTH! Card Khul Gaya"
        override val tut7Body =
            "Aapke paas NETA tha hi nahi — bluff rangey haath pakda gaya. Ek certificate kurbaan. Yahi hai khel ka dil: daava, " +
                "challenge, " +
                "faisla."
        override val tutBlockTitle = "VAKIL Rokta Hai"
        override val tutBlockBody =
            "Netaji Vachan SUPARI bol raha hai — aapki jaan lene ka daava. VAKIL block karta hai. Ek tap, khatra tal gaya."
        override val tutCoupTitle = "KHELA — Seedha Vaar"
        override val tutCoupBody =
            "7 khokha ho toh KHELA khareediye. Na block, na challenge — seedha ek influence chhinti hai. 10 pe " +
                "majboori."
        override val tutExchangeTitle = "SETTING — Hath Badaliye"
        override val tutExchangeBody =
            "SETTING se 2 naye card khinchte hain, best do rakh ke baaki laut dete hain. Koi nahi jaanta kya " +
                "badla."
        override val tut8Title = "Hazri Poori, Sarkar"
        override val tut8Body =
            "Ab aap sab jaante hain — daava, challenge, block, khela, aur setting. Gaddi pe baithne ka waqt aa gaya."

        // ── M5 ONBOARD — Presets / quick-match ──
        override val setupQuickMatchLabel = "TURANT KHEL"
        override val setupQuickMatchSub = "Ek tap — aapki purani setting se seedha mez par"
        override val setupPresetSectionLabel = "BANI-BANAYI REQUISITION"
        override val setupPresetSectionSub = "Curated roster — ek muhar, poora khel taiyaar."
        override val presetCabinetName = "POORANI CABINET"
        override val presetCabinetSub = "4 khiladi · mila-jula · Permanent Babu"
        override val presetSnakePitName = "SAANP KA PINJRA"
        override val presetSnakePitSub = "4 khiladi · aakraamak chaal · Section Officer"
        override val presetChaosName = "AFRA-TAFRI · 10"
        override val presetChaosSub = "10 khiladi · poori mehfil · Permanent Babu"
        override val setupPresetRequisition = "FORM 1 · TAIYAAR"

        // ── M6e GAUNTLET ──
        override val homeCtaGauntlet = "TARAKKI KI SEEDHI"
        override val homeCtaGauntletSub = "Gauntlet — ek-ek karke har tier ko harao, promotion lo"

        override fun gauntletStripLabel(
            cleared: Int,
            total: Int,
        ) = "Seedhi: $cleared / $total paaydaan paar"

        override val gauntletHeader = "TARAKKI KI SEEDHI"
        override val gauntletBadge = "PADONNATI FILE"
        override val gauntletTagline = "Probationer se Cabinet tak. Har paaydaan jeeto, agli gaddi paao."
        override val gauntletStartCta = "AGLI PARIKSHA DO"
        override val gauntletStartCtaSub = "Is paaydaan ki mez ko harao"
        override val gauntletReplayCta = "PHIR SE KHELO"
        override val gauntletLockedTag = "BAND · pehle pichla paar karo"
        override val gauntletClearedTag = "PAAR · muhar lagi"
        override val gauntletCurrentTag = "ABHI YAHIN"
        override val gauntletConqueredTitle = "POORI SEEDHI FATEH"
        override val gauntletConqueredBody =
            "Cabinet Secretary saab. Sabse oonchi gaddi aapki. Phir bhi koi paaydaan dobara khel sakte " +
                "hain."
        override val gauntletRung0Name = "PROBATIONER"
        override val gauntletRung1Name = "BABU"
        override val gauntletRung2Name = "SECTION OFFICER"
        override val gauntletRung3Name = "SECRETARY"
        override val gauntletRung4Name = "CABINET SECRETARY"

        // ── M6e SPECTATOR / TAMASHA ──
        override val homeCtaSpectate = "TAMASHA"
        override val homeCtaSpectateSub = "Demo — bina khele, asli mez par khel dekho"
        override val spectatorBanner = "TAMASHA — sirf dekhne ke liye"

        // ── M6e TEAM KHEL ──
        override val setupTeamLabel = "FORM 1-D: TEAM KHEL"
        override val setupTeamSublabel = "Mez do dhadon mein bant jaaye — aakhri dhada jeete."
        override val setupTeamToggleOn = "TEAM KHEL · CHALU"
        override val setupTeamToggleOff = "Har koi apne liye (classic)"
        override val teamNameA = "SAT PAKSH"
        override val teamNameB = "VIPAKSH"
        override val lobbyTeamHeader = "DHADE — kaun kiske saath"

        override fun teamBadge(teamId: Int) = if (teamId == 0) "SAT" else "VIP"

        // M6c REVIEW
        override val reviewHeader = "SAMEEKSHA — Khel Ki Sameeksha"
        override val reviewBadge = "PUNAR-NIRIKSHAN"
        override val reviewCta = "IS KHEL KI SAMEEKSHA"
        override val reviewCtaSub = "Chaal-dar-chaal — Salahkaar ke saath"
        override val reviewRecentHeader = "PICHHLE KHEL"
        override val reviewRecentSub = "Daftar mein darj — kisi bhi file ko dobara kholo"
        override val reviewRecentEmpty = "Abhi koi file band nahi hui. Ek khel khatam karo, phir yahaan milega."

        override fun reviewMatchCaption(
            players: Int,
            difficulty: String,
            moves: Int,
        ) = "$players khiladi · $difficulty · $moves chaal"

        override val reviewMatchWon = "JEET"
        override val reviewMatchLost = "HAAR"
        override val reviewPlay = "Chalao"
        override val reviewPause = "Roko"
        override val reviewStepBack = "Ek chaal peechhe"
        override val reviewStepForward = "Ek chaal aage"
        override val reviewPrevDecision = "Pichhla faisla"
        override val reviewNextDecision = "Agla faisla"

        override fun reviewStepCounter(
            current: Int,
            total: Int,
        ) = "Padaav $current / $total"

        override val reviewTimeline = "CHAAL-PATTI"
        override val reviewTerminal = "FAISLA — khel khatam"
        override val reviewAdvisorHeader = "SALAHKAAR KI RAAY"
        override val reviewAdvisorSub = "Us pal ka hisaab — jo khela vs jo behtar tha"
        override val reviewPlayedLabel = "Aapne khela"
        override val reviewBestLabel = "Behtar chaal"
        override val reviewEvGapLabel = "Faasla"

        override fun reviewEvGapValue(pct: Int) = "−$pct% jeet ki sambhavna"

        override val reviewMatchedBest = "Behtareen chaal — Salahkaar bhi yahi khelta."
        override val reviewVerdictSharp = "TEZ"
        override val reviewVerdictFine = "THEEK"
        override val reviewVerdictLoose = "DHEELA"
        override val reviewVerdictCostly = "MEHNGA"
        override val reviewBack = "‹ FAISLA"

        // ── M6d RANKED ELO ladder + sarkari rank tiers (Hinglish) ──
        override val rankClerk = "BABU / CLERK"
        override val rankSectionOfficer = "SECTION OFFICER"
        override val rankUnderSecretary = "UNDER SECRETARY"
        override val rankDeputySecretary = "DEPUTY SECRETARY"
        override val rankJointSecretary = "JOINT SECRETARY"
        override val rankSecretary = "SECRETARY"
        override val rankCabinetSecretary = "CABINET SECRETARY"
        override val rankedStripTag = "DARJA"
        override val rankedRatingWord = "ank"
        override val rankedProvisional = "Pehli ranked baazi khelo — darja tay hoga."

        override fun rankedToNext(
            points: Int,
            nextRank: String,
        ) = "$nextRank tak $points ank"

        override val rankedTopTier = "Sabse oonchi gaddi — Cabinet Secretary."

        // ── M6d DAILY CHALLENGE (Hinglish) ──
        override val dailyCta = "AAJ KI CHUNAUTI"
        override val dailyCtaSub = "Daily challenge — sabke liye ek hi mez, ek hi seed"
        override val dailyDoneSub = "Aaj ki chunauti poori — kal phir aana"

        override fun dailyStreakLabel(streak: Int) = "🔥 $streak din ki laga"

        override val dailyOpenBadge = "AAJ KHULA"
        override val dailyDoneBadge = "DARJ ✓"

        // ── M6d LEADERBOARD / STANDINGS (Hinglish) ──
        override val leaderboardCta = "DARJA-SUCHI"
        override val leaderboardCtaSub = "Standings — rank, ank-itihaas, daily streak"
        override val leaderboardHeader = "DARJA-SUCHI — Standings"
        override val leaderboardBack = "‹ DAFTAR"
        override val leaderboardRankSection = "AAPKA DARJA — Rank"
        override val leaderboardPeakLabel = "Sabse ooncha ank"
        override val leaderboardRatingLabel = "Abhi ka ank"
        override val leaderboardGamesLabel = "Ranked baazi"
        override val leaderboardHistorySection = "ANK KA ITIHAAS — Rating History"
        override val leaderboardHistoryEmpty = "Abhi koi ranked baazi nahi. Ek khel khatam karo."
        override val leaderboardDailySection = "AAJ KI CHUNAUTI — Daily"
        override val leaderboardStreakLabel = "Abhi ki laga"
        override val leaderboardBestStreakLabel = "Sabse lambi laga"
        override val leaderboardDailyWinsLabel = "Daily jeet"
        override val leaderboardEmpty = "Khaata khaali. Ek baazi khelo — phir darja darj hoga."
        override val leaderboardServerPending = "Online darja-suchi — muhar pending (M7)."
        override val leaderboardOnlineSection = "ONLINE DARJA-SUCHI"
        override val leaderboardOnlineConnectedSub = "Sarkar se judaa — live online ranking."
        override val leaderboardOnlineOfflineSub = "Server se judo to live online ranking dikhegi."
        override val onlineHubTitle = "ONLINE MEHFIL"
        override val onlineHubHeader = "ONLINE KAMRA — DAFTAR"
        override val onlineHubServerLabel = "SARKAR KA PATA"
        override val onlineHubServerHint = "server ka pata (default localhost)"
        override val onlineCreateLabel = "PRIVATE KAMRA BANAO"
        override val onlineCreateSub = "naya kamra — code dost ko bhejo"
        override val onlineJoinLabel = "CODE SE AAO"
        override val onlineJoinSub = "dost ke kamre ka code daalo"
        override val onlineJoinCodeHint = "KAMRA CODE"
        override val onlineJoinCta = "AAO"
        override val onlineQuickLabel = "KHULI BOLI"
        override val onlineQuickSub = "public match — koi bhi mil jaaye"
        override val onlineLanLabel = "EK HI LAN"
        override val onlineLanSub = "aas-paas ke kamre — wahi network"
        override val onlineLanBrowseCta = "AAS-PAAS DHOONDO"
        override val onlineLanSearching = "Dhoondh rahe hain…"
        override val onlineLanEmpty = "Koi kamra nahi mila. Host shuru karo ya dobara dhoondo."
        override val onlinePlayersLabel = "KHILADI"
        override val onlineWorking = "Daftar se baat ho rahi hai…"
        override val onlineLobbyHeader = "INTEZAAR KAMRA"
        override val onlineLobbyShareLabel = "YEH CODE BHEJO"
        override val onlineLobbyConnecting = "Jud rahe hain…"
        override val onlineLobbyWaiting = "Khiladiyon ka intezaar…"
        override val onlineLobbyLost = "Rabta toot gaya."
        override val onlineLobbyLeave = "KAMRA CHHODO"

        override fun onlineLobbySeated(seat: Int) = "Aapki gaddi #${seat + 1} pakki."

        override fun onlineLobbyRoster(
            joined: Int,
            total: Int,
        ) = if (total > 0) "$joined / $total baith gaye" else "$joined baith gaye"

        // ── Story / Narrative mode (Hinglish) ──
        override val homeCtaStory = "KISSA — DARBAR MODE"
        override val homeCtaStorySub = "Bots baat karte hain, saazish karte hain — aap saazmaan karo"
        override val storyTitle = "KISSA — DARBAR"
        override val storyTagline = "Asli daftar mein bakwaas, saazish, aur natak — sab ek mez par."
        override val storyFreeLabel = "FREE DARBAR — koi agenda nahi"
        override val storyStartCta = "DARBAR SHURU KARO ✦"

        // ── Setup new toggles (Darbar / Anarchy / Draft) — Hinglish ──
        override val setupDarbarLabel = "FORM 1-E: DARBAR (CHAT + KISSA)"
        override val setupDarbarSub = "Bots aapas mein baat karenge, saazish karenge. Aap unhe manipulate kar sakte ho."
        override val setupAnarchyLabel = "FORM 1-F: ANDHER NAGARI — ANARCHY"
        override val setupAnarchySub = "Khela ki kimat giregi. Aap samjhe — baaki bhi samjhe."
        override val setupDraftLabel = "FORM 1-G: NILAAMI — DECK DRAFT"
        override val setupDraftSub = "Koi ek deck-draft preset chuniye — ya seedha khelo (CLASSIC)."
    }

    object English : GaddiStrings() {
        override val back = "← Back"
        override val homeRosterHeader = "ON DUTY TODAY"
        override val homeTagline = "everything is a game of chairs."
        override val homeCtaNewGame = "TAKE THE CHAIR"
        override val homeCtaNewGameSub = "Single Player · vs. the Cabinet"
        override val homeCtaRules = "READ THE RULES"
        override val homeCtaRulesSub = "The Rules — who beats whom"
        override val homeCtaSettings = "SETTINGS"
        override val homeCtaSettingsSub = "Sound, motion, difficulty"
        override val homeCtaMultiplayer = "MULTIPLAYER"
        override val homeCtaMultiplayerSub = "Online + LAN · create or join"
        override val homeCtaMultiplayerBadge = "PENDING"
        override val homeFooterLeft = "The Principal Office · Est. whenever convenient."
        override val homeFooterRight = "All characters are fictional."
        override val homeResumeLabel = "RESUME GAME"

        override val setupTitle = "NEW GAME"
        override val setupFormTitle = "GAME SETUP"
        override val setupFormBadge = "FORM 1-A THROUGH 1-C"
        override val setupModeLabel = "MODE"
        override val setupModeSublabel = "How will you govern today?"

        override fun setupPlayerSublabel(count: Int) = "Today: $count seats — you + ${count - 1} opponents."

        override val setupPlayerSectionLabel = "PLAYERS AT THE TABLE"
        override val setupDifficultyLabel = "OPPONENT DIFFICULTY"
        override val setupDifficultySublabel = "Choose your adversaries' calibre."
        override val setupCta = "PROCEED →"
        override val setupCtaSub = "Generate roster · proceed to register"
        override val modeAILabel = "VS THE OFFICE"
        override val modeAISub = "vs the office — offline AI"
        override val modePrivateLabel = "PRIVATE ROOM"
        override val modePrivateSub = "share a code with a friend"
        override val modeOpenLabel = "OPEN MATCH"
        override val modeOpenSub = "public match — pair with anyone"
        override val modeLocalLabel = "PASS & PLAY"
        override val modeLocalSub = "one phone, take turns"
        override val modeLanLabel = "SAME LAN"
        override val modeLanSub = "nearby rooms — same network"
        override val modeOnlineBadge = "ONLINE →"
        override val modePendingBadge = "PENDING"
        override val modeComingSoonBadge = "COMING SOON"
        override val setupHumanCountLabel = "HOW MANY HUMANS?"

        override fun setupHumanCountSublabel(
            humans: Int,
            players: Int,
        ) = "$humans humans take turns; the computer plays the other ${players - humans}."

        override val diffEasyName = "EASY"
        override val diffEasyVoice = "Still in training. Fills forms, doesn't read them."
        override val diffMediumName = "MEDIUM"
        override val diffMediumVoice = "10 years in the same seat. Has the rulebook memorised."
        override val diffHardName = "HARD"
        override val diffHardVoice = "Can smell a lie."
        override val diffExpertName = "EXPERT"
        override val diffExpertVoice = "Already wrote down your move before you made it."
        override val diffGrandmasterName = "GRANDMASTER"
        override val diffGrandmasterVoice = "Knows your habits — and beats you with them."

        override val lobbyHeader = "ROSTER — today's lineup"
        override val humanSeatName = "You"
        override val humanSeatTitle = "The Human Player"
        override val humanSeatArchetype = "Unknown quantity"
        override val humanSeatPersonality = "Human · seat 0 · your rules"
        override val humanSeatBark = "I'll handle everything myself."
        override val lobbyReroll = "🎲 REROLL"
        override val lobbyDealIn = "DEAL IN ✦"
        override val lobbyDealInSub = "Deal in — stamp the roster"

        override val resultsTitle = "VERDICT — Certificate"
        override val resultsVerdictSub = "Verdict secured. File closed."
        override val resultsRecapHeader = "GAME REPORT"
        override val resultsRecapSurvived = "How long you survived"
        override val resultsRecapBluffsLanded = "Bluffs that worked"
        override val resultsRecapBluffsCaught = "Bluffs caught"
        override val resultsRecapStandings = "Final Standings:"
        override val resultsRecapWinnerSuffix = " ← CHAIR SECURED"
        override val resultsRecapSeal = "✦ VERDICT SEALED ✦"
        override val resultsWinStamp = "CHAIR SECURED"
        override val resultsRematch = "REMATCH"
        override val resultsRematchSub = "Same roster · new seed · next round"
        override val resultsNewGame = "NEW GAME"
        override val resultsHome = "HOME"

        // M6b decision-quality (English)
        override val dqHeader = "DECISION QUALITY"
        override val dqAccuracyLabel = "Best move"
        override val dqEvLostLabel = "Win-prob bled"
        override val dqChallengeLabel = "Challenge accuracy"
        override val dqBluffLabel = "Bluff success"

        override fun dqSampleSub(decisions: Int) = "graded over $decisions decisions"

        override val dqGradeSharp = "SHARP BABU"
        override val dqGradeSteady = "STEADY HAND"
        override val dqGradeReckless = "RECKLESS"
        override val dqGradeUnrated = "UNRATED"
        override val dqGradeSharpSub = "Tight reads, little EV bled."
        override val dqGradeSteadySub = "Solid — mostly sound play."
        override val dqGradeRecklessSub = "Missing a lot of best moves."
        override val dqGradeUnratedSub = "Play more — a grade will land."
        override val resultsRecapDecision = "This game's decisions"

        override fun resultsDecisionValue(
            accuracyPct: Int,
            evLostPct: Int,
        ) = "$accuracyPct% best · $evLostPct% bled"

        override val settingsTitle = "SETTINGS"
        override val settingsSoundSection = "SOUND"
        override val settingsSoundLabel = "Master Sound"
        override val settingsSoundSub = "Stamps, barks, and effects"
        override val settingsMotionSection = "MOTION"
        override val settingsMotionLabel = "REDUCED MOTION"
        override val settingsMotionSub = "Reduced motion — disables stamp animations"
        override val settingsDefaultsSection = "DEFAULTS"
        override val settingsDefaultDiffLabel = "Default Difficulty"

        override fun settingsDefaultPlayersLabel(n: Int) = "Default Players: $n"

        override val settingsLearningSection = "LEARNING"
        override val settingsReplayPrimerLabel = "REPLAY TUTORIAL"
        override val settingsReplayPrimerSub = "Replay the first-run onboarding"
        override val settingsRulesLabel = "RULES GAZETTE"
        override val settingsRulesSub = "Full rules and who-beats-whom matrix"
        override val settingsAboutSection = "ABOUT"
        override val settingsAboutTitle = "Gaddi — a satirical bluffing game"
        override val settingsAboutDisclaimer =
            "\"All characters are fictional. Any resemblance to actual bureaucrats, politicians, or inspectors is purely " +
                "coincidental and deeply regrettable.\""
        override val settingsAboutFooter = "The Principal Office · Est. whenever convenient."
        override val settingsLanguageSection = "LANGUAGE"
        override val settingsLangHinglish = "HINGLISH"
        override val settingsLangEnglish = "ENGLISH"
        override val settingsCoachSection = "DECISION COACH"
        override val settingsCoachLabel = "Show Advisor"
        override val settingsCoachSub = "Show the advisor's reads + best move"
        override val settingsAutoSection = "AUTO-MODE"
        override val settingsTurnSpeedLabel = "Turn speed"
        override val settingsSpeedSlow = "SLOW"
        override val settingsSpeedNormal = "NORMAL"
        override val settingsSpeedFast = "FAST"
        override val settingsAutoPassLabel = "Auto-pass"
        override val settingsAutoPassSub = "Pass automatically when Pass is your only move"
        override val settingsAutoForcedLabel = "Auto-play forced moves"
        override val settingsAutoForcedSub = "Play forced moves (e.g. the 10+ coin Coup) for you"

        // ── M5 ONBOARD — Tutorial (Pehli Hazri) ──
        override val homeCtaTutorial = "FIRST DAY"
        override val homeCtaTutorialSub = "Tutorial — learn by doing, in one short round"
        override val tutorialOfferTitle = "Take your first day?"
        override val tutorialOfferBody =
            "New job, new chair. A short training round — the Advisor walks beside you, and one bluff gets caught. Learn by " +
                "watching."
        override val tutorialOfferAccept = "YES, TEACH ME"
        override val tutorialOfferDecline = "No, straight to play"
        override val tutorialHeader = "FIRST DAY — Training Round"
        override val tutorialBadge = "TRAINING · CONFIDENTIAL"
        override val tutorialStepLabel = "BEAT"
        override val tutorialCoachTag = "ADVISOR"
        override val tutorialNext = "NEXT →"
        override val tutorialBack = "← Back"
        override val tutorialSkip = "Skip training →"
        override val tutorialFinish = "DAY COMPLETE ✦"
        override val tutorialDoIt = "STAMP GHOTALA"
        override val tutorialDoBlock = "BLOCK WITH VAKIL"
        override val tutorialDoCoup = "STAMP KHELA"
        override val tutorialDoExchange = "RUN SETTING"
        override val tut1Title = "Two Certificates, Two Lives"
        override val tut1Body =
            "These two secret certificates are your real identities. Lose both and you're out of the chair. Keep " +
                "them hidden."
        override val tut2Title = "Coins Are Power"
        override val tut2Body = "Coins buy power. 7 buys a hit (KHELA), 10 forces one. Always watch the count."
        override val tut3Title = "Claim a Role — True or Not"
        override val tut3Body = "Every move claims a role. The best part: you don't actually need that role. Bluffing is allowed."
        override val tut4Title = "Why the Advisor Speaks"
        override val tut4Body =
            "With the coach ON, every option shows a ⭐ best move and a 'how likely it flies' read. It's your inside " +
                "man."
        override val tut5Title = "Now Your Turn: GHOTALA"
        override val tut5Body = "GHOTALA claims NETA — for +3 coins. True? Nobody knows. Stamp it and see what happens."
        override val tut6Title = "Babu Filewala Challenges!"
        override val tut6Body = "\"You hold NETA? Prove it.\" A challenge means: reveal the card. If you were lying, you lose it."
        override val tut7Title = "BLUFF! The Card Is Revealed"
        override val tut7Body =
            "You never held NETA — the bluff is caught red-handed. One certificate sacrificed. This is the heart of the game: claim, " +
                "challenge, verdict."
        override val tutBlockTitle = "VAKIL Blocks"
        override val tutBlockBody = "Netaji Vachan calls SUPARI — a claim to take your life. VAKIL blocks it. One tap, threat gone."
        override val tutCoupTitle = "KHELA — The Direct Hit"
        override val tutCoupBody = "At 7 coins, KHELA is on the table. No block, no challenge — a straight loss of influence. Forced at 10."
        override val tutExchangeTitle = "SETTING — Refresh Your Hand"
        override val tutExchangeBody = "SETTING draws 2 new cards, keeps the best two, returns the rest. Nobody else knows what changed."
        override val tut8Title = "First Day Done, Sarkar"
        override val tut8Body = "Now you know it all — claim, challenge, block, coup, and exchange. Time to take the chair for real."

        // ── M5 ONBOARD — Presets / quick-match ──
        override val setupQuickMatchLabel = "QUICK MATCH"
        override val setupQuickMatchSub = "One tap — straight to the table with your saved settings"
        override val setupPresetSectionLabel = "READY-MADE REQUISITION"
        override val setupPresetSectionSub = "Curated rosters — one stamp, the whole game set up."
        override val presetCabinetName = "CLASSIC CABINET"
        override val presetCabinetSub = "4 players · mixed · Permanent Babu"
        override val presetSnakePitName = "THE SNAKE PIT"
        override val presetSnakePitSub = "4 players · aggressive · Section Officer"
        override val presetChaosName = "CHAOS · 10"
        override val presetChaosSub = "10 players · full house · Permanent Babu"
        override val setupPresetRequisition = "FORM 1 · READY"

        // ── M6e GAUNTLET ──
        override val homeCtaGauntlet = "THE PROMOTION LADDER"
        override val homeCtaGauntletSub = "Gauntlet — beat each tier in turn, earn your promotion"

        override fun gauntletStripLabel(
            cleared: Int,
            total: Int,
        ) = "Ladder: $cleared / $total rungs cleared"

        override val gauntletHeader = "THE PROMOTION LADDER"
        override val gauntletBadge = "PROMOTION FILE"
        override val gauntletTagline = "From Probationer to Cabinet. Beat each rung to earn the next chair."
        override val gauntletStartCta = "TAKE THE NEXT EXAM"
        override val gauntletStartCtaSub = "Beat this rung's table"
        override val gauntletReplayCta = "PLAY AGAIN"
        override val gauntletLockedTag = "LOCKED · clear the previous rung first"
        override val gauntletClearedTag = "CLEARED · stamped"
        override val gauntletCurrentTag = "YOU ARE HERE"
        override val gauntletConqueredTitle = "LADDER CONQUERED"
        override val gauntletConqueredBody = "Cabinet Secretary, sir. The top chair is yours. You may still re-run any rung."
        override val gauntletRung0Name = "PROBATIONER"
        override val gauntletRung1Name = "CLERK"
        override val gauntletRung2Name = "SECTION OFFICER"
        override val gauntletRung3Name = "SECRETARY"
        override val gauntletRung4Name = "CABINET SECRETARY"

        // ── M6e SPECTATOR / TAMASHA ──
        override val homeCtaSpectate = "WATCH A DEMO"
        override val homeCtaSpectateSub = "Demo — watch a full game play out on the real table"
        override val spectatorBanner = "DEMO — watch only"

        // ── M6e TEAM KHEL ──
        override val setupTeamLabel = "FORM 1-D: TEAM PLAY"
        override val setupTeamSublabel = "Split the table into two sides — last side standing wins."
        override val setupTeamToggleOn = "TEAM PLAY · ON"
        override val setupTeamToggleOff = "Everyone for themselves (classic)"
        override val teamNameA = "THE PARTY"
        override val teamNameB = "THE OPPOSITION"
        override val lobbyTeamHeader = "SIDES — who's with whom"

        override fun teamBadge(teamId: Int) = if (teamId == 0) "PTY" else "OPP"

        // M6c REVIEW
        override val reviewHeader = "REVIEW — Match Review"
        override val reviewBadge = "RE-EXAMINATION"
        override val reviewCta = "REVIEW THIS GAME"
        override val reviewCtaSub = "Move by move — with the Advisor"
        override val reviewRecentHeader = "RECENT MATCHES"
        override val reviewRecentSub = "On file — reopen any game"
        override val reviewRecentEmpty = "No file closed yet. Finish a game and it lands here."

        override fun reviewMatchCaption(
            players: Int,
            difficulty: String,
            moves: Int,
        ) = "$players players · $difficulty · $moves moves"

        override val reviewMatchWon = "WON"
        override val reviewMatchLost = "LOST"
        override val reviewPlay = "Play"
        override val reviewPause = "Pause"
        override val reviewStepBack = "Step back"
        override val reviewStepForward = "Step forward"
        override val reviewPrevDecision = "Previous decision"
        override val reviewNextDecision = "Next decision"

        override fun reviewStepCounter(
            current: Int,
            total: Int,
        ) = "Step $current / $total"

        override val reviewTimeline = "TIMELINE"
        override val reviewTerminal = "VERDICT — game over"
        override val reviewAdvisorHeader = "ADVISOR'S READ"
        override val reviewAdvisorSub = "The read of that moment — played vs best"
        override val reviewPlayedLabel = "You played"
        override val reviewBestLabel = "Best move"
        override val reviewEvGapLabel = "Gap"

        override fun reviewEvGapValue(pct: Int) = "−$pct% win chance"

        override val reviewMatchedBest = "Best move — the Advisor would have played this too."
        override val reviewVerdictSharp = "SHARP"
        override val reviewVerdictFine = "FINE"
        override val reviewVerdictLoose = "LOOSE"
        override val reviewVerdictCostly = "COSTLY"
        override val reviewBack = "‹ VERDICT"

        // ── M6d RANKED ELO ladder + sarkari rank tiers (English) ──
        override val rankClerk = "CLERK"
        override val rankSectionOfficer = "SECTION OFFICER"
        override val rankUnderSecretary = "UNDER SECRETARY"
        override val rankDeputySecretary = "DEPUTY SECRETARY"
        override val rankJointSecretary = "JOINT SECRETARY"
        override val rankSecretary = "SECRETARY"
        override val rankCabinetSecretary = "CABINET SECRETARY"
        override val rankedStripTag = "RANK"
        override val rankedRatingWord = "rating"
        override val rankedProvisional = "Play your first ranked game — your rank will be set."

        override fun rankedToNext(
            points: Int,
            nextRank: String,
        ) = "$points to $nextRank"

        override val rankedTopTier = "Top of the ladder — Cabinet Secretary."

        // ── M6d DAILY CHALLENGE (English) ──
        override val dailyCta = "TODAY'S CHALLENGE"
        override val dailyCtaSub = "Daily challenge — one table, one seed, for everyone"
        override val dailyDoneSub = "Today's challenge done — come back tomorrow"

        override fun dailyStreakLabel(streak: Int) = "🔥 $streak-day streak"

        override val dailyOpenBadge = "OPEN TODAY"
        override val dailyDoneBadge = "DONE ✓"

        // ── M6d LEADERBOARD / STANDINGS (English) ──
        override val leaderboardCta = "STANDINGS"
        override val leaderboardCtaSub = "Standings — rank, rating history, daily streak"
        override val leaderboardHeader = "STANDINGS"
        override val leaderboardBack = "‹ HOME"
        override val leaderboardRankSection = "YOUR RANK"
        override val leaderboardPeakLabel = "Peak rating"
        override val leaderboardRatingLabel = "Current rating"
        override val leaderboardGamesLabel = "Ranked games"
        override val leaderboardHistorySection = "RATING HISTORY"
        override val leaderboardHistoryEmpty = "No ranked games yet. Finish a game."
        override val leaderboardDailySection = "DAILY CHALLENGE"
        override val leaderboardStreakLabel = "Current streak"
        override val leaderboardBestStreakLabel = "Best streak"
        override val leaderboardDailyWinsLabel = "Daily wins"
        override val leaderboardEmpty = "Empty file. Play a game — your standing lands here."
        override val leaderboardServerPending = "Online standings — pending sanction (M7)."
        override val leaderboardOnlineSection = "ONLINE STANDINGS"
        override val leaderboardOnlineConnectedSub = "Connected to server — live online ranking."
        override val leaderboardOnlineOfflineSub = "Connect to a server to see live online ranking."
        override val onlineHubTitle = "ONLINE LOBBY"
        override val onlineHubHeader = "ONLINE ROOM — REGISTRY"
        override val onlineHubServerLabel = "SERVER ADDRESS"
        override val onlineHubServerHint = "server address (default localhost)"
        override val onlineCreateLabel = "CREATE PRIVATE ROOM"
        override val onlineCreateSub = "new room — share the code with a friend"
        override val onlineJoinLabel = "JOIN BY CODE"
        override val onlineJoinSub = "enter a friend's room code"
        override val onlineJoinCodeHint = "ROOM CODE"
        override val onlineJoinCta = "JOIN"
        override val onlineQuickLabel = "OPEN MATCH"
        override val onlineQuickSub = "public match — pair with anyone"
        override val onlineLanLabel = "SAME LAN"
        override val onlineLanSub = "nearby rooms — same network"
        override val onlineLanBrowseCta = "FIND NEARBY"
        override val onlineLanSearching = "Searching…"
        override val onlineLanEmpty = "No rooms found. Host one or search again."
        override val onlinePlayersLabel = "PLAYERS"
        override val onlineWorking = "Talking to the registry…"
        override val onlineLobbyHeader = "WAITING ROOM"
        override val onlineLobbyShareLabel = "SHARE THIS CODE"
        override val onlineLobbyConnecting = "Connecting…"
        override val onlineLobbyWaiting = "Waiting for players…"
        override val onlineLobbyLost = "Connection lost."
        override val onlineLobbyLeave = "LEAVE ROOM"

        override fun onlineLobbySeated(seat: Int) = "Your chair #${seat + 1} is confirmed."

        override fun onlineLobbyRoster(
            joined: Int,
            total: Int,
        ) = if (total > 0) "$joined / $total seated" else "$joined seated"

        // ── Story / Narrative mode (English) ──
        override val homeCtaStory = "STORY MODE — DARBAR"
        override val homeCtaStorySub = "Bots chat, conspire, and react — you pull the strings"
        override val storyTitle = "STORY — DARBAR"
        override val storyTagline = "The real office: gossip, schemes, and theatre — all at one table."
        override val storyFreeLabel = "FREE DARBAR — no lead arc"
        override val storyStartCta = "START THE DARBAR ✦"

        // ── Setup new toggles (Darbar / Anarchy / Draft) — English ──
        override val setupDarbarLabel = "FORM 1-E: DARBAR (CHAT + STORY)"
        override val setupDarbarSub = "Bots will talk, conspire, and react mid-game. You can read and manipulate them."
        override val setupAnarchyLabel = "FORM 1-F: ANARCHY (ANDHER NAGARI)"
        override val setupAnarchySub = "The Coup cost drops. You understand what that means — so do they."
        override val setupDraftLabel = "FORM 1-G: DECK DRAFT (NILAAMI)"
        override val setupDraftSub = "Pick a deck-draft preset — or play the standard deck (CLASSIC)."
    }
}

/** Bilingual sarkari rank-tier name for a [SarkariRank], in the active language. */
fun GaddiStrings.rankName(rank: com.kursi.core.prefs.SarkariRank): String =
    when (rank) {
        com.kursi.core.prefs.SarkariRank.CLERK -> rankClerk
        com.kursi.core.prefs.SarkariRank.SECTION_OFFICER -> rankSectionOfficer
        com.kursi.core.prefs.SarkariRank.UNDER_SECRETARY -> rankUnderSecretary
        com.kursi.core.prefs.SarkariRank.DEPUTY_SECRETARY -> rankDeputySecretary
        com.kursi.core.prefs.SarkariRank.JOINT_SECRETARY -> rankJointSecretary
        com.kursi.core.prefs.SarkariRank.SECRETARY -> rankSecretary
        com.kursi.core.prefs.SarkariRank.CABINET_SECRETARY -> rankCabinetSecretary
    }

val LocalGaddiStrings = staticCompositionLocalOf<GaddiStrings> { GaddiStrings.Hinglish }
