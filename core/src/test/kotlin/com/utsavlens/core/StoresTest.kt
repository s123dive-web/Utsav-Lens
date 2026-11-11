package com.utsavlens.core

import com.google.common.truth.Truth.assertThat
import com.utsavlens.core.secure.SecretCipher
import com.utsavlens.core.secure.SecretStore
import com.utsavlens.core.settings.AppSettings
import com.utsavlens.core.settings.SettingsStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoresTest {
    @get:Rule val tmp = TemporaryFolder()

    /** XOR "cipher" with an integrity byte: enough to prove plaintext never hits disk. */
    private class FakeCipher(private val k: Int = 0x5A) : SecretCipher {
        override fun encrypt(plain: ByteArray) = byteArrayOf(7) + plain.map { (it.toInt() xor k).toByte() }
        override fun decrypt(encrypted: ByteArray): ByteArray {
            require(encrypted.isNotEmpty() && encrypted[0] == 7.toByte()) { "tampered" }
            return encrypted.drop(1).map { (it.toInt() xor k).toByte() }.toByteArray()
        }
    }

    @Test fun settingsPersistAcrossInstancesAndStartFromDefaults() = runTest {
        val file = File(tmp.root, "settings/settings.json")
        val d = StandardTestDispatcher(testScheduler)
        val store = SettingsStore(file, d)
        assertThat(store.load()).isEqualTo(AppSettings())
        store.update { it.copy(language = "hi", cloud = it.cloud.copy(modelId = "m1")) }
        val reopened = SettingsStore(file, d)
        assertThat(reopened.load().language).isEqualTo("hi")
        assertThat(reopened.load().cloud.modelId).isEqualTo("m1")
        assertThat(file.parentFile!!.list()!!.toList()).containsExactly("settings.json")
    }

    @Test fun concurrentUpdatesAreNotLost() = runTest {
        val store = SettingsStore(File(tmp.root, "s.json"), StandardTestDispatcher(testScheduler))
        (1..50).map { async { store.update { it.copy(cloud = it.cloud.copy(timeoutSeconds = it.cloud.timeoutSeconds + 1)) } } }.awaitAll()
        assertThat(store.settings.value.cloud.timeoutSeconds).isEqualTo(AppSettings().cloud.timeoutSeconds + 50)
    }

    @Test fun corruptSettingsFileFallsBackToDefaults() = runTest {
        val file = tmp.newFile("bad.json").apply { writeText("\u0000\u0001 not json") }
        assertThat(SettingsStore(file, StandardTestDispatcher(testScheduler)).load()).isEqualTo(AppSettings())
    }

    @Test fun secretsAreEncryptedOnDiskAndRoundTrip() {
        val dir = File(tmp.root, "secure")
        val store = SecretStore(dir, FakeCipher())
        assertThat(store.get("image_gemini")).isNull()
        store.put("image_gemini", "  AIzaSecretValue123  ")
        assertThat(store.get("image_gemini")).isEqualTo("AIzaSecretValue123")
        dir.listFiles()!!.forEach { assertThat(String(it.readBytes(), Charsets.ISO_8859_1)).doesNotContain("AIzaSecret") }
        assertThat(store.remove("image_gemini")).isTrue()
        assertThat(store.has("image_gemini")).isFalse()
    }

    @Test fun undecryptableSecretIsTreatedAsAbsentAndWarningHasNoSecret() {
        val dir = File(tmp.root, "secure")
        val warnings = mutableListOf<String>()
        SecretStore(dir, FakeCipher()).put("image_gemini", "TopSecret")
        File(dir, "image_gemini.enc").writeBytes(byteArrayOf(1, 2, 3))
        val store = SecretStore(dir, FakeCipher(), AppLogger { _, m -> warnings += m })
        assertThat(store.get("image_gemini")).isNull()
        assertThat(File(dir, "image_gemini.enc").exists()).isFalse()
        assertThat(warnings.single()).doesNotContain("TopSecret")
    }

    @Test fun rejectsPathLikeNames() {
        val store = SecretStore(tmp.root, FakeCipher())
        assertThat(runCatching { store.put("../evil", "x") }.isFailure).isTrue()
    }
}
