package com.utsavlens.platform

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.utsavlens.core.io.AtomicFileWriter
import com.utsavlens.core.io.OutputNames
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

data class SavedMedia(val uri: Uri, val inGallery: Boolean)

/**
 * Creates NEW gallery entries under Pictures/UtsavLens and Movies/UtsavLens. It only ever inserts;
 * there is no code path that opens an existing user file for writing.
 *
 * Atomicity: on Android 10+ the row is inserted with IS_PENDING=1 (invisible to other apps),
 * written, fsynced, then published; a failure deletes the pending row. On Android 8/9 the bytes
 * go through [AtomicFileWriter] (temp file + fsync + rename) before the row is inserted.
 */
class MediaStoreWriter(private val context: Context) {

    @Throws(IOException::class)
    fun saveImage(displayName: String, mimeType: String = "image/jpeg", write: (OutputStream) -> Unit): SavedMedia =
        save(displayName, mimeType, isVideo = false, write = write)

    @Throws(IOException::class)
    fun saveVideo(displayName: String, source: File): SavedMedia =
        save(displayName, "video/mp4", isVideo = true) { out -> FileInputStream(source).use { it.copyTo(out, 1 shl 16) } }

    /** Writes into the app's private cache (thumbnails / previews). Never touches the gallery. */
    @Throws(IOException::class)
    fun saveToCache(subDir: String, fileName: String, write: (OutputStream) -> Unit): Uri {
        val file = File(File(context.cacheDir, subDir), fileName)
        AtomicFileWriter.write(file, write)
        return Uri.fromFile(file)
    }

    /** Deletes one of OUR outputs. Refuses anything that is not inside the UtsavLens album or app storage. */
    fun deleteOwnOutput(uri: Uri): Boolean = try {
        when (uri.scheme) {
            "file" -> {
                val f = File(uri.path ?: return false).canonicalFile
                val roots = listOfNotNull(context.cacheDir, context.filesDir, context.getExternalFilesDir(null)).map { it.canonicalPath }
                roots.any { f.path.startsWith(it) } && f.delete()
            }
            "content" -> if (isOwnGalleryEntry(uri)) context.contentResolver.delete(uri, null, null) > 0 else false
            else -> false
        }
    } catch (_: Exception) {
        false
    }

    private fun isOwnGalleryEntry(uri: Uri): Boolean {
        if (uri.authority != MediaStore.AUTHORITY) return false
        val column = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        return try {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
                c.moveToFirst() && (c.getString(0) ?: "").contains(OutputNames.ALBUM + "/")
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun save(displayName: String, mimeType: String, isVideo: Boolean, write: (OutputStream) -> Unit): SavedMedia =
        try {
            if (Build.VERSION.SDK_INT >= 29) saveScoped(displayName, mimeType, isVideo, write)
            else saveLegacy(displayName, mimeType, isVideo, write)
        } catch (e: SecurityException) {
            // Storage permission denied on Android 8/9: keep the result in app storage instead of failing.
            saveToAppStorage(displayName, isVideo, write)
        }

    private fun saveScoped(displayName: String, mimeType: String, isVideo: Boolean, write: (OutputStream) -> Unit): SavedMedia {
        val resolver = context.contentResolver
        val collection =
            if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val dir = (if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/" + OutputNames.ALBUM
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { out ->
                    write(out)
                    out.flush()
                    out.fd.sync()
                }
            } ?: throw IOException("Cannot open MediaStore entry")
            val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (resolver.update(uri, publish, null, null) != 1) throw IOException("Cannot publish MediaStore entry")
            return SavedMedia(uri, inGallery = true)
        } catch (t: Throwable) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            throw if (t is IOException || t is SecurityException) t else IOException("Save failed: ${t.javaClass.simpleName}", t)
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(displayName: String, mimeType: String, isVideo: Boolean, write: (OutputStream) -> Unit): SavedMedia {
        val base = Environment.getExternalStoragePublicDirectory(if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES)
        val dir = File(base, OutputNames.ALBUM)
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw SecurityException("No storage access")
        val file = File(dir, displayName)
        try {
            AtomicFileWriter.write(file, write)
        } catch (e: IOException) {
            if (!dir.canWrite()) throw SecurityException("No storage access")
            throw e
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
        }
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = context.contentResolver.insert(collection, values) ?: Uri.fromFile(file)
        return SavedMedia(uri, inGallery = true)
    }

    private fun saveToAppStorage(displayName: String, isVideo: Boolean, write: (OutputStream) -> Unit): SavedMedia {
        val base = context.getExternalFilesDir(if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES)
            ?: File(context.filesDir, "outputs")
        val file = File(base, displayName)
        AtomicFileWriter.write(file, write)
        return SavedMedia(Uri.fromFile(file), inGallery = false)
    }
}
