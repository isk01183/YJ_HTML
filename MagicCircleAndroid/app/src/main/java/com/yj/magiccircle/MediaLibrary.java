package com.yj.magiccircle;

import android.content.Context;
import android.annotation.SuppressLint;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.os.SystemClock;
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
    private static final int SCHEMA_VERSION = 2;
    private static final int ACTIVATION_REVISION = 1;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    @SuppressLint("StaticFieldLeak") // The singleton retains only the application context.
    private static MediaLibrary instance;
    private final Context context;
    private final File directory;
    private final AtomicFile manifest;
    private final AtomicFile recovery;
    private List<Item> media = new ArrayList<>();
    private Set<String> hidden = new LinkedHashSet<>();
    private Set<String> pendingDeletes = new LinkedHashSet<>();
    private List<Tab> tabs = new ArrayList<>();
    private String selected;
    private String migrationNotice = "";
    private boolean readable = true;
    private long initializationMillis;

    static synchronized MediaLibrary get(Context context) {
        if (instance == null) instance = new MediaLibrary(context.getApplicationContext());
        return instance;
    }

    private MediaLibrary(Context context) {
        this(context, context.getNoBackupFilesDir(), legacySelection(context));
    }

    MediaLibrary(Context context, File storageRoot, String legacySelected) {
        this(context, storageRoot, legacySelected,
                new AtomicFile(new File(storageRoot, "media-library.json")),
                new AtomicFile(new File(storageRoot, "media-library.v1-recovery.json")));
    }

    MediaLibrary(Context context, File storageRoot, String legacySelected,
            AtomicFile manifest, AtomicFile recovery) {
        long started = SystemClock.elapsedRealtime();
        this.context = context;
        this.directory = new File(storageRoot, "imported-media");
        this.manifest = manifest;
        this.recovery = recovery;
        selected = LibraryPolicy.selectionAfterMigration(legacySelected, java.util.Collections.emptySet());
        hidden = LibraryPolicy.hiddenForRevision(0, java.util.Collections.emptySet());
        migrationNotice = selected.equals(legacySelected) ? "" : "selection_reset";
        try {
            if (!exists(manifest)) {
                save(media, hidden, selected, pendingDeletes, tabs, migrationNotice);
            } else {
                byte[] original = read(manifest);
                JSONObject saved = new JSONObject(new String(original, StandardCharsets.UTF_8));
                int version = saved.getInt("version");
                State state;
                if (version == 1) {
                    state = readV1(saved);
                    backupV1(original);
                    Set<String> ids = mediaIds(state.media);
                    String migrated = LibraryPolicy.selectionAfterMigration(state.selected, ids);
                    state = new State(state.media, LibraryPolicy.hiddenForRevision(0, state.hidden), migrated,
                            state.pendingDeletes, new ArrayList<>(),
                            migrated.equals(state.selected) ? "" : "selection_reset");
                    save(state.media, state.hidden, state.selected, state.pendingDeletes,
                            state.tabs, state.migrationNotice);
                } else if (version == SCHEMA_VERSION) {
                    state = readV2(saved);
                    apply(state);
                } else {
                    throw new JSONException("Unknown library version");
                }
            }
        } catch (IOException | JSONException error) {
            // Keep every private file if a damaged manifest needs recovery; do not overwrite it.
            readable = false;
        } finally {
            initializationMillis = SystemClock.elapsedRealtime() - started;
        }
    }

    private static String legacySelection(Context context) {
        android.content.SharedPreferences preferences =
                context.getSharedPreferences("magic_circle", Context.MODE_PRIVATE);
        return ThemeSelection.initialSelection(preferences.contains("selected_style"),
                preferences.getString("selected_style", null));
    }

    private static boolean exists(AtomicFile file) {
        return file.getBaseFile().exists() || new File(file.getBaseFile() + ".bak").exists();
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

    static final class Tab {
        final String id, name;
        final Set<String> members;
        Tab(String id, String name, Set<String> members) {
            this.id = id;
            this.name = name;
            this.members = new LinkedHashSet<>(members);
        }
        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("name", name)
                    .put("members", new JSONArray(members));
        }
    }

    private static final class State {
        final List<Item> media;
        final Set<String> hidden;
        final String selected;
        final Set<String> pendingDeletes;
        final List<Tab> tabs;
        final String migrationNotice;
        State(List<Item> media, Set<String> hidden, String selected, Set<String> pendingDeletes,
                List<Tab> tabs, String migrationNotice) {
            this.media = media;
            this.hidden = hidden;
            this.selected = selected;
            this.pendingDeletes = pendingDeletes;
            this.tabs = tabs;
            this.migrationNotice = migrationNotice;
        }
    }

    private State readV1(JSONObject saved) throws JSONException {
        List<Item> entries = readMedia(saved.getJSONArray("media"));
        Set<String> ids = mediaIds(entries);
        Set<String> hiddenIds = readBuiltIns(saved.getJSONArray("hidden"), "hidden");
        Set<String> deletes = readDeletes(saved.optJSONArray("pendingDeletes"),
                saved.has("pendingDeletes"), ids);
        String selection = saved.getString("selected");
        if (!validSelection(selection, ids)) throw new JSONException("Invalid selection");
        return new State(entries, hiddenIds, selection, deletes, new ArrayList<>(), "");
    }

    private State readV2(JSONObject saved) throws JSONException {
        if (saved.getInt("activationRevision") != ACTIVATION_REVISION) {
            throw new JSONException("Unknown activation revision");
        }
        List<Item> entries = readMedia(saved.getJSONArray("media"));
        Set<String> ids = mediaIds(entries);
        Set<String> hiddenIds = readBuiltIns(saved.getJSONArray("hidden"), "hidden");
        Set<String> deletes = readDeletes(saved.getJSONArray("pendingDeletes"), true, ids);
        String selection = saved.getString("selected");
        if (!validSelection(selection, ids) || hiddenIds.contains(selection)) {
            throw new JSONException("Invalid selection");
        }
        List<Tab> restoredTabs = readTabs(saved.getJSONArray("tabs"), ids);
        return new State(entries, hiddenIds, selection, deletes, restoredTabs,
                saved.getString("migrationNotice"));
    }

    private static List<Item> readMedia(JSONArray entries) throws JSONException {
        List<Item> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < entries.length(); index++) {
            JSONObject entry = entries.getJSONObject(index);
            String id = entry.getString("id"), name = entry.getString("name"), mime = entry.getString("mime");
            if (!MediaValidation.isId(id) || !supportedMime(mime) || !seen.add(id)
                    || !name.equals(safeName(name))) throw new JSONException("Invalid media entry");
            result.add(new Item(id, name, mime));
        }
        return result;
    }

    private static Set<String> readBuiltIns(JSONArray values, String label) throws JSONException {
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index < values.length(); index++) {
            String id = values.getString(index);
            if (!ThemeSelection.isValid(id) || !result.add(id)) {
                throw new JSONException("Invalid " + label + " entry");
            }
        }
        return result;
    }

    private static Set<String> readDeletes(JSONArray values, boolean required, Set<String> mediaIds)
            throws JSONException {
        if (values == null) {
            if (required) throw new JSONException("Invalid pending deletion list");
            return new LinkedHashSet<>();
        }
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index < values.length(); index++) {
            String id = values.getString(index);
            if (!MediaValidation.isId(id) || mediaIds.contains(id) || !result.add(id)) {
                throw new JSONException("Invalid pending deletion");
            }
        }
        return result;
    }

    private static List<Tab> readTabs(JSONArray values, Set<String> mediaIds) throws JSONException {
        List<Tab> result = new ArrayList<>();
        Set<String> ids = new HashSet<>(), names = new LinkedHashSet<>();
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.getJSONObject(index);
            String id = value.getString("id"), rawName = value.getString("name");
            String name;
            try { name = LibraryPolicy.tabName(rawName, names); }
            catch (IllegalArgumentException error) { throw new JSONException("Invalid tab name"); }
            if (!MediaValidation.isId(id) || !ids.add(id) || !name.equals(rawName)) {
                throw new JSONException("Invalid tab");
            }
            names.add(name);
            JSONArray memberValues = value.getJSONArray("members");
            Set<String> members = new LinkedHashSet<>();
            for (int memberIndex = 0; memberIndex < memberValues.length(); memberIndex++) {
                String member = memberValues.getString(memberIndex);
                if ((!ThemeSelection.isValid(member) && !mediaIds.contains(member)) || !members.add(member)) {
                    throw new JSONException("Invalid tab member");
                }
            }
            result.add(new Tab(id, name, members));
        }
        return result;
    }

    private static Set<String> mediaIds(List<Item> entries) {
        Set<String> result = new LinkedHashSet<>();
        for (Item item : entries) result.add(item.id);
        return result;
    }

    private static boolean validSelection(String selection, Set<String> mediaIds) {
        return "".equals(selection) || ThemeSelection.isValid(selection) || mediaIds.contains(selection);
    }

    private void apply(State state) {
        media = new ArrayList<>(state.media);
        hidden = new LinkedHashSet<>(state.hidden);
        selected = state.selected;
        pendingDeletes = new LinkedHashSet<>(state.pendingDeletes);
        tabs = copyTabs(state.tabs);
        migrationNotice = state.migrationNotice;
    }

    private static List<Tab> copyTabs(List<Tab> source) {
        List<Tab> result = new ArrayList<>();
        for (Tab tab : source) result.add(new Tab(tab.id, tab.name, tab.members));
        return result;
    }

    synchronized String selected() {
        if (!readable) return "";
        Set<String> unavailable = new HashSet<>(hidden);
        for (Item item : media) if (!file(item.id).isFile()) unavailable.add(item.id);
        return ThemeSelection.nextVisible(selected, order(), unavailable);
    }

    synchronized boolean available(String id) {
        if (!readable) return false;
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
        JSONArray tabEntries = new JSONArray();
        for (Tab tab : tabs) tabEntries.put(tab.json());
        return new JSONObject().put("selected", selected()).put("enabled", enabled)
                .put("language", language).put("hidden", new JSONArray(hidden)).put("media", entries)
                .put("tabs", tabEntries).put("activationRevision", ACTIVATION_REVISION)
                .put("migrationNotice", migrationNotice).put("initializationMillis", initializationMillis)
                .put("readable", readable);
    }

    synchronized void select(String id) throws IOException {
        requireReadable();
        if (available(id)) save(media, hidden, id, pendingDeletes, tabs, migrationNotice);
    }

    synchronized void setEnabled(String id, boolean enabled) throws IOException {
        requireReadable();
        if (!ThemeSelection.isValid(id)) throw new IllegalArgumentException("Unknown built-in design");
        Set<String> nextHidden = new LinkedHashSet<>(hidden);
        if (enabled) nextHidden.remove(id); else nextHidden.add(id);
        if (nextHidden.equals(hidden)) return;
        String nextSelected = selected();
        String notice = migrationNotice;
        if (!enabled && id.equals(nextSelected)) {
            Set<String> unavailable = new HashSet<>(nextHidden);
            for (Item item : media) if (!file(item.id).isFile()) unavailable.add(item.id);
            nextSelected = ThemeSelection.nextVisible(nextSelected, order(), unavailable);
            notice = "selection_changed";
        }
        save(media, nextHidden, nextSelected, pendingDeletes, tabs, notice);
    }

    synchronized String createTab(String name) throws IOException {
        requireReadable();
        String normalized = LibraryPolicy.tabName(name, tabNames(null));
        String id = UUID.randomUUID().toString();
        List<Tab> next = copyTabs(tabs);
        next.add(new Tab(id, normalized, java.util.Collections.emptySet()));
        save(media, hidden, selected(), pendingDeletes, next, migrationNotice);
        return id;
    }

    synchronized void renameTab(String id, String name) throws IOException {
        requireReadable();
        Tab current = tab(id);
        if (current == null) throw new IllegalArgumentException("Unknown tab");
        String normalized = LibraryPolicy.tabName(name, tabNames(id));
        List<Tab> next = new ArrayList<>();
        for (Tab value : tabs) next.add(value.id.equals(id)
                ? new Tab(id, normalized, value.members) : new Tab(value.id, value.name, value.members));
        save(media, hidden, selected(), pendingDeletes, next, migrationNotice);
    }

    synchronized void deleteTab(String id) throws IOException {
        requireReadable();
        if (tab(id) == null) throw new IllegalArgumentException("Unknown tab");
        List<Tab> next = new ArrayList<>();
        for (Tab value : tabs) if (!value.id.equals(id)) {
            next.add(new Tab(value.id, value.name, value.members));
        }
        save(media, hidden, selected(), pendingDeletes, next, migrationNotice);
    }

    synchronized void setTabMember(String tabId, String theme, boolean member) throws IOException {
        requireReadable();
        if (!ThemeSelection.isValid(theme) && find(theme) == null) {
            throw new IllegalArgumentException("Unknown design");
        }
        if (tab(tabId) == null) throw new IllegalArgumentException("Unknown tab");
        List<Tab> next = new ArrayList<>();
        for (Tab value : tabs) {
            Set<String> members = new LinkedHashSet<>(value.members);
            if (value.id.equals(tabId)) {
                if (member) members.add(theme); else members.remove(theme);
            }
            next.add(new Tab(value.id, value.name, members));
        }
        save(media, hidden, selected(), pendingDeletes, next, migrationNotice);
    }

    private List<String> tabNames(String excludedId) {
        List<String> names = new ArrayList<>();
        for (Tab value : tabs) if (!value.id.equals(excludedId)) names.add(value.name);
        return names;
    }

    private Tab tab(String id) {
        for (Tab value : tabs) if (value.id.equals(id)) return value;
        return null;
    }

    private void requireReadable() throws IOException {
        if (!readable) throw new IOException("Library manifest is unreadable");
    }

    static final class CleanupPending extends IOException {}

    synchronized void remove(String id) throws IOException {
        requireReadable();
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
        List<Tab> nextTabs = copyTabs(tabs);
        if (imported != null) for (Tab value : nextTabs) value.members.remove(id);
        save(remaining, nextHidden, next, nextDeletes, nextTabs, migrationNotice);
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
        if (!remaining.equals(pendingDeletes)) {
            save(media, hidden, selected(), remaining, tabs, migrationNotice);
        }
    }

    synchronized void restore() throws IOException {
        requireReadable();
        save(media, new LinkedHashSet<>(), selected().isEmpty() ? "classic" : selected(),
                pendingDeletes, tabs, migrationNotice);
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
                save(added, hidden, id, pendingDeletes, tabs, migrationNotice);
                saved = true;
            }
        } catch (SecurityException error) {
            throw new IOException("Document permission was lost", error);
        } finally {
            if (!saved && readable) {
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

    private static byte[] read(AtomicFile file) throws IOException {
        try (InputStream input = file.openRead(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            MediaValidation.copy(input, output, MAX_MANIFEST_BYTES);
            return output.toByteArray();
        }
    }

    private void backupV1(byte[] original) throws IOException {
        if (exists(recovery)) {
            if (!Arrays.equals(original, read(recovery))) {
                throw new IOException("Existing v1 recovery differs");
            }
            return;
        }
        writeAtomic(recovery, original, false);
    }

    private void save(List<Item> entries, Set<String> hiddenIds, String selection, Set<String> deletes,
            List<Tab> tabEntries, String notice) throws IOException {
        requireReadable();
        byte[] bytes;
        try {
            JSONArray items = new JSONArray();
            for (Item item : entries) items.put(item.json());
            JSONArray savedTabs = new JSONArray();
            for (Tab tab : tabEntries) savedTabs.put(tab.json());
            bytes = new JSONObject().put("version", SCHEMA_VERSION)
                    .put("activationRevision", ACTIVATION_REVISION).put("media", items)
                    .put("hidden", new JSONArray(hiddenIds)).put("selected", selection)
                    .put("pendingDeletes", new JSONArray(deletes))
                    .put("tabs", savedTabs).put("migrationNotice", notice)
                    .toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException error) { throw new IOException(error); }
        if (bytes.length > MAX_MANIFEST_BYTES) throw new IOException("Library is full");
        State checked;
        try { checked = readV2(new JSONObject(new String(bytes, StandardCharsets.UTF_8))); }
        catch (JSONException error) { throw new IOException("Invalid library state", error); }
        writeAtomic(manifest, bytes, true);
        apply(checked);
    }

    private void writeAtomic(AtomicFile file, byte[] bytes, boolean blocksOnUnknownCommit) throws IOException {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(bytes);
            output.getFD().sync();
            file.finishWrite(output);
            output = null;
            try {
                if (!Arrays.equals(bytes, read(file))) throw new IOException("Atomic write was not committed");
            } catch (IOException error) {
                if (blocksOnUnknownCommit) readable = false;
                throw error;
            }
        } catch (IOException error) {
            if (output != null) file.failWrite(output);
            throw error;
        }
    }
}
