package com.hereliesaz.blusnu.ui.geolocation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzRoller
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.ui.components.LeafletMapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindScreen(viewModel: FindViewModel, deviceRepository: DeviceRepository) {
    val uiState by viewModel.uiState.collectAsState()

    androidx.compose.runtime.DisposableEffect(Unit) {
        viewModel.startTracking()
        onDispose {
            viewModel.stopTracking()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Section: Arrow, Distance, Selector (Weight 0.5)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = "Track device locations on a map.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val deviceOptions = uiState.devices.map { it.name ?: it.macAddress }
            val selectedOption = uiState.selectedDevice?.let { it.name ?: it.macAddress }
                                 ?: if (uiState.devices.isEmpty()) "No devices found" else "Select a target device"

            AzRoller(
                options = deviceOptions,
                selectedOption = selectedOption,
                onOptionSelected = { selectedName ->
                    val device = uiState.devices.find { (it.name ?: it.macAddress) == selectedName }
                    if (device != null) {
                        viewModel.selectDevice(device)
                    }
                },
                hint = "Select a target device",
                enabled = uiState.devices.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.selectedDevice != null) {
                // Show RSSI Distance ALWAYS if available
                val dist = uiState.rssiDistance
                if (dist != null) {
                    val distanceText = if (uiState.isMetric) {
                        "%.2f m".format(dist)
                    } else {
                        val feet = dist * 3.28084
                        val ft = feet.toInt()
                        val inches = ((feet - ft) * 12).toInt()
                        "$ft ft $inches in"
                    }
                    Text(
                        text = "Estimated Range: $distanceText",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Text(
                        text = "Waiting for signal...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Instruction
                Text(
                    text = "Wave device around to locate signal source.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Hardware features
                if (!uiState.isUsbConnected) {
                    Button(onClick = { viewModel.connectUsbDongle() }) {
                        Text("Connect USB Dongle (Dual RSSI)")
                    }
                } else {
                    Text("USB Dongle Connected (Dual RSSI Active)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tandem Mode Toggle
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Tandem Mode: ", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Switch(
                        checked = uiState.isTandemModeEnabled,
                        onCheckedChange = { viewModel.toggleTandemMode() }
                    )
                }
                if (uiState.isTandemModeEnabled) {
                    Text("Broadcasting service via NSD...", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Arrow Logic
                // Show arrow based on Estimated Bearing from Signal Analysis (Fuzzy/Gradient)
                if (uiState.estimatedBearing != null) {
                    val rotation = uiState.estimatedBearing!! - uiState.currentAzimuth

                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Direction to signal",
                        modifier = Modifier
                            .rotate(rotation)
                            .size(150.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Signal Direction",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    // No valid bearing yet
                    Text("Acquiring Direction...", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(150.dp)) // Placeholder
                }

                // GPS status only for map/distance context, not arrow
                if (uiState.distanceToTarget != null) {
                     Text("GPS Range Available", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Map Section (Weight 0.4)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
        ) {
            LeafletMapView(
                modifier = Modifier.fillMaxSize(),
                userLocation = uiState.userLocation?.let { it.latitude to it.longitude },
                devices = uiState.devices
            )
        }

        // Bottom Clear Zone (Weight 0.1)
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.1f)
        )
    }
}
