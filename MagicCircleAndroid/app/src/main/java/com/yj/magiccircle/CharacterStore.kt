package com.yj.magiccircle

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class CharacterStore(private val root: File, private val file: AtomicFile=AtomicFile(File(root,"characters.json"))) {
    companion object {
        @Volatile private var instance: CharacterStore?=null
        @JvmStatic fun get(context: Context): CharacterStore = instance ?: synchronized(this) {
            instance ?: CharacterStore(File(context.applicationContext.noBackupFilesDir,"characters")).also { instance=it }
        }
    }
    private data class State(val saved: List<CharacterDefinition>,val drafts: List<CharacterDefinition>)
    private fun read(): State {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return State(emptyList(),emptyList())
        try {
            val bytes=file.openRead().use { it.readBytes() }; require(bytes.size<=4*1024*1024)
            val j=JSONObject(bytes.toString(Charsets.UTF_8)); require(j.get("version")==1)
            fun entries(key: String): List<CharacterDefinition> {
                val array=j.getJSONArray(key); require(array.length()<=500)
                return (0 until array.length()).map { CharacterRules.fromJson(array.getJSONObject(it)) }.also { list -> require(list.map { it.id }.toSet().size==list.size) }
            }
            return State(entries("saved"),entries("drafts"))
        } catch(e: Exception) { throw IOException("Character library could not be read; original preserved",e) }
    }
    @Synchronized fun list(): List<CharacterDefinition> = read().saved
    @Synchronized fun find(id: String) = read().saved.find { it.id==id }
    @Synchronized fun draft(id: String) = read().drafts.find { it.id==id }
    @Synchronized fun save(v: CharacterDefinition) {
        CharacterRules.validate(v); val s=read()
        write(State(s.saved.filterNot { it.id==v.id }+v,s.drafts.filterNot { it.id==v.id }))
    }
    @Synchronized fun saveDraft(v: CharacterDefinition) {
        CharacterRules.validate(v); val s=read(); write(s.copy(drafts=s.drafts.filterNot { it.id==v.id }+v))
    }
    @Synchronized fun discardDraft(id: String) { val s=read(); write(s.copy(drafts=s.drafts.filterNot { it.id==id })) }
    @Synchronized fun delete(id: String) { val s=read(); write(State(s.saved.filterNot { it.id==id },s.drafts.filterNot { it.id==id })) }
    private fun write(s: State) {
        require(s.saved.size<=500 && s.drafts.size<=500) { "Character library is full" }
        if(!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create character directory")
        val bytes=JSONObject().put("version",1).put("saved",JSONArray(s.saved.map { CharacterRules.toJson(it) }))
            .put("drafts",JSONArray(s.drafts.map { CharacterRules.toJson(it) })).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size<=4*1024*1024)
        // The primary file was validated by read(). Keep its exact bytes before replacing it.
        if(file.baseFile.exists()) atomicWrite(AtomicFile(File(root,"characters.recovery.json")),file.readFully())
        atomicWrite(file,bytes)
    }
    private fun atomicWrite(target: AtomicFile,bytes: ByteArray) {
        val stream=target.startWrite()
        try { stream.write(bytes); target.finishWrite(stream) }
        catch(e: Exception) { target.failWrite(stream); throw IOException("Character save failed",e) }
    }
}
