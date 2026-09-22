package com.yj.magiccircle

import org.junit.Assert.*
import org.junit.Test

class WallpaperPolicyTest {
    @Test fun stillTargetAndApiAreNeverSubstituted() {
        assertFalse(WallpaperPolicy.supportsStill(22, "home"))
        assertTrue(WallpaperPolicy.supportsStill(23, "home"))
        assertFalse(WallpaperPolicy.supportsStill(23, "lock"))
        assertTrue(WallpaperPolicy.supportsStill(24, "lock"))
        assertFalse(WallpaperPolicy.supportsStill(37, "both"))
        assertFalse(WallpaperPolicy.supportsStill(37, ""))
    }
    @Test fun onlyTheTwoArtworkThemesAreAllowed() {
        assertTrue(WallpaperPolicy.allowedTheme("ref-W03"))
        assertTrue(WallpaperPolicy.allowedTheme("ref-R01"))
        for (id in listOf(null, "native-N01", "ref-C03", "W03", ""))
            assertFalse(WallpaperPolicy.allowedTheme(id))
    }
    @Test fun renderingRequiresEveryLifecycleCondition() {
        assertTrue(WallpaperPolicy.shouldRender(true, true, false))
        assertFalse(WallpaperPolicy.shouldRender(false, true, false))
        assertFalse(WallpaperPolicy.shouldRender(true, false, false))
        assertFalse(WallpaperPolicy.shouldRender(true, true, true))
    }
    @Test fun confirmationRequiresTheActualRequestedComponent() {
        assertTrue(WallpaperPolicy.confirmed("R01", "R01"))
        assertTrue(WallpaperPolicy.confirmed("W03", "W03"))
        assertFalse(WallpaperPolicy.confirmed("R01", null))
        assertFalse(WallpaperPolicy.confirmed("R01", "W03"))
        assertFalse(WallpaperPolicy.confirmed("native-N01", "native-N01"))
    }
    @Test fun pickerCancellationNeverBecomesNewSuccess() {
        assertEquals("retained", WallpaperPolicy.liveResult("R01", "W03", "W03", false))
        assertEquals("retained", WallpaperPolicy.liveResult("W03", "W03", "W03", false))
        assertEquals("unconfirmed", WallpaperPolicy.liveResult("R01", null, "W03", true))
        assertEquals("unconfirmed", WallpaperPolicy.liveResult("R01", "W03", "W03", true))
        assertEquals("unconfirmed", WallpaperPolicy.liveResult("R01", "R01", "W03", false))
        assertEquals("confirmed", WallpaperPolicy.liveResult("R01", "R01", "W03", true))
    }
    @Test fun stillBitmapPreservesViewportRatioWithinSixMillionPixels() {
        assertEquals(1080 to 2400, WallpaperPolicy.bitmapSize(1080, 2400))
        assertEquals(2560 to 1600, WallpaperPolicy.bitmapSize(2560, 1600))
        assertEquals(1632 to 3674, WallpaperPolicy.bitmapSize(3200, 7200))
        val (width, height) = WallpaperPolicy.bitmapSize(Int.MAX_VALUE, Int.MAX_VALUE)
        assertTrue(width.toLong() * height <= 6_000_000)
        assertEquals(width, height)
        for (size in listOf(1 to Int.MAX_VALUE, Int.MAX_VALUE to 1)) {
            val bounded = WallpaperPolicy.bitmapSize(size.first, size.second)
            assertTrue(bounded.first.toLong() * bounded.second <= 6_000_000)
        }
        assertThrows(IllegalArgumentException::class.java) { WallpaperPolicy.bitmapSize(0, 10) }
    }
}
