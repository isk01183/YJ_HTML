package com.yj.magiccircle

import org.junit.Assert.assertEquals
import org.junit.Test

class VrmPlacementTest {
    @Test fun invalidPlacementRecoversAndFiniteValuesStayBounded() {
        assertEquals(VrmPlacement(),VrmPlacement(Float.NaN,Float.POSITIVE_INFINITY,Float.NaN).normalized())
        assertEquals(VrmPlacement(-.35f,.35f,.5f,false),VrmPlacement(-2f,2f,-1f,false).normalized())
        assertEquals(1.5f,VrmPlacement(scale=10f).normalized().scale,0f)
    }
}
