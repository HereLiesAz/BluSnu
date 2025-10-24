package com.hereliesaz.blusnu.ui.devicemanagement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.TargetDevice

@Composable
fun DeviceManagementScreen(viewModel: DeviceManagementViewModel) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.devices) { device ->
            DeviceRow(device = device, onNotesChanged = { notes ->
                viewModel.updateDeviceNotes(device, notes)
            })
        }
    }
}

@Composable
fun DeviceRow(device: TargetDevice, onNotesChanged: (String) -> Unit) {
    var notes by remember { mutableStateOf(device.notes) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = device.name ?: "Unknown Device", style = MaterialTheme.typography.titleMedium)
            Text(text = device.macAddress, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = {
                    notes = it
                    onNotesChanged(it)
                },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
