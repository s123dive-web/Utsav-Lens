package com.utsavlens.engine.local

import com.google.common.truth.Truth.assertThat
import com.utsavlens.core.io.Hashing
import com.utsavlens.engine.api.UnsupportedReason
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalGateAndStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val gb = 1024L * 1024 * 1024
    private val flagship = DeviceInfo(totalRamBytes = 11 * gb + gb / 2, supportedAbis = listOf("arm64-v8a"), cpuCores = 8, sdkInt = 34, freeStorageBytes = 60 * gb, socModel = "SM8650")

    @Test fun flagshipPasses() {
        assertThat(DeviceGate.check(flagship, LocalModelManifest.totalBytes, modelPresent = false)).isNull()
    }

    @Test fun eachLimitGivesItsOwnReason() {
        val bytes = LocalModelManifest.totalBytes
        assertThat(DeviceGate.check(flagship.copy(totalRamBytes = 7 * gb), bytes, false)!!.reason).isEqualTo(UnsupportedReason.LOW_RAM)
        assertThat(DeviceGate.check(flagship.copy(supportedAbis = listOf("armeabi-v7a")), bytes, false)!!.reason).isEqualTo(UnsupportedReason.CHIPSET)
        assertThat(DeviceGate.check(flagship.copy(cpuCores = 4), bytes, false)!!.reason).isEqualTo(UnsupportedReason.CHIPSET)
        assertThat(DeviceGate.check(flagship.copy(sdkInt = 26), bytes, false)!!.reason).isEqualTo(UnsupportedReason.ANDROID_VERSION)
        assertThat(DeviceGate.check(flagship.copy(freeStorageBytes = 2 * gb), bytes, false)!!.reason).isEqualTo(UnsupportedReason.STORAGE)
        // Storage only matters while the model still has to be downloaded.
        assertThat(DeviceGate.check(flagship.copy(freeStorageBytes = 2 * gb), bytes, true)).isNull()
    }

    @Test fun manifestIsPinnedWithRealChecksums() {
        assertThat(LocalModelManifest.files).hasSize(7)
        LocalModelManifest.files.forEach { f ->
            assertThat(f.sha256).matches("[0-9a-f]{64}")
            assertThat(f.bytes).isGreaterThan(0L)
        }
        assertThat(LocalModelManifest.totalBytes).isGreaterThan(4_000_000_000L)
        assertThat(LocalModelManifest.BASE_URL).startsWith("https://")
    }

    @Test fun storeIsCompleteOnlyAfterEveryFileIsVerified() {
        val a = "aaa".toByteArray(); val b = "bbbb".toByteArray()
        val manifest = listOf(ModelFile("x/a.bin", 3, Hashing.sha256Hex(a)), ModelFile("b.bin", 4, Hashing.sha256Hex(b)))
        val store = ModelStore(tmp.root, manifest, "m")
        assertThat(store.isComplete()).isFalse()
        store.fileFor(manifest[0]).apply { parentFile!!.mkdirs(); writeBytes(a) }
        store.markVerified(manifest[0])
        assertThat(store.isVerified(manifest[0])).isTrue()
        assertThat(store.isComplete()).isFalse()
        store.partFor(manifest[1]).writeBytes("bb".toByteArray())
        assertThat(store.downloadedBytes()).isEqualTo(5)
        store.fileFor(manifest[1]).writeBytes(b)
        // Present but not verified yet: still not complete.
        assertThat(store.isComplete()).isFalse()
        store.markVerified(manifest[1])
        assertThat(store.isComplete()).isTrue()
        // A truncated file invalidates completeness even though the marker exists.
        store.fileFor(manifest[1]).writeBytes("b".toByteArray())
        assertThat(store.isComplete()).isFalse()
        assertThat(store.delete()).isTrue()
        assertThat(store.dir.exists()).isFalse()
    }
}
