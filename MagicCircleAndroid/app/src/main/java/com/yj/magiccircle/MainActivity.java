package com.yj.magiccircle;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebView;
import android.webkit.RenderProcessGoneDetail;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String GALLERY_URL = "file:///android_asset/gallery.html";
    private static final int IMPORT_DOCUMENT = 20;
    private static final ExecutorService LIBRARY_IO = Executors.newSingleThreadExecutor();
    private static final Handler LIBRARY_UI = new Handler(Looper.getMainLooper());
    private static WeakReference<MainActivity> activeActivity = new WeakReference<>(null);
    private static boolean libraryBusy;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable finishPreview = this::dismissPreview;
    private WebView gallery;
    private Dialog previewDialog;
    private WallpaperController wallpaperController;
    private String galleryScreen = "home";
    private boolean importingWallpaper;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        wallpaperController = new WallpaperController(this);
        wallpaperController.restoreState(state);
        if (state != null) {
            galleryScreen = state.getString("gallery.screen", "home");
            importingWallpaper = state.getBoolean("gallery.importingWallpaper");
        }
        activeActivity = new WeakReference<>(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF05080B);
        fitSystemInsets(getWindow(), root);
        gallery = WebViews.magicCircle(this);
        gallery.setWebViewClient(new WebViews.LocalClient(this) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView current, String url) {
                handleGalleryLink(current, Uri.parse(url));
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView current, WebResourceRequest request) {
                if (request.isForMainFrame()) handleGalleryLink(current, request.getUrl());
                return true;
            }

            @Override
            public void onPageFinished(WebView current, String url) {
                if (current == gallery && GALLERY_URL.equals(url)) updateGalleryState();
            }

            @Override
            public boolean onRenderProcessGone(WebView current, RenderProcessGoneDetail detail) {
                boolean owned = current == gallery;
                if (owned) gallery = null;
                super.onRenderProcessGone(current, detail);
                if (owned && !isFinishing() && !isDestroyed()) recreate();
                return true;
            }
        });
        root.addView(gallery, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::navigateBack);
        gallery.loadUrl(GALLERY_URL);
        MediaLibrary library = MediaLibrary.get(this);
        if (!library.isReadable()) message(R.string.library_load_error);
        else LIBRARY_IO.execute(() -> {
            try { library.retryPendingDeletes(); }
            catch (IOException ignored) { /* Durable pending IDs are retried on the next app opening. */ }
        });
    }

    private void handleGalleryLink(WebView current, Uri uri) {
        if (current != gallery || !GALLERY_URL.equals(current.getUrl())
                || !"magiccircle".equals(uri.getScheme()) || !uri.isHierarchical()
                || uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty())) return;
        String action = uri.getAuthority();
        MediaLibrary library = MediaLibrary.get(this);
        if ("screen".equals(action) && hasExactQuery(uri, "name")) {
            String name = uri.getQueryParameter("name");
            if ("home".equals(name) || "wallpaper".equals(name) || "charging".equals(name)) galleryScreen = name;
            return;
        }
        if ("create".equals(action) && hasExactQuery(uri, "purpose")) {
            String purpose = uri.getQueryParameter("purpose");
            if (!libraryBusy && ("wallpaper".equals(purpose) || "charging".equals(purpose)))
                startActivity(new Intent(this, ScreenEditorActivity.class).putExtra("scenePurpose", purpose.toUpperCase(Locale.ROOT)));
            return;
        }
        if ("edit".equals(action) && hasExactQuery(uri, "theme")) {
            String id = uri.getQueryParameter("theme");
            if (!libraryBusy && (ThemeSelection.isValid(id) || library.find(id) != null || library.scene(id) != null || library.editorDraft(id) != null))
                startActivity(new Intent(this, ScreenEditorActivity.class).putExtra("themeId", id));
            return;
        }
        if ("duration".equals(action) && hasExactQuery(uri, "ms")) {
            String ms = uri.getQueryParameter("ms");
            if (java.util.Arrays.asList("1000", "3000", "5000", "7000").contains(ms))
                changeLibrary(() -> library.setDurationMs(Integer.parseInt(ms)), 0);
            return;
        }
        if ("settings".equals(action) && uri.getQuery() == null) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        if ("language".equals(action)) {
            if (!hasExactQuery(uri, "lang")) return;
            String language = uri.getQueryParameter("lang");
            if (!LanguageSelection.isValid(language)) return;
            WebViews.selectLanguage(this, language);
            updateGalleryState();
            return;
        }
        if ("import".equals(action) && uri.getQuery() == null) {
            if (!libraryBusy) chooseMedia(false);
            return;
        }
        if ("import-wallpaper".equals(action) && uri.getQuery() == null) {
            if (!libraryBusy) chooseMedia(true);
            return;
        }
        if ("wallpaper".equals(action)) {
            if (!hasExactQuery(uri, "theme", "target") || libraryBusy) return;
            String theme = uri.getQueryParameter("theme"), target = uri.getQueryParameter("target");
            if (!WallpaperPolicy.allowedTheme(theme) || !library.available(theme)
                    || (!"home".equals(target) && !"lock".equals(target))) return;
            wallpaperController.show(theme, target);
            return;
        }
        if ("restore".equals(action) && uri.getQuery() == null) {
            changeLibrary(library::restore, 0);
            return;
        }
        if ("enable".equals(action) || "disable".equals(action)) {
            if (!hasExactQuery(uri, "theme")) return;
            String theme = uri.getQueryParameter("theme");
            if (!ThemeSelection.isValid(theme)) return;
            if ("enable".equals(action)) changeLibrary(() -> library.setEnabled(theme, true), 0);
            else if (!libraryBusy) new AlertDialog.Builder(this)
                    .setTitle(localizedString(R.string.library_hide_title))
                    .setMessage(localizedString(R.string.library_hide_message))
                    .setNegativeButton(localizedString(R.string.library_cancel), null)
                    .setPositiveButton(localizedString(R.string.library_hide),
                            (dialog, which) -> changeLibrary(() -> library.setEnabled(theme, false), 0))
                    .show();
            return;
        }
        if ("tab-create".equals(action)) {
            if (!hasExactQuery(uri, "name")) return;
            String name = uri.getQueryParameter("name");
            changeLibrary(() -> library.createTab(name), 0);
            return;
        }
        if ("tab-rename".equals(action)) {
            if (!hasExactQuery(uri, "id", "name")) return;
            String id = uri.getQueryParameter("id"), name = uri.getQueryParameter("name");
            changeLibrary(() -> library.renameTab(id, name), 0);
            return;
        }
        if ("tab-delete".equals(action)) {
            if (!hasExactQuery(uri, "id") || libraryBusy) return;
            String id = uri.getQueryParameter("id");
            new AlertDialog.Builder(this)
                    .setTitle(localizedString(R.string.library_tab_delete_title))
                    .setMessage(localizedString(R.string.library_tab_delete_message))
                    .setNegativeButton(localizedString(R.string.library_cancel), null)
                    .setPositiveButton(localizedString(R.string.library_tab_delete),
                            (dialog, which) -> changeLibrary(() -> library.deleteTab(id), 0))
                    .setOnDismissListener(dialog -> updateGalleryState())
                    .show();
            return;
        }
        if ("tab-member".equals(action)) {
            if (!hasExactQuery(uri, "id", "theme", "member")) return;
            String id = uri.getQueryParameter("id"), theme = uri.getQueryParameter("theme");
            String member = uri.getQueryParameter("member");
            if (!"0".equals(member) && !"1".equals(member)) return;
            changeLibrary(() -> library.setTabMember(id, theme, "1".equals(member)), 0);
            return;
        }
        if (!"select".equals(action) && !"preview".equals(action) && !"delete".equals(action)) return;
        if (!hasExactQuery(uri, "theme")) return;
        String theme = uri.getQueryParameter("theme");
        if (!library.available(theme)) return;
        if ("select".equals(action)) {
            changeLibrary(() -> library.select(theme), 0);
        } else if ("preview".equals(action)) {
            showPreview(theme);
        } else if ("delete".equals(action) && !libraryBusy) {
            boolean builtin = ThemeSelection.isValid(theme);
            new AlertDialog.Builder(this)
                    .setTitle(localizedString(builtin ? R.string.library_hide_title : R.string.library_delete_title))
                    .setMessage(localizedString(builtin ? R.string.library_hide_message : R.string.library_delete_message))
                    .setNegativeButton(localizedString(R.string.library_cancel), null)
                    .setPositiveButton(localizedString(builtin ? R.string.library_hide : R.string.library_delete),
                            (dialog, which) -> changeLibrary(() -> library.remove(theme), 0))
                    .show();
        }
    }

    private static boolean hasExactQuery(Uri uri, String... names) {
        String query = uri.getEncodedQuery();
        if (query == null) return names.length == 0;
        String[] pairs = query.split("&", -1);
        if (pairs.length != names.length) return false;
        for (String pair : pairs) if (pair.isEmpty() || pair.indexOf('=') < 1) return false;
        if (uri.getQueryParameterNames().size() != names.length) return false;
        for (String name : names) {
            if (!uri.getQueryParameterNames().contains(name)
                    || uri.getQueryParameters(name).size() != 1) return false;
        }
        return true;
    }

    private void updateGalleryState() {
        if (gallery == null || !GALLERY_URL.equals(gallery.getUrl())) return;
        try {
            String json = MediaLibrary.get(this).galleryState(isServiceEnabled(), WebViews.selectedLanguage(this))
                    .put("busy", libraryBusy).put("screen", galleryScreen).toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
            gallery.evaluateJavascript("if (typeof window.setGalleryState === 'function') "
                    + "window.setGalleryState(" + json + ")", null);
        } catch (JSONException error) { message(R.string.library_storage_error); }
    }

    private void chooseMedia(boolean forWallpaper) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/gif", "image/png", "image/jpeg"})
                .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            importingWallpaper = forWallpaper;
            libraryBusy = true;
            updateGalleryState();
            startActivityForResult(intent, IMPORT_DOCUMENT);
        } catch (ActivityNotFoundException error) {
            libraryBusy = false;
            updateGalleryState();
            message(R.string.library_picker_error);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (wallpaperController.onActivityResult(requestCode, resultCode, data)) return;
        if (requestCode != IMPORT_DOCUMENT) return;
        libraryBusy = false;
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            MediaLibrary library = MediaLibrary.get(this);
            boolean selectForCharging = !importingWallpaper;
            importingWallpaper = false;
            message(R.string.library_importing);
            changeLibrary(() -> library.importDocument(uri, selectForCharging), R.string.library_imported);
        } else updateGalleryState();
    }

    private interface LibraryChange { void run() throws IOException; }

    private void changeLibrary(LibraryChange change, int successMessage) {
        if (libraryBusy) return;
        libraryBusy = true;
        updateGalleryState();
        LIBRARY_IO.execute(() -> {
            int result = successMessage;
            try { change.run(); }
            catch (MediaValidation.InvalidMedia error) {
                result = "too_large".equals(error.code) ? R.string.library_too_large
                        : "dimensions".equals(error.code) ? R.string.library_dimensions : R.string.library_invalid;
            } catch (MediaLibrary.CleanupPending error) { result = R.string.library_cleanup_pending; }
            catch (IllegalArgumentException error) { result = R.string.library_invalid_change; }
            catch (IOException | RuntimeException error) { result = R.string.library_storage_error; }
            final int messageId = result;
            LIBRARY_UI.post(() -> {
                libraryBusy = false;
                MainActivity current = activeActivity.get();
                if (current == null || current.isDestroyed() || current.isFinishing()) return;
                current.updateGalleryState();
                if (messageId != 0) current.message(messageId);
            });
        });
    }

    private String localizedString(int resource) {
        Configuration localized = new Configuration(getResources().getConfiguration());
        localized.setLocale(new Locale(WebViews.selectedLanguage(this)));
        return createConfigurationContext(localized).getString(resource);
    }

    private void message(int resource) {
        Toast.makeText(this, localizedString(resource), Toast.LENGTH_LONG).show();
    }

    private void showPreview(String theme) {
        dismissPreview();
        Dialog dialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        FrameLayout root = new FrameLayout(this);
        MediaLibrary library = MediaLibrary.get(this);
        ChargingSceneView scene = new ChargingSceneView(this, theme, library.chargeInfo(theme));
        root.addView(scene, new FrameLayout.LayoutParams(-1, -1));
        Button close = new Button(this);
        close.setText(localizedString(R.string.close_preview));
        close.setTextColor(0xFFF1DEC0);
        close.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xCC17212A));
        close.setOnClickListener(v -> dismissPreview());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        closeParams.setMargins(dp(12), dp(12), dp(12), dp(12));
        root.addView(close, closeParams);
        dialog.setContentView(root);
        Runnable timeout = () -> { if (previewDialog == dialog) dismissPreview(); };
        dialog.setOnDismissListener(ignored -> {
            handler.removeCallbacks(timeout);
            if (previewDialog == dialog) previewDialog = null;
            scene.close();
        });
        previewDialog = dialog;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setLayout(-1, -1);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            fitSystemInsets(window, root);
        }
        handler.postDelayed(timeout, 2000);
        scene.prepare(() -> {
            if (previewDialog != dialog) return;
            handler.removeCallbacks(timeout);
            long now = android.os.SystemClock.uptimeMillis();
            long deadline = ChargingTransition.displayDeadline(now, library.durationMs());
            handler.postAtTime(timeout, deadline);
            scene.start(now, deadline);
        }, () -> { if (previewDialog == dialog) dismissPreview(); });
    }

    private void dismissPreview() {
        handler.removeCallbacks(finishPreview);
        if (previewDialog != null) {
            Dialog dialog = previewDialog;
            previewDialog = null;
            dialog.dismiss();
        }
    }

    private void fitSystemInsets(Window window, View root) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            });
            root.requestApplyInsets();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isServiceEnabled() {
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        if (manager == null) return false;
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() == null) continue;
            ServiceInfo service = info.getResolveInfo().serviceInfo;
            if (service != null && getPackageName().equals(service.packageName)
                    && ChargingAccessibilityService.class.getName().equals(service.name)) return true;
        }
        return false;
    }

    private static void destroyWebView(WebView view) {
        WebViews.destroy(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gallery != null) gallery.onResume();
        updateGalleryState();
    }

    @Override
    protected void onPause() {
        wallpaperController.pause();
        dismissPreview();
        if (gallery != null) gallery.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        wallpaperController.close();
        if (activeActivity.get() == this) activeActivity.clear();
        dismissPreview();
        handler.removeCallbacks(finishPreview);
        if (gallery != null) {
            WebView view = gallery;
            gallery = null;
            destroyWebView(view);
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        wallpaperController.saveState(state);
        state.putString("gallery.screen", galleryScreen);
        state.putBoolean("gallery.importingWallpaper", importingWallpaper);
        super.onSaveInstanceState(state);
    }

    private void navigateBack() {
        if (gallery == null) { finish(); return; }
        gallery.evaluateJavascript("typeof window.navigateBack === 'function' && window.navigateBack()",
                handled -> { if (!"true".equals(handled)) finish(); });
    }

    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("GestureBackNavigation") // API 23–32 only; API 33+ uses the registered OnBackInvokedCallback.
    @Override public void onBackPressed() { navigateBack(); }
}
