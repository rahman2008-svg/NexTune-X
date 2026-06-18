package com.example.data

import androidx.room.Entity

@Entity(
    tableName = "playlist_track_ref",
    primaryKeys = ["playlistId", "trackId"]
)
data class PlaylistTrackRef(
    val playlistId: Long,
    val trackId: Long
)
