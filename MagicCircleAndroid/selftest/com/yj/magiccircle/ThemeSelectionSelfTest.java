package com.yj.magiccircle;

public final class ThemeSelectionSelfTest {
    public static void main(String[] args) {
        for (String id : new String[] {"classic", "moon", "raphael", "layered", "premium",
                "basic", "blue", "gold", "silver", "violet", "cyan", "core", "minimal"}) {
            if (!ThemeSelection.isValid(id) || !id.equals(ThemeSelection.normalize(id))) {
                throw new AssertionError("Supported selection was lost: " + id);
            }
        }
        for (String id : new String[] {null, "", "unknown", "BLUE", " blue", "blue ",
                "../gallery.html", "blue&battery=100", "javascript:alert(1)"}) {
            if (ThemeSelection.isValid(id) || !"classic".equals(ThemeSelection.normalize(id))) {
                throw new AssertionError("Invalid selection did not safely fall back: " + id);
            }
        }
        System.out.println("THEME_SELECTION_OK");
    }
}
