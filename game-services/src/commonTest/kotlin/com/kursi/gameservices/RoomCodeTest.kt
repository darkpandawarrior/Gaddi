package com.kursi.gameservices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomCodeTest {
    @Test
    fun `server code round-trips through the party-code boundary`() {
        val minted = "ABC234"
        val party = RoomCode.toPartyCode(minted)
        assertEquals("ABC-234", party)
        assertEquals(minted, RoomCode.fromPartyCode(party!!))
    }

    @Test
    fun `party code halves are equal length for every code the server can mint`() {
        // Exhaustive over the shape, not the alphabet: RoomRegistry always mints SERVER_LENGTH.
        val sample = RoomCode.ALPHABET.take(RoomCode.SERVER_LENGTH)
        val party = RoomCode.toPartyCode(sample)!!
        val parts = party.split('-')
        assertEquals(2, parts.size)
        assertEquals(parts[0].length, parts[1].length)
    }

    @Test
    fun `odd length is refused rather than mangled`() {
        assertNull(RoomCode.toPartyCode("ABC23"))
        assertNull(RoomCode.toPartyCode(""))
    }

    @Test
    fun `ambiguous glyphs are outside the alphabet so they are refused, not silently mapped`() {
        assertNull(RoomCode.toPartyCode("ABC0I1"))
    }

    @Test
    fun `parse is permissive about how a human retypes a code`() {
        listOf("ABC-234", "abc-234", "abc 234", " ABC234 ", "ABC–234")
            .forEach { assertEquals("ABC234", RoomCode.fromPartyCode(it), "failed on: $it") }
    }

    @Test
    fun `isServerCode matches exactly what RoomRegistry mints`() {
        assertTrue(RoomCode.isServerCode("ABC234"))
        assertFalse(RoomCode.isServerCode("ABC-234")) // dashed form is never sent to the server
        assertFalse(RoomCode.isServerCode("ABC23"))
        assertFalse(RoomCode.isServerCode("ABC0I1"))
    }
}
