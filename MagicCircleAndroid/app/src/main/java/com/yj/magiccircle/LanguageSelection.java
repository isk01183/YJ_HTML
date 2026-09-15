package com.yj.magiccircle;

final class LanguageSelection {
    private LanguageSelection() {}

    static boolean isValid(String language) {
        return "ko".equals(language) || "ja".equals(language) || "en".equals(language);
    }

    static String resolve(String saved, String device) {
        if (isValid(saved)) return saved;
        return isValid(device) ? device : "en";
    }
}
