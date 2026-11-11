package com.utsavlens.app.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.utsavlens.app.R
import com.utsavlens.app.jobs.JobController
import com.utsavlens.app.jobs.LiveJob
import com.utsavlens.app.jobs.ModelJobs
import com.utsavlens.app.ui.common.errorText
import com.utsavlens.core.AppError
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.core.job.JobKind
import com.utsavlens.core.job.JobState
import com.utsavlens.core.settings.AppSettings
import com.utsavlens.core.settings.SettingsStore
import com.utsavlens.engine.api.Readiness
import com.utsavlens.engine.api.UnsupportedReason
import com.utsavlens.engine.local.LocalAiEngine
import com.utsavlens.engine.local.LocalModelManifest
import com.utsavlens.engine.local.ModelStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LocalAiUiState(
    val readiness: Readiness? = null,
    val diskBytes: Long = 0,
    val failure: AppError.Kind? = null,
)

@HiltViewModel
class LocalAiSettingsViewModel @Inject constructor(
    private val engine: LocalAiEngine,
    private val store: ModelStore,
    private val modelJobs: ModelJobs,
    private val jobs: JobController,
    private val settingsStore: SettingsStore,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsStore.settings
    val download: StateFlow<LiveJob?> = jobs.live
        .map { live -> live.values.firstOrNull { it.kind == JobKind.MODEL_DOWNLOAD } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _ui = MutableStateFlow(LocalAiUiState())
    val ui: StateFlow<LocalAiUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val (ready, disk) = withContext(dispatchers.io) { engine.isReady() to store.diskBytes() }
            _ui.update { it.copy(readiness = ready, diskBytes = disk) }
        }
    }

    fun startDownload() {
        _ui.update { it.copy(failure = null) }
        viewModelScope.launch {
            val jobId = modelJobs.startDownload()
            // Wait for the job to finish (success, failure or cancel), then refresh the status.
            val end = jobs.observe(jobId).filterNotNull().first { it.state.isTerminal }
            _ui.update { it.copy(failure = if (end.state == JobState.FAILED) end.failure else null) }
            refresh()
        }
    }

    fun cancelDownload() {
        download.value?.let { jobs.cancel(it.jobId) }
    }

    fun deleteModel() {
        viewModelScope.launch {
            withContext(dispatchers.io) { store.delete() }
            refresh()
        }
    }

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch { settingsStore.update { it.copy(local = it.local.copy(wifiOnly = value)) } }
    }

    fun setSteps(steps: Int) {
        viewModelScope.launch { settingsStore.update { it.copy(local = it.local.copy(steps = steps.coerceIn(2, 8))) } }
    }
}

@Composable
fun LocalAiSettingsSection(vm: LocalAiSettingsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val total = Formatter.formatShortFileSize(context, LocalModelManifest.totalBytes)

    SettingsSection(stringResource(R.string.local_title)) {
        Text(stringResource(R.string.local_experimental), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.local_model, LocalModelManifest.DISPLAY_NAME, total), style = MaterialTheme.typography.bodyMedium)

        val active = download
        when (val r = ui.readiness) {
            null -> Text(stringResource(R.string.loading))
            is Readiness.Unsupported -> Text(
                stringResource(
                    R.string.ready_unsupported,
                    when (r.reason) {
                        UnsupportedReason.LOW_RAM -> stringResource(R.string.unsupported_ram, r.detail)
                        UnsupportedReason.CHIPSET -> stringResource(R.string.unsupported_chipset, r.detail)
                        UnsupportedReason.ANDROID_VERSION -> stringResource(R.string.unsupported_android)
                        UnsupportedReason.STORAGE -> stringResource(R.string.unsupported_storage, r.detail)
                        UnsupportedReason.OTHER -> r.detail
                    },
                ),
                color = MaterialTheme.colorScheme.error,
            )
            Readiness.Ready -> Text(stringResource(R.string.local_ready, Formatter.formatShortFileSize(context, ui.diskBytes)), color = MaterialTheme.colorScheme.primary)
            else -> if (active == null) {
                val remaining = (r as? Readiness.NeedsDownload)?.totalBytes ?: LocalModelManifest.totalBytes
                Text(stringResource(R.string.local_not_downloaded))
                Button(onClick = vm::startDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.local_download, Formatter.formatShortFileSize(context, remaining)))
                }
            }
        }
        if (active != null) {
            Text(stringResource(R.string.local_downloading, active.percent))
            LinearProgressIndicator(progress = { active.percent / 100f }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = vm::cancelDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.cancel)) }
        }
        ui.failure?.let { kind ->
            Text(
                stringResource(if (kind == AppError.Kind.BAD_RESPONSE) R.string.local_checksum_failed else if (kind == AppError.Kind.NO_NETWORK && settings.local.wifiOnly) R.string.local_wifi_needed else errorText(kind)),
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { vm.setWifiOnly(!settings.local.wifiOnly) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.local_wifi_only), Modifier.weight(1f))
            Switch(checked = settings.local.wifiOnly, onCheckedChange = vm::setWifiOnly)
        }
        Text(stringResource(R.string.local_steps, settings.local.steps))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 4, 6, 8).forEach { n ->
                FilterChip(selected = settings.local.steps == n, onClick = { vm.setSteps(n) }, label = { Text(n.toString()) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        if (ui.diskBytes > 0 && active == null) {
            OutlinedButton(onClick = vm::deleteModel, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.local_delete, Formatter.formatShortFileSize(context, ui.diskBytes)))
            }
        }
        Text(stringResource(R.string.local_source, LocalModelManifest.SOURCE_PAGE), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
