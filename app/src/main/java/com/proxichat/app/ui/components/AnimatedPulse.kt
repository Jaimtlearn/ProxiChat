package com.proxichat.app.ui.components

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

@Composable
fun AnimatedPulse(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 48.dp,
    pulseCount: Int = 3
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulses = (0 until pulseCount).map { index ->
        val delay = index * 600
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1800,
                    delayMillis = delay,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulse_$index"
        )
    }

    Canvas(modifier = modifier.size(size)) {
        val centerX = this.size.width / 2
        val centerY = this.size.height / 2
        val maxRadius = this.size.minDimension / 2

        // Draw static center dot
        drawCircle(
            color = color,
            radius = maxRadius * 0.15f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
        )

        // Draw expanding pulses
        pulses.forEach { animatedValue ->
            val progress by animatedValue
            val radius = maxRadius * progress
            val alpha = (1f - progress).coerceIn(0f, 0.4f)

            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
            )
        }
    }
}

@Composable
fun ScanningIndicator(
    modifier: Modifier = Modifier,
    isScanning: Boolean
) {
    if (isScanning) {
        AnimatedPulse(
            modifier = modifier,
            color = MaterialTheme.colorScheme.primary,
            size = 32.dp,
            pulseCount = 2
        )
    }
}
