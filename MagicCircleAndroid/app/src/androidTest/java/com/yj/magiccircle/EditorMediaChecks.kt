package com.yj.magiccircle

import android.content.Context
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object EditorMediaChecks {
    fun run(context: Context) {
        val root = File(context.cacheDir, "editor-media-${UUID.randomUUID()}").apply { check(mkdirs()) }
        MediaLibrary(context, root, "classic")
        val material = UUID.randomUUID().toString()
        val normal = UUID.randomUUID().toString()
        val manifest = File(root, "media-library.json")
        val saved = JSONObject(manifest.readText()).put("media", JSONArray()
            .put(JSONObject().put("id", material).put("name", "Material").put("mime", "image/png").put("editorOnly", true))
            .put(JSONObject().put("id", normal).put("name", "Old upload").put("mime", "image/png")))
            .put("selected", material)
        manifest.writeText(saved.toString())
        val directory = File(root, "imported-media").apply { check(mkdirs()) }
        val image = File(directory, material).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        File(directory, normal).writeBytes(byteArrayOf(4, 5, 6))
        var library = MediaLibrary(context, root, "classic")
        check(library.isReadable())
        val visible = library.galleryState(false, "en").getJSONArray("media")
        check(visible.length() == 1 && visible.getJSONObject(0).getString("id") == normal) {
            "Editor materials leaked into the public gallery"
        }
        check(library.selected() != material) { "Editor material became charging fallback" }
        library.select(normal)
        library.select(material)
        check(library.selected() == normal) { "Editor material accepted as a direct selection" }
        val tab = library.createTab("Uploads")
        check(runCatching { library.setTabMember(tab, material, true) }.isFailure)
        library.setTabMember(tab, normal, true)
        val scene = ScreenScene("scene-${UUID.randomUUID()}", "Artwork", ScenePurpose.CHARGING,
            listOf(ImageLayer("layer", material, .5f, .5f, 1f, 0f, false, true)))
        library.saveEditorDraft(EditorDraft(scene.id, scene, null))
        library = MediaLibrary(context, root, "classic")
        check(library.editorDraft(scene.id)?.scene == scene)
        check(library.items().size == 2 && library.find(material) != null)
        library.open(material, false).use { check(it.read() == 1) }
        check(runCatching { library.remove(material) }.isFailure && image.exists())
        library.saveScene(scene, null)
        library.select(scene.id)
        check(library.selected() == scene.id)
        library.removeScene(scene.id)
        val lease = library.leaseMedia(listOf(material))
        check(runCatching { library.remove(material) }.isFailure)
        lease.close()
        check(image.exists()) { "Removing a scene automatically deleted its material" }
        val reloaded = MediaLibrary(context, root, "classic")
        check(reloaded.galleryState(false, "en").getJSONArray("media").length() == 1)
        check(reloaded.find(normal) != null) { "Legacy upload without editorOnly was lost" }
        reloaded.select(normal)
        reloaded.setTabMember(tab, normal, true)
        reloaded.setEditorOnly(normal, true)
        check(reloaded.selected() != normal)
        check(reloaded.galleryState(false, "en").getJSONArray("media").length() == 0)
        check(reloaded.galleryState(false, "en").getJSONArray("tabs").getJSONObject(0)
            .getJSONArray("members").length() == 0)
        check(File(directory, normal).exists())
        val converted = MediaLibrary(context, root, "classic")
        check(converted.editorOnly(normal) && converted.editorOnly(material))
        converted.setEditorOnly(normal, false)
        check(converted.galleryState(false, "en").getJSONArray("media").length() == 1)
        check(!MediaLibrary(context, root, "classic").editorOnly(normal))
        val beforeFailure = manifest.readBytes()
        val failing = MediaLibrary(context, root, "classic", object : AtomicFile(manifest) {
            override fun startWrite(): FileOutputStream = throw IOException("injected startWrite failure")
        }, AtomicFile(File(root, "media-library.v1-recovery.json")))
        check(runCatching { failing.setEditorOnly(normal, true) }.exceptionOrNull() is IOException)
        check(!failing.editorOnly(normal) && manifest.readBytes().contentEquals(beforeFailure))
        check(File(directory, normal).exists() && image.exists())
        importKeepsEditorMaterialPrivate(context, root)
        val malformed = JSONObject(manifest.readText())
        malformed.getJSONArray("media").getJSONObject(0).put("editorOnly", "true")
        manifest.writeText(malformed.toString())
        val malformedBytes = manifest.readBytes()
        check(!MediaLibrary(context, root, "classic").isReadable())
        check(manifest.readBytes().contentEquals(malformedBytes) && image.exists())
    }

    private fun importKeepsEditorMaterialPrivate(context: Context, root: File) {
        val source = File(root, "source.png")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try { source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "image/png"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?) = null
            override fun openFile(uri: Uri, mode: String) = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            override fun insert(uri: Uri, values: ContentValues?) = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        val resolver = ContentResolver.wrap(provider)
        val wrapped = object : ContextWrapper(context) { override fun getContentResolver() = resolver }
        val importRoot = File(root, "imports").apply { check(mkdirs()) }
        val library = MediaLibrary(wrapped, importRoot, "classic")
        val before = library.selected()
        val material = library.importEditorDocument(Uri.parse("content://editor/source"))
        check(library.editorOnly(material) && library.selected() == before)
        check(library.galleryState(false, "en").getJSONArray("media").length() == 0)
        val background = library.importDocument(Uri.parse("content://editor/source"), false)
        check(!library.editorOnly(background) && library.selected() == before)
        check(library.galleryState(false, "en").getJSONArray("media").length() == 1)
        val reloaded = MediaLibrary(wrapped, importRoot, "classic")
        check(reloaded.editorOnly(material) && !reloaded.editorOnly(background))
        reloaded.open(material, false).use { check(it.read() == 137) }
    }
}
