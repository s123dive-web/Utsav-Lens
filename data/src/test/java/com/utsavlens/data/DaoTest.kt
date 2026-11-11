package com.utsavlens.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.utsavlens.data.db.AppDatabase
import com.utsavlens.data.db.HistoryEntity
import com.utsavlens.data.db.JobEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: AppDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun item(n: Int, batch: String = "b") = HistoryEntity(
        sourceUri = "content://src/$n", themeId = "holi", engineId = "lite", optionsJson = "{}",
        outputUri = "content://out/$n", mediaType = "PHOTO", batchId = batch, createdAt = n.toLong(),
    )

    @Test fun historyIsNewestFirstAndDeleteOnlyRemovesRows() = runBlocking {
        val dao = db.historyDao()
        val ids = (1..3).map { dao.insert(item(it)) }
        assertThat(dao.observeAll().first().map { it.outputUri })
            .containsExactly("content://out/3", "content://out/2", "content://out/1").inOrder()
        assertThat(dao.delete(listOf(ids[0]))).isEqualTo(1)
        assertThat(dao.count()).isEqualTo(2)
        assertThat(dao.clear()).isEqualTo(2)
    }

    @Test fun cloudUsageIncrementsPerDayAndProvider() = runBlocking {
        val dao = db.cloudUsageDao()
        assertThat(dao.increment("2026-11-11", "gemini")).isEqualTo(1)
        assertThat(dao.increment("2026-11-11", "gemini")).isEqualTo(2)
        assertThat(dao.increment("2026-11-12", "gemini")).isEqualTo(1)
        assertThat(dao.observeDay("2026-11-11").first()).isEqualTo(2)
        assertThat(dao.observeDay("2000-01-01").first()).isEqualTo(0)
    }

    @Test fun jobLifecycle() = runBlocking {
        val dao = db.jobDao()
        dao.upsert(JobEntity("j1", "TRANSFORM", "{}", "RUNNING", createdAt = 1, updatedAt = 1))
        dao.upsert(JobEntity("j2", "TRANSFORM", "{}", "SUCCEEDED", createdAt = 1, updatedAt = 1))
        assertThat(dao.unfinished().map { it.id }).containsExactly("j1")
        assertThat(dao.progress("j1", 55, 2)).isEqualTo(1)
        assertThat(dao.progress("j2", 55, 2)).isEqualTo(0)
        dao.update("j1", "FAILED", 55, "interrupted", null, 3)
        assertThat(dao.get("j1")!!.failureReason).isEqualTo("interrupted")
        assertThat(dao.unfinished()).isEmpty()
    }
}
