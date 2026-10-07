package com.yj.magiccircle

import org.json.JSONObject
import java.util.Collections
import java.util.Locale

enum class OutfitSlot { HALO, HEAD, TORSO, GLOVES, SHOES, WINGS }
enum class CharacterMotion { STILL, IDLE, WAVE }
data class CharacterAppearance(val bodyType: String="feminine", val hair: String="bob", val face: String="round",
    val eyes: String="round", val brows: String="soft", val nose: String="small", val mouth: String="soft")
data class BodyProportions(val heightCm: Float=160f, val head: Float=1f, val shoulders: Float=1f,
    val torso: Float=1f, val arms: Float=1f, val legs: Float=1f, val torsoWidth: Float=1f,
    val armWidth: Float=1f, val legWidth: Float=1f) {
    fun values() = listOf(heightCm,head,shoulders,torso,arms,legs,torsoWidth,armWidth,legWidth)
    fun with(index: Int, v: Float) = when(index) {
        0->copy(heightCm=v); 1->copy(head=v); 2->copy(shoulders=v); 3->copy(torso=v)
        4->copy(arms=v); 5->copy(legs=v); 6->copy(torsoWidth=v); 7->copy(armWidth=v); 8->copy(legWidth=v)
        else->throw IllegalArgumentException("Unknown body control")
    }
}

/** Copies maps at the boundary: a wallpaper never shares mutable editor settings. */
class CharacterDefinition(val id: String, val name: String, val appearance: CharacterAppearance,
    val body: BodyProportions, outfit: Map<OutfitSlot,String>, colors: Map<String,Int>,
    val motion: CharacterMotion, val artworkVersion: Int=1) {
    val outfit: Map<OutfitSlot,String> = Collections.unmodifiableMap(LinkedHashMap(outfit))
    val colors: Map<String,Int> = Collections.unmodifiableMap(LinkedHashMap(colors))
    fun copy(id: String=this.id, name: String=this.name, appearance: CharacterAppearance=this.appearance,
        body: BodyProportions=this.body, outfit: Map<OutfitSlot,String> = this.outfit,
        colors: Map<String,Int> = this.colors, motion: CharacterMotion=this.motion, artworkVersion: Int=this.artworkVersion) =
        CharacterDefinition(id,name,appearance,body,outfit,colors,motion,artworkVersion)
    override fun equals(other: Any?) = other is CharacterDefinition && id==other.id && name==other.name &&
        appearance==other.appearance && body==other.body && outfit==other.outfit && colors==other.colors &&
        motion==other.motion && artworkVersion==other.artworkVersion
    override fun hashCode() = listOf(id,name,appearance,body,outfit,colors,motion,artworkVersion).hashCode()
}

object CharacterColors {
    fun parseRgb(r: String,g: String,b: String): Int? {
        val values=listOf(r,g,b).map { s -> if(s.matches(Regex("[0-9]{1,3}"))) s.toInt() else return null }
        if(values.any { it !in 0..255 }) return null
        return (0xff000000L or (values[0].toLong() shl 16) or (values[1].toLong() shl 8) or values[2].toLong()).toInt()
    }
    fun parseHex(text: String): Int? = if(text.matches(Regex("#[0-9a-fA-F]{6}")))
        (0xff000000L or text.substring(1).toLong(16)).toInt() else null
    fun hex(argb: Int) = String.format(Locale.ROOT,"#%06X",argb and 0xffffff)
}

object CharacterRules {
    val appearanceIds = listOf(listOf("feminine","masculine"),listOf("bob","long","tied"),listOf("round","oval","soft-square"),
        listOf("round","almond","soft"),listOf("soft","straight","arched"),listOf("small","soft","defined"),listOf("soft","smile","straight"))
    val outfitIds = mapOf(OutfitSlot.HALO to listOf("none","ring","star"), OutfitSlot.HEAD to listOf("none","star","ribbon"),
        OutfitSlot.TORSO to listOf("tunic","coat","robe"),OutfitSlot.GLOVES to listOf("none","short","long"),
        OutfitSlot.SHOES to listOf("boots","flats"),OutfitSlot.WINGS to listOf("none","feather","fairy"))
    val bodyKeys = listOf("heightCm","head","shoulders","torso","arms","legs","torsoWidth","armWidth","legWidth")
    val appearanceKeys = listOf("bodyType","hair","face","eyes","brows","nose","mouth")
    val colorKeys = listOf("skin","hair","eyes") + OutfitSlot.entries.flatMap { listOf(it.name.lowercase(Locale.ROOT)+".base",it.name.lowercase(Locale.ROOT)+".accent") }
    fun appearanceValues(a: CharacterAppearance) = listOf(a.bodyType,a.hair,a.face,a.eyes,a.brows,a.nose,a.mouth)
    fun appearance(values: List<String>) = CharacterAppearance(values[0],values[1],values[2],values[3],values[4],values[5],values[6])
    fun range(index: Int) = if(index==0) 120f..200f else if(index<6) .8f..1.2f else .7f..1.3f
    fun defaults(id: String,name: String): CharacterDefinition {
        val colors=colorKeys.associateWith { 0xfff4e9cc.toInt() }.toMutableMap()
        colors.putAll(mapOf("skin" to 0xfff8c9ad.toInt(),"hair" to 0xffa91b50.toInt(),"eyes" to 0xffaf4a94.toInt(),
            "torso.base" to 0xff5096dc.toInt(),"torso.accent" to 0xffeee0b8.toInt(),"shoes.base" to 0xff493b4a.toInt(),
            "gloves.base" to 0xffeee9e1.toInt(),"wings.base" to 0xffe0ebfa.toInt()))
        return CharacterDefinition(id,name,CharacterAppearance(),BodyProportions(),
            mapOf(OutfitSlot.HALO to "none",OutfitSlot.HEAD to "none",OutfitSlot.TORSO to "tunic",
                OutfitSlot.GLOVES to "none",OutfitSlot.SHOES to "boots",OutfitSlot.WINGS to "none"),colors,CharacterMotion.IDLE)
    }
    fun validate(v: CharacterDefinition) {
        require(MediaValidation.isId(v.id) && v.artworkVersion==1)
        require(v.name==v.name.trim() && v.name.length in 1..40 && v.name.none { it.isISOControl() || Character.getType(it)==Character.FORMAT.toInt() })
        appearanceValues(v.appearance).forEachIndexed { i,s -> require(s in appearanceIds[i]) }
        v.body.values().forEachIndexed { i,f -> require(f.isFinite() && f in range(i)) }
        require(v.outfit.keys==OutfitSlot.entries.toSet())
        v.outfit.forEach { (slot,id) -> require(id in outfitIds.getValue(slot)) }
        require(v.colors.keys==colorKeys.toSet() && v.colors.values.all { it ushr 24==255 })
    }
    fun toJson(v: CharacterDefinition): JSONObject {
        validate(v)
        return JSONObject().put("id",v.id).put("name",v.name).put("artworkVersion",v.artworkVersion).put("motion",v.motion.name)
            .put("appearance",JSONObject().also { j -> appearanceValues(v.appearance).forEachIndexed { i,s -> j.put(appearanceKeys[i],s) } })
            .put("body",JSONObject().also { j -> v.body.values().forEachIndexed { i,f -> j.put(bodyKeys[i],f.toDouble()) } })
            .put("outfit",JSONObject().also { j -> v.outfit.forEach { (k,s) -> j.put(k.name,s) } })
            .put("colors",JSONObject().also { j -> v.colors.forEach { (k,c) -> j.put(k,CharacterColors.hex(c)) } })
    }
    fun fromJson(j: JSONObject): CharacterDefinition {
        fun string(o: JSONObject,k: String) = (o.get(k) as? String) ?: throw IllegalArgumentException(k)
        val a=j.getJSONObject("appearance"); val b=j.getJSONObject("body")
        val outfit=j.getJSONObject("outfit"); val colors=j.getJSONObject("colors")
        require(outfit.length()==OutfitSlot.entries.size && colors.length()==colorKeys.size)
        val numbers=bodyKeys.map { (b.get(it) as? Number)?.toFloat() ?: throw IllegalArgumentException(it) }
        require(j.get("artworkVersion") is Int)
        return CharacterDefinition(string(j,"id"),string(j,"name"),appearance(appearanceKeys.map { string(a,it) }),
            BodyProportions(numbers[0],numbers[1],numbers[2],numbers[3],numbers[4],numbers[5],numbers[6],numbers[7],numbers[8]),
            OutfitSlot.entries.associateWith { string(outfit,it.name) }, colorKeys.associateWith { CharacterColors.parseHex(string(colors,it)) ?: throw IllegalArgumentException(it) },
            CharacterMotion.valueOf(string(j,"motion")),j.getInt("artworkVersion")).also { validate(it) }
    }
}
