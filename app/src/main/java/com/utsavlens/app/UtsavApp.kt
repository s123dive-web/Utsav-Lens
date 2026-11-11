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
}
