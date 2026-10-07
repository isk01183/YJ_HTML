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
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public final class ChargingAccessibilityService extends AccessibilityService {
    private static final String TAG = "MagicCircleCharging";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private PowerManager power;
    private ChargingSceneView overlay;
    private boolean receiverRegistered;
    private long runId, connectedAt, startedAt;

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) connect();
            else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) hideOverlay("disconnect");
            else if (Intent.ACTION_SCREEN_OFF.equals(action) && power != null && !power.isInteractive())
                hideOverlay("screen-off");
        }
    };
    @Override protected void onServiceConnected() {
        windows = getSystemService(WindowManager.class);
        power = getSystemService(PowerManager.class);
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(powerReceiver, filter, RECEIVER_EXPORTED);
            else registerReceiver(powerReceiver, filter);
            receiverRegistered = true;
        }
        Log.d(TAG, "Service connected");
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0) connect();
    }
    private void connect() {
        hideOverlay("restart");
        final long run = ++runId;
        connectedAt = SystemClock.uptimeMillis();
        startedAt = 0;
        MediaLibrary library = MediaLibrary.get(this);
        String theme = library.selected();
        if (theme.isEmpty()) return;
        final int duration = library.durationMs();
        try {
            ChargingSceneView view = new ChargingSceneView(this, theme, library.chargeInfo(theme));
            overlay = view;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            windows.addView(view, params);
            Runnable timeout = () -> { if (current(run, view)) hideOverlay("prepare-timeout"); };
            handler.postAtTime(timeout, ChargingTransition.prepareDeadline(connectedAt));
            view.prepare(() -> {
                if (!current(run, view)) return;
                handler.removeCallbacks(timeout);
                startedAt = SystemClock.uptimeMillis();
                long deadline = ChargingTransition.displayDeadline(startedAt, duration);
                handler.postAtTime(() -> { if (current(run, view)) hideOverlay("deadline"); }, deadline);
                view.start(startedAt, deadline);
                if (current(run, view)) log("Started duration=" + duration);
            }, () -> { if (current(run, view)) hideOverlay("prepare-or-render-error"); });
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to show charging overlay", error);
            hideOverlay("window-error");
        }
    }
    private boolean current(long run, ChargingSceneView view) {
        return ChargingTransition.acceptsCallback(runId, run, overlay, view);
    }
    private void log(String message) {
        long now = SystemClock.uptimeMillis();
        Log.d(TAG, "run=" + runId + " " + message + " elapsed=" + (now-connectedAt)
                + " visibleMs=" + (startedAt == 0 ? 0 : now-startedAt));
    }
    private void hideOverlay(String reason) {
        handler.removeCallbacksAndMessages(null);
        ChargingSceneView view = overlay;
        overlay = null;
        if (view == null) return;
        log("Dismiss reason=" + reason);
        try { if (view.isAttachedToWindow()) windows.removeViewImmediate(view); }
        catch (RuntimeException error) { Log.w(TAG, "Unable to remove charging overlay", error); }
        view.close();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        hideOverlay("service-destroyed");
        if (receiverRegistered) unregisterReceiver(powerReceiver);
        receiverRegistered = false;
        super.onDestroy();
    }
}
