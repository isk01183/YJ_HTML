package com.yj.magiccircle

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder

abstract class MagicWallpaperService : WallpaperService() {
    protected abstract val themeId: String
    override fun onCreateEngine(): Engine = ArtworkEngine(themeId)

    private inner class ArtworkEngine(private val fixedTheme: String) : Engine() {
        private val worker = HandlerThread("Wallpaper-$fixedTheme").apply { start() }
        private val handler = Handler(worker.looper)
        private val gate = Any()
        private var visible = false
        private var surfaceReady = false
        private var destroyed = false
        private var generation = 0L
        private var width = 0
        private var height = 0
        private var pending: Runnable? = null
        // Artwork and its caches are accessed only on this engine's worker.
        private var artwork: WallpaperArtwork? = null
        private var preparedWidth = 0
        private var preparedHeight = 0
        private val started = SystemClock.elapsedRealtime()

        override fun onVisibilityChanged(value: Boolean) {
            synchronized(gate) { visible = value; restart() }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            synchronized(gate) {
                width = w; height = h
                surfaceReady = w > 0 && h > 0
                restart()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            synchronized(gate) { surfaceReady = false; restart() }
            super.onSurfaceDestroyed(holder)
        }

        /** Called under gate: one queued frame, with stale preparations unable to reschedule. */
        private fun restart() {
            generation++
            pending?.let(handler::removeCallbacks)
            pending = null
            if (!WallpaperPolicy.shouldRender(visible, surfaceReady, destroyed)) return
            val token = generation
            val w = width
            val h = height
            val frame = object : Runnable {
                override fun run() {
                    synchronized(gate) {
                        if (!current(token)) return
                        pending = null
                    }
                    try {
                        val renderer = artwork ?: WallpaperArtwork(fixedTheme).also { artwork = it }
                        if (preparedWidth != w || preparedHeight != h) {
                            renderer.prepare(w, h)
                            preparedWidth = w; preparedHeight = h
                        }
                        synchronized(gate) {
                            if (!current(token)) return
                            val canvas = if (Build.VERSION.SDK_INT >= 26) surfaceHolder.lockHardwareCanvas()
                                else surfaceHolder.lockCanvas()
                            if (canvas == null) { stopSurface(); return }
                            try { renderer.draw(canvas, SystemClock.elapsedRealtime() - started, true) }
                            finally { surfaceHolder.unlockCanvasAndPost(canvas) }
                            if (current(token)) {
                                pending = this
                                handler.postDelayed(this, 34L)
                            }
                        }
                    } catch (error: RuntimeException) {
                        failed(token, error)
                    } catch (error: OutOfMemoryError) {
                        failed(token, error)
                    }
                }
            }
            pending = frame
            handler.post(frame)
        }

        private fun current(token: Long) = generation == token &&
            WallpaperPolicy.shouldRender(visible, surfaceReady, destroyed)

        private fun stopSurface() { surfaceReady = false; generation++; pending = null }

        private fun failed(token: Long, error: Throwable) {
            synchronized(gate) { if (generation == token) stopSurface() }
            Log.w("MagicWallpaper", "Rendering stopped; waiting for a new surface event", error)
        }

        override fun onDestroy() {
            synchronized(gate) { destroyed = true; restart() }
            // Terminal close happens once, after any in-flight preparation. Surface recreation
            // uses the still-open renderer; only engine destruction closes it.
            handler.post {
                try { artwork?.close(); artwork = null }
                finally { worker.quitSafely() }
            }
            super.onDestroy()
        }
    }
}

class W03WallpaperService : MagicWallpaperService() { override val themeId = "ref-W03" }
class R01WallpaperService : MagicWallpaperService() { override val themeId = "ref-R01" }
