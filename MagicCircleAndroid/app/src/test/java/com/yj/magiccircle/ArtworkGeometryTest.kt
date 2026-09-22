package com.yj.magiccircle

import org.junit.Assert.*
import org.junit.Test

class ArtworkGeometryTest {
    @Test fun squareDesignFitsPhoneAndLandscapeWithoutStretching() {
        val phone = ArtworkGeometry.fit(1080, 2400, 1024f, 1024f)
        assertEquals(1.0546875f, phone.scale, 0f)
        assertEquals(0f, phone.left, 0f)
        assertEquals(660f, phone.top, 0f)
        val landscape = ArtworkGeometry.fit(2560, 1600, 1024f, 1024f)
        assertEquals(1.5625f, landscape.scale, 0f)
        assertEquals(480f, landscape.left, 0f)
        assertEquals(0f, landscape.top, 0f)
    }

    @Test fun emptyViewportHasNoDrawableScale() {
        for ((w, h) in listOf(0 to 100, 100 to 0, -1 to 100, 100 to -1)) {
            assertEquals(0f, ArtworkGeometry.fit(w, h, 1024f, 1024f).scale, 0f)
        }
    }

    @Test fun petalsAndRingsUseTheAuthoredSixfoldGeometry() {
        assertEquals(512f, ArtworkGeometry.R01_CENTER_X, 0f)
        assertEquals(512f, ArtworkGeometry.R01_CENTER_Y, 0f)
        assertEquals(listOf(0f, 60f, 120f, 180f, 240f, 300f), ArtworkGeometry.r01Angles)
        assertEquals(listOf(440f, 420f, 390f, 372f), ArtworkGeometry.r01OuterRadii)
    }

    @Test fun nodesKeepTheirAlternatingOffAxisCentersAndNestedRadii() {
        val nodes = ArtworkGeometry.r01Nodes
        assertEquals(6, nodes.size)
        assertEquals(listOf(false, true, false, true, false, true), nodes.map { it.diamond })
        assertEquals(listOf(273f, 246f, 273f, 246f, 273f, 246f), nodes.map { it.innerX })
        assertEquals(listOf(-23f, 21f, -23f, 21f, -23f, 21f), nodes.map { it.innerY })
        assertEquals(listOf(0f, 60f, 120f, 180f, 240f, 300f), nodes.map { it.angle })
        assertEquals(255f, ArtworkGeometry.R01_NODE_X, 0f)
        assertEquals(listOf(80f, 72f), ArtworkGeometry.r01NodeOuterRadii)
        assertEquals(listOf(49f, 42f), ArtworkGeometry.r01NodeInnerRadii)
        assertEquals(listOf(31f, 22f, 10f), ArtworkGeometry.r01NodeDiamonds)
    }

    @Test fun runeLanesRetainAuthoredCountsAnglesAndScaffoldClearance() {
        val outer = ArtworkGeometry.r01OuterRunes()
        assertEquals(76, outer.size)
        assertEquals(0f, outer.first().x, .001f)
        assertEquals(-405f, outer.first().y, .001f)
        assertEquals(0f, outer.first().rotation, .001f)
        val inner = ArtworkGeometry.r01InnerRunes()
        assertEquals(30, inner.size)
        assertEquals(104f, inner.first().rotation, .001f)
        assertEquals(136f, inner[4].rotation, .001f)
        assertEquals(350.887f, inner.first().x / kotlin.math.cos(Math.toRadians(14.0)).toFloat(), .002f)
        assertEquals(5, inner[7].glyph)
    }

    @Test fun staticAndFrameZeroHaveNoRotationAndLiveMotionIsSlow() {
        assertEquals(0f, ArtworkGeometry.r01RuneAngle(999999, false), 0f)
        assertEquals(0f, ArtworkGeometry.r01RuneAngle(0, true), 0f)
        assertEquals(0f, ArtworkGeometry.r01RuneAngle(-1, true), 0f)
        assertEquals(3f, ArtworkGeometry.r01RuneAngle(60000, true), .001f)
        assertEquals(0f, ArtworkGeometry.r01RuneAngle(7200000, true), 0f)
    }
}
