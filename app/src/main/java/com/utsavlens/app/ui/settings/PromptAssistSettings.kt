package com.utsavlens.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.utsavlens.app.R
import com.utsavlens.app.di.AppModule
import com.utsavlens.app.ui.common.errorText
import com.utsavlens.core.AppError
import com.utsavlens.core.AppResult
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.core.secure.SecretStore
import com.utsavlens.core.settings.AppSettings
import com.utsavlens.core.settings.PromptAssistSettings
import com.utsavlens.core.settings.SettingsStore
import com.utsavlens.engine.api.ApiKey
import com.utsavlens.engine.api.PromptAssistProvider
import com.utsavlens.engine.api.ProviderConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AssistUiState(val hasOwnKey: Boolean = false, val hasAnyKey: Boolean = false, val test: KeyTest = KeyTest.Idle)

@HiltViewModel
class PromptAssistViewModel @Inject constructor(
    private val store: SettingsStore,
    private val secrets: SecretStore,
    val provider: PromptAssistProvider,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = store.settings
    private val _ui = MutableStateFlow(AssistUiState())
    val ui: StateFlow<AssistUiState> = _ui.asStateFlow()

    private val ownKey = SecretStore.assistKeyName(provider.id)
    private val sharedKey = SecretStore.imageKeyName(provider.id)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val (own, shared) = withContext(dispatchers.io) { secrets.has(ownKey) to secrets.has(sharedKey) }
            _ui.update { it.copy(hasOwnKey = own, hasAnyKey = own || shared) }
        }
    }

    /** Turning the feature on records the user's explicit consent to send the photo to the text model. */
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            store.update { s ->
                val marker = AppModule.ASSIST_CONSENT_PREFIX + provider.id
                val consents = if (enabled) (s.cloud.uploadConsentProviders + marker).distinct() else s.cloud.uploadConsentProviders - marker
                s.copy(promptAssist = s.promptAssist.copy(enabled = enabled), cloud = s.cloud.copy(uploadConsentProviders = consents))
            }
        }
    }

    fun saveKey(key: String) {
        if (key.isBlank()) return
        viewModelScope.launch {
            withContext(dispatchers.io) { secrets.put(ownKey, key) }
            _ui.update { it.copy(test = KeyTest.Idle) }
            refresh()
        }
    }

    fun removeKey() {
        viewModelScope.launch {
            withContext(dispatchers.io) { secrets.remove(ownKey) }
            refresh()
        }
    }

    fun testKey(typed: String) {
        _ui.update { it.copy(test = KeyTest.Running) }
        viewModelScope.launch {
            val keyText = typed.trim().ifEmpty { withContext(dispatchers.io) { secrets.get(ownKey) ?: secrets.get(sharedKey) }.orEmpty() }
            val a = settings.value.promptAssist
            val result = if (keyText.isEmpty()) AppResult.Failure(AppError(AppError.Kind.BAD_KEY))
            else provider.testKey(ApiKey(keyText), ProviderConfig(a.baseUrl, a.modelId, 30))
            _ui.update { it.copy(test = if (result is AppResult.Failure) KeyTest.Failed(result.error.kind) else KeyTest.Ok) }
        }
    }

    fun setModel(modelId: String) {
        viewModelScope.launch { store.update { it.copy(promptAssist = it.promptAssist.copy(modelId = modelId.ifBlank { PromptAssistSettings.DEFAULT_TEXT_MODEL })) } }
    }
}

@Composable
fun PromptAssistSection(vm: PromptAssistViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    var typedKey by remember { mutableStateOf("") }
    var modelId by remember(settings.promptAssist.modelId) { mutableStateOf(settings.promptAssist.modelId) }
    val enabled = settings.promptAssist.enabled

    SettingsSection(stringResource(R.string.assist_title)) {
        Text(stringResource(R.string.assist_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { if (enabled) vm.setEnabled(false) else confirm = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.assist_enable), Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { on -> if (on) confirm = true else vm.setEnabled(false) })
        }
        if (enabled && !ui.hasAnyKey) Text(stringResource(R.string.assist_needs_key), color = MaterialTheme.colorScheme.error)
        Text(stringResource(if (ui.hasOwnKey) R.string.assist_own_key else R.string.assist_shared_key), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = typedKey, onValueChange = { typedKey = it.trim().take(200) },
            label = { Text(stringResource(R.string.assist_key_label)) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveKey(typedKey); typedKey = "" }, enabled = typedKey.isNotBlank(), modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.cloud_key_save)) }
            OutlinedButton(onClick = { vm.testKey(typedKey) }, enabled = (typedKey.isNotBlank() || ui.hasAnyKey) && ui.test != KeyTest.Running, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.cloud_key_test)) }
        }
        when (val t = ui.test) {
            KeyTest.Idle -> Unit
            KeyTest.Running -> Text(stringResource(R.string.cloud_key_testing))
            KeyTest.Ok -> Text(stringResource(R.string.cloud_key_ok), color = MaterialTheme.colorScheme.primary)
            is KeyTest.Failed -> Text(stringResource(errorText(t.kind)), color = MaterialTheme.colorScheme.error)
        }
        if (ui.hasOwnKey) TextButton(onClick = vm::removeKey, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cloud_key_remove)) }
        OutlinedTextField(value = modelId, onValueChange = { modelId = it.trim().take(100) }, label = { Text(stringResource(R.string.cloud_model_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { vm.setModel(modelId) }, enabled = modelId != settings.promptAssist.modelId && modelId.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.cloud_apply))
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.assist_title)) },
            text = { Text(stringResource(R.string.assist_consent, vm.provider.displayName)) },
            confirmButton = { TextButton(onClick = { confirm = false; vm.setEnabled(true) }) { Text(stringResource(R.string.consent_accept)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
