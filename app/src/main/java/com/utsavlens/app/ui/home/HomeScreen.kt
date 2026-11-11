package com.utsavlens.app.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.utsavlens.app.R
import com.utsavlens.app.nav.Routes
import com.utsavlens.app.ui.common.MinTouch
import kotlinx.coroutines.launch
import java.io.File

/** Keeps read access to a picked item across restarts where the provider allows it. */
private fun persistRead(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: Exception) {
        // Not every provider offers persistable grants; the session grant is enough for this run.
    }
}

private fun newCaptureUri(context: Context, extension: String): Uri {
    val dir = File(context.filesDir, "captures").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.$extension")
    return FileProvider.getUriForFile(context, context.packageName + ".files", file)
}

@Composable
fun HomeScreen(
    onMediaChosen: (uri: String, kind: String) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Survives the camera app evicting our process while it is in front.
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    val noCamera = stringResource(R.string.error_no_camera)

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { persistRead(context, uri); onMediaChosen(uri.toString(), Routes.KIND_PHOTO) }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { persistRead(context, uri); onMediaChosen(uri.toString(), Routes.KIND_VIDEO) }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCapture
        pendingCapture = null
        if (ok && uri != null) onMediaChosen(uri, Routes.KIND_PHOTO)
    }
    val recordVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val uri = pendingCapture
        pendingCapture = null
        if (ok && uri != null) onMediaChosen(uri, Routes.KIND_VIDEO)
    }

    fun capture(extension: String, launch: (Uri) -> Unit) {
        try {
            val uri = newCaptureUri(context, extension)
            pendingCapture = uri.toString()
            launch(uri)
        } catch (_: Exception) {
            pendingCapture = null
            scope.launch { snackbar.showSnackbar(noCamera) }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            val wide = Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = MinTouch)
            Button(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = wide) {
                Text(stringResource(R.string.home_pick_photo))
            }
            FilledTonalButton(onClick = { capture("jpg") { takePhoto.launch(it) } }, modifier = wide) {
                Text(stringResource(R.string.home_take_photo))
            }
            Button(onClick = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, modifier = wide) {
                Text(stringResource(R.string.home_pick_video))
            }
            FilledTonalButton(onClick = { capture("mp4") { recordVideo.launch(it) } }, modifier = wide) {
                Text(stringResource(R.string.home_record_video))
            }
            Row(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.home_history)) }
                OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.home_settings)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.rights_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
