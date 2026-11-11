package com.utsavlens.core

import com.google.common.truth.Truth.assertThat
import com.utsavlens.core.io.AtomicFileWriter
import com.utsavlens.core.io.Hashing
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AtomicFileWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun writesNewFile() {
        val target = File(tmp.root, "a/b/settings.json")
        AtomicFileWriter.writeText(target, "hello")
        assertThat(target.readText()).isEqualTo("hello")
        assertThat(target.parentFile!!.list()!!.toList()).containsExactly("settings.json")
    }

    @Test fun replacesExistingFileCompletely() {
        val target = tmp.newFile("data.bin")
        target.writeText("old content that is longer")
        AtomicFileWriter.writeText(target, "new")
        assertThat(target.readText()).isEqualTo("new")
    }

    @Test fun failureKeepsOldContentAndLeavesNoTempFile() {
        val target = tmp.newFile("keep.txt")
        target.writeText("original")
        assertThrows(IOException::class.java) {
            AtomicFileWriter.write(target) { out ->
                out.write("partial".toByteArray())
                throw IOException("disk full")
            }
        }
        assertThat(target.readText()).isEqualTo("original")
        assertThat(tmp.root.list()!!.toList()).containsExactly("keep.txt")
    }

    @Test fun failureOnNewFileLeavesNothingBehind() {
        val target = File(tmp.root, "never.txt")
        assertThrows(IllegalStateException::class.java) {
            AtomicFileWriter.write(target) { throw IllegalStateException("boom") }
        }
        assertThat(target.exists()).isFalse()
        assertThat(tmp.root.list()!!.toList()).isEmpty()
    }

    @Test fun commitPromotesFinishedFile() {
        val part = tmp.newFile("model.part")
        part.writeBytes(ByteArray(1000) { it.toByte() })
        val target = File(tmp.root, "model.bin")
        AtomicFileWriter.commit(part, target)
        assertThat(part.exists()).isFalse()
        assertThat(target.length()).isEqualTo(1000)
    }

    @Test fun cleansOnlyStaleTemps() {
        val stale = tmp.newFile("x.123.tmp")
        val fresh = tmp.newFile("y.456.tmp")
        val real = tmp.newFile("real.json")
        val now = System.currentTimeMillis()
        stale.setLastModified(now - 120_000)
        fresh.setLastModified(now)
        val removed = AtomicFileWriter.cleanStaleTemps(tmp.root, olderThanMs = 60_000, now = now)
        assertThat(removed).isEqualTo(1)
        assertThat(stale.exists()).isFalse()
        assertThat(fresh.exists()).isTrue()
        assertThat(real.exists()).isTrue()
    }

    @Test fun sha256MatchesKnownVector() {
        assertThat(Hashing.sha256Hex("abc".toByteArray()))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
}
