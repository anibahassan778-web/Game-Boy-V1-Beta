package com.example.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ArrowLeft
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun performHapticFeedback(context: Context, durationMs: Long = 15) {
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
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun DPadControl(
    onDirectionChange: (GbButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true
) {
    val context = LocalContext.current
    var activeDirection by remember { mutableStateOf<GbButton?>(null) }

    fun triggerHaptic() {
        performHapticFeedback(context, 15)
    }

    val armColor = if (isDarkTheme) Color(0xFF222225) else Color(0xFF2B2D2F)
    val borderColor = if (isDarkTheme) Color(0xFF3F3F46) else Color.Transparent

    Box(
        modifier = modifier
            .size(160.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val dir = getDirectionFromOffset(offset, 160f)
                        if (dir != activeDirection) {
                            activeDirection?.let { onDirectionChange(it, false) }
                            activeDirection = dir
                            dir?.let {
                                triggerHaptic()
                                onDirectionChange(it, true)
                            }
                        }
                    },
                    onDragEnd = {
                        activeDirection?.let { onDirectionChange(it, false) }
                        activeDirection = null
                    },
                    onDragCancel = {
                        activeDirection?.let { onDirectionChange(it, false) }
                        activeDirection = null
                    },
                    onDrag = { change, _ ->
                        val dir = getDirectionFromOffset(change.position, 160f)
                        if (dir != activeDirection) {
                            activeDirection?.let { onDirectionChange(it, false) }
                            activeDirection = dir
                            dir?.let {
                                triggerHaptic()
                                onDirectionChange(it, true)
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Horizontal Cross Bar
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(50.dp)
                .shadow(6.dp, RoundedCornerShape(8.dp))
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .background(armColor, RoundedCornerShape(8.dp))
        )
        // Vertical Cross Bar
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(150.dp)
                .shadow(6.dp, RoundedCornerShape(8.dp))
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .background(armColor, RoundedCornerShape(8.dp))
        )

        // Center Box detail
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(if (isDarkTheme) Color(0xFF18181B) else Color(0xFF1F2022), CircleShape)
        )

        // Directional Arrow Symbols (matching image!)
        // Top Arrow
        Icon(
            imageVector = Icons.Default.ArrowDropUp,
            contentDescription = "Up",
            tint = if (activeDirection == GbButton.UP) Color.White else Color(0xFF808088),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .size(28.dp)
        )
        // Bottom Arrow
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = "Down",
            tint = if (activeDirection == GbButton.DOWN) Color.White else Color(0xFF808088),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .size(28.dp)
        )
        // Left Arrow
        Icon(
            imageVector = Icons.Default.ArrowLeft,
            contentDescription = "Left",
            tint = if (activeDirection == GbButton.LEFT) Color.White else Color(0xFF808088),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
                .size(28.dp)
        )
        // Right Arrow
        Icon(
            imageVector = Icons.Default.ArrowRight,
            contentDescription = "Right",
            tint = if (activeDirection == GbButton.RIGHT) Color.White else Color(0xFF808088),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .size(28.dp)
        )
    }
}

private fun getDirectionFromOffset(offset: Offset, sizePx: Float): GbButton? {
    val center = sizePx / 2f
    val dx = offset.x - center
    val dy = offset.y - center

    if (dx * dx + dy * dy < 25 * 25) return null // Deadzone

    return if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
        if (dx > 0) GbButton.RIGHT else GbButton.LEFT
    } else {
        if (dy > 0) GbButton.DOWN else GbButton.UP
    }
}

@Composable
fun ActionButtons(
    onButtonPress: (GbButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true
) {
    val context = LocalContext.current

    fun triggerHaptic() {
        performHapticFeedback(context, 20)
    }

    val buttonColor = if (isDarkTheme) Color(0xFF222225) else Color(0xFF9E1F30)
    val borderColor = if (isDarkTheme) Color(0xFF52525B) else Color.Transparent

    if (isDarkTheme) {
        // Vertical stacked dark round buttons matching the exact image layout!
        Column(
            modifier = modifier
                .width(90.dp)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Button A
            RoundGbButton(
                text = "A",
                color = buttonColor,
                borderColor = borderColor,
                onPress = { pressed ->
                    if (pressed) triggerHaptic()
                    onButtonPress(GbButton.A, pressed)
                },
                testTag = "button_a"
            )

            // Button B
            RoundGbButton(
                text = "B",
                color = buttonColor,
                borderColor = borderColor,
                onPress = { pressed ->
                    if (pressed) triggerHaptic()
                    onButtonPress(GbButton.B, pressed)
                },
                testTag = "button_b"
            )
        }
    } else {
        // Angled diagonal Game Boy style
        Box(
            modifier = modifier
                .width(160.dp)
                .height(140.dp)
                .rotate(-20f),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundGbButton(
                    text = "B",
                    color = buttonColor,
                    borderColor = borderColor,
                    onPress = { pressed ->
                        if (pressed) triggerHaptic()
                        onButtonPress(GbButton.B, pressed)
                    },
                    testTag = "button_b"
                )

                RoundGbButton(
                    text = "A",
                    color = buttonColor,
                    borderColor = borderColor,
                    onPress = { pressed ->
                        if (pressed) triggerHaptic()
                        onButtonPress(GbButton.A, pressed)
                    },
                    testTag = "button_a"
                )
            }
        }
    }
}

@Composable
fun RoundGbButton(
    text: String,
    color: Color,
    borderColor: Color = Color.Transparent,
    onPress: (Boolean) -> Unit,
    testTag: String
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1.0f)

    Box(
        modifier = Modifier
            .size(62.dp)
            .scale(scale)
            .testTag(testTag)
            .shadow(if (isPressed) 2.dp else 6.dp, CircleShape)
            .border(2.dp, borderColor, CircleShape)
            .background(if (isPressed) color.copy(alpha = 0.7f) else color, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPress(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPress(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ShoulderPillButton(
    label: String,
    onPress: (Boolean) -> Unit,
    testTag: String
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(80.dp)
            .height(28.dp)
            .testTag(testTag)
            .shadow(if (isPressed) 1.dp else 3.dp, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF52525B), RoundedCornerShape(14.dp))
            .background(
                if (isPressed) Color(0xFF333338) else Color(0xFF1E1E22),
                RoundedCornerShape(14.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPress(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPress(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color(0xFFD4D4D8),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SystemPillButton(
    label: String,
    onPress: (Boolean) -> Unit,
    testTag: String,
    isDarkTheme: Boolean = true
) {
    var isPressed by remember { mutableStateOf(false) }

    if (isDarkTheme) {
        Box(
            modifier = Modifier
                .width(68.dp)
                .height(22.dp)
                .testTag(testTag)
                .border(1.dp, Color(0xFF52525B), RoundedCornerShape(11.dp))
                .background(
                    if (isPressed) Color(0xFF3f3f46) else Color(0xFF27272a),
                    RoundedCornerShape(11.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            onPress(true)
                            tryAwaitRelease()
                            isPressed = false
                            onPress(false)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color(0xFFA1A1AA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(14.dp)
                    .rotate(-20f)
                    .testTag(testTag)
                    .shadow(if (isPressed) 1.dp else 3.dp, RoundedCornerShape(10.dp))
                    .background(if (isPressed) Color(0xFF333333) else Color(0xFF555555), RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                onPress(true)
                                tryAwaitRelease()
                                isPressed = false
                                onPress(false)
                            }
                        )
                    }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                color = Color(0xFF666666),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
