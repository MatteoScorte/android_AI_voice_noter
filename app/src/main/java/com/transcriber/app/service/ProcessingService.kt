package com.transcriber.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.transcriber.app.MainActivity
import com.transcriber.app.R

/**
 * Minimal foreground service that keeps the process alive during audio transcription
 * and AI processing. Without this, Android can kill the app process while it is
 * backgrounded, interrupting GlobalScope coroutines in TranscriptViewModel.
 */
class ProcessingService : Service() {

    companion object {
        const val CHANNEL_ID = "processing_channel"
        const val NOTIFICATION_ID = 1002
        const val ACTION_START = "com.transcriber.app.PROCESSING_START"
        const val ACTION_STOP  = "com.transcriber.app.PROCESSING_STOP"
        const val EXTRA_STEP   = "step"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID, "Elaborazione Audio", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val step = intent.getStringExtra(EXTRA_STEP) ?: "Elaborazione in corso..."
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    ?: Intent(this, MainActivity::class.java)
                val openPi = PendingIntent.getActivity(
                    this, 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Voxlog — Elaborazione audio")
                    .setContentText(step)
                    .setSmallIcon(R.drawable.ic_notification_mic)
                    .setColor(0xFF49DD7F.toInt())
                    .setOngoing(true)
                    .setContentIntent(openPi)
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                    .build()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
}
