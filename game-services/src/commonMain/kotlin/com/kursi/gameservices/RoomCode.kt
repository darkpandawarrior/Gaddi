package com.kursi.gameservices

/**
 * CLIENT-SIDE room-code formatting. The server keeps minting what it always minted.
 *
 * `:server`'s `RoomRegistry.generateRoomCode()` mints six characters from
 * `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` — ambiguous glyphs (0/O, 1/I) deliberately excluded so a code
 * survives being read aloud down a phone line. `RoomRegistry.findRoom()` uppercases and looks up
 * that exact six-character string.
 *
 * iOS 26's `GKGameActivity` party code, by contrast, must be two EQUAL-LENGTH parts joined by a
 * dash — `ABC-123`. Two incompatible shapes, and only one of them is allowed to change.
 *
 * Changing the server would change what every existing client sends, what every saved code means,
 * and what players read out to each other. So the translation lives here, at the boundary, and
 * only here:
 *
 *   - [toPartyCode] on the way OUT to `GKGameActivity` — strict, because handing the platform a
 *     malformed code fails inside GameKit with an error no player can act on.
 *   - [fromPartyCode] on the way IN from anything a human or a platform hands us — permissive,
 *     because a human types `abc 123`, pastes `ABC-123`, or reads `ABC123` off a screenshot, and
 *     all three mean the same room.
 *
 * Nothing here ever reaches the wire in dashed form: `OnlineHubController.joinByCode` gets the
 * bare code back.
 */
object RoomCode {
    /** The exact alphabet `RoomRegistry.generateRoomCode()` draws from. */
    const val ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** The length `RoomRegistry` mints today. [toPartyCode] does not hard-code it — it only needs evenness. */
    const val SERVER_LENGTH: Int = 6

    private const val SEPARATOR = '-'

    /** True iff [code] is exactly what the server mints: right length, right alphabet, already uppercase. */
    fun isServerCode(code: String): Boolean =
        code.length == SERVER_LENGTH && code.all { it in ALPHABET }

    /**
     * `"ABC123"` → `"ABC-123"`. Null when the code cannot be split into two equal halves, which
     * `GKGameActivity` would reject: an odd length, an empty string, or a character outside
     * [ALPHABET] (a dash in the middle of a half would itself break the parse back).
     *
     * Returning null rather than a best-effort string is the point — a party code that looks right
     * and is not is exactly the failure that surfaces only once a second player tries to join.
     */
    fun toPartyCode(serverCode: String): String? {
        val code = serverCode.trim().uppercase()
        if (code.isEmpty() || code.length % 2 != 0) return null
        if (!code.all { it in ALPHABET }) return null
        val half = code.length / 2
        return code.substring(0, half) + SEPARATOR + code.substring(half)
    }

    /**
     * Anything a human or a platform hands us → the bare code the server understands.
     * Strips separators and whitespace, uppercases. Does NOT validate: `joinByCode` already
     * reports an unknown room, and rejecting a typo here would only duplicate that message with
     * less information.
     */
    fun fromPartyCode(partyCode: String): String =
        partyCode.uppercase().filter { it in ALPHABET }
}
