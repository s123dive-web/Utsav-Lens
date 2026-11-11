package com.utsavlens.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utsavlens.app.LocaleSupport
import com.utsavlens.app.jobs.EngineRegistry
import com.utsavlens.app.repo.HistoryRepository
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.core.settings.AppSettings
import com.utsavlens.core.settings.SettingsStore
import com.utsavlens.data.db.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class StorageInfo(val historyCount: Int = 0, val cacheBytes: Long = 0)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SettingsStore,
    private val history: HistoryRepository,
    private val db: AppDatabase,
    private val dispatchers: DispatcherProvider,
    engines: EngineRegistry,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = store.settings
    val engineIds: List<String> = engines.engines.map { it.id }

    private val _storage = MutableStateFlow(StorageInfo())
    val storage: StateFlow<StorageInfo> = _storage.asStateFlow()

    init {
        viewModelScope.launch { store.load() }
        refreshStorage()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { store.update(transform) }
    }

    /** Persists the language, then lets the caller recreate the Activity so resources reload. */
    fun setLanguage(tag: String, onApplied: () -> Unit) {
        viewModelScope.launch {
            store.update { it.copy(language = tag) }
            LocaleSupport.onLanguageChanged(tag)
            onApplied()
        }
    }

    fun clearHistory(alsoDeleteOutputs: Boolean) {
        viewModelScope.launch {
            history.clear(alsoDeleteOutputs)
            refreshStorage()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(dispatchers.io) { cacheDirs().forEach { it.deleteRecursively() } }
            refreshStorage()
        }
    }

    fun refreshStorage() {
        viewModelScope.launch {
            _storage.update {
                withContext(dispatchers.io) {
                    StorageInfo(
                        historyCount = db.historyDao().count(),
                        cacheBytes = cacheDirs().sumOf { dir -> dir.walkTopDown().filter(File::isFile).sumOf(File::length) },
                    )
                }
            }
        }
    }

    private fun cacheDirs(): List<File> = listOf("previews", "share", "video", "upload").map { File(context.cacheDir, it) }
}
