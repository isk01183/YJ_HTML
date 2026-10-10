package com.yj.magiccircle

class V113Instrumentation : android.app.Instrumentation() {
    private fun checkOnMain(block: () -> Unit) {
        var failure: Throwable?=null
        runOnMainSync {try {block()}catch(error: Throwable){failure=error}}
        failure?.let {throw it}
    }
    private var options: android.os.Bundle?=null
    override fun onCreate(arguments: android.os.Bundle?) {
        super.onCreate(arguments)
        options=arguments
        start()
    }

    override fun onStart() {
        val result = android.os.Bundle()
        try {
            options?.getString("duration")?.let { value ->
                check(android.os.Build.PRODUCT.startsWith("sdk_")) { "Replay setup is emulator-only" }
                val library=MediaLibrary.get(targetContext)
                library.setDurationMs(value.toInt())
                options?.getString("theme")?.let {
                    if(ThemeSelection.isValid(it)) library.setEnabled(it,true)
                    library.select(it)
                    check(library.selected()==it) {"Replay theme was not selected"}
                }
                result.putString("stream","REPLAY_SETUP_OK duration="+library.durationMs()+" theme="+library.selected())
                finish(android.app.Activity.RESULT_OK,result)
                return
            }
            options?.getString("checks")?.let { name ->
                when(name) {
                    "vrm-hair-assembly" -> VrmHairAssemblyChecks.run(this)
                    "vrm-hair-storage" -> VrmHairPartStoreChecks.run(this)
                    "vrm-hair-actual" -> VrmHairAssemblyChecks.actual(this)
                    "vrm-avatar-storage" -> VrmAvatarStorageChecks.run(targetContext)
                    "vrm-avatar-save" -> VrmAvatarSaveChecks.run(this,checkNotNull(options?.getString("mode")))
                    "vrm-dye" -> VrmDyeChecks.run(this)
                    "vrm-avatar-editor" -> VrmAvatarEditorChecks.run(this)
                    "vrm-avatar-scene" -> VrmAvatarSceneChecks.run(this)
                    "vrm-avatar-scene-reopen" -> VrmAvatarSceneChecks.reopen(this,checkNotNull(options?.getString("fixture")))
                    "vrm-scene" -> VrmSceneChecks.run(this,options?.getString("seconds")?.toIntOrNull() ?: 0)
                    "vrm-scene-screen" -> VrmSceneChecks.screen(this,options?.getString("seconds")?.toIntOrNull() ?: 20)
                    "vrm-scene-live" -> VrmSceneChecks.live(this,options?.getString("seconds")?.toIntOrNull() ?: 0,options?.getString("model")?.toIntOrNull() ?: 0,options?.getString("soak")?.toIntOrNull() ?: 0)
                    "vrm-preview" -> VrmPreviewChecks.run(targetContext)
                    "vrm-wallpaper" -> VrmWallpaperChecks.run(targetContext)
                    "vrm-wallpaper-live" -> VrmWallpaperChecks.live(this,options?.getString("seconds")?.toIntOrNull() ?: 0)
                    "vrm-preview-screen" -> VrmPreviewChecks.screen(this)
                    "vrm-preview-render" -> VrmPreviewChecks.render(this)
                    "vrm-memory" -> VrmMemoryChecks.run(this,options?.getString("model"),options?.getString("alternate"),options?.getString("pixels")?.toLong() ?: 54_067_392L)
                    "vrm-hair" -> VrmHairChecks.run(this,checkNotNull(options?.getString("model")),checkNotNull(options?.getString("alternate")),checkNotNull(options?.getString("pixels")).toLong())
                    "character-storage" -> CharacterStorageChecks.run(targetContext)
                    "character-render" -> CharacterRenderingChecks.run(targetContext)
                    "character-editor" -> CharacterEditorChecks.run(this)
                    "character-scene" -> {CharacterSceneChecks.run(targetContext);checkOnMain {CharacterSceneChecks.gesture(targetContext)}}
                    "media116" -> EditorMediaChecks.run(targetContext)
                    "gesture116" -> checkOnMain { EditorGestureChecks.run(targetContext) }
                    "message116" -> checkOnMain { StageMessageChecks.run(targetContext) }
                    else -> error("Unknown check: $name")
                }
                result.putString("stream", "$name OK")
                finish(android.app.Activity.RESULT_OK,result)
                return
            }
            CharacterStorageChecks.run(targetContext)
            CharacterRenderingChecks.run(targetContext)
            CharacterEditorChecks.run(this)
            LibraryStorageChecks.run(targetContext)
            SceneStorageChecks.run(targetContext)
            SceneWallpaperChecks.run(targetContext)
            CharacterSceneChecks.run(targetContext)
            checkOnMain {CharacterSceneChecks.gesture(targetContext)}
            LayeredSceneChecks.run()
            checkOnMain { ChargeInfoChecks.run(targetContext) }
            checkOnMain { ScreenEditorChecks.run(targetContext) }
            EditorMediaChecks.run(targetContext)
            checkOnMain { EditorGestureChecks.run(targetContext) }
            checkOnMain { StageMessageChecks.run(targetContext) }
            WallpaperChecks.run(targetContext)
            MediaWallpaperChecks.run()
            ChargeStatusPanelChecks.run()
            R01RenderingChecks.run()
            W03RenderingChecks.run()
            result.putString("stream", "V113_CHECKS_OK")
            finish(android.app.Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            android.util.Log.e("MagicCircleChecks", "On-device checks failed", error)
            result.putString("stream", "V113_CHECKS_FAILED: " + error.javaClass.simpleName)
            finish(android.app.Activity.RESULT_CANCELED, result)
        }
    }
}
