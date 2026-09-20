package com.kursi.gameservices

/**
 * The three values Play Games Services cannot work without and this repo cannot know.
 *
 * Each ships as its `__PROVISION_<KEY>__` sentinel and is registered in `provisioning/
 * placeholders.json` with its real format and where to get it. [availability] detects an
 * unreplaced sentinel and answers [GameServicesAvailability.NOT_CONFIGURED], which is what makes
 * the whole surface refuse to draw rather than draw a button that dies on tap. A placeholder that
 * silently no-ops is the bug class this seam exists to avoid.
 *
 * The same sentinel appears once more, in `cmp-android/src/gms/AndroidManifest.xml`, as the
 * `com.google.android.gms.games.APP_ID` meta-data the Play Games SDK reads at
 * `PlayGamesSdk.initialize()`. That call is gated on [isConfigured] precisely because initializing
 * against the literal sentinel throws.
 */
object PlayGamesProvisioning {
    /** Play Console > Play Games Services > Configuration. Numeric, 6+ digits. */
    const val PROJECT_ID: String = "__PROVISION_PLAY_GAMES_PROJECT_ID__"

    /**
     * Bound to the RELEASE signing SHA-1. With Play App Signing that is GOOGLE's re-signed
     * certificate, not your upload key — the wrong one works in debug and fails for every real
     * install, which is why the two build types carry separate values instead of one.
     */
    const val OAUTH_CLIENT_ID_RELEASE: String = "__PROVISION_PLAY_GAMES_OAUTH_CLIENT_ID_RELEASE__"

    /** Bound to the DEBUG signing SHA-1. */
    const val OAUTH_CLIENT_ID_DEBUG: String = "__PROVISION_PLAY_GAMES_OAUTH_CLIENT_ID_DEBUG__"

    private const val SENTINEL_MARKER = "__PROVISION_"

    /** True while [value] is still an unreplaced sentinel. */
    fun isSentinel(value: String): Boolean = value.startsWith(SENTINEL_MARKER)

    /** The OAuth client id that matters for this build. Debug and release are bound to different SHA-1s. */
    fun oauthClientId(debuggable: Boolean): String =
        if (debuggable) OAUTH_CLIENT_ID_DEBUG else OAUTH_CLIENT_ID_RELEASE

    /**
     * Configured iff the project id AND the OAuth client id for THIS build type have both been
     * provisioned. Checking only the project id would let a release build sail past a missing
     * release client id and fail at sign-in, on users' devices, with no local repro.
     */
    fun isConfigured(debuggable: Boolean): Boolean =
        !isSentinel(PROJECT_ID) && !isSentinel(oauthClientId(debuggable))
}
