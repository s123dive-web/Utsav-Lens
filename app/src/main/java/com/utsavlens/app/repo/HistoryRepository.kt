package com.utsavlens.app.repo

import android.net.Uri
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.data.db.AppDatabase
import com.utsavlens.data.db.HistoryEntity
import com.utsavlens.platform.MediaStoreWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * History operations. Deleting history removes rows and, only when asked, the OUTPUT files this
 * app created. There is deliberately no code here that touches `source_uri`.
 */
class HistoryRepository(
    private val db: AppDatabase,
    private val writer: MediaStoreWriter,
    private val dispatchers: DispatcherProvider,
) {
    fun observeAll(): Flow<List<HistoryEntity>> = db.historyDao().observeAll()

    suspend fun delete(ids: List<Long>, alsoDeleteOutputs: Boolean): Int = withContext(dispatchers.io) {
        if (ids.isEmpty()) return@withContext 0
        if (alsoDeleteOutputs) {
            db.historyDao().byIds(ids).forEach { writer.deleteOwnOutput(Uri.parse(it.outputUri)) }
        }
        db.historyDao().delete(ids)
    }

    suspend fun clear(alsoDeleteOutputs: Boolean): Int = withContext(dispatchers.io) {
        if (alsoDeleteOutputs) {
            db.historyDao().all().forEach { writer.deleteOwnOutput(Uri.parse(it.outputUri)) }
        }
        db.historyDao().clear()
    }
}
