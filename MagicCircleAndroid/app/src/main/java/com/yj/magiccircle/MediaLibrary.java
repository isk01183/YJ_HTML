package com.yj.magiccircle;

import android.content.Context;
import android.annotation.SuppressLint;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Private copies and one atomic manifest; source document URIs are never saved or deleted. */
final class MediaLibrary {
    static final String MEDIA_ORIGIN = "https://appassets.androidplatform.net/media/";
    @SuppressLint("StaticFieldLeak") // The singleton retains only the application context.
    private static MediaLibrary instance;
    private final Context context;
    private final File directory;
    private final AtomicFile manifest;
    private List<Item> media = new ArrayList<>();
    private Set<String> hidden = new LinkedHashSet<>();
    private Set<String> pendingDeletes = new LinkedHashSet<>();
    private String selected;
    private boolean readable = true;

    static synchronized MediaLibrary get(Context context) {
        if (instance == null) instance = new MediaLibrary(context.getApplicationContext());
        return instance;
    }

    private MediaLibrary(Context context) {
        this.context = context;
        directory = new File(context.getNoBackupFilesDir(), "imported-media");
        manifest = new AtomicFile(new File(context.getNoBackupFilesDir(), "media-library.json"));
        android.content.SharedPreferences preferences = context.getSharedPreferences("magic_circle", Context.MODE_PRIVATE);
        selected = ThemeSelection.initialSelection(preferences.contains("selected_style"),
                preferences.getString("selected_style", null));
        if (!manifest.getBaseFile().exists() && !new File(manifest.getBaseFile() + ".bak").exists()) return;
        try {
            JSONObject saved = new JSONObject(new String(readManifest(), StandardCharsets.UTF_8));
            if (saved.getInt("version") != 1) throw new JSONException("Unknown library version");
            JSONArray entries = saved.getJSONArray("media");
            List<Item> loaded = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < entries.length(); index++) {
                JSONObject entry = entries.getJSONObject(index);
                String id = entry.getString("id"), mime = entry.getString("mime");
                if (!MediaValidation.isId(id) || !supportedMime(mime) || !seen.add(id)) {
                    throw new JSONException("Invalid media entry");
                }
                loaded.add(new Item(id, safeName(entry.getString("name")), mime));
            }
            Set<String> restoredHidden = new LinkedHashSet<>();
            JSONArray hiddenIds = saved.getJSONArray("hidden");
            for (int index = 0; index < hiddenIds.length(); index++) {
                String id = hiddenIds.getString(index);
                if (!ThemeSelection.isValid(id)) throw new JSONException("Invalid hidden entry");
                restoredHidden.add(id);
            }
            Set<String> restoredDeletes = new LinkedHashSet<>();
            JSONArray deletes = saved.optJSONArray("pendingDeletes");
            if (saved.has("pendingDeletes") && deletes == null) throw new JSONException("Invalid pending deletion list");
            if (deletes != null) for (int index = 0; index < deletes.length(); index++) {
                String id = deletes.getString(index);
                if (!MediaValidation.isId(id) || seen.contains(id)) throw new JSONException("Invalid pending deletion");
                restoredDeletes.add(id);
            }
            selected = saved.getString("selected");
            media = loaded;
            hidden = restoredHidden;
            pendingDeletes = restoredDeletes;
            selected = selected();
        } catch (IOException | JSONException error) {
            // Keep every private file if a damaged manifest needs recovery; do not overwrite it.
            readable = false;
        }
    }

    static final class Item {
        final String id, name, mime;
        Item(String id, String name, String mime) {
            this.id = id;
            this.name = name;
            this.mime = mime;
        }
        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("name", name).put("mime", mime);
        }
    }

    synchronized String selected() {
        if (!readable) return "";
        Set<String> unavailable = new HashSet<>(hidden);
        for (Item item : media) if (!file(item.id).isFile()) unavailable.add(item.id);
        return ThemeSelection.nextVisible(selected, order(), unavailable);
    }

    synchronized boolean available(String id) {
        if (ThemeSelection.isValid(id)) return !hidden.contains(id);
        return find(id) != null && file(id).isFile();
    }

    synchronized boolean isReadable() { return readable; }

    synchronized Item find(String id) {
        if (!MediaValidation.isId(id)) return null;
        for (Item item : media) if (item.id.equals(id)) return item;
        return null;
    }

    synchronized JSONObject galleryState(boolean enabled, String language) throws JSONException {
        JSONArray entries = new JSONArray();
        for (Item item : media) if (file(item.id).isFile()) {
            entries.put(item.json().put("url", MEDIA_ORIGIN + item.id));
        }
        return new JSONObject().put("selected", selected()).put("enabled", enabled)
                .put("language", language).put("hidden", new JSONArray(hidden)).put("media", entries)
                .put("readable", readable);
    }

    synchronized void select(String id) throws IOException {
        if (available(id)) save(media, hidden, id, pendingDeletes);
    }

    static final class CleanupPending extends IOException {}

    synchronized void remove(String id) throws IOException {
        if (!available(id)) return;
        List<Item> remaining = new ArrayList<>(media);
        Set<String> nextHidden = new LinkedHashSet<>(hidden);
        Set<String> nextDeletes = new LinkedHashSet<>(pendingDeletes);
        Set<String> unavailable = new HashSet<>(hidden);
        unavailable.add(id);
        for (Item item : media) if (!file(item.id).isFile()) unavailable.add(item.id);
        String next = ThemeSelection.nextVisible(selected(), order(), unavailable);
        Item imported = find(id);
        if (imported == null) nextHidden.add(id); else {
            remaining.remove(imported);
            nextDeletes.add(id);
        }
        save(remaining, nextHidden, next, nextDeletes);
        if (imported != null) {
            try { retryPendingDeletes(); }
            catch (IOException error) { throw new CleanupPending(); }
            if (pendingDeletes.contains(id)) throw new CleanupPending();
        }
    }

    synchronized void retryPendingDeletes() throws IOException {
        if (!readable || pendingDeletes.isEmpty()) return;
        Set<String> remaining = new LinkedHashSet<>(pendingDeletes);
        for (String id : pendingDeletes) if (MediaValidation.deleteCopies(directory, id)) remaining.remove(id);
        if (!remaining.equals(pendingDeletes)) save(media, hidden, selected(), remaining);
    }

    synchronized void restore() throws IOException {
        save(media, new LinkedHashSet<>(), selected().isEmpty() ? "classic" : selected(), pendingDeletes);
    }

    void importDocument(Uri uri) throws IOException {
        if (uri == null || !"content".equals(uri.getScheme())) throw new MediaValidation.InvalidMedia("invalid");
        synchronized (this) {
            if (!readable) throw new IOException("Library manifest is unreadable");
        }
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create private directory");
        String id = UUID.randomUUID().toString();
        File pending = new File(directory, id + ".pending");
        File pendingThumbnail = new File(directory, id + ".thumb.pending");
        File destination = file(id);
        boolean saved = false;
        try {
            String declaredType = context.getContentResolver().getType(uri);
            String name = documentName(uri);
            if (!pending.createNewFile()) throw new IOException("Cannot create private copy");
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                    FileOutputStream output = new FileOutputStream(pending)) {
                if (input == null) throw new IOException("Document is unavailable");
                MediaValidation.copy(input, output, MediaValidation.MAX_BYTES);
                output.getFD().sync();
            }
            String mime = validate(pending, declaredType, pendingThumbnail);
            if (destination.exists() || !pending.renameTo(destination)) throw new IOException("Cannot finish private copy");
            if (!pendingThumbnail.renameTo(thumbnail(id))) throw new IOException("Cannot finish thumbnail");
            synchronized (this) {
                List<Item> added = new ArrayList<>(media);
                added.add(new Item(id, name, mime));
                save(added, hidden, id, pendingDeletes);
                saved = true;
            }
        } catch (SecurityException error) {
            throw new IOException("Document permission was lost", error);
        } finally {
            if (!saved) {
                pending.delete();
                destination.delete();
                pendingThumbnail.delete();
                thumbnail(id).delete();
            }
        }
    }

    synchronized InputStream open(String id, boolean thumb) throws IOException {
        if (find(id) == null) throw new IOException("Unknown media ID");
        return new FileInputStream(thumb ? thumbnail(id) : file(id));
    }

    private File file(String id) {
        if (!MediaValidation.isId(id)) throw new IllegalArgumentException("Invalid media ID");
        return new File(directory, id);
    }

    private File thumbnail(String id) {
        if (!MediaValidation.isId(id)) throw new IllegalArgumentException("Invalid media ID");
        return new File(directory, id + ".png");
    }

    private List<String> order() {
        List<String> order = new ArrayList<>(ThemeSelection.IDS);
        for (Item item : media) order.add(item.id);
        return order;
    }

    private String documentName(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return safeName(cursor.getString(0));
        } catch (RuntimeException ignored) {
            // A display name is optional; opening and validating bytes remains authoritative.
        }
        return "Image";
    }

    private static String safeName(String name) {
        if (name == null) return "Image";
        String clean = name.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
        return clean.isEmpty() ? "Image" : clean.substring(0, Math.min(clean.length(), 120));
    }

    private static boolean supportedMime(String mime) {
        return "image/gif".equals(mime) || "image/png".equals(mime) || "image/jpeg".equals(mime);
    }

    private static String validate(File file, String declared, File thumbnail) throws IOException {
        byte[] header = new byte[12];
        int count;
        try (FileInputStream input = new FileInputStream(file)) { count = input.read(header); }
        String mime = MediaValidation.mime(Arrays.copyOf(header, Math.max(0, count)));
        if (!MediaValidation.matchesType(mime, declared)) throw new MediaValidation.InvalidMedia("invalid");
        if ("image/gif".equals(mime)) {
            try (FileInputStream input = new FileInputStream(file)) { MediaValidation.validateGif(input); }
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (!MediaValidation.validDimensions(options.outWidth, options.outHeight)) {
            throw new MediaValidation.InvalidMedia("dimensions");
        }
        if (!mime.equals(options.outMimeType)) throw new MediaValidation.InvalidMedia("invalid");
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (decoded == null) throw new MediaValidation.InvalidMedia("invalid");
        Bitmap small = decoded;
        try {
            int longest = Math.max(decoded.getWidth(), decoded.getHeight());
            if (longest > 320) small = Bitmap.createScaledBitmap(decoded,
                    Math.max(1, decoded.getWidth() * 320 / longest),
                    Math.max(1, decoded.getHeight() * 320 / longest), true);
            try (FileOutputStream output = new FileOutputStream(thumbnail)) {
                if (!small.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Cannot create thumbnail");
                output.getFD().sync();
            }
        } finally {
            if (small != decoded) small.recycle();
            decoded.recycle();
        }
        return mime;
    }

    private byte[] readManifest() throws IOException {
        try (InputStream input = manifest.openRead(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            MediaValidation.copy(input, output, 1024 * 1024);
            return output.toByteArray();
        }
    }

    private void save(List<Item> entries, Set<String> hiddenIds, String selection, Set<String> deletes) throws IOException {
        if (!readable) throw new IOException("Library manifest is unreadable");
        byte[] bytes;
        try {
            JSONArray items = new JSONArray();
            for (Item item : entries) items.put(item.json());
            bytes = new JSONObject().put("version", 1).put("media", items)
                    .put("hidden", new JSONArray(hiddenIds)).put("selected", selection)
                    .put("pendingDeletes", new JSONArray(deletes))
                    .toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException error) { throw new IOException(error); }
        if (bytes.length > 1024 * 1024) throw new IOException("Library is full");
        FileOutputStream output = null;
        try {
            output = manifest.startWrite();
            output.write(bytes);
            output.getFD().sync();
            manifest.finishWrite(output);
            output = null;
            if (!Arrays.equals(bytes, readManifest())) throw new IOException("Manifest write was not committed");
        } catch (IOException error) {
            if (output != null) manifest.failWrite(output);
            throw error;
        }
        media = new ArrayList<>(entries);
        hidden = new LinkedHashSet<>(hiddenIds);
        pendingDeletes = new LinkedHashSet<>(deletes);
        selected = selection;
    }
}
