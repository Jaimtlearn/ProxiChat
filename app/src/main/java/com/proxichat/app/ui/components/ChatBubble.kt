package com.proxichat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proxichat.app.domain.model.ChatMessage
import com.proxichat.app.domain.model.MessageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatBubble(
    message: ChatMessage,
    showTimestamp: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isOutgoing = message.isOutgoing
    val maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.78f

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    }

    val bubbleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val textColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        label = "bubble_appear"
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 80.dp, max = maxWidth)
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        // Encryption indicator
                        if (message.isEncrypted) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Encrypted",
                                modifier = Modifier.size(10.dp),
                                tint = textColor.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }

                        // Timestamp
                        if (showTimestamp) {
                            Text(
                                text = formatTime(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.6f)
                            )
                        }

                        // Status indicator (outgoing only)
                        if (isOutgoing) {
                            Spacer(modifier = Modifier.width(4.dp))
                            MessageStatusIcon(status = message.status, tint = textColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(
    status: MessageStatus,
    tint: Color,
    modifier: Modifier = Modifier
) {
    when (status) {
        MessageStatus.SENDING -> {
            CircularProgressIndicator(
                modifier = modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = tint.copy(alpha = 0.5f)
            )
        }
        MessageStatus.SENT -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Sent",
                modifier = modifier.size(14.dp),
                tint = tint.copy(alpha = 0.6f)
            )
        }
        MessageStatus.DELIVERED -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Delivered",
                modifier = modifier.size(14.dp),
                tint = tint.copy(alpha = 0.6f)
            )
        }
        MessageStatus.READ -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Read",
                modifier = modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        MessageStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Failed",
                modifier = modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun DateSeparator(timestamp: Long, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatDate(timestamp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            TypingDot(delayMillis = index * 200)
        }
    }
}

@Composable
private fun TypingDot(delayMillis: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 600,
                delayMillis = delayMillis,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha.value)
            )
    )
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val dayMs = 24 * 60 * 60 * 1000L

    return when {
        diff < dayMs -> "Today"
        diff < 2 * dayMs -> "Yesterday"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
