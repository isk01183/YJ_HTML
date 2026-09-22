package com.yj.magiccircle

import android.content.Context
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContextWrapper
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import android.util.Base64
import org.json.JSONObject

object LibraryStorageChecks {
    private const val V1 = """{"version":1,"media":[],"hidden":[],"selected":"classic","pendingDeletes":[]}"""

    fun run(context: Context) {
        migratesBeforeUseAndPersistsTabs(context)
        rejectsDamagedAndFutureState(context)
        rejectsBackupFailure(context)
        rejectsOversizeState(context)
        preservesStateBeforeReplacementFailure(context)
        blocksWritesAfterVerificationFailure(context)
        preservesImportedFilesAfterUncertainCommit(context)
    }

    private fun root(context: Context): File =
        File(context.cacheDir, "v113-storage-${UUID.randomUUID()}").apply { check(mkdirs()) }

    private fun migratesBeforeUseAndPersistsTabs(context: Context) {
        val root = root(context)
        val manifest = File(root, "media-library.json").apply { writeText(V1) }

        // This direct construction represents a service becoming the first process entry point.
        val first = MediaLibrary(context, root, "classic")
        check(first.selected() == "native-N01")
        check(!first.available("ref-C03"))
        first.setEnabled("ref-C03", true)
        val tab = first.createTab("  E\u0301toile  ")
        first.setTabMember(tab, "ref-C03", true)

        val reloaded = MediaLibrary(context, root, "classic")
        check(reloaded.available("ref-C03"))
        val state = reloaded.galleryState(false, "en")
        check(state.getInt("activationRevision") == 1)
        check(state.getString("migrationNotice").isNotEmpty())
        val savedTab = state.getJSONArray("tabs").getJSONObject(0)
        check(savedTab.getString("id") == tab)
        check(savedTab.getString("name") == "Étoile")
        check(savedTab.getJSONArray("members").getString(0) == "ref-C03")
        check(File(root, "media-library.v1-recovery.json").readText() == V1)
        check(JSONObject(manifest.readText()).getInt("version") == 2)

        reloaded.renameTab(tab, "Night")
        check(runCatching { reloaded.createTab("Night") }.exceptionOrNull() is IllegalArgumentException)
        reloaded.deleteTab(tab)
        check(MediaLibrary(context, root, "classic").galleryState(false, "en")
            .getJSONArray("tabs").length() == 0)
    }

    private fun rejectsDamagedAndFutureState(context: Context) {
        listOf("not-json", """{"version":99}""").forEach { bytes ->
            val root = root(context)
            val manifest = File(root, "media-library.json").apply { writeText(bytes) }
            val library = MediaLibrary(context, root, "classic")
            check(!library.isReadable())
            check(manifest.readText() == bytes)
            check(!File(root, "media-library.v1-recovery.json").exists())
        }
    }

    private fun rejectsBackupFailure(context: Context) {
        val root = root(context)
        val manifestFile = File(root, "media-library.json").apply { writeText(V1) }
        val recoveryFile = File(root, "media-library.v1-recovery.json")
        val library = MediaLibrary(context, root, "classic", AtomicFile(manifestFile),
            StartFailAtomicFile(recoveryFile))
        check(!library.isReadable())
        check(manifestFile.readText() == V1)
        check(!recoveryFile.exists())
    }

    private fun rejectsOversizeState(context: Context) {
        val root = root(context)
        val manifest = File(root, "media-library.json").apply {
            writeBytes(ByteArray(1024 * 1024 + 1) { 'x'.code.toByte() })
        }
        val library = MediaLibrary(context, root, "classic")
        check(!library.isReadable())
        check(manifest.length() == 1024L * 1024 + 1)
    }

    private fun preservesStateBeforeReplacementFailure(context: Context) {
        val root = root(context)
        MediaLibrary(context, root, "classic")
        val manifestFile = File(root, "media-library.json")
        val before = manifestFile.readBytes()
        val library = MediaLibrary(context, root, "classic", StartFailAtomicFile(manifestFile),
            AtomicFile(File(root, "media-library.v1-recovery.json")))
        check(runCatching { library.setEnabled("ref-C03", true) }.exceptionOrNull() is IOException)
        check(!library.available("ref-C03"))
        check(manifestFile.readBytes().contentEquals(before))
        check(library.isReadable())
    }

    private fun blocksWritesAfterVerificationFailure(context: Context) {
        val root = root(context)
        MediaLibrary(context, root, "classic")
        val manifestFile = File(root, "media-library.json")
        val atomic = VerifyFailAtomicFile(manifestFile)
        val library = MediaLibrary(context, root, "classic", atomic,
            AtomicFile(File(root, "media-library.v1-recovery.json")))
        check(runCatching { library.setEnabled("ref-C03", true) }.exceptionOrNull() is IOException)
        val committed = manifestFile.readBytes()
        check(!library.isReadable())
        check(runCatching { library.createTab("Blocked") }.exceptionOrNull() is IOException)
        check(manifestFile.readBytes().contentEquals(committed))
    }

    private fun preservesImportedFilesAfterUncertainCommit(context: Context) {
        if (Build.VERSION.SDK_INT < 29) return
        val root = root(context)
        MediaLibrary(context, root, "classic")
        val source = File(root, "source.png").apply {
            writeBytes(Base64.decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZQAAAABJRU5ErkJggg==",
                Base64.DEFAULT))
        }
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "image/png"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?) =
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("source.png")) }
            override fun openFile(uri: Uri, mode: String) =
                ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            override fun insert(uri: Uri, values: ContentValues?) = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?,
                selectionArgs: Array<out String>?) = 0
        }
        val resolver = ContentResolver.wrap(provider)
        val wrapped = object : ContextWrapper(context) {
            override fun getContentResolver() = resolver
        }
        val manifestFile = File(root, "media-library.json")
        val library = MediaLibrary(wrapped, root, "classic", VerifyFailAtomicFile(manifestFile),
            AtomicFile(File(root, "media-library.v1-recovery.json")))
        check(runCatching { library.importDocument(Uri.parse("content://v113/source")) }
            .exceptionOrNull() is IOException)
        check(!library.isReadable())
        val id = JSONObject(manifestFile.readText()).getJSONArray("media").getJSONObject(0).getString("id")
        check(File(root, "imported-media/$id").isFile)
        check(File(root, "imported-media/$id.png").isFile)
    }

    private class StartFailAtomicFile(file: File) : AtomicFile(file) {
        override fun startWrite(): FileOutputStream = throw IOException("injected startWrite failure")
    }

    private class VerifyFailAtomicFile(file: File) : AtomicFile(file) {
        private var failReads = false
        override fun finishWrite(stream: FileOutputStream) {
            super.finishWrite(stream)
            failReads = true
        }
        override fun openRead() = if (failReads) {
            throw FileNotFoundException("injected verification failure")
        } else {
            super.openRead()
        }
    }
}
