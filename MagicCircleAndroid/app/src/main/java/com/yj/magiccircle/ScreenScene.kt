package com.yj.magiccircle

import org.json.JSONArray
import org.json.JSONObject

enum class ScenePurpose { WALLPAPER, CHARGING }
enum class InfoField { BATTERY, STATUS, TEMPERATURE, HEALTH, CONNECTION, METER, MESSAGE }
data class ImageLayer(val id: String, val mediaId: String, val x: Float, val y: Float,
    val width: Float, val angle: Float, val flipX: Boolean, val visible: Boolean)
data class CharacterLayer(val definition: CharacterDefinition, val x: Float=.5f,val y: Float=.5f,
    val width: Float=.6f,val angle: Float=0f,val flipX: Boolean=false,val visible: Boolean=true,val beforeImage: Int=0)
data class VrmSceneLayer(val modelId: String,val placement: VrmPlacement=VrmPlacement(),val visible: Boolean=true,val beforeImage: Int=0)
data class ScreenScene(val id: String, val name: String, val purpose: ScenePurpose, val layers: List<ImageLayer>,val character: CharacterLayer?=null,val vrm: VrmSceneLayer?=null)
data class StageMessages(val connected: String, val charging: String, val complete: String) {
    fun at(progress: Float) = when { progress<.28f -> connected; progress<.83f -> charging; else -> complete }
}
data class InfoPlacement(val field: InfoField, val x: Float, val y: Float, val visible: Boolean, val messages: StageMessages?=null)
data class EditorDraft(val key: String, val scene: ScreenScene?, val information: List<InfoPlacement>?)

object SceneRules {
    @JvmStatic fun isSceneId(id: String?) = id != null && id.startsWith("scene-") && MediaValidation.isId(id.substring(6))
    @JvmStatic fun validate(scene: ScreenScene, mimeById: Map<String,String>) {
        require(isSceneId(scene.id))
        require(scene.name == scene.name.trim() && scene.name.length in 1..40 && scene.name.none { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() })
        require(scene.layers.size <= 8 && scene.layers.map { it.id }.toSet().size == scene.layers.size)
        require(scene.layers.count { mimeById[it.mediaId] == "image/gif" } <= 2)
        scene.vrm?.let {
            require(scene.purpose==ScenePurpose.WALLPAPER && scene.character==null)
            require(it.modelId.matches(Regex("[a-f0-9]{64}")))
            require(it.beforeImage in 0..scene.layers.size)
            require(it.placement.x.isFinite() && it.placement.y.isFinite() && it.placement.scale.isFinite())
            require(it.placement==it.placement.normalized())
        }
        scene.character?.let {
            require(scene.purpose==ScenePurpose.WALLPAPER)
            CharacterRules.validate(it.definition)
            require(it.beforeImage in 0..scene.layers.size)
            require(it.x.isFinite() && it.x in 0f..1f && it.y.isFinite() && it.y in 0f..1f)
            require(it.width.isFinite() && it.width in .05f..4f && it.angle.isFinite() && it.angle>=-180f && it.angle<180f)
        }
        scene.layers.forEach {
            require(it.id.isNotBlank() && it.id.length <= 80)
            require(mimeById[it.mediaId] in setOf("image/jpeg", "image/png", "image/gif"))
            require(it.x.isFinite() && it.x in 0f..1f && it.y.isFinite() && it.y in 0f..1f)
            require(it.width.isFinite() && it.width in .05f..4f && it.angle.isFinite() && it.angle >= -180f && it.angle < 180f)
        }
    }
    @JvmStatic fun validateInformation(items: List<InfoPlacement>) {
        require(items.map { it.field }.toSet().size == items.size)
        items.forEach {
            require(it.x.isFinite() && it.x in 0f..1f && it.y.isFinite() && it.y in 0f..1f)
            it.messages?.let { m ->
                require(it.field==InfoField.MESSAGE)
                require(listOf(m.connected,m.charging,m.complete).all { text -> text.length<=120 && text.none { c -> c.isISOControl() && c!='\n' } })
            }
        }
    }
}

/** Immutable manifest extension; each update is published only after MediaLibrary commits. */
internal data class SceneData(
    val scenes: Map<String,ScreenScene> = emptyMap(),
    val layouts: Map<String,List<InfoPlacement>> = emptyMap(),
    val drafts: Map<String,EditorDraft> = emptyMap(),
    val duration: Int = 7000
) {
    fun references(id: String) = scenes.values.any { s -> s.layers.any { it.mediaId == id } } ||
        drafts.values.any { d -> d.scene?.layers?.any { it.mediaId == id } == true }
    fun withScene(scene: ScreenScene, info: List<InfoPlacement>?) = copy(
        scenes = scenes + (scene.id to scene.copy(layers=scene.layers.toList())),
        layouts = if (info == null) layouts - scene.id else layouts + (scene.id to info.toList()), drafts=drafts-scene.id)
    fun withoutScene(id: String) = copy(scenes=scenes-id,layouts=layouts-id,drafts=drafts-id)
    fun withLayout(id: String, info: List<InfoPlacement>?) = copy(layouts=if(info==null) layouts-id else layouts+(id to info.toList()))
    fun withDraft(d: EditorDraft) = copy(drafts=drafts+(d.key to d.copy(scene=d.scene?.copy(layers=d.scene.layers.toList()), information=d.information?.toList())))
    fun withoutDraft(id: String) = copy(drafts=drafts-id)
    fun withDuration(ms: Int) = copy(duration=ChargingTransition.durationMs(ms).toInt())
    fun json(): JSONObject = JSONObject().put("scenes",JSONArray(scenes.values.map { sceneJson(it) }))
        .put("layouts",JSONObject().also { o -> layouts.forEach { (id,info) -> o.put(id, infoJson(info)) } })
        .put("drafts",JSONArray(drafts.values.map { d -> JSONObject().put("key",d.key)
            .put("scene",d.scene?.let { sceneJson(it) } ?: JSONObject.NULL)
            .put("information",d.information?.let { infoJson(it) } ?: JSONObject.NULL) }))
        .put("duration",duration)
    companion object {
        @JvmStatic fun sceneJson(s: ScreenScene): JSONObject = JSONObject().put("id",s.id).put("name",s.name).put("purpose",s.purpose.name)
            .put("layers",JSONArray(s.layers.map { l -> JSONObject().put("id",l.id).put("mediaId",l.mediaId)
                .put("x",l.x.toDouble()).put("y",l.y.toDouble()).put("width",l.width.toDouble()).put("angle",l.angle.toDouble()).put("flipX",l.flipX).put("visible",l.visible) }))
            .put("character",s.character?.let { c->JSONObject().put("definition",CharacterRules.toJson(c.definition))
                .put("x",c.x.toDouble()).put("y",c.y.toDouble()).put("width",c.width.toDouble()).put("angle",c.angle.toDouble())
                .put("flipX",c.flipX).put("visible",c.visible).put("beforeImage",c.beforeImage) } ?: JSONObject.NULL)
            .put("vrm",s.vrm?.let { v->JSONObject().put("modelId",v.modelId).put("visible",v.visible).put("beforeImage",v.beforeImage)
                .put("x",v.placement.x.toDouble()).put("y",v.placement.y.toDouble()).put("scale",v.placement.scale.toDouble()).put("blink",v.placement.blink) } ?: JSONObject.NULL)
        fun readScene(o: JSONObject) = ScreenScene(o.getString("id"),o.getString("name"),ScenePurpose.valueOf(o.getString("purpose")),
            o.getJSONArray("layers").objects().map { l -> ImageLayer(l.getString("id"),l.getString("mediaId"),l.getDouble("x").toFloat(),l.getDouble("y").toFloat(),
                l.getDouble("width").toFloat(),l.getDouble("angle").toFloat(),l.getBoolean("flipX"),l.getBoolean("visible")) },
            if(!o.has("character") || o.isNull("character"))null else o.getJSONObject("character").let { c->
                CharacterLayer(CharacterRules.fromJson(c.getJSONObject("definition")),c.getDouble("x").toFloat(),c.getDouble("y").toFloat(),
                    c.getDouble("width").toFloat(),c.getDouble("angle").toFloat(),c.getBoolean("flipX"),c.getBoolean("visible"),c.getInt("beforeImage")) },
            if(!o.has("vrm") || o.isNull("vrm"))null else o.getJSONObject("vrm").let { v->
                VrmSceneLayer(v.getString("modelId"),VrmPlacement(v.getDouble("x").toFloat(),v.getDouble("y").toFloat(),
                    v.getDouble("scale").toFloat(),v.getBoolean("blink")),v.getBoolean("visible"),v.getInt("beforeImage")) })
        private fun infoJson(info: List<InfoPlacement>) = JSONArray(info.map { p -> JSONObject().put("field",p.field.name).put("x",p.x.toDouble()).put("y",p.y.toDouble()).put("visible",p.visible).also { o ->
            p.messages?.let { o.put("messages",JSONObject().put("connected",it.connected).put("charging",it.charging).put("complete",it.complete)) }
        } })
        private fun readInfo(a: JSONArray) = a.objects().map { o ->
            val m=if(o.has("messages")) o.getJSONObject("messages").let { StageMessages(it.getString("connected"),it.getString("charging"),it.getString("complete")) } else null
            InfoPlacement(InfoField.valueOf(o.getString("field")),o.getDouble("x").toFloat(),o.getDouble("y").toFloat(),o.getBoolean("visible"),m)
        }.also { SceneRules.validateInformation(it) }
        private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
        @JvmStatic fun read(o: JSONObject, mime: Map<String,String>): SceneData {
            val scenes = linkedMapOf<String,ScreenScene>()
            o.getJSONArray("scenes").objects().forEach { val s=readScene(it); SceneRules.validate(s,mime); require(scenes.put(s.id,s)==null) }
            fun valid(id: String) = ThemeSelection.isValid(id) || mime.containsKey(id) || scenes.containsKey(id)
            val layouts = linkedMapOf<String,List<InfoPlacement>>()
            val entries=o.getJSONObject("layouts")
            entries.keys().forEach { id -> require(valid(id)); layouts[id]=readInfo(entries.getJSONArray(id)) }
            val drafts=linkedMapOf<String,EditorDraft>()
            o.getJSONArray("drafts").objects().forEach { d ->
                val key=d.getString("key"); val s=if(d.isNull("scene")) null else readScene(d.getJSONObject("scene"))
                if(s!=null) { require(s.id==key); SceneRules.validate(s,mime) } else require(valid(key))
                val info=if(d.isNull("information")) null else readInfo(d.getJSONArray("information"))
                require(drafts.put(key,EditorDraft(key,s,info))==null)
            }
            return SceneData(scenes,layouts,drafts,ChargingTransition.durationMs(o.getInt("duration")).toInt())
        }
    }
}
