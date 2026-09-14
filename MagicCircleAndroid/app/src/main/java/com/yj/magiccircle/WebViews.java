package com.yj.magiccircle;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

final class WebViews {
    private static final String PREFERENCES = "magic_circle";
    private static final String SELECTED_STYLE = "selected_style";

    private WebViews() {}

    @SuppressLint("SetJavaScriptEnabled") // Only bundled offline pages run scripts.
    static WebView magicCircle(Context context) {
        WebView view = new WebView(context);
        view.setBackgroundColor(0x00000000);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBlockNetworkLoads(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setOffscreenPreRaster(true);
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView current, String url) {
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView current, WebResourceRequest request) {
                return true;
            }

            @Override
            public void onPageFinished(WebView current, String url) {
                startMagicCircle(current, null);
            }
        });
        return view;
    }

    static String selectedTheme(Context context) {
        return ThemeSelection.normalize(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(SELECTED_STYLE, "classic"));
    }

    static void selectTheme(Context context, String id) {
        if (!ThemeSelection.isValid(id)) return;
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(SELECTED_STYLE, id).apply();
    }

    static void loadMagicCircle(WebView view) {
        loadMagicCircle(view, selectedTheme(view.getContext()));
    }

    static void loadMagicCircle(WebView view, String id) {
        String theme = ThemeSelection.normalize(id);
        Intent battery = view.getContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percent = level >= 0 && scale > 0 && level <= scale
                ? Math.round(level * 100f / scale) : -1;
        String temperature = battery != null && battery.hasExtra(BatteryManager.EXTRA_TEMPERATURE)
                ? Integer.toString(battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)) : "";
        String page = "classic".equals(theme) ? "magic_circle.html" : "theme_circle.html";
        Uri uri = Uri.parse("file:///android_asset/" + page).buildUpon()
                .appendQueryParameter("theme", theme)
                .appendQueryParameter("battery", Integer.toString(percent))
                .appendQueryParameter("temperature", temperature)
                .appendQueryParameter("health", Integer.toString(battery == null
                        ? BatteryManager.BATTERY_HEALTH_UNKNOWN
                        : battery.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)))
                .appendQueryParameter("plugged", Integer.toString(battery == null ? -1
                        : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)))
                .appendQueryParameter("status", Integer.toString(battery == null
                        ? BatteryManager.BATTERY_STATUS_UNKNOWN
                        : battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)))
                .appendQueryParameter("run", Long.toString(System.nanoTime()))
                .build();
        view.loadUrl(uri.toString());
    }

    static void startMagicCircle(WebView view, android.webkit.ValueCallback<String> callback) {
        view.evaluateJavascript("window.startChargingAnimation()", callback);
    }
}
