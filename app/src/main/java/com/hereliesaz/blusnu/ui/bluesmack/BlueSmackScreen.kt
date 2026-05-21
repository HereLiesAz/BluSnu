package com.hereliesaz.blusnu.ui.bluesmack

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlueSmackScreen(viewModel: BlueSmackViewModel) {
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val status by viewModel.status.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("BlueSmack", style = MaterialTheme.typography.headlineSmall)
        Text(
            "L2CAP ping flood attack. Sends oversized l2ping packets to overwhelm the target device. Requires root.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Device picker
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedDevice?.let { it.name ?: it.macAddress } ?: "Select a target device",
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
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.name ?: device.macAddress) },
                        onClick = {
                            viewModel.onDeviceSelected(device)
                            expanded = false
                        }
                    )
                }
                if (devices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No devices found - run a scan first") },
                        onClick = { expanded = false },
                        enabled = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.startAttack() },
            enabled = selectedDevice != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Attack")
        }

        if (status.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
