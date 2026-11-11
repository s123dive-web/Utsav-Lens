package com.utsavlens.app.ui.result

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.utsavlens.app.R
import com.utsavlens.app.ui.common.LoadedBitmap
import com.utsavlens.app.ui.common.rememberUriBitmap

/**
 * Before/after comparison: the original is revealed on the left of the handle, the result on the
 * right. Drag or tap on the picture, or use the slider underneath (accessible alternative).
 */
@Composable
fun CompareSlider(beforeUri: String?, afterUri: String, modifier: Modifier = Modifier) {
    var fraction by rememberSaveable { mutableStateOf(0.5f) }
    val after = rememberUriBitmap(afterUri, 1600)
    val before = rememberUriBitmap(beforeUri, 1280)
    val label = stringResource(R.string.result_compare)
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, drag ->
                        change.consume()
                        fraction = (fraction + drag / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos -> fraction = (pos.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.Center,
        ) {
            when (after) {
                is LoadedBitmap.Ready -> Image(after.bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                LoadedBitmap.Loading -> CircularProgressIndicator()
                LoadedBitmap.Failed -> Text(stringResource(R.string.result_missing))
            }
            if (before is LoadedBitmap.Ready && after is LoadedBitmap.Ready) {
                Image(
                    before.bitmap.asImageBitmap(), null,
                    Modifier.fillMaxSize().drawWithContent {
                        clipRect(right = size.width * fraction) { this@drawWithContent.drawContent() }
                        val x = size.width * fraction
                        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
                        drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(x, size.height / 2))
                    },
                    contentScale = ContentScale.Fit,
                )
            }
        }
        if (before is LoadedBitmap.Ready) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.result_before), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = fraction, onValueChange = { fraction = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).heightIn(min = 48.dp).semantics { contentDescription = label },
                )
                Text(stringResource(R.string.result_after), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
