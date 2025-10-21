package com.hereliesaz.blusnu.ui.geolocation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.TargetDevice
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.TextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import kotlin.random.Random
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.hereliesaz.blusnu.data.DeviceRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeolocationScreen(viewModel: GeolocationViewModel, deviceRepository: DeviceRepository) {
    val uiState by viewModel.uiState.collectAsState()
    val discoveredDevices by deviceRepository.discoveredDevices.collectAsState(initial = emptyList())
    var selectedDevice by remember { mutableStateOf<TargetDevice?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = screenHeight * 0.2f),
        contentAlignment = Alignment.TopEnd
    ) {
        Column {
            ExposedDropdownMenuBox(
                expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                value = selectedDevice?.name ?: "Select a target device",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                discoveredDevices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.name ?: "Unknown") },
                        onClick = {
                            selectedDevice = device
                            expanded = false
                        }
                    )
                }
            }
        }

        Button(onClick = {
            selectedDevice?.let {
                val newRssi = it.rssi + (-5..5).random()
                // Simulate user moving around
                val userLocation = LatLng(
                    1.35 + Random.nextDouble(-0.1, 0.1),
                    103.87 + Random.nextDouble(-0.1, 0.1)
                )
                viewModel.onDeviceRssiUpdated(it, newRssi, userLocation)
            }
        }, enabled = selectedDevice != null) {
            Text("Simulate RSSI Update")
        }

        val singapore = LatLng(1.35, 103.87)
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(singapore, 10f)
        }
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            Marker(
                state = MarkerState(position = singapore),
                title = "Your Location",
            )
            uiState.deviceLocations.forEach { (macAddress, location) ->
                Marker(
                    state = MarkerState(position = location),
                    title = macAddress
                )
            }
        }
    }
    }
}