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
                "dream-constellation", "twilight-balance",
                "ref-1-01", "ref-1-05", "ref-2-01", "ref-2-09", "ref-2-10",
                "ref-2-19", "ref-2-20", "ref-2-29", "ref-2-30", "ref-3-01", "ref-3-30",
                "ref-4-01", "ref-4-30", "ref-5-01", "ref-5-05"}) {
            if (!ThemeSelection.isValid(id) || !id.equals(ThemeSelection.normalize(id))) {
                throw new AssertionError("Supported selection was lost: " + id);
            }
        }
        for (String id : new String[] {null, "", "unknown", "BLUE", " blue", "blue ",
                "../gallery.html", "blue&battery=100", "javascript:alert(1)",
                "ref-1-00", "ref-1-06", "ref-2-00", "ref-2-31", "ref-3-31", "ref-4-31",
                "ref-5-00", "ref-5-06", "ref-0-01", "ref-6-01", "ref-2-1", "ref-02-01",
                "ref-2-001", "REF-2-01", "ref-2-01 ", " ref-2-01", "ref-2-01\n",
                "ref-2-01\r\n", "ref-2-01&lang=ko", "ref-2-01');alert(1);//",
                "ref-2-01/../gallery.html", "ref-2-\uFF10\uFF11"}) {
            if (ThemeSelection.isValid(id) || !"classic".equals(ThemeSelection.normalize(id))) {
                throw new AssertionError("Invalid selection did not safely fall back: " + id);
            }
        }
        System.out.println("THEME_SELECTION_OK");
    }
}
