package com.yj.magiccircle

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class SanctuaryGeometryTest {
    @Test fun closedTriangleReturnsToItsActualStart() {
        val points = SanctuaryLayout.polygon(300f, 3, 1, -90f)
        assertEquals(8, points.size)
        assertEquals(432f, points[0], .001f)
        assertEquals(418f, points[1], .001f)
        assertEquals(points[0], points[points.size - 2], 0f)
        assertEquals(points[1], points[points.size - 1], 0f)
    }

    @Test fun allFourStarPathsCloseAndShareTheSameCenter() {
        val paths = SanctuaryLayout.starPolygons()
        assertEquals(4, paths.size)
        for ((index, points) in paths.withIndex()) {
            val vertices = if (index < 2) 3 else 4
            assertEquals((vertices + 1) * 2, points.size)
            assertEquals(points[0], points.lastIndex.let { points[it - 1] }, 0f)
            assertEquals(points[1], points.last(), 0f)
            assertEquals(432f, (0 until vertices).sumOf { points[it * 2].toDouble() }.toFloat() / vertices, .001f)
            assertEquals(718f, (0 until vertices).sumOf { points[it * 2 + 1].toDouble() }.toFloat() / vertices, .001f)
        }
    }

    @Test fun runePlacementsHaveEvenSpacingAndTangentOrientation() {
        for ((points, count, radius) in listOf(Triple(SanctuaryLayout.goldRunes(), 48, 365f), Triple(SanctuaryLayout.blueRunes(), 54, 281f))) {
            assertEquals(count * 3, points.size)
            for (i in 0 until count) {
                assertEquals(radius, hypot(points[i * 3] - 432f, points[i * 3 + 1] - 718f), .001f)
                assertEquals(i * 360f / count, points[i * 3 + 2], .001f)
            }
            assertEquals(432f, points[0], .001f)
            assertEquals(718f - radius, points[1], .001f)
        }
    }

    @Test fun eightNodesAreTheActualMajorAxisEndsOfFourOrbits() {
        val orbits = SanctuaryLayout.orbits()
        val nodes = SanctuaryLayout.orbitNodes(orbits)
        assertEquals(4, orbits.size)
        assertEquals(16, nodes.size)
        assertEquals(687f, nodes[0], .001f)
        assertEquals(718f, nodes[1], .001f)
        assertEquals(177f, nodes[2], .001f)
        assertEquals(718f, nodes[3], .001f)
        for (i in orbits.indices) {
            assertEquals(864f, nodes[i * 4] + nodes[i * 4 + 2], .001f)
            assertEquals(1436f, nodes[i * 4 + 1] + nodes[i * 4 + 3], .001f)
            assertEquals(255f, hypot(nodes[i * 4] - 432f, nodes[i * 4 + 1] - 718f), .001f)
        }
        // A changed ellipse radius must move its node; nodes cannot be decorative constants.
        val changed = SanctuaryLayout.orbitNodes(arrayOf(SanctuaryLayout.Orbit(100f, 40f, 90f)))
        assertEquals(432f, changed[0], .001f)
        assertEquals(818f, changed[1], .001f)
    }

    @Test fun runeBaselinesAreTangentAndGlyphTopsPointOutward() {
        for (points in arrayOf(SanctuaryLayout.goldRunes(), SanctuaryLayout.blueRunes())) {
            for (i in points.indices step 3) {
                val x = points[i]
                val y = points[i + 1]
                val radialLength = hypot(x - 432f, y - 718f)
                val radialX = (x - 432f) / radialLength
                val radialY = (y - 718f) / radialLength
                val rotation = Math.toRadians(points[i + 2].toDouble())
                val c = cos(rotation).toFloat()
                val s = sin(rotation).toFloat()
                // Apply the renderer's rotate-then-translate transform to local (1,0) and (0,-1).
                val baselineX = (x + c) - x
                val baselineY = (y + s) - y
                val glyphUpX = (x + s) - x
                val glyphUpY = (y - c) - y
                assertEquals("Baseline must be perpendicular to the radius", 0f, baselineX * radialX + baselineY * radialY, .0001f)
                assertEquals("Glyph top must face away from the circle center", 1f, glyphUpX * radialX + glyphUpY * radialY, .0001f)
            }
        }
    }
}
