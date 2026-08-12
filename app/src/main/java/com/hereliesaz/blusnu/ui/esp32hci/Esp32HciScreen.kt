package com.hereliesaz.blusnu.ui.esp32hci

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
import com.hereliesaz.blusnu.data.Esp32HciCommand
import com.hereliesaz.blusnu.ui.components.ResultActions

/**
 * Screen for ESP32 HCI Exploitation.
 *
 * Exploits undocumented vendor HCI commands (CVE-2025-27840) on ESP32 chips
 * via USB-OTG. Enables memory read/write, address spoofing, and firmware analysis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Esp32HciScreen(viewModel: Esp32HciViewModel) {
    val selectedCommand by viewModel.selectedCommand.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val hardwareConnected by viewModel.hardwareConnected.collectAsState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var expanded by remember { mutableStateOf(false) }

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
                text = "ESP32 HCI Exploitation",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Exploits undocumented vendor HCI commands (CVE-2025-27840) " +
                        "on ESP32 chips via USB-OTG. Enables memory read/write, " +
                        "address spoofing, and firmware analysis.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Hardware status card.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (hardwareConnected) "Hardware: Connected" else "Hardware: Disconnected",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (hardwareConnected) {
                        Button(
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
                            Text("Connect ESP32 via USB-OTG")
                        }
                    }
                }
            }

            // Command dropdown.
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = selectedCommand.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("HCI Command") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isRunning)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    Esp32HciCommand.entries.forEach { command ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(command.displayName)
                                    Text(
                                        text = command.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                viewModel.selectCommand(command)
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Logs.
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
                    label = "ESP32 HCI Exploitation Results",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isRunning) {
                CircularProgressIndicator()
            }

            // Action buttons.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRunning) {
                    // Stop button.
                    OutlinedButton(
                        onClick = { viewModel.stopAttack() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("STOP")
                    }
                } else {
                    // Execute button.
                    Button(
                        onClick = { viewModel.startAttack() },
                        enabled = hardwareConnected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("EXECUTE ${selectedCommand.displayName.uppercase()}")
                    }
                }
            }

            Spacer(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.1f))
        }
    }
}
