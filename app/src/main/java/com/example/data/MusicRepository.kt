package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sin

class MusicRepository(private val context: Context, private val musicDao: MusicDao) {

    val allTracks: Flow<List<Track>> = musicDao.getAllTracks()
    val favoriteTracks: Flow<List<Track>> = musicDao.getFavoriteTracks()
    val recentlyPlayed: Flow<List<Track>> = musicDao.getRecentlyPlayedTracks()
    val mostPlayed: Flow<List<Track>> = musicDao.getMostPlayedTracks()
    val playlists: Flow<List<Playlist>> = musicDao.getAllPlaylists()
    val blacklistedFolders: Flow<List<BlacklistedFolder>> = musicDao.getBlacklistedFolders()

    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>> {
        return musicDao.getTracksForPlaylist(playlistId)
    }

    suspend fun insertPlaylist(name: String): Long {
        return musicDao.insertPlaylist(Playlist(name = name))
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        musicDao.deletePlaylist(playlist)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        musicDao.insertPlaylistTrackRef(PlaylistTrackRef(playlistId, trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        musicDao.deletePlaylistTrackRef(playlistId, trackId)
    }

    suspend fun toggleFavorite(track: Track) {
        musicDao.updateTrack(track.copy(isFavorite = !track.isFavorite))
    }

    suspend fun recordPlay(trackId: Long) {
        val track = musicDao.getTrackById(trackId)
        if (track != null) {
            musicDao.updateTrack(track.copy(
                playCount = track.playCount + 1,
                lastPlayedTimestamp = System.currentTimeMillis()
            ))
        }
    }

    suspend fun addBlacklistedFolder(folderPath: String) {
        musicDao.insertBlacklistedFolder(BlacklistedFolder(folderPath))
        // Re-scan or clean tracks up afterwards
    }

    suspend fun removeBlacklistedFolder(folderPath: String) {
        musicDao.deleteBlacklistedFolder(BlacklistedFolder(folderPath))
    }

    /**
     * Seeds procedural demo tracks if database is empty so users can experience NexTune X immediately.
     */
    suspend fun seedDemoSongsIfEmpty() {
        withContext(Dispatchers.IO) {
            val dbTracks = allTracks.first()
            if (dbTracks.isEmpty()) {
                val demoTracks = mutableListOf<Track>()

                // Create 4 procedural audio files
                val tracksInfo = listOf(
                    Triple("lofi_sunset_sphaera.wav", "Lofi Sunset Sphaera", 144.0),
                    Triple("deep_focus_rain.wav", "Deep Focus Rain", 80.0),
                    Triple("cyberpunk_sine_wave.wav", "Cyberpunk Vibrato Wave", 220.0),
                    Triple("cosmic_sleep_drone.wav", "Cosmic Sleep Drone", 55.0)
                )

                tracksInfo.forEach { (fileName, title, frequency) ->
                    try {
                        val file = generateDemoWavFile(context, fileName, frequency, 45, title)
                        demoTracks.add(
                            Track(
                                title = title,
                                artist = "Prince AR",
                                album = "NexTune X Ambient Vol.1",
                                duration = 45000L, // 45 seconds
                                path = file.absolutePath,
                                size = file.length(),
                                mimeType = "audio/wav",
                                dateAdded = System.currentTimeMillis()
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("MusicRepository", "Failed to generate demo track: $title", e)
                    }
                }

                if (demoTracks.isNotEmpty()) {
                    musicDao.insertTracks(demoTracks)
                }
            }
        }
    }

    /**
     * Scans device directories for local music files and registers them.
     */
    suspend fun scanDeviceMusic(): Int = withContext(Dispatchers.IO) {
        val contentResolver: ContentResolver = context.contentResolver
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE
        )

        // Read blacklisted folders to exclude them during query / register
        val blacklist = blacklistedFolders.first().map { it.folderPath }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        var songsAdded = 0
        val cursor: Cursor? = contentResolver.query(uri, projection, selection, null, null)

        val newTracks = mutableListOf<Track>()

        cursor?.use {
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (it.moveToNext()) {
                val path = it.getString(dataCol) ?: continue
                
                // Exclude any blacklisted directories
                val isBlacklisted = blacklist.any { blacklisted -> path.startsWith(blacklisted) }
                if (isBlacklisted) continue

                val title = it.getString(titleCol) ?: "Unknown Song"
                val artist = it.getString(artistCol) ?: "Unknown Artist"
                val album = it.getString(albumCol) ?: "Unknown Album"
                val duration = it.getLong(durationCol)
                val size = it.getLong(sizeCol)

                // Skip extremely short ringtones or invalid sizes
                if (duration < 5000 || size < 1000) continue

                newTracks.add(
                    Track(
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        path = path,
                        size = size,
                        mimeType = "audio/mpeg"
                    )
                )
            }
        }

        if (newTracks.isNotEmpty()) {
            // Find unique (not already in db based on path)
            val existingTracks = musicDao.getAllTracks().first()
            val existingPaths = existingTracks.map { it.path }.toSet()

            val uniqueTracks = newTracks.filter { it.path !in existingPaths }
            if (uniqueTracks.isNotEmpty()) {
                musicDao.insertTracks(uniqueTracks)
                songsAdded = uniqueTracks.size
            }
        }

        return@withContext songsAdded
    }

    private fun generateDemoWavFile(
        context: Context,
        fileName: String,
        frequency: Double,
        durationSeconds: Int,
        title: String
    ): File {
        val file = File(context.filesDir, fileName)
        if (file.exists()) return file

        val sampleRate = 22050
        val numSamples = sampleRate * durationSeconds
        val dataSize = numSamples * 2 // 16-bit mono
        val totalSize = 36 + dataSize

        val header = ByteArray(44)
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'G'.code.toByte() // standard RIFF uses 'F' not 'G', wait, RIFF is 0x52 0x49 0x46 0x46
        header[3] = 'F'.code.toByte()

        header[4] = (totalSize and 0xff).toByte()
        header[5] = ((totalSize shr 8) and 0xff).toByte()
        header[6] = ((totalSize shr 16) and 0xff).toByte()
        header[7] = ((totalSize shr 24) and 0xff).toByte()

        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        header[20] = 1 // Format: PCM
        header[21] = 0
        header[22] = 1 // 1 channel
        header[23] = 0

        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        val byteRate = sampleRate * 2
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        header[32] = 2
        header[33] = 0
        header[34] = 16
        header[35] = 0

        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        header[40] = (dataSize and 0xff).toByte()
        header[41] = ((dataSize shr 8) and 0xff).toByte()
        header[42] = ((dataSize shr 16) and 0xff).toByte()
        header[43] = ((dataSize shr 24) and 0xff).toByte()

        val pcmData = ByteArray(dataSize)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            
            // Sweep frequency + modulation to sound like premium soothing pads
            val lfo = sin(2.0 * Math.PI * 0.12 * t) * 0.4 + 0.6
            val vibrato = sin(2.0 * Math.PI * 6.0 * t) * 2.0
            val curFreq = frequency + vibrato + (lfo * frequency * 0.08)
            
            val angle = 2.0 * Math.PI * curFreq * t
            
            // Generate basic sweeping sine + subharmonic tone
            val valuePrimary = sin(angle)
            val valueSub = sin(angle * 0.5) * 0.4
            val valueCombined = (valuePrimary + valueSub) / 1.4
            
            val shortVal = (valueCombined * 15000.0 * lfo).toInt().coerceIn(-32768, 32767).toShort()

            pcmData[i * 2] = (shortVal.toInt() and 0xff).toByte()
            pcmData[i * 2 + 1] = ((shortVal.toInt() shr 8) and 0xff).toByte()
        }

        file.outputStream().use { fos ->
            fos.write(header)
            fos.write(pcmData)
        }
        return file
    }
}
