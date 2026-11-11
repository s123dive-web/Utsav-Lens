package com.utsavlens.app.ui.history

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.utsavlens.app.R
import com.utsavlens.app.repo.HistoryRepository
import com.utsavlens.app.ui.common.AppBar
import com.utsavlens.app.ui.common.UriImage
import com.utsavlens.data.db.HistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(private val history: HistoryRepository) : ViewModel() {
    val items: StateFlow<List<HistoryEntity>?> = history.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    fun toggle(id: Long) = _selected.update { if (id in it) it - id else it + id }
    fun clearSelection() = _selected.update { emptySet() }

    fun deleteSelected(alsoDeleteOutputs: Boolean) {
        val ids = _selected.value.toList()
        _selected.value = emptySet()
        viewModelScope.launch { history.delete(ids, alsoDeleteOutputs) }
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (batchId: String) -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    var selecting by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var alsoFiles by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppBar(stringResource(R.string.home_history), onBack) {
                if (!items.isNullOrEmpty()) {
                    if (selecting && selected.isNotEmpty()) {
                        TextButton(onClick = { confirm = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delete) + " (${selected.size})") }
                    }
                    TextButton(onClick = { selecting = !selecting; vm.clearSelection() }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(if (selecting) R.string.cancel else R.string.history_select))
                    }
                }
            }
        },
    ) { padding ->
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding))
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(stringResource(R.string.history_empty)) }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.id }) { item ->
                    val isSelected = item.id in selected
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                            .border(if (isSelected) 4.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                            .clickable { if (selecting) vm.toggle(item.id) else onOpen(item.batchId) },
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        UriImage(item.outputUri, Modifier.fillMaxSize(), maxEdgePx = 300, isVideo = item.mediaType == "VIDEO", contentScale = ContentScale.Crop)
                        if (item.mediaType == "VIDEO") Text("▶", color = Color.White, modifier = Modifier.padding(6.dp))
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.history_delete_body))
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { alsoFiles = !alsoFiles },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = alsoFiles, onCheckedChange = { alsoFiles = it })
                        Text(stringResource(R.string.history_delete_files))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { confirm = false; selecting = false; vm.deleteSelected(alsoFiles) }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
