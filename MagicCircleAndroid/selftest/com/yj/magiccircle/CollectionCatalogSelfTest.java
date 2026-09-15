package com.yj.magiccircle;

public final class CollectionCatalogSelfTest {
    public static void main(String[] args) {
        // Wrong page routing would send a catalog ID into the unrelated legacy renderer.
        check("magic_circle.html".equals(ThemeSelection.page("classic")), "classic renderer retained");
        check("theme_circle.html".equals(ThemeSelection.page("moon")), "legacy renderer retained");
        for (String id : ThemeSelection.IDS) if (id.startsWith("ref-")) {
            check("collection_circle.html".equals(ThemeSelection.page(id)), "collection renderer: " + id);
            String source = id.substring(4);
            for (String suffix : new String[] {"", "?run=123456", "?run=0"}) {
                check(("collection/art/" + source + ".svgz").equals(CollectionCatalog.assetPath(
                        "https://appassets.androidplatform.net/collection/" + source + ".svg" + suffix)),
                        "packaged SVG route: " + source + suffix);
            }
        }
        for (String id : new String[] {null, "", "ref-C31", "C03", "ref-c03", "ref-C03.svg",
                "550e8400-e29b-41d4-a716-446655440000"}) {
            check(ThemeSelection.page(id) == null, "non-builtin cannot choose a built-in renderer: " + id);
        }
        String url = "https://appassets.androidplatform.net/collection/C03.svg";
        // An origin, path, or query bypass could expose another asset or initiate a network fetch.
        for (String bad : new String[] {null, "", url + "?", url + "#", url + "#x", url + "/extra",
                url + "?run=", url + "?run=-1", url + "?run=1.0", url + "?run=1&run=2", url + "?thumb=1",
                url + "?run=1&path=secret", url + "?run=%31", url + "?run=1#x",
                url.replace("https:", "http:"), url.replace("https:", "HTTPS:"),
                url.replace(".net/", ".net:443/"), url.replace(".net/", ".net.evil/"),
                url.replace("https://", "https://user@"), url.replace("https://", "https://appassets.androidplatform.net@"),
                url.replace(".net", ".NET"), url.replace("/C03", "/../C03"),
                url.replace("/C03", "/%2e%2e/C03"), url.replace("/C03", "/%4303"),
                url.replace("/C03", "//C03"), url.replace("C03.svg", "C03.svgz"),
                url.replace("C03.svg", "C3.svg"), url.replace("C03.svg", "c03.svg"),
                url.replace("C03.svg", "ref-C03.svg"), url.replace("C03.svg", "C31.svg"),
                url.replace("C03.svg", "R02.svg"), url.replace("C03.svg", "classic.svg"),
                url.replace("/collection/", "/media/"), "file:///android_asset/collection/art/C03.svgz",
                "content://appassets.androidplatform.net/collection/C03.svg", "javascript:alert(1)"}) {
            check(CollectionCatalog.assetPath(bad) == null, "unsafe SVG route blocked: " + bad);
        }
        System.out.println("COLLECTION_CATALOG_OK");
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
