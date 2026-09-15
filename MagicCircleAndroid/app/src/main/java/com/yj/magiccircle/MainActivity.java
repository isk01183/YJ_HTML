package com.yj.magiccircle;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;

import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String GALLERY_URL = "file:///android_asset/gallery.html";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable finishPreview = this::dismissPreview;
    private WebView gallery;
    private Dialog previewDialog;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF05080B);
        fitSystemInsets(getWindow(), root);
        gallery = WebViews.magicCircle(this);
        gallery.setWebViewClient(new WebViewClient() {
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
        });
        root.addView(gallery, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        gallery.loadUrl(GALLERY_URL);
    }

    private void handleGalleryLink(WebView current, Uri uri) {
        if (current != gallery || !GALLERY_URL.equals(current.getUrl())
                || !"magiccircle".equals(uri.getScheme()) || !uri.isHierarchical()
                || uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty())) return;
        String action = uri.getAuthority();
        if ("settings".equals(action) && uri.getQuery() == null) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        if ("language".equals(action)) {
            if (uri.getQueryParameterNames().size() != 1
                    || uri.getQueryParameters("lang").size() != 1) return;
            String language = uri.getQueryParameter("lang");
            if (!LanguageSelection.isValid(language)) return;
            WebViews.selectLanguage(this, language);
            updateGalleryState();
            return;
        }
        if (uri.getQueryParameterNames().size() != 1
                || uri.getQueryParameters("theme").size() != 1) return;
        String theme = uri.getQueryParameter("theme");
        if (!ThemeSelection.isValid(theme)) return;
        if ("select".equals(action)) {
            WebViews.selectTheme(this, theme);
            updateGalleryState();
        } else if ("preview".equals(action)) {
            showPreview(theme);
        }
    }

    private void updateGalleryState() {
        if (gallery == null || !GALLERY_URL.equals(gallery.getUrl())) return;
        // The theme and language are allowlisted before insertion into JavaScript.
        gallery.evaluateJavascript("if (typeof window.setGalleryState === 'function') "
                + "window.setGalleryState({selected:'" + WebViews.selectedTheme(this)
                + "',enabled:" + isServiceEnabled() + ",language:'"
                + WebViews.selectedLanguage(this) + "'})", null);
    }

    private void showPreview(String theme) {
        dismissPreview();
        Dialog dialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        WebView preview = WebViews.magicCircle(this);
        root.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Button close = new Button(this);
        Configuration localized = new Configuration(getResources().getConfiguration());
        localized.setLocale(new Locale(WebViews.selectedLanguage(this)));
        close.setText(createConfigurationContext(localized).getString(R.string.close_preview));
        close.setTextColor(0xFFF1DEC0);
        close.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xCC17212A));
        close.setOnClickListener(v -> dismissPreview());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        closeParams.setMargins(dp(12), dp(12), dp(12), dp(12));
        root.addView(close, closeParams);
        dialog.setContentView(root);
        dialog.setOnDismissListener(ignored -> {
            if (previewDialog == dialog) {
                handler.removeCallbacks(finishPreview);
                previewDialog = null;
            }
            destroyWebView(preview);
        });
        previewDialog = dialog;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            fitSystemInsets(window, root);
        }
        handler.postDelayed(finishPreview, 7_000L);
        preview.post(() -> {
            if (previewDialog == dialog) WebViews.loadMagicCircle(preview, theme);
        });
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
        view.setWebViewClient(new WebViewClient());
        view.stopLoading();
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        view.destroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gallery != null) gallery.onResume();
        updateGalleryState();
    }

    @Override
    protected void onPause() {
        dismissPreview();
        if (gallery != null) gallery.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        dismissPreview();
        handler.removeCallbacks(finishPreview);
        if (gallery != null) {
            WebView view = gallery;
            gallery = null;
            destroyWebView(view);
        }
        super.onDestroy();
    }
}
