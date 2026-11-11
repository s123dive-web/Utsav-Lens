package com.utsavlens.app.jobs

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.utsavlens.core.AppError
import com.utsavlens.core.job.JobKind
import com.utsavlens.core.settings.SettingsStore
import com.utsavlens.engine.api.TransformStage
import com.utsavlens.engine.local.ChecksumMismatchException
import com.utsavlens.engine.local.ModelDownloader
import com.utsavlens.engine.local.WifiRequiredException
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Runs the Local AI model download as a cancellable foreground job with progress. */
class ModelJobs(
    private val context: Context,
    private val controller: JobController,
    private val downloader: ModelDownloader,
    private val settings: SettingsStore,
) {
    init {
        controller.registerRunner(JobKind.MODEL_DOWNLOAD) { jobId, _, report ->
            val wifiOnly = settings.load().local.wifiOnly
            try {
                downloader.downloadAll(allowedNow = { !wifiOnly || isUnmetered() }) { done, total ->
                    report(LiveJob(jobId, JobKind.MODEL_DOWNLOAD, (done * 100 / total.coerceAtLeast(1)).toInt().coerceIn(0, 100), TransformStage.DOWNLOADING))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: WifiRequiredException) {
                throw JobController.JobFailedException(AppError.Kind.NO_NETWORK, "wifi")
            } catch (e: ChecksumMismatchException) {
                throw JobController.JobFailedException(AppError.Kind.BAD_RESPONSE, "checksum")
            } catch (e: IOException) {
                throw JobController.JobFailedException(if (isOnline()) AppError.Kind.IO else AppError.Kind.NO_NETWORK)
            }
            emptyList()
        }
    }

    suspend fun startDownload(): String = controller.submitCustom(JobKind.MODEL_DOWNLOAD, "{}")

    private fun capabilities(): NetworkCapabilities? = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)
    } catch (_: Exception) {
        null
    }

    private fun isOnline(): Boolean = capabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    fun isUnmetered(): Boolean {
        val caps = capabilities() ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
