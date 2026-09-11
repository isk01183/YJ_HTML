package com.yj.magiccircle;

final class ChargingTransition {
    enum State { DISCONNECTED, LOADING, PLAYING, COMPLETE }
    enum Event { CONNECT, VISUAL_READY, DISCONNECT, FINISH }

    private ChargingTransition() {}

    static State next(State current, Event event) {
        if (event == Event.CONNECT) return State.LOADING;
        if (event == Event.VISUAL_READY && current == State.LOADING) return State.PLAYING;
        if (event == Event.DISCONNECT) return State.DISCONNECTED;
        if (event == Event.FINISH
                && (current == State.LOADING || current == State.PLAYING)) return State.COMPLETE;
        return current;
    }
}
