package com.proxichat.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.proxichat.app.domain.model.SignalStrength
import com.proxichat.app.ui.theme.SignalExcellent
import com.proxichat.app.ui.theme.SignalFair
import com.proxichat.app.ui.theme.SignalGood
import com.proxichat.app.ui.theme.SignalWeak

@Composable
fun SignalStrengthIndicator(
    signalStrength: SignalStrength,
    modifier: Modifier = Modifier
) {
    val activeColor = when (signalStrength) {
        SignalStrength.EXCELLENT -> SignalExcellent
        SignalStrength.GOOD -> SignalGood
        SignalStrength.FAIR -> SignalFair
        SignalStrength.WEAK -> SignalWeak
    }
    val inactiveColor = Color.Gray.copy(alpha = 0.25f)
    val bars = signalStrength.bars

    Canvas(modifier = modifier.size(width = 24.dp, height = 20.dp)) {
        val barCount = 4
        val totalWidth = size.width
        val totalHeight = size.height
        val barWidth = totalWidth / (barCount * 2 - 1)
        val cornerRadius = CornerRadius(barWidth / 3, barWidth / 3)

        for (i in 0 until barCount) {
            val barHeight = totalHeight * (0.25f + 0.25f * i)
            val x = i * barWidth * 2
            val y = totalHeight - barHeight
            val color = if (i < bars) activeColor else inactiveColor

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}
