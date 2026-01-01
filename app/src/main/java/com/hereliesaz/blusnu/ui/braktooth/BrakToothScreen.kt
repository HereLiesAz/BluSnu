package com.hereliesaz.blusnu.ui.braktooth

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
import com.hereliesaz.blusnu.data.BrakToothVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrakToothScreen(viewModel: BrakToothViewModel) {
    val devices by viewModel.devices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val selectedVector by viewModel.selectedVector.collectAsState()
    val hardwareConnected by viewModel.hardwareConnected.collectAsState()
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
            Text(
                text = "BrakTooth Fuzzer (ESP32)",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Requires dedicated ESP32 dongle flashed with BrakTooth firmware connected via USB-OTG.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            // Hardware Check
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (hardwareConnected) "Hardware: CONNECTED" else "Hardware: DISCONNECTED",
                    color = if (hardwareConnected) Color.Green else Color.Red
                )
                Button(
                    onClick = { viewModel.checkHardware() },
                    enabled = !isRunning
                ) {
                    Text("Check Hardware")
                }
            }

            // Device Selection
            ExposedDropdownMenuBox(
                expanded = deviceExpanded,
                onExpandedChange = { deviceExpanded = !deviceExpanded }
            ) {
                TextField(
                    value = selectedDevice?.name ?: "Select Target Device",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Target Device (Classic)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isRunning)
                )
                ExposedDropdownMenu(
                    expanded = deviceExpanded,
                    onDismissRequest = { deviceExpanded = false }
                ) {
                    devices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.name ?: device.macAddress) },
                            onClick = {
                                viewModel.selectDevice(device)
                                deviceExpanded = false
                            }
                        )
                    }
                }
            }

            // Vector Selection
            ExposedDropdownMenuBox(
                expanded = vectorExpanded,
                onExpandedChange = { vectorExpanded = !vectorExpanded }
            ) {
                TextField(
                    value = selectedVector.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Crash Vector") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vectorExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isRunning)
                )
                ExposedDropdownMenu(
                    expanded = vectorExpanded,
                    onDismissRequest = { vectorExpanded = false }
                ) {
                    BrakToothVector.values().forEach { vector ->
                        DropdownMenuItem(
                            text = { Text(vector.name) },
                            onClick = {
                                viewModel.selectVector(vector)
                                vectorExpanded = false
                            }
                        )
                    }
                }
            }
            Text(
                text = selectedVector.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Logs
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.DarkGray.copy(alpha = 0.3f))
                    .padding(8.dp)
            ) {
                LazyColumn(reverseLayout = true) {
                    items(logs) { log ->
                        Text(text = log, style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                }
            }

            if (isRunning) {
                CircularProgressIndicator()
            }

            Button(
                onClick = { viewModel.startFuzzing() },
                enabled = selectedDevice != null && hardwareConnected && !isRunning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "FUZZING IN PROGRESS..." else "START FUZZING")
            }

            Spacer(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.1f))
        }
    }
}
