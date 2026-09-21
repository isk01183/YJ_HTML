package com.yj.magiccircle;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashSet;
import org.junit.Test;

public class SanctuaryIntegrationTest {
    @Test public void installDefaultNeverOverridesExistingChoice() {
        assertEquals("native-N01", ThemeSelection.initialSelection(false, null));
        assertEquals("moon", ThemeSelection.initialSelection(true, "moon"));
        assertEquals("", ThemeSelection.nextVisible("", ThemeSelection.IDS, Collections.emptySet()));
        assertTrue(ThemeSelection.isValid("native-N01"));
        assertEquals(174, new HashSet<>(ThemeSelection.IDS).size());
    }

    @Test public void repeatedNativeRunsDoNotRemainComplete() {
        ChargingTransition.State s = ChargingTransition.State.COMPLETE;
        for (int i = 0; i < 10; i++) {
            s = ChargingTransition.next(s, ChargingTransition.Event.CONNECT);
            s = ChargingTransition.next(s, ChargingTransition.Event.NATIVE_READY);
            assertEquals(ChargingTransition.State.PLAYING, s);
            s = ChargingTransition.next(s, ChargingTransition.Event.DISCONNECT);
            assertEquals(ChargingTransition.State.DISCONNECTED, s);
        }
    }
}
