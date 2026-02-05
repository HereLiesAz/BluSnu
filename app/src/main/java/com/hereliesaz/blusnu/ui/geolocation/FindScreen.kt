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

/**
 * The main UI for the Geolocation (Find) feature.
 *
 * <p>
 * <b>Layout Strategy:</b>
 * The screen is vertically divided to accommodate both the directional guidance and the map context:
 * 1. <b>Top 50% (Weight 0.5):</b> Contains the Directional Arrow, Distance Meter, and Device Selector.
 * 2. <b>Bottom 40% (Weight 0.4):</b> Contains the [LeafletMapView] for GPS plotting.
 * 3. <b>Bottom 10% (Weight 0.1):</b> Reserved clear space for the [AzNavRail] footer alignment.
 * </p>
 *
 * @param viewModel The state holder for the screen logic.
 * @param deviceRepository The repository instance (passed for preview/mocking purposes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindScreen(viewModel: FindViewModel, deviceRepository: DeviceRepository) {
    // Collect UI State as a stable Compose State object
    val uiState by viewModel.uiState.collectAsState()

    // Manage Lifecycle: Start tracking sensors when screen enters, Stop when it leaves
    androidx.compose.runtime.DisposableEffect(Unit) {
        viewModel.startTracking()
        onDispose {
            viewModel.stopTracking()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // -----------------------------------------------------------------------------------------
        // SECTION 1: Directional Guidance (Top 50%)
        // -----------------------------------------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f) // Takes half the screen height
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

            // Custom Dropdown Component from AzNavRail library
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
                // --- Distance Display ---
                val dist = uiState.rssiDistance
                if (dist != null) {
                    val distanceText = if (uiState.isMetric) {
                        "%.2f m".format(dist)
                    } else {
                        // Custom Imperial formatting
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

                // User Instruction for fuzzy finding
                Text(
                    text = "Wave device around to locate signal source.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- Hardware Connection Controls ---
                if (!uiState.isUsbConnected) {
                    Button(onClick = { viewModel.connectUsbDongle() }) {
                        Text("Connect USB Dongle (Dual RSSI)")
                    }
                } else {
                    Text("USB Dongle Connected (Dual RSSI Active)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- Tandem Mode Controls ---
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

                // --- Directional Arrow ---
                // Logic: Rotate arrow to point towards the highest probability bucket relative to North
                // then subtract current phone azimuth to point relative to phone.
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
                    // Placeholder while gathering data
                    Text("Acquiring Direction...", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(150.dp))
                }

                if (uiState.distanceToTarget != null) {
                     Text("GPS Range Available", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // -----------------------------------------------------------------------------------------
        // SECTION 2: Map Visualization (Bottom 40%)
        // -----------------------------------------------------------------------------------------
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

        // -----------------------------------------------------------------------------------------
        // SECTION 3: Navigation Reservation (Bottom 10%)
        // -----------------------------------------------------------------------------------------
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.1f)
        )
    }
}
