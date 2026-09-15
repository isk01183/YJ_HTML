package com.yj.magiccircle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MediaValidationSelfTest {
    public static void main(String[] args) throws Exception {
        // Missing signature validation would admit HTML disguised as a photo.
        check("image/gif".equals(MediaValidation.mime("GIF89a123456".getBytes())), "GIF signature");
        check("image/png".equals(MediaValidation.mime(new byte[] {(byte)137,80,78,71,13,10,26,10})), "PNG signature");
        check("image/jpeg".equals(MediaValidation.mime(new byte[] {(byte)255,(byte)216,(byte)255})), "JPEG signature");
        check(MediaValidation.mime("<script>alert(1)</script>".getBytes()) == null, "script rejected");
        check(MediaValidation.mime(new byte[] {71,73,70}) == null, "truncated header rejected");
        check(MediaValidation.matchesType("image/jpeg", "image/jpg"), "JPEG alias");
        check(!MediaValidation.matchesType("image/png", "image/svg+xml"), "claimed SVG rejected");
        check(!MediaValidation.matchesType("image/png", "image/jpeg"), "mismatched MIME rejected");
        check(MediaValidation.matchesType("image/png", null), "unknown provider MIME accepted after sniffing");

        // Removing bounded copying would permit an untrusted provider to exhaust storage.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MediaValidation.copy(new ByteArrayInputStream(new byte[] {1,2,3,4}), output, 4);
        check(output.size() == 4, "exact byte limit accepted");
        try {
            MediaValidation.copy(new ByteArrayInputStream(new byte[] {1,2,3,4,5}), new ByteArrayOutputStream(), 4);
            throw new AssertionError("oversize accepted");
        } catch (MediaValidation.InvalidMedia expected) {
            check("too_large".equals(expected.code), "size error classification");
        }
        check(MediaValidation.validDimensions(4096, 3906), "bounded dimensions accepted");
        check(!MediaValidation.validDimensions(4096, 4096), "pixel budget enforced");
        check(!MediaValidation.validDimensions(0, 20), "failed decoding rejected");
        check(!MediaValidation.validDimensions(5000, 1), "extreme dimension rejected");
        check(MediaValidation.isId("550e8400-e29b-41d4-a716-446655440000"), "UUID accepted");
        for (String id : new String[] {"../secret", "x.html", "javascript:alert(1)", "550e8400-e29b-41d4-a716-446655440000/extra", null}) {
            check(!MediaValidation.isId(id), "unsafe ID rejected: " + id);
        }
        String id = "550e8400-e29b-41d4-a716-446655440000";
        String url = "https://appassets.androidplatform.net/media/" + id;
        // Broadening the interceptor must never expose a filename or trigger network fallback.
        for (String suffix : new String[] {"", "?thumb=1", "?run=123456"}) {
            check(id.equals(MediaValidation.resourceId(url + suffix)), "local resource allowed: " + suffix);
        }
        for (String bad : new String[] {url + "/../private", url + "?thumb=1&path=secret", url + "#x",
                url.replace("https:", "http:"), url.replace("/media/", "/media/%2e%2e/"),
                url.replace(".net/", ".net:443/"), url.replace(".net/", ".net.evil/"),
                url.replace("/media/5", "/media/%35"), "file:///data/data/com.yj.magiccircle/files/secret"}) {
            check(MediaValidation.resourceId(bad) == null, "unsafe resource blocked: " + bad);
        }
        // A failed physical deletion must stay queued; never treat one deleted file as complete.
        Path directory = Files.createTempDirectory("magic-circle-delete-check-");
        Path image = Files.createDirectory(directory.resolve(id));
        Path retained = Files.createFile(image.resolve("keep"));
        Path thumbnail = Files.createFile(directory.resolve(id + ".png"));
        try {
            check(!MediaValidation.deleteCopies(directory.toFile(), id), "partial deletion reports pending cleanup");
            check(Files.exists(retained), "unrelated contents untouched");
            check(!Files.exists(thumbnail), "other private copy cleanup attempted");
            Files.delete(retained);
            Files.delete(image);
            Files.createFile(image);
            check(MediaValidation.deleteCopies(directory.toFile(), id), "cleanup retry succeeds");
            check(!Files.exists(image), "retry deletes remaining private copy");
            try {
                MediaValidation.deleteCopies(directory.toFile(), "../original");
                throw new AssertionError("unsafe deletion ID accepted");
            } catch (IllegalArgumentException expected) {}
        } finally {
            Files.deleteIfExists(retained);
            Files.deleteIfExists(image);
            Files.deleteIfExists(thumbnail);
            Files.deleteIfExists(directory);
        }
        byte[] gif = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==");
        // A GIF parser must inspect all frames, not only the first decoded frame.
        check(MediaValidation.validateGif(new ByteArrayInputStream(gif)) == 1, "single-frame GIF accepted");
        try {
            MediaValidation.validateGif(new ByteArrayInputStream(java.util.Arrays.copyOf(gif, gif.length - 1)));
            throw new AssertionError("missing GIF trailer accepted");
        } catch (MediaValidation.InvalidMedia expected) {}
        ByteArrayOutputStream animation = new ByteArrayOutputStream();
        animation.write(gif, 0, 19);
        for (int frame = 0; frame < 301; frame++) animation.write(gif, 19, gif.length - 20);
        animation.write(59);
        try {
            MediaValidation.validateGif(new ByteArrayInputStream(animation.toByteArray()));
            throw new AssertionError("excessive GIF frame count accepted");
        } catch (MediaValidation.InvalidMedia expected) {}
        System.out.println("MEDIA_VALIDATION_OK");
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
