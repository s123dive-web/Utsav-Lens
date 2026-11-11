package com.utsavlens.app.ui.themes

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.utsavlens.app.R

/** The chosen photo with the mask debug toggle and the "no person found" notice. */
@Composable
fun SourceHeader(state: SourceUiState, height: Dp, modifier: Modifier = Modifier) {
    var showMask by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            val shown = if (showMask && state.maskDebug != null) state.maskDebug else state.preview
            when {
                state.failed -> Text(stringResource(R.string.error_open_media))
                shown != null -> Image(shown.asImageBitmap(), null, Modifier.fillMaxWidth().height(height), contentScale = ContentScale.Fit)
                else -> CircularProgressIndicator()
            }
        }
        if (state.preview != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = showMask,
                    enabled = state.maskDebug != null,
                    onClick = { showMask = !showMask },
                    label = { Text(stringResource(if (showMask) R.string.mask_hide else R.string.mask_show)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                val note = when {
                    !state.maskReady -> stringResource(R.string.loading)
                    state.segmenterFailed -> stringResource(R.string.mask_failed)
                    !state.personFound -> stringResource(R.string.mask_no_person)
                    else -> stringResource(R.string.mask_person_found, state.faceCount)
                }
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.maskReady && !state.personFound) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
