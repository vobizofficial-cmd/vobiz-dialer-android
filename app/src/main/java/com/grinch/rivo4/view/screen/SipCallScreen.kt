package com.grinch.rivo4.view.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grinch.rivo4.auth.CredentialStore
import com.grinch.rivo4.sip.SipCallController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.linphone.core.Call
import kotlin.math.roundToInt

@Composable
fun SipCallScreen(
    state: SipCallController.SipCallUiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit = {},
    onToggleBluetooth: () -> Unit = {},
    onToggleRecord: () -> Unit = {},
    onTransfer: (String) -> Unit = {},
) {
    var seconds by remember { mutableIntStateOf(0) }
    var keypadOpen by remember { mutableStateOf(false) }
    var digits by remember { mutableStateOf("") }
    var transferOpen by remember { mutableStateOf(false) }
    var transferTarget by remember { mutableStateOf("") }

    LaunchedEffect(state.active) {
        if (!state.active) {
            keypadOpen = false
            digits = ""
            transferOpen = false
            transferTarget = ""
        }
    }

    LaunchedEffect(state.connectedAt, state.active, state.isIncoming) {
        if (state.connectedAt > 0L) {
            while (true) {
                seconds = ((System.currentTimeMillis() - state.connectedAt) / 1000)
                    .toInt()
                    .coerceAtLeast(0)
                delay(1000)
            }
        } else {
            seconds = 0
        }
    }

    val incoming =
        state.isIncoming && state.connectedAt == 0L && state.state != Call.State.End &&
            state.state != Call.State.Released && state.state != Call.State.Error

    val isOutgoingPhase = !state.isIncoming && state.active && (
        state.state == Call.State.OutgoingInit ||
        state.state == Call.State.OutgoingProgress ||
        state.state == Call.State.OutgoingRinging ||
        state.state == Call.State.OutgoingEarlyMedia
    )

    val effectiveCallerId = remember(isOutgoingPhase) { CredentialStore.getEffectiveCallerId() }
    val callerIdSubtitle = if (isOutgoingPhase) {
        if (!effectiveCallerId.isNullOrBlank()) "Outgoing \u00B7 from $effectiveCallerId"
        else "Outgoing \u00B7 from default number"
    } else null

    val status = when {
        isOutgoingPhase -> callerIdSubtitle ?: "Calling\u2026"
        incoming -> "Incoming Vobiz Call"
        !state.active -> if (state.endedReason.isNotEmpty()) {
            "Call ended \u00B7 ${state.endedReason.replace('_', ' ')}"
        } else "Call ended"
        state.state == Call.State.Paused || state.state == Call.State.PausedByRemote || state.held -> "On hold"
        state.connectedAt > 0L -> if (state.recording) {
            "REC \u00B7 ${formatCallDuration(seconds)}"
        } else formatCallDuration(seconds)
        else -> ""
    }

    val headerName = state.displayName.ifEmpty { state.number }.ifEmpty { "Unknown" }
    val showNumberAsHeader = state.displayName.isNotEmpty() && state.number.isNotEmpty() &&
        state.displayName != state.number

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F172A), Color(0xFF020617))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(if (keypadOpen) 36.dp else 72.dp))

            Text(
                text = headerName,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )

            if (showNumberAsHeader) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.number,
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = status,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.weight(1f))

            AnimatedContent(
                targetState = incoming,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            scaleIn(initialScale = 0.9f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                        .togetherWith(
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                    scaleOut(targetScale = 0.9f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        )
                },
                label = "IncomingControlsTransition",
                modifier = Modifier.fillMaxWidth()
            ) { isIncomingState ->
                if (isIncomingState) {
                    // UI-1: INCOMING CALL SCREEN — SHOW ONLY ACCEPT & DECLINE WITH GESTURE SWIPE
                    IncomingCallActions(
                        onAccept = onAccept,
                        onDecline = onDecline
                    )
                } else if (state.active) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (keypadOpen) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = digits,
                                    color = Color.White,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.width(20.dp))
                                Surface(
                                    onClick = {
                                        keypadOpen = false
                                        digits = ""
                                    },
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.12f),
                                    contentColor = Color.White,
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Hide keypad",
                                            modifier = Modifier.size(26.dp),
                                            tint = Color.White,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            KeypadGrid(onDigit = { d ->
                                digits += d
                                SipCallController.sendDtmf(d)
                            })
                            Spacer(modifier = Modifier.height(14.dp))
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                CircleActionButton(
                                    icon = if (state.muted) Icons.Default.MicOff else Icons.Default.Mic,
                                    container = if (state.muted) Color.White.copy(alpha = 0.22f)
                                    else Color.White.copy(alpha = 0.12f),
                                    contentDescription = "Mute",
                                    onClick = onToggleMute,
                                )
                                CircleActionButton(
                                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                                    container = if (state.speakerOn) Color(0xFF1DB954).copy(alpha = 0.30f)
                                    else Color.White.copy(alpha = 0.12f),
                                    contentDescription = "Speaker",
                                    onClick = onToggleSpeaker,
                                )
                                CircleActionButton(
                                    icon = if (state.held) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    container = if (state.held) Color(0xFF1DB954).copy(alpha = 0.30f)
                                    else Color.White.copy(alpha = 0.12f),
                                    contentDescription = "Hold",
                                    onClick = onToggleHold,
                                )
                                CircleActionButton(
                                    icon = Icons.Default.Bluetooth,
                                    container = if (state.bluetoothOn) Color(0xFF1DB954).copy(alpha = 0.30f)
                                    else Color.White.copy(alpha = 0.12f),
                                    contentDescription = "Bluetooth",
                                    onClick = onToggleBluetooth,
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                CircleActionButton(
                                    icon = Icons.Default.Dialpad,
                                    container = Color.White.copy(alpha = 0.12f),
                                    contentDescription = "Keypad",
                                    onClick = { keypadOpen = true },
                                )
                                CircleActionButton(
                                    icon = Icons.AutoMirrored.Filled.CallSplit,
                                    container = Color.White.copy(alpha = 0.12f),
                                    contentDescription = "Transfer",
                                    onClick = {
                                        transferTarget = ""
                                        transferOpen = true
                                    },
                                )
                                CircleActionButton(
                                    icon = if (state.recording) Icons.Default.Stop
                                    else Icons.Default.FiberManualRecord,
                                    container = if (state.recording) Color(0xFFE53935).copy(alpha = 0.30f)
                                    else Color.White.copy(alpha = 0.12f),
                                    contentDescription = "Record",
                                    onClick = onToggleRecord,
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                        CircleActionButton(
                            icon = Icons.Default.CallEnd,
                            container = Color(0xFFE53935),
                            contentDescription = "End call",
                            onClick = onEnd,
                        )
                    }
                } else {
                    CircleActionButton(
                        icon = Icons.Default.CallEnd,
                        container = Color(0xFFE53935),
                        contentDescription = "Close",
                        onClick = onEnd,
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (transferOpen) {
        AlertDialog(
            onDismissRequest = { transferOpen = false },
            containerColor = Color(0xFF1A2233),
            title = { Text("Transfer call", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = transferTarget,
                    onValueChange = { transferTarget = it },
                    label = { Text("Destination number") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF1DB954),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.30f),
                        focusedLabelColor = Color(0xFF1DB954),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.60f),
                        cursorColor = Color(0xFF1DB954),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        transferOpen = false
                        onTransfer(transferTarget)
                        transferTarget = ""
                    },
                    enabled = transferTarget.trim().isNotEmpty(),
                ) { Text("Transfer", color = Color(0xFF1DB954)) }
            },
            dismissButton = {
                TextButton(onClick = { transferOpen = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.70f))
                }
            },
        )
    }
}

/**
 * UI-1 Expressive Gestured Incoming Call Action Buttons:
 * Green Accept (Swipe up / Tap) and Red Decline (Swipe down / Tap) with anchored spring-drag feedback.
 */
@Composable
private fun IncomingCallActions(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val acceptOffsetY = remember { Animatable(0f) }
    val declineOffsetY = remember { Animatable(0f) }
    var acceptThresholdCrossed by remember { mutableStateOf(false) }
    var declineThresholdCrossed by remember { mutableStateOf(false) }

    val swipeThresholdPx = -300f // Up for Accept
    val declineThresholdPx = 300f // Down for Decline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Red Decline Button (Swipe Down / Tap)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset { IntOffset(0, declineOffsetY.value.roundToInt()) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (declineOffsetY.value >= declineThresholdPx) {
                                onDecline()
                            } else {
                                scope.launch {
                                    declineOffsetY.animateTo(
                                        0f,
                                        spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            }
                            declineThresholdCrossed = false
                        },
                        onDragCancel = {
                            scope.launch {
                                declineOffsetY.animateTo(
                                    0f,
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                            declineThresholdCrossed = false
                        },
                        onVerticalDrag = { _, dragAmount ->
                            val newY = (declineOffsetY.value + dragAmount).coerceAtLeast(0f)
                            scope.launch { declineOffsetY.snapTo(newY) }
                            if (newY >= declineThresholdPx && !declineThresholdCrossed) {
                                declineThresholdCrossed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (newY < declineThresholdPx && declineThresholdCrossed) {
                                declineThresholdCrossed = false
                            }
                        }
                    )
                }
        ) {
            Text(
                text = "Swipe down",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Surface(
                onClick = onDecline,
                shape = RoundedCornerShape(if (declineThresholdCrossed) 18.dp else 36.dp),
                color = Color(0xFFE53935),
                contentColor = Color.White,
                modifier = Modifier
                    .size(80.dp)
                    .scale(if (declineThresholdCrossed) 1.12f else 1f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Decline",
                        modifier = Modifier.size(38.dp),
                        tint = Color.White,
                    )
                }
            }
        }

        // Green Accept Button (Swipe Up / Tap)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset { IntOffset(0, acceptOffsetY.value.roundToInt()) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (acceptOffsetY.value <= swipeThresholdPx) {
                                onAccept()
                            } else {
                                scope.launch {
                                    acceptOffsetY.animateTo(
                                        0f,
                                        spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            }
                            acceptThresholdCrossed = false
                        },
                        onDragCancel = {
                            scope.launch {
                                acceptOffsetY.animateTo(
                                    0f,
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                            acceptThresholdCrossed = false
                        },
                        onVerticalDrag = { _, dragAmount ->
                            val newY = (acceptOffsetY.value + dragAmount).coerceAtMost(0f)
                            scope.launch { acceptOffsetY.snapTo(newY) }
                            if (newY <= swipeThresholdPx && !acceptThresholdCrossed) {
                                acceptThresholdCrossed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (newY > swipeThresholdPx && acceptThresholdCrossed) {
                                acceptThresholdCrossed = false
                            }
                        }
                    )
                }
        ) {
            Text(
                text = "Swipe up",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Surface(
                onClick = onAccept,
                shape = RoundedCornerShape(if (acceptThresholdCrossed) 18.dp else 36.dp),
                color = Color(0xFF1DB954),
                contentColor = Color.White,
                modifier = Modifier
                    .size(80.dp)
                    .scale(if (acceptThresholdCrossed) 1.12f else 1f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Answer",
                        modifier = Modifier.size(38.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun CircleActionButton(
    icon: ImageVector,
    container: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = Color.White,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(34.dp),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun KeypadGrid(onDigit: (Char) -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#"),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { label ->
                    Surface(
                        onClick = { onDigit(label[0]) },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.10f),
                        contentColor = Color.White,
                        modifier = Modifier.size(66.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Normal,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun formatCallDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}
