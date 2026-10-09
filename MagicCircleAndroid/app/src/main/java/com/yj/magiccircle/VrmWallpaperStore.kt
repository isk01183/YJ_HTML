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
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

internal object VrmWallpaperStore {
    private val services=listOf(VrmWallpaperService0::class.java,VrmWallpaperService1::class.java,VrmWallpaperService2::class.java)
    private fun valid(slot: String) {require(slot.matches(Regex("vrm-slot-[0-2]")))}
    private fun root(context: Context)=File(context.noBackupFilesDir,"vrm-wallpapers")
    fun component(context: Context,slot: String): ComponentName {valid(slot);return ComponentName(context,services[slot.last().digitToInt()])}
    fun key(context: Context,component: ComponentName?)=services.indexOfFirst {ComponentName(context,it)==component}.takeIf {it>=0}?.let {"vrm-slot-$it"}
    fun freeSlot(protectedSlots: Set<String>)=(0..2).map {"vrm-slot-$it"}.firstOrNull {it !in protectedSlots}
    data class Snapshot(val generation: String,val modelId: String,val placement: VrmPlacement,
        val scene: ScreenScene?=null,private val directory: File?=null) {
        fun acceptsRetry(request: String?)=request?.substringBefore(':')==generation
        fun open(id: String): InputStream {require(MediaValidation.isId(id));return File(checkNotNull(directory),id).inputStream()}
    }
    data class PendingApplication(val request: String,val slot: String?=null,val generation: String?=null,val launched: Boolean=false,val launch: String?=null)
    private val working=mutableSetOf<String>()
    private fun workKey(context: Context,request: String)=root(context).absolutePath+":"+request
    private fun pendingFile(context: Context)=AtomicFile(File(root(context),"pending.json"))
    // AtomicFile.openRead may remove an in-progress .new file; readers and publishers share this lock.
    @Synchronized private fun read(file: AtomicFile,limit: Long): ByteArray = file.openRead().use {input->
        ByteArrayOutputStream().also {MediaValidation.copy(input,it,limit)}.toByteArray()
    }
    @Synchronized private fun write(file: AtomicFile,json: JSONObject) {
        file.baseFile.parentFile?.let {if(!it.isDirectory && !it.mkdirs())throw IOException("Cannot create wallpaper storage")}
        val bytes=json.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=65536)
        val output=file.startWrite()
        try {output.write(bytes);file.finishWrite(output)}catch(e: Throwable){file.failWrite(output);throw e}
    }
    private fun readPending(context: Context): PendingApplication? {
        val file=pendingFile(context)
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists())return null
        val json=JSONObject(read(file,4096).toString(Charsets.UTF_8));require(json.getInt("version")==1)
        val request=json.getString("request");require(MediaValidation.isId(request))
        val slot=if(json.isNull("slot"))null else json.getString("slot").also(::valid)
        val generation=if(json.isNull("generation"))null else json.getString("generation").also {require(MediaValidation.isId(it))}
        val launched=json.getBoolean("launched")
        val launch=if(json.isNull("launch"))null else json.getString("launch").also {require(MediaValidation.isId(it))}
        require((slot==null)==(generation==null) && (!launched || slot!=null))
        return PendingApplication(request,slot,generation,launched,launch)
    }
    private fun writePending(context: Context,p: PendingApplication)=write(pendingFile(context),JSONObject().put("version",1)
        .put("request",p.request).put("slot",p.slot ?: JSONObject.NULL).put("generation",p.generation ?: JSONObject.NULL).put("launched",p.launched).put("launch",p.launch ?: JSONObject.NULL))
    @Synchronized fun pendingApplication(context: Context): PendingApplication? {
        val p=readPending(context) ?: return null
        // Persist the candidate generation before copying, so a process death after publication is recoverable.
        val prepared=p.slot!=null && File(root(context),"${p.slot}.json").exists() &&
            JSONObject(read(AtomicFile(File(root(context),"${p.slot}.json")),65536).toString(Charsets.UTF_8)).getString("generation")==p.generation
        check(!p.launched || prepared) {"Applied candidate is unavailable"}
        return if(prepared)p else p.copy(generation=null)
    }
    @Synchronized fun beginApplication(context: Context): String {
        check(readPending(context)==null) {"Finish the previous wallpaper request first"}
        return UUID.randomUUID().toString().also {writePending(context,PendingApplication(it))}
    }
    @Synchronized fun markLaunched(context: Context,request: String): String {
        val p=checkNotNull(pendingApplication(context));check(p.request==request && p.generation!=null)
        check(!isPreparing(context,request));enable(context,p.slot!!)
        return UUID.randomUUID().toString().also {writePending(context,p.copy(launched=true,launch=it))}
    }
    @Synchronized fun isPreparing(context: Context,request: String)=workKey(context,request) in working
    @Synchronized fun finishApplication(context: Context,request: String,launch: String?=null): Boolean {
        val p=readPending(context) ?: return false
        if(p.request!=request || p.launched && p.launch!=launch)return false
        check(!isPreparing(context,request)) {"Wallpaper copy is still running"}
        pendingFile(context).delete()
        check(readPending(context)==null) {"Cannot finish wallpaper request"}
        return true
    }
    fun snapshot(context: Context,slot: String)=snapshot(root(context),slot)
    fun snapshot(root: File,slot: String): Snapshot {
        valid(slot)
        val atomic=AtomicFile(File(root,"$slot.json"))
        val bytes=read(atomic,65536)
        val json=JSONObject(bytes.toString(Charsets.UTF_8));val version=json.getInt("version")
        require(version in 1..2 && (version!=1 || bytes.size<=8192))
        val id=json.getString("modelId");require(id.matches(Regex("[a-f0-9]{64}")))
        val generation=json.getString("generation");require(UUID.fromString(generation).toString()==generation)
        val p=VrmPlacement(json.optDouble("x",0.0).toFloat(),json.optDouble("y",0.0).toFloat(),
            json.optDouble("scale",1.0).toFloat(),json.optBoolean("blink",true)).normalized()
        if(version==1)return Snapshot(generation,id,p)
        val scene=SceneData.readScene(json.getJSONObject("scene"));val directory=File(root,generation)
        require(scene.vrm?.modelId==id && scene.vrm.placement==p)
        SceneRules.validate(scene,scene.layers.map {it.mediaId}.distinct().associateWith {imageMime(File(directory,it))})
        return Snapshot(generation,id,p,scene,directory)
    }
    private fun availableSlot(context: Context): String {
        val manager=WallpaperManager.getInstance(context);val protectedSlots=mutableSetOf<String>()
        var uncertain=Build.VERSION.SDK_INT<34
        try {
            key(context,manager.wallpaperInfo?.component)?.let(protectedSlots::add)
            if(Build.VERSION.SDK_INT>=34)key(context,manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.component)?.let(protectedSlots::add)
        } catch(_: Exception) {uncertain=true}
        if(uncertain)for(i in 0..2)if(File(root(context),"vrm-slot-$i.json").exists())protectedSlots.add("vrm-slot-$i")
        return freeSlot(protectedSlots) ?: throw IOException("No safely reusable VRM wallpaper slot")
    }
    private fun enable(context: Context,slot: String)=context.packageManager.setComponentEnabledSetting(component(context,slot),
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP)
    fun stage(context: Context,request: String,modelId: String,placement: VrmPlacement)=stageReserved(context,request,modelId) {slot,generation->
        publish(root(context),slot,Snapshot(generation,modelId,placement.normalized()))
    }
    fun stage(context: Context,request: String,scene: ScreenScene)=stageReserved(context,request,checkNotNull(scene.vrm).modelId) {slot,generation->
        val library=MediaLibrary.get(context)
        library.leaseMedia(scene.layers.map {it.mediaId}).use {compose(root(context),slot,generation,scene){library.open(it,false)}}
    }
    private fun stageReserved(context: Context,request: String,modelId: String,prepare: (String,String)->Unit): String {
        val p=synchronized(this) {
            val current=checkNotNull(readPending(context));check(current.request==request && current.slot==null)
            require(VrmModelStore.get(context).entries().any {it.id==modelId}) {"Character is unavailable"}
            val reserved=current.copy(slot=availableSlot(context),generation=UUID.randomUUID().toString())
            writePending(context,reserved);working.add(workKey(context,request));reserved
        }
        try {
            checkNotNull(VrmModelStore.get(context).openModel(modelId)).use {VrmModelStore.verifyOutput(modelId,it)}
            prepare(p.slot!!,p.generation!!);enable(context,p.slot);return p.slot
        }
        finally {synchronized(this){working.remove(workKey(context,request))}}
    }
    fun stageFiles(root: File,slot: String,modelId: String,placement: VrmPlacement): Snapshot {
        valid(slot);require(modelId.matches(Regex("[a-f0-9]{64}")))
        if(!root.isDirectory && !root.mkdirs())throw IOException("Cannot create wallpaper storage")
        return Snapshot(UUID.randomUUID().toString(),modelId,placement.normalized()).also {publish(root,slot,it)}
    }
    private fun publish(root: File,slot: String,snapshot: Snapshot) {
        val p=snapshot.placement
        val json=JSONObject().put("version",if(snapshot.scene==null)1 else 2).put("generation",snapshot.generation).put("modelId",snapshot.modelId)
            .put("x",p.x).put("y",p.y).put("scale",p.scale).put("blink",p.blink)
        snapshot.scene?.let {json.put("scene",SceneData.sceneJson(it))}
        write(AtomicFile(File(root,"$slot.json")),json)
    }
    fun stageFiles(root: File,slot: String,scene: ScreenScene,open: (String)->InputStream): Snapshot =
        compose(root,slot,UUID.randomUUID().toString(),scene,open)
    private fun compose(root: File,slot: String,generation: String,scene: ScreenScene,open: (String)->InputStream): Snapshot {
        valid(slot);require(scene.vrm!=null && scene.purpose==ScenePurpose.WALLPAPER && scene.layers.size<=8)
        val ids=scene.layers.map {it.mediaId}.distinct();require(ids.all {MediaValidation.isId(it)})
        val directory=File(root,generation);check(directory.mkdirs())
        val mime=ids.associateWith {id->
            val file=File(directory,id)
            file.outputStream().use {out->open(id).use {MediaValidation.copy(it,out,MediaValidation.MAX_BYTES)};out.fd.sync()}
            imageMime(file)
        }
        SceneRules.validate(scene,mime)
        // Keep generations intact while old engines may still read them; reclamation needs engine-aware GC.
        return Snapshot(generation,scene.vrm.modelId,scene.vrm.placement,scene,directory).also {publish(root,slot,it)}
    }
    private fun imageMime(file: File): String {
        require(MediaValidation.isId(file.name) && file.length() in 1..MediaValidation.MAX_BYTES)
        val header=ByteArray(12);file.inputStream().use {it.read(header)}
        val mime=MediaValidation.mime(header) ?: throw IOException("Invalid snapshot image")
        if(mime=="image/gif")file.inputStream().use {MediaValidation.validateGif(it)}
        val options=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
        android.graphics.BitmapFactory.decodeFile(file.path,options)
        require(options.outMimeType==mime && MediaValidation.validDimensions(options.outWidth,options.outHeight))
        return mime
    }
    fun retry(context: Context,slot: String,generation: String) {
        valid(slot);require(snapshot(context,slot).generation==generation)
        context.getSharedPreferences("vrm-retry",Context.MODE_PRIVATE).edit().putString(slot,"$generation:${UUID.randomUUID()}").apply()
    }
}
