package com.kursi.android

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GaddiNotificationChannels.specs] ids are referenced by the FCM service and must stay stable —
 * this guards against an accidental rename or duplicate id breaking that wiring silently.
 */
class GaddiNotificationChannelsTest {
    @Test
    fun `spec ids match the public constants`() {
        val ids = GaddiNotificationChannels.specs.map { it.id }
        assertEquals(
            listOf(GaddiNotificationChannels.GAME_INVITES, GaddiNotificationChannels.SYSTEM),
            ids,
        )
    }

    @Test
    fun `spec ids are unique`() {
        val ids = GaddiNotificationChannels.specs.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every spec has a non-blank name and description`() {
        GaddiNotificationChannels.specs.forEach { spec ->
            assertTrue("id=${spec.id} has a blank name", spec.name.isNotBlank())
            assertTrue("id=${spec.id} has a blank description", spec.description.isNotBlank())
        }
    }

    @Test
    fun `game invites channel is high importance so match alerts are not silenced`() {
        val gameInvites = GaddiNotificationChannels.specs.first { it.id == GaddiNotificationChannels.GAME_INVITES }
        assertEquals(NotificationManager.IMPORTANCE_HIGH, gameInvites.importance)
    }
}
