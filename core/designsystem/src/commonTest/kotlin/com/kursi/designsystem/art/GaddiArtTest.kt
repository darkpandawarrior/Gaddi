package com.kursi.designsystem.art

import com.kursi.engine.Role
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The loading seam's pure resolution logic (§7.4): a slot resolves to [ArtResolution.Asset]
 * only when it is both globally enabled ([GaddiArtPolicy.assetsEnabled]) and present in the
 * ready-set; otherwise it falls back. [resolveArt] takes the ready-set as a parameter
 * (defaulting to the real, empty-until-art-lands [GaddiArtRegistry]) precisely so both branches
 * are testable without mutating production state.
 */
class GaddiArtTest {
    @AfterTest
    fun resetPolicy() {
        GaddiArtPolicy.assetsEnabled = true
    }

    @Test
    fun shippedRegistry_isEmpty_soEveryRoleFaceFallsBack() {
        Role.entries.forEach { role ->
            assertEquals(ArtResolution.Fallback, resolveArt(ArtSlot.RoleFace(role)))
        }
    }

    @Test
    fun shippedRegistry_isEmpty_soEveryPersonaPortraitFallsBack() {
        GaddiPersonaIds.all.forEach { id ->
            assertEquals(ArtResolution.Fallback, resolveArt(ArtSlot.PersonaPortrait(id)))
        }
    }

    @Test
    fun shippedRegistry_isEmpty_soEveryHeroMomentFallsBack() {
        HeroMoment.entries.forEach { moment ->
            assertEquals(ArtResolution.Fallback, resolveArt(ArtSlot.Moment(moment)))
        }
    }

    @Test
    fun unknownPersonaId_fallsBack() {
        assertEquals(ArtResolution.Fallback, resolveArt(ArtSlot.PersonaPortrait("not_a_real_persona")))
    }

    @Test
    fun assetPresentInReadySet_resolvesToAsset() {
        val slot = ArtSlot.RoleFace(Role.NETA)
        assertEquals(ArtResolution.Asset, resolveArt(slot, readySlots = setOf(slot)))
    }

    @Test
    fun assetPresentForOneSlot_stillFallsBackForASiblingSlot() {
        val ready = setOf<ArtSlot>(ArtSlot.RoleFace(Role.NETA))
        assertEquals(ArtResolution.Fallback, resolveArt(ArtSlot.RoleFace(Role.BHAI), readySlots = ready))
    }

    @Test
    fun assetsDisabledGlobally_forcesFallback_evenWhenPresentInReadySet() {
        val slot = ArtSlot.Moment(HeroMoment.CREST)
        GaddiArtPolicy.assetsEnabled = false
        assertEquals(ArtResolution.Fallback, resolveArt(slot, readySlots = setOf(slot)))
    }

    @Test
    fun distinctSlotsAreNotEqual() {
        // Sanity check on ArtSlot's data-class equality, which readySlots membership relies on.
        val a: ArtSlot = ArtSlot.RoleFace(Role.NETA)
        val b: ArtSlot = ArtSlot.RoleFace(Role.BHAI)
        val c: ArtSlot = ArtSlot.RoleFace(Role.NETA)
        assertEquals(a, c)
        assertNotEquals(a, b)
    }
}
