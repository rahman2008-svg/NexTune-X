package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.Track
import com.example.service.RepeatMode
import kotlin.math.cos
import kotlin.math.sin

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(
    visible: Boolean,
    track: Track?,
    isPlaying: Boolean,
    progress: Long,
    duration: Long,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    playbackSpeed: Float,
    sleepTimerMinutesLeft: Int,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSleepTimerChange: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    if (!visible || track == null) return

    var activeTab by remember { mutableStateOf(0) } // 0: Player, 1: Embedded Lyrics & Info
    var showQuickSettings by remember { mutableStateOf(false) }

    // Disk revolving animation
    val infiniteTransition = rememberInfiniteTransition(label = "disc_rotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "angle"
    )

    // Pulse factor for visual spectrum spikes
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "NOW PLAYING",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = track.album,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Close player"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showQuickSettings = !showQuickSettings }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Quick Audio settings",
                                tint = if (showQuickSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Moving neon background ambient blobs
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x229C27B0),
                            Color(0x00000000)
                        ),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.3f),
                        radius = size.width * 0.6f
                    )
                    drawRect(brush = brush)

                    val brush2 = Brush.radialGradient(
                        colors = listOf(
                            Color(0x2200BCD4),
                            Color(0x00000000)
                        ),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.8f),
                        radius = size.width * 0.7f
                    )
                    drawRect(brush = brush2)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Media Control tabs
                    TabRow(
                        selectedTabIndex = activeTab,
                        containerColor = Color.Transparent,
                        divider = {},
                        modifier = Modifier
                            .width(180.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text("Player", fontSize = 12.sp) }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("Lyrics", fontSize = 12.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (activeTab == 0) {
                        // PLAYER DISK VIEW
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(280.dp)
                                .graphicsLayer {
                                    shadowElevation = 24f
                                    shape = CircleShape
                                    clip = true
                                }
                        ) {
                            // Soundwave visualizer spikes surrounding the disk
                            val spikesCount = 48
                            val activePulse = if (isPlaying) pulse else 0.95f
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                                val baseRadius = size.width / 2.3f

                                for (i in 0 until spikesCount) {
                                    val rad = (i.toDouble() / spikesCount) * 2.0 * java.lang.Math.PI
                                    val factor = (sin(rad * 5.0 + activePulse * 2.0) * 12.dp.toPx() * activePulse).toFloat()
                                    val startX = (center.x + (baseRadius * cos(rad))).toFloat()
                                    val startY = (center.y + (baseRadius * sin(rad))).toFloat()
                                    val endX = (center.x + ((baseRadius + factor) * cos(rad))).toFloat()
                                    val endY = (center.y + ((baseRadius + factor) * sin(rad))).toFloat()

                                    drawLine(
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                Color(0xFFE040FB),
                                                Color(0xFF00E5FF)
                                            )
                                        ),
                                        start = androidx.compose.ui.geometry.Offset(startX, startY),
                                        end = androidx.compose.ui.geometry.Offset(endX, endY),
                                        strokeWidth = 3.dp.toPx()
                                    )
                                }
                            }

                            // Rotating vinyl / album cover art
                            Image(
                                painter = painterResource(id = R.drawable.img_album_art_placeholder),
                                contentDescription = "Spinning vinyl record representation",
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(CircleShape)
                                    .rotate(if (isPlaying) angle else 0f)
                                    .border(
                                        width = 6.dp,
                                        brush = Brush.sweepGradient(
                                            colors = listOf(
                                                Color(0xFF111111),
                                                Color(0xFF333333),
                                                Color(0xFF111111)
                                            )
                                        ),
                                        shape = CircleShape
                                    ),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.height(36.dp))

                        // Song Title and Artist (Horizontal layout detailing name + favorite toggle)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { onToggleFavorite(track) },
                                modifier = Modifier.testTag("full_player_fav_button")
                            ) {
                                Icon(
                                    imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite track",
                                    tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Custom Seeker with sliding capabilities
                        var dynamicSeekProgress by remember(progress) { mutableStateOf(progress.toFloat()) }
                        var isUserDragging by remember { mutableStateOf(false) }

                        val calculatedProgress = if (isUserDragging) dynamicSeekProgress else progress.toFloat()
                        val percent = if (duration > 0) calculatedProgress / duration.toFloat() else 0f

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = percent,
                                onValueChange = {
                                    isUserDragging = true
                                    dynamicSeekProgress = it * duration
                                },
                                onValueChangeFinished = {
                                    isUserDragging = false
                                    onSeekTo(dynamicSeekProgress.toLong())
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("full_player_progress")
                            )

                            // Seek positions time indicators
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDuration(calculatedProgress.toLong()),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Text(
                                    text = formatDuration(duration),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Main controls block
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = onToggleShuffle) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(
                                onClick = onPrevious,
                                modifier = Modifier.testTag("full_player_prev")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Song",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Button(
                                onClick = onTogglePlay,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .size(76.dp)
                                    .testTag("full_player_play_pause")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play index toggle",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            IconButton(
                                onClick = onNext,
                                modifier = Modifier.testTag("full_player_next")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Skip index Next",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            IconButton(onClick = onToggleRepeat) {
                                Icon(
                                    imageVector = when (repeatMode) {
                                        RepeatMode.ONE -> Icons.Default.RepeatOne
                                        else -> Icons.Default.Repeat
                                    },
                                    contentDescription = "Repeat state",
                                    tint = if (repeatMode != RepeatMode.NONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                    } else {
                        // LYRICS & META INFO TAB
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(460.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Lyrics & Insights",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // Display real procedural relaxing lyrics matching our beautiful sweep waves
                            val proceduralLyrics = when {
                                track.title.contains("Sunset") -> listOf(
                                    "[Instrumental Introduction - Swelling sweeps]",
                                    "Golden sunbeams painting twilight shadows on the sea,",
                                    "Whispers of the night wind set the dancing currents free.",
                                    "[Vibrato waves oscillating...]",
                                    "In the glowing orange orbit, we resolve to stay,",
                                    "Floating on the silent echoes of the yesterday.",
                                    "[Fading frequency swells]"
                                )
                                track.title.contains("Rain") -> listOf(
                                    "[Deep Rumble - Atmospheric rain static]",
                                    "Drip, drop, cleansing water washing down the street,",
                                    "Quiet focus, rhythmic thunder, heavy stormy beat.",
                                    "[Subtonic bass boost sweeps]",
                                    "Let the pressure wash the clutter from your busy mind,",
                                    "Deep concentration is the magic you will find.",
                                    "[Rain fades to distant thunder]"
                                )
                                track.title.contains("Vibrato") -> listOf(
                                    "[High-Tech Cyber pulse opening]",
                                    "Synth-waves crashing inside a neon cyber dome,",
                                    "Glitches in the hardware vectoring the path to home.",
                                    "[Modular frequencies sweeping left and right]",
                                    "Speeding past the terminal, we bypass the constraint,",
                                    "NexTune playing infinite, with colors bright and paint."
                                )
                                else -> listOf(
                                    "[Deep Delta drone hum]",
                                    "In the endless starry cosmos, stars begin to weep,",
                                    "Close your eyes, find peace, fade in to gentle sleep.",
                                    "Waves of silver stardust carrying the ocean sound,",
                                    "Perfect relaxation is the treasure we have found."
                                )
                            }

                            proceduralLyrics.forEach { line ->
                                Text(
                                    text = line,
                                    style = if (line.startsWith("[")) {
                                        MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 28.sp
                                        )
                                    },
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))
                }

                // QUICK AUDIO SETTINGS COLLAPSIBLE PANEL OVERLAY
                AnimatedVisibility(
                    visible = showQuickSettings,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Card(
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Tune",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Playback Audio Enhancer",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { showQuickSettings = false }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close settings")
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            // Playback Speed Slider
                            Text(
                                text = "Playback Speed: ${String.format("%.2fx", playbackSpeed)}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Slider(
                                value = playbackSpeed,
                                onValueChange = onSpeedChange,
                                valueRange = 0.5f..2.5f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Sleep Timer Config
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (sleepTimerMinutesLeft > 0) "Sleep Timer: ${sleepTimerMinutesLeft}m remaining" else "Sleep Timer: OFF",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                if (sleepTimerMinutesLeft > 0) {
                                    Button(
                                        onClick = { onSleepTimerChange(0) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Cancel", fontSize = 11.sp)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf(10, 15, 30, 45, 60).forEach { mins ->
                                    ElevatedButton(
                                        onClick = { onSleepTimerChange(mins) },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("${mins}m", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val sec = (millis / 1000) % 60
    val min = (millis / (1000 * 60)) % 60
    val hrs = (millis / (1000 * 60 * 60)) % 24
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}
