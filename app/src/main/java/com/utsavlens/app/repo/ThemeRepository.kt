package com.utsavlens.app.repo

import android.content.Context
import com.utsavlens.core.AppLogger
import com.utsavlens.core.DispatcherProvider
import com.utsavlens.themes.Theme
import com.utsavlens.themes.ThemeCatalogue
import com.utsavlens.themes.ThemeLookup
import com.utsavlens.themes.ThemeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Loads assets/themes/[*].json once. Adding a file there adds a theme; bad files are skipped and logged. */
class ThemeRepository(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger,
) : ThemeLookup {
    private val mutex = Mutex()
    private var cached: ThemeCatalogue? = null

    suspend fun catalogue(): ThemeCatalogue = mutex.withLock {
        cached ?: withContext(dispatchers.io) {
            val assets = context.assets
            val names = try {
                assets.list(DIR)?.filter { it.endsWith(".json") }.orEmpty()
            } catch (e: Exception) {
                logger.warn("ThemeRepository", "Cannot list themes: ${e.javaClass.simpleName}")
                emptyList()
            }
            val sources = names.map { name ->
                ThemeSource("$DIR/$name") { assets.open("$DIR/$name").bufferedReader(Charsets.UTF_8).use { it.readText() } }
            }
            ThemeCatalogue.load(sources, logger)
        }.also { cached = it }
    }

    override suspend fun theme(id: String): Theme? = catalogue()[id]

    companion object {
        const val DIR = "themes"
    }
}
