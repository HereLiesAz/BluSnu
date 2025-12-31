package com.hereliesaz.blusnu.ui.bluetoothlog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BluetoothLogScreen(viewModel: BluetoothLogViewModel) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "View and filter real-time Bluetooth logs.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text("Bluetooth Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = state.filter,
                onValueChange = { viewModel.onFilterChanged(it) },
                label = { Text("Filter") },
                modifier = Modifier.weight(1f)
            )
            Checkbox(
                checked = state.isFiltered,
                onCheckedChange = { viewModel.onFilterEnabled(it) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(state.logs) { log ->
                    Text(log, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(onClick = { viewModel.onSaveToNotes() }, modifier = Modifier.weight(1f)) {
                Text("Save to Notes")
            }
            Button(onClick = { viewModel.onSaveToFile() }, modifier = Modifier.weight(1f)) {
                Text("Save to File")
            }
        }
    }
}
