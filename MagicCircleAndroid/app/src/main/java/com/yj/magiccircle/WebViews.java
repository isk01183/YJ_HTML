package com.yj.magiccircle;

import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;

final class WebViews {
    private WebViews() {}

    static WebView magicCircle(Context context) {
        WebView view = new WebView(context);
        view.setBackgroundColor(0xFF000000);
        WebSettings settings = view.getSettings();
        settings.setDomStorageEnabled(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(true);
        settings.setBlockNetworkLoads(true);
        view.loadUrl("file:///android_asset/magic_circle.html?run=" + System.nanoTime());
        return view;
    }
}
