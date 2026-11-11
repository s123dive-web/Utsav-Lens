package com.utsavlens.app.ui.progress

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.utsavlens.app.R
import com.utsavlens.app.jobs.JobController
import com.utsavlens.app.jobs.JobView
import com.utsavlens.app.nav.Routes
import com.utsavlens.app.ui.common.AppBar
import com.utsavlens.app.ui.common.MinTouch
import com.utsavlens.app.ui.common.errorText
import com.utsavlens.app.ui.common.stageText
import com.utsavlens.core.EngineIds
import com.utsavlens.core.job.JobState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProgressUi {
    data object Loading : ProgressUi
    data object Missing : ProgressUi
    data class Job(val view: JobView) : ProgressUi
}

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val jobs: JobController,
    savedState: SavedStateHandle,
) : ViewModel() {
    val jobId: String = savedState.get<String>(Routes.ARG_JOB).orEmpty()

    val state: StateFlow<ProgressUi> = kotlinx.coroutines.flow.flow {
        jobs.observe(jobId).collect { emit(if (it == null) ProgressUi.Missing else ProgressUi.Job(it)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUi.Loading)

    fun cancel() = jobs.cancel(jobId)

    fun retry(onNewJob: (String) -> Unit) = resubmit(null, onNewJob)

    fun useLite(onNewJob: (String) -> Unit) = resubmit(EngineIds.LITE, onNewJob)

    private fun resubmit(engine: String?, onNewJob: (String) -> Unit) {
        viewModelScope.launch { jobs.resubmit(jobId, engine)?.let(onNewJob) }
    }
}

@Composable
fun ProgressScreen(
    onDone: (jobId: String) -> Unit,
    onNewJob: (jobId: String) -> Unit,
    onClose: () -> Unit,
    vm: ProgressViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val view = (ui as? ProgressUi.Job)?.view
    LaunchedEffect(view?.state) {
        if (view?.state == JobState.SUCCEEDED) onDone(vm.jobId)
    }
    Scaffold(topBar = { AppBar(stringResource(R.string.progress_title), onBack = onClose) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            val wide = Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = MinTouch)
            when {
                ui is ProgressUi.Loading -> CircularProgressIndicator()
                view == null -> {
                    Text(stringResource(R.string.result_missing))
                    Button(onClick = onClose, modifier = wide) { Text(stringResource(R.string.close)) }
                }
                view.state == JobState.RUNNING || view.state == JobState.QUEUED || view.state == JobState.SUCCEEDED -> {
                    view.preview?.let { p ->
                        val bmp = remember(p) { Bitmap.createBitmap(p.argb, p.width, p.height, Bitmap.Config.ARGB_8888).asImageBitmap() }
                        Image(bmp, null, Modifier.size(220.dp))
                    }
                    Text(stringResource(stageText(view.stage)), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { view.percent.coerceIn(0, 100) / 100f }, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())
                    Text(stringResource(R.string.progress_percent, view.percent.coerceIn(0, 100)))
                    if (view.totalSteps > 1) Text(stringResource(R.string.progress_step, view.step, view.totalSteps), style = MaterialTheme.typography.bodySmall)
                    if (view.state != JobState.SUCCEEDED) {
                        OutlinedButton(onClick = vm::cancel, modifier = wide) { Text(stringResource(R.string.cancel)) }
                    }
                }
                else -> {
                    // FAILED or CANCELLED: never a dead end. Retry, fall back to LITE, or leave.
                    val cancelled = view.state == JobState.CANCELLED
                    Text(
                        stringResource(if (cancelled) R.string.progress_cancelled else R.string.progress_failed),
                        style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                    )
                    if (!cancelled) Text(stringResource(errorText(view.failure, view.interrupted)), textAlign = TextAlign.Center)
                    if (view.canFallbackToLite || (cancelled && view.engineId.isNotEmpty() && view.engineId != EngineIds.LITE)) {
                        Button(onClick = { vm.useLite(onNewJob) }, modifier = wide) { Text(stringResource(R.string.use_lite_instead)) }
                    }
                    FilledTonalButton(onClick = { vm.retry(onNewJob) }, modifier = wide) { Text(stringResource(R.string.retry)) }
                    OutlinedButton(onClick = onClose, modifier = wide) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}
