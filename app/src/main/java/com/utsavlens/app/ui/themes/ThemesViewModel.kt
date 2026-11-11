package com.utsavlens.app.ui.themes

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utsavlens.app.LocaleSupport
import com.utsavlens.app.jobs.EngineRegistry
import com.utsavlens.app.nav.Routes
import com.utsavlens.app.repo.ThemeRepository
import com.utsavlens.core.AppLanguage
import com.utsavlens.core.settings.SettingsStore
import com.utsavlens.engine.api.Aspect
import com.utsavlens.engine.api.TransformOptions
import com.utsavlens.engine.api.TransformProgress
import com.utsavlens.engine.api.TransformRequest
import com.utsavlens.themes.Theme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThemesUiState(
    val loading: Boolean = true,
    val themes: List<Theme> = emptyList(),
    /** themeId -> URI of a live LITE thumbnail rendered from the user's own photo. */
    val thumbnails: Map<String, String> = emptyMap(),
    val language: AppLanguage = AppLanguage.EN,
    val showMaskToggle: Boolean = false,
)

@HiltViewModel
class ThemesViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val themes: ThemeRepository,
    private val engines: EngineRegistry,
    settings: SettingsStore,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val sourceUri: String = savedState.get<String>(Routes.ARG_SRC).orEmpty()
    private val isVideo: Boolean = savedState.get<String>(Routes.ARG_KIND) == Routes.KIND_VIDEO

    private val _state = MutableStateFlow(ThemesUiState(language = LocaleSupport.contentLanguage(context)))
    val state: StateFlow<ThemesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val showMask = settings.load().showMaskDebug
            val list = themes.catalogue().themes
            _state.update { it.copy(loading = false, themes = list, showMaskToggle = showMask) }
            if (sourceUri.isEmpty()) return@launch
            // Live thumbnails go through the same TransformEngine interface as full renders.
            val lite = engines.lite
            val key = Integer.toHexString(sourceUri.hashCode())
            for (theme in list) {
                val request = TransformRequest(
                    jobId = "thumb_$key",
                    sourceUri = sourceUri,
                    themeId = theme.id,
                    options = TransformOptions(aspect = Aspect.SQUARE, keepBackground = isVideo, strength = 0.9f),
                    outputMaxEdgePx = 360,
                    seed = 1L,
                    saveToGallery = false,
                )
                lite.transform(request).collect { p ->
                    if (p is TransformProgress.Success) {
                        _state.update { it.copy(thumbnails = it.thumbnails + (theme.id to p.outputUris.first())) }
                    }
                }
            }
        }
    }
}
