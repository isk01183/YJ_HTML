package com.yj.magiccircle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;

public class LibraryPolicyTest {
    @Test public void activationIsAppliedOnlyOnce() {
        Set<String> first = LibraryPolicy.hiddenForRevision(0, Collections.emptySet());
        assertEquals(43, ThemeSelection.V113_ACTIVE.size());
        assertEquals(131, first.size());
        assertTrue(first.contains("ref-C03"));
        assertFalse(first.contains("raphael"));
        first.remove("ref-C03");
        assertFalse(LibraryPolicy.hiddenForRevision(1, first).contains("ref-C03"));
        assertEquals("native-N01", LibraryPolicy.selectionAfterMigration("classic", Collections.emptySet()));
        assertEquals("", LibraryPolicy.selectionAfterMigration("", Collections.emptySet()));
    }

    @Test public void migrationKeepsActiveAndUploadedSelections() {
        Set<String> uploads = Collections.singleton("7f56d2d8-cf99-44e7-b33c-e047cb2f11bb");
        assertEquals("raphael", LibraryPolicy.selectionAfterMigration("raphael", uploads));
        assertEquals("7f56d2d8-cf99-44e7-b33c-e047cb2f11bb",
                LibraryPolicy.selectionAfterMigration("7f56d2d8-cf99-44e7-b33c-e047cb2f11bb", uploads));
        assertEquals("native-N01", LibraryPolicy.selectionAfterMigration("missing", uploads));
    }

    @Test public void tabNamesAreNormalizedAndComparedExactly() {
        assertEquals("Étoile", LibraryPolicy.tabName("  E\u0301toile  ", Collections.emptyList()));
        assertEquals("etoile", LibraryPolicy.tabName("etoile", Collections.singleton("Etoile")));
        assertThrows(IllegalArgumentException.class,
                () -> LibraryPolicy.tabName("E\u0301toile", Collections.singleton("Étoile")));
    }

    @Test public void tabNamesUseUnicodeCodePointLimits() {
        assertEquals(40, LibraryPolicy.tabName("\ud83c\udf19".repeat(40), Collections.emptyList())
                .codePointCount(0, 80));
        for (String invalid : Arrays.asList("", "   ", "a".repeat(41), "\ud83c\udf19".repeat(41))) {
            assertThrows(IllegalArgumentException.class,
                    () -> LibraryPolicy.tabName(invalid, Collections.emptyList()));
        }
    }

    @Test public void unknownActivationRevisionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> LibraryPolicy.hiddenForRevision(2, Collections.emptySet()));
    }
}
