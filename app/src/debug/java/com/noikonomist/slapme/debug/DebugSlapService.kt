package com.noikonomist.slapme.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.noikonomist.slapme.R
import com.noikonomist.slapme.SlapMeApp
import com.noikonomist.slapme.util.SlapLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debug spike: a short-lived foreground service that counts down, slaps (so it can be tried over
 * other apps), checks whether Home was reached, posts the result and stops.
 */
class DebugSlapService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_COUNTDOWN, getString(R.string.debug_channel_countdown), NotificationManager.IMPORTANCE_LOW),
        )
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, getString(R.string.debug_channel_result), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val delaySeconds = (intent?.getIntExtra(EXTRA_DELAY, DEFAULT_DELAY) ?: DEFAULT_DELAY).coerceIn(0, 60)
        val alwaysPlay = intent?.getBooleanExtra(EXTRA_ALWAYS_PLAY, false) ?: false

        ServiceCompat.startForeground(
            this,
            ID_COUNTDOWN,
            countdown(delaySeconds),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            } else {
                0
            },
        )
        SlapLog.i("Debug slap in ${delaySeconds}s (alwaysPlay=$alwaysPlay)")

        job?.cancel()
        job = scope.launch {
            for (left in delaySeconds downTo 1) {
                notifications.notify(ID_COUNTDOWN, countdown(left))
                delay(1_000)
            }
            val report = (application as SlapMeApp).container.slapPipeline.slap(alwaysPlay)
            delay(HOME_CHECK_DELAY_MS)
            val verdict = if (report.homeRequested) {
                HomeLaunchCheck.verdict(this@DebugSlapService, report.homeRequestedAtMillis)
            } else {
                "not requested"
            }
            SlapLog.i("Home launch check: $verdict")
            val summary = DebugState.record(report, verdict)
            notifications.notify(ID_RESULT, result(verdict, summary))
            stopNow()
        }
        return START_NOT_STICKY
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTimeout(startId: Int) {
        SlapLog.w("Debug slap service timed out")
        stopNow()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        SlapLog.w("Debug slap service timed out (type $fgsType)")
        stopNow()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun stopNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun countdown(secondsLeft: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.debug_countdown_title, secondsLeft))
            .setContentText(getString(R.string.debug_countdown_text))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()

    private fun result(verdict: String, summary: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.debug_result_title, verdict))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .build()

    companion object {
        private const val EXTRA_DELAY = "delay"
        private const val EXTRA_ALWAYS_PLAY = "always_play"
        private const val DEFAULT_DELAY = 10
        private const val CHANNEL_COUNTDOWN = "debug_countdown"
        private const val CHANNEL_RESULT = "debug_result"
        private const val ID_COUNTDOWN = 1001
        private const val ID_RESULT = 1002

        /** Usage events need a moment to be written after the launcher resumes. */
        const val HOME_CHECK_DELAY_MS = 600L

        fun start(context: Context, delaySeconds: Int, alwaysPlay: Boolean) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DebugSlapService::class.java)
                    .putExtra(EXTRA_DELAY, delaySeconds)
                    .putExtra(EXTRA_ALWAYS_PLAY, alwaysPlay),
            )
        }
    }
}
