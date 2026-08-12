package com.hereliesaz.blusnu.ui.geolocation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzRoller
import com.hereliesaz.blusnu.ui.components.LeafletMapView

/**
 * Combined Geolocation screen with "Track" and "Find" tabs.
 * - Track: multi-observation WLS trilateration (walk around to locate).
 * - Find: real-time RSSI-based directional finder with compass, USB dongle, and Tandem support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeolocationScreen(
    viewModel: GeolocationViewModel,
    findViewModel: FindViewModel? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab selector
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Track") }
            SegmentedButton(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                enabled = findViewModel != null
            ) { Text("Find") }
        }

        when (selectedTab) {
            0 -> TrackTab(viewModel = viewModel)
            1 -> if (findViewModel != null) FindTab(viewModel = findViewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackTab(viewModel: GeolocationViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Geolocation", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindTab(viewModel: FindViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Manage Lifecycle: Start tracking sensors when screen enters, Stop when it leaves
    DisposableEffect(Unit) {
        viewModel.startTracking()
        onDispose {
            viewModel.stopTracking()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Directional Guidance (Top 50%)
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

            // Prepare list for the AzRoller dropdown
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
                enabled = uiState.devices.isNotEmpty()
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.selectedDevice != null) {
                // Distance Display
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

                Text(
                    text = "Wave device around to locate signal source.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Hardware Connection Controls
                if (!uiState.isUsbConnected) {
                    Button(onClick = { viewModel.connectUsbDongle() }) {
                        Text("Connect USB Dongle (Dual RSSI)")
                    }
                } else {
                    Text("USB Dongle Connected (Dual RSSI Active)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tandem Mode Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Tandem Mode: ", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = uiState.isTandemModeEnabled,
                        onCheckedChange = { viewModel.toggleTandemMode() }
                    )
                }
                if (uiState.isTandemModeEnabled) {
                    Text("Broadcasting service via NSD...", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Directional Arrow
                if (uiState.estimatedBearing != null) {
                    val rotation = uiState.estimatedBearing!! - uiState.currentAzimuth

                    Icon(
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
                    Text("Acquiring Direction...", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(150.dp))
                }

                if (uiState.distanceToTarget != null) {
                     Text("GPS Range Available", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Map Visualization (Bottom 40%)
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

        // Navigation Reservation (Bottom 10%)
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.1f)
        )
    }
}
