package com.hereliesaz.blusnu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.hereliesaz.blusnu.data.TargetDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetManagementScreen(modifier: Modifier = Modifier, viewModel: TargetManagementViewModel, navController: NavController) {
    val state by viewModel.state.collectAsState()
    var textFilter by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<FilterType>(FilterType.Text) }

    Column(modifier = modifier.padding(16.dp)) {
        Row {
            OutlinedButton(
                onClick = { if (state.isScanning) viewModel.stopScan() else viewModel.startScan() },
                shape = RoundedCornerShape(0.dp)
            ) {
                Text(if (state.isScanning) "Stop Scan" else "Start Scan")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        var filterExpanded by remember { mutableStateOf(false) }
        var filterTypeExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(expanded = filterExpanded, onExpandedChange = { filterExpanded = !filterExpanded }) {
            TextField(
                value = textFilter,
                onValueChange = {
                    textFilter = it
                    if (it.isNotBlank()) {
                        viewModel.addFilter(filterType, it)
                    } else {
                        viewModel.removeFilter(filterType)
                    }
                },
                label = { Text("Filter by $filterType") },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded)
                }
            )
            ExposedDropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                when(filterType) {
                    FilterType.Text -> {
                        state.discoveredDevices.forEach { device ->
                            DropdownMenuItem(text = { Text(device.name ?: device.macAddress) }, onClick = {
                                textFilter = device.name ?: device.macAddress
                                viewModel.addFilter(FilterType.Text, textFilter)
                                filterExpanded = false
                            })
                        }
                    }
                    FilterType.Protocol -> {
                        DropdownMenuItem(text = { Text("Classic") }, onClick = {
                            viewModel.addFilter(FilterType.Protocol, FilterProtocol.CLASSIC)
                            filterExpanded = false
                        })
                        DropdownMenuItem(text = { Text("BLE") }, onClick = {
                            viewModel.addFilter(FilterType.Protocol, FilterProtocol.BLE)
                            filterExpanded = false
                        })
                        DropdownMenuItem(text = { Text("All") }, onClick = {
                            viewModel.removeFilter(FilterType.Protocol)
                            filterExpanded = false
                        })
                    }
                    FilterType.SignalStrength -> {
                        // Not implemented
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.End)) {
            OutlinedButton(onClick = { filterTypeExpanded = true }, shape = RoundedCornerShape(0.dp)) {
                Text("Filter by")
            }
            DropdownMenu(expanded = filterTypeExpanded, onDismissRequest = { filterTypeExpanded = false }) {
                DropdownMenuItem(text = { Text("Text") }, onClick = {
                    filterType = FilterType.Text
                    filterTypeExpanded = false
                })
                DropdownMenuItem(text = { Text("Protocol") }, onClick = {
                    filterType = FilterType.Protocol
                    filterTypeExpanded = false
                })
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!state.hasPermissions) {
            Text("Permissions not granted")
        } else if (!state.isBluetoothEnabled) {
            Text("Bluetooth is not enabled")
        } else if (state.isScanning && state.discoveredDevices.isEmpty()) {
            CircularProgressIndicator()
        } else if (!state.isScanning && state.discoveredDevices.isEmpty()) {
            Text("No devices found. Click 'Start Scan' to begin.")
        } else {
            LazyColumn {
                items(state.discoveredDevices, key = { it.macAddress }) { device ->
                    DeviceListItem(device = device, viewModel = viewModel, navController = navController)
                }
            }
        }
    }
}

@Composable
fun DeviceListItem(device: TargetDevice, viewModel: TargetManagementViewModel, navController: NavController) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.clickable { expanded = !expanded }) {
        Row {
            Text(text = device.name ?: "Unknown")
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = device.macAddress)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "${device.rssi} dBm")
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = device.protocol.name)
        }
        Row {
            OutlinedButton(onClick = { viewModel.discoverServices(device) }, shape = RoundedCornerShape(0.dp)) {
                Text("Services")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { viewModel.checkForVulnerabilities(device) }, shape = RoundedCornerShape(0.dp)) {
                Text("Check Vulns")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = {
                val targetDeviceJson = Gson().toJson(device)
                navController.navigate("btlejuice/$targetDeviceJson")
            }, shape = RoundedCornerShape(0.dp)) {
                Text("Juice")
            }
        }
        if (expanded) {
            if (device.services.isNotEmpty()) {
                Column {
                    device.services.forEach { service ->
                        Text(text = "Service: $service")
                    }
                }
            }
            if (device.vulnerabilities.isNotEmpty()) {
                Column {
                    device.vulnerabilities.forEach { vulnerability ->
                        Text(text = "Vulnerability: ${vulnerability.name} (${vulnerability.cve})")
                    }
                }
            }
        }
    }
}
