package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MusicViewModel

@Composable
fun EqualizerScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val eqEnabled by viewModel.eqEnabled.collectAsState()
    val eqBands by viewModel.eqBands.collectAsState()
    val eqFrequencies by viewModel.eqFrequencies.collectAsState()
    val bandCount by viewModel.bandCount.collectAsState()

    val bassBoostLevel by viewModel.bassBoostLevel.collectAsState()
    val virtualizerLevel by viewModel.virtualizerLevel.collectAsState()
    val loudnessLevel by viewModel.loudnessLevel.collectAsState()

    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val pitchControl by viewModel.pitchControl.collectAsState()

    // Default static bands if equalizer hardware is not initialized yet
    val defaultBands = listOf(60 to "60Hz", 230 to "230Hz", 910 to "910Hz", 4000 to "4kHz", 14000 to "14kHz")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Custom Equalizer Master Card with Switch
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Graphic EQ icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "NexTune Master EQ",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Hardware Decibel Amplification",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }

                    Switch(
                        checked = eqEnabled,
                        onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                        modifier = Modifier.testTag("eq_master_switch")
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Frequencies decibel horizontal control bars
                Text(
                    text = "Frequency Band adjustments (-15dB to +15dB)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (bandCount > 0) {
                    // Display actual queried system hardware bands
                    eqFrequencies.forEachIndexed { index, freqHz ->
                        val currentLevelMillibels = eqBands[index] ?: 0
                        val currentLevelDb = currentLevelMillibels / 100 // Convert millibels to decibels (100 millibels = 1dB)

                        BandControlRow(
                            label = if (freqHz >= 1000) "${freqHz / 1000}kHz" else "${freqHz}Hz",
                            levelDb = currentLevelDb,
                            enabled = eqEnabled,
                            onLevelChange = { newDb ->
                                viewModel.updateEqualizerBand(index, newDb * 100)
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                } else {
                    // Fallback to beautiful mockup controller if audio session isn't running yet
                    defaultBands.forEachIndexed { index, pair ->
                        var localLevelDb by remember { mutableStateOf(0) }
                        BandControlRow(
                            label = pair.second,
                            levelDb = localLevelDb,
                            enabled = eqEnabled,
                            onLevelChange = {
                                localLevelDb = it
                                viewModel.updateEqualizerBand(index, it * 100)
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Advanced Acoustical Enhancers: Bass, Virtualizer, Loudness
        Text(
            text = "Acoustical Enhancers",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Bass Boost Card
            AcousticControlCard(
                title = "Bass Boost 🔊",
                value = bassBoostLevel.toFloat(),
                valueRange = 0f..1000f,
                onValueChange = { viewModel.updateBassBoost(it.toInt()) },
                valueFormat = { "${(it / 10).toInt()}%" },
                modifier = Modifier.weight(1f)
            )

            // Virtualizer Card
            AcousticControlCard(
                title = "3D Virtualizer 🎧",
                value = virtualizerLevel.toFloat(),
                valueRange = 0f..1000f,
                onValueChange = { viewModel.updateVirtualizer(it.toInt()) },
                valueFormat = { "${(it / 10).toInt()}%" },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Loudness Enhancer Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Loudness Enhancer (Maximum Gain Boost dB): ${String.format("%.1fdB", loudnessLevel.toFloat() / 100)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Slider(
                    value = loudnessLevel.toFloat(),
                    onValueChange = { viewModel.updateLoudness(it.toInt()) },
                    valueRange = 0f..1000f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Technical speed modifiers info
        Text(
            text = "Playback Modifiers",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Pitch modifier slider
                Text(
                    text = "Audio Pitch Modulation: ${String.format("%.2fx", pitchControl)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Slider(
                    value = pitchControl,
                    onValueChange = { viewModel.updatePitchControl(it) },
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Playback speed details
                Text(
                    text = "Tempo / Speed rate: ${String.format("%.2fx", playbackSpeed)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Slider(
                    value = playbackSpeed,
                    onValueChange = { viewModel.updatePlaybackSpeed(it) },
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notice information
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Disclaimer info",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Note: NexTune equalizer binds tightly to Android Audio Session filters. High levels of Bass and Loudness boosts may produce organic clipping distortion on simple device micro-speakers.",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun BandControlRow(
    label: String,
    levelDb: Int,
    enabled: Boolean,
    onLevelChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(52.dp)
        )

        Slider(
            value = levelDb.toFloat(),
            onValueChange = { onLevelChange(it.toInt()) },
            valueRange = -15f..15f,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = if (levelDb > 0) "+${levelDb}dB" else "${levelDb}dB",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(48.dp),
            color = if (levelDb != 0 && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AcousticControlCard(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueFormat: (Float) -> String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = valueFormat(value),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
