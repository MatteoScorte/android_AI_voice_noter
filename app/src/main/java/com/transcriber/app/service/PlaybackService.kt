package com.transcriber.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Foreground service that owns the MediaPlayer for audio playback.
 *
 * Communication model:
 *  - ViewModel → Service : [startService] with ACTION_* intents (commands)
 *  - Service → ViewModel : [PlaybackBinder.events] SharedFlow (state events)
 *  - ViewModel polls position: [PlaybackBinder.currentPositionMs] every 80ms
 */
class PlaybackService : Service() {

    // ── Public API ────────────────────────────────────────────────────────────

    companion object {
        const val ACTION_INIT       = "com.transcriber.app.ACTION_INIT"
        const val ACTION_PLAY_PAUSE = "com.transcriber.app.ACTION_PLAY_PAUSE"
        const val ACTION_STOP       = "com.transcriber.app.ACTION_STOP"
        const val ACTION_SEEK       = "com.transcriber.app.ACTION_SEEK"

        const val EXTRA_FILE_PATH   = "filePath"
        const val EXTRA_SEEK_MS     = "seekMs"

        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID      = "playback_channel"
    }

    sealed class PlaybackEvent {
        /** MediaPlayer.prepare() completed — player is ready to play. */
        object Ready : PlaybackEvent()
        /** Playback reached the end of the file. */
        object Completed : PlaybackEvent()
        /** Duration is available (emitted together with Ready). */
        data class DurationAvailable(val ms: Long) : PlaybackEvent()
    }

    inner class PlaybackBinder : Binder() {
        fun currentPositionMs(): Long =
            try { mediaPlayer?.currentPosition?.toLong() ?: 0L } catch (_: Exception) { 0L }
        fun durationMs(): Long =
            try { mediaPlayer?.duration?.toLong() ?: 0L } catch (_: Exception) { 0L }
        fun isReady(): Boolean   = isPlayerReady
        fun isPlaying(): Boolean = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }
        val events: SharedFlow<PlaybackEvent> get() = _events.asSharedFlow()
    }

    // ── Internal state ────────────────────────────────────────────────────────

    private val _events      = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 8)
    private val binder       = PlaybackBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaPlayer:    MediaPlayer?       = null
    private var mediaSession:   MediaSessionCompat? = null
    private var isPlayerReady   = false
    private var currentFilePath: String? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INIT       -> handleInit(
                intent.getStringExtra(EXTRA_FILE_PATH) ?: return START_NOT_STICKY
            )
            ACTION_PLAY_PAUSE -> handlePlayPause()
            ACTION_STOP       -> handleStop()
            ACTION_SEEK       -> handleSeek(intent.getLongExtra(EXTRA_SEEK_MS, 0L))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        mediaSession?.release()
        mediaSession = null
    }

    // ── Command handlers ──────────────────────────────────────────────────────

    private fun handleInit(filePath: String) {
        // On config-change reload the ViewModel sends ACTION_INIT again with the same path.
        // Re-emit state events so the ViewModel can restore its UI without reloading the file.
        if (currentFilePath == filePath && isPlayerReady) {
            serviceScope.launch {
                _events.emit(PlaybackEvent.DurationAvailable(mediaPlayer?.duration?.toLong() ?: 0L))
                _events.emit(PlaybackEvent.Ready)
            }
            return
        }

        currentFilePath = filePath
        isPlayerReady   = false

        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null

        ensureMediaSession()
        startForegroundCompat(NOTIFICATION_ID, buildNotification(isPlaying = false))

        serviceScope.launch(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) return@launch

            val mp = MediaPlayer()
            try {
                mp.setDataSource(file.absolutePath)
                mp.prepare()
                withContext(Dispatchers.Main) {
                    mediaPlayer   = mp
                    isPlayerReady = true
                    mp.setOnCompletionListener {
                        updateNotification(isPlaying = false)
                        serviceScope.launch { _events.emit(PlaybackEvent.Completed) }
                    }
                    updateNotification(isPlaying = false)
                    _events.emit(PlaybackEvent.DurationAvailable(mp.duration.toLong()))
                    _events.emit(PlaybackEvent.Ready)
                }
            } catch (_: Exception) {
                mp.release()
            }
        }
    }

    private fun handlePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            updateNotification(isPlaying = false)
        } else {
            mp.start()
            updateNotification(isPlaying = true)
        }
    }

    private fun handleStop() {
        try { mediaPlayer?.apply { if (isPlaying) stop(); release() } } catch (_: Exception) {}
        mediaPlayer   = null
        isPlayerReady = false
        stopForegroundCompat()
        stopSelf()
    }

    private fun handleSeek(ms: Long) {
        mediaPlayer?.seekTo(ms.toInt())
    }

    // ── MediaSession & notification ───────────────────────────────────────────

    private fun ensureMediaSession() {
        if (mediaSession != null) return
        mediaSession = MediaSessionCompat(this, "PlaybackService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay()            = handlePlayPause()
                override fun onPause()           = handlePlayPause()
                override fun onStop()            = handleStop()
                override fun onSeekTo(pos: Long) = handleSeek(pos)
            })
            isActive = true
        }
    }

    private fun updateNotification(isPlaying: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(isPlaying))
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val playPauseIntent = PendingIntent.getService(
            this, 0,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Audio Transcriber")
            .setContentText(if (isPlaying) "Riproduzione in corso" else "In pausa")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pausa" else "Play",
                playPauseIntent
            )
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(isPlaying)

        mediaSession?.let { session ->
            builder.setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0)
            )
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Riproduzione audio",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                id, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(id, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }
}
