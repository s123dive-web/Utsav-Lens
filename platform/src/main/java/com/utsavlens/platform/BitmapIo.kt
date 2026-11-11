package com.utsavlens.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max

/** Read-only access to the user's media. Nothing in this object can write to a source URI. */
object BitmapIo {

    class ImageTooLargeException(message: String) : IOException(message)

    /**
     * Decodes [uri] so that its longest edge is at most [maxEdgePx], applying EXIF orientation.
     * Large files are subsampled while decoding, so a 100 MP photo never needs full-size memory.
     */
    @Throws(IOException::class)
    fun decode(context: Context, uri: Uri, maxEdgePx: Int): Bitmap {
        var edge = maxEdgePx.coerceIn(64, 8192)
        repeat(3) {
            try {
                return decodeOnce(context, uri, edge)
            } catch (_: OutOfMemoryError) {
                System.gc()
                edge /= 2
            }
        }
        throw ImageTooLargeException("Not enough memory to decode image")
    }

    fun isVideo(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.getType(uri)?.startsWith("video/") == true || uri.toString().substringAfterLast('.').lowercase() in setOf("mp4", "mkv", "webm", "3gp", "mov")
    } catch (_: Exception) {
        false
    }

    /** Decodes a photo, or a representative frame when [uri] is a video. */
    @Throws(IOException::class)
    fun decodeAny(context: Context, uri: Uri, maxEdgePx: Int): Bitmap =
        if (isVideo(context, uri)) videoFrame(context, uri, maxEdgePx) ?: throw IOException("Cannot read video frame")
        else decode(context, uri, maxEdgePx)

    private fun decodeOnce(context: Context, uri: Uri, maxEdgePx: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("Cannot open image")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not a readable image")

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdgePx) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Cannot decode image")

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val longest = max(raw.width, raw.height)
        val scale = if (longest > maxEdgePx) maxEdgePx.toFloat() / longest else 1f
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        }
        if (scale != 1f) matrix.postScale(scale, scale)
        if (matrix.isIdentity) return ensureArgb(raw)
        val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (out !== raw) raw.recycle()
        return ensureArgb(out)
    }

    private fun ensureArgb(b: Bitmap): Bitmap {
        if (b.config == Bitmap.Config.ARGB_8888 && b.isMutable.not()) return b
        if (b.config == Bitmap.Config.ARGB_8888) return b
        val copy = b.copy(Bitmap.Config.ARGB_8888, false) ?: throw IOException("Cannot convert bitmap")
        b.recycle()
        return copy
    }

    /** A representative frame of a video (used for thumbnails and theme previews only). */
    fun videoFrame(context: Context, uri: Uri, maxEdgePx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            scaleDown(frame, maxEdgePx)
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    fun scaleDown(bitmap: Bitmap, maxEdgePx: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdgePx) return bitmap
        val s = maxEdgePx.toFloat() / longest
        val out = Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * s).toInt()), max(1, (bitmap.height * s).toInt()), true)
        if (out !== bitmap) bitmap.recycle()
        return out
    }

    /**
     * Re-encodes pixels only. The result carries no EXIF block at all, so location, device and
     * timestamp metadata of the original can never be uploaded.
     */
    fun encodeJpeg(bitmap: Bitmap, quality: Int = 92): ByteArray {
        val out = ByteArrayOutputStream(bitmap.width * bitmap.height / 4)
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) throw IOException("JPEG encode failed")
        return out.toByteArray()
    }
}
