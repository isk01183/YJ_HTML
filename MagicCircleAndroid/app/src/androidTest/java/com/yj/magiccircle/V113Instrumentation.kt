package com.yj.magiccircle

class V113Instrumentation : android.app.Instrumentation() {
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
            LibraryStorageChecks.run(targetContext)
            SceneStorageChecks.run(targetContext)
            SceneWallpaperChecks.run(targetContext)
            LayeredSceneChecks.run()
            runOnMainSync { ChargeInfoChecks.run(targetContext) }
            runOnMainSync { ScreenEditorChecks.run(targetContext) }
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
