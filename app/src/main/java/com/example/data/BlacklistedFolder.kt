package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blacklisted_folders")
data class BlacklistedFolder(
    @PrimaryKey val folderPath: String,
    val addedAt: Long = System.currentTimeMillis()
)
