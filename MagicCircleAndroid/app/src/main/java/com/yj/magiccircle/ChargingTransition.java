package com.yj.magiccircle;

final class ChargingTransition {
    enum State { DISCONNECTED, PLAYING, COMPLETE }
    enum Event { CONNECT, DISCONNECT, FINISH }

    private ChargingTransition() {}

    static State next(State current, Event event) {
        if (event == Event.DISCONNECT) return State.DISCONNECTED;
        if (event == Event.CONNECT && current == State.DISCONNECTED) return State.PLAYING;
        if (event == Event.FINISH && current == State.PLAYING) return State.COMPLETE;
        return current;
    }
}
