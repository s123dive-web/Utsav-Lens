package com.utsavlens.app.jobs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.utsavlens.app.LocaleSupport
import com.utsavlens.app.MainActivity
import com.utsavlens.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the process alive while [JobController] has running jobs and
 * shows their progress with a Cancel action. It stops itself as soon as no job is running.
 */
@AndroidEntryPoint
class JobService : Service() {
    @Inject lateinit var controller: JobController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun attachBaseContext(base: Context) = super.attachBaseContext(LocaleSupport.wrap(base))

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_JOB_ID)?.let(controller::cancel)
        }
        ensureChannel()
        val first = controller.live.value.values.firstOrNull()
        try {
            val type = when {
                Build.VERSION.SDK_INT >= 35 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                else -> 0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(first), type)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (watcher == null) {
            watcher = scope.launch {
                var idleTicks = 0
                while (true) {
                    val jobs = controller.live.value.values
                    if (jobs.isEmpty()) {
                        // Small grace period so back-to-back jobs do not restart the service.
                        if (++idleTicks >= 3) break
                    } else {
                        idleTicks = 0
                        notificationManager().notify(NOTIFICATION_ID, buildNotification(jobs.first()))
                    }
                    delay(500)
                }
                ServiceCompat.stopForeground(this@JobService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        // Not sticky: after process death the job is failed cleanly by reconcileOnStart instead of
        // being silently restarted with a null intent.
        return START_NOT_STICKY
    }

    /** Android 15+ limits dataSync / mediaProcessing services; stop cleanly when the system says so. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        controller.live.value.keys.forEach(controller::cancel)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notificationManager() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        notificationManager().createNotificationChannel(channel)
    }

    private fun buildNotification(job: LiveJob?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_job)
            .setContentTitle(getString(R.string.notif_working))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
        if (job != null) {
            builder.setProgress(100, job.percent.coerceIn(0, 100), false)
            builder.setContentText(getString(R.string.progress_percent, job.percent.coerceIn(0, 100)))
            val cancel = PendingIntent.getService(
                this, job.jobId.hashCode(),
                Intent(this, JobService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_JOB_ID, job.jobId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, getString(R.string.cancel), cancel)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    companion object {
        const val ACTION_CANCEL = "com.utsavlens.app.CANCEL_JOB"
        const val EXTRA_JOB_ID = "jobId"
        private const val CHANNEL_ID = "jobs"
        private const val NOTIFICATION_ID = 41
    }
}
