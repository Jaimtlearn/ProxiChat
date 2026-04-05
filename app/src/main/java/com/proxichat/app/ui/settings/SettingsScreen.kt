package com.proxichat.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }
    var showDarkModeDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var editedName by remember(state.displayName) { mutableStateOf(state.displayName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold
                    )
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Profile section
            SectionTitle("Profile")

            ListItem(
                headlineContent = { Text("Display Name") },
                supportingContent = { Text(state.displayName) },
                modifier = Modifier.clickable { showNameDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Appearance section
            SectionTitle("Appearance")

            ListItem(
                headlineContent = { Text("Dark Mode") },
                supportingContent = {
                    Text(
                        when (state.darkMode) {
                            "on" -> "Always on"
                            "off" -> "Always off"
                            else -> "Follow system"
                        }
                    )
                },
                modifier = Modifier.clickable { showDarkModeDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Bluetooth section
            SectionTitle("Bluetooth")

            ListItem(
                headlineContent = { Text("Discoverable") },
                supportingContent = { Text("Allow nearby devices to find you") },
                trailingContent = {
                    Switch(
                        checked = state.isDiscoverable,
                        onCheckedChange = viewModel::setDiscoverable
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            ListItem(
                headlineContent = { Text("Auto-reconnect") },
                supportingContent = { Text("Automatically reconnect to known devices") },
                trailingContent = {
                    Switch(
                        checked = state.autoReconnect,
                        onCheckedChange = viewModel::setAutoReconnect
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Security section
            SectionTitle("Security")

            ListItem(
                headlineContent = { Text("Message Encryption") },
                supportingContent = { Text("Encrypt messages with AES-256-GCM") },
                trailingContent = {
                    Switch(
                        checked = state.encryptionEnabled,
                        onCheckedChange = viewModel::setEncryption
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Data section
            SectionTitle("Data")

            ListItem(
                headlineContent = {
                    Text(
                        "Clear Chat History",
                        color = MaterialTheme.colorScheme.error
                    )
                },
                supportingContent = { Text("Delete all messages") },
                modifier = Modifier.clickable { showClearHistoryDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // About section
            SectionTitle("About")

            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text(state.appVersion) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            ListItem(
                headlineContent = { Text("ProxiChat") },
                supportingContent = { Text("Bluetooth proximity chat — no internet required") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Edit name dialog
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Display Name") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { if (it.length <= 20) editedName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    supportingText = { Text("${editedName.length}/20") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editedName.isNotBlank()) {
                            viewModel.updateDisplayName(editedName.trim())
                        }
                        showNameDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dark mode dialog
    if (showDarkModeDialog) {
        AlertDialog(
            onDismissRequest = { showDarkModeDialog = false },
            title = { Text("Dark Mode") },
            text = {
                Column {
                    listOf(
                        "system" to "Follow system",
                        "on" to "Always on",
                        "off" to "Always off"
                    ).forEach { (value, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            modifier = Modifier.clickable {
                                viewModel.setDarkMode(value)
                                showDarkModeDialog = false
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (state.darkMode == value)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDarkModeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Clear history confirmation dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Chat History") },
            text = { Text("This will permanently delete all messages. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChatHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text(
                        "Delete All",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}
