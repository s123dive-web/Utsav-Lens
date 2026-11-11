package com.utsavlens.core.settings

import com.utsavlens.core.AppLogger
import com.utsavlens.core.io.AtomicFileWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single source of truth for [AppSettings]. Reads once (migrating + deep-merging through
 * [SettingsCodec]); every update is serialised by a mutex and persisted with [AtomicFileWriter].
 */
class SettingsStore(
    private val file: File,
    private val io: CoroutineDispatcher,
    private val codec: SettingsCodec = SettingsCodec(),
    private val logger: AppLogger = AppLogger.NONE,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow(AppSettings())
    @Volatile private var loaded = false

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    suspend fun load(): AppSettings = mutex.withLock { ensureLoaded() }

    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings = mutex.withLock {
        val current = ensureLoaded()
        val next = transform(current).copy(schemaVersion = AppSettings.CURRENT_VERSION)
        if (next != current) {
            withContext(io) { AtomicFileWriter.writeText(file, codec.encode(next)) }
            state.value = next
        }
        next
    }

    private suspend fun ensureLoaded(): AppSettings {
        if (!loaded) {
            state.value = withContext(io) {
                val text = try {
                    if (file.isFile) file.readText() else null
                } catch (e: Exception) {
                    logger.warn("SettingsStore", "Cannot read settings: ${e.javaClass.simpleName}")
                    null
                }
                codec.decode(text)
            }
            loaded = true
        }
        return state.value
    }
}
