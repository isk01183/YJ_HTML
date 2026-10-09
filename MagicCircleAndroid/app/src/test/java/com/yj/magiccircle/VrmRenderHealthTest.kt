package com.yj.magiccircle

import org.junit.Assert.*
import org.junit.Test

class VrmRenderHealthTest {
    @Test fun memoryPressureDoesNotAutomaticallyRetry() { assertNull(VrmRenderHealth().onFailure(VrmFailure.MEMORY)) }
    @Test fun damagedMediaDoesNotRetry() { assertNull(VrmRenderHealth().onFailure(VrmFailure.MEDIA)) }
    @Test fun timeoutCountsOnlyVisibleTime() {
        val h=VrmRenderHealth();h.newAttempt(0);h.setVisible(true,0)
        assertNull(h.sample(false,0,44999));h.setVisible(false,44999)
        assertNull(h.sample(false,0,104999));h.setVisible(true,104999)
        assertEquals(VrmFailure.TIMEOUT,h.sample(false,0,105000))
    }
    @Test fun retryIsBoundedAcrossSurfaceAndVisibilityChanges() {
        val h=VrmRenderHealth();h.newAttempt(0)
        assertEquals(1000L,h.onFailure(VrmFailure.RENDERER));h.newAttempt(1000)
        h.setVisible(false,1001);h.setVisible(true,1002)
        assertEquals(3000L,h.onFailure(VrmFailure.CONTEXT));h.newAttempt(4002)
        assertNull(h.onFailure(VrmFailure.TIMEOUT))
        h.reset(5000);assertEquals(1000L,h.onFailure(VrmFailure.CONTEXT))
    }
    @Test fun onlyHealthyFramesForThirtySecondsResetRetryBudget() {
        val h=VrmRenderHealth();h.setVisible(true,0)
        h.onFailure(VrmFailure.CONTEXT);h.newAttempt(0);h.sample(true,1,0)
        h.sample(true,2,29999)
        assertEquals(3000L,h.onFailure(VrmFailure.RENDERER))
        h.newAttempt(30000);h.sample(true,1,30000);h.sample(true,2,60000)
        assertEquals(1000L,h.onFailure(VrmFailure.CONTEXT))
        assertNull(h.onFailure(VrmFailure.MODEL));assertNull(h.onFailure(VrmFailure.PAGE))
    }
}
