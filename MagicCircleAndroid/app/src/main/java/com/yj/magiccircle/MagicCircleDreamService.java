package com.yj.magiccircle;

import android.service.dreams.DreamService;

public final class MagicCircleDreamService extends DreamService {
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        setContentView(WebViews.magicCircle(this));
    }
}
