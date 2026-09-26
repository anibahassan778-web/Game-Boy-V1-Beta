package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modern Professional Emulator Gaming Screen.
 * Fullscreen cinematic display with translucent touch overlay controls,
 * matching professional emulators like AetherSX2, PPSSPP, and Dolphin.
 */
@Composable
fun GameBoyScreen(
    viewModel: EmulatorViewModel,
    onOpenMenu: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val screenImage by viewModel.screenImage.collectAsState()
    val romTitle by viewModel.romTitle.collectAsState()
    val isTurbo by viewModel.isTurbo.collectAsState()
    val enableCrtScanlines by viewModel.enableCrtScanlines.collectAsState()
    val overlayOpacity by viewModel.overlayOpacity.collectAsState()
    val screenAspectRatio by viewModel.screenAspectRatio.collectAsState()

    // File Picker for loading .gb and .gbc ROMs
    val romFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.loadRomFromUri(context, it)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000)), // Deep OLED black background
        contentAlignment = Alignment.Center
    ) {
        // 1. GAME DISPLAY VIEWPORT
        // Supports Original 10:9, Standard 4:3, or Widescreen Fit
        val displayModifier = when (screenAspectRatio) {
            "Standard 4:3" -> Modifier.aspectRatio(4f / 3f).fillMaxSize()
            "Widescreen Fit" -> Modifier.fillMaxSize()
            else -> Modifier.aspectRatio(160f / 144f).fillMaxSize()
        }

        Box(
            modifier = displayModifier
                .testTag("gameboy_screen_canvas"),
            contentAlignment = Alignment.Center
        ) {
            if (screenImage != null) {
                Image(
                    bitmap = screenImage!!,
                    contentDescription = "Game Display",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (screenAspectRatio == "Widescreen Fit") ContentScale.FillBounds else ContentScale.Fit
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "READY",
                        color = Color(0xFF3B82F6),
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Load ROM from Menu to Play",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }

            // Optional CRT Scanlines Effect
            if (enableCrtScanlines) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.08f))
                )
            }

            // Turbo Mode Indicator badge in top-center
            if (isTurbo) {
                Surface(
                    color = Color(0xDDDC2626),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 40.dp)
                ) {
                    Text(
                        text = " TURBO 4X ",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // 2. ON-SCREEN TRANSLUCENT CONTROLLER OVERLAY & HUD (Matching screenshot)
        GameControllerOverlay(
            viewModel = viewModel,
            opacity = overlayOpacity,
            onOpenMenu = onOpenMenu,
            modifier = Modifier.fillMaxSize()
        )
    }
}
