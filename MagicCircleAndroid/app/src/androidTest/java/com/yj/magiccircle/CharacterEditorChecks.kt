package com.yj.magiccircle

import android.app.Instrumentation
import android.content.Intent
import android.widget.EditText
import android.view.View
import java.util.UUID

object CharacterEditorChecks {
    fun run(test: Instrumentation) {
        check(android.os.Build.PRODUCT.startsWith("sdk_")) { "Character UI checks are emulator-only" }
        val store=CharacterStore.get(test.targetContext)
        val original=CharacterRules.defaults(UUID.randomUUID().toString(),"Editor check")
        store.save(original)
        var activity=test.startActivitySync(Intent(test.targetContext,CharacterActivity::class.java)
            .putExtra("characterId",original.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CharacterActivity
        fun waitFor(tag: String): View {
            var found: View?=null
            repeat(100) { test.runOnMainSync { found=activity.window.decorView.findViewWithTag(tag) }; if(found!=null) return found!!; Thread.sleep(30) }
            error("Missing control: $tag")
        }
        fun click(tag: String) { val view=waitFor(tag); test.runOnMainSync { check(view.performClick()) }; test.waitForIdleSync() }
        try {
            click("appearance-hair-long")
            click("appearance-face-oval")
            click("character-tab-3")
            fun dialog()=CharacterActivity::class.java.getDeclaredField("colorDialog").apply {isAccessible=true}.get(activity) as android.app.AlertDialog
            click("color-hair")
            test.runOnMainSync {
                val inputs=ArrayList<EditText>()
                fun collect(v: View) {if(v is EditText)inputs.add(v);if(v is android.view.ViewGroup)for(i in 0 until v.childCount)collect(v.getChildAt(i))}
                collect(dialog().window!!.decorView)
                inputs[1].setText("999")
                check(!dialog().getButton(-1).isEnabled)
            }
            val monitor=test.addMonitor(CharacterActivity::class.java.name,null,false)
            test.runOnMainSync {activity.recreate()}
            activity=test.waitForMonitorWithTimeout(monitor,5000) as? CharacterActivity ?: error("Recreation failed")
            test.removeMonitor(monitor);test.waitForIdleSync()
            test.runOnMainSync {
                check(!dialog().getButton(-1).isEnabled) {"Invalid RGB was replaced on recreation"}
                dialog().getButton(-2).performClick()
            }
            val name=waitFor("character-name") as EditText
            test.runOnMainSync { name.setText("Edited character") }
            click("character-save")
            var saved=store.find(original.id)
            repeat(100) { if(saved?.name!="Edited character") { Thread.sleep(30); saved=store.find(original.id) } }
            check(saved?.name=="Edited character")
            check(saved!!.appearance.hair=="long" && saved!!.appearance.face=="oval")
            check(saved!!.appearance.eyes==original.appearance.eyes && saved!!.colors==original.colors && saved!!.outfit==original.outfit)
            val draft=original.copy(name="Unfinished")
            store.saveDraft(draft)
            check(store.drafts().any {it.id==original.id && it.name=="Unfinished"})
            store.rename(original.id,"Renamed")
            check(store.draft(original.id)!!.copy(name=draft.name)==draft) {"Rename destroyed unfinished work"}
            store.save(saved!!)
            val sceneActivity=test.startActivitySync(Intent(test.targetContext,ScreenEditorActivity::class.java)
                .putExtra("characterId",original.id).putExtra("scenePurpose","WALLPAPER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ScreenEditorActivity
            var composed: EditorDraft?=null
            val field=ScreenEditorActivity::class.java.getDeclaredField("editor").apply {isAccessible=true}
            try {
                repeat(100) {if(composed?.scene?.character==null){test.runOnMainSync {composed=(field.get(sceneActivity) as ScreenEditorView).currentDraft()};Thread.sleep(30)}}
                check(composed?.scene?.character?.definition==saved) {"Saved character not attached to wallpaper"}
                test.runOnMainSync {ScreenEditorActivity::class.java.getDeclaredMethod("preview").apply {isAccessible=true}.invoke(sceneActivity)}
                Thread.sleep(7800)
                var showing=false
                test.runOnMainSync {showing=(ScreenEditorActivity::class.java.getDeclaredField("previewDialog").apply {isAccessible=true}.get(sceneActivity) as? android.app.Dialog)?.isShowing==true}
                check(showing) {"Wallpaper preview inherited charging timeout"}
            } finally {
                test.runOnMainSync {sceneActivity.finish()}
                test.waitForIdleSync()
                composed?.let {MediaLibrary.get(test.targetContext).discardEditorDraft(it.key)}
            }
            val preferences=test.targetContext.getSharedPreferences("magic_circle",0)
            val oldLanguage=preferences.getString("language",null)
            try {
                for(language in listOf("ko","ja","en")) {
                    test.runOnMainSync {activity.finish();WebViews.selectLanguage(test.targetContext,language)}
                    activity=test.startActivitySync(Intent(test.targetContext,CharacterActivity::class.java)
                        .putExtra("characterId",original.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CharacterActivity
                    click("character-tab-1")
                    val height=waitFor("body-heightCm") as EditText
                    test.runOnMainSync {height.setText("180")}
                    click("character-tab-2")
                    for(slot in OutfitSlot.entries)click("outfit-${slot.name}-${CharacterRules.outfitIds.getValue(slot).last()}")
                    click("character-tab-4")
                    val preview=CharacterActivity::class.java.getDeclaredField("preview").apply {isAccessible=true}
                    test.runOnMainSync {(preview.get(activity) as CharacterPreviewView).apply {restoreView(2f,12f,15f);setActive(false);setActive(true);resetView()}}
                    check(store.find(original.id)==saved) {"Uncommitted UI edits changed saved character"}
                }
            } finally {preferences.edit().apply {if(oldLanguage==null)remove("language")else putString("language",oldLanguage)}.commit()}
        } finally { test.runOnMainSync { activity.finish() }; store.delete(original.id) }
    }
}
