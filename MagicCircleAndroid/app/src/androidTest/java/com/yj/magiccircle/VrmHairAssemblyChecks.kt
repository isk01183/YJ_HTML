package com.yj.magiccircle

import android.app.Instrumentation
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object VrmHairAssemblyChecks {
    fun actual(instrumentation: Instrumentation) {
        val context=instrumentation.targetContext
        check(android.os.Build.PRODUCT.startsWith("sdk_")) {"Private fixture export is emulator-only"}
        val store=VrmModelStore.get(context);val selected=store.selected()?.id
        val root=File(checkNotNull(context.getExternalFilesDir(null)),"hair-proof-${java.util.UUID.randomUUID()}");check(root.mkdir())
        val base=File(root,"base.vrm");checkNotNull(store.openModel(VrmAvatarRules.BASE)).use {input->base.outputStream().use(input::copyTo)}
        val donorId=listOf("f4df98833a830f84c6f8bcdb90701e86420bc2fe369e7936fff1cf3b0971573e",VrmAvatarRules.HAIR02).first {id->store.entries().any {it.id==id}}
        val donor=File(root,"donor.vrm");checkNotNull(store.openModel(donorId)).use {input->donor.outputStream().use(input::copyTo)}
        HairDocument.open(base,128L*1024*1024).use {doc->val f=doc.fingerprints();File(root,"base-fingerprints.json").writeText(hairJson(ho("world" to f.world,"shapes" to f.shapes)))}
        for((name,source) in listOf("original" to base,"hair02" to donor)) {
            val part=VrmHairAssembly.extract(base,source,File(root,"$name-part"),128L*1024*1024)
            val result=VrmHairAssembly.compose(base,part,File(root,"$name-composed.vrm"),128L*1024*1024)
            check(result.modelId==hash(result.file));android.util.Log.i("HairProof","$name ${part.id} ${result.modelId}")
        }
        check(store.selected()?.id==selected)
        android.util.Log.i("HairProof","private output ${root.path}")
    }
    fun run(instrumentation: Instrumentation) {
        for(text in listOf("{\"a\":TRUE}","{\"a\":01}","{\"a\":\"bad\\x01\"}","{\"a\":\"line\nfeed\"}"))
            check(runCatching {hairParse(text.toByteArray())}.isFailure){"Permissive JSON accepted: $text"}
        val root=File(instrumentation.targetContext.cacheDir,"hair-assembly-${java.util.UUID.randomUUID()}")
        check(root.mkdir())
        fun fixture(name: String)=File(root,name).also { file->
            instrumentation.context.assets.open("hair-parts/$name").use { input->file.outputStream().use(input::copyTo) }
        }
        val base=fixture("base.vrm");val donor=fixture("donor.vrm")
        val baseHash=hash(base);val donorHash=hash(donor)
        val budget=128L*1024*1024
        for((name,source) in listOf("original" to base,"hair02" to donor)) {
            val part=VrmHairAssembly.extract(base,source,File(root,"$name-part"),budget)
            val reread=VrmHairPartCodec.read(part.directory);check(part==reread)
            val result=VrmHairAssembly.compose(base,part,File(root,"$name.vrm"),budget)
            val expected=JSONObject(fixture("$name-expected.json").readText())
            check(result.protectedDigest==expected.getString("protectedDigest")) {"Protected body/face/physics changed"}
            check(result.hairDigest==expected.getString("hairDigest")) {"Donor hair changed"}
            check(result.partId==part.id&&result.modelId==hash(result.file))
            val file=File(root,"sentinel.vrm").apply {writeText("sentinel")}
            check(runCatching {VrmHairAssembly.compose(base,part,file,0)}.isFailure)
            check(runCatching {VrmHairAssembly.compose(base,part,file,budget)}.isFailure)
            check(file.readText()=="sentinel")
            val manifest=File(part.directory,"part.json");val originalJson=manifest.readText()
            val cyclic=JSONObject(originalJson);cyclic.getJSONArray("nodes").getJSONObject(0).put("parent",JSONObject().put("local",0));manifest.writeText(cyclic.toString())
            val fdsBefore=File("/proc/self/fd").list()!!.size
            repeat(20){check(runCatching {VrmHairPartCodec.read(part.directory)}.isFailure)}
            check(File("/proc/self/fd").list()!!.size<=fdsBefore+3){"Malformed part leaks open files"}
            manifest.writeText(originalJson)
            val damaged=File(part.directory,"part.bin")
            val bytes=damaged.readBytes();bytes[0]=(bytes[0].toInt() xor 1).toByte();damaged.writeBytes(bytes)
            check(runCatching {VrmHairPartCodec.read(part.directory)}.isFailure)
            check(runCatching {VrmHairAssembly.compose(base,part,File(root,"bad.vrm"),budget)}.isFailure)
            check(!File(root,"bad.vrm").exists())
        }
        check(hash(base)==baseHash&&hash(donor)==donorHash) {"Input originals modified"}
        check(runCatching {VrmHairAssembly.extract(base,donor,File(root,"zero"),0)}.isFailure)
        check(!File(root,"zero").exists())
        Thread.currentThread().interrupt()
        try {check(runCatching {VrmHairAssembly.extract(base,donor,File(root,"cancelled"),budget)}.isFailure)}finally {Thread.interrupted()}
        check(!File(root,"cancelled").exists())
        val blocked=File(root,"blocked").apply {writeText("keep")}
        check(runCatching {VrmHairAssembly.extract(base,donor,File(blocked,"part"),budget)}.isFailure)
        check(blocked.readText()=="keep")
        val huge=File(root,"oversize.vrm");java.io.RandomAccessFile(huge,"rw").use {it.setLength(VrmModelStore.MAX_BYTES+4)}
        check(runCatching {VrmHairAssembly.extract(base,huge,File(root,"oversize-part"),budget)}.isFailure)
        check(!File(root,"oversize-part").exists())
    }
    private fun hash(file: File): String {
        val md=MessageDigest.getInstance("SHA-256");file.inputStream().use { input->val b=ByteArray(32768);while(true){val n=input.read(b);if(n<0)break;md.update(b,0,n)}}
        return md.digest().joinToString(""){"%02x".format(it)}
    }
}
