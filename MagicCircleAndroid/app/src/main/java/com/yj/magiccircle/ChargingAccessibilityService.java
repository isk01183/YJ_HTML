package com.yj.magiccircle;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.webkit.WebView;

public final class ChargingAccessibilityService extends AccessibilityService {
    private static final String TAG = "MagicCircleCharging";
    private static final long ANIMATION_DURATION_MS = 7_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable finishAnimation = () -> handle(ChargingTransition.Event.FINISH);
    private WindowManager windows;
    private WebView overlay;
    private boolean receiverRegistered;
    private ChargingTransition.State state = ChargingTransition.State.DISCONNECTED;

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) handle(ChargingTransition.Event.CONNECT);
            if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) handle(ChargingTransition.Event.DISCONNECT);
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
        handle(isCharging() ? ChargingTransition.Event.CONNECT : ChargingTransition.Event.DISCONNECT);
    }

    private boolean isCharging() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    private void handle(ChargingTransition.Event event) {
        ChargingTransition.State previous = state;
        state = ChargingTransition.next(state, event);
        if (previous != ChargingTransition.State.PLAYING
                && state == ChargingTransition.State.PLAYING) {
            if (!showOverlay()) state = ChargingTransition.State.COMPLETE;
        } else if (previous == ChargingTransition.State.PLAYING
                && state != ChargingTransition.State.PLAYING) {
            hideOverlay();
        }
    }

    private boolean showOverlay() {
        WebView view = WebViews.magicCircle(this);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windows.addView(view, params);
            overlay = view;
            handler.postDelayed(finishAnimation, ANIMATION_DURATION_MS);
            return true;
        } catch (RuntimeException error) {
            view.destroy();
            Log.e(TAG, "Unable to show charging overlay", error);
            return false;
        }
    }

    private void hideOverlay() {
        handler.removeCallbacks(finishAnimation);
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
        state = ChargingTransition.State.DISCONNECTED;
        if (receiverRegistered) unregisterReceiver(powerReceiver);
        receiverRegistered = false;
        super.onDestroy();
    }
}
