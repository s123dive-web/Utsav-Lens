package com.utsavlens.app.ui.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utsavlens.app.R
import com.utsavlens.app.ui.common.AppBar
import com.utsavlens.app.ui.common.UriImage
import com.utsavlens.themes.Theme

@Composable
fun ThemesScreen(
    onBack: () -> Unit,
    onThemeChosen: (sourceUri: String, kind: String, themeId: String) -> Unit,
    source: SourceViewModel = hiltViewModel(),
    vm: ThemesViewModel = hiltViewModel(),
) {
    val src by source.state.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val kind = if (source.isVideo) com.utsavlens.app.nav.Routes.KIND_VIDEO else com.utsavlens.app.nav.Routes.KIND_PHOTO

    Scaffold(topBar = { AppBar(stringResource(R.string.themes_title), onBack) }) { padding ->
        // Adaptive columns: 2 on a folded/outer screen, more on an unfolded inner screen or tablet.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SourceHeader(src, height = 240.dp, showMaskToggle = state.showMaskToggle)
            }
            if (state.loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else if (state.themes.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.themes_none), Modifier.padding(24.dp)) }
            }
            items(state.themes, key = { it.id }) { theme ->
                ThemeCard(theme, theme.name.get(state.language), state.thumbnails[theme.id]) {
                    if (!src.failed) onThemeChosen(source.sourceUri, kind, theme.id)
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(theme: Theme, name: String, thumbnail: String?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(parseColor(theme.palette.background)),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    UriImage(thumbnail, Modifier.fillMaxSize(), maxEdgePx = 360, contentScale = ContentScale.Crop, contentDescription = name)
                } else {
                    CircularProgressIndicator(color = parseColor(theme.palette.accent))
                }
            }
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
    }
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: IllegalArgumentException) {
    Color.Gray
}
