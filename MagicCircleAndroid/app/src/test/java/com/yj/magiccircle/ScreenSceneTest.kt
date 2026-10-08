package com.yj.magiccircle

import org.junit.Assert.*
import org.junit.Test

class ScreenSceneTest {
    private val media = "12345678-1234-4234-8234-123456789abc"
    private fun layer(id: String = "a") = ImageLayer(id, media, .5f, .5f, 1f, 0f, false, true)
    private fun scene(layers: List<ImageLayer>) = ScreenScene("scene-$media", "작품", ScenePurpose.CHARGING, layers)
    private fun rejected(block: () -> Unit) { try { block(); fail("Invalid input accepted") } catch (_: IllegalArgumentException) {} }
    @Test fun durationPresetsAndFallback() {
        listOf(1000,3000,5000,7000).forEach { assertEquals(it.toLong(), ChargingTransition.durationMs(it)) }
        listOf(0,2000,Int.MAX_VALUE).forEach { assertEquals(7000L, ChargingTransition.durationMs(it)) }
    }
    @Test fun preparationDoesNotConsumeDisplayDuration() {
        assertEquals(12000L,ChargingTransition.prepareDeadline(10000L))
        listOf(1000,3000,5000,7000).forEach { assertEquals(10000L+it,ChargingTransition.displayDeadline(10000L,it)) }
        val current=Any();val stale=Any()
        assertFalse(ChargingTransition.acceptsCallback(2,1,current,stale))
        assertFalse(ChargingTransition.acceptsCallback(2,2,current,stale))
    }
    @Test fun validatesCompositionBoundaries() {
        val png = mapOf(media to "image/png")
        SceneRules.validate(scene(List(8) { layer("$it") }), png)
        rejected { SceneRules.validate(scene(List(9) { layer("$it") }), png) }
        rejected { SceneRules.validate(scene(List(3) { layer("$it").copy(visible=false) }), mapOf(media to "image/gif")) }
        rejected { SceneRules.validate(scene(listOf(layer(),layer())), png) }
        rejected { SceneRules.validate(scene(listOf(layer())), emptyMap()) }
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f).forEach { value ->
            rejected { SceneRules.validate(scene(listOf(layer().copy(x=value))), png) }
        }
        rejected { SceneRules.validate(scene(listOf(layer().copy(width=0f))), png) }
        rejected { SceneRules.validate(scene(listOf(layer().copy(angle=180f))), png) }
        listOf("", " ", "x\ny", "x".repeat(41)).forEach { name -> rejected { SceneRules.validate(scene(emptyList()).copy(name=name),png) } }
        rejected { SceneRules.validateInformation(listOf(InfoPlacement(InfoField.BATTERY,.5f,.5f,true),InfoPlacement(InfoField.BATTERY,.1f,.1f,false))) }
    }
    @Test fun characterIsIndependentWallpaperLayer() {
        val c=CharacterLayer(CharacterRules.defaults(media,"Character"),.5f,.5f,1f,0f,false,true,0)
        val wallpaper=scene(emptyList()).copy(purpose=ScenePurpose.WALLPAPER,character=c)
        SceneRules.validate(wallpaper,emptyMap())
        SceneRules.validate(wallpaper.copy(layers=List(8){layer("$it")},character=c.copy(beforeImage=8)),mapOf(media to "image/png"))
        rejected {SceneRules.validate(wallpaper.copy(purpose=ScenePurpose.CHARGING),emptyMap())}
        rejected {SceneRules.validate(wallpaper.copy(character=c.copy(beforeImage=1)),emptyMap())}
        rejected {SceneRules.validate(wallpaper.copy(character=c.copy(x=Float.NaN)),emptyMap())}
        rejected {SceneRules.validate(wallpaper.copy(character=c.copy(definition=c.definition.copy(artworkVersion=2))),emptyMap())}
    }
}
