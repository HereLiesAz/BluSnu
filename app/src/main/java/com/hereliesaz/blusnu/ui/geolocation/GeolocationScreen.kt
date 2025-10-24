package com.hereliesaz.blusnu.ui.geolocation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.TargetDevice
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeolocationScreen(viewModel: GeolocationViewModel, deviceRepository: DeviceRepository) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedDevice by remember { mutableStateOf<TargetDevice?>(null) }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = selectedDevice?.name ?: "Select a target device",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    uiState.devices.forEach { device ->
                        val isAvailable = System.currentTimeMillis() - device.lastSeen < 60000
                        val textColor = if (isAvailable) MaterialTheme.colorScheme.primary else Color.Unspecified
                        DropdownMenuItem(
                            text = { Text(device.name ?: "Unknown", color = textColor) },
                            onClick = {
                                selectedDevice = device
                                expanded = false
                            }
                        )
                    }
                }
            }

            Button(onClick = {
                selectedDevice?.let {
                    val newRssi = it.rssi + (-5..5).random()
                    viewModel.onDeviceRssiUpdated(it, newRssi)
                }
            }, enabled = selectedDevice != null) {
                Text("Simulate RSSI Update")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        // Apply dark theme
                        overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()
                    uiState.userLocation?.let {
                        val userMarker = Marker(mapView)
                        userMarker.position = GeoPoint(it.latitude, it.longitude)
                        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        userMarker.title = "Your Location"
                        mapView.overlays.add(userMarker)
                        mapView.controller.setCenter(userMarker.position)
                    }
                    uiState.devices.forEach { device ->
                        if (device.latitude != null && device.longitude != null) {
                            val deviceMarker = Marker(mapView)
                            deviceMarker.position = GeoPoint(device.latitude, device.longitude)
                            deviceMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            deviceMarker.title = device.name ?: device.macAddress
                            mapView.overlays.add(deviceMarker)
                        }
                    }
                    mapView.invalidate()
                }
            )
        }
    }
}
