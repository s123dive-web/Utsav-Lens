package com.utsavlens.core.io

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object Hashing {
    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(file: File): String = file.inputStream().use(::sha256Hex)

    fun sha256Hex(bytes: ByteArray): String = sha256Hex(bytes.inputStream())
}
