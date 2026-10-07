package com.yj.magiccircle

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object CharacterStorageChecks {
    fun run(context: Context) {
        val root = File(context.cacheDir, "character-check-${UUID.randomUUID()}")
        val store = CharacterStore(root)
        val a = CharacterRules.defaults(UUID.randomUUID().toString(), "별빛")
        check(CharacterRules.fromJson(CharacterRules.toJson(a)) == a)
        store.save(a)
        val draft = a.copy(name="초안", appearance=a.appearance.copy(hair="long"))
        store.saveDraft(draft)
        check(CharacterStore(root).find(a.id) == a && CharacterStore(root).draft(a.id) == draft)
        val b = draft.copy(id=UUID.randomUUID().toString())
        store.save(b)
        store.delete(a.id)
        check(store.find(a.id) == null && store.draft(a.id) == null && store.find(b.id) == b)
        val file = File(root, "characters.json")
        val bytes = file.readBytes()
        val failing = CharacterStore(root, object : AtomicFile(file) {
            override fun startWrite(): FileOutputStream = throw IOException("injected failure")
        })
        check(runCatching { failing.save(a) }.exceptionOrNull() is IOException)
        check(file.readBytes().contentEquals(bytes) && failing.list() == listOf(b))
        file.writeText("{broken")
        check(runCatching { CharacterStore(root).list() }.isFailure)
        check(runCatching { CharacterStore(root).save(a) }.isFailure)
        check(file.readText() == "{broken")
        check(File(root, "characters.recovery.json").exists())
        val mutable = a.colors.toMutableMap()
        val snap = a.copy(colors=mutable)
        mutable["hair"] = 0xff000000.toInt()
        check(snap.colors["hair"] == a.colors["hair"])
    }
}
