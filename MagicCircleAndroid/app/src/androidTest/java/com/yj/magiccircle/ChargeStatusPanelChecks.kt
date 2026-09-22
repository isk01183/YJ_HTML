package com.yj.magiccircle

import android.graphics.Bitmap
import android.graphics.Canvas

object ChargeStatusPanelChecks {
    fun run() {
        val renderer = ChargeStatusPanelRenderer()
        renderer.javaClass.getDeclaredField("batteryLayout").apply { isAccessible = true }.set(renderer, null)
        val bitmap = Bitmap.createBitmap(864, 1536, Bitmap.Config.ARGB_8888)
        try {
            renderer.draw(Canvas(bitmap))
        } finally {
            bitmap.recycle()
        }
    }
}
