package com.hereliesaz.blusnu.ui.injectable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.InjectionMode
import com.hereliesaz.blusnu.ui.components.ResultActions

/**
 * Screen for InjectaBLE (Packet Injection).
 *
 * Injects packets into active BLE connections via external nRF52 hardware.
 * Enables role hijacking and MITM on live connections without disrupting them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InjectableScreen(viewModel: InjectableViewModel) {
    val devices by viewModel.devices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val hardwareConnected by viewModel.hardwareConnected.collectAsState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var deviceExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "InjectaBLE (Packet Injection)",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Injects packets into active BLE connections via external nRF52 hardware. " +
                        "Enables role hijacking and MITM on live connections without disrupting them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Hardware connection status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hardwareConnected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (hardwareConnected) "Hardware: Connected" else "Hardware: Disconnected",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (hardwareConnected) {
                        OutlinedButton(
                            onClick = { viewModel.disconnectHardware() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect Hardware")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.connectHardware() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect nRF52 Hardware")
                        }
                    }
                }
            }

            // Injection mode dropdown
            ExposedDropdownMenuBox(
                expanded = modeExpanded,
                onExpandedChange = { modeExpanded = !modeExpanded }
            ) {
                TextField(
                    value = when (selectedMode) {
                        InjectionMode.SLAVE_HIJACK -> "Slave Hijack"
                        InjectionMode.MASTER_HIJACK -> "Master Hijack"
                        InjectionMode.PACKET_INJECT -> "Packet Inject"
                        InjectionMode.FULL_MITM -> "Full MITM"
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Injection Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isRunning)
                )
                ExposedDropdownMenu(
                    expanded = modeExpanded,
                    onDismissRequest = { modeExpanded = false }
                ) {
                    InjectionMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (mode) {
                                        InjectionMode.SLAVE_HIJACK -> "Slave Hijack"
                                        InjectionMode.MASTER_HIJACK -> "Master Hijack"
                                        InjectionMode.PACKET_INJECT -> "Packet Inject"
                                        InjectionMode.FULL_MITM -> "Full MITM"
                                    }
                                )
                            },
                            onClick = {
                                viewModel.selectMode(mode)
                                modeExpanded = false
                            }
                        )
                    }
                }
            }

            // Device Selection
            ExposedDropdownMenuBox(
                expanded = deviceExpanded,
                onExpandedChange = { deviceExpanded = !deviceExpanded }
            ) {
                TextField(
                    value = selectedDevice?.let { device ->
                        device.name ?: "Unknown Device (${device.macAddress})"
                    } ?: "Select Target Device",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Target Device") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isRunning)
                )
                ExposedDropdownMenu(
                    expanded = deviceExpanded,
                    onDismissRequest = { deviceExpanded = false }
                ) {
                    devices.forEach { device ->
                        DropdownMenuItem(
                            text = {
                                Text(device.name ?: "Unknown Device (${device.macAddress})")
                            },
                            onClick = {
                                viewModel.selectDevice(device)
                                deviceExpanded = false
                            }
                        )
                    }
                    if (devices.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No devices found -- run a scan first") },
                            onClick = { deviceExpanded = false },
                            enabled = false
                        )
                    }
                }
            }

            // Logs
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.DarkGray.copy(alpha = 0.3f))
                    .padding(8.dp)
            ) {
                LazyColumn(reverseLayout = true) {
                    items(logs.reversed()) { log ->
                        Text(text = log, style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                }
            }

            // Copy/Share result actions
            if (logs.isNotEmpty()) {
                ResultActions(
                    resultText = logs.joinToString("\n"),
                    label = "InjectaBLE Results",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isRunning) {
                CircularProgressIndicator()
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRunning) {
                    // Stop button
                    OutlinedButton(
                        onClick = { viewModel.stopAttack() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("STOP INJECTION")
                    }
                } else {
                    // Start button
                    Button(
                        onClick = { viewModel.startAttack() },
                        enabled = selectedDevice != null && hardwareConnected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("START INJECTION")
                    }
                }
            }

            Spacer(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.1f))
        }
    }
}
