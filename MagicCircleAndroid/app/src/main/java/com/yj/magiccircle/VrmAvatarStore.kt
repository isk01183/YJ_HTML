package com.yj.magiccircle

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class VrmAvatarStore(private val root: File,private val file: AtomicFile=AtomicFile(File(root,"avatars.json"))) {
    companion object {
        @Volatile private var instance: VrmAvatarStore?=null
        fun get(context: Context)=instance ?: synchronized(this) {
            instance ?: VrmAvatarStore(File(context.applicationContext.noBackupFilesDir,"vrm-avatars")).also {instance=it}
        }
    }
    private data class State(val saved: List<VrmAvatarDefinition> = emptyList(),val drafts: List<VrmAvatarDefinition> = emptyList())
    private fun read(): State {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists())return State()
        val bytes=file.openRead().use {input->ByteArrayOutputStream().also {MediaValidation.copy(input,it,1024L*1024)}.toByteArray()}
        val json=JSONObject(bytes.toString(Charsets.UTF_8));require(json.get("version")==1)
        fun entries(key: String): List<VrmAvatarDefinition> {
            val a=json.getJSONArray(key);require(a.length()<=1024)
            return (0 until a.length()).map {VrmAvatarRules.fromJson(a.getJSONObject(it))}.also {list->
                require(list.map {it.id}.distinct().size==list.size)
                if(key=="saved")require(list.all {it.revision>0})
            }
        }
        return State(entries("saved"),entries("drafts"))
    }
    @Synchronized fun list()=read().saved
    @Synchronized fun drafts()=read().drafts
    @Synchronized fun find(id: String)=read().saved.find {it.id==id}
    @Synchronized fun draft(id: String)=read().drafts.find {it.id==id}
    @Synchronized fun save(value: VrmAvatarDefinition,expectedRevision: Int?): VrmAvatarDefinition {
        VrmAvatarRules.validate(value)
        val state=read();val current=state.saved.find {it.id==value.id}
        require(if(expectedRevision==null) current==null && value.revision==0 else current!=null && current.revision==expectedRevision && value.revision==expectedRevision) {"Character changed; reopen before saving"}
        val saved=value.copy(revision=(current?.revision ?: 0)+1);VrmAvatarRules.validate(saved)
        write(State(state.saved.filterNot {it.id==value.id}+saved,state.drafts.filterNot {it.id==value.id}))
        return saved
    }
    @Synchronized fun saveDraft(value: VrmAvatarDefinition) {
        VrmAvatarRules.validate(value);val state=read();val current=state.saved.find {it.id==value.id}
        require(value.revision==(current?.revision ?: 0)) {"Character changed; reopen before editing"}
        write(state.copy(drafts=state.drafts.filterNot {it.id==value.id}+value))
    }
    @Synchronized fun discardDraft(id: String) {val state=read();write(state.copy(drafts=state.drafts.filterNot {it.id==id}))}
    private fun write(state: State) {
        require(state.saved.size<=1024 && state.drafts.size<=1024)
        if(!root.isDirectory && !root.mkdirs())throw IOException("Cannot create avatar storage")
        val bytes=JSONObject().put("version",1).put("saved",JSONArray(state.saved.map {VrmAvatarRules.toJson(it)}))
            .put("drafts",JSONArray(state.drafts.map {VrmAvatarRules.toJson(it)})).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size<=1024*1024)
        val stream=file.startWrite()
        try {
            stream.write(bytes);stream.fd.sync();file.finishWrite(stream)
            if(!file.readFully().contentEquals(bytes))throw IOException("Avatar commit verification failed")
        }catch(e: Exception){file.failWrite(stream);throw IOException("Avatar save failed; previous data retained",e)}
    }
}
