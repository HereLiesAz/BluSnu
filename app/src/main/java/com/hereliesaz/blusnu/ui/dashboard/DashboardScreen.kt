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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.hereliesaz.blusnu.data.TargetDevice

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    bleDeviceCount: Int = 0,
    classicDeviceCount: Int = 0,
    devicesWithLocation: List<TargetDevice> = emptyList(),
    savedSessions: List<com.hereliesaz.blusnu.data.SavedSession> = emptyList(),
    attackChainTemplates: List<com.hereliesaz.blusnu.data.AttackChainTemplate> = emptyList(),
    onStartScanClicked: () -> Unit = {}
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = screenHeight * 0.2f),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Device Counters
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    DeviceCounter("BLE Devices", bleDeviceCount)
                    DeviceCounter("Classic Devices", classicDeviceCount)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onStartScanClicked,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start Scan")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sections
            DashboardSection(title = "Device Heatmap") {
                Heatmap(devices = devicesWithLocation)
            }
            DashboardSection(title = "Saved Sessions") {
                LazyRow {
                    items(savedSessions) { session ->
                        DashboardCard(title = session.name, description = session.date)
                    }
                }
            }
            DashboardSection(title = "Attack Chain Templates") {
                LazyRow {
                    items(attackChainTemplates) { template ->
                        DashboardCard(title = template.name, description = template.description)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardCard(title: String, description: String) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .height(100.dp)
            .padding(end = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun DeviceCounter(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun DashboardSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
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

@Composable
fun Heatmap(devices: List<TargetDevice>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        devices.forEach { device ->
            if (device.latitude != null && device.longitude != null) {
                drawCircle(
                    color = Color.Red,
                    center = Offset(
                        x = size.width * (device.latitude.toFloat() / 180f),
                        y = size.height * (device.longitude.toFloat() / 180f)
                    ),
                    radius = 10f
                )
            }
        }
    }
}
