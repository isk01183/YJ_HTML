package com.yj.magiccircle

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors

/** User-initiated wallpaper flow; independent of the charging selection and live engines. */
class WallpaperController(private val activity: Activity) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val manager = WallpaperManager.getInstance(activity)
    private var dialog: AlertDialog? = null
    @Volatile private var generation = 0L
    @Volatile private var closed = false
    private var pendingTheme: String? = null
    private var pendingTarget: String? = null
    private var previousComponent: String? = null

    fun show(theme: String, target: String) {
        if (closed || pendingTheme != null) return
        if (!valid(theme, target)) { message(R.string.wallpaper_unavailable); return }
        pause()
        dialog = AlertDialog.Builder(activity)
            .setTitle(title(theme, target))
            .setItems(arrayOf(text(R.string.wallpaper_still), text(R.string.wallpaper_live))) { _, which ->
                if (which == 0) previewStill(theme, target) else confirmLive(theme, target)
            }.setNegativeButton(text(R.string.library_cancel), null).show()
    }

    private fun valid(theme: String, target: String) = WallpaperPolicy.allowedTheme(theme) &&
        target in setOf("home", "lock") && MediaLibrary.get(activity).available(theme)

    private fun canSet(): Boolean = manager.isWallpaperSupported &&
        (Build.VERSION.SDK_INT < 24 || manager.isSetWallpaperAllowed)

    @Suppress("DEPRECATION")
    private fun viewport(): Pair<Int, Int> {
        val size = if (Build.VERSION.SDK_INT >= 30) {
            val bounds = activity.windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val point = Point()
            activity.windowManager.defaultDisplay.getRealSize(point)
            point.x to point.y
        }
        return WallpaperPolicy.bitmapSize(size.first, size.second)
    }

    private fun previewStill(theme: String, target: String) {
        if (!valid(theme, target)) { message(R.string.wallpaper_unavailable); return }
        if (!WallpaperPolicy.supportsStill(Build.VERSION.SDK_INT, target)) {
            message(R.string.wallpaper_lock_unsupported); return
        }
        try { if (!canSet()) { message(R.string.wallpaper_denied); return } }
        catch (_: SecurityException) { message(R.string.wallpaper_denied); return }
        val (width, height) = try { viewport() }
            catch (_: RuntimeException) { message(R.string.wallpaper_error); return }
        val token = ++generation
        message(R.string.wallpaper_preparing)
        worker.execute {
            var bitmap: Bitmap? = null
            try {
                if (closed || generation != token) return@execute
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                WallpaperArtwork(theme).use { renderer ->
                    renderer.prepare(width, height)
                    renderer.draw(Canvas(bitmap), 0, false)
                }
                val ready = bitmap
                bitmap = null
                main.post {
                    if (alive() && generation == token) showStillPreview(theme, target, ready)
                    else ready.recycle()
                }
            } catch (_: OutOfMemoryError) { report(token, R.string.wallpaper_memory_error) }
            catch (_: RuntimeException) { report(token, R.string.wallpaper_error) }
            finally { bitmap?.recycle() }
        }
    }

    private fun showStillPreview(theme: String, target: String, bitmap: Bitmap) {
        var transferred = false
        val image = ImageView(activity).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = title(theme, target)
            maxHeight = (activity.resources.displayMetrics.heightPixels * .6f).toInt()
        }
        dialog = AlertDialog.Builder(activity).setTitle(title(theme, target))
            .setMessage(text(R.string.wallpaper_still_confirm))
            .setView(image)
            .setNegativeButton(text(R.string.library_cancel), null)
            .setPositiveButton(text(R.string.wallpaper_apply)) { _, _ ->
                image.setImageDrawable(null)
                transferred = true
                applyStill(theme, target, bitmap)
            }.create().also { preview ->
                preview.setOnDismissListener {
                    image.setImageDrawable(null)
                    if (!transferred) bitmap.recycle()
                    if (dialog === preview) dialog = null
                }
                preview.show()
            }
    }

    private fun applyStill(theme: String, target: String, bitmap: Bitmap) {
        val token = generation
        worker.execute {
            try {
                if (closed || generation != token) return@execute
                if (!valid(theme, target) || !WallpaperPolicy.supportsStill(Build.VERSION.SDK_INT, target)) {
                    report(token, R.string.wallpaper_unavailable); return@execute
                }
                if (!canSet()) { report(token, R.string.wallpaper_denied); return@execute }
                if (Build.VERSION.SDK_INT >= 24) {
                    if (manager.setBitmap(bitmap, null, false, flag(target)) <= 0) throw IOException("Wallpaper rejected")
                } else manager.setBitmap(bitmap)
                remember(theme, target, "still")
                report(token, R.string.wallpaper_applied)
            } catch (_: SecurityException) { report(token, R.string.wallpaper_denied) }
            catch (_: IOException) { report(token, R.string.wallpaper_error) }
            catch (_: OutOfMemoryError) { report(token, R.string.wallpaper_memory_error) }
            catch (_: RuntimeException) { report(token, R.string.wallpaper_error) }
            finally { bitmap.recycle() }
        }
    }

    private fun confirmLive(theme: String, target: String) {
        dialog = AlertDialog.Builder(activity).setTitle(title(theme, target))
            .setMessage(text(if (target == "lock") R.string.wallpaper_live_lock_hint else R.string.wallpaper_live_home_hint))
            .setNegativeButton(text(R.string.library_cancel), null)
            .setNeutralButton(text(R.string.wallpaper_still)) { _, _ -> previewStill(theme, target) }
            .setPositiveButton(text(R.string.wallpaper_system_preview)) { _, _ -> openLive(theme, target) }.show()
    }

    private fun openLive(theme: String, target: String) {
        if (!valid(theme, target)) { message(R.string.wallpaper_unavailable); return }
        try {
            if (!canSet()) { message(R.string.wallpaper_denied); return }
            previousComponent = actualTheme(target)
            pendingTheme = theme
            pendingTarget = target
            val component = ComponentName(activity,
                if (theme == "ref-W03") W03WallpaperService::class.java else R01WallpaperService::class.java)
            activity.startActivityForResult(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component), LIVE_REQUEST)
        } catch (_: ActivityNotFoundException) { clearPending(); message(R.string.wallpaper_picker_error) }
        catch (_: SecurityException) { clearPending(); message(R.string.wallpaper_denied) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != LIVE_REQUEST) return false
        val theme = pendingTheme
        val target = pendingTarget
        val before = previousComponent
        clearPending()
        if (theme == null || target == null) { message(R.string.wallpaper_unconfirmed); return true }
        val outcome = WallpaperPolicy.liveResult(theme.removePrefix("ref-"), actualTheme(target), before,
            resultCode == Activity.RESULT_OK)
        when (outcome) {
            "confirmed" -> { remember(theme, target, "live"); message(R.string.wallpaper_applied) }
            "retained" -> message(R.string.wallpaper_retained)
            else -> message(R.string.wallpaper_unconfirmed)
        }
        // Returning from the system picker never applies a still image automatically.
        return true
    }

    private fun actualTheme(target: String): String? = try {
        val info = if (Build.VERSION.SDK_INT >= 34) manager.getWallpaperInfo(flag(target))
            else if (target == "home") manager.wallpaperInfo else null
        when (info?.component) {
            ComponentName(activity, W03WallpaperService::class.java) -> "W03"
            ComponentName(activity, R01WallpaperService::class.java) -> "R01"
            else -> null
        }
    } catch (_: RuntimeException) { null }

    fun saveState(state: Bundle) {
        pendingTheme?.let { state.putString("wallpaper.theme", it) }
        pendingTarget?.let { state.putString("wallpaper.target", it) }
        previousComponent?.let { state.putString("wallpaper.before", it) }
    }

    fun restoreState(state: Bundle?) {
        val theme = state?.getString("wallpaper.theme") ?: return
        val target = state.getString("wallpaper.target") ?: return
        if (!WallpaperPolicy.allowedTheme(theme) || target !in setOf("home", "lock")) return
        pendingTheme = theme
        pendingTarget = target
        previousComponent = state.getString("wallpaper.before")?.takeIf { it == "W03" || it == "R01" }
    }

    fun pause() { generation++; dialog?.dismiss(); dialog = null }
    fun close() { closed = true; pause(); worker.shutdown() }
    private fun clearPending() { pendingTheme = null; pendingTarget = null; previousComponent = null }
    private fun alive() = !closed && !activity.isFinishing && !activity.isDestroyed
    private fun flag(target: String) = if (target == "lock") WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
    private fun remember(theme: String, target: String, mode: String) {
        activity.getSharedPreferences("wallpaper_applications", Activity.MODE_PRIVATE).edit()
            .putString("${target}_theme", theme).putString("${target}_mode", mode).apply()
    }
    private fun report(token: Long, resource: Int) { main.post { if (alive() && token == generation) message(resource) } }
    private fun text(resource: Int): String {
        val config = Configuration(activity.resources.configuration)
        config.setLocale(Locale.forLanguageTag(WebViews.selectedLanguage(activity)))
        return activity.createConfigurationContext(config).getString(resource)
    }
    private fun title(theme: String, target: String) = theme.removePrefix("ref-") + " · " +
        text(if (target == "lock") R.string.wallpaper_lock else R.string.wallpaper_home)
    private fun message(resource: Int) { if (alive()) Toast.makeText(activity, text(resource), Toast.LENGTH_LONG).show() }
    companion object { const val LIVE_REQUEST = 31 }
}
