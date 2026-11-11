package com.utsavlens.app.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.utsavlens.app.R
import com.utsavlens.platform.BitmapIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 56dp is comfortably above the 48dp minimum touch target. */
val MinTouch = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            if (onBack != null) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("←  " + stringResource(R.string.back)) }
            }
        },
        actions = actions,
    )
}

sealed interface LoadedBitmap {
    data object Loading : LoadedBitmap
    data object Failed : LoadedBitmap
    data class Ready(val bitmap: Bitmap) : LoadedBitmap
}

suspend fun loadBitmap(context: Context, uri: Uri, maxEdgePx: Int, isVideo: Boolean = false): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            if (isVideo) BitmapIo.videoFrame(context, uri, maxEdgePx) else BitmapIo.decode(context, uri, maxEdgePx)
        } catch (_: Exception) {
            null
        }
    }

@Composable
fun rememberUriBitmap(uri: String?, maxEdgePx: Int, isVideo: Boolean = false): LoadedBitmap {
    val context = LocalContext.current.applicationContext
    val state by produceState<LoadedBitmap>(LoadedBitmap.Loading, uri, maxEdgePx, isVideo) {
        value = LoadedBitmap.Loading
        value = if (uri.isNullOrEmpty()) LoadedBitmap.Failed
        else loadBitmap(context, Uri.parse(uri), maxEdgePx, isVideo)?.let { LoadedBitmap.Ready(it) } ?: LoadedBitmap.Failed
    }
    return state
}

@Composable
fun UriImage(
    uri: String?,
    modifier: Modifier = Modifier,
    maxEdgePx: Int = 1024,
    isVideo: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
    contentDescription: String? = null,
) {
    when (val s = rememberUriBitmap(uri, maxEdgePx, isVideo)) {
        is LoadedBitmap.Ready -> Image(s.bitmap.asImageBitmap(), contentDescription, modifier, contentScale = contentScale)
        LoadedBitmap.Loading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LoadedBitmap.Failed -> Box(modifier, contentAlignment = Alignment.Center) { Text(stringResource(R.string.error_open_media)) }
    }
}
