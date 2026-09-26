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
    val colorPaletteMode by viewModel.colorPaletteMode.collectAsState()
    val enableCrtScanlines by viewModel.enableCrtScanlines.collectAsState()
    val isAudioMuted by viewModel.isAudioMuted.collectAsState()
    val overlayOpacity by viewModel.overlayOpacity.collectAsState()
    val overlayHapticsEnabled by viewModel.overlayHapticsEnabled.collectAsState()
    val allowDiagonals by viewModel.allowDiagonals.collectAsState()
    val isTelemetryVisible by viewModel.isTelemetryVisible.collectAsState()
    val isImmersiveGamingMode by viewModel.isImmersiveGamingMode.collectAsState()
    val screenAspectRatio by viewModel.screenAspectRatio.collectAsState()
    val showAbComboButton by viewModel.showAbComboButton.collectAsState()

    val aspectRatios = listOf("Original 10:9", "Standard 4:3", "Widescreen Fit")

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
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Professional Touch Overlay & Display Configuration",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }

        // On-Screen Touch Overlay Configuration (matching screenshot)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Touch Overlay Controls", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Overlay Opacity Slider
                    Column {
                        Text("Overlay Opacity: ${(overlayOpacity * 100).toInt()}%", color = Color.White)
                        Slider(
                            value = overlayOpacity,
                            onValueChange = { viewModel.overlayOpacity.value = it },
                            valueRange = 0.2f..1.0f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("overlay_opacity_settings_slider")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 8-Way Diagonals
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("8-Way Diagonal D-Pad", color = Color.White)
                            Text("Smooth diagonal movement (Up+Right etc.)", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = allowDiagonals,
                            onCheckedChange = { viewModel.allowDiagonals.value = it },
                            modifier = Modifier.testTag("overlay_diagonals_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Haptic Feedback
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vibration Haptic Feedback", color = Color.White)
                            Text("Tactile feel on directional and button press", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = overlayHapticsEnabled,
                            onCheckedChange = { viewModel.overlayHapticsEnabled.value = it },
                            modifier = Modifier.testTag("overlay_haptics_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // A+B Combo Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("A+B Combo Touch Button", color = Color.White)
                            Text("Convenient simultaneous button press", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = showAbComboButton,
                            onCheckedChange = { viewModel.showAbComboButton.value = it },
                            modifier = Modifier.testTag("overlay_combo_button_switch")
                        )
                    }
                }
            }
        }

        // Display & Screen Presentation
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Display & Aspect Ratio", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Aspect Ratio Selector
                    Text("Screen Aspect Ratio", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    aspectRatios.forEach { ratio ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (screenAspectRatio == ratio),
                                onClick = { viewModel.screenAspectRatio.value = ratio },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF3B82F6))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(ratio, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Immersive Edge-to-Edge Gaming Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Immersive Edge-to-Edge Mode", color = Color.White)
                            Text("Maximize screen without bottom navigation bar", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = isImmersiveGamingMode,
                            onCheckedChange = { viewModel.isImmersiveGamingMode.value = it },
                            modifier = Modifier.testTag("immersive_mode_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Live FPS & Telemetry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Live Telemetry & FPS Counter", color = Color.White)
                            Text("Show G: FPS / V: VPS metrics at top right", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = isTelemetryVisible,
                            onCheckedChange = { viewModel.isTelemetryVisible.value = it },
                            modifier = Modifier.testTag("telemetry_stats_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // CRT Scanlines Effect
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CRT Scanlines Filter", color = Color.White)
                            Text("Simulate retro monitor raster scanlines", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = enableCrtScanlines,
                            onCheckedChange = { viewModel.enableCrtScanlines.value = it },
                            modifier = Modifier.testTag("crt_scanlines_switch")
                        )
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
