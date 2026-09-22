package com.yj.magiccircle;

import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

final class LibraryPolicy {
    private LibraryPolicy() {}

    static Set<String> hiddenForRevision(int revision, Set<String> saved) {
        if (revision == 1) return new LinkedHashSet<>(saved);
        if (revision != 0) throw new IllegalArgumentException("Unknown activation revision");
        Set<String> result = new LinkedHashSet<>(ThemeSelection.IDS);
        result.removeAll(ThemeSelection.V113_ACTIVE);
        return result;
    }

    static String selectionAfterMigration(String saved, Set<String> validMediaIds) {
        if ("".equals(saved)) return "";
        return ThemeSelection.V113_ACTIVE.contains(saved) || validMediaIds.contains(saved)
                ? saved : "native-N01";
    }

    static String tabName(String raw, Collection<String> otherNames) {
        if (raw == null) throw new IllegalArgumentException("Tab name is required");
        String name = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC);
        int length = name.codePointCount(0, name.length());
        if (length < 1 || length > 40) throw new IllegalArgumentException("Invalid tab name length");
        for (String other : otherNames) {
            if (name.equals(Normalizer.normalize(other.trim(), Normalizer.Form.NFC))) {
                throw new IllegalArgumentException("Duplicate tab name");
            }
        }
        return name;
    }
}
