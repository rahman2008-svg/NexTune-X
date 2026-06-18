package com.example.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.media.session.MediaSession
import android.media.session.PlaybackState as SessionPlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.MainActivity
import com.example.R
import com.example.data.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Collections
import java.util.Timer
import java.util.TimerTask

enum class RepeatMode {
    NONE, ONE, ALL
}

object PlaybackState {
    val isPlaying = MutableStateFlow(false)
    val currentTrack = MutableStateFlow<Track?>(null)
    val currentPosition = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val queue = MutableStateFlow<List<Track>>(emptyList())
    val currentIndex = MutableStateFlow(0)
    val isShuffle = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(RepeatMode.NONE)
    val playbackSpeed = MutableStateFlow(1.0f)
    val pitchControl = MutableStateFlow(1.0f)

    // Audio FX
    val bassBoostLevel = MutableStateFlow(0) // 0 to 1000
    val virtualizerLevel = MutableStateFlow(0) // 0 to 1000
    val loudnessLevel = MutableStateFlow(0) // 0 to 1000
    val eqEnabled = MutableStateFlow(false)
    val eqBands = MutableStateFlow<Map<Int, Int>>(emptyMap()) // Band index -> Level in millibels
    val eqFrequencies = MutableStateFlow<List<Int>>(emptyList()) // Central frequencies in Hz
    val bandCount = MutableStateFlow(0)

    // Sleep Timer
    val sleepTimerMinutesLeft = MutableStateFlow(-1)
}

@Suppress("DEPRECATION")
class MusicService : Service(), AudioManager.OnAudioFocusChangeListener {

    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSession? = null

    // Audio Effects
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var progressTimer: Timer? = null

    private var sleepTimer: Timer? = null

    private val CHANNEL_ID = "nextune_playback_channel"
    private val NOTIFICATION_ID = 404

    companion object {
        @Volatile
        var instance: MusicService? = null

        const val ACTION_PLAY = "com.example.nextunex.play"
        const val ACTION_PAUSE = "com.example.nextunex.pause"
        const val ACTION_PREVIOUS = "com.example.nextunex.previous"
        const val ACTION_NEXT = "com.example.nextunex.next"
        const val ACTION_STOP = "com.example.nextunex.stop"
        const val ACTION_SEEK = "com.example.nextunex.seek"
        const val ACTION_TOGGLE_PLAY = "com.example.nextunex.toggle"

        const val EXTRA_TRACK_ID = "extra_track_id"
        const val EXTRA_SEEK_POSITION = "extra_seek_pos"
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pausePlayback()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        createNotificationChannel()
        setupMediaSession()

        // Sync visual update timer
        startProgressUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val trackId = intent.getLongExtra(EXTRA_TRACK_ID, -1L)
                if (trackId != -1L) {
                    val index = PlaybackState.queue.value.indexOfFirst { it.id == trackId }
                    if (index != -1) {
                        PlaybackState.currentIndex.value = index
                        playTrack(PlaybackState.queue.value[index])
                    }
                } else {
                    resumePlayback()
                }
            }
            ACTION_PAUSE -> pausePlayback()
            ACTION_TOGGLE_PLAY -> {
                if (PlaybackState.isPlaying.value) {
                    pausePlayback()
                } else {
                    resumePlayback()
                }
            }
            ACTION_PREVIOUS -> playPrevious()
            ACTION_NEXT -> playNext()
            ACTION_STOP -> stopPlayback()
            ACTION_SEEK -> {
                val progress = intent.getLongExtra(EXTRA_SEEK_POSITION, 0L)
                seekTo(progress)
            }
        }
        return START_NOT_STICKY
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "NexTuneXMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { resumePlayback() }
                override fun onPause() { pausePlayback() }
                override fun onSkipToNext() { playNext() }
                override fun onSkipToPrevious() { playPrevious() }
                override fun onStop() { stopPlayback() }
                override fun onSeekTo(pos: Long) { seekTo(pos) }
            })
            isActive = true
        }
    }

    private fun playTrack(track: Track) {
        if (requestAudioFocus() != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(track.path)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                prepare()
                
                // Audio speed (Android 6.0+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val speed = PlaybackState.playbackSpeed.value
                    val pitch = PlaybackState.pitchControl.value
                    if (speed != 1.0f || pitch != 1.0f) {
                        playbackParams = playbackParams.setSpeed(speed).setPitch(pitch)
                    }
                }

                setOnCompletionListener {
                    handleTrackCompletion()
                }
                start()
            }

            PlaybackState.currentTrack.value = track
            PlaybackState.isPlaying.value = true
            PlaybackState.duration.value = track.duration

            setupAudioEffects(mediaPlayer?.audioSessionId ?: 0)
            updateNotification()
            updateMediaSessionState(SessionPlaybackState.STATE_PLAYING)

        } catch (e: Exception) {
            Log.e("MusicService", "Error playing track: ${track.title}", e)
            playNext() // Fallback to next on error
        }
    }

    private fun handleTrackCompletion() {
        when (PlaybackState.repeatMode.value) {
            RepeatMode.ONE -> {
                PlaybackState.currentTrack.value?.let { playTrack(it) }
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.NONE -> {
                val isLast = PlaybackState.currentIndex.value == PlaybackState.queue.value.size - 1
                if (isLast) {
                    pausePlayback()
                    seekTo(0)
                } else {
                    playNext()
                }
            }
        }
    }

    private fun resumePlayback() {
        val current = PlaybackState.currentTrack.value
        if (current == null && PlaybackState.queue.value.isNotEmpty()) {
            val index = PlaybackState.currentIndex.value
            playTrack(PlaybackState.queue.value[index])
            return
        }

        if (current != null && mediaPlayer != null) {
            if (requestAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaPlayer?.start()
                PlaybackState.isPlaying.value = true
                updateNotification()
                updateMediaSessionState(SessionPlaybackState.STATE_PLAYING)
            }
        }
    }

    private fun pausePlayback() {
        mediaPlayer?.pause()
        PlaybackState.isPlaying.value = false
        updateNotification()
        updateMediaSessionState(SessionPlaybackState.STATE_PAUSED)
    }

    private fun playNext() {
        val queue = PlaybackState.queue.value
        if (queue.isEmpty()) return

        var nextIndex = PlaybackState.currentIndex.value + 1
        if (PlaybackState.isShuffle.value) {
            nextIndex = (0 until queue.size).random()
        } else if (nextIndex >= queue.size) {
            nextIndex = 0
        }

        PlaybackState.currentIndex.value = nextIndex
        playTrack(queue[nextIndex])
    }

    private fun playPrevious() {
        val queue = PlaybackState.queue.value
        if (queue.isEmpty()) return

        // If played more than 3 seconds, restart current song
        if (PlaybackState.currentPosition.value > 3000) {
            seekTo(0)
            return
        }

        var prevIndex = PlaybackState.currentIndex.value - 1
        if (prevIndex < 0) {
            prevIndex = queue.size - 1
        }

        PlaybackState.currentIndex.value = prevIndex
        playTrack(queue[prevIndex])
    }

    private fun seekTo(pos: Long) {
        mediaPlayer?.seekTo(pos.toInt())
        PlaybackState.currentPosition.value = pos
        updateMediaSessionState(
            if (PlaybackState.isPlaying.value) SessionPlaybackState.STATE_PLAYING else SessionPlaybackState.STATE_PAUSED
        )
    }

    private fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        PlaybackState.isPlaying.value = false
        PlaybackState.currentTrack.value = null
        updateMediaSessionState(SessionPlaybackState.STATE_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun setupAudioEffects(audioSessionId: Int) {
        if (audioSessionId == 0) return
        try {
            // Equalizer
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply {
                val bands = numberOfBands
                val freqList = mutableListOf<Int>()
                val bandsMap = mutableMapOf<Int, Int>()
                
                for (i in 0 until bands) {
                    freqList.add(getCenterFreq(i.toShort()) / 1000) // in Hz
                    bandsMap[i] = getBandLevel(i.toShort()).toInt()
                }

                PlaybackState.bandCount.value = bands.toInt()
                PlaybackState.eqFrequencies.value = freqList
                PlaybackState.eqBands.value = bandsMap

                // Apply saved settings
                enabled = PlaybackState.eqEnabled.value
                val currentLevels = PlaybackState.eqBands.value
                currentLevels.forEach { (band, level) ->
                    setBandLevel(band.toShort(), level.toShort())
                }
            }

            // Bass Boost
            bassBoost?.release()
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
                setStrength(PlaybackState.bassBoostLevel.value.toShort())
            }

            // Virtualizer
            virtualizer?.release()
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
                setStrength(PlaybackState.virtualizerLevel.value.toShort())
            }

            // Loudness Enhancer (API 19+)
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                enabled = true
                setTargetGain(PlaybackState.loudnessLevel.value)
            }

        } catch (e: Exception) {
            Log.e("MusicService", "Error setting up audio effects", e)
        }
    }

    // Public setter methods called by UI models or direct interactions
    fun updateEqualizerBand(band: Int, millibels: Int) {
        try {
            equalizer?.setBandLevel(band.toShort(), millibels.toShort())
            val updated = PlaybackState.eqBands.value.toMutableMap()
            updated[band] = millibels
            PlaybackState.eqBands.value = updated
        } catch (e: Exception) {
            Log.e("MusicService", "Failed to set equalizer level", e)
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            PlaybackState.eqEnabled.value = enabled
        } catch (e: Exception) {
            PlaybackState.eqEnabled.value = enabled
        }
    }

    fun updateBassBoost(strength: Int) {
        try {
            bassBoost?.setStrength(strength.toShort())
            PlaybackState.bassBoostLevel.value = strength
        } catch (e: Exception) {
            PlaybackState.bassBoostLevel.value = strength
        }
    }

    fun updateVirtualizer(strength: Int) {
        try {
            virtualizer?.setStrength(strength.toShort())
            PlaybackState.virtualizerLevel.value = strength
        } catch (e: Exception) {
            PlaybackState.virtualizerLevel.value = strength
        }
    }

    fun updateLoudness(gainMillibels: Int) {
        try {
            loudnessEnhancer?.setTargetGain(gainMillibels)
            PlaybackState.loudnessLevel.value = gainMillibels
        } catch (e: Exception) {
            PlaybackState.loudnessLevel.value = gainMillibels
        }
    }

    fun updatePlaybackSpeed(speed: Float) {
        PlaybackState.playbackSpeed.value = speed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.playbackParams = it.playbackParams.setSpeed(speed).setPitch(PlaybackState.pitchControl.value)
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicService", "Failed to set playback speed", e)
            }
        }
    }

    fun updatePitchControl(pitch: Float) {
        PlaybackState.pitchControl.value = pitch
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.playbackParams = it.playbackParams.setSpeed(PlaybackState.playbackSpeed.value).setPitch(pitch)
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicService", "Failed to set pitch control", e)
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimer?.cancel()
        if (minutes <= 0) {
            PlaybackState.sleepTimerMinutesLeft.value = -1
            return
        }

        PlaybackState.sleepTimerMinutesLeft.value = minutes
        sleepTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    val currentVal = PlaybackState.sleepTimerMinutesLeft.value
                    if (currentVal <= 1) {
                        PlaybackState.sleepTimerMinutesLeft.value = -1
                        cancel()
                        // Pause audio in main thread
                        Handler(Looper.getMainLooper()).post {
                            pausePlayback()
                        }
                    } else {
                        PlaybackState.sleepTimerMinutesLeft.value = currentVal - 1
                    }
                }
            }, 60000L, 60000L) // updates every 1 minute
        }
    }

    private fun requestAudioFocus(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            return audioManager?.requestAudioFocus(audioFocusRequest!!) ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            @Suppress("DEPRECATION")
            return audioManager?.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Resume play if paused on focus loss transiently
                mediaPlayer?.setVolume(1.0f, 1.0f)
                if (!PlaybackState.isPlaying.value) {
                    resumePlayback()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Dim volume
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NexTune X Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows controls for ongoing audio play"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val track = PlaybackState.currentTrack.value ?: return

        val playPauseIcon = if (PlaybackState.isPlaying.value) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val actionPrevious = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_previous), "Previous",
                getServicePendingIntent(ACTION_PREVIOUS)
            ).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Action.Builder(
                android.R.drawable.ic_media_previous, "Previous",
                getServicePendingIntent(ACTION_PREVIOUS)
            ).build()
        }

        val actionToggle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, playPauseIcon), "Play/Pause",
                getServicePendingIntent(ACTION_TOGGLE_PLAY)
            ).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Action.Builder(
                playPauseIcon, "Play/Pause",
                getServicePendingIntent(ACTION_TOGGLE_PLAY)
            ).build()
        }

        val actionNext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_next), "Next",
                getServicePendingIntent(ACTION_NEXT)
            ).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Action.Builder(
                android.R.drawable.ic_media_next, "Next",
                getServicePendingIntent(ACTION_NEXT)
            ).build()
        }

        val notification = notificationBuilder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(track.album)
            .setContentIntent(pendingIntent)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(actionPrevious)
            .addAction(actionToggle)
            .addAction(actionNext)
            .setOngoing(PlaybackState.isPlaying.value)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun getServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun updateMediaSessionState(state: Int) {
        val speed = PlaybackState.playbackSpeed.value
        val actions = SessionPlaybackState.ACTION_PLAY or
                SessionPlaybackState.ACTION_PAUSE or
                SessionPlaybackState.ACTION_SKIP_TO_NEXT or
                SessionPlaybackState.ACTION_SKIP_TO_PREVIOUS or
                SessionPlaybackState.ACTION_SEEK_TO or
                SessionPlaybackState.ACTION_STOP

        val stateBuilder = SessionPlaybackState.Builder()
            .setActions(actions)
            .setState(state, mediaPlayer?.currentPosition?.toLong() ?: 0L, speed)
        
        mediaSession?.setPlaybackState(stateBuilder.build())
    }

    private fun startProgressUpdates() {
        progressTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (PlaybackState.isPlaying.value) {
                        mediaPlayer?.let {
                            PlaybackState.currentPosition.value = it.currentPosition.toLong()
                        }
                    }
                }
            }, 0L, 500L) // every 500ms
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceJob.cancel()
        progressTimer?.cancel()
        sleepTimer?.cancel()
        unregisterReceiver(noisyReceiver)
        mediaPlayer?.release()
        mediaSession?.release()
    }
}
