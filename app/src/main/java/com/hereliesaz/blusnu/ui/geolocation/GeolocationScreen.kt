package com.hereliesaz.blusnu.ui.geolocation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.TargetDevice
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.TextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hereliesaz.blusnu.data.DeviceRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeolocationScreen(viewModel: GeolocationViewModel, deviceRepository: DeviceRepository) {
    val uiState by viewModel.uiState.collectAsState()
    val discoveredDevices by deviceRepository.discoveredDevices.collectAsState(initial = emptyList())
    var selectedDevice by remember { mutableStateOf<TargetDevice?>(null) }
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(text = "Geolocation")
        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                value = selectedDevice?.name ?: "Select a target device",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor()
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
                            viewModel.onDeviceSelected(device)
                        }
                    )
                }
            }
        }

        uiState.selectedDevice?.let {
            Text(text = "Selected Device: ${it.name}")
            Text(text = "Distance: ${uiState.distance} meters")
        }
    }
}
