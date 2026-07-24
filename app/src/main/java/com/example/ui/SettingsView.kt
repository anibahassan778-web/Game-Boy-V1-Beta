package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsView(
    viewModel: EmulatorViewModel,
    modifier: Modifier = Modifier
) {
    val shellTheme by viewModel.shellTheme.collectAsState()
    val colorPaletteMode by viewModel.colorPaletteMode.collectAsState()
    val enableCrtScanlines by viewModel.enableCrtScanlines.collectAsState()
    val isAudioMuted by viewModel.isAudioMuted.collectAsState()

    val themes = listOf("Dark Touch Overlay", "Transparent Overlay", "Classic DMG", "Atomic Purple", "Game Boy Color Berry", "Pocket Black", "Cyberpunk")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121417))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Emulator Settings",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Hardware Shell Theme Picker
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Handheld Shell Theme", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    themes.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (shellTheme == theme),
                                onClick = { viewModel.shellTheme.value = theme },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF42A5F5))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(theme, color = Color.White)
                        }
                    }
                }
            }
        }

        // Display Palette Mode
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("DMG Screen Palette Preset", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Classic Dot Matrix Green", color = Color.White)
                        Switch(
                            checked = (colorPaletteMode == 0),
                            onCheckedChange = { checked -> viewModel.colorPaletteMode.value = if (checked) 0 else 1 },
                            modifier = Modifier.testTag("palette_toggle_switch")
                        )
                    }
                }
            }
        }

        // Display CRT Scanline Filter
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("CRT Scanlines Effect", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Simulate retro grid lines on screen", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = enableCrtScanlines,
                        onCheckedChange = { viewModel.enableCrtScanlines.value = it },
                        modifier = Modifier.testTag("crt_scanlines_switch")
                    )
                }
            }
        }

        // Audio Mute Toggle
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Mute Audio", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Mute APU sound output", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = isAudioMuted,
                        onCheckedChange = { viewModel.toggleAudioMute() },
                        modifier = Modifier.testTag("audio_mute_switch")
                    )
                }
            }
        }
    }
}
