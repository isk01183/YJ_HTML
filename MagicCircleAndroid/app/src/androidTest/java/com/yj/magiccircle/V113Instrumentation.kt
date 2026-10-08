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
                    "vrm-preview" -> VrmPreviewChecks.run(targetContext)
                    "vrm-wallpaper" -> VrmWallpaperChecks.run(targetContext)
                    "vrm-preview-screen" -> VrmPreviewChecks.screen(this)
                    "vrm-preview-render" -> VrmPreviewChecks.render(this)
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
