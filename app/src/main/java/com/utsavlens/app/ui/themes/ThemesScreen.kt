package com.utsavlens.app.ui.themes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utsavlens.app.R
import com.utsavlens.app.ui.common.AppBar

@Composable
fun ThemesScreen(onBack: () -> Unit, source: SourceViewModel = hiltViewModel()) {
    val state by source.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppBar(stringResource(R.string.themes_title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SourceHeader(state, height = 360.dp)
        }
    }
}
