package com.hereliesaz.blusnu.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.ActiveTask
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    bleDeviceCount: Int = 0,
    classicDeviceCount: Int = 0,
    devicesWithLocation: List<TargetDevice> = emptyList(),
    savedSessions: List<com.hereliesaz.blusnu.data.SavedSession> = emptyList(),
    attackChainTemplates: List<com.hereliesaz.blusnu.data.AttackChainTemplate> = emptyList(),
    activeTasks: List<ActiveTask> = emptyList(),
    onStartScanClicked: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Dashboard", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        // Device Counters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DeviceCountCard(
                label = "BLE Devices",
                count = bleDeviceCount,
                modifier = Modifier.weight(1f)
            )
            DeviceCountCard(
                label = "Classic Devices",
                count = classicDeviceCount,
                modifier = Modifier.weight(1f)
            )
            DeviceCountCard(
                label = "Total",
                count = bleDeviceCount + classicDeviceCount,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scan button
        Button(
            onClick = onStartScanClicked,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.BluetoothSearching, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Scan")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Active Tasks
        DashboardSection(title = "Active Tasks") {
            if (activeTasks.isEmpty()) {
                Text(
                    "No active tasks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(activeTasks) { task ->
                        DashboardCard(title = task.name, description = task.description)
                    }
                }
            }
        }

        // Geolocated devices
        if (devicesWithLocation.isNotEmpty()) {
            DashboardSection(title = "Devices with Location (${devicesWithLocation.size})") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(devicesWithLocation) { device ->
                        DashboardCard(
                            title = device.name ?: "Unknown",
                            description = device.macAddress
                        )
                    }
                }
            }
        }

        // Saved Sessions
        DashboardSection(title = "Saved Sessions") {
            if (savedSessions.isEmpty()) {
                Text(
                    "No saved sessions yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(savedSessions) { session ->
                        DashboardCard(title = session.name, description = session.date)
                    }
                }
            }
        }

        // Attack Chain Templates
        DashboardSection(title = "Attack Chain Templates") {
            if (attackChainTemplates.isEmpty()) {
                Text(
                    "No templates saved yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(attackChainTemplates) { template ->
                        DashboardCard(title = template.name, description = template.description)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCountCard(label: String, count: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun DashboardCard(title: String, description: String) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .height(80.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun DashboardSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    BluSnuTheme {
        DashboardScreen(bleDeviceCount = 5, classicDeviceCount = 2)
    }
}
