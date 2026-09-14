package com.yj.magiccircle;

final class ThemeSelection {
    private ThemeSelection() {}

    static boolean isValid(String id) {
        if (id == null) return false;
        switch (id) {
            case "classic": case "moon": case "raphael": case "layered": case "premium":
            case "basic": case "blue": case "gold": case "silver": case "violet":
            case "cyan": case "core": case "minimal":
                return true;
            default:
                return false;
        }
    }

    static String normalize(String id) {
        return isValid(id) ? id : "classic";
    }
}
