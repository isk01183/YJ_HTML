package com.yj.magiccircle;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Locale;
import java.util.Collections;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import android.view.ViewGroup;

final class WebViews {
    private static final String PREFERENCES = "magic_circle";
    private static final String LANGUAGE = "language";
    private static final Object DESTROYED = new Object();

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
        view.setWebViewClient(new LocalClient(context) {
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
        return MediaLibrary.get(context).selected();
    }

    static String selectedLanguage(Context context) {
        return LanguageSelection.resolve(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(LANGUAGE, null), Locale.getDefault().getLanguage());
    }

    static void selectLanguage(Context context, String language) {
        if (!LanguageSelection.isValid(language)) return;
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(LANGUAGE, language).apply();
    }

    static void loadMagicCircle(WebView view) {
        loadMagicCircle(view, selectedTheme(view.getContext()));
    }

    static void loadMagicCircle(WebView view, String id) {
        MediaLibrary library = MediaLibrary.get(view.getContext());
        if (!library.available(id)) return;
        String theme = id;
        MediaLibrary.Item media = library.find(theme);
        Intent battery = view.getContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percent = level >= 0 && scale > 0 && level <= scale
                ? Math.round(level * 100f / scale) : -1;
        String temperature = battery != null && battery.hasExtra(BatteryManager.EXTRA_TEMPERATURE)
                ? Integer.toString(battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)) : "";
        String page = media != null ? "media_circle.html" : ThemeSelection.page(theme);
        Uri uri = Uri.parse("file:///android_asset/" + page).buildUpon()
                .appendQueryParameter("theme", theme)
                .appendQueryParameter("media", media == null ? "" : media.id)
                .appendQueryParameter("mime", media == null ? "" : media.mime)
                .appendQueryParameter("lang", selectedLanguage(view.getContext()))
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
        startMagicCircle(view, 7000L, callback);
    }

    static void startMagicCircle(WebView view, long remainingMs, android.webkit.ValueCallback<String> callback) {
        long duration = Math.max(0L, Math.min(7000L, remainingMs));
        view.evaluateJavascript("typeof window.startChargingAnimation==='function' && "
                + "(window.startChargingAnimation(" + duration + "),document.documentElement.classList.contains('running'))", callback);
    }

    static void destroy(WebView view) {
        if (view.getTag() == DESTROYED) return;
        view.setTag(DESTROYED);
        view.setWebViewClient(new WebViewClient());
        view.stopLoading();
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        view.destroy();
    }

    static class LocalClient extends WebViewClient {
        private final MediaLibrary library;
        private final android.content.res.AssetManager assets;

        LocalClient(Context context) {
            library = MediaLibrary.get(context);
            assets = context.getAssets();
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return intercept(request.getUrl());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return intercept(Uri.parse(url));
        }

        private WebResourceResponse intercept(Uri uri) {
            String path = uri.getPath();
            if ("file".equals(uri.getScheme()) && (uri.getAuthority() == null || uri.getAuthority().isEmpty())
                    && path != null && path.startsWith("/android_asset/") && !path.contains("..")) return null;
            String collectionAsset = CollectionCatalog.assetPath(uri.toString());
            if (collectionAsset != null) try {
                InputStream packed = assets.open(collectionAsset);
                try {
                    java.util.Map<String, String> headers = new java.util.HashMap<>();
                    headers.put("Cache-Control", "no-store");
                    headers.put("X-Content-Type-Options", "nosniff");
                    headers.put("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox");
                    return new WebResourceResponse("image/svg+xml", "UTF-8", 200, "OK",
                            headers, new GZIPInputStream(packed));
                } catch (IOException error) { packed.close(); }
            } catch (IOException ignored) { /* Missing or invalid packaged SVG is never a network fallback. */ }
            String id = MediaValidation.resourceId(uri.toString());
            if (id != null) {
                boolean thumb = "thumb=1".equals(uri.getEncodedQuery());
                MediaLibrary.Item item = library.find(id);
                if (item != null) try {
                    java.util.Map<String, String> headers = new java.util.HashMap<>();
                    headers.put("Cache-Control", "no-store");
                    headers.put("X-Content-Type-Options", "nosniff");
                    return new WebResourceResponse(thumb ? "image/png" : item.mime, null, 200, "OK",
                            headers, library.open(id, thumb));
                } catch (IOException ignored) { /* Missing private file is never a network fallback. */ }
            }
            return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                    Collections.singletonMap("Cache-Control", "no-store"), new ByteArrayInputStream(new byte[0]));
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            destroy(view);
            return true;
        }
    }
}
