package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // TRACKS
    @Query("SELECT * FROM tracks WHERE NOT EXISTS (SELECT 1 FROM blacklisted_folders WHERE tracks.path LIKE blacklisted_folders.folderPath || '%') ORDER BY title ASC")
    fun getAllTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT 50")
    fun getRecentlyPlayedTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY playCount DESC LIMIT 50")
    fun getMostPlayedTracks(): Flow<List<Track>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: Track): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<Track>)

    @Update
    suspend fun updateTrack(track: Track)

    @Delete
    suspend fun deleteTrack(track: Track)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: Long)

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: Long): Track?

    // PLAYLISTS
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    // PLAYLIST REFS (tracks in playlist)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTrackRef(ref: PlaylistTrackRef)

    @Query("DELETE FROM playlist_track_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deletePlaylistTrackRef(playlistId: Long, trackId: Long)

    @Query("""
        SELECT t.* FROM tracks t 
        INNER JOIN playlist_track_ref r ON t.id = r.trackId 
        WHERE r.playlistId = :playlistId
        ORDER BY t.title ASC
    """)
    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>>

    // BLACKLIST
    @Query("SELECT * FROM blacklisted_folders")
    fun getBlacklistedFolders(): Flow<List<BlacklistedFolder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlacklistedFolder(folder: BlacklistedFolder)

    @Delete
    suspend fun deleteBlacklistedFolder(folder: BlacklistedFolder)
}
