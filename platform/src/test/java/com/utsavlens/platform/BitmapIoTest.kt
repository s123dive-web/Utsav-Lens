package com.utsavlens.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.utsavlens.core.io.Hashing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File

/** Real JPEG encode/decode (native graphics) to prove the privacy and read-only guarantees. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapIoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A 40x20 JPEG that claims to be rotated 90 degrees and carries a GPS position. */
    private fun photoWithExif(): File {
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 80, 40)) }
        val file = File(context.cacheDir, "exif_source.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setLatLong(18.5204, 73.8567)
            setAttribute(ExifInterface.TAG_MAKE, "TestCam")
            saveAttributes()
        }
        assertThat(ExifInterface(file).latLong).isNotNull()
        return file
    }

    @Test fun decodeAppliesOrientationAndNeverTouchesTheOriginal() {
        val file = photoWithExif()
        val before = Hashing.sha256Hex(file)
        val modified = file.lastModified()
        val decoded = BitmapIo.decode(context, Uri.fromFile(file), 100)
        assertThat(decoded.width).isEqualTo(20)
        assertThat(decoded.height).isEqualTo(40)
        assertThat(Hashing.sha256Hex(file)).isEqualTo(before)
        assertThat(file.lastModified()).isEqualTo(modified)
    }

    @Test fun uploadBytesCarryNoExifLocationOrDeviceData() {
        val decoded = BitmapIo.decode(context, Uri.fromFile(photoWithExif()), 100)
        val bytes = BitmapIo.encodeJpeg(decoded, 90)
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        assertThat(exif.latLong).isNull()
        assertThat(exif.getAttribute(ExifInterface.TAG_MAKE)).isNull()
        assertThat(String(bytes, Charsets.ISO_8859_1)).doesNotContain("TestCam")
    }

    @Test fun largeImagesAreDownscaledWhileDecoding() {
        val big = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val file = File(context.cacheDir, "big.jpg")
        file.outputStream().use { big.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        val decoded = BitmapIo.decode(context, Uri.fromFile(file), 400)
        assertThat(maxOf(decoded.width, decoded.height)).isEqualTo(400)
        assertThat(decoded.width.toFloat() / decoded.height).isWithin(0.02f).of(4f / 3f)
    }
}
