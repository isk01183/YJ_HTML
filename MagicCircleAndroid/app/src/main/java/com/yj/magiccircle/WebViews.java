package com.yj.magiccircle;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.IOException;

final class WebViews {
    private WebViews() {}

    @SuppressLint("SetJavaScriptEnabled") // Required by the bundled animation; network loads remain blocked.
    static WebView magicCircle(Context context) {
        WebView view = new WebView(context);
        view.setBackgroundColor(0xFF000000);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(true);
        settings.setBlockNetworkLoads(true);
        view.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView webView, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.endsWith("/three.min.js")) return asset(context, "js/three.min.js");
                if (url.endsWith("/OrbitControls.js")) return asset(context, "js/OrbitControls.js");
                if (url.endsWith("/CSS2DRenderer.js")) return asset(context, "js/CSS2DRenderer.js");
                return super.shouldInterceptRequest(webView, request);
            }
        });
        view.loadUrl("file:///android_asset/magic_circle.html");
        return view;
    }

    private static WebResourceResponse asset(Context context, String path) {
        try {
            return new WebResourceResponse("application/javascript", "UTF-8", context.getAssets().open(path));
        } catch (IOException e) {
            return new WebResourceResponse("text/plain", "UTF-8", null);
        }
    }
}
