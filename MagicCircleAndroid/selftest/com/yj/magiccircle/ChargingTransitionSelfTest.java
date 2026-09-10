package com.yj.magiccircle;

public final class ChargingTransitionSelfTest {
    public static void main(String[] args) {
        check(ChargingTransition.Action.SHOW, false, true);
        check(ChargingTransition.Action.NONE, true, true);
        check(ChargingTransition.Action.HIDE, true, false);
        check(ChargingTransition.Action.NONE, false, false);
    }

    private static void check(ChargingTransition.Action expected, boolean visible, boolean charging) {
        ChargingTransition.Action actual = ChargingTransition.next(visible, charging);
        if (actual != expected) throw new AssertionError(expected + " != " + actual);
    }
}
