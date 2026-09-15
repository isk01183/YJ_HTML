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
        // An omitted built-in must not lose saved selection or disappear after restore.
        String[] groups = {"C", "W", "F", "A", "B", "G", "E", "U", "R"};
        int[] counts = {30, 5, 5, 30, 30, 5, 8, 4, 1};
        java.util.List<String> collection = new java.util.ArrayList<>();
        for (int group = 0; group < groups.length; group++) {
            for (int number = 1; number <= counts[group]; number++) {
                String id = "ref-" + groups[group] + (number < 10 ? "0" : "") + number;
                check(ThemeSelection.isValid(id), "collection selection accepted: " + id);
                check(id.equals(ThemeSelection.normalize(id)), "saved collection selection retained: " + id);
                collection.add(id);
            }
        }
        check(ThemeSelection.IDS.size() == 173 && new HashSet<>(ThemeSelection.IDS).size() == 173,
                "all 55 existing and 118 collection entries remain distinct");
        check(ThemeSelection.IDS.subList(55, 173).equals(collection), "collection order matches approved catalog");
        check("".equals(ThemeSelection.nextVisible("", ThemeSelection.IDS,
                new HashSet<>(ThemeSelection.IDS.subList(0, 55)))),
                "v1.9 explicit empty selection must not activate a new design after upgrade");
        for (String bad : new String[] {"C03", "ref-C00", "ref-C31", "ref-W06", "ref-F06", "ref-A31",
                "ref-B31", "ref-G06", "ref-E09", "ref-U05", "ref-R02", "ref-D01", "ref-c03",
                "ref-C3", "ref-C003", "ref-C03.svg", " ref-C03", "ref-C03 ", "ref-C03/../A01",
                "ref-C03?run=1", "ref-%4303"}) {
            check(!ThemeSelection.isValid(bad) && "classic".equals(ThemeSelection.normalize(bad)),
                    "non-catalog selection rejected: " + bad);
        }
        for (String id : collection) {
            HashSet<String> hidden = new HashSet<>(ThemeSelection.IDS);
            hidden.remove(id);
            check(id.equals(ThemeSelection.nextVisible("classic", ThemeSelection.IDS, hidden)),
                    "only visible collection entry selected: " + id);
            hidden.add(id);
            check("".equals(ThemeSelection.nextVisible(id, ThemeSelection.IDS, hidden)),
                    "hiding last collection entry leaves no selection: " + id);
            hidden.clear();
            check(id.equals(ThemeSelection.nextVisible(id, ThemeSelection.IDS, hidden)),
                    "restoring collection retains available selection: " + id);
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
