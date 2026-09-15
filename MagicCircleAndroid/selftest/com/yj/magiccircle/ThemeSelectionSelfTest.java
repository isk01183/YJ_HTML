package com.yj.magiccircle;

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
        System.out.println("THEME_SELECTION_OK");
    }
}
