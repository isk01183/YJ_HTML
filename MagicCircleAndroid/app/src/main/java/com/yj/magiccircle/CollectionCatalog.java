package com.yj.magiccircle;

import java.net.URI;

final class CollectionCatalog {
    private CollectionCatalog() {}

    static String assetPath(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getRawPath(), query = uri.getRawQuery();
            if (!"https".equals(uri.getScheme()) || !"appassets.androidplatform.net".equals(uri.getRawAuthority())
                    || uri.getRawFragment() != null || path == null || !path.startsWith("/collection/")
                    || !path.endsWith(".svg")
                    || query != null && !query.matches("run=[0-9]+")) return null;
            String id = path.substring("/collection/".length(), path.length() - ".svg".length());
            return ThemeSelection.isValid("ref-" + id) ? "collection/art/" + id + ".svgz" : null;
        } catch (IllegalArgumentException | NullPointerException error) { return null; }
    }
}
