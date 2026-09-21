package com.yj.magiccircle;

final class ChargingTransition {
    static final long ANIMATION_DURATION_MS = 7_000L;
    enum State { DISCONNECTED, LOADING, STARTING, PLAYING, COMPLETE }
    enum Event { CONNECT, NATIVE_READY, PAGE_READY, JS_STARTED, VISUAL_READY, START_FAILED, DISCONNECT, SCREEN_OFF, FINISH }

    private ChargingTransition() {}

    static State next(State current, Event event) {
        if (event == Event.CONNECT) return State.LOADING;
        if (event == Event.NATIVE_READY && current == State.LOADING) return State.PLAYING;
        if (event == Event.PAGE_READY && current == State.LOADING) return State.STARTING;
        if (event == Event.JS_STARTED && current == State.STARTING) return State.PLAYING;
        if (event == Event.START_FAILED && current == State.STARTING) return State.COMPLETE;
        if (event == Event.DISCONNECT) return State.DISCONNECTED;
        if ((event == Event.FINISH || event == Event.SCREEN_OFF)
                && (current == State.LOADING || current == State.STARTING
                || current == State.PLAYING)) return State.COMPLETE;
        return current;
    }

    static long startupDeadline(long connectedAt, long pageReadyAt) {
        return Math.min(connectedAt + ANIMATION_DURATION_MS, pageReadyAt + 3_000L);
    }

    static long retryDelay(long deadline, long now) {
        return Math.max(0L, Math.min(250L, deadline - now));
    }
}
