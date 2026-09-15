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
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;

public final class ChargingAccessibilityService extends AccessibilityService {
    private static final String TAG = "MagicCircleCharging";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable finishAnimation = () -> finish("deadline");
    private WindowManager windows;
    private PowerManager power;
    private WebView overlay;
    private final Runnable checkStart = () -> startAnimation(overlay);
    private boolean receiverRegistered;
    private long runId;
    private long connectedAt;
    private long startupDeadline;
    private int startAttempts;
    private ChargingTransition.State state = ChargingTransition.State.DISCONNECTED;

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) handle(ChargingTransition.Event.CONNECT);
            if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) handle(ChargingTransition.Event.DISCONNECT);
            // A deferred screen-off broadcast must not cancel a new run after waking.
            if (Intent.ACTION_SCREEN_OFF.equals(action) && power != null && !power.isInteractive()) {
                handle(ChargingTransition.Event.SCREEN_OFF);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        Log.d(TAG, "Service connected");
        windows = getSystemService(WindowManager.class);
        power = getSystemService(PowerManager.class);
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
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
        if (event == ChargingTransition.Event.CONNECT) {
            long receivedAt = SystemClock.uptimeMillis();
            hideOverlay("restart");
            runId++;
            connectedAt = receivedAt;
            startAttempts = 0;
        }
        log("Event " + event + " from " + state);
        ChargingTransition.State previous = state;
        state = ChargingTransition.next(state, event);
        if (event == ChargingTransition.Event.CONNECT) {
            if (WebViews.selectedTheme(this).isEmpty()) {
                state = ChargingTransition.State.COMPLETE;
                log("Skipped: no selected animation");
                return;
            }
            handler.postAtTime(finishAnimation,
                    connectedAt + ChargingTransition.ANIMATION_DURATION_MS);
            if (!showOverlay()) state = ChargingTransition.State.COMPLETE;
        } else if (event == ChargingTransition.Event.DISCONNECT) {
            hideOverlay("disconnect");
        } else if (event == ChargingTransition.Event.SCREEN_OFF) {
            // Handler deadlines pause in deep sleep; never resume an obsolete overlay on wake.
            hideOverlay("screen-off");
        } else if (state != previous && event == ChargingTransition.Event.PAGE_READY) {
            startupDeadline = ChargingTransition.startupDeadline(connectedAt, SystemClock.uptimeMillis());
            startAnimation(overlay);
        } else if (state != previous && event == ChargingTransition.Event.JS_STARTED) {
            handler.removeCallbacks(checkStart);
            WebView current = overlay;
            if (current == null) return;
            // Rendering confirmation is diagnostic. It must never gate JavaScript startup.
            current.postVisualStateCallback(runId, new WebView.VisualStateCallback() {
                @Override
                public void onComplete(long requestId) {
                    if (overlay != current) return;
                    log("Visual callback ready");
                    handle(ChargingTransition.Event.VISUAL_READY);
                }
            });
        }
    }

    private void startAnimation(WebView current) {
        if (current == null || overlay != current || state != ChargingTransition.State.STARTING) return;
        long delay = ChargingTransition.retryDelay(startupDeadline, SystemClock.uptimeMillis());
        if (delay == 0) {
            handle(ChargingTransition.Event.START_FAILED);
            finish("js-not-ready");
            return;
        }
        // Imported images become ready asynchronously. Retry only during this bounded startup.
        handler.removeCallbacks(checkStart);
        handler.postDelayed(checkStart, delay);
        final int attempt = ++startAttempts;
        try {
            long remaining = connectedAt + ChargingTransition.ANIMATION_DURATION_MS
                    - SystemClock.uptimeMillis();
            WebViews.startMagicCircle(current, Math.max(1L, remaining), result -> {
                if (overlay != current || state != ChargingTransition.State.STARTING) return;
                log("Animation running=" + result + " attempt=" + attempt);
                if ("true".equals(result)) handle(ChargingTransition.Event.JS_STARTED);
            });
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to start charging animation", error);
            finish("js-error");
        }
    }

    private boolean showOverlay() {
        // Keep the root drawable: alpha=0 can stall the visual callback in the background.
        WebView view = null;
        try {
            view = WebViews.magicCircle(this);
            view.setWebViewClient(new WebViews.LocalClient(this) {
                @Override
                public void onPageFinished(WebView current, String url) {
                    if (overlay != current) return;
                    log("Page ready; attached=" + current.isAttachedToWindow()
                            + " visibility=" + current.getWindowVisibility());
                    handle(ChargingTransition.Event.PAGE_READY);
                }

                @Override
                public void onReceivedError(WebView current, WebResourceRequest request,
                                            WebResourceError error) {
                    if (overlay != current) return;
                    log("Load error=" + error.getErrorCode() + " mainFrame=" + request.isForMainFrame());
                    if (request.isForMainFrame()) finish("load-error");
                }

                @Override
                public boolean onRenderProcessGone(WebView current, RenderProcessGoneDetail detail) {
                    if (overlay == current) {
                        log("Renderer gone; crashed=" + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && detail.didCrash()));
                        finish("renderer-gone");
                    } else current.destroy();
                    return true;
                }
            });
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            // This is a service window, not an Activity: wake without unlocking.
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            windows.addView(view, params);
            overlay = view;
            log("Overlay attached");
            WebViews.loadMagicCircle(view);
            return true;
        } catch (RuntimeException error) {
            if (overlay == view && view != null) hideOverlay("window-error");
            else {
                handler.removeCallbacks(finishAnimation);
                if (view != null) view.destroy();
            }
            Log.e(TAG, "Unable to show charging overlay", error);
            return false;
        }
    }

    private void finish(String reason) {
        log("Event FINISH from " + state);
        state = ChargingTransition.next(state, ChargingTransition.Event.FINISH);
        hideOverlay(reason);
    }

    private void log(String message) {
        Log.d(TAG, "run=" + runId + " " + message + " elapsed="
                + (SystemClock.uptimeMillis() - connectedAt)
                + " interactive=" + (power != null && power.isInteractive()));
    }

    private void hideOverlay(String reason) {
        handler.removeCallbacks(finishAnimation);
        handler.removeCallbacks(checkStart);
        if (overlay == null) return;
        log("Dismiss reason=" + reason);
        WebView view = overlay;
        overlay = null;
        try {
            windows.removeView(view);
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to remove charging overlay", error);
        }
        if (!"renderer-gone".equals(reason)) view.stopLoading();
        view.destroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        hideOverlay("service-destroyed");
        state = ChargingTransition.State.DISCONNECTED;
        if (receiverRegistered) unregisterReceiver(powerReceiver);
        receiverRegistered = false;
        super.onDestroy();
    }
}
