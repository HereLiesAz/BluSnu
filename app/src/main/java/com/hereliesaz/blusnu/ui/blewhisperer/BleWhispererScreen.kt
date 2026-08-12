package com.hereliesaz.blusnu.ui.blewhisperer

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.WhispererMode
import com.hereliesaz.blusnu.ui.components.ResultActions

/**
 * Screen for BLEWhisperer (Covert Data Exfiltration).
 *
 * Provides mode selection (Transmit/Receive), data input for transmit mode,
 * a log area for operation output, and start/stop controls.
 */
@Composable
fun BleWhispererScreen(viewModel: BleWhispererViewModel) {
    val selectedMode by viewModel.selectedMode.collectAsState()
    val transmitData by viewModel.transmitData.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

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
                text = "BLEWhisperer (Data Exfiltration)",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Covert data exfiltration via BLE advertisements. " +
                        "Embeds arbitrary data in advertisement payloads " +
                        "for nearby collection without pairing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Mode Selection (Radio Buttons).
            Text(
                text = "Mode",
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WhispererMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == mode,
                            onClick = { viewModel.setMode(mode) },
                            enabled = !isRunning
                        )
                        Text(
                            text = mode.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Transmit Data Input (only shown in TRANSMIT mode).
            if (selectedMode == WhispererMode.TRANSMIT) {
                OutlinedTextField(
                    value = transmitData,
                    onValueChange = { viewModel.setTransmitData(it) },
                    label = { Text("Data to Exfiltrate") },
                    placeholder = { Text("Enter text data to embed in advertisements...") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunning,
                    minLines = 3,
                    maxLines = 5
                )
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
                    label = "BLEWhisperer Results",
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
                        onClick = { viewModel.stop() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("STOP")
                    }
                } else {
                    // Start button.
                    Button(
                        onClick = { viewModel.start() },
                        enabled = selectedMode == WhispererMode.RECEIVE || transmitData.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (selectedMode == WhispererMode.TRANSMIT)
                                "START TRANSMIT"
                            else
                                "START RECEIVE"
                        )
                    }
                }
            }

            // Bottom padding for Nav Rail.
            Spacer(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.1f))
        }
    }
}
