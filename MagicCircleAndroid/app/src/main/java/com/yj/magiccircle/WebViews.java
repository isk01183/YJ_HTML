package com.yj.magiccircle;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

final class WebViews {
    private WebViews() {}

    @SuppressLint("SetJavaScriptEnabled") // Only the bundled offline page runs scripts.
    static WebView magicCircle(Context context) {
        WebView view = new WebView(context);
        view.setBackgroundColor(0x00000000);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(true);
        settings.setBlockNetworkLoads(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setOffscreenPreRaster(true);
        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView current, String url) {
                startMagicCircle(current, null);
            }
        });
        return view;
    }

    static void loadMagicCircle(WebView view) {
        view.loadUrl("file:///android_asset/magic_circle.html?run=" + System.nanoTime());
    }

    static void startMagicCircle(WebView view, android.webkit.ValueCallback<String> callback) {
        view.evaluateJavascript("window.startChargingAnimation()", callback);
    }
}
