package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Track
import com.example.service.MusicService
import com.example.ui.components.FullPlayer
import com.example.ui.components.MiniPlayer
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MusicViewModel
import com.example.ui.viewmodel.MusicViewModelFactory

class MainActivity : ComponentActivity() {

    // Permission launcher for storage scans and notification postings
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val isStorageGranted = permissions[storagePermission] ?: false

        if (isStorageGranted) {
            Toast.makeText(this, "Storage scanner permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission denied. Real-time folder scans will fail.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check and prompt storage access on startup
        checkAndPromptPermissions()

        setContent {
            // Local state to manage visual theme customizations
            var isDarkTheme by remember { mutableStateOf(true) } // default true for luxury dark vibe
            var amoledThemeEnabled by remember { mutableStateOf(true) } // default true for AMOLED black theme
            var dynamicColorEnabled by remember { mutableStateOf(false) }

            // Apply custom theme colors
            MyApplicationTheme(
                darkTheme = isDarkTheme,
                dynamicColor = dynamicColorEnabled
            ) {
                // Determine screen background override (AMOLED pitch black support)
                val appBgColor = if (isDarkTheme && amoledThemeEnabled) {
                    Color.Black
                } else {
                    MaterialTheme.colorScheme.background
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = appBgColor
                ) {
                    val viewModel: MusicViewModel = viewModel(
                        factory = MusicViewModelFactory(LocalContext.current)
                    )

                    NexTuneDashboard(
                        viewModel = viewModel,
                        amoledThemeEnabled = amoledThemeEnabled,
                        onAmoledToggle = { amoledThemeEnabled = it },
                        dynamicColorEnabled = dynamicColorEnabled,
                        onDynamicColorToggle = { dynamicColorEnabled = it },
                        isDarkTheme = isDarkTheme,
                        onDarkThemeToggle = { isDarkTheme = it }
                    )
                }
            }
        }
    }

    private fun checkAndPromptPermissions() {
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val permissionsToRequest = mutableListOf(storagePermission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}

@Composable
fun NexTuneDashboard(
    viewModel: MusicViewModel,
    amoledThemeEnabled: Boolean,
    onAmoledToggle: (Boolean) -> Unit,
    dynamicColorEnabled: Boolean,
    onDynamicColorToggle: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    onDarkThemeToggle: (Boolean) -> Unit
) {
    var activeDashboardTab by remember { mutableStateOf("Library") }
    var showFullPlayer by remember { mutableStateOf(false) }

    // Synchronized playback flows from music player engine
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val progress by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val sleepTimerMinutesLeft by viewModel.sleepTimerMinutesLeft.collectAsState()

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // 1. Floating Mini Player bar (lays beautifully above navigation tabs)
                MiniPlayer(
                    track = currentTrack,
                    isPlaying = isPlaying,
                    progress = progress,
                    duration = duration,
                    onTogglePlay = { viewModel.togglePlay() },
                    onNext = { viewModel.playNext() },
                    onClick = { showFullPlayer = true }
                )

                // 2. Primary Navigation Bar
                NavigationBar(
                    containerColor = if (isDarkTheme && amoledThemeEnabled) Color.Black else MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .height(64.dp)
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    val items = listOf("Library", "Equalizer", "Settings", "About")
                    val icons = listOf(Icons.Default.MusicNote, Icons.Default.Equalizer, Icons.Default.Settings, Icons.Default.Info)

                    items.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = activeDashboardTab == title,
                            onClick = { activeDashboardTab = title },
                            icon = {
                                Icon(
                                    imageVector = icons[index],
                                    contentDescription = title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeDashboardTab) {
                "Library" -> LibraryScreen(
                    viewModel = viewModel,
                    onTrackSelect = { track, contextQueue ->
                        viewModel.playTrack(track, contextQueue)
                    }
                )
                "Equalizer" -> EqualizerScreen(
                    viewModel = viewModel
                )
                "Settings" -> SettingsScreen(
                    viewModel = viewModel,
                    amoledThemeEnabled = amoledThemeEnabled,
                    onAmoledToggle = onAmoledToggle,
                    dynamicColorEnabled = dynamicColorEnabled,
                    onDynamicColorToggle = onDynamicColorToggle,
                    isDarkTheme = isDarkTheme,
                    onDarkThemeToggle = onDarkThemeToggle
                )
                "About" -> AboutScreen()
            }
        }
    }

    // 3. Immersive expandable Full Screen Player Dialog Sheet
    if (showFullPlayer && currentTrack != null) {
        FullPlayer(
            visible = showFullPlayer,
            track = currentTrack,
            isPlaying = isPlaying,
            progress = progress,
            duration = duration,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            playbackSpeed = playbackSpeed,
            sleepTimerMinutesLeft = sleepTimerMinutesLeft,
            onTogglePlay = { viewModel.togglePlay() },
            onNext = { viewModel.playNext() },
            onPrevious = { viewModel.playPrevious() },
            onSeekTo = { viewModel.seekTo(it) },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleRepeat = { viewModel.toggleRepeatMode() },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onSpeedChange = { viewModel.updatePlaybackSpeed(it) },
            onSleepTimerChange = { viewModel.startSleepTimer(it) },
            onDismissRequest = { showFullPlayer = false }
        )
    }
}
