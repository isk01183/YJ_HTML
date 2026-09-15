package com.yj.magiccircle;

public final class LanguageSelectionSelfTest {
    public static void main(String[] args) {
        for (String language : new String[] {"ko", "ja", "en"}) {
            if (!LanguageSelection.isValid(language)) {
                throw new AssertionError("Language cannot be selected: " + language);
            }
            check(language, language, "fr");
            check(language, null, language);
        }
        // Saved language wins over the device; invalid saved values never enter JS/URLs.
        check("ko", "ko", "ja");
        check("ja", "ja", "en");
        check("en", null, "fr");
        check("en", null, null);
        for (String invalid : new String[] {null, "", "KO", " ko", "ko ", "ko-KR", "fr",
                "ko&theme=classic", "ja');alert(1);//", "javascript:alert(1)", "%6b%6f"}) {
            if (LanguageSelection.isValid(invalid)) {
                throw new AssertionError("Invalid language accepted: " + invalid);
            }
            check("ja", invalid, "ja");
            check("en", invalid, "de");
        }
        System.out.println("LANGUAGE_SELECTION_OK");
    }

    private static void check(String expected, String saved, String device) {
        String actual = LanguageSelection.resolve(saved, device);
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
