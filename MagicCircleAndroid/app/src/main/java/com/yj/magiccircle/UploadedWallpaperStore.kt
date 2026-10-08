package com.yj.magiccircle

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.util.UUID

/** Each engine captures an immutable generation; publishing a candidate never rewrites its assets. */
internal object UploadedWallpaperStore {
    private val services=listOf(UploadedWallpaperService0::class.java,UploadedWallpaperService1::class.java,UploadedWallpaperService2::class.java)
    private fun valid(key: String) {require(key.matches(Regex("upload-slot-[0-2]")))}
    fun component(context: Context,key: String): ComponentName {valid(key);return ComponentName(context,services[key.last().digitToInt()])}
    fun key(context: Context,component: ComponentName?)=services.indexOfFirst {ComponentName(context,it)==component}.takeIf {it>=0}?.let {"upload-slot-$it"}
    private fun root(context: Context)=File(context.noBackupFilesDir,"wallpapers")
    fun file(context: Context,key: String): File {valid(key);return File(root(context),key)}
    data class Snapshot(val scene: ScreenScene?,private val directory: File,private val mediaId: String?) {
        fun open(id: String): InputStream {require(MediaValidation.isId(id));return File(directory,id).inputStream()}
        fun openSingle(): InputStream = if(mediaId==null)directory.inputStream()else open(mediaId)
    }
    fun snapshot(context: Context,key: String)=snapshot(root(context),key)
    fun snapshot(root: File,key: String): Snapshot {
        valid(key)
        val file=File(root,"$key.json")
        if(!file.exists()) return Snapshot(null,File(root,key),null) // v1.14 single-file slots.
        val bytes=AtomicFile(file).readFully();require(bytes.size<=65536)
        val json=JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.getInt("version") in 1..2)
        val generation=json.getString("generation");require(MediaValidation.isId(generation))
        val directory=File(root,generation)
        val scene=if(json.isNull("scene"))null else SceneData.readScene(json.getJSONObject("scene"))
        val mediaId=if(scene==null)json.getString("mediaId")else null
        if(scene!=null) {
            require(scene.purpose==ScenePurpose.WALLPAPER)
            SceneRules.validate(scene,scene.layers.associate {it.mediaId to mime(File(directory,it.mediaId))})
        } else require(MediaValidation.isId(mediaId))
        return Snapshot(scene,directory,mediaId)
    }
    private fun mime(file: File): String {
        val header=ByteArray(12)
        file.inputStream().use {it.read(header)}
        return MediaValidation.mime(header) ?: throw IOException("Invalid snapshot image")
    }
    @Synchronized fun stage(context: Context,id: String): String {
        val manager=WallpaperManager.getInstance(context)
        val used=mutableSetOf<String>()
        key(context,manager.wallpaperInfo?.component)?.let(used::add)
        if(Build.VERSION.SDK_INT>=34) key(context,manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.component)?.let(used::add)
        else for(i in 0..2) {
            val k="upload-slot-$i"
            // Older APIs cannot identify the lock engine: protect every previously published slot.
            if(File(root(context),"$k.json").exists() || file(context,k).exists())used.add(k)
        }
        val slot=WallpaperPolicy.freeUploadSlot(used) ?: throw IOException("No safely reusable wallpaper slot")
        val library=MediaLibrary.get(context)
        val scene=library.scene(id)
        require(scene!=null || MediaValidation.isId(id))
        library.leaseMedia(scene?.layers?.map {it.mediaId} ?: listOf(id)).use {
            stageFiles(root(context),slot,id,scene){library.open(it,false)}
        }
        context.packageManager.setComponentEnabledSetting(component(context,slot),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP)
        return slot
    }
    fun stageFiles(root: File,key: String,id: String,scene: ScreenScene?,open: (String)->InputStream) {
        valid(key);require(scene?.purpose==ScenePurpose.WALLPAPER || scene==null && MediaValidation.isId(id))
        if(!root.isDirectory && !root.mkdirs())throw IOException("Cannot create wallpaper storage")
        val generation=UUID.randomUUID().toString()
        val directory=File(root,generation);check(directory.mkdir())
        val ids=scene?.layers?.map {it.mediaId}?.distinct() ?: listOf(id)
        for(media in ids) {
            require(MediaValidation.isId(media))
            File(directory,media).outputStream().use {out->open(media).use {MediaValidation.copy(it,out,MediaValidation.MAX_BYTES)}}
        }
        if(scene!=null)SceneRules.validate(scene,ids.associateWith {mime(File(directory,it))})
        val json=JSONObject().put("version",2).put("generation",generation)
            .put("scene",scene?.let {SceneData.sceneJson(it)} ?: JSONObject.NULL).put("mediaId",if(scene==null)id else JSONObject.NULL)
        val atomic=AtomicFile(File(root,"$key.json"));val out=atomic.startWrite()
        try {out.write(json.toString().toByteArray(Charsets.UTF_8));atomic.finishWrite(out)}
        catch(e: Throwable){atomic.failWrite(out);throw e}
        // ponytail: preserve generations while old engines may read them; explicit safe GC can reclaim disk later.
    }
}
class UploadedWallpaperService0: MagicWallpaperService(){override val themeId="upload-slot-0"}
class UploadedWallpaperService1: MagicWallpaperService(){override val themeId="upload-slot-1"}
class UploadedWallpaperService2: MagicWallpaperService(){override val themeId="upload-slot-2"}
