package com.proxichat.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.proxichat.app.domain.model.ConnectionState

@Composable
fun ConnectionIndicator(
    state: ConnectionState,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp
) {
    val targetColor = when (state) {
        ConnectionState.CONNECTED -> Color(0xFF4CAF50) // Green
        ConnectionState.CONNECTING -> Color(0xFFFFC107) // Amber
        ConnectionState.DISCONNECTING -> Color(0xFFFFC107)
        ConnectionState.FAILED -> Color(0xFFF44336) // Red
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "connection_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "connecting_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val alpha = when (state) {
        ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> pulseAlpha
        else -> 1f
    }

    Canvas(modifier = modifier.size(size)) {
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = this.size.minDimension / 2
        )
    }
}
