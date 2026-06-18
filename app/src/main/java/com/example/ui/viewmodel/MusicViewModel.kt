package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.MusicService
import com.example.service.PlaybackState
import com.example.service.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicViewModel(
    application: Application,
    private val repository: MusicRepository
) : AndroidViewModel(application) {

    // Music lists from Database
    val allTracks: StateFlow<List<Track>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTracks: StateFlow<List<Track>> = repository.favoriteTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<Track>> = repository.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayed: StateFlow<List<Track>> = repository.mostPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blacklistedFolders: StateFlow<List<BlacklistedFolder>> = repository.blacklistedFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tracks for the currently selected/viewed custom playlist
    private val _currentPlaylistTracks = MutableStateFlow<List<Track>>(emptyList())
    val currentPlaylistTracks: StateFlow<List<Track>> = _currentPlaylistTracks

    // Search query state
    val searchQuery = MutableStateFlow("")

    // Observing Real-time Playback State from the background player (MusicService via PlaybackState)
    val isPlaying = PlaybackState.isPlaying
    val currentTrack = PlaybackState.currentTrack
    val currentPosition = PlaybackState.currentPosition
    val duration = PlaybackState.duration
    val queue = PlaybackState.queue
    val currentIndex = PlaybackState.currentIndex
    val isShuffle = PlaybackState.isShuffle
    val repeatMode = PlaybackState.repeatMode
    val playbackSpeed = PlaybackState.playbackSpeed
    val pitchControl = PlaybackState.pitchControl

    // Audio effects
    val bassBoostLevel = PlaybackState.bassBoostLevel
    val virtualizerLevel = PlaybackState.virtualizerLevel
    val loudnessLevel = PlaybackState.loudnessLevel
    val eqEnabled = PlaybackState.eqEnabled
    val eqBands = PlaybackState.eqBands
    val eqFrequencies = PlaybackState.eqFrequencies
    val bandCount = PlaybackState.bandCount

    // Sleep Timer
    val sleepTimerMinutesLeft = PlaybackState.sleepTimerMinutesLeft

    init {
        viewModelScope.launch {
            // Seed 4 high-quality procedural sweeping audio tracks if database has no records
            repository.seedDemoSongsIfEmpty()
        }
    }

    fun playTrack(track: Track, currentContextQueue: List<Track>) {
        viewModelScope.launch {
            PlaybackState.queue.value = currentContextQueue
            val index = currentContextQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            PlaybackState.currentIndex.value = index

            // Launch the service with action play and track ID
            val context = getApplication<Application>().applicationContext
            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_PLAY
                putExtra(MusicService.EXTRA_TRACK_ID, track.id)
            }
            context.startService(intent)
            
            // Record track play count
            repository.recordPlay(track.id)
        }
    }

    fun togglePlay() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_TOGGLE_PLAY
        }
        context.startService(intent)
    }

    fun playNext() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_NEXT
        }
        context.startService(intent)
    }

    fun playPrevious() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_PREVIOUS
        }
        context.startService(intent)
    }

    fun seekTo(progress: Long) {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_SEEK
            putExtra(MusicService.EXTRA_SEEK_POSITION, progress)
        }
        context.startService(intent)
    }

    fun toggleShuffle() {
        PlaybackState.isShuffle.value = !PlaybackState.isShuffle.value
    }

    fun toggleRepeatMode() {
        val current = PlaybackState.repeatMode.value
        val next = when (current) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        PlaybackState.repeatMode.value = next
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            repository.toggleFavorite(track)
        }
    }

    // Custom Playlists Operations
    private var playlistTracksJob: kotlinx.coroutines.Job? = null

    fun loadPlaylistTracks(playlistId: Long) {
        playlistTracksJob?.cancel()
        playlistTracksJob = viewModelScope.launch {
            repository.getTracksForPlaylist(playlistId).collect {
                _currentPlaylistTracks.value = it
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.insertPlaylist(name)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
            // Refresh
            loadPlaylistTracks(playlistId)
        }
    }

    // Audio effects operations (forward directly to active MusicService instance)
    fun setEqualizerEnabled(enabled: Boolean) {
        MusicService.instance?.setEqualizerEnabled(enabled) ?: run {
            PlaybackState.eqEnabled.value = enabled
        }
    }

    fun updateEqualizerBand(band: Int, millibels: Int) {
        MusicService.instance?.updateEqualizerBand(band, millibels) ?: run {
            val updated = PlaybackState.eqBands.value.toMutableMap()
            updated[band] = millibels
            PlaybackState.eqBands.value = updated
        }
    }

    fun updateBassBoost(strength: Int) {
        MusicService.instance?.updateBassBoost(strength) ?: run {
            PlaybackState.bassBoostLevel.value = strength
        }
    }

    fun updateVirtualizer(strength: Int) {
        MusicService.instance?.updateVirtualizer(strength) ?: run {
            PlaybackState.virtualizerLevel.value = strength
        }
    }

    fun updateLoudness(gainMillibels: Int) {
        MusicService.instance?.updateLoudness(gainMillibels) ?: run {
            PlaybackState.loudnessLevel.value = gainMillibels
        }
    }

    fun updatePlaybackSpeed(speed: Float) {
        MusicService.instance?.updatePlaybackSpeed(speed) ?: run {
            PlaybackState.playbackSpeed.value = speed
        }
    }

    fun updatePitchControl(pitch: Float) {
        MusicService.instance?.updatePitchControl(pitch) ?: run {
            PlaybackState.pitchControl.value = pitch
        }
    }

    fun startSleepTimer(minutes: Int) {
        MusicService.instance?.startSleepTimer(minutes) ?: run {
            PlaybackState.sleepTimerMinutesLeft.value = minutes
        }
    }

    // Folder blacklist operations
    fun addBlacklistedFolder(folderPath: String) {
        viewModelScope.launch {
            repository.addBlacklistedFolder(folderPath)
        }
    }

    fun removeBlacklistedFolder(folderPath: String) {
        viewModelScope.launch {
            repository.removeBlacklistedFolder(folderPath)
        }
    }

    fun scanMusicFiles(onScanComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.scanDeviceMusic()
            onScanComplete(count)
        }
    }
}

class MusicViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicViewModel::class.java)) {
            val appDb = AppDatabase.getDatabase(context)
            val repository = MusicRepository(context.applicationContext, appDb.musicDao())
            @Suppress("UNCHECKED_CAST")
            return MusicViewModel(context.applicationContext as Application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
