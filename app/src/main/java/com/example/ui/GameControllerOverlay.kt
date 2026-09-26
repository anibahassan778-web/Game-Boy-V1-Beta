package com.example.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot

private fun triggerHapticFeedback(context: Context, durationMs: Long = 18) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        }
    } catch (_: Exception) {
    }
}

/**
 * Modern Professional Emulator On-Screen Touch Overlay.
 * Replicates the clean translucent glass aesthetic of professional mobile emulators
 * (AetherSX2, PPSSPP, Dolphin) as shown in the reference screenshot.
 */
@Composable
fun GameControllerOverlay(
    viewModel: EmulatorViewModel,
    modifier: Modifier = Modifier,
    opacity: Float = 0.45f,
    onOpenMenu: () -> Unit = {}
) {
    val hapticsEnabled by viewModel.overlayHapticsEnabled.collectAsState()
    val allowDiagonals by viewModel.allowDiagonals.collectAsState()
    val showAbCombo by viewModel.showAbComboButton.collectAsState()
    val isTurbo by viewModel.isTurbo.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val liveFps by viewModel.liveFps.collectAsState()
    val liveVps by viewModel.liveVps.collectAsState()
    val isTelemetryVisible by viewModel.isTelemetryVisible.collectAsState()
    val romTitle by viewModel.romTitle.collectAsState()

    var showInGamePauseDialog by remember { mutableStateOf(false) }

    GameControllerOverlay(
        onButtonStateChange = { button, pressed -> viewModel.setButtonState(button, pressed) },
        onDirectionsChange = { up, down, left, right -> viewModel.setDirectionsState(up, down, left, right) },
        onReleaseAll = { viewModel.releaseAllButtons() },
        modifier = modifier,
        opacity = opacity,
        hapticsEnabled = hapticsEnabled,
        allowDiagonals = allowDiagonals,
        showAbCombo = showAbCombo,
        isTurbo = isTurbo,
        isPaused = isPaused,
        liveFps = liveFps,
        liveVps = liveVps,
        isTelemetryVisible = isTelemetryVisible,
        romTitle = romTitle,
        onTogglePause = {
            viewModel.togglePause()
            showInGamePauseDialog = !showInGamePauseDialog
        },
        onToggleTurbo = { viewModel.toggleTurbo() }
    )

    // In-Game Pause & Quick Settings Modal Dialog
    if (showInGamePauseDialog) {
        AlertDialog(
            onDismissRequest = {
                showInGamePauseDialog = false
                if (viewModel.isPaused.value) {
                    viewModel.togglePause()
                }
            },
            containerColor = Color(0xEE14161A),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EMULATOR MENU",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(
                        onClick = {
                            showInGamePauseDialog = false
                            if (viewModel.isPaused.value) {
                                viewModel.togglePause()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Resume Game
                    Button(
                        onClick = {
                            showInGamePauseDialog = false
                            if (viewModel.isPaused.value) {
                                viewModel.togglePause()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("menu_resume_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resume Game", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    // Save / Load State Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.quickSaveState(1)
                                showInGamePauseDialog = false
                                if (viewModel.isPaused.value) viewModel.togglePause()
                            },
                            modifier = Modifier.weight(1f).testTag("menu_save_state_button")
                        ) {
                            Text("Save State", color = Color.White, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.quickLoadState(1)
                                showInGamePauseDialog = false
                                if (viewModel.isPaused.value) viewModel.togglePause()
                            },
                            modifier = Modifier.weight(1f).testTag("menu_load_state_button")
                        ) {
                            Text("Load State", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    // Turbo & Telemetry Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fast Forward (Turbo 4X)", color = Color.White, fontSize = 13.sp)
                        Switch(
                            checked = isTurbo,
                            onCheckedChange = { viewModel.toggleTurbo() },
                            modifier = Modifier.testTag("menu_turbo_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show Live FPS Stats", color = Color.White, fontSize = 13.sp)
                        Switch(
                            checked = isTelemetryVisible,
                            onCheckedChange = { viewModel.isTelemetryVisible.value = it },
                            modifier = Modifier.testTag("menu_telemetry_switch")
                        )
                    }

                    // Full Settings Navigation
                    OutlinedButton(
                        onClick = {
                            showInGamePauseDialog = false
                            onOpenMenu()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("menu_all_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.LightGray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("All Settings & ROM Library", color = Color.White)
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun GameControllerOverlay(
    onButtonStateChange: (GbButton, Boolean) -> Unit,
    onDirectionsChange: ((Boolean, Boolean, Boolean, Boolean) -> Unit)? = null,
    onReleaseAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    opacity: Float = 0.45f,
    hapticsEnabled: Boolean = true,
    allowDiagonals: Boolean = true,
    showAbCombo: Boolean = false,
    isTurbo: Boolean = false,
    isPaused: Boolean = false,
    liveFps: Float = 59.9f,
    liveVps: Float = 60.0f,
    isTelemetryVisible: Boolean = true,
    romTitle: String = "Game Boy Core",
    onTogglePause: (() -> Unit)? = null,
    onToggleTurbo: (() -> Unit)? = null
) {
    val safeOpacity = opacity.coerceIn(0.20f, 1.0f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("game_controller_overlay")
    ) {
        // TOP TELEMETRY & SYSTEM BAR (Matching uploaded image top area)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Top-Left: Subtle translucent status pill (matching image notice pill)
            Surface(
                color = Color(0x44000000),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .testTag("hud_status_banner")
            ) {
                Text(
                    text = romTitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            // Top-Right: Telemetry Metrics & Pause Button (matching image right area)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isTelemetryVisible) {
                    val fpsFormatted = String.format(Locale.US, "%.2f", liveFps)
                    val vpsFormatted = String.format(Locale.US, "%.2f", liveVps)
                    Text(
                        text = "G: $fpsFormatted [P] | V: $vpsFormatted",
                        color = Color(0xFFFF4B4B), // Console HUD red font
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.testTag("hud_fps_telemetry")
                    )
                }

                // Pause Button || (exact match to image top-right ||)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTogglePause?.invoke() }
                        .testTag("button_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Pause",
                        tint = Color.White.copy(alpha = 0.90f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // LEFT ON-SCREEN D-PAD (Matching translucent cross in uploaded image)
        OverlayCrossDPad(
            onDirectionChange = onButtonStateChange,
            onDirectionsChange = onDirectionsChange,
            onReleaseAll = onReleaseAll,
            opacity = safeOpacity,
            hapticsEnabled = hapticsEnabled,
            allowDiagonals = allowDiagonals,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
                .testTag("overlay_dpad")
        )

        // RIGHT ACTION BUTTONS: A and B (Matching translucent circles in uploaded image)
        OverlayTranslucentActionButtons(
            onButtonPress = onButtonStateChange,
            opacity = safeOpacity,
            hapticsEnabled = hapticsEnabled,
            showComboButton = showAbCombo,
            isTurbo = isTurbo,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
                .testTag("overlay_action_buttons")
        )

        // BOTTOM CENTER: SELECT & START PILL BUTTONS (Matching image bottom center pills)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OverlayTranslucentPillButton(
                label = "SELECT",
                onPress = { pressed -> onButtonStateChange(GbButton.SELECT, pressed) },
                testTag = "button_select",
                opacity = safeOpacity,
                hapticsEnabled = hapticsEnabled
            )

            OverlayTranslucentPillButton(
                label = "START",
                onPress = { pressed -> onButtonStateChange(GbButton.START, pressed) },
                testTag = "button_start",
                opacity = safeOpacity,
                hapticsEnabled = hapticsEnabled
            )
        }
    }
}

/**
 * Translucent Rounded Cross D-Pad matching modern mobile emulators (AetherSX2/PPSSPP).
 * Thin clean outline, smoke dark translucent interior, and crisp directional arrowheads.
 */
@Composable
fun OverlayCrossDPad(
    onDirectionChange: (GbButton, Boolean) -> Unit,
    onDirectionsChange: ((Boolean, Boolean, Boolean, Boolean) -> Unit)? = null,
    onReleaseAll: (() -> Unit)? = null,
    opacity: Float,
    hapticsEnabled: Boolean,
    allowDiagonals: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp
) {
    val context = LocalContext.current

    var upPressed by remember { mutableStateOf(false) }
    var downPressed by remember { mutableStateOf(false) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }

    fun updateDirections(up: Boolean, down: Boolean, left: Boolean, right: Boolean) {
        val changed = (up != upPressed) || (down != downPressed) || (left != leftPressed) || (right != rightPressed)
        if (changed) {
            if (hapticsEnabled && (up || down || left || right)) {
                triggerHapticFeedback(context, 16)
            }
            upPressed = up
            downPressed = down
            leftPressed = left
            rightPressed = right

            if (onDirectionsChange != null) {
                onDirectionsChange(up, down, left, right)
            } else {
                onDirectionChange(GbButton.UP, up)
                onDirectionChange(GbButton.DOWN, down)
                onDirectionChange(GbButton.LEFT, left)
                onDirectionChange(GbButton.RIGHT, right)
            }
        }
    }

    fun releaseAll() {
        if (upPressed || downPressed || leftPressed || rightPressed) {
            upPressed = false
            downPressed = false
            leftPressed = false
            rightPressed = false
            if (onReleaseAll != null) {
                onReleaseAll()
            } else {
                onDirectionChange(GbButton.UP, false)
                onDirectionChange(GbButton.DOWN, false)
                onDirectionChange(GbButton.LEFT, false)
                onDirectionChange(GbButton.RIGHT, false)
            }
        }
    }

    val outlineColor = Color.White.copy(alpha = opacity * 0.85f)
    val bodyBgColor = Color(0x35000000) // Translucent smoke black matching screenshot
    val activeHighlight = Color.White.copy(alpha = opacity * 0.35f)

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(allowDiagonals) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val (u, d, l, r) = calculateDirections(down.position, size.toPx(), allowDiagonals)
                    updateDirections(u, d, l, r)

                    do {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.find { it.id == down.id } ?: break
                        if (!pointer.pressed) break

                        val (curU, curD, curL, curR) = calculateDirections(pointer.position, size.toPx(), allowDiagonals)
                        updateDirections(curU, curD, curL, curR)
                    } while (event.changes.any { it.pressed })

                    releaseAll()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Horizontal Arm (Left - Right)
        Box(
            modifier = Modifier
                .width(size)
                .height(size * 0.36f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.6.dp, outlineColor, RoundedCornerShape(14.dp))
                .background(bodyBgColor, RoundedCornerShape(14.dp))
        )

        // Vertical Arm (Up - Down)
        Box(
            modifier = Modifier
                .width(size * 0.36f)
                .height(size)
                .clip(RoundedCornerShape(14.dp))
                .border(1.6.dp, outlineColor, RoundedCornerShape(14.dp))
                .background(bodyBgColor, RoundedCornerShape(14.dp))
        )

        // Center cross intersection blender (seamless connection)
        Box(
            modifier = Modifier
                .size(size * 0.34f)
                .background(bodyBgColor)
        )

        // UP Arm Arrow & Visuals
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(size * 0.36f)
                .height(size * 0.34f)
                .testTag("dpad_up")
                .background(
                    if (upPressed) activeHighlight else Color.Transparent,
                    RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▲",
                color = if (upPressed) Color.White else Color.White.copy(alpha = opacity * 0.9f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // DOWN Arm Arrow & Visuals
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(size * 0.36f)
                .height(size * 0.34f)
                .testTag("dpad_down")
                .background(
                    if (downPressed) activeHighlight else Color.Transparent,
                    RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▼",
                color = if (downPressed) Color.White else Color.White.copy(alpha = opacity * 0.9f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // LEFT Arm Arrow & Visuals
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(size * 0.34f)
                .height(size * 0.36f)
                .testTag("dpad_left")
                .background(
                    if (leftPressed) activeHighlight else Color.Transparent,
                    RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "◀",
                color = if (leftPressed) Color.White else Color.White.copy(alpha = opacity * 0.9f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // RIGHT Arm Arrow & Visuals
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(size * 0.34f)
                .height(size * 0.36f)
                .testTag("dpad_right")
                .background(
                    if (rightPressed) activeHighlight else Color.Transparent,
                    RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▶",
                color = if (rightPressed) Color.White else Color.White.copy(alpha = opacity * 0.9f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun calculateDirections(
    position: Offset,
    sizePx: Float,
    allowDiagonals: Boolean
): DirectionState {
    val center = sizePx / 2f
    val dx = position.x - center
    val dy = position.y - center
    val distance = hypot(dx, dy)
    val deadZone = sizePx * 0.12f

    if (distance < deadZone) {
        return DirectionState(up = false, down = false, left = false, right = false)
    }

    if (!allowDiagonals) {
        return if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
            if (dx > 0) DirectionState(up = false, down = false, left = false, right = true)
            else DirectionState(up = false, down = false, left = true, right = false)
        } else {
            if (dy > 0) DirectionState(up = false, down = true, left = false, right = false)
            else DirectionState(up = true, down = false, left = false, right = false)
        }
    }

    val angleRad = atan2(dy, dx)
    val angleDeg = Math.toDegrees(angleRad.toDouble())

    return when {
        angleDeg in -22.5..22.5 -> DirectionState(up = false, down = false, left = false, right = true)
        angleDeg in 22.5..67.5 -> DirectionState(up = false, down = true, left = false, right = true)
        angleDeg in 67.5..112.5 -> DirectionState(up = false, down = true, left = false, right = false)
        angleDeg in 112.5..157.5 -> DirectionState(up = false, down = true, left = true, right = false)
        angleDeg >= 157.5 || angleDeg <= -157.5 -> DirectionState(up = false, down = false, left = true, right = false)
        angleDeg in -157.5..-112.5 -> DirectionState(up = true, down = false, left = true, right = false)
        angleDeg in -112.5..-67.5 -> DirectionState(up = true, down = false, left = false, right = false)
        angleDeg in -67.5..-22.5 -> DirectionState(up = true, down = false, left = false, right = true)
        else -> DirectionState(up = false, down = false, left = false, right = false)
    }
}

private data class DirectionState(
    val up: Boolean,
    val down: Boolean,
    val left: Boolean,
    val right: Boolean
)

/**
 * Clean Translucent Action Buttons (A & B) matching the uploaded screenshot.
 * Large circular outlines, dark smoke translucent interiors, and crisp centered letters.
 */
@Composable
fun OverlayTranslucentActionButtons(
    onButtonPress: (GbButton, Boolean) -> Unit,
    opacity: Float,
    hapticsEnabled: Boolean,
    showComboButton: Boolean = false,
    isTurbo: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .width(170.dp)
            .height(160.dp)
    ) {
        // Button B: Placed lower-left exactly like in the reference image
        OverlayTranslucentCircleButton(
            label = "B",
            onPress = { pressed ->
                if (pressed && hapticsEnabled) triggerHapticFeedback(context, 18)
                onButtonPress(GbButton.B, pressed)
            },
            testTag = "button_b",
            opacity = opacity,
            size = 72.dp,
            modifier = Modifier.align(Alignment.BottomStart)
        )

        // Optional A+B Combo button (if enabled)
        if (showComboButton) {
            var comboPressed by remember { mutableStateOf(false) }
            val comboScale by animateFloatAsState(if (comboPressed) 0.88f else 1.0f)

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 46.dp, height = 30.dp)
                    .scale(comboScale)
                    .testTag("button_ab_combo")
                    .clip(RoundedCornerShape(15.dp))
                    .border(1.2.dp, Color.White.copy(alpha = opacity * 0.7f), RoundedCornerShape(15.dp))
                    .background(
                        if (comboPressed) Color.White.copy(alpha = 0.35f) else Color(0x35000000),
                        RoundedCornerShape(15.dp)
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            comboPressed = true
                            if (hapticsEnabled) triggerHapticFeedback(context, 22)
                            onButtonPress(GbButton.A, true)
                            onButtonPress(GbButton.B, true)

                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })

                            comboPressed = false
                            onButtonPress(GbButton.A, false)
                            onButtonPress(GbButton.B, false)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A+B",
                    color = Color.White.copy(alpha = opacity * 0.95f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Button A: Placed higher-right exactly like in the reference image
        OverlayTranslucentCircleButton(
            label = "A",
            onPress = { pressed ->
                if (pressed && hapticsEnabled) triggerHapticFeedback(context, 18)
                onButtonPress(GbButton.A, pressed)
            },
            testTag = "button_a",
            opacity = opacity,
            size = 72.dp,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

/**
 * Translucent circular outline button matching the AetherSX2 / PPSSPP aesthetic.
 */
@Composable
fun OverlayTranslucentCircleButton(
    label: String,
    onPress: (Boolean) -> Unit,
    testTag: String,
    opacity: Float,
    size: Dp = 72.dp,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.90f else 1.0f)

    val outlineColor = Color.White.copy(alpha = opacity * 0.85f)
    val bodyBgColor = if (isPressed) Color.White.copy(alpha = 0.30f) else Color(0x35000000)

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .testTag(testTag)
            .clip(CircleShape)
            .border(1.6.dp, outlineColor, CircleShape)
            .background(bodyBgColor, CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    onPress(true)

                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })

                    isPressed = false
                    onPress(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = opacity * 0.95f),
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif
        )
    }
}

/**
 * Translucent Pill Button for SELECT and START placed side-by-side at bottom-center.
 */
@Composable
fun OverlayTranslucentPillButton(
    label: String,
    onPress: (Boolean) -> Unit,
    testTag: String,
    opacity: Float,
    hapticsEnabled: Boolean,
    width: Dp = 76.dp,
    height: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.92f else 1.0f)

    val outlineColor = Color.White.copy(alpha = opacity * 0.75f)
    val bodyBgColor = if (isPressed) Color.White.copy(alpha = 0.28f) else Color(0x40000000)

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .scale(scale)
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .border(1.4.dp, outlineColor, RoundedCornerShape(16.dp))
            .background(bodyBgColor, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    if (hapticsEnabled) triggerHapticFeedback(context, 16)
                    onPress(true)

                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })

                    isPressed = false
                    onPress(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = opacity * 0.95f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.5.sp
        )
    }
}
