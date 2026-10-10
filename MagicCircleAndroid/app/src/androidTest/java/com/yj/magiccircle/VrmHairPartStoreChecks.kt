package com.yj.magiccircle

import android.app.Instrumentation
import java.io.File
import java.util.UUID
import org.json.JSONObject

object VrmHairPartStoreChecks {
    fun run(instrumentation: Instrumentation) {
        val context=instrumentation.targetContext
        val root=File(context.cacheDir,"hair-store-${UUID.randomUUID()}").apply {check(mkdir())}
        val global=VrmModelStore.get(context);val selected=global.selected()?.id
        val models=VrmModelStore(File(root,"models"));val partsRoot=File(root,"parts");val store=VrmHairPartStore(partsRoot,models)
        val budget=128L*1024*1024
        check(store.prepare(budget).isEmpty());check(runCatching {store.compose("0".repeat(64),budget)}.isFailure)
        check(runCatching {global.openModel(VrmAvatarRules.BASE)!!.use {models.registerDerived(it,"No base")}}.isFailure)
        val donor=listOf(VrmHairPartStore.DONOR,VrmAvatarRules.HAIR02).first {id->global.entries().any {it.id==id}}
        for(id in listOf(VrmAvatarRules.BASE,donor))checkNotNull(global.openModel(id)).use {models.importModel(it,id.take(8))}
        val oldSelected=models.selected()?.id
        checkNotNull(models.openModel(VrmAvatarRules.BASE)).use {check(!models.registerDerived(it,"same").derived)}
        check(models.selected()?.id==oldSelected)
        val avatarsRoot=File(root,"avatars");val avatars=VrmAvatarStore(avatarsRoot)
        val legacy=avatars.save(VrmAvatarDefinition(UUID.randomUUID().toString(),0,"Legacy",VrmAvatarRules.original()),null)
        val before=File(avatarsRoot,"avatars.json").readBytes()
        val parts=store.prepare(budget);check(parts.map {it.styleId}.toSet()==setOf("e-original","e-hair02"))
        check(store.prepare(budget).map {it.id}==parts.map {it.id})
        val blocker=File(partsRoot,"receipts").apply {writeText("keep")}
        check(runCatching {store.compose(parts[0].id,budget)}.isFailure);check(blocker.readText()=="keep")
        check(models.selected()?.id==oldSelected&&JSONObject(File(partsRoot,"index.json").readText()).getJSONObject("assemblies").length()==0)
        check(blocker.delete())
        for(part in parts) {
            val receipt=store.compose(part.id,budget);check(store.compose(part.id,budget)==receipt)
            check(models.entries().first {it.id==receipt.modelId}.derived)
            val appearance=receipt.appearance(VrmDye("#12ABEF","#123456"))
            check(VrmAvatarRules.readAppearance(VrmAvatarRules.appearanceJson(appearance))==appearance)
            check(store.resolve(appearance)==receipt);check(store.resolve(legacy.appearance)==null)
            val receiptFile=File(partsRoot,"receipts/${receipt.modelId}-${part.id}.json");val proof=receiptFile.readText()
            receiptFile.writeText(JSONObject(proof).put("styleId","unknown").toString())
            check(runCatching {store.resolve(appearance)}.isFailure);receiptFile.writeText(proof)
            val bin=File(part.directory,"part.bin");java.io.RandomAccessFile(bin,"rw").use {r->val first=r.read();r.seek(0);r.write(first xor 1)}
            check(runCatching {store.resolve(appearance)}.isFailure)
            java.io.RandomAccessFile(bin,"rw").use {r->val first=r.read();r.seek(0);r.write(first xor 1)}
            check(store.resolve(appearance)==receipt)
            check(File(avatarsRoot,"avatars.json").readBytes().contentEquals(before))
            val v=legacy.copy(id=UUID.randomUUID().toString(),revision=0,appearance=appearance)
            val saved=avatars.save(v,null);avatars.saveDraft(saved)
            val state=File(avatarsRoot,"avatars.json").readBytes();check(runCatching {avatars.save(saved,0)}.isFailure)
            check(File(avatarsRoot,"avatars.json").readBytes().contentEquals(state))
            // Keep each iteration's legacy-byte check independent of the new test avatar.
            File(avatarsRoot,"avatars.json").writeBytes(before)
        }
        check(models.selected()?.id==oldSelected&&global.selected()?.id==selected)
        val existing=File(root,"models/models/${VrmAvatarRules.BASE}.vrm")
        java.io.RandomAccessFile(existing,"rw").use {r->val first=r.read();r.seek(0);r.write(first xor 1)}
        check(runCatching {global.openModel(VrmAvatarRules.BASE)!!.use {models.registerDerived(it,"same damaged")}}.isFailure)
    }
}
