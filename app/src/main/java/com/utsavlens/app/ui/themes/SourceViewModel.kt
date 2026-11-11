package com.utsavlens.app.ui.themes

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utsavlens.app.nav.Routes
import com.utsavlens.app.ui.common.loadBitmap
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.segmentation.MaskBitmaps
import com.utsavlens.segmentation.MaskProvider
import com.utsavlens.segmentation.PersonMask
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SourceUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val preview: Bitmap? = null,
    val maskDebug: Bitmap? = null,
    val personFound: Boolean = false,
    val segmenterFailed: Boolean = false,
    val faceCount: Int = 0,
    val maskReady: Boolean = false,
)

/** Loads the chosen media (a frame, for video) and its person mask. Arguments come from the nav back stack, so it survives process death. */
@HiltViewModel
class SourceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val maskProvider: MaskProvider,
    private val dispatchers: DispatcherProvider,
    savedState: SavedStateHandle,
) : ViewModel() {
    val sourceUri: String = savedState.get<String>(Routes.ARG_SRC).orEmpty()
    val isVideo: Boolean = savedState.get<String>(Routes.ARG_KIND) == Routes.KIND_VIDEO

    private val _state = MutableStateFlow(SourceUiState())
    val state: StateFlow<SourceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val bitmap = if (sourceUri.isEmpty()) null else loadBitmap(context, Uri.parse(sourceUri), PREVIEW_EDGE, isVideo)
            if (bitmap == null) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            _state.update { it.copy(loading = false, preview = bitmap) }
            val mask: PersonMask = maskProvider.segment(bitmap)
            val debug = withContext(dispatchers.default) {
                try { MaskBitmaps.debugOverlay(bitmap, mask) } catch (_: Throwable) { null }
            }
            _state.update {
                it.copy(
                    maskDebug = debug, personFound = mask.personFound, segmenterFailed = mask.segmenterFailed,
                    faceCount = mask.faces.size, maskReady = true,
                )
            }
        }
    }

    companion object {
        const val PREVIEW_EDGE = 1024
    }
}
