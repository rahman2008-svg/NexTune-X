package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MusicViewModel

@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    amoledThemeEnabled: Boolean,
    onAmoledToggle: (Boolean) -> Unit,
    dynamicColorEnabled: Boolean,
    onDynamicColorToggle: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    onDarkThemeToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val blacklistedFolders by viewModel.blacklistedFolders.collectAsState()
    var folderInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // THEME PREFERENCES SECTION
        Text(
            text = "Visual Theme Configuration",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Dark Theme toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Brightness4, contentDescription = "Dark mode")
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Force Dark Mode Theme", fontWeight = FontWeight.SemiBold)
                            Text("Deep dark eyes-friendly palette", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(checked = isDarkTheme, onCheckedChange = onDarkThemeToggle, modifier = Modifier.testTag("dark_theme_switch"))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // AMOLED Black toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.DarkMode, contentDescription = "AMOLED mode")
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("AMOLED Pitch Black Theme", fontWeight = FontWeight.SemiBold)
                            Text("Pure black background to save pixels", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(checked = amoledThemeEnabled, onCheckedChange = onAmoledToggle, enabled = isDarkTheme, modifier = Modifier.testTag("amoled_theme_switch"))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Material You toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.ColorLens, contentDescription = "Material You dynamic")
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Material You Dynamic Accent", fontWeight = FontWeight.SemiBold)
                            Text("Harmonize colors with system wallpapers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(checked = dynamicColorEnabled, onCheckedChange = onDynamicColorToggle, modifier = Modifier.testTag("dynamic_color_switch"))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SCAN STORAGE SETTINGS
        Text(
            text = "Storage Scan Settings",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Automatically indexes songs inside system partitions, skipping ringtones and noisy system voice memos automatically.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Button(
                    onClick = {
                        viewModel.scanMusicFiles { count ->
                            Toast.makeText(context, "Scanned: Indexed $count new audio files!", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("force_scan_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Scan")
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Trigger MediaStore Auto-Scan", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // BLACKLIST FOLDERS & DIRECTORY LOCK
        Text(
            text = "Locked & Blacklisted Folders 🔒",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Songs in these directories will be automatically blocked, hidden, and ignored by NexTune scanning modules.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = folderInput,
                        onValueChange = { folderInput = it },
                        placeholder = { Text("/storage/emulated/0/Download") },
                        label = { Text("Directory Path") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (folderInput.isNotBlank()) {
                                viewModel.addBlacklistedFolder(folderInput)
                                folderInput = ""
                                Toast.makeText(context, "Added to blacklisted locks!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add blacklist")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (blacklistedFolders.isEmpty()) {
                    Text(
                        text = "No folders blacklisted or locked.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Text(
                        text = "Blacklist exclusions:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        blacklistedFolders.forEach { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.Folder, contentDescription = "Folder", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = folder.folderPath,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removeBlacklistedFolder(folder.folderPath) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove lock", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // BATTERY OPTIMIZATION INFORMATION
        Text(
            text = "Battery Optimization Helper",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Battery info",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Unlimited Background Playback 🔋",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "To guarantee completely continuous background acoustics when screen is off, please select 'Don't Optimize' or 'Unrestricted' battery access inside your device's app details menu.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
