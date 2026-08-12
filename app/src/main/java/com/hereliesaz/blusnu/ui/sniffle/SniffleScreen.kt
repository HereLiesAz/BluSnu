package com.hereliesaz.blusnu.ui.sniffle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.ui.components.ResultActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SniffleScreen(viewModel: SniffleViewModel) {
    val state by viewModel.state.collectAsState()
    var deviceDropdownExpanded by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(SniffMode.ADVERTISING) }
    val logListState = rememberLazyListState()

    // Auto-scroll to bottom when new logs arrive.
    LaunchedEffect(state.deviceLogs.size) {
        if (state.deviceLogs.isNotEmpty()) {
            logListState.animateScrollToItem(state.deviceLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text("Sniffle BLE Sniffer", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Captures BLE packets using an external TI CC26x2/CC1352 Sniffle dongle via USB-OTG. " +
                    "Supports advertising sniffing, connection following, and passive monitoring.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Hardware connection status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Hardware: ${state.hardwareState}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                when (state.hardwareState) {
                    HardwareState.DISCONNECTED, HardwareState.CONNECTION_FAILED -> {
                        Button(
                            onClick = { viewModel.connectHardware() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect Sniffle Dongle")
                        }
                    }
                    HardwareState.CONNECTING -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    HardwareState.CONNECTED_BTLEJACK, HardwareState.CONNECTED_DUAL -> {
                        Button(
                            onClick = { viewModel.disconnectHardware() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect Hardware")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sniff mode selector (radio buttons)
        if (state.hardwareState == HardwareState.CONNECTED_BTLEJACK || state.hardwareState == HardwareState.CONNECTED_DUAL) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Sniff Mode", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))

                    SniffModeOption(
                        label = "Advertising Scan",
                        description = "Sniff all BLE advertisements on channels 37, 38, 39",
                        selected = selectedMode == SniffMode.ADVERTISING,
                        enabled = !state.isSniffing,
                        onClick = { selectedMode = SniffMode.ADVERTISING }
                    )
                    SniffModeOption(
                        label = "Connection Follow",
                        description = "Follow a specific device's connections by MAC address",
                        selected = selectedMode == SniffMode.CONNECTION_FOLLOW,
                        enabled = !state.isSniffing,
                        onClick = { selectedMode = SniffMode.CONNECTION_FOLLOW }
                    )
                    SniffModeOption(
                        label = "Passive Monitor",
                        description = "Follow the first new BLE connection observed",
                        selected = selectedMode == SniffMode.PASSIVE,
                        enabled = !state.isSniffing,
                        onClick = { selectedMode = SniffMode.PASSIVE }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Device selector (only shown for CONNECTION_FOLLOW mode)
            if (selectedMode == SniffMode.CONNECTION_FOLLOW) {
                ExposedDropdownMenuBox(
                    expanded = deviceDropdownExpanded,
                    onExpandedChange = { deviceDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state.selectedDevice?.let { it.name ?: it.macAddress }
                            ?: "Select a target device",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Device") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = deviceDropdownExpanded,
                        onDismissRequest = { deviceDropdownExpanded = false }
                    ) {
                        state.discoveredDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.name ?: device.macAddress) },
                                onClick = {
                                    viewModel.selectDevice(device)
                                    deviceDropdownExpanded = false
                                }
                            )
                        }
                        if (state.discoveredDevices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No devices found - run a scan first") },
                                onClick = { deviceDropdownExpanded = false },
                                enabled = false
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Start / Stop controls
            if (state.isSniffing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text(
                        "Sniffing (${selectedMode.name})...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.stopSniffing() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Stop Sniffing")
                }
            } else {
                Button(
                    onClick = { viewModel.startSniffing(selectedMode) },
                    enabled = selectedMode != SniffMode.CONNECTION_FOLLOW || state.selectedDevice != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Sniffing")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Log output and ResultActions
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Packet Log", style = MaterialTheme.typography.labelMedium)
                    ResultActions(
                        resultText = state.deviceLogs.joinToString("\n"),
                        label = "Sniffle Capture Log"
                    )
                }
                LazyColumn(
                    state = logListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.deviceLogs) { log ->
                        Text(log, style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.deviceLogs.isEmpty()) {
                        item {
                            Text(
                                "No packets captured yet. Connect the Sniffle dongle and start sniffing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A single radio-button row for selecting a [SniffMode].
 */
@Composable
private fun SniffModeOption(
    label: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
