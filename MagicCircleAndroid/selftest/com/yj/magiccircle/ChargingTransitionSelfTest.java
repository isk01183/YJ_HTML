package com.yj.magiccircle;

public final class ChargingTransitionSelfTest {
    public static void main(String[] args) {
        ChargingTransition.Event screenOff;
        try { screenOff = ChargingTransition.Event.valueOf("SCREEN_OFF"); }
        catch (IllegalArgumentException error) { throw new AssertionError("Screen-off must end the active run before sleep"); }
        for (ChargingTransition.State active : new ChargingTransition.State[] {
                ChargingTransition.State.LOADING, ChargingTransition.State.STARTING, ChargingTransition.State.PLAYING}) {
            check(ChargingTransition.State.COMPLETE, active, screenOff);
        }
        check(ChargingTransition.State.DISCONNECTED, ChargingTransition.State.DISCONNECTED, screenOff);
        check(ChargingTransition.State.LOADING, ChargingTransition.State.DISCONNECTED,
                ChargingTransition.Event.CONNECT);
        // A compositor callback is rendering evidence, not JavaScript startup.
        check(ChargingTransition.State.LOADING, ChargingTransition.State.LOADING,
                ChargingTransition.Event.VISUAL_READY);
        check(ChargingTransition.State.LOADING, ChargingTransition.State.PLAYING,
                ChargingTransition.Event.CONNECT);
        check(ChargingTransition.State.COMPLETE, ChargingTransition.State.PLAYING,
                ChargingTransition.Event.FINISH);
        check(ChargingTransition.State.COMPLETE, ChargingTransition.State.LOADING,
                ChargingTransition.Event.FINISH);
        check(ChargingTransition.State.LOADING, ChargingTransition.State.COMPLETE,
                ChargingTransition.Event.CONNECT);
        check(ChargingTransition.State.DISCONNECTED, ChargingTransition.State.PLAYING,
                ChargingTransition.Event.DISCONNECT);
        check(ChargingTransition.State.DISCONNECTED, ChargingTransition.State.LOADING,
                ChargingTransition.Event.DISCONNECT);
        check(ChargingTransition.State.DISCONNECTED, ChargingTransition.State.COMPLETE,
                ChargingTransition.Event.DISCONNECT);
        check(ChargingTransition.State.DISCONNECTED, ChargingTransition.State.DISCONNECTED,
                ChargingTransition.Event.VISUAL_READY);
        check(ChargingTransition.State.COMPLETE, ChargingTransition.State.COMPLETE,
                ChargingTransition.Event.VISUAL_READY);
        // Page readiness starts JavaScript even if the compositor never calls back.
        ChargingTransition.State state = ChargingTransition.next(
                ChargingTransition.State.DISCONNECTED, ChargingTransition.Event.CONNECT);
        state = ChargingTransition.next(state, ChargingTransition.Event.PAGE_READY);
        if (state != ChargingTransition.State.STARTING) throw new AssertionError("Page must start JS");
        check(ChargingTransition.State.STARTING, state, ChargingTransition.Event.PAGE_READY);
        check(ChargingTransition.State.STARTING, state, ChargingTransition.Event.VISUAL_READY);
        check(ChargingTransition.State.PLAYING, state, ChargingTransition.Event.JS_STARTED);
        check(ChargingTransition.State.COMPLETE, state, ChargingTransition.Event.START_FAILED);
        check(ChargingTransition.State.LOADING, ChargingTransition.State.LOADING,
                ChargingTransition.Event.JS_STARTED);
        for (ChargingTransition.State terminal : new ChargingTransition.State[] {
                ChargingTransition.State.DISCONNECTED, ChargingTransition.State.COMPLETE}) {
            check(terminal, terminal, ChargingTransition.Event.PAGE_READY);
            check(terminal, terminal, ChargingTransition.Event.JS_STARTED);
            check(terminal, terminal, ChargingTransition.Event.START_FAILED);
            check(ChargingTransition.State.LOADING, terminal, ChargingTransition.Event.CONNECT);
        }
        check(ChargingTransition.State.DISCONNECTED, state, ChargingTransition.Event.DISCONNECT);
        check(ChargingTransition.State.COMPLETE, state, ChargingTransition.Event.FINISH);
        check(ChargingTransition.State.LOADING, state, ChargingTransition.Event.CONNECT);
        check(ChargingTransition.State.PLAYING, ChargingTransition.State.PLAYING,
                ChargingTransition.Event.START_FAILED);
        // Cold startup has a finite retry window and never extends the connection deadline.
        if (ChargingTransition.startupDeadline(1000, 2000) != 5000
                || ChargingTransition.startupDeadline(1000, 7000) != 8000
                || ChargingTransition.retryDelay(5000, 2000) != 250
                || ChargingTransition.retryDelay(5000, 4999) != 1
                || ChargingTransition.retryDelay(5000, 5000) != 0
                || ChargingTransition.retryDelay(5000, 5100) != 0) {
            throw new AssertionError("Startup retries must stop at their bounded deadline");
        }
        System.out.println("CHARGING_TRANSITION_OK: page-led start, render independence, terminal guards, bounded retries");
    }

    private static void check(ChargingTransition.State expected, ChargingTransition.State current,
                              ChargingTransition.Event event) {
        ChargingTransition.State actual = ChargingTransition.next(current, event);
        if (actual != expected) throw new AssertionError(expected + " != " + actual);
    }

}
