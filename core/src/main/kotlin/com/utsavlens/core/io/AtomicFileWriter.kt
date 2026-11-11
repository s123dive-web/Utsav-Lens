package com.utsavlens.core.io

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * The only way this app writes its own files: write to a temp file in the same directory, fsync,
 * then atomically rename over the target. A crash at any point leaves either the complete old
 * file or the complete new file, never a partial one.
 */
object AtomicFileWriter {

    private const val TMP_SUFFIX = ".tmp"

    @Throws(IOException::class)
    fun write(target: File, block: (OutputStream) -> Unit) {
        val dir = target.absoluteFile.parentFile ?: throw IOException("No parent directory for $target")
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException("Cannot create $dir")
        val tmp = File.createTempFile(target.name + ".", TMP_SUFFIX, dir)
        try {
            FileOutputStream(tmp).use { out ->
                block(out)
                out.flush()
                out.fd.sync()
            }
            commit(tmp, target)
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }

    @Throws(IOException::class)
    fun writeBytes(target: File, bytes: ByteArray) = write(target) { it.write(bytes) }

    @Throws(IOException::class)
    fun writeText(target: File, text: String) = writeBytes(target, text.toByteArray(Charsets.UTF_8))

    /** Atomically promotes an already complete, already synced file (e.g. a finished download). */
    @Throws(IOException::class)
    fun commit(completed: File, target: File) {
        try {
            Files.move(
                completed.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            throw IOException("Atomic rename not supported between $completed and $target", e)
        }
        syncDirectory(target.absoluteFile.parentFile)
    }

    /** Removes temp files left behind by a process that died mid-write. */
    fun cleanStaleTemps(dir: File, olderThanMs: Long = 60_000L, now: Long = System.currentTimeMillis()): Int {
        val stale = dir.listFiles { f -> f.isFile && f.name.endsWith(TMP_SUFFIX) && now - f.lastModified() >= olderThanMs }
        return stale?.count { it.delete() } ?: 0
    }

    private fun syncDirectory(dir: File?) {
        if (dir == null) return
        try {
            FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (_: IOException) {
            // Directory fsync is best effort: not every filesystem allows opening a directory.
        }
    }
}
