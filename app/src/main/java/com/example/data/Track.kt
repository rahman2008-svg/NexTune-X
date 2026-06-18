package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val size: Long,
    val mimeType: String = "audio/mpeg",
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0,
    val dateAdded: Long = System.currentTimeMillis()
)
