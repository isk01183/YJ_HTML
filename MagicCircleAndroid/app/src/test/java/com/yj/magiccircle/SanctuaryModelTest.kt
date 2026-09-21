package com.yj.magiccircle

import org.junit.Assert.*
import org.junit.Test

class SanctuaryModelTest {
    @Test fun measuredDataDoesNotInventSampleValues() {
        val absent = ChargeSnapshot.fromRaw(null, null, null, null, null, null)
        assertNull(absent.percent)
        assertNull(absent.temperatureC)
        assertNull(ChargeSnapshot.fromRaw(4, 0, null, null, null, null).percent)
        assertNull(ChargeSnapshot.fromRaw(101, 100, null, null, null, null).percent)
        assertEquals(69, ChargeSnapshot.fromRaw(69, 100, 325, 2, 3, 0).percent)
        assertEquals(0f, ChargeSnapshot.fromRaw(0, 100, 0, 2, 3, 0).temperatureC!!, 0f)
    }

    @Test fun invalidReadingsStayUnknownButOtherMeasurementsSurvive() {
        val invalid = ChargeSnapshot.fromRaw(-1, 100, -55, 4, 5, 2)
        assertNull(invalid.percent)
        assertEquals(-5.5f, invalid.temperatureC!!, 0.0001f)
        assertEquals(4, invalid.health)
        assertEquals(5, invalid.status)
        assertEquals(2, invalid.plugged)
        assertNull(ChargeSnapshot.fromRaw(50, -1, null, null, null, null).percent)
        assertNull(ChargeSnapshot.fromRaw(51, 50, null, null, null, null).percent)
    }

    @Test fun percentCalculationHandlesFullAndVeryLargeScale() {
        assertEquals(100, ChargeSnapshot.fromRaw(100, 100, null, null, null, null).percent)
        assertEquals(50, ChargeSnapshot.fromRaw(1, 2, null, null, null, null).percent)
        assertEquals(100, ChargeSnapshot.fromRaw(Int.MAX_VALUE, Int.MAX_VALUE, null, null, null, null).percent)
        assertEquals(0, ChargeSnapshot.fromRaw(0, Int.MAX_VALUE, null, null, null, null).percent)
    }

    @Test fun unknownBatteryCodesRemainDistinctRawCodes() {
        val reading = ChargeSnapshot.fromRaw(null, null, Int.MAX_VALUE, 123, 456, 789)
        assertEquals(Int.MAX_VALUE / 10f, reading.temperatureC!!, 1f)
        assertEquals(123, reading.health)
        assertEquals(456, reading.status)
        assertEquals(789, reading.plugged)
    }

    @Test fun circleUsesReferenceProportionsAndUniformScale() {
        val f = SanctuaryLayout.fit(864, 1536)
        assertEquals(1f, f.scale, 0.0001f)
        assertEquals(0f, f.left, 0.0001f)
        assertEquals(0f, f.top, 0.0001f)
        val landscape = SanctuaryLayout.fit(1600, 900)
        assertEquals(900f / 1536f, landscape.scale, 0.0001f)
        assertTrue(landscape.left >= 0f)
        assertEquals(0f, landscape.top, 0.0001f)
        assertEquals(0f, SanctuaryLayout.fit(0, 0).scale, 0f)
    }

    @Test fun narrowerViewportCentersUnstretchedDesign() {
        val f = SanctuaryLayout.fit(432, 1536)
        assertEquals(0.5f, f.scale, 0f)
        assertEquals(0f, f.left, 0f)
        assertEquals(384f, f.top, 0f)
    }

    @Test fun onlyRuneAnglesAdvanceAndRestartAtZero() {
        assertEquals(2f, SanctuaryLayout.goldAngle(1000), 0.0001f)
        assertEquals(-1.5f, SanctuaryLayout.blueAngle(1000), 0.0001f)
        assertEquals(0f, SanctuaryLayout.goldAngle(180000), 0.0001f)
        assertEquals(0f, SanctuaryLayout.goldAngle(0), 0f)
    }

    @Test fun rotationClampsNegativeTimeAndWrapsWithoutOverflow() {
        assertEquals(0f, SanctuaryLayout.goldAngle(-1), 0f)
        assertEquals(0f, SanctuaryLayout.blueAngle(-1), 0f)
        assertEquals(0f, SanctuaryLayout.blueAngle(240000), 0f)
        assertEquals(2f, SanctuaryLayout.goldAngle(180000L * 100000 + 1000), 0.0001f)
        assertEquals(-1.5f, SanctuaryLayout.blueAngle(240000L * 100000 + 1000), 0.0001f)
    }
}
