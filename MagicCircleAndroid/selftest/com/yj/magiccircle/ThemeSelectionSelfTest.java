package com.yj.magiccircle;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public final class ThemeSelectionSelfTest {
    public static void main(String[] args) {
        for (String id : new String[] {"classic", "moon", "raphael", "layered", "premium",
                "basic", "blue", "gold", "silver", "violet", "cyan", "core", "minimal",
                "celestial-satellites", "crimson-abyss", "ivory-alchemy", "triune-seal",
                "vital-staff", "solar-crown", "healing-wings", "lunar-phases", "frost-crystal",
                "sage-nexus", "hex-lattice", "grimoire-star", "world-tree", "blood-moon",
                "all-seeing-eye", "spacetime-prism", "spiral-galaxy", "heart-sanctuary",
                "snowflake", "jade-lotus", "flame-spirit", "tidal-vortex", "moon-wheel",
                "gravity-well", "sakura-seal", "elemental-concord", "spirit-bloom",
                "crescent-moon", "star-guide", "chronos-gears", "seraph-wings", "infernal-pact",
                "binding-chains", "guardian-shield", "infinite-bond", "fate-compass",
                "earth-crystal", "water-spirit", "wind-spirit", "light-rosette",
                "dream-constellation", "twilight-balance"}) {
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
        // Losing the current position must not jump to a hidden/deleted selection.
        java.util.List<String> order = Arrays.asList("classic", "moon", "imported-a", "imported-b");
        check("imported-b".equals(ThemeSelection.nextVisible("imported-a", order,
                new HashSet<>(Arrays.asList("moon", "imported-a")))), "deletion chooses next visible item");
        check("classic".equals(ThemeSelection.nextVisible("imported-b", order,
                Collections.singleton("imported-b"))), "deletion wraps to first visible item");
        check("".equals(ThemeSelection.nextVisible("moon", order,
                new HashSet<>(order))), "empty library has no selection");
        check("moon".equals(ThemeSelection.nextVisible("moon", order,
                Collections.emptySet())), "unchanged selection retained");
        check("classic".equals(ThemeSelection.nextVisible("../bad", order,
                Collections.emptySet())), "invalid saved selection chooses first visible");
        System.out.println("THEME_SELECTION_OK");
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
