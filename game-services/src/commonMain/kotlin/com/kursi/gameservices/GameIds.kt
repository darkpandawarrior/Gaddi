package com.kursi.gameservices

/**
 * Why the two platforms carry different id shapes.
 *
 * Game Center ids are AUTHOR-CHOSEN: you type the reverse-DNS string into App Store Connect, so it
 * can be a compile-time constant here and stay in sync by convention.
 *
 * Play Games ids are PLAY-ASSIGNED (`CgkI…`). You cannot know them before the Play Console
 * configuration exists, and the Console's own workflow is to hand you a `res/values/games-ids.xml`
 * to paste into the app. So Android carries a RESOURCE NAME, resolved at runtime; a missing
 * resource means "not configured yet" and the call returns false rather than posting to a
 * non-existent board. That is why these are not sentinels — there is nothing to swap until the
 * Console file is pasted, and its absence is already self-describing.
 */
enum class Leaderboard(
    val gameCenterId: String,
    val androidResourceName: String,
) {
    /** Lifetime human wins — `StatsLedger.wins`. */
    CAREER_WINS("com.kursi.leaderboard.career_wins", "leaderboard_career_wins"),

    /** Best-ever consecutive-day daily-challenge streak — `DailyStanding.bestStreak`. */
    DAILY_BEST_STREAK("com.kursi.leaderboard.daily_best_streak", "leaderboard_daily_best_streak"),

    /** Highest gauntlet rung cleared — `GauntletProgress.clearedRung` (submitted 1-based). */
    GAUNTLET_RUNG("com.kursi.leaderboard.gauntlet_rung", "leaderboard_gauntlet_rung"),
}

/**
 * [totalSteps] > 1 marks an INCREMENTAL achievement. The distinction is load-bearing on both
 * platforms: Play Games rejects `increment()` on a one-shot achievement, and Game Center models
 * everything as a percentage, so incremental ones need the step count to compute it.
 */
enum class Achievement(
    val gameCenterId: String,
    val androidResourceName: String,
    val totalSteps: Int = 1,
) {
    FIRST_WIN("com.kursi.achievement.first_win", "achievement_first_win"),
    TEN_WINS("com.kursi.achievement.ten_wins", "achievement_ten_wins", totalSteps = 10),
    HUNDRED_GAMES("com.kursi.achievement.hundred_games", "achievement_hundred_games", totalSteps = 100),
    WEEK_STREAK("com.kursi.achievement.week_streak", "achievement_week_streak"),
}

/**
 * A cloud save slot. [platformName] is the snapshot/save-game file name on both platforms; keep it
 * filesystem-safe — Play Games rejects names outside `[a-zA-Z0-9-._~]` and 100 chars.
 */
enum class SavedGameSlot(val platformName: String) {
    /** The career ledger + daily standing + gauntlet progress, i.e. everything in `AppPrefs`. */
    CAREER("gaddi.career"),
}
