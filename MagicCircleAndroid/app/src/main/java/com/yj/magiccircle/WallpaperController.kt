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
    private var dialog: android.app.Dialog? = null
    @Volatile private var generation = 0L
    @Volatile private var closed = false
    private var pendingTheme: String? = null
    private var pendingTarget: String? = null
    private var previousComponent: String? = null
    private var pendingComponent: String? = null
    private var vrmRequest: String?=null
    private var vrmLaunch: String?=null
    private var recoveryOffered=false
    var beforeVrmLaunch: (()->Unit)?=null

    fun show(theme: String, target: String) {
        if (closed || offerPendingVrm() || pendingTheme != null) return
        if (!valid(theme, target)) { message(R.string.wallpaper_unavailable); return }
        pause()
        val scene=MediaLibrary.get(activity).scene(theme)
        if(scene?.vrm!=null) {
            try {dialog=VrmSceneDialog(activity,scene,onApply={prepareVrm(theme,target,scene,null)}).also {it.show()}}
            catch(_: Exception){message(R.string.wallpaper_error)}
            return
        }
        val imported = MediaLibrary.get(activity).find(theme)
        if (imported != null && imported.mime != "image/gif") { previewStill(theme, target); return }
        dialog = AlertDialog.Builder(activity)
            .setTitle(title(theme, target))
            .setItems(arrayOf(text(R.string.wallpaper_still), text(R.string.wallpaper_live))) { _, which ->
                if (which == 0) previewStill(theme, target) else confirmLive(theme, target)
            }.setNegativeButton(text(R.string.library_cancel), null).show()
    }

    fun applyVrm(entry: VrmEntry) {
        if(closed || offerPendingVrm() || pendingTheme!=null)return
        prepareVrm("vrm-${entry.id}","home",null,entry)
    }
    fun resume() {if(!closed && !recoveryOffered){recoveryOffered=true;offerPendingVrm()}}
    private fun w(ko: String,ja: String,en: String)=when(WebViews.selectedLanguage(activity)){"ja"->ja;"en"->en;else->ko}
    private fun notice(value: String){if(alive())Toast.makeText(activity,value,Toast.LENGTH_LONG).show()}
    private fun offerPendingVrm(): Boolean {
        val p=try {VrmWallpaperStore.pendingApplication(activity)}catch(_: Exception){message(R.string.wallpaper_error);return true} ?: return false
        if(VrmWallpaperStore.isPreparing(activity,p.request)) {
            notice(w("이전 배경화면을 준비 중입니다. 잠시 후 다시 시도하세요.","壁紙を準備中です。しばらくお待ちください。","The previous wallpaper is still being prepared. Please wait."));return true
        }
        dialog?.dismiss()
        val builder=AlertDialog.Builder(activity).setTitle(w("이전 배경화면 작업","前の壁紙設定","Previous wallpaper request"))
            .setNegativeButton(text(R.string.library_cancel),null)
        if(p.generation==null)builder.setMessage(w("준비가 중단되었습니다. 이전 배경은 유지됩니다. 이 작업을 정리할까요?","準備が中断しました。既存の壁紙を保ったまま終了しますか？","Preparation stopped. Keep the existing wallpaper and clear this request?"))
            .setPositiveButton(w("중단된 작업 정리","中断した処理を終了","Clear interrupted request")){_,_->
                runCatching {VrmWallpaperStore.finishApplication(activity,p.request)}.onSuccess {clearPending()}.onFailure {message(R.string.wallpaper_error)}
            }
        else builder.setMessage(w("준비된 배경화면으로 시스템 미리보기를 다시 엽니다. 새 작품을 덮어쓰지 않습니다.","準備済みの壁紙のプレビューを再開します。","Reopen the prepared wallpaper in system preview without replacing its contents."))
            .setPositiveButton(w("이전 적용 계속","前の設定を続ける","Continue previous application")){_,_->
                val token=++generation
                worker.execute {
                    val result=runCatching {VrmWallpaperStore.snapshot(activity,p.slot!!).also {check(it.generation==p.generation)}}
                    main.post {if(alive() && token==generation)result.onSuccess {snapshot->
                        launchVrm(p.request,p.slot!!,snapshot.scene?.id ?: "vrm-${snapshot.modelId}",pendingTarget ?: "home")
                    }.onFailure {message(R.string.wallpaper_error)}}
                }
            }
        dialog=builder.show();return true
    }
    private fun prepareVrm(theme: String,target: String,scene: ScreenScene?,entry: VrmEntry?) {
        if(closed)return
        try {if(!canSet()){message(R.string.wallpaper_denied);return}}catch(_: Exception){message(R.string.wallpaper_denied);return}
        val request=try {VrmWallpaperStore.beginApplication(activity)}catch(_: Exception){offerPendingVrm();return}
        vrmRequest=request;pendingTheme=theme;pendingTarget=target
        val token=++generation;message(R.string.wallpaper_preparing)
        worker.execute {
            val result=runCatching {
                check(!closed && generation==token) {"Preparation interrupted"}
                if(scene!=null)VrmWallpaperStore.stage(activity,request,scene)
                else VrmWallpaperStore.stage(activity,request,entry!!.id,entry.placement)
            }
            main.post {if(alive() && token==generation)result.onSuccess {launchVrm(request,it,theme,target)}
                .onFailure {clearPending();message(R.string.wallpaper_error);offerPendingVrm()}}
        }
    }
    private fun launchVrm(request: String,slot: String,theme: String,target: String) {
        try {
            vrmRequest=request;previousComponent=actualTheme(target);pendingTheme=theme;pendingTarget=target;pendingComponent=slot
            vrmLaunch=VrmWallpaperStore.markLaunched(activity,request)
            beforeVrmLaunch?.invoke()
            activity.startActivityForResult(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,VrmWallpaperStore.component(activity,slot)),LIVE_REQUEST)
        } catch(_: Exception){finishVrm();clearPending();message(R.string.wallpaper_picker_error)}
    }
    private fun finishVrm(): Boolean {
        val request=vrmRequest ?: return true
        return runCatching {VrmWallpaperStore.finishApplication(activity,request,vrmLaunch)}.getOrDefault(false)
    }

    private fun valid(theme: String, target: String) = WallpaperPolicy.allowedTheme(theme) &&
        target in setOf("home", "lock") && MediaLibrary.get(activity).available(theme) &&
        !MediaLibrary.get(activity).editorOnly(theme) &&
        (!SceneRules.isSceneId(theme) || MediaLibrary.get(activity).scene(theme)?.purpose==ScenePurpose.WALLPAPER)

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
                WallpaperArtwork(theme, activity.applicationContext).use { renderer ->
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
            catch (_: IOException) { report(token, R.string.wallpaper_error) }
            catch (_: RuntimeException) { report(token, R.string.wallpaper_error) }
            finally { bitmap?.recycle() }
        }
    }

    private fun showStillPreview(theme: String, target: String, bitmap: Bitmap) {
        if (!valid(theme, target)) { bitmap.recycle(); message(R.string.wallpaper_unavailable); return }
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
        if (MediaValidation.isId(theme) || SceneRules.isSceneId(theme)) {
            val token = ++generation
            message(R.string.wallpaper_preparing)
            worker.execute {
                try {
                    if (closed || generation != token) return@execute
                    val key = UploadedWallpaperStore.stage(activity.applicationContext, theme)
                    main.post { if (alive() && generation == token) launchLive(theme, target, key) }
                } catch (_: IOException) { report(token, R.string.wallpaper_error) }
                catch (_: RuntimeException) { report(token, R.string.wallpaper_error) }
            }
        } else launchLive(theme, target, theme.removePrefix("ref-"))
    }

    private fun launchLive(theme: String, target: String, key: String) {
        if (!valid(theme, target)) { message(R.string.wallpaper_unavailable); return }
        try {
            if (!canSet()) { message(R.string.wallpaper_denied); return }
            previousComponent = actualTheme(target)
            pendingTheme = theme
            pendingTarget = target
            pendingComponent = key
            val component = if (MediaValidation.isId(theme) || SceneRules.isSceneId(theme)) UploadedWallpaperStore.component(activity, key)
                else ComponentName(activity,
                if (theme == "ref-W03") W03WallpaperService::class.java else R01WallpaperService::class.java)
            activity.startActivityForResult(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component), LIVE_REQUEST)
        } catch (_: ActivityNotFoundException) { clearPending(); message(R.string.wallpaper_picker_error) }
        catch (_: SecurityException) { clearPending(); message(R.string.wallpaper_denied) }
        catch (_: RuntimeException) { clearPending(); message(R.string.wallpaper_error) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != LIVE_REQUEST) return false
        val theme = pendingTheme
        val target = pendingTarget
        val before = previousComponent
        val component = pendingComponent
        val accepted=finishVrm()
        clearPending()
        if(!accepted){message(R.string.wallpaper_unconfirmed);return true}
        if (theme == null || target == null || component == null) { message(R.string.wallpaper_unconfirmed); return true }
        val outcome = WallpaperPolicy.liveResult(component, actualTheme(target), before,
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
            else -> UploadedWallpaperStore.key(activity, info?.component) ?: VrmWallpaperStore.key(activity,info?.component)
        }
    } catch (_: RuntimeException) { null }

    fun saveState(state: Bundle) {
        pendingTheme?.let { state.putString("wallpaper.theme", it) }
        pendingTarget?.let { state.putString("wallpaper.target", it) }
        previousComponent?.let { state.putString("wallpaper.before", it) }
        pendingComponent?.let { state.putString("wallpaper.component", it) }
        vrmRequest?.let {state.putString("wallpaper.vrmRequest",it)}
        vrmLaunch?.let {state.putString("wallpaper.vrmLaunch",it)}
    }

    fun restoreState(state: Bundle?) {
        val theme = state?.getString("wallpaper.theme") ?: return
        val target = state.getString("wallpaper.target") ?: return
        if ((!WallpaperPolicy.allowedTheme(theme) && !theme.matches(Regex("vrm-[a-f0-9]{64}"))) || target !in setOf("home", "lock")) return
        pendingTheme = theme
        pendingTarget = target
        previousComponent = state.getString("wallpaper.before")
        pendingComponent = state.getString("wallpaper.component")
        vrmRequest=state.getString("wallpaper.vrmRequest")?.takeIf {MediaValidation.isId(it)}
        vrmLaunch=state.getString("wallpaper.vrmLaunch")?.takeIf {MediaValidation.isId(it)}
    }

    fun pause() { generation++; dialog?.dismiss(); dialog = null }
    fun close() { closed = true; pause(); worker.shutdown() }
    private fun clearPending() { pendingTheme = null; pendingTarget = null; previousComponent = null; pendingComponent = null;vrmRequest=null;vrmLaunch=null }
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
    private fun title(theme: String, target: String) = (MediaLibrary.get(activity).scene(theme)?.name ?: MediaLibrary.get(activity).find(theme)?.name ?: theme.removePrefix("ref-")) + " · " +
        text(if (target == "lock") R.string.wallpaper_lock else R.string.wallpaper_home)
    private fun message(resource: Int) { if (alive()) Toast.makeText(activity, text(resource), Toast.LENGTH_LONG).show() }
    companion object { const val LIVE_REQUEST = 31 }
}
