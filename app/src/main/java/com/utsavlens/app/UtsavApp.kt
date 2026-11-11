package com.utsavlens.app

import android.app.Application
import com.utsavlens.app.jobs.JobController
import com.utsavlens.core.io.AtomicFileWriter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class UtsavApp : Application() {
    @Inject lateinit var jobs: JobController

    /** Injected eagerly so the video job runners are registered before any job can be retried. */
    @Inject lateinit var videoJobs: com.utsavlens.app.jobs.VideoJobs
    @Inject lateinit var modelJobs: com.utsavlens.app.jobs.ModelJobs

    @Inject lateinit var liteEngine: com.utsavlens.engine.lite.LiteEngine
    @Inject lateinit var maskProvider: com.utsavlens.segmentation.MaskProvider

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // Process-death recovery: fail jobs that died with the previous process and remove
            // temp files left behind by interrupted atomic writes.
            jobs.reconcileOnStart()
            listOf(File(filesDir, "datastore"), File(filesDir, "secure"), File(filesDir, "models"), File(cacheDir, "previews"))
                .filter { it.isDirectory }
                .forEach { runCatching { AtomicFileWriter.cleanStaleTemps(it) } }
        }
    }

    /** Low-memory handling: give back caches and, when nothing is running, the segmentation models. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            appScope.launch {
                liteEngine.clearCaches()
                if (jobs.live.value.isEmpty()) maskProvider.close()
            }
        }
    }
}
