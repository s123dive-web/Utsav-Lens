package com.utsavlens.engine.local

import com.utsavlens.core.io.AtomicFileWriter
import com.utsavlens.core.io.Hashing
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException

class ChecksumMismatchException(path: String) : IOException("checksum mismatch for $path")
class WifiRequiredException : IOException("Wi-Fi required")

/**
 * Resumable, checksum-verified model download.
 * - Bytes go to `<file>.part`; an interrupted download continues with an HTTP Range request.
 * - When a file is complete its SHA-256 is compared with the manifest; only then is it atomically
 *   renamed into place and recorded as verified. A mismatch deletes the partial file.
 * - [allowedNow] is polled so a Wi-Fi-only download stops as soon as the phone leaves Wi-Fi.
 */
class ModelDownloader(
    private val client: OkHttpClient,
    private val store: ModelStore,
    private val io: CoroutineDispatcher,
    private val manifest: List<ModelFile> = LocalModelManifest.files,
    private val baseUrl: String = LocalModelManifest.BASE_URL,
) {
    suspend fun downloadAll(allowedNow: () -> Boolean, onProgress: (doneBytes: Long, totalBytes: Long) -> Unit) = withContext(io) {
        val total = manifest.sumOf { it.bytes }
        var doneBefore = 0L
        for (file in manifest) {
            currentCoroutineContext().ensureActive()
            if (!store.isVerified(file)) {
                val base = doneBefore
                downloadOne(file, allowedNow) { fileBytes -> onProgress(base + fileBytes, total) }
            }
            doneBefore += file.bytes
            onProgress(doneBefore, total)
        }
    }

    private suspend fun downloadOne(file: ModelFile, allowedNow: () -> Boolean, onFileProgress: (Long) -> Unit) {
        val part = store.partFor(file)
        part.parentFile?.mkdirs()
        if (part.length() > file.bytes) part.delete()
        var existing = if (part.isFile) part.length() else 0L

        if (existing < file.bytes) {
            if (!allowedNow()) throw WifiRequiredException()
            val builder = Request.Builder().url(baseUrl + file.path)
            if (existing > 0) builder.header("Range", "bytes=$existing-")
            val call = client.newCall(builder.build())
            try {
                call.execute().use { response ->
                    if (response.code == 416) {
                        // Our partial file is not a valid prefix any more: start over next time.
                        part.delete()
                        throw IOException("range not satisfiable")
                    }
                    if (!response.isSuccessful) throw IOException("http ${response.code}")
                    val resumed = response.code == 206
                    if (!resumed) existing = 0L
                    FileOutputStream(part, resumed).use { out ->
                        val source = response.body.byteStream()
                        val buffer = ByteArray(1 shl 16)
                        var sinceCheck = 0L
                        while (true) {
                            val n = source.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            existing += n
                            sinceCheck += n
                            if (sinceCheck >= 4L * 1024 * 1024) {
                                sinceCheck = 0
                                currentCoroutineContext().ensureActive()
                                if (!allowedNow()) throw WifiRequiredException()
                                onFileProgress(existing)
                            }
                        }
                        out.flush()
                        out.fd.sync()
                    }
                }
            } finally {
                call.cancel()
            }
        }
        if (part.length() != file.bytes) throw IOException("incomplete download of ${file.path}")
        currentCoroutineContext().ensureActive()
        if (!Hashing.sha256Hex(part).equals(file.sha256, ignoreCase = true)) {
            part.delete()
            throw ChecksumMismatchException(file.path)
        }
        AtomicFileWriter.commit(part, store.fileFor(file))
        store.markVerified(file)
        onFileProgress(file.bytes)
    }
}
