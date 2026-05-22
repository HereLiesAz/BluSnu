package com.hereliesaz.blusnu.ui.geolocation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.ui.components.LeafletMapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeolocationScreen(viewModel: GeolocationViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Geolocation", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Locates a BLE device by collecting RSSI readings as you walk around. Select a device, start tracking, and move in different directions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Device picker
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = uiState.selectedDevice?.let { "${it.name ?: it.macAddress} (RSSI: ${it.rssi})" }
                    ?: "Select a device to track",
                onValueChange = {},
                readOnly = true,
                label = { Text("Target Device") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                uiState.devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text("${device.name ?: device.macAddress} (RSSI: ${device.rssi})") },
                        onClick = {
                            viewModel.selectDevice(device)
                            expanded = false
                        }
                    )
                }
                if (uiState.devices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No devices found - run a scan first") },
                        onClick = { expanded = false },
                        enabled = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Start / Stop button
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (uiState.isTracking) viewModel.stopTracking()
                    else viewModel.startTracking()
                },
                enabled = uiState.selectedDevice != null,
                colors = if (uiState.isTracking)
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else
                    ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.isTracking) "Stop Tracking" else "Start Tracking")
            }
        }

        // Progress HUD
        if (uiState.isTracking || uiState.observationCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "${uiState.observationCount} readings collected",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (uiState.isTracking) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (uiState.observationCount / 20f).coerceAtMost(1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    val guidanceText = when (uiState.walkingGuidance) {
                        WalkingGuidance.NOT_TRACKING -> "Tap Start Tracking to begin"
                        WalkingGuidance.NEED_MORE_POINTS -> "Keep walking to collect more readings..."
                        WalkingGuidance.CHANGE_DIRECTION -> "Walk in a different direction for better accuracy"
                        WalkingGuidance.GOOD_ESTIMATE -> "Good coverage — estimate improving"
                    }
                    val guidanceColor = when (uiState.walkingGuidance) {
                        WalkingGuidance.CHANGE_DIRECTION -> MaterialTheme.colorScheme.error
                        WalkingGuidance.GOOD_ESTIMATE -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(guidanceText, style = MaterialTheme.typography.bodySmall, color = guidanceColor)

                    uiState.estimatedAccuracyMeters?.let { acc ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Estimated accuracy: ${String.format("%.1f", acc)} m",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Map
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LeafletMapView(
                modifier = Modifier.fillMaxSize(),
                userLocation = uiState.userLocation?.let { it.latitude to it.longitude },
                devices = uiState.devices
            )
        }
    }
}
