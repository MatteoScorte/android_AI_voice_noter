package com.transcriber.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.transcriber.app.R

class RecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.transcriber.app.ACTION_START"
        const val ACTION_STOP = "com.transcriber.app.ACTION_STOP"
        const val ACTION_PAUSE = "com.transcriber.app.ACTION_PAUSE"
        const val ACTION_RESUME = "com.transcriber.app.ACTION_RESUME"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_MEETING_ID = "meeting_id"
        const val BROADCAST_RECORDING_STATUS = "com.transcriber.app.RECORDING_STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_ELAPSED_TIME = "elapsed_time"
        const val EXTRA_ERROR_MSG = "error_msg"
        const val STATUS_STARTED = "started"
        const val STATUS_STOPPED = "stopped"
        const val STATUS_ERROR = "error"
        const val STATUS_PAUSED = "paused"

        private const val MAX_WAKELOCK_MS = 8L * 60 * 60 * 1000
        private const val TIMER_INTERVAL_MS = 1000L
    }

    private var mediaRecorder: MediaRecorder? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isRecording = false
    private var isPaused = false
    private var startTimeMs: Long = 0
    private var totalPausedTimeMs: Long = 0
    private var lastPauseTimeMs: Long = 0
    private var meetingId: String = ""
    private var outputFilePath: String = ""

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording && !isPaused) {
                updateNotification(SystemClock.elapsedRealtime() - startTimeMs - totalPausedTimeMs)
                handler.postDelayed(this, TIMER_INTERVAL_MS)
            }
        }
    }

    private val errorListener = MediaRecorder.OnErrorListener { _, what, extra ->
        android.util.Log.e("RecordingService", "MediaRecorder error: what=$what extra=$extra")
        sendStatusBroadcast(STATUS_ERROR, getElapsedRealTime(), "Errore registrazione (codice: $what).")
        safeStopRecorder()
        sendStatusBroadcast(STATUS_STOPPED, getElapsedRealTime())
        cleanup()
        stopSelf()
    }

    private val infoListener = MediaRecorder.OnInfoListener { _, what, _ ->
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            android.util.Log.w("RecordingService", "Max file size reached")
            sendStatusBroadcast(STATUS_ERROR, getElapsedRealTime(), "Limite dimensione raggiunto.")
            safeStopRecorder()
            sendStatusBroadcast(STATUS_STOPPED, getElapsedRealTime())
            cleanup()
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
                meetingId = intent.getStringExtra(EXTRA_MEETING_ID) ?: ""
                startRecording(filePath)
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRecording(outputFile: String) {
        if (isRecording) return 
        outputFilePath = outputFile
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "AudioTranscriber::RecordingWakeLock"
            ).also { it.acquire(MAX_WAKELOCK_MS) }

            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile)
                setOnErrorListener(errorListener)
                setOnInfoListener(infoListener)
                setMaxFileSize(1_500_000_000L)
                prepare()
                start()
            }

            isRecording = true
            isPaused = false
            startTimeMs = SystemClock.elapsedRealtime()
            totalPausedTimeMs = 0

            val notification = buildNotification(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            handler.post(timerRunnable)
            sendStatusBroadcast(STATUS_STARTED, 0)
        } catch (e: Exception) {
            android.util.Log.e("RecordingService", "Failed to start", e)
            sendStatusBroadcast(STATUS_ERROR, 0, e.message ?: "Errore avvio registrazione")
            cleanup()
            stopSelf()
        }
    }

    private fun pauseRecording() {
        if (!isRecording || isPaused) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                isPaused = true
                lastPauseTimeMs = SystemClock.elapsedRealtime()
                sendStatusBroadcast(STATUS_PAUSED, getElapsedRealTime())
                updateNotification(getElapsedRealTime())
            } catch (e: Exception) {
                android.util.Log.e("RecordingService", "Failed to pause", e)
            }
        }
    }

    private fun resumeRecording() {
        if (!isRecording || !isPaused) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                isPaused = false
                totalPausedTimeMs += (SystemClock.elapsedRealtime() - lastPauseTimeMs)
                sendStatusBroadcast(STATUS_STARTED, getElapsedRealTime())
                updateNotification(getElapsedRealTime())
                handler.post(timerRunnable)
            } catch (e: Exception) {
                android.util.Log.e("RecordingService", "Failed to resume", e)
            }
        }
    }

    private fun getElapsedRealTime(): Long {
        var elapsed = SystemClock.elapsedRealtime() - startTimeMs - totalPausedTimeMs
        if (isPaused) {
            elapsed -= (SystemClock.elapsedRealtime() - lastPauseTimeMs)
        }
        return elapsed.coerceAtLeast(0)
    }

    private fun stopRecording() {
        val elapsed = getElapsedRealTime()
        safeStopRecorder()
        sendStatusBroadcast(STATUS_STOPPED, elapsed)
        cleanup()
    }

    private fun safeStopRecorder() {
        try {
            mediaRecorder?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isPaused) {
                    resume() // Sometimes needed before stop
                }
                stop()
                release()
            }
        } catch (e: Exception) {
            android.util.Log.e("RecordingService", "Error stopping", e)
            try { mediaRecorder?.release() } catch (_: Exception) {}
        } finally {
            mediaRecorder = null
        }
    }

    private fun cleanup() {
        isRecording = false
        isPaused = false
        handler.removeCallbacks(timerRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Registrazione Audio", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(elapsedMs: Long): Notification {
        val timeStr = formatDuration(elapsedMs)

        val stopIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, com.transcriber.app.MainActivity::class.java)
        val openPi = PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isPaused) "Registrazione in pausa" else "Registrazione in corso")
            .setContentText("Durata: $timeStr")
            .setSmallIcon(R.drawable.ic_notification_mic)
            .setColor(0xFF49DD7F.toInt())
            .setOngoing(true)
            .setShowWhen(true)
            .setUsesChronometer(!isPaused)
            .setWhen(System.currentTimeMillis() - elapsedMs)
            .setContentIntent(openPi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        // Pausa / Riprendi (solo API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (isPaused) {
                val resumeIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_RESUME }
                val resumePi = PendingIntent.getService(this, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(R.drawable.ic_notification_play, "Riprendi", resumePi)
            } else {
                val pauseIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_PAUSE }
                val pausePi = PendingIntent.getService(this, 3, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(R.drawable.ic_notification_pause, "Pausa", pausePi)
            }
        }

        builder.addAction(R.drawable.ic_notification_stop, "Stop", stopPi)

        return builder.build()
    }

    private fun updateNotification(elapsedMs: Long) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(elapsedMs))
    }

    private fun sendStatusBroadcast(status: String, elapsedMs: Long, error: String = "") {
        sendBroadcast(Intent(BROADCAST_RECORDING_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_ELAPSED_TIME, elapsedMs)
            putExtra(EXTRA_MEETING_ID, meetingId)
            if (error.isNotEmpty()) putExtra(EXTRA_ERROR_MSG, error)
        })
    }

    private fun formatDuration(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            safeStopRecorder()
            sendStatusBroadcast(STATUS_STOPPED, getElapsedRealTime())
        }
        handler.removeCallbacks(timerRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
    }
}
