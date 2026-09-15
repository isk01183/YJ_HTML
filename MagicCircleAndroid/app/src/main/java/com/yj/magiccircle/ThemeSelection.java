package com.yj.magiccircle;

final class ThemeSelection {
    private ThemeSelection() {}

    static boolean isValid(String id) {
        if (id == null) return false;
        switch (id) {
            case "classic": case "moon": case "raphael": case "layered": case "premium":
            case "basic": case "blue": case "gold": case "silver": case "violet":
            case "cyan": case "core": case "minimal":
            case "celestial-satellites": case "crimson-abyss": case "ivory-alchemy":
            case "triune-seal": case "vital-staff": case "solar-crown": case "healing-wings":
            case "lunar-phases": case "frost-crystal": case "sage-nexus": case "hex-lattice":
            case "grimoire-star": case "world-tree": case "blood-moon": case "all-seeing-eye":
            case "spacetime-prism": case "spiral-galaxy": case "heart-sanctuary":
            case "snowflake": case "jade-lotus": case "flame-spirit": case "tidal-vortex":
            case "moon-wheel": case "gravity-well": case "sakura-seal": case "elemental-concord":
            case "spirit-bloom": case "crescent-moon": case "star-guide": case "chronos-gears":
            case "seraph-wings": case "infernal-pact": case "binding-chains": case "guardian-shield":
            case "infinite-bond": case "fate-compass": case "earth-crystal": case "water-spirit":
            case "wind-spirit": case "light-rosette": case "dream-constellation": case "twilight-balance":
                return true;
            default:
                return id.matches("\\Aref-(?:[15]-0[1-5]|[234]-(?:0[1-9]|[12][0-9]|30))\\z");
        }
    }

    static String normalize(String id) {
        return isValid(id) ? id : "classic";
    }
}
