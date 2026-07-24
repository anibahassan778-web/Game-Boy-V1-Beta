package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GameControllerOverlay(
    viewModel: EmulatorViewModel,
    modifier: Modifier = Modifier,
    opacity: Float = 0.5f
) {
    GameControllerOverlay(
        onButtonStateChange = { button, pressed -> viewModel.setButtonState(button, pressed) },
        modifier = modifier,
        opacity = opacity
    )
}

@Composable
fun GameControllerOverlay(
    onButtonStateChange: (GbButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    opacity: Float = 0.5f
) {
    val context = LocalContext.current
    val buttonBgColor = Color.White.copy(alpha = opacity * 0.25f)
    val buttonActiveColor = Color.White.copy(alpha = opacity * 0.6f)
    val borderColor = Color.White.copy(alpha = opacity * 0.4f)
    val textColor = Color.White.copy(alpha = opacity * 0.9f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("game_controller_overlay")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Upper Row: D-Pad on Left, A/B on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Transparent D-Pad
                TransparentDPad(
                    onDirectionChange = onButtonStateChange,
                    opacity = opacity,
                    modifier = Modifier.testTag("overlay_dpad")
                )

                // Transparent Action Buttons (A & B)
                TransparentActionButtons(
                    onButtonPress = onButtonStateChange,
                    opacity = opacity,
                    modifier = Modifier.testTag("overlay_action_buttons")
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Lower Row: Select and Start Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransparentPillButton(
                    label = "SELECT",
                    onPress = { pressed -> onButtonStateChange(GbButton.SELECT, pressed) },
                    testTag = "button_select",
                    opacity = opacity
                )

                TransparentPillButton(
                    label = "START",
                    onPress = { pressed -> onButtonStateChange(GbButton.START, pressed) },
                    testTag = "button_start",
                    opacity = opacity
                )
            }
        }
    }
}

@Composable
private fun TransparentDPad(
    onDirectionChange: (GbButton, Boolean) -> Unit,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    var activeDirection by remember { mutableStateOf<GbButton?>(null) }

    val armColor = Color.White.copy(alpha = opacity * 0.2f)
    val activeArmColor = Color.White.copy(alpha = opacity * 0.5f)
    val borderColor = Color.White.copy(alpha = opacity * 0.35f)

    Box(
        modifier = modifier
            .size(150.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val dir = getOverlayDirectionFromOffset(offset, 150f)
                        if (dir != activeDirection) {
                            activeDirection?.let { onDirectionChange(it, false) }
                            activeDirection = dir
                            dir?.let { onDirectionChange(it, true) }
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
                        val dir = getOverlayDirectionFromOffset(change.position, 150f)
                        if (dir != activeDirection) {
                            activeDirection?.let { onDirectionChange(it, false) }
                            activeDirection = dir
                            dir?.let { onDirectionChange(it, true) }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Horizontal Arm
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(46.dp)
                .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
                .background(
                    if (activeDirection == GbButton.LEFT || activeDirection == GbButton.RIGHT) activeArmColor else armColor,
                    RoundedCornerShape(8.dp)
                )
        )
        // Vertical Arm
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(140.dp)
                .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
                .background(
                    if (activeDirection == GbButton.UP || activeDirection == GbButton.DOWN) activeArmColor else armColor,
                    RoundedCornerShape(8.dp)
                )
        )

        // Center Indicator
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(Color.Black.copy(alpha = opacity * 0.3f), CircleShape)
        )

        // Labels
        Box(modifier = Modifier.fillMaxSize()) {
            Text("▲", color = Color.White.copy(alpha = opacity * 0.8f), fontSize = 12.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            Text("▼", color = Color.White.copy(alpha = opacity * 0.8f), fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
            Text("◀", color = Color.White.copy(alpha = opacity * 0.8f), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
            Text("▶", color = Color.White.copy(alpha = opacity * 0.8f), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
        }
    }
}

@Composable
private fun TransparentActionButtons(
    onButtonPress: (GbButton, Boolean) -> Unit,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(140.dp)
            .height(130.dp)
    ) {
        // B Button (Left / Lower)
        TransparentRoundButton(
            label = "B",
            onPress = { pressed -> onButtonPress(GbButton.B, pressed) },
            testTag = "button_b",
            opacity = opacity,
            modifier = Modifier.align(Alignment.BottomStart)
        )

        // A Button (Right / Upper)
        TransparentRoundButton(
            label = "A",
            onPress = { pressed -> onButtonPress(GbButton.A, pressed) },
            testTag = "button_a",
            opacity = opacity,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun TransparentRoundButton(
    label: String,
    onPress: (Boolean) -> Unit,
    testTag: String,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val bgColor = if (isPressed) Color.White.copy(alpha = opacity * 0.6f) else Color.White.copy(alpha = opacity * 0.25f)
    val borderColor = Color.White.copy(alpha = opacity * 0.45f)
    val textColor = Color.White.copy(alpha = opacity * 0.95f)

    Box(
        modifier = modifier
            .size(58.dp)
            .testTag(testTag)
            .clip(CircleShape)
            .border(1.5.dp, borderColor, CircleShape)
            .background(bgColor, CircleShape)
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
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TransparentPillButton(
    label: String,
    onPress: (Boolean) -> Unit,
    testTag: String,
    opacity: Float
) {
    var isPressed by remember { mutableStateOf(false) }
    val bgColor = if (isPressed) Color.White.copy(alpha = opacity * 0.5f) else Color.White.copy(alpha = opacity * 0.2f)
    val borderColor = Color.White.copy(alpha = opacity * 0.4f)
    val textColor = Color.White.copy(alpha = opacity * 0.9f)

    Box(
        modifier = Modifier
            .width(64.dp)
            .height(24.dp)
            .testTag(testTag)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor, RoundedCornerShape(12.dp))
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
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun getOverlayDirectionFromOffset(offset: Offset, sizePx: Float): GbButton? {
    val center = sizePx / 2f
    val dx = offset.x - center
    val dy = offset.y - center
    val deadZone = sizePx * 0.12f

    if (kotlin.math.sqrt(dx * dx + dy * dy) < deadZone) {
        return null
    }

    return if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
        if (dx > 0) GbButton.RIGHT else GbButton.LEFT
    } else {
        if (dy > 0) GbButton.DOWN else GbButton.UP
    }
}
