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
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.blusnu.data.TargetDevice

/**
 * The main Dashboard screen providing an overview of the app's status.
 *
 * Displays:
 * 1. Counts of discovered BLE and Classic devices.
 * 2. A "Start Scan" quick action button.
 * 3. A Heatmap visualization of device locations.
 * 4. Lists of saved sessions and templates.
 */
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
    // Get screen height to calculate spacing.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.End
        ) {
            // Header Text.
            Text(
                text = "Overview of detected devices and recent activity.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Device Counters Card.
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

            // Quick Actions Row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                 // Uses AzButton from the library for consistent styling.
                 AzButton(
                    onClick = onStartScanClicked,
                    text = "Start Scan",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dashboard Sections.

            // Heatmap Section.
            DashboardSection(title = "Device Heatmap") {
                Heatmap(devices = devicesWithLocation)
            }

            // Saved Sessions Section.
            DashboardSection(title = "Saved Sessions") {
                LazyRow {
                    items(savedSessions) { session ->
                        DashboardCard(title = session.name, description = session.date)
                    }
                }
            }

            // Templates Section.
            DashboardSection(title = "Attack Chain Templates") {
                LazyRow {
                    items(attackChainTemplates) { template ->
                        DashboardCard(title = template.name, description = template.description)
                    }
                }
            }

            // Spacer to push content up and reserve bottom area.
            Spacer(modifier = Modifier.weight(1f))
            // Bottom spacing for Navigation Rail alignment compatibility.
            Spacer(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.1f))
        }
    }
}

/**
 * Reusable Card component for Dashboard items.
 */
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

/**
 * Simple counter widget.
 */
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

/**
 * Section wrapper with a title.
 */
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
fun DashboardScreenPreviewInternal() {
    BluSnuTheme {
        DashboardScreen(bleDeviceCount = 5, classicDeviceCount = 2)
    }
}

/**
 * Draws a simple heatmap of devices based on their lat/long.
 * Normalizes coordinates to fit the canvas.
 */
@Composable
fun Heatmap(devices: List<TargetDevice>) {
    val devicesWithLoc = devices.filter { it.latitude != null && it.longitude != null }
    if (devicesWithLoc.isEmpty()) return

    // Find bounds.
    val minLat = devicesWithLoc.minOf { it.latitude!! }
    val maxLat = devicesWithLoc.maxOf { it.latitude!! }
    val minLon = devicesWithLoc.minOf { it.longitude!! }
    val maxLon = devicesWithLoc.maxOf { it.longitude!! }

    // Avoid divide by zero if all points are the same.
    val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
    val lonRange = (maxLon - minLon).coerceAtLeast(0.0001)

    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        devicesWithLoc.forEach { device ->
            // Normalize coordinates to 0..1.
            // Invert Y because screen Y goes down, Latitude goes up (North).
            val normalizedLat = (device.latitude!! - minLat) / latRange
            val normalizedLon = (device.longitude!! - minLon) / lonRange

            drawCircle(
                color = Color.Red,
                center = Offset(
                    x = (normalizedLon * size.width).toFloat(),
                    y = ((1 - normalizedLat) * size.height).toFloat()
                ),
                radius = 10f
            )
        }
    }
}
