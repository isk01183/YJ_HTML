package com.yj.magiccircle

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

internal object VrmWallpaperStore {
    private val services=listOf(VrmWallpaperService0::class.java,VrmWallpaperService1::class.java,VrmWallpaperService2::class.java)
    private fun valid(slot: String) {require(slot.matches(Regex("vrm-slot-[0-2]")))}
    private fun root(context: Context)=File(context.noBackupFilesDir,"vrm-wallpapers")
    fun component(context: Context,slot: String): ComponentName {valid(slot);return ComponentName(context,services[slot.last().digitToInt()])}
    fun key(context: Context,component: ComponentName?)=services.indexOfFirst {ComponentName(context,it)==component}.takeIf {it>=0}?.let {"vrm-slot-$it"}
    fun freeSlot(protectedSlots: Set<String>)=(0..2).map {"vrm-slot-$it"}.firstOrNull {it !in protectedSlots}
    data class Snapshot(val generation: String,val modelId: String,val placement: VrmPlacement) {
        fun acceptsRetry(request: String?)=request?.substringBefore(':')==generation
    }
    fun snapshot(context: Context,slot: String)=snapshot(root(context),slot)
    fun snapshot(root: File,slot: String): Snapshot {
        valid(slot)
        val atomic=AtomicFile(File(root,"$slot.json"))
        val bytes=atomic.openRead().use {input->
            val buffer=ByteArray(8193);var size=0
            while(size<buffer.size){val n=input.read(buffer,size,buffer.size-size);if(n<0)break;require(n>0);size+=n}
            require(size<=8192);buffer.copyOf(size)
        }
        val json=JSONObject(bytes.toString(Charsets.UTF_8));require(json.getInt("version")==1)
        val id=json.getString("modelId");require(id.matches(Regex("[a-f0-9]{64}")))
        val generation=json.getString("generation");require(UUID.fromString(generation).toString()==generation)
        return Snapshot(generation,id,VrmPlacement(json.optDouble("x",0.0).toFloat(),json.optDouble("y",0.0).toFloat(),
            json.optDouble("scale",1.0).toFloat(),json.optBoolean("blink",true)).normalized())
    }
    @Synchronized fun stage(context: Context,modelId: String,placement: VrmPlacement): String {
        require(VrmModelStore.get(context).entries().any {it.id==modelId}) {"Character is unavailable"}
        val manager=WallpaperManager.getInstance(context);val protectedSlots=mutableSetOf<String>()
        var uncertain=Build.VERSION.SDK_INT<34
        try {
            key(context,manager.wallpaperInfo?.component)?.let(protectedSlots::add)
            if(Build.VERSION.SDK_INT>=34)key(context,manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.component)?.let(protectedSlots::add)
        } catch(_: Exception) {uncertain=true}
        if(uncertain)for(i in 0..2)if(File(root(context),"vrm-slot-$i.json").exists())protectedSlots.add("vrm-slot-$i")
        val slot=freeSlot(protectedSlots) ?: throw IOException("No safely reusable VRM wallpaper slot")
        stageFiles(root(context),slot,modelId,placement)
        context.packageManager.setComponentEnabledSetting(component(context,slot),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP)
        return slot
    }
    fun stageFiles(root: File,slot: String,modelId: String,placement: VrmPlacement): Snapshot {
        valid(slot);require(modelId.matches(Regex("[a-f0-9]{64}")))
        if(!root.isDirectory && !root.mkdirs())throw IOException("Cannot create wallpaper storage")
        val p=placement.normalized();val snapshot=Snapshot(UUID.randomUUID().toString(),modelId,p)
        val json=JSONObject().put("version",1).put("generation",snapshot.generation).put("modelId",modelId)
            .put("x",p.x).put("y",p.y).put("scale",p.scale).put("blink",p.blink)
        val atomic=AtomicFile(File(root,"$slot.json"));val output=atomic.startWrite()
        try {output.write(json.toString().toByteArray());atomic.finishWrite(output)}
        catch(e: Exception){atomic.failWrite(output);throw e}
        return snapshot
    }
    fun retry(context: Context,slot: String,generation: String) {
        valid(slot);require(snapshot(context,slot).generation==generation)
        context.getSharedPreferences("vrm-retry",Context.MODE_PRIVATE).edit().putString(slot,"$generation:${UUID.randomUUID()}").apply()
    }
}
