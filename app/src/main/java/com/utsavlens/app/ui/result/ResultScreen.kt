package com.utsavlens.app.ui.result

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utsavlens.app.R
import com.utsavlens.app.ui.common.AppBar
import com.utsavlens.app.ui.common.UriImage

@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onMakeVideo: (batchId: String) -> Unit,
    vm: ResultViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val shareTitle = stringResource(R.string.share_title)

    fun launch(intent: Intent?, chooser: Boolean) {
        if (intent == null) return
        try {
            context.startActivity(if (chooser) Intent.createChooser(intent, shareTitle) else intent)
        } catch (_: Exception) {
            // No app can handle it: nothing to do, the file stays saved.
        }
    }

    Scaffold(topBar = { AppBar(stringResource(R.string.result_title), onBack) }) { padding ->
        val current = state.current
        if (state.loading || current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator() else Text(stringResource(R.string.result_missing))
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isVideo) {
                Box(Modifier.fillMaxWidth().weight(1f).clickable { launch(vm.viewIntent(), false) }, contentAlignment = Alignment.Center) {
                    UriImage(current.outputUri, Modifier.fillMaxSize(), maxEdgePx = 1280, isVideo = true)
                    Text("▶", style = MaterialTheme.typography.displayLarge, color = androidx.compose.ui.graphics.Color.White)
                }
            } else {
                CompareSlider(beforeUri = current.sourceUri, afterUri = current.outputUri, modifier = Modifier.fillMaxWidth().weight(1f))
            }
            if (state.items.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.items.forEachIndexed { i, item ->
                        val shape = RoundedCornerShape(8.dp)
                        UriImage(
                            item.outputUri,
                            Modifier.size(72.dp).clip(shape)
                                .border(if (i == state.selected) 3.dp else 0.dp, if (i == state.selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, shape)
                                .clickable { vm.select(i) },
                            maxEdgePx = 200, isVideo = item.mediaType == "VIDEO", contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(if (state.savedInGallery) R.string.result_saved else R.string.result_saved_private),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.noPersonFound) {
                    Text(stringResource(R.string.result_no_person), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { launch(vm.shareIntent(), true) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.share)) }
                    if (!state.isVideo) {
                        FilledTonalButton(onClick = { onMakeVideo(vm.batchId) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.result_make_video)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.delete)) }
                    OutlinedButton(onClick = onHome, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.result_new)) }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.result_delete_confirm)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteCurrent(onEmpty = onBack) }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
