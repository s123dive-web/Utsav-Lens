package com.utsavlens.app.ui.options

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utsavlens.app.LocaleSupport
import com.utsavlens.app.jobs.EngineRegistry
import com.utsavlens.app.jobs.JobController
import com.utsavlens.app.jobs.VideoJobs
import com.utsavlens.app.nav.Routes
import com.utsavlens.app.repo.ThemeRepository
import com.utsavlens.core.AppLanguage
import com.utsavlens.core.EngineIds
import com.utsavlens.core.settings.SettingsStore
import com.utsavlens.core.settings.StoredOptions
import com.utsavlens.engine.api.Aspect
import com.utsavlens.engine.api.EngineCapabilities
import com.utsavlens.engine.api.GreetingText
import com.utsavlens.engine.api.OptionAvailability
import com.utsavlens.engine.api.OptionsPolicy
import com.utsavlens.engine.api.Readiness
import com.utsavlens.engine.api.TransformOptions
import com.utsavlens.engine.api.TransformRequest
import com.utsavlens.themes.Theme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.random.Random

data class EngineChoice(val id: String, val caps: EngineCapabilities, val readiness: Readiness)

/** The user's choices; serialised into SavedStateHandle so they survive process death. */
@Serializable
data class OptionsDraft(
    val engineId: String = EngineIds.LITE,
    val keepFace: Boolean = true,
    val keepBackground: Boolean = false,
    val changeOutfit: Boolean = false,
    val strength: Float = 0.7f,
    val aspect: Aspect = Aspect.ORIGINAL,
    val greeting: String = "",
    val greetingLanguage: String = "en",
    val variations: Int = 1,
    val initialised: Boolean = false,
)

data class OptionsUiState(
    val loading: Boolean = true,
    val theme: Theme? = null,
    val language: AppLanguage = AppLanguage.EN,
    val isVideo: Boolean = false,
    val engines: List<EngineChoice> = emptyList(),
    val draft: OptionsDraft = OptionsDraft(),
    val availability: OptionAvailability = OptionAvailability(false, false, true, 4),
    val cloudProviderName: String = "",
    val cloudConsentGiven: Boolean = true,
    val starting: Boolean = false,
) {
    val selected: EngineChoice? get() = engines.firstOrNull { it.id == draft.engineId }
    val canStart: Boolean get() = !loading && !starting && theme != null && (isVideo || selected?.readiness == Readiness.Ready)
    val needsUploadConsent: Boolean get() = !isVideo && selected?.caps?.needsNetwork == true && !cloudConsentGiven
}

@HiltViewModel
class OptionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themes: ThemeRepository,
    private val engines: EngineRegistry,
    private val settings: SettingsStore,
    private val jobs: JobController,
    private val videoJobs: VideoJobs,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val sourceUri: String = savedState.get<String>(Routes.ARG_SRC).orEmpty()
    val isVideo: Boolean = savedState.get<String>(Routes.ARG_KIND) == Routes.KIND_VIDEO
    private val themeId: String = savedState.get<String>(Routes.ARG_THEME).orEmpty()
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(OptionsUiState(isVideo = isVideo, language = LocaleSupport.contentLanguage(context)))
    val state: StateFlow<OptionsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val theme = themes.theme(themeId)
            val s = settings.load()
            val restored = savedState.get<String>(KEY_DRAFT)?.let { runCatching { json.decodeFromString(OptionsDraft.serializer(), it) }.getOrNull() }
            val language = _state.value.language
            val draft = restored ?: OptionsDraft(
                engineId = if (isVideo) EngineIds.LITE else s.defaultEngine,
                keepBackground = s.lastOptions.keepBackground,
                changeOutfit = s.lastOptions.changeOutfit,
                strength = theme?.prompt?.recommendedStrength ?: s.lastOptions.strength,
                aspect = Aspect.fromKey(s.lastOptions.aspect),
                greeting = "",
                greetingLanguage = if (language == AppLanguage.SYSTEM) "en" else language.tag,
                variations = s.lastOptions.variations,
                initialised = true,
            )
            _state.update { it.copy(theme = theme, draft = draft) }
            refresh()
        }
    }

    /** Re-reads engine readiness (e.g. after the user added a key or downloaded a model in Settings). */
    fun refresh() {
        viewModelScope.launch {
            val choices = engines.engines.map { EngineChoice(it.id, it.capabilities(), it.isReady()) }
            val s = settings.load()
            _state.update { st ->
                val engineId = if (choices.any { it.id == st.draft.engineId }) st.draft.engineId else EngineIds.LITE
                val caps = choices.firstOrNull { it.id == engineId }?.caps
                st.copy(
                    loading = false,
                    engines = choices,
                    draft = st.draft.copy(engineId = engineId),
                    availability = caps?.let(OptionsPolicy::availability) ?: st.availability,
                    cloudProviderName = s.cloud.providerId,
                    cloudConsentGiven = s.cloud.providerId in s.cloud.uploadConsentProviders,
                )
            }
        }
    }

    fun edit(transform: (OptionsDraft) -> OptionsDraft) {
        _state.update { st ->
            val next = transform(st.draft)
            val caps = st.engines.firstOrNull { it.id == next.engineId }?.caps
            savedState[KEY_DRAFT] = json.encodeToString(OptionsDraft.serializer(), next)
            st.copy(draft = next, availability = caps?.let(OptionsPolicy::availability) ?: st.availability)
        }
    }

    /** Stores the one-time "photo leaves the device" consent for the current provider, then starts. */
    fun startWhenConsentStored(onStarted: (jobId: String) -> Unit) {
        viewModelScope.launch {
            settings.update { s ->
                s.copy(cloud = s.cloud.copy(uploadConsentProviders = (s.cloud.uploadConsentProviders + s.cloud.providerId).distinct()))
            }
            val s = settings.load()
            _state.update { it.copy(cloudConsentGiven = s.cloud.providerId in s.cloud.uploadConsentProviders) }
            start(onStarted)
        }
    }

    /** Builds the request (unsupported options are forced off by [OptionsPolicy]) and starts the job. */
    fun start(onStarted: (jobId: String) -> Unit) {
        val st = _state.value
        val theme = st.theme ?: return
        if (!st.canStart || st.needsUploadConsent) return
        _state.update { it.copy(starting = true) }
        viewModelScope.launch {
            val d = st.draft
            val caps = st.selected?.caps
            val wanted = TransformOptions(
                keepFace = d.keepFace, keepBackground = d.keepBackground, changeOutfit = d.changeOutfit,
                strength = d.strength, aspect = d.aspect,
                greeting = d.greeting.takeIf { it.isNotBlank() }?.let { GreetingText(it, d.greetingLanguage) },
                variations = d.variations,
            )
            val options = if (caps != null) OptionsPolicy.resolve(wanted, caps) else wanted
            settings.update { s ->
                s.copy(lastOptions = StoredOptions(d.keepBackground, d.changeOutfit, d.strength, d.aspect.key, d.variations))
            }
            val jobId = if (isVideo) {
                videoJobs.startOverlay(
                    com.utsavlens.video.VideoOverlaySpec(
                        sourceUri = sourceUri, themeId = theme.id, strength = options.strength,
                        greeting = options.greeting?.text, seed = Random.nextLong(1, Long.MAX_VALUE / 2),
                    ),
                )
            } else {
                jobs.submitTransform(
                    d.engineId,
                    TransformRequest(jobId = jobs.newJobId(), sourceUri = sourceUri, themeId = theme.id, options = options, seed = Random.nextLong(1, Long.MAX_VALUE / 2)),
                )
            }
            _state.update { it.copy(starting = false) }
            if (jobId.isNotEmpty()) onStarted(jobId)
        }
    }

    companion object {
        private const val KEY_DRAFT = "draft"
    }
}
