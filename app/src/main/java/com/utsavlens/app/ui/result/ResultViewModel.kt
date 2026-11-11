package com.utsavlens.app.ui.result

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utsavlens.app.nav.Routes
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.data.db.AppDatabase
import com.utsavlens.data.db.HistoryEntity
import com.utsavlens.engine.api.ResultNote
import com.utsavlens.platform.MediaStoreWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ResultUiState(
    val loading: Boolean = true,
    val items: List<HistoryEntity> = emptyList(),
    val selected: Int = 0,
    val noPersonFound: Boolean = false,
) {
    val current: HistoryEntity? get() = items.getOrNull(selected)
    val isVideo: Boolean get() = current?.mediaType == "VIDEO"
    val savedInGallery: Boolean get() = current?.outputUri?.startsWith("content://") == true
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val writer: MediaStoreWriter,
    private val dispatchers: DispatcherProvider,
    savedState: SavedStateHandle,
) : ViewModel() {
    val batchId: String = savedState.get<String>(Routes.ARG_JOB).orEmpty()
    private val _state = MutableStateFlow(ResultUiState())
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    init { reload() }

    private fun reload() {
        viewModelScope.launch {
            val (items, notes) = withContext(dispatchers.io) {
                db.historyDao().byBatch(batchId) to db.jobDao().get(batchId)?.failureReason.orEmpty()
            }
            _state.update {
                it.copy(
                    loading = false, items = items, selected = it.selected.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                    noPersonFound = ResultNote.NO_PERSON_FOUND.name in notes,
                )
            }
        }
    }

    fun select(index: Int) = _state.update { it.copy(selected = index.coerceIn(0, (it.items.size - 1).coerceAtLeast(0))) }

    /** Deletes OUR output file and its history row. The original (source_uri) is never touched. */
    fun deleteCurrent(onEmpty: () -> Unit) {
        val item = _state.value.current ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                writer.deleteOwnOutput(Uri.parse(item.outputUri))
                db.historyDao().delete(listOf(item.id))
            }
            val left = withContext(dispatchers.io) { db.historyDao().byBatch(batchId) }
            if (left.isEmpty()) onEmpty() else reload()
        }
    }

    fun shareIntent(): Intent? {
        val item = _state.value.current ?: return null
        val uri = shareableUri(context, item.outputUri) ?: return null
        val send = Intent(Intent.ACTION_SEND)
            .setType(if (item.mediaType == "VIDEO") "video/mp4" else "image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newRawUri(null, uri)
        return send
    }

    fun viewIntent(): Intent? {
        val item = _state.value.current ?: return null
        val uri = shareableUri(context, item.outputUri) ?: return null
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, if (item.mediaType == "VIDEO") "video/mp4" else "image/jpeg")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    companion object {
        /** content:// URIs are shared as they are; app-private files go through our FileProvider. */
        fun shareableUri(context: Context, uri: String): Uri? = try {
            val parsed = Uri.parse(uri)
            if (parsed.scheme == "file") FileProvider.getUriForFile(context, context.packageName + ".files", File(parsed.path!!)) else parsed
        } catch (_: Exception) {
            null
        }
    }
}
