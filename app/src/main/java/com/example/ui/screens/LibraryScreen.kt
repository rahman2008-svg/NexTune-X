package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Playlist
import com.example.data.Track
import com.example.ui.viewmodel.MusicViewModel

@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    onTrackSelect: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier
) {
    val allTracks by viewModel.allTracks.collectAsState()
    val favoriteTracks by viewModel.favoriteTracks.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val mostPlayed by viewModel.mostPlayed.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    var activeTab by remember { mutableStateOf("Songs") }
    var searchQuery by remember { mutableStateOf("") }
    
    // Dialog states
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    var showTrackMenuOptionsFor by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylistDialogFor by remember { mutableStateOf<Track?>(null) }

    val context = LocalContext.current

    // Local sorting & searching filter
    val filteredTracks = allTracks.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Large Premium NexTune X search field
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search songs, artists, folders...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_field")
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Horizontal Category Tabs
        val tabs = listOf("Songs", "Favorites", "Playlists", "Recent", "Stats")
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOf(activeTab).coerceAtLeast(0),
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
            divider = {},
            indicator = { tabPositions ->
                val index = tabs.indexOf(activeTab).coerceAtLeast(0)
                if (index < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[index]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    text = { Text(tab, fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Medium) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display contents depending on active category tab
        when (activeTab) {
            "Songs" -> {
                if (filteredTracks.isEmpty()) {
                    EmptyStatePlaceholder(
                        title = if (searchQuery.isNotEmpty()) "No search results match" else "Your Offline Library is empty",
                        subTitle = "Generate interactive premium soothing demo ambient songs below, or scan your external phone partitions to load MP3 music files.",
                        actionButtonText = "Scan Internal Storage",
                        onActionClick = {
                            viewModel.scanMusicFiles { count ->
                                Toast.makeText(context, "Scanned & loaded $count new songs from MediaStore!", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredTracks) { track ->
                            TrackRow(
                                track = track,
                                isCurrentlyPlaying = currentTrack?.id == track.id,
                                onClick = { onTrackSelect(track, filteredTracks) },
                                onMenuClick = { showTrackMenuOptionsFor = track }
                            )
                        }
                    }
                }
            }

            "Favorites" -> {
                if (favoriteTracks.isEmpty()) {
                    EmptyStatePlaceholder(
                        title = "No Favorites found",
                        subTitle = "Tap the heart icon in the music player to add soothing offline tracks here.",
                        icon = Icons.Default.FavoriteBorder
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(favoriteTracks) { track ->
                            TrackRow(
                                track = track,
                                isCurrentlyPlaying = currentTrack?.id == track.id,
                                onClick = { onTrackSelect(track, favoriteTracks) },
                                onMenuClick = { showTrackMenuOptionsFor = track }
                            )
                        }
                    }
                }
            }

            "Playlists" -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "My Custom Playlists",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Button(
                            onClick = { showCreatePlaylistDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Playlists", fontSize = 12.sp)
                        }
                    }

                    if (playlists.isEmpty()) {
                        EmptyStatePlaceholder(
                            title = "No Playlists yet",
                            subTitle = "Organize customized offline libraries by creation date.",
                            icon = Icons.AutoMirrored.Filled.QueueMusic
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(playlists) { playlist ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.loadPlaylistTracks(playlist.id)
                                            // Quick select the first track of this playlist automatically as cue if user plays it!
                                            activeTab = "Songs"
                                            Toast.makeText(context, "Loaded playlist: ${playlist.name}", Toast.LENGTH_SHORT).show()
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                            contentDescription = "Playlist representation",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = playlist.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "Tap to view list",
                                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deletePlaylist(playlist) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Playlist",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "Recent" -> {
                if (recentlyPlayed.isEmpty()) {
                    EmptyStatePlaceholder(
                        title = "None recently played",
                        subTitle = "Songs play history will automatically display here for quick resume.",
                        icon = Icons.Default.History
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recentlyPlayed) { track ->
                            TrackRow(
                                track = track,
                                isCurrentlyPlaying = currentTrack?.id == track.id,
                                onClick = { onTrackSelect(track, recentlyPlayed) },
                                onMenuClick = { showTrackMenuOptionsFor = track }
                            )
                        }
                    }
                }
            }

            "Stats" -> {
                // MUSIC STATISTICS DISPLAY
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Library Status Tracker 📊",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Total Songs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                    Text("${allTracks.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Column {
                                    Text("Playlists Count", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                    Text("${playlists.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Column {
                                    Text("Starred Tracks", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                    Text("${favoriteTracks.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }

                    Text(
                        text = "Most Played (Repeat Loops Count)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (mostPlayed.isEmpty()) {
                        EmptyStatePlaceholder(
                            title = "No statistics logged yet",
                            subTitle = "Begin looping demo frequency compositions to construct real-time visual stats here.",
                            icon = Icons.Default.BarChart
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(mostPlayed) { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .padding(12.dp)
                                        .clickable { onTrackSelect(track, mostPlayed) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Plays count",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text("${track.artist} • Played ${track.playCount} times", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // CREATE PLAYLIST DIALOG
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Custom Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistNameInput.isNotBlank()) {
                            viewModel.createPlaylist(playlistNameInput)
                            playlistNameInput = ""
                            showCreatePlaylistDialog = false
                            Toast.makeText(context, "Playlist created successfully!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ACTIONS ROW CONTEXT MENU POPUP FOR INDIVIDUAL TRACKS
    showTrackMenuOptionsFor?.let { track ->
        AlertDialog(
            onDismissRequest = { showTrackMenuOptionsFor = null },
            title = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.toggleFavorite(track)
                                showTrackMenuOptionsFor = null
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite icon tint",
                            tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(if (track.isFavorite) "Remove from Favorites" else "Add to Favorites")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddToPlaylistDialogFor = track
                                showTrackMenuOptionsFor = null
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add playlist icon", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Add to Playlist...")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Add parent folder lock exclusion blacklist
                                val parentFolder = track.path.substringBeforeLast("/")
                                viewModel.addBlacklistedFolder(parentFolder)
                                showTrackMenuOptionsFor = null
                                Toast.makeText(context, "Folder blacklisted and locked: $parentFolder", Toast.LENGTH_LONG).show()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Block, contentDescription = "Lock", tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Blacklist Folder / Lock Layout")
                    }
                }
            },
            confirmButton = {}
        )
    }

    // ADD TO PLAYLIST DIALOG SELECTION
    showAddToPlaylistDialogFor?.let { track ->
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialogFor = null },
            title = { Text("Select playlist to append") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists found. Please create one first!")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addTrackToPlaylist(playlist.id, track.id)
                                        showAddToPlaylistDialogFor = null
                                        Toast.makeText(context, "Added '${track.title}' to playlist: ${playlist.name}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Playlist option link", tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(playlist.name, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToPlaylistDialogFor = null }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@Composable
fun TrackRow(
    track: Track,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("track_row_${track.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyPlaying) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (isCurrentlyPlaying) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else null
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini track cover image
            Image(
                painter = painterResource(id = R.drawable.img_album_art_placeholder),
                contentDescription = "album image track placeholder",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist}  •  ${formatDuration(track.duration)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Beautiful status icon if audio is currently looping
            if (isCurrentlyPlaying) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = "Looping currently",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Track settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyStatePlaceholder(
    title: String,
    subTitle: String,
    icon: ImageVector = Icons.Default.MusicNote,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Empty state icon",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subTitle,
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        if (actionButtonText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onActionClick) {
                Text(actionButtonText)
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val sec = (millis / 1000) % 60
    val min = (millis / (1000 * 60)) % 60
    return String.format("%02d:%02d", min, sec)
}
