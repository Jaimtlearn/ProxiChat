package com.proxichat.app.ui.discovery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.proxichat.app.ui.components.DeviceCard
import com.proxichat.app.ui.components.EmptyState
import com.proxichat.app.ui.components.ScanningIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onDeviceClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ProxiChat",
                            fontWeight = FontWeight.Bold
                        )
                        if (state.isScanning) {
                            Spacer(modifier = Modifier.width(12.dp))
                            ScanningIndicator(isScanning = true)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (state.isScanning) viewModel.stopDiscovery()
                    else viewModel.startDiscovery()
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = if (state.isScanning) Icons.Default.BluetoothSearching
                    else Icons.Default.Refresh,
                    contentDescription = if (state.isScanning) "Stop scanning" else "Start scanning"
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Status bar
            AnimatedVisibility(
                visible = state.isScanning,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Scanning for nearby devices...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (state.devices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.BluetoothSearching,
                        title = "No Devices Found",
                        subtitle = "Make sure Bluetooth is enabled on nearby devices running ProxiChat.",
                        animate = state.isScanning
                    )
                }
            } else {
                // Separate connected devices and discovered devices
                val connected = state.devices.filter { it.connectionState == ConnectionState.CONNECTED }
                val others = state.devices.filter { it.connectionState != ConnectionState.CONNECTED }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .animateContentSize(spring(stiffness = Spring.StiffnessLow)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (connected.isNotEmpty()) {
                        item {
                            SectionHeader("Connected")
                        }
                        items(
                            items = connected,
                            key = { it.address }
                        ) { device ->
                            DeviceCard(
                                device = device,
                                onClick = { onDeviceClick(device.address) },
                                modifier = Modifier.animateItemPlacement()
                            )
                        }
                    }

                    if (others.isNotEmpty()) {
                        item {
                            SectionHeader(
                                if (connected.isNotEmpty()) "Nearby" else "Nearby Devices"
                            )
                        }
                        items(
                            items = others,
                            key = { it.address }
                        ) { device ->
                            DeviceCard(
                                device = device,
                                onClick = {
                                    if (device.connectionState == ConnectionState.DISCONNECTED ||
                                        device.connectionState == ConnectionState.FAILED
                                    ) {
                                        viewModel.connectToDevice(device.address)
                                    } else if (device.connectionState == ConnectionState.CONNECTED) {
                                        onDeviceClick(device.address)
                                    }
                                },
                                modifier = Modifier.animateItemPlacement()
                            )
                        }
                    }

                    // Bottom spacer for FAB
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}
