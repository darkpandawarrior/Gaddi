package com.kursi.designsystem

import kotlin.test.Test
import kotlin.test.assertTrue

class GaddiMotionTest {
    @Test
    fun durations_are_positive_and_ordered() {
        assertTrue(GaddiMotion.DEAL_MS > 0)
        assertTrue(GaddiMotion.FOCUS_PULL_MS > 0)
        // A quick card deal is shorter than the more dramatic focus-pull camera push.
        assertTrue(GaddiMotion.DEAL_MS < GaddiMotion.FOCUS_PULL_MS)
    }
}
