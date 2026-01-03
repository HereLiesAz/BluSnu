package com.hereliesaz.blusnu.ui.geolocation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.ui.draw.rotate
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.ui.components.LeafletMapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindScreen(viewModel: FindViewModel, deviceRepository: DeviceRepository) {
    val uiState by viewModel.uiState.collectAsState()
    var expanded by remember { mutableStateOf(false) }

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

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = uiState.selectedDevice?.name ?: "Select a target device",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    if (uiState.devices.isEmpty()) {
                         DropdownMenuItem(
                            text = { Text("No devices found", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { expanded = false }
                        )
                    } else {
                        uiState.devices.forEach { device ->
                            val isAvailable = System.currentTimeMillis() - device.lastSeen < 60000
                            val textColor = if (isAvailable) MaterialTheme.colorScheme.primary else Color.Unspecified
                            DropdownMenuItem(
                                text = { Text(device.name ?: device.macAddress, color = textColor) },
                                onClick = {
                                    viewModel.selectDevice(device)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Display placeholder arrow if device selected but location unknown, to show "something"
            if (uiState.selectedDevice != null) {
                if (uiState.distanceToTarget != null && uiState.bearingToTarget != null) {
                    val bearing = uiState.bearingToTarget!!
                    val currentAzimuth = uiState.currentAzimuth
                    val rotation = bearing - currentAzimuth

                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Direction to device",
                        modifier = Modifier
                            .rotate(rotation)
                            .size(100.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val distanceText = if (uiState.isMetric) {
                        "%.2f m".format(uiState.distanceToTarget)
                    } else {
                        val feet = uiState.distanceToTarget!! * 3.28084
                        val ft = feet.toInt()
                        val inches = ((feet - ft) * 12).toInt()
                        "$ft ft $inches in"
                    }
                    Text(
                        text = "Distance: $distanceText",
                        style = MaterialTheme.typography.headlineMedium
                    )
                } else {
                    // Show disabled arrow as placeholder
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Direction unknown",
                        modifier = Modifier
                            .size(100.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (uiState.userLocation == null) {
                         Text("Waiting for GPS...", color = MaterialTheme.colorScheme.error)
                    } else if (uiState.selectedDevice?.latitude == null) {
                        Text("Target location unknown (Needs more scans)", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Calculating...", color = MaterialTheme.colorScheme.secondary)
                    }
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
