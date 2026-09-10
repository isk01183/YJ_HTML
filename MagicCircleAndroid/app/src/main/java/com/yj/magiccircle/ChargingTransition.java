package com.yj.magiccircle;

final class ChargingTransition {
    enum Action { SHOW, HIDE, NONE }

    private ChargingTransition() {}

    static Action next(boolean visible, boolean charging) {
        if (visible == charging) return Action.NONE;
        return charging ? Action.SHOW : Action.HIDE;
    }
}
