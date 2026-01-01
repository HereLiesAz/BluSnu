package com.hereliesaz.blusnu.ui.btlejuice

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.TargetDevice
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.hereliesaz.blusnu.ui.btlejuice.BtlejuiceHardwareState
import com.hereliesaz.blusnu.ui.btlejuice.BtlejuiceState
import com.hereliesaz.blusnu.ui.btlejuice.GattTraffic
import com.hereliesaz.blusnu.ui.components.ScreenTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BtlejuiceScreen(
    hardwareState: BtlejuiceHardwareState,
    btlejuiceState: BtlejuiceState,
    logs: List<String>,
    discoveredDevices: List<TargetDevice>,
    onConnectHardware: () -> Unit,
    onConnectDual: () -> Unit,
    onStartProxy: (TargetDevice?) -> Unit,
    onStopProxy: () -> Unit,
    gattTraffic: GattTraffic
) {
    var selectedDevice by remember { mutableStateOf<TargetDevice?>(null) }
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val descriptionText = buildAnnotatedString {
                append("Btlejuice is a framework for performing Man-in-the-Middle (MitM) attacks on Bluetooth Low Energy (BLE) devices.\n")
                append("It intercepts GATT packets by acting as a proxy between the target device and the mobile app.\n\n")
                append("Requires two Bluetooth 4.0+ adapters.\n")
                pushStringAnnotation(tag = "URL", annotation = "https://www.adafruit.com/product/1327")
                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                    append("Buy Bluetooth Dongle")
                }
                pop()
            }
            ClickableText(
                text = descriptionText,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.padding(bottom = 16.dp),
                onClick = { offset ->
                    descriptionText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                        }
                }
            )

            HardwareStatus(hardwareState, onConnectHardware, onConnectDual)
            Spacer(modifier = Modifier.height(16.dp))

        if (hardwareState.isConnected) {
            TargetSelection(discoveredDevices, selectedDevice) { selectedDevice = it }
            Spacer(modifier = Modifier.height(16.dp))

            ProxyControls(
                btlejuiceState,
                onStartProxy = { onStartProxy(selectedDevice) },
                onStopProxy = onStopProxy,
                isTargetSelected = selectedDevice != null
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        TrafficLog(gattTraffic.entries)
    }
    }
}


@Composable
private fun HardwareStatus(
    hardwareState: BtlejuiceHardwareState,
    onConnect: () -> Unit,
    onConnectDual: () -> Unit
) {
    Column {
        Text("Hardware Status: ${if(hardwareState.isConnected) "Connected" else "Disconnected"}", style = MaterialTheme.typography.titleMedium)
        if (!hardwareState.isConnected) {
            Button(onClick = onConnect) { Text("Connect BtleJack") }
        } else {
            Button(onClick = onConnectDual) { Text("Connect USB Dongle") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSelection(
    devices: List<TargetDevice>,
    selectedDevice: TargetDevice?,
    onDeviceSelected: (TargetDevice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
            devices.forEach { device ->
                val isAvailable = System.currentTimeMillis() - device.lastSeen < 60000
                val textColor = if (isAvailable) MaterialTheme.colorScheme.primary else Color.Unspecified
                DropdownMenuItem(
                    text = { Text(device.name ?: "Unknown", color = textColor) },
                    onClick = {
                        onDeviceSelected(device)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ProxyControls(
    btlejuiceState: BtlejuiceState,
    onStartProxy: () -> Unit,
    onStopProxy: () -> Unit,
    isTargetSelected: Boolean
) {
    Row {
        Button(
            onClick = onStartProxy,
            enabled = isTargetSelected && !btlejuiceState.isProxying
        ) {
            Text("Start Proxy")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onStopProxy,
            enabled = btlejuiceState.isProxying
        ) {
            Text("Stop Proxy")
        }
    }
}

@Composable
private fun TrafficLog(logs: List<String>) {
    Text("Intercepted Traffic", style = MaterialTheme.typography.titleMedium)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(logs) { log ->
            Text(log)
        }
    }
}
