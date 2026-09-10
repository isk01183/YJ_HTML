package com.yj.magiccircle;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.webkit.WebView;

public final class ChargingAccessibilityService extends AccessibilityService {
    private static final String TAG = "MagicCircleCharging";
    private WindowManager windows;
    private WebView overlay;
    private boolean receiverRegistered;

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) applyCharging(true);
            if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) applyCharging(false);
        }
    };

    @Override
    protected void onServiceConnected() {
        windows = getSystemService(WindowManager.class);
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(powerReceiver, filter, RECEIVER_EXPORTED);
            } else {
                registerReceiver(powerReceiver, filter);
            }
            receiverRegistered = true;
        }
        applyCharging(isCharging());
    }

    private boolean isCharging() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    private void applyCharging(boolean charging) {
        switch (ChargingTransition.next(overlay != null, charging)) {
            case SHOW -> showOverlay();
            case HIDE -> hideOverlay();
            case NONE -> { }
        }
    }

    private void showOverlay() {
        WebView view = WebViews.magicCircle(this);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windows.addView(view, params);
            overlay = view;
        } catch (RuntimeException error) {
            view.destroy();
            Log.e(TAG, "Unable to show charging overlay", error);
        }
    }

    private void hideOverlay() {
        if (overlay == null) return;
        try {
            windows.removeView(overlay);
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to remove charging overlay", error);
        }
        overlay.destroy();
        overlay = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        hideOverlay();
        if (receiverRegistered) unregisterReceiver(powerReceiver);
        receiverRegistered = false;
        super.onDestroy();
    }
}
