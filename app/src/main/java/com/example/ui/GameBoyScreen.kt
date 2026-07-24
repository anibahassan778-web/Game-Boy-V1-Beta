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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Menu
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val shellTheme by viewModel.shellTheme.collectAsState()
    val enableCrtScanlines by viewModel.enableCrtScanlines.collectAsState()

    // File Picker for loading .gb and .gbc ROMs
    val romFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.loadRomFromUri(context, it)
        }
    }

    // Live clock formatted like in the picture (e.g., "21:00")
    var currentTimeString by remember { mutableStateOf("21:00") }
    LaunchedEffect(Unit) {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTimeString = formatter.format(Date())
            kotlinx.coroutines.delay(10000)
        }
    }

    val isDarkOverlay = (shellTheme == "Dark Touch Overlay" || shellTheme == "Pocket Black" || shellTheme.isBlank() || shellTheme == "Transparent Overlay")
    val isTransparentOverlay = (shellTheme == "Transparent Overlay")

    val shellBgColor = when (shellTheme) {
        "Atomic Purple" -> Color(0xFF6A1B9A)
        "Game Boy Color Berry" -> Color(0xFFC2185B)
        "Cyberpunk" -> Color(0xFF00B0FF)
        "Classic DMG" -> Color(0xFFC8C3B8)
        else -> Color(0xFF000000) // Pure dark layout matching the image!
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(shellBgColor),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP BAR WITH 4 ACTION ICONS (matching image!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF000000))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Group: Import (Download) & Export (Upload/Save)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Icon 1: Import / Load ROM File from Device (.gb, .gbc, .zip)
                IconButton(
                    onClick = { romFilePickerLauncher.launch("*/*") },
                    modifier = Modifier.testTag("load_rom_file_icon")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Load ROM File",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Icon 2: Quick Save State
                IconButton(
                    onClick = { viewModel.quickSaveState(1) },
                    modifier = Modifier.testTag("quick_save_icon")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = "Save State",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Right Group: Fast-Forward (Turbo) & Menu
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Icon 3: Fast-Forward (Turbo Mode)
                IconButton(
                    onClick = { viewModel.toggleTurbo() },
                    modifier = Modifier.testTag("turbo_icon")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Fast Forward",
                        tint = if (isTurbo) Color(0xFFFF5252) else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Icon 4: Menu / Settings / ROM Library
                IconButton(
                    onClick = { onOpenMenu() },
                    modifier = Modifier.testTag("menu_icon")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // GAME BOY DISPLAY SCREEN AREA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isDarkOverlay) {
                // Minimalist Dark Screen Frame
                Box(
                    modifier = Modifier
                        .aspectRatio(160f / 144f)
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F380F)) // Game Boy LCD Dark Green base
                        .border(2.dp, Color(0xFF27272A), RoundedCornerShape(6.dp))
                        .testTag("gameboy_screen_canvas"),
                    contentAlignment = Alignment.Center
                ) {
                    if (screenImage != null) {
                        Image(
                            bitmap = screenImage!!,
                            contentDescription = "Game Boy Display",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = "GAME BOY",
                            color = Color(0xFF8BAC0F),
                            fontSize = 26.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (enableCrtScanlines) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.08f))
                        )
                    }

                    if (isTurbo) {
                        Surface(
                            color = Color.Red,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = " TURBO 4X ",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // Classic DMG Hardware Bezel Shell
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF505A69)),
                    shape = RoundedCornerShape(16.dp, 16.dp, 36.dp, 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .aspectRatio(160f / 144f)
                                .fillMaxWidth()
                                .background(Color(0xFF8BAC0F))
                                .border(4.dp, Color(0xFF222222), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (screenImage != null) {
                                Image(
                                    bitmap = screenImage!!,
                                    contentDescription = "Game Boy Display",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(
                                    text = "GAME BOY",
                                    color = Color(0xFF0F380F),
                                    fontSize = 24.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // CONTROLLER CONTROLS AREA
        if (isTransparentOverlay) {
            GameControllerOverlay(
                viewModel = viewModel,
                opacity = 0.6f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row: Shoulder Buttons L and R
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShoulderPillButton(
                        label = "L",
                        onPress = { pressed -> viewModel.setButtonState(GbButton.SELECT, pressed) }, // L shoulder mapping
                        testTag = "button_l"
                    )

                    ShoulderPillButton(
                        label = "R",
                        onPress = { pressed -> viewModel.setButtonState(GbButton.START, pressed) }, // R shoulder mapping
                        testTag = "button_r"
                    )
                }

                // Main Controls Row: D-Pad on Left, Stacked A/B Buttons on Right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DPadControl(
                        onDirectionChange = { button, pressed ->
                            viewModel.setButtonState(button, pressed)
                        },
                        isDarkTheme = isDarkOverlay
                    )

                    ActionButtons(
                        onButtonPress = { button, pressed ->
                            viewModel.setButtonState(button, pressed)
                        },
                        isDarkTheme = isDarkOverlay
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom System Buttons: SELECT and START
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SystemPillButton(
                        label = "SELECT",
                        onPress = { pressed -> viewModel.setButtonState(GbButton.SELECT, pressed) },
                        testTag = "button_select",
                        isDarkTheme = isDarkOverlay
                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    SystemPillButton(
                        label = "START",
                        onPress = { pressed -> viewModel.setButtonState(GbButton.START, pressed) },
                        testTag = "button_start",
                        isDarkTheme = isDarkOverlay
                    )
                }
            }
        }

        // BOTTOM STATUS BAR (Clock Time e.g. 21:00 on left, Battery e.g. 26% on right, like in image!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentTimeString,
                color = Color(0xFFA1A1AA),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "100%",
                color = Color(0xFFA1A1AA),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

