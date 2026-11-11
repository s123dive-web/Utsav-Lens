package com.utsavlens.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.utsavlens.app.R
import com.utsavlens.app.ui.common.errorText
import com.utsavlens.core.AppError
import com.utsavlens.core.AppResult
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.core.secure.SecretStore
import com.utsavlens.core.settings.AppSettings
import com.utsavlens.core.settings.CloudSettings
import com.utsavlens.core.settings.SettingsStore
import com.utsavlens.data.db.AppDatabase
import com.utsavlens.engine.api.ApiKey
import com.utsavlens.engine.api.ImageEditProvider
import com.utsavlens.engine.api.ProviderConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

sealed interface KeyTest {
    data object Idle : KeyTest
    data object Running : KeyTest
    data object Ok : KeyTest
    data class Failed(val kind: AppError.Kind) : KeyTest
}

data class CloudUiState(val hasKey: Boolean = false, val test: KeyTest = KeyTest.Idle)

@HiltViewModel
class CloudSettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val secrets: SecretStore,
    private val providers: Map<String, @JvmSuppressWildcards ImageEditProvider>,
    private val dispatchers: DispatcherProvider,
    db: AppDatabase,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = store.settings
    val requestsToday: StateFlow<Int> = db.cloudUsageDao().observeDay(LocalDate.now().toString())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _ui = MutableStateFlow(CloudUiState())
    val ui: StateFlow<CloudUiState> = _ui.asStateFlow()

    val provider: ImageEditProvider? get() = providers[settings.value.cloud.providerId] ?: providers.values.firstOrNull()

    init { refreshKey() }

    private fun keyName(): String = SecretStore.imageKeyName(provider?.id ?: "gemini")

    private fun refreshKey() {
        viewModelScope.launch {
            val has = withContext(dispatchers.io) { secrets.has(keyName()) }
            _ui.update { it.copy(hasKey = has) }
        }
    }

    /** The key goes straight into the encrypted store; it is never kept in UI state or settings. */
    fun saveKey(key: String) {
        if (key.isBlank()) return
        viewModelScope.launch {
            withContext(dispatchers.io) { secrets.put(keyName(), key) }
            _ui.update { it.copy(hasKey = true, test = KeyTest.Idle) }
        }
    }

    fun removeKey() {
        viewModelScope.launch {
            withContext(dispatchers.io) { secrets.remove(keyName()) }
            _ui.update { CloudUiState() }
        }
    }

    /** Tests the typed key if given, otherwise the stored one. Makes one small metadata request, no photo. */
    fun testKey(typed: String) {
        val p = provider ?: return
        _ui.update { it.copy(test = KeyTest.Running) }
        viewModelScope.launch {
            val keyText = typed.trim().ifEmpty { withContext(dispatchers.io) { secrets.get(keyName()) }.orEmpty() }
            val cloud = settings.value.cloud
            val result = if (keyText.isEmpty()) AppResult.Failure(AppError(AppError.Kind.BAD_KEY))
            else p.testKey(ApiKey(keyText), ProviderConfig(cloud.baseUrl, cloud.modelId, 30))
            _ui.update { it.copy(test = if (result is AppResult.Failure) KeyTest.Failed(result.error.kind) else KeyTest.Ok) }
        }
    }

    fun updateCloud(transform: (CloudSettings) -> CloudSettings) {
        viewModelScope.launch { store.update { it.copy(cloud = transform(it.cloud)) } }
        _ui.update { it.copy(test = KeyTest.Idle) }
    }

    fun resetEndpoint() = updateCloud { it.copy(baseUrl = CloudSettings().baseUrl, modelId = CloudSettings().modelId) }
}

@Composable
fun CloudSettingsSection(vm: CloudSettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val today by vm.requestsToday.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val provider = vm.provider
    // Typed key lives only in this composition (not saveable on purpose: never written to a Bundle).
    var typedKey by remember { mutableStateOf("") }
    var baseUrl by remember(settings.cloud.baseUrl) { mutableStateOf(settings.cloud.baseUrl) }
    var modelId by remember(settings.cloud.modelId) { mutableStateOf(settings.cloud.modelId) }

    SettingsSection(stringResource(R.string.cloud_title)) {
        Text(stringResource(R.string.cloud_provider, provider?.displayName.orEmpty()), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(if (ui.hasKey) R.string.cloud_key_saved else R.string.cloud_key_missing), color = if (ui.hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        OutlinedTextField(
            value = typedKey, onValueChange = { typedKey = it.trim().take(200) },
            label = { Text(stringResource(R.string.cloud_key_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveKey(typedKey); typedKey = "" }, enabled = typedKey.isNotBlank(), modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Text(stringResource(R.string.cloud_key_save))
            }
            OutlinedButton(onClick = { vm.testKey(typedKey) }, enabled = (typedKey.isNotBlank() || ui.hasKey) && ui.test != KeyTest.Running, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Text(stringResource(R.string.cloud_key_test))
            }
        }
        when (val t = ui.test) {
            KeyTest.Idle -> Unit
            KeyTest.Running -> Text(stringResource(R.string.cloud_key_testing))
            KeyTest.Ok -> Text(stringResource(R.string.cloud_key_ok), color = MaterialTheme.colorScheme.primary)
            is KeyTest.Failed -> Text(stringResource(errorText(t.kind)), color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    val url = provider?.keyHelpUrl ?: return@TextButton
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.cloud_get_key)) }
            if (ui.hasKey) TextButton(onClick = vm::removeKey, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cloud_key_remove)) }
        }
        Text(stringResource(R.string.cloud_requests_today, today), style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(value = modelId, onValueChange = { modelId = it.trim().take(100) }, label = { Text(stringResource(R.string.cloud_model_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it.trim().take(200) }, label = { Text(stringResource(R.string.cloud_base_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.updateCloud { it.copy(baseUrl = baseUrl, modelId = modelId) } },
                enabled = (baseUrl != settings.cloud.baseUrl || modelId != settings.cloud.modelId) && baseUrl.startsWith("https://") && modelId.isNotBlank(),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.cloud_apply)) }
            TextButton(onClick = vm::resetEndpoint, modifier = Modifier.heightIn(min = 52.dp)) { Text(stringResource(R.string.cloud_reset)) }
        }
        Text(stringResource(R.string.cloud_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
