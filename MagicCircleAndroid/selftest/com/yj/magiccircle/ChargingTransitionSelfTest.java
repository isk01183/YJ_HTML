package com.yj.magiccircle;

public final class ChargingTransitionSelfTest {
    public static void main(String[] args) {
        check(ChargingTransition.State.LOADING, ChargingTransition.State.DISCONNECTED,
                ChargingTransition.Event.CONNECT);
        check(ChargingTransition.State.PLAYING, ChargingTransition.State.LOADING,
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
    }

    private static void check(ChargingTransition.State expected, ChargingTransition.State current,
                              ChargingTransition.Event event) {
        ChargingTransition.State actual = ChargingTransition.next(current, event);
        if (actual != expected) throw new AssertionError(expected + " != " + actual);
    }

}
