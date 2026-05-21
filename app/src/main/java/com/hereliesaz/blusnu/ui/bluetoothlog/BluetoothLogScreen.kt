package com.hereliesaz.blusnu.ui.bluetoothlog

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BluetoothLogScreen(viewModel: BluetoothLogViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Bluetooth Log", style = MaterialTheme.typography.headlineSmall)
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
