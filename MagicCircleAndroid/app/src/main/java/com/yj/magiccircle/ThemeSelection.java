package com.yj.magiccircle;

final class ThemeSelection {
    private ThemeSelection() {}

    static final java.util.List<String> IDS = java.util.Collections.unmodifiableList(java.util.Arrays.asList(
            "classic", "moon", "raphael", "layered", "premium", "basic", "blue", "gold", "silver", "violet",
            "cyan", "core", "minimal", "celestial-satellites", "crimson-abyss", "ivory-alchemy", "triune-seal",
            "vital-staff", "solar-crown", "healing-wings", "lunar-phases", "frost-crystal", "sage-nexus",
            "hex-lattice", "grimoire-star", "world-tree", "blood-moon", "all-seeing-eye", "spacetime-prism",
            "spiral-galaxy", "heart-sanctuary", "snowflake", "jade-lotus", "flame-spirit", "tidal-vortex",
            "moon-wheel", "gravity-well", "sakura-seal", "elemental-concord", "spirit-bloom", "crescent-moon",
            "star-guide", "chronos-gears", "seraph-wings", "infernal-pact", "binding-chains", "guardian-shield",
            "infinite-bond", "fate-compass", "earth-crystal", "water-spirit", "wind-spirit", "light-rosette",
            "dream-constellation", "twilight-balance"));

    static boolean isValid(String id) {
        return IDS.contains(id);
    }

    static String normalize(String id) {
        return isValid(id) ? id : "classic";
    }

    static String nextVisible(String current, java.util.List<String> ordered,
            java.util.Set<String> unavailable) {
        int start = ordered.indexOf(current);
        if (start < 0) start = 0;
        for (int offset = 0; offset < ordered.size(); offset++) {
            String id = ordered.get((start + offset) % ordered.size());
            if (!unavailable.contains(id)) return id;
        }
        return "";
    }
}
