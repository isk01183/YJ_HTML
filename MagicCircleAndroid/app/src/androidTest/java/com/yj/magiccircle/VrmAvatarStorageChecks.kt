package com.yj.magiccircle

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object VrmAvatarStorageChecks {
    fun run(context: Context) {
        val root=File(context.cacheDir,"avatar-check-${UUID.randomUUID()}")
        val store=VrmAvatarStore(root)
        val v=VrmAvatarDefinition(UUID.randomUUID().toString(),0,"별빛",VrmAvatarRules.original())
        check(VrmAvatarRules.fromJson(VrmAvatarRules.toJson(v))==v)
        store.saveDraft(v);check(store.list().isEmpty() && store.draft(v.id)==v)
        val first=store.save(v,null);check(first.revision==1 && store.draft(v.id)==null)
        val second=store.save(v.copy(id=UUID.randomUUID().toString(),name="다른 색"),null)
        check(store.list().size==2 && store.find(first.id)==first)
        val changed=first.copy(appearance=first.appearance.copy(dye=VrmDye("#FFFFFF","#123456")))
        store.saveDraft(changed)
        check(store.find(first.id)==first && store.draft(first.id)==changed)
        val file=File(root,"avatars.json");val original=file.readBytes()
        check(runCatching {store.save(changed,0)}.isFailure)
        check(file.readBytes().contentEquals(original))
        for(silent in listOf(false,true)) {
            val failing=VrmAvatarStore(root,object: AtomicFile(file){
                override fun startWrite(): FileOutputStream {if(!silent)throw IOException("injected");return super.startWrite()}
                override fun finishWrite(stream: FileOutputStream?){if(silent)failWrite(stream)else super.finishWrite(stream)}
            })
            check(runCatching {failing.save(changed,1)}.isFailure)
            check(store.find(first.id)==first && store.draft(first.id)==changed && file.readBytes().contentEquals(original))
        }
        val saved=store.save(changed,1)
        check(saved.revision==2 && VrmAvatarStore(root).find(saved.id)==saved && store.draft(saved.id)==null)
        check(store.find(second.id)==second)
        check(runCatching {store.saveDraft(first)}.isFailure)
        check(runCatching {store.save(v,null)}.isFailure)
        file.writeText("{broken")
        check(runCatching {store.save(v,null)}.isFailure && file.readText()=="{broken")
    }
}
