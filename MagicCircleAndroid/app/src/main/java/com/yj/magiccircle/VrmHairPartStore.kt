package com.yj.magiccircle

import android.content.Context
import android.system.Os
import android.util.AtomicFile
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID

data class VrmHairReceipt(val modelId: String,val baseModelId: String,val partId: String,val styleId: String,val assemblerVersion: Int,val protectedDigest: String,val hairDigest: String) {
    fun toJson()=JSONObject().put("modelId",modelId).put("baseModelId",baseModelId).put("partId",partId).put("styleId",styleId)
        .put("assemblerVersion",assemblerVersion).put("protectedDigest",protectedDigest).put("hairDigest",hairDigest)
    fun appearance(dye: VrmDye=VrmDye())=VrmAvatarAppearance(2,baseModelId,styleId,modelId,dye,mapOf("hair" to partId))
}

/** Immutable private parts and proofs. Published models are retained for saved wallpaper snapshots. */
class VrmHairPartStore(private val root: File,private val models: VrmModelStore) {
    companion object {
        const val DONOR="f4df98833a830f84c6f8bcdb90701e86420bc2fe369e7936fff1cf3b0971573e"
        const val ASSEMBLER=1
        private val sources=listOf(VrmAvatarRules.BASE,DONOR,VrmAvatarRules.HAIR02)
        @Volatile private var instance: VrmHairPartStore?=null
        fun get(context: Context)=instance ?: synchronized(this) {
            instance ?: VrmHairPartStore(File(context.applicationContext.noBackupFilesDir,"vrm-hair-parts"),VrmModelStore.get(context)).also {instance=it}
        }
    }
    private val index get()=AtomicFile(File(root,"index.json"))
    private fun id(value: String)=value.also {require(it.matches(Regex("[a-f0-9]{64}")))}
    private fun directory(partId: String)=File(root,"parts/${id(partId)}")
    private fun state(): JSONObject {
        if(!index.baseFile.exists()&&!File(index.baseFile.path+".bak").exists())return JSONObject().put("version",1).put("parts",JSONArray()).put("assemblies",JSONObject())
        val json=read(index);require(json.get("version")==1&&json.getJSONArray("parts").length()<=64)
        val parts=json.getJSONArray("parts");val ids=(0 until parts.length()).map {id(parts.getString(it))};require(ids.distinct().size==ids.size)
        val assemblies=json.getJSONObject("assemblies");assemblies.keys().forEach {require(it in ids);id(assemblies.getString(it))};return json
    }
    private fun read(file: AtomicFile): JSONObject {
        val output=java.io.ByteArrayOutputStream();file.openRead().use {MediaValidation.copy(it,output,1024*1024L)}
        val text=output.toString("UTF-8");validateVrmJson(text);return JSONObject(text)
    }
    private fun write(file: AtomicFile,json: JSONObject) {
        require(file.baseFile.parentFile!!.isDirectory||file.baseFile.parentFile!!.mkdirs())
        val bytes=json.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=1024*1024)
        val stream=file.startWrite()
        try {stream.write(bytes);stream.fd.sync();file.finishWrite(stream);check(file.readFully().contentEquals(bytes))}
        catch(e: Exception){file.failWrite(stream);throw e}
    }
    private fun part(partId: String): VrmHairPart {
        val p=VrmHairPartCodec.read(directory(partId));require(p.id==partId&&p.baseModelId==VrmAvatarRules.BASE&&p.sourceModelId in sources)
        require(p.styleId==if(p.sourceModelId==VrmAvatarRules.BASE)"e-original"else"e-hair02");return p
    }
    private fun copyModel(modelId: String,target: File): File {
        checkNotNull(models.openModel(modelId)).use {input->target.outputStream().use {MediaValidation.copy(input,it,VrmModelStore.MAX_BYTES)}}
        require(hairHash(target)==modelId);return target
    }
    private fun work(): File {require(root.isDirectory||root.mkdirs());return File(root,".work-${UUID.randomUUID()}").also {check(it.mkdir())}}
    private fun clean(work: File) {File(work,"part/part.json").delete();File(work,"part/part.bin").delete();File(work,"part").delete();listOf("base.vrm","source.vrm","output.vrm").forEach {File(work,it).delete()};work.delete()}
    @Synchronized fun prepare(budgetBytes: Long): List<VrmHairPart> {
        val json=state();val array=json.getJSONArray("parts");val result=(0 until array.length()).map {part(array.getString(it))}.toMutableList()
        val available=models.entries().map {it.id}.toSet();if(VrmAvatarRules.BASE !in available)return result
        for(source in sources) {
            val style=if(source==VrmAvatarRules.BASE)"e-original"else"e-hair02"
            if(source !in available||result.any {it.styleId==style})continue
            val temp=work()
            try {
                val base=copyModel(VrmAvatarRules.BASE,File(temp,"base.vrm"));val donor=if(source==VrmAvatarRules.BASE)base else copyModel(source,File(temp,"source.vrm"))
                val p=VrmHairAssembly.extract(base,donor,File(temp,"part"),budgetBytes);val dest=directory(p.id)
                require(dest.parentFile!!.isDirectory||dest.parentFile!!.mkdirs())
                if(dest.exists())require(part(p.id).id==p.id)else Os.rename(p.directory.path,dest.path)
                array.put(p.id);write(index,json);result.add(part(p.id))
            }finally {clean(temp)}
        }
        return result
    }
    @Synchronized fun compose(partId: String,budgetBytes: Long): VrmHairReceipt {
        val json=state();val array=json.getJSONArray("parts");require((0 until array.length()).any {array.getString(it)==partId})
        val p=part(partId);val assemblies=json.getJSONObject("assemblies")
        if(assemblies.has(partId))return checkNotNull(resolve(VrmAvatarAppearance(2,p.baseModelId,p.styleId,assemblies.getString(partId),parts=mapOf("hair" to partId))))
        require(models.entries().any {it.id==VrmAvatarRules.BASE}){"Import the base model first"}
        val temp=work()
        try {
            val base=copyModel(p.baseModelId,File(temp,"base.vrm"))
            val result=VrmHairAssembly.compose(base,p,File(temp,"output.vrm"),budgetBytes)
            val model=result.file.inputStream().use {models.registerDerived(it,"E · ${p.styleId}")};require(model.id==result.modelId)
            val receipt=VrmHairReceipt(model.id,p.baseModelId,p.id,p.styleId,ASSEMBLER,result.protectedDigest,result.hairDigest)
            val file=AtomicFile(File(root,"receipts/${receipt.modelId}-${receipt.partId}.json"))
            if(file.baseFile.exists())require(read(file).toString()==receipt.toJson().toString())else write(file,receipt.toJson())
            assemblies.put(partId,model.id);write(index,json)
            return checkNotNull(resolve(receipt.appearance()))
        }finally {clean(temp)}
    }
    @Synchronized fun resolve(appearance: VrmAvatarAppearance): VrmHairReceipt? {
        VrmAvatarRules.validate(appearance);if(appearance.profileVersion==1)return null
        val partId=appearance.parts.getValue("hair");val p=part(partId)
        require(p.styleId==appearance.hairId&&appearance.modelId !in sources)
        require(state().getJSONObject("assemblies").getString(partId)==appearance.modelId)
        val r=read(AtomicFile(File(root,"receipts/${id(appearance.modelId)}-${id(partId)}.json")))
        val receipt=VrmHairReceipt(r.getString("modelId"),r.getString("baseModelId"),r.getString("partId"),r.getString("styleId"),r.getInt("assemblerVersion"),r.getString("protectedDigest"),r.getString("hairDigest"))
        require(receipt.assemblerVersion==ASSEMBLER&&receipt.appearance(appearance.dye)==appearance)
        val manifest=read(AtomicFile(File(p.directory,"part.json")))
        require(receipt.protectedDigest==manifest.getString("protectedDigest")&&receipt.hairDigest==manifest.getString("hairDigest"))
        checkNotNull(models.openModel(receipt.modelId)).use {VrmModelStore.verifyOutput(receipt.modelId,it)}
        return receipt
    }
}
