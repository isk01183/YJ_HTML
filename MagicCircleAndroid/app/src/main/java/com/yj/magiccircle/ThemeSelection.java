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
            "dream-constellation", "twilight-balance",
            "ref-C01", "ref-C02", "ref-C03", "ref-C04", "ref-C05", "ref-C06", "ref-C07", "ref-C08", "ref-C09", "ref-C10",
            "ref-C11", "ref-C12", "ref-C13", "ref-C14", "ref-C15", "ref-C16", "ref-C17", "ref-C18", "ref-C19", "ref-C20",
            "ref-C21", "ref-C22", "ref-C23", "ref-C24", "ref-C25", "ref-C26", "ref-C27", "ref-C28", "ref-C29", "ref-C30",
            "ref-W01", "ref-W02", "ref-W03", "ref-W04", "ref-W05", "ref-F01", "ref-F02", "ref-F03", "ref-F04", "ref-F05",
            "ref-A01", "ref-A02", "ref-A03", "ref-A04", "ref-A05", "ref-A06", "ref-A07", "ref-A08", "ref-A09", "ref-A10",
            "ref-A11", "ref-A12", "ref-A13", "ref-A14", "ref-A15", "ref-A16", "ref-A17", "ref-A18", "ref-A19", "ref-A20",
            "ref-A21", "ref-A22", "ref-A23", "ref-A24", "ref-A25", "ref-A26", "ref-A27", "ref-A28", "ref-A29", "ref-A30",
            "ref-B01", "ref-B02", "ref-B03", "ref-B04", "ref-B05", "ref-B06", "ref-B07", "ref-B08", "ref-B09", "ref-B10",
            "ref-B11", "ref-B12", "ref-B13", "ref-B14", "ref-B15", "ref-B16", "ref-B17", "ref-B18", "ref-B19", "ref-B20",
            "ref-B21", "ref-B22", "ref-B23", "ref-B24", "ref-B25", "ref-B26", "ref-B27", "ref-B28", "ref-B29", "ref-B30",
            "ref-G01", "ref-G02", "ref-G03", "ref-G04", "ref-G05", "ref-E01", "ref-E02", "ref-E03", "ref-E04",
            "ref-E05", "ref-E06", "ref-E07", "ref-E08", "ref-U01", "ref-U02", "ref-U03", "ref-U04", "ref-R01"));

    static boolean isValid(String id) {
        return IDS.contains(id);
    }

    static String normalize(String id) {
        return isValid(id) ? id : "classic";
    }

    static String page(String id) {
        if (!isValid(id)) return null;
        return "classic".equals(id) ? "magic_circle.html"
                : id.startsWith("ref-") ? "collection_circle.html" : "theme_circle.html";
    }

    static String nextVisible(String current, java.util.List<String> ordered,
            java.util.Set<String> unavailable) {
        // An explicitly empty saved library stays disabled when a later app adds new designs.
        if ("".equals(current)) return "";
        int start = ordered.indexOf(current);
        if (start < 0) start = 0;
        for (int offset = 0; offset < ordered.size(); offset++) {
            String id = ordered.get((start + offset) % ordered.size());
            if (!unavailable.contains(id)) return id;
        }
        return "";
    }
}
