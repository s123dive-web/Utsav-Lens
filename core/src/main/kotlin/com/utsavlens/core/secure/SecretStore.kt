package com.utsavlens.core.secure

import com.utsavlens.core.AppLogger
import com.utsavlens.core.io.AtomicFileWriter
import java.io.File

/** Authenticated encryption seam. The Android implementation uses a non-exportable Keystore key. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    /** Throws if the data was tampered with or the key is gone. */
    fun decrypt(encrypted: ByteArray): ByteArray
}

/**
 * Stores named secrets (API keys) as individual encrypted files in [dir]. The directory must be
 * excluded from backups. Values are never logged; a secret that cannot be decrypted (for example
 * after a restore onto another device) is treated as absent and removed.
 */
class SecretStore(
    private val dir: File,
    private val cipher: SecretCipher,
    private val logger: AppLogger = AppLogger.NONE,
) {
    private val lock = Any()

    fun put(name: String, secret: String) = synchronized(lock) {
        require(secret.isNotBlank()) { "secret is blank" }
        AtomicFileWriter.writeBytes(fileFor(name), cipher.encrypt(secret.trim().toByteArray(Charsets.UTF_8)))
    }

    fun get(name: String): String? = synchronized(lock) {
        val file = fileFor(name)
        if (!file.isFile) return null
        try {
            String(cipher.decrypt(file.readBytes()), Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            logger.warn("SecretStore", "Stored secret '$name' is unreadable and was removed (${e.javaClass.simpleName})")
            file.delete()
            null
        }
    }

    fun has(name: String): Boolean = get(name) != null

    fun remove(name: String): Boolean = synchronized(lock) { fileFor(name).delete() }

    private fun fileFor(name: String): File {
        require(NAME_REGEX.matches(name)) { "invalid secret name" }
        return File(dir, "$name.enc")
    }

    companion object {
        private val NAME_REGEX = Regex("^[a-z0-9_]{1,40}$")
        fun imageKeyName(providerId: String) = "image_$providerId"
        fun assistKeyName(providerId: String) = "assist_$providerId"
    }
}
