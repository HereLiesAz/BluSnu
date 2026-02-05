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
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzRoller
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.blusnu.ui.btlejuice.BtlejuiceHardwareState
import com.hereliesaz.blusnu.ui.btlejuice.BtlejuiceState
import com.hereliesaz.blusnu.ui.btlejuice.GattTraffic
import com.hereliesaz.blusnu.ui.components.ScreenTitle

/**
 * Screen for the Btlejuice MitM Proxy.
 *
 * Allows users to set up a BLE Proxy using dual radios.
 */
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
            // Uses deprecated ClickableText for link handling.
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
            AzButton(onClick = onConnect, text = "Connect BtleJack", shape = AzButtonShape.RECTANGLE)
        } else {
            AzButton(onClick = onConnectDual, text = "Connect USB Dongle", shape = AzButtonShape.RECTANGLE)
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
    val deviceOptions = devices.map { it.name ?: it.macAddress }
    val selectedOption = selectedDevice?.let { it.name ?: it.macAddress }
                         ?: if (devices.isEmpty()) "No devices found" else "Select a target device"

    AzRoller(
        options = deviceOptions,
        selectedOption = selectedOption,
        onOptionSelected = { selectedName ->
             val device = devices.find { (it.name ?: it.macAddress) == selectedName }
             if (device != null) {
                 onDeviceSelected(device)
             }
        },
        hint = "Select a target device",
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ProxyControls(
    btlejuiceState: BtlejuiceState,
    onStartProxy: () -> Unit,
    onStopProxy: () -> Unit,
    isTargetSelected: Boolean
) {
    Row {
        AzButton(
            onClick = onStartProxy,
            enabled = isTargetSelected && !btlejuiceState.isProxying,
            text = "Start Proxy",
            shape = AzButtonShape.RECTANGLE
        )
        Spacer(modifier = Modifier.width(8.dp))
        AzButton(
            onClick = onStopProxy,
            enabled = btlejuiceState.isProxying,
            text = "Stop Proxy",
            shape = AzButtonShape.RECTANGLE
        )
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
