package com.utsavlens.app.ui.options

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utsavlens.app.R
import com.utsavlens.app.ui.common.AppBar
import com.utsavlens.app.ui.common.MinTouch
import com.utsavlens.app.ui.common.engineDescription
import com.utsavlens.app.ui.common.engineName
import com.utsavlens.core.AppLanguage
import com.utsavlens.core.EngineIds
import com.utsavlens.engine.api.Aspect
import com.utsavlens.engine.api.Readiness
import kotlin.math.roundToInt

@Composable
fun OptionsScreen(
    onBack: () -> Unit,
    onStarted: (jobId: String) -> Unit,
    onOpenSettings: () -> Unit,
    vm: OptionsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showConsent by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    // Permission results never block the job: without notifications the job still runs, and
    // without storage access (Android 8/9) the result is kept inside the app and can be shared.
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.start(onStarted) }
    fun startWithPermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) vm.start(onStarted) else permissions.launch(needed.toTypedArray())
    }

    val theme = state.theme
    Scaffold(topBar = { AppBar(theme?.name?.get(state.language) ?: stringResource(R.string.options_title), onBack) }) { padding ->
        if (state.loading || theme == null) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.loading) CircularProgressIndicator() else Text(stringResource(R.string.themes_none))
            }
            return@Scaffold
        }
        val d = state.draft
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.isVideo) {
                    SectionTitle(stringResource(R.string.options_engine))
                    state.engines.forEach { choice ->
                        EngineCard(choice, selected = choice.id == d.engineId, onSelect = { vm.edit { it.copy(engineId = choice.id) } }, onOpenSettings = onOpenSettings)
                    }
                    SwitchRow(
                        title = stringResource(R.string.opt_keep_face),
                        subtitle = if (!state.availability.keepFaceEditable) stringResource(R.string.opt_keep_face_locked) else null,
                        checked = if (state.availability.keepFaceEditable) d.keepFace else true,
                        enabled = state.availability.keepFaceEditable,
                        onChange = { v -> vm.edit { it.copy(keepFace = v) } },
                    )
                    SwitchRow(
                        title = stringResource(R.string.opt_keep_background),
                        subtitle = stringResource(R.string.opt_keep_background_desc),
                        checked = d.keepBackground, enabled = true,
                        onChange = { v -> vm.edit { it.copy(keepBackground = v) } },
                    )
                    SwitchRow(
                        title = stringResource(R.string.opt_outfit),
                        subtitle = if (!state.availability.outfitEnabled) stringResource(R.string.opt_outfit_unavailable) else null,
                        checked = d.changeOutfit && state.availability.outfitEnabled,
                        enabled = state.availability.outfitEnabled,
                        onChange = { v -> vm.edit { it.copy(changeOutfit = v) } },
                    )
                }

                SectionTitle(stringResource(R.string.opt_strength) + "  " + (d.strength * 100).roundToInt() + "%")
                Slider(value = d.strength, onValueChange = { v -> vm.edit { it.copy(strength = v) } }, valueRange = 0f..1f, modifier = Modifier.heightIn(min = 48.dp))

                if (!state.isVideo) {
                    SectionTitle(stringResource(R.string.opt_aspect))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Aspect.entries.forEach { a ->
                            FilterChip(
                                selected = d.aspect == a,
                                onClick = { vm.edit { it.copy(aspect = a) } },
                                label = { Text(if (a == Aspect.ORIGINAL) stringResource(R.string.aspect_original) else a.key) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }

                SectionTitle(stringResource(R.string.opt_greeting))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AppLanguage.EN to R.string.lang_en, AppLanguage.HI to R.string.lang_hi, AppLanguage.MR to R.string.lang_mr).forEach { (lang, label) ->
                        FilterChip(
                            selected = d.greetingLanguage == lang.tag,
                            onClick = { vm.edit { it.copy(greetingLanguage = lang.tag) } },
                            label = { Text(stringResource(label)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = d.greeting,
                    onValueChange = { v -> vm.edit { it.copy(greeting = v.take(80)) } },
                    label = { Text(stringResource(R.string.opt_greeting_hint)) },
                    singleLine = false, maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = d.greeting.isBlank(), onClick = { vm.edit { it.copy(greeting = "") } },
                        label = { Text(stringResource(R.string.opt_greeting_none)) }, modifier = Modifier.heightIn(min = 48.dp),
                    )
                    theme.greetings.get(AppLanguage.fromTag(d.greetingLanguage)).forEach { g ->
                        FilterChip(
                            selected = d.greeting == g, onClick = { vm.edit { it.copy(greeting = g) } },
                            label = { Text(g) }, modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }

                if (!state.isVideo) {
                    SectionTitle(stringResource(R.string.opt_variations))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..4).forEach { n ->
                            FilterChip(
                                selected = d.variations.coerceAtMost(state.availability.maxVariations) == n,
                                enabled = n <= state.availability.maxVariations,
                                onClick = { vm.edit { it.copy(variations = n) } },
                                label = { Text(n.toString()) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { if (state.needsUploadConsent) showConsent = true else startWithPermissions() },
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                ) { Text(stringResource(R.string.opt_start)) }
                Text(stringResource(R.string.rights_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text(stringResource(R.string.consent_title)) },
            text = { Text(stringResource(R.string.consent_body, state.cloudProviderName)) },
            confirmButton = {
                TextButton(onClick = { showConsent = false; startAfterConsent(vm, onStarted) }) { Text(stringResource(R.string.consent_accept)) }
            },
            dismissButton = { TextButton(onClick = { showConsent = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun startAfterConsent(vm: OptionsViewModel, onStarted: (String) -> Unit) {
    // Consent is stored first; the start call re-checks it, so a failed write cannot upload.
    vm.startWhenConsentStored(onStarted)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable(enabled = enabled) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun EngineCard(choice: EngineChoice, selected: Boolean, onSelect: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(stringResource(engineName(choice.id)), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(engineDescription(choice.id)), style = MaterialTheme.typography.bodySmall)
                val status = when (val r = choice.readiness) {
                    Readiness.Ready -> null
                    Readiness.NeedsKey -> stringResource(R.string.ready_needs_key)
                    is Readiness.NeedsDownload -> stringResource(R.string.ready_needs_download, Formatter.formatShortFileSize(context, r.totalBytes))
                    is Readiness.Unsupported -> stringResource(R.string.ready_unsupported, unsupportedText(r))
                }
                if (status != null) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (choice.readiness !is Readiness.Unsupported && choice.id != EngineIds.LITE) {
                        TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.home_settings)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun unsupportedText(r: Readiness.Unsupported): String = when (r.reason) {
    com.utsavlens.engine.api.UnsupportedReason.LOW_RAM -> stringResource(R.string.unsupported_ram, r.detail)
    com.utsavlens.engine.api.UnsupportedReason.CHIPSET -> stringResource(R.string.unsupported_chipset, r.detail)
    com.utsavlens.engine.api.UnsupportedReason.ANDROID_VERSION -> stringResource(R.string.unsupported_android)
    com.utsavlens.engine.api.UnsupportedReason.STORAGE -> stringResource(R.string.unsupported_storage, r.detail)
    com.utsavlens.engine.api.UnsupportedReason.OTHER -> r.detail
}
