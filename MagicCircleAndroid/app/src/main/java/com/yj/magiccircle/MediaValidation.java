package com.yj.magiccircle;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.net.URI;
import java.io.File;

final class MediaValidation {
    static final long MAX_BYTES = 20L * 1024 * 1024;
    static final class InvalidMedia extends IOException {
        final String code;
        InvalidMedia(String code) { super(code); this.code = code; }
    }
    static String mime(byte[] header) {
        if (header.length >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                && header[3] == '8' && (header[4] == '7' || header[4] == '9') && header[5] == 'a') return "image/gif";
        byte[] png = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
        if (header.length >= png.length) {
            boolean matches = true;
            for (int i = 0; i < png.length; i++) matches &= header[i] == png[i];
            if (matches) return "image/png";
        }
        return header.length >= 3 && (header[0] & 255) == 255 && (header[1] & 255) == 216
                && (header[2] & 255) == 255 ? "image/jpeg" : null;
    }

    static boolean matchesType(String actual, String declared) {
        if (actual == null) return false;
        if (declared == null || declared.isEmpty()) return true;
        String type = declared.trim().toLowerCase(Locale.ROOT);
        return type.equals(actual) || type.equals("application/octet-stream")
                || type.equals("image/*") || (actual.equals("image/jpeg") && type.equals("image/jpg"));
    }

    static boolean validDimensions(int width, int height) {
        return width > 0 && height > 0 && width <= 4096 && height <= 4096
                && (long) width * height <= 16_000_000;
    }

    static boolean isId(String id) {
        return id != null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    static String resourceId(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getRawPath(), query = uri.getRawQuery();
            if (!"https".equals(uri.getScheme()) || !"appassets.androidplatform.net".equals(uri.getRawAuthority())
                    || uri.getRawFragment() != null || path == null || !path.startsWith("/media/")) return null;
            String id = path.substring("/media/".length());
            return isId(id) && (query == null || "thumb=1".equals(query) || query.matches("run=[0-9]+")) ? id : null;
        } catch (IllegalArgumentException | NullPointerException error) { return null; }
    }

    static boolean deleteCopies(File directory, String id) {
        if (!isId(id)) throw new IllegalArgumentException("Invalid media ID");
        File image = new File(directory, id), thumbnail = new File(directory, id + ".png");
        boolean imageRemoved = !image.exists() || image.isFile() && image.delete();
        boolean thumbnailRemoved = !thumbnail.exists() || thumbnail.isFile() && thumbnail.delete();
        return imageRemoved && thumbnailRemoved;
    }

    static int validateGif(InputStream input) throws IOException {
        DataInputStream data = new DataInputStream(input);
        try {
            byte[] header = new byte[13];
            data.readFully(header);
            if (!"image/gif".equals(mime(header))) throw new InvalidMedia("invalid");
            int width = littleEndian(header, 6), height = littleEndian(header, 8);
            long canvas = (long) width * height;
            if (!validDimensions(width, height) || canvas > 4_000_000) throw new InvalidMedia("dimensions");
            skipColors(data, header[10] & 255);
            int frames = 0;
            while (true) {
                int marker = data.readUnsignedByte();
                if (marker == 0x3b) {
                    if (frames == 0 || data.read() != -1) throw new InvalidMedia("invalid");
                    return frames;
                }
                if (marker == 0x21) {
                    data.readUnsignedByte();
                    skipBlocks(data);
                } else if (marker == 0x2c) {
                    byte[] frame = new byte[9];
                    data.readFully(frame);
                    int left = littleEndian(frame, 0), top = littleEndian(frame, 2);
                    int frameWidth = littleEndian(frame, 4), frameHeight = littleEndian(frame, 6);
                    if (frameWidth == 0 || frameHeight == 0 || left + frameWidth > width
                            || top + frameHeight > height) throw new InvalidMedia("invalid");
                    if (++frames > 300 || frames * canvas > 120_000_000) throw new InvalidMedia("dimensions");
                    skipColors(data, frame[8] & 255);
                    int codeSize = data.readUnsignedByte();
                    if (codeSize < 2 || codeSize > 8) throw new InvalidMedia("invalid");
                    if (skipBlocks(data) == 0) throw new InvalidMedia("invalid");
                } else throw new InvalidMedia("invalid");
            }
        } catch (EOFException error) {
            throw new InvalidMedia("invalid");
        }
    }

    private static int littleEndian(byte[] bytes, int offset) {
        return (bytes[offset] & 255) | ((bytes[offset + 1] & 255) << 8);
    }

    private static void skipColors(DataInputStream data, int flags) throws IOException {
        if ((flags & 128) != 0) data.readFully(new byte[3 * (1 << ((flags & 7) + 1))]);
    }

    private static int skipBlocks(DataInputStream data) throws IOException {
        int size, total = 0;
        byte[] block = new byte[255];
        while ((size = data.readUnsignedByte()) != 0) {
            data.readFully(block, 0, size);
            total += size;
        }
        return total;
    }

    static void copy(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count == 0) {
                int value = input.read();
                if (value == -1) break;
                buffer[0] = (byte) value;
                count = 1;
            }
            if (count > limit - total) throw new InvalidMedia("too_large");
            output.write(buffer, 0, count);
            total += count;
        }
    }
}
