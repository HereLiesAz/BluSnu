package com.hereliesaz.blusnu.ui.bluetoothlog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothLogScreen(viewModel: BluetoothLogViewModel) {
    val state by viewModel.state.collectAsState()
    var deviceExpanded by remember { mutableStateOf(false) }

    // SAF file-creation launcher -- replaces deprecated Environment.getExternalStoragePublicDirectory.
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.writeLogsToUri(uri)
        }
    }

    // Trigger the SAF picker when the ViewModel requests it.
    LaunchedEffect(state.requestSaveFile) {
        if (state.requestSaveFile) {
            createDocumentLauncher.launch("bluetooth_log.txt")
            viewModel.onSaveFileRequestHandled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Bluetooth Log", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        // Device picker dropdown so the user can select which device's log to view.
        ExposedDropdownMenuBox(
            expanded = deviceExpanded,
            onExpandedChange = { deviceExpanded = it }
        ) {
            OutlinedTextField(
                value = state.selectedDevice?.let { "${it.name ?: it.macAddress}" }
                    ?: "All devices",
                onValueChange = {},
                readOnly = true,
                label = { Text("Filter by Device") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = deviceExpanded,
                onDismissRequest = { deviceExpanded = false }
            ) {
                state.devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.name ?: device.macAddress) },
                        onClick = {
                            viewModel.onDeviceSelected(device)
                            deviceExpanded = false
                        }
                    )
                }
                if (state.devices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No devices found -- run a scan first") },
                        onClick = { deviceExpanded = false },
                        enabled = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.filter,
                onValueChange = { viewModel.onFilterChanged(it) },
                label = { Text("Filter") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Checkbox(
                checked = state.isFiltered,
                onCheckedChange = { viewModel.onFilterEnabled(it) }
            )
            Text("Filter", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (state.logs.isEmpty()) {
                Text(
                    "No log entries yet. Start a scan or attack to see Bluetooth activity.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.padding(12.dp)) {
                    items(state.logs) { log ->
                        Text(log, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row {
            Button(
                onClick = { viewModel.onSaveToNotes() },
                modifier = Modifier.weight(1f),
                enabled = state.selectedDevice != null && state.logs.isNotEmpty()
            ) {
                Text("Save to Notes")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.onSaveToFile() },
                modifier = Modifier.weight(1f),
                enabled = state.logs.isNotEmpty()
            ) {
                Text("Save to File")
            }
        }
    }
}
