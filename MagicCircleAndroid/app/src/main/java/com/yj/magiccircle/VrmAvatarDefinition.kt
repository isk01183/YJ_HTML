package com.yj.magiccircle

import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class VrmDye(val hair: String?=null,val iris: String?=null)
data class VrmAvatarAppearance(val profileVersion: Int,val baseModelId: String,val hairId: String,val modelId: String,val dye: VrmDye=VrmDye(),val parts: Map<String,String> = emptyMap())
data class VrmAvatarDefinition(val id: String,val revision: Int,val name: String,val appearance: VrmAvatarAppearance)

object VrmAvatarRules {
    const val BASE="ef6513de66aee3ab78b105e53b2e72c5d92834fc2a49c542221c08ec9f0811d0"
    const val HAIR02="c1853aa3c22b5c3b58ba8f819c4b2c4e7bd318aeeefaa9739f7c9f3bb205a708"
    val hairIds=listOf("e-original","e-hair02")
    // shortcut: these two exact, locally validated exports only; add profiles after separate part/texture verification.
    fun modelId(hairId: String): String=when(hairId){"e-original"->BASE;"e-hair02"->HAIR02;else->throw IllegalArgumentException("Unknown hair")}
    fun original()=VrmAvatarAppearance(1,BASE,"e-original",BASE)
    fun parseHex(value: String): String {require(value.matches(Regex("#[0-9a-fA-F]{6}")));return value.uppercase(Locale.ROOT)}
    fun fromRgb(r: Int,g: Int,b: Int): String {
        require(r in 0..255 && g in 0..255 && b in 0..255)
        return String.format(Locale.ROOT,"#%02X%02X%02X",r,g,b)
    }
    fun validate(value: VrmAvatarAppearance) {
        require(value.baseModelId==BASE && value.hairId in hairIds)
        when(value.profileVersion) {
            1->require(value.parts.isEmpty()&&value.modelId==modelId(value.hairId))
            2->require(value.parts.keys==setOf("hair")&&value.parts.getValue("hair").matches(Regex("[a-f0-9]{64}"))&&value.modelId.matches(Regex("[a-f0-9]{64}")))
            else->throw IllegalArgumentException("Unsupported appearance")
        }
        listOfNotNull(value.dye.hair,value.dye.iris).forEach {require(parseHex(it)==it)}
    }
    fun validate(value: VrmAvatarDefinition) {
        require(runCatching {UUID.fromString(value.id).toString()==value.id}.getOrDefault(false))
        require(value.revision>=0 && value.revision<Int.MAX_VALUE)
        require(value.name==value.name.trim() && value.name.length in 1..40 && value.name.none {it.isISOControl() || Character.getType(it)==Character.FORMAT.toInt()})
        validate(value.appearance)
    }
    fun appearanceJson(value: VrmAvatarAppearance): JSONObject {
        validate(value)
        return JSONObject().put("profileVersion",value.profileVersion).put("baseModelId",value.baseModelId).put("hairId",value.hairId)
            .put("modelId",value.modelId).put("hair",value.dye.hair ?: JSONObject.NULL).put("iris",value.dye.iris ?: JSONObject.NULL)
            .also {if(value.profileVersion==2)it.put("parts",JSONObject(value.parts))}
    }
    fun readAppearance(json: JSONObject): VrmAvatarAppearance {
        fun color(key: String): String? {require(json.has(key));return if(json.isNull(key))null else json.get(key).also {require(it is String)} as String}
        require(json.get("profileVersion") is Int);val version=json.getInt("profileVersion")
        val parts=if(json.has("parts"))json.getJSONObject("parts").let {p->p.keys().asSequence().associateWith {p.get(it).also {v->require(v is String)} as String}}else emptyMap()
        return VrmAvatarAppearance(version,json.getString("baseModelId"),json.getString("hairId"),json.getString("modelId"),VrmDye(color("hair"),color("iris")),parts).also(::validate)
    }
    fun toJson(value: VrmAvatarDefinition): JSONObject {
        validate(value)
        return JSONObject().put("version",1).put("id",value.id).put("revision",value.revision).put("name",value.name).put("appearance",appearanceJson(value.appearance))
    }
    fun fromJson(json: JSONObject): VrmAvatarDefinition {
        require(json.get("version")==1 && json.get("revision") is Int)
        return VrmAvatarDefinition(json.getString("id"),json.getInt("revision"),json.getString("name"),readAppearance(json.getJSONObject("appearance"))).also(::validate)
    }
}
