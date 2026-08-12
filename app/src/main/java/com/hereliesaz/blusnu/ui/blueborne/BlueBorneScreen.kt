package com.hereliesaz.blusnu.ui.blueborne

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
import com.hereliesaz.blusnu.data.BlueBorneVector
import com.hereliesaz.blusnu.ui.components.ResultActions

/**
 * Screen for the BlueBorne zero-click RCE scanner.
 *
 * Tests for BlueBorne vulnerabilities across the BNEP, SDP, and L2CAP layers.
 * Supports selecting a specific attack vector or running a full vulnerability
 * assessment across all four CVEs. Requires Root + NDK.
 *
 * @param viewModel State holder for BlueBorne logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlueBorneScreen(viewModel: BlueBorneViewModel) {
    val devices by viewModel.devices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val selectedVector by viewModel.selectedVector.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var deviceExpanded by remember { mutableStateOf(false) }
    var vectorExpanded by remember { mutableStateOf(false) }

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
            // Header.
            Text(
                text = "BlueBorne (Zero-Click RCE)",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Tests for BlueBorne vulnerabilities (CVE-2017-0781/0782/0785/1000251) " +
                        "-- zero-click pre-auth RCE via L2CAP, BNEP, and SDP parsing flaws " +
                        "on legacy devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Device Selection Dropdown.
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
                    label = { Text("Target Device (Classic/Dual)") },
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

            // BlueBorne Vector Selection Dropdown.
            ExposedDropdownMenuBox(
                expanded = vectorExpanded,
                onExpandedChange = { vectorExpanded = !vectorExpanded }
            ) {
                TextField(
                    value = selectedVector?.let { "${it.cve}: ${it.description}" }
                        ?: "Full Assessment (all CVEs)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Attack Vector") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vectorExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isRunning)
                )
                ExposedDropdownMenu(
                    expanded = vectorExpanded,
                    onDismissRequest = { vectorExpanded = false }
                ) {
                    // "Full Assessment" option (null vector)
                    DropdownMenuItem(
                        text = { Text("Full Assessment (all CVEs)") },
                        onClick = {
                            viewModel.selectVector(null)
                            vectorExpanded = false
                        }
                    )
                    // Individual vector options
                    BlueBorneVector.values().forEach { vector ->
                        DropdownMenuItem(
                            text = { Text("${vector.cve}: ${vector.description}") },
                            onClick = {
                                viewModel.selectVector(vector)
                                vectorExpanded = false
                            }
                        )
                    }
                }
            }

            // Terminal-style Log Output.
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

            // Copy/Share result actions.
            if (logs.isNotEmpty()) {
                ResultActions(
                    resultText = logs.joinToString("\n"),
                    label = "BlueBorne Scan Results",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isRunning) {
                CircularProgressIndicator()
            }

            // Action buttons (start/stop).
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
                        Text("STOP SCAN")
                    }
                } else {
                    // Start button
                    Button(
                        onClick = { viewModel.startAttack() },
                        enabled = selectedDevice != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("RUN BLUEBORNE SCAN")
                    }
                }
            }

            // Bottom Padding.
            Spacer(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.1f))
        }
    }
}
