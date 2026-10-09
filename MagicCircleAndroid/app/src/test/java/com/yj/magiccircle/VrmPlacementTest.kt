package com.yj.magiccircle

import org.junit.Assert.assertEquals
import org.junit.Test

class VrmPlacementTest {
    @Test fun placementUsesScreenCoordinates() {
        assertEquals(.5f,VrmPlacement().screenX(),0f)
        assertEquals(.5f,VrmPlacement().screenY(),0f)
        val p=VrmPlacement(scale=1.2f,blink=false).withScreenPosition(.7f,.4f)
        assertEquals(.2f,p.x,.00001f)
        assertEquals(-.1f,p.y,.00001f)
        assertEquals(p,p.withScreenPosition(p.screenX(),p.screenY()))
        assertEquals(VrmPlacement(-.35f,.35f),VrmPlacement().withScreenPosition(-1f,2f))
        assertEquals(VrmPlacement(),VrmPlacement().withScreenPosition(Float.NaN,Float.POSITIVE_INFINITY))
    }
    @Test fun invalidPlacementRecoversAndFiniteValuesStayBounded() {
        assertEquals(VrmPlacement(),VrmPlacement(Float.NaN,Float.POSITIVE_INFINITY,Float.NaN).normalized())
        assertEquals(VrmPlacement(-.35f,.35f,.5f,false),VrmPlacement(-2f,2f,-1f,false).normalized())
        assertEquals(1.5f,VrmPlacement(scale=10f).normalized().scale,0f)
    }
}
