package com.yj.magiccircle

class V113Instrumentation : android.app.Instrumentation() {
    override fun onCreate(arguments: android.os.Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = android.os.Bundle()
        try {
            LibraryStorageChecks.run(targetContext)
            WallpaperChecks.run(targetContext)
            ChargeStatusPanelChecks.run()
            R01RenderingChecks.run()
            W03RenderingChecks.run()
            result.putString("stream", "V113_CHECKS_OK")
            finish(android.app.Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "V113_CHECKS_FAILED: " + error.javaClass.simpleName)
            finish(android.app.Activity.RESULT_CANCELED, result)
        }
    }
}
