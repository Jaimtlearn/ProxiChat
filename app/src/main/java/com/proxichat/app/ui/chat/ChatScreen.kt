package com.proxichat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.proxichat.app.domain.model.ConnectionState
import com.proxichat.app.domain.model.MessageStatus
import com.proxichat.app.ui.components.ChatBubble
import com.proxichat.app.ui.components.ConnectionIndicator
import com.proxichat.app.ui.components.DateSeparator
import com.proxichat.app.ui.components.DeviceAvatar
import com.proxichat.app.ui.components.EmptyState
import com.proxichat.app.ui.components.MessageInput
import com.proxichat.app.ui.components.TypingIndicator
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        state.device?.let { device ->
                            DeviceAvatar(
                                displayName = device.displayName,
                                colorIndex = device.avatarColorIndex,
                                connectionState = state.connectionState,
                                size = 36.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Column {
                            Text(
                                text = state.device?.displayName ?: "Chat",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ConnectionIndicator(
                                    state = state.connectionState,
                                    size = 6.dp
                                )
                                Text(
                                    text = when {
                                        state.isRemoteTyping -> "typing..."
                                        state.connectionState == ConnectionState.CONNECTED -> "Connected"
                                        state.connectionState == ConnectionState.CONNECTING -> "Connecting..."
                                        else -> "Disconnected"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.isRemoteTyping)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column {
                // Connection lost banner
                AnimatedVisibility(
                    visible = state.connectionState == ConnectionState.DISCONNECTED ||
                            state.connectionState == ConnectionState.FAILED,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Connection lost",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = { viewModel.reconnect() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reconnect")
                        }
                    }
                }

                MessageInput(
                    value = inputText,
                    onValueChange = viewModel::onInputChanged,
                    onSend = viewModel::sendMessage,
                    onTypingStateChange = viewModel::sendTypingIndicator,
                    enabled = state.connectionState == ConnectionState.CONNECTED
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.shapes.medium
                )
            }
        }
    ) { paddingValues ->
        if (state.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.ChatBubbleOutline,
                    title = "No Messages Yet",
                    subtitle = "Send a message to start the conversation."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val groupedMessages = groupMessagesByDate(state.messages)

                groupedMessages.forEach { (dateKey, messages) ->
                    item(key = "date_$dateKey") {
                        DateSeparator(timestamp = messages.first().timestamp)
                    }

                    items(
                        items = messages,
                        key = { it.id }
                    ) { message ->
                        ChatBubble(
                            message = message,
                            modifier = if (message.status == MessageStatus.FAILED) {
                                Modifier.clickable { viewModel.retryMessage(message.id) }
                            } else {
                                Modifier
                            }
                        )
                    }
                }

                // Typing indicator
                if (state.isRemoteTyping) {
                    item(key = "typing") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            TypingIndicator()
                        }
                    }
                }
            }
        }
    }
}

private fun groupMessagesByDate(
    messages: List<com.proxichat.app.domain.model.ChatMessage>
): Map<String, List<com.proxichat.app.domain.model.ChatMessage>> {
    return messages.groupBy { message ->
        val cal = Calendar.getInstance().apply { timeInMillis = message.timestamp }
        "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }
}
