package com.utsavlens.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utsavlens.app.BuildConfig
import com.utsavlens.app.R
import com.utsavlens.app.ui.common.AppBar
import com.utsavlens.app.ui.common.engineName
import com.utsavlens.core.AppLanguage

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
    extraSections: @Composable ColumnScope.() -> Unit = {},
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var alsoFiles by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { AppBar(stringResource(R.string.home_settings), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsSection(stringResource(R.string.settings_language)) {
                    listOf(
                        AppLanguage.SYSTEM to stringResource(R.string.lang_system),
                        AppLanguage.EN to stringResource(R.string.lang_en),
                        AppLanguage.HI to stringResource(R.string.lang_hi),
                        AppLanguage.MR to stringResource(R.string.lang_mr),
                    ).forEach { (lang, label) ->
                        RadioRow(label, settings.language == lang.tag) {
                            if (settings.language != lang.tag) vm.setLanguage(lang.tag) { context.findActivity()?.recreate() }
                        }
                    }
                }

                SettingsSection(stringResource(R.string.settings_default_engine)) {
                    vm.engineIds.forEach { id ->
                        RadioRow(stringResource(engineName(id)), settings.defaultEngine == id) { vm.update { it.copy(defaultEngine = id) } }
                    }
                }

                extraSections()

                SettingsSection(stringResource(R.string.settings_debug)) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { vm.update { it.copy(showMaskDebug = !it.showMaskDebug) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.settings_mask_debug), Modifier.weight(1f))
                        Switch(checked = settings.showMaskDebug, onCheckedChange = { v -> vm.update { it.copy(showMaskDebug = v) } })
                    }
                }

                SettingsSection(stringResource(R.string.settings_storage)) {
                    Text(stringResource(R.string.settings_history_count, storage.historyCount))
                    OutlinedButton(onClick = { confirmClear = true }, enabled = storage.historyCount > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.settings_clear_history))
                    }
                    Text(stringResource(R.string.settings_cache_size, Formatter.formatShortFileSize(context, storage.cacheBytes)))
                    OutlinedButton(onClick = vm::clearCache, enabled = storage.cacheBytes > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.settings_clear_cache))
                    }
                }

                SettingsSection(stringResource(R.string.settings_about)) {
                    Text(stringResource(R.string.rights_note), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.history_delete_body))
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { alsoFiles = !alsoFiles }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alsoFiles, onCheckedChange = { alsoFiles = it })
                        Text(stringResource(R.string.history_delete_files))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearHistory(alsoFiles) }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
