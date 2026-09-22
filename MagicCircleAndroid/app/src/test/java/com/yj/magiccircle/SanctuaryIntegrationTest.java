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

    @Test public void onlySharedCanvasThemesUseNativePreviewAndCharging() {
        assertTrue(ThemeSelection.isNative("native-N01"));
        assertTrue(ThemeSelection.isNative("ref-W03"));
        assertTrue(ThemeSelection.isNative("ref-R01"));
        assertFalse(ThemeSelection.isNative("moon"));
        assertFalse(ThemeSelection.isNative("ref-F01"));
    }

    @Test public void generatedArtworkUrlsAreExactAndLimitedToTwoThemes() {
        assertEquals("ref-W03", ThemeSelection.generatedArtworkTheme(
                "https://appassets.androidplatform.net/generated/ref-W03.png"));
        assertEquals("ref-R01", ThemeSelection.generatedArtworkTheme(
                "https://appassets.androidplatform.net/generated/ref-R01.png"));
        for (String url : java.util.Arrays.asList(
                "http://appassets.androidplatform.net/generated/ref-W03.png",
                "https://example.com/generated/ref-W03.png",
                "https://appassets.androidplatform.net/generated/ref-F01.png",
                "https://appassets.androidplatform.net/generated/ref-W03.png?thumb=1",
                "https://appassets.androidplatform.net/generated/ref-R01.png#preview",
                "https://appassets.androidplatform.net:443/generated/ref-W03.png")) {
            assertNull(url, ThemeSelection.generatedArtworkTheme(url));
        }
    }

    @Test public void reconnectStartsFreshAndOldRunCallbacksAreRejected() {
        assertEquals(ChargingTransition.State.LOADING, ChargingTransition.next(
                ChargingTransition.State.COMPLETE, ChargingTransition.Event.CONNECT));
        Object currentView = new Object();
        Object oldView = new Object();
        assertTrue(ChargingTransition.acceptsCallback(8L, 8L, currentView, currentView));
        assertFalse(ChargingTransition.acceptsCallback(8L, 7L, currentView, currentView));
        assertFalse(ChargingTransition.acceptsCallback(8L, 8L, currentView, oldView));
    }
}
