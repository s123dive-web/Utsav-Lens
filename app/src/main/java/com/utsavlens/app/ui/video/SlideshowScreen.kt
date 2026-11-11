package com.utsavlens.app.ui.video

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.utsavlens.app.R
import com.utsavlens.app.jobs.VideoJobs
import com.utsavlens.app.nav.Routes
import com.utsavlens.app.ui.common.AppBar
import com.utsavlens.app.ui.common.MinTouch
import com.utsavlens.app.ui.common.UriImage
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.data.db.AppDatabase
import com.utsavlens.data.db.HistoryEntity
import com.utsavlens.video.SlideshowSpec
import com.utsavlens.video.SlideshowTimeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.random.Random

data class SlideshowUiState(
    val photos: List<HistoryEntity> = emptyList(),
    /** History ids in slideshow order. */
    val selected: List<Long> = emptyList(),
    val aspect: String = "9:16",
    val seconds: Float = 3f,
    val greeting: String = "",
    val audioUri: String? = null,
    val starting: Boolean = false,
)

@HiltViewModel
class SlideshowViewModel @Inject constructor(
    private val db: AppDatabase,
    private val videoJobs: VideoJobs,
    private val dispatchers: DispatcherProvider,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val batchId: String = savedState.get<String>(Routes.ARG_BATCH).orEmpty()
    private val _state = MutableStateFlow(SlideshowUiState())
    val state: StateFlow<SlideshowUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val photos = withContext(dispatchers.io) { db.historyDao().all().filter { it.mediaType == "PHOTO" } }
            val restored = savedState.get<LongArray>(KEY_SELECTED)?.toList()
            val initial = restored ?: photos.filter { it.batchId == batchId }.sortedBy { it.id }.map { it.id }
            _state.update {
                it.copy(
                    photos = photos, selected = initial.filter { id -> photos.any { p -> p.id == id } }.take(SlideshowTimeline.MAX_PHOTOS),
                    aspect = savedState.get<String>(KEY_ASPECT) ?: it.aspect,
                    seconds = savedState.get<Float>(KEY_SECONDS) ?: it.seconds,
                    greeting = savedState.get<String>(KEY_GREETING) ?: it.greeting,
                    audioUri = savedState.get<String>(KEY_AUDIO),
                )
            }
        }
    }

    private fun edit(transform: (SlideshowUiState) -> SlideshowUiState) {
        _state.update(transform)
        val s = _state.value
        savedState[KEY_SELECTED] = s.selected.toLongArray()
        savedState[KEY_ASPECT] = s.aspect
        savedState[KEY_SECONDS] = s.seconds
        savedState[KEY_GREETING] = s.greeting
        savedState[KEY_AUDIO] = s.audioUri
    }

    fun toggle(id: Long) = edit { s ->
        when {
            id in s.selected -> s.copy(selected = s.selected - id)
            s.selected.size >= SlideshowTimeline.MAX_PHOTOS -> s
            else -> s.copy(selected = s.selected + id)
        }
    }

    fun setAspect(aspect: String) = edit { it.copy(aspect = aspect) }
    fun setSeconds(seconds: Float) = edit { it.copy(seconds = seconds) }
    fun setGreeting(text: String) = edit { it.copy(greeting = text.take(80)) }
    fun setAudio(uri: String?) = edit { it.copy(audioUri = uri) }

    fun start(onStarted: (String) -> Unit) {
        val s = _state.value
        val chosen = s.selected.mapNotNull { id -> s.photos.firstOrNull { it.id == id } }
        if (chosen.isEmpty() || s.starting) return
        _state.update { it.copy(starting = true) }
        viewModelScope.launch {
            val jobId = videoJobs.startSlideshow(
                SlideshowSpec(
                    photoUris = chosen.map { it.outputUri }, themeId = chosen.first().themeId, aspect = s.aspect,
                    secondsPerPhoto = s.seconds, greeting = s.greeting.takeIf { it.isNotBlank() }, audioUri = s.audioUri,
                    seed = Random.nextLong(1, Long.MAX_VALUE / 2),
                ),
            )
            _state.update { it.copy(starting = false) }
            onStarted(jobId)
        }
    }

    companion object {
        private const val KEY_SELECTED = "selected"
        private const val KEY_ASPECT = "aspect"
        private const val KEY_SECONDS = "seconds"
        private const val KEY_GREETING = "greeting"
        private const val KEY_AUDIO = "audio"
    }
}

private fun persistRead(context: Context, uri: android.net.Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: Exception) {
    }
}

@Composable
fun SlideshowScreen(onBack: () -> Unit, onStarted: (jobId: String) -> Unit, vm: SlideshowViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persistRead(context, uri); vm.setAudio(uri.toString()) }
    }

    Scaffold(topBar = { AppBar(stringResource(R.string.slideshow_title), onBack) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.slideshow_pick, state.selected.size, SlideshowTimeline.MAX_PHOTOS), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("9:16", "1:1").forEach { a ->
                            FilterChip(selected = state.aspect == a, onClick = { vm.setAspect(a) }, label = { Text(a) }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                    Text(stringResource(R.string.slideshow_seconds, state.seconds))
                    Slider(value = state.seconds, onValueChange = vm::setSeconds, valueRange = SlideshowTimeline.MIN_SECONDS..SlideshowTimeline.MAX_SECONDS, modifier = Modifier.heightIn(min = 48.dp))
                    OutlinedTextField(
                        value = state.greeting, onValueChange = vm::setGreeting,
                        label = { Text(stringResource(R.string.opt_greeting)) }, maxLines = 2, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
                            Text(stringResource(if (state.audioUri == null) R.string.slideshow_add_audio else R.string.slideshow_change_audio))
                        }
                        if (state.audioUri != null) {
                            OutlinedButton(onClick = { vm.setAudio(null) }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.slideshow_remove_audio)) }
                        }
                    }
                    Text(stringResource(R.string.slideshow_audio_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { vm.start(onStarted) },
                        enabled = state.selected.isNotEmpty() && !state.starting,
                        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                    ) { Text(stringResource(R.string.slideshow_create)) }
                    if (state.photos.isEmpty()) Text(stringResource(R.string.slideshow_empty))
                }
            }
            items(state.photos, key = { it.id }) { photo ->
                val order = state.selected.indexOf(photo.id)
                val shape = RoundedCornerShape(10.dp)
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                        .border(if (order >= 0) 4.dp else 0.dp, if (order >= 0) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                        .clickable { vm.toggle(photo.id) },
                    contentAlignment = Alignment.TopEnd,
                ) {
                    UriImage(photo.outputUri, Modifier.fillMaxSize(), maxEdgePx = 260, contentScale = ContentScale.Crop)
                    if (order >= 0) {
                        Text(
                            (order + 1).toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
