package com.yj.magiccircle

import org.junit.Assert.*
import org.junit.Test

class CharacterRulesTest {
    private fun value() = CharacterRules.defaults("12345678-1234-4234-8234-123456789abc", "별빛")
    private fun rejected(block: () -> Unit) { try { block(); fail("Invalid character accepted") } catch (_: IllegalArgumentException) {} }

    @Test fun rgbAndHexPreserveValidColorsRejectIncompleteInput() {
        assertEquals(0xff5096dc.toInt(), CharacterColors.parseRgb("80", "150", "220"))
        assertEquals(0xff5096dc.toInt(), CharacterColors.parseHex("#5096dc"))
        assertEquals("#5096DC", CharacterColors.hex(0xff5096dc.toInt()))
        listOf("#50", "GG5096", "", "#5096DCFF").forEach { assertNull(CharacterColors.parseHex(it)) }
        listOf("256", "-1", "", "1.5").forEach { assertNull(CharacterColors.parseRgb(it, "0", "0")) }
    }

    @Test fun validatesAllBodyRangesAndUnknownParts() {
        val original = value()
        CharacterRules.validate(original)
        listOf(119f, 201f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            rejected { CharacterRules.validate(original.copy(body=original.body.copy(heightCm=it))) }
        }
        listOf(.79f,1.21f).forEach { rejected { CharacterRules.validate(original.copy(body=original.body.copy(head=it))) } }
        listOf(.69f,1.31f).forEach { rejected { CharacterRules.validate(original.copy(body=original.body.copy(armWidth=it))) } }
        rejected { CharacterRules.validate(original.copy(appearance=original.appearance.copy(hair="unknown"))) }
        rejected { CharacterRules.validate(original.copy(artworkVersion=2)) }
        rejected { CharacterRules.validate(original.copy(name="x\ny")) }
        rejected { CharacterRules.validate(original.copy(colors=emptyMap())) }
        rejected { CharacterRules.validate(original.copy(outfit=original.outfit - OutfitSlot.TORSO)) }
    }

    @Test fun changesDoNotResetOtherAppearanceOrSlotSettings() {
        val original = value()
        val changed = original.copy(appearance=original.appearance.copy(face="oval"))
        assertEquals(original.appearance.hair, changed.appearance.hair)
        assertEquals(original.appearance.eyes, changed.appearance.eyes)
        assertEquals(original.colors, changed.colors)
        assertEquals(original.outfit, changed.outfit)
    }
}
