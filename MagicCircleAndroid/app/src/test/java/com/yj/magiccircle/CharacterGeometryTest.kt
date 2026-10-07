package com.yj.magiccircle
import org.junit.Assert.*
import org.junit.Test

class CharacterGeometryTest {
    @Test fun proportionsKeepRequestedHeightAndFeetOnGround() {
        val normal=BodyProportions()
        val cases=mutableListOf(normal)
        for(i in 0..8) { cases+=normal.with(i,CharacterRules.range(i).start); cases+=normal.with(i,CharacterRules.range(i).endInclusive) }
        cases+=(0..8).fold(normal) { b,i -> b.with(i,CharacterRules.range(i).start) }
        cases+=(0..8).fold(normal) { b,i -> b.with(i,CharacterRules.range(i).endInclusive) }
        cases.forEach { b ->
            val f=CharacterGeometry.measure(b)
            assertEquals(b.heightCm,f.soleY-f.crownY,.001f)
            assertEquals(200f,f.soleY,0f)
            assertEquals(f.leftAnkle.y,f.rightAnkle.y,0f)
            assertTrue(f.points.all { it.x.isFinite() && it.y.isFinite() })
            assertTrue(f.leftShoulder.y<f.leftElbow.y && f.leftElbow.y<f.leftWrist.y)
        }
        assertEquals(160f,normal.heightCm,0f)
        assertNotEquals(CharacterGeometry.measure(normal).leftKnee.y,CharacterGeometry.measure(normal.copy(legs=1.2f)).leftKnee.y)
    }
}
