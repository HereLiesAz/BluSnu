package com.hereliesaz.blusnu.ui.spoofing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzRoller
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme

/**
 * Screen for MAC Address Spoofing and Identity Manipulation.
 *
 * Allows the user to:
 * 1. Select a device from the discovered list to impersonate (clone).
 * 2. Manually enter a MAC address to spoof.
 * 3. View logs of the spoofing operation (stdout/stderr of root commands).
 * 4. Initiate advanced MitM attacks that rely on identity cloning.
 *
 * @param state The UI state containing logs, devices, and error flags.
 * @param onMacAddressChanged Callback when the user types in the text box.
 * @param onApplyClicked Callback when the "Apply" button is pressed.
 * @param onDeviceSelected Callback when a device is picked from the roller.
 * @param onStartMitmAttack Callback to trigger the multi-stage MitM sequence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoofingScreen(
    state: SpoofingState = SpoofingState(),
    onMacAddressChanged: (String) -> Unit = {},
    onApplyClicked: () -> Unit = {},
    onDeviceSelected: (com.hereliesaz.blusnu.data.TargetDevice) -> Unit = {},
    onStartMitmAttack: () -> Unit = {}
) {
    // Local state for the text field content to allow immediate UI feedback.
    var macAddress by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Explanation.
            Text(
                text = "Spoofing changes the local Bluetooth adapter's MAC address to impersonate another device or evade blocklists.\n\n" +
                       "Requires a rooted device and a Bluetooth chipset/driver that supports address modification. It may not work on all hardware.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Prepare device list for AzRoller.
            // Map devices to a display string "Name" or "MAC".
            val deviceOptions = state.devices.map { it.name ?: it.macAddress }

            // Determine the currently selected text.
            val selectedDeviceName = state.selectedDevice?.let { it.name ?: it.macAddress }
                                     ?: if (state.devices.isEmpty()) "No devices" else "Select a device to spoof"

            // Device Selector Roller.
            AzRoller(
                options = deviceOptions,
                selectedOption = selectedDeviceName,
                onOptionSelected = { selectedName ->
                     // Find the device object matching the selected name.
                     val device = state.devices.find { (it.name ?: it.macAddress) == selectedName }
                     if (device != null) {
                         onDeviceSelected(device)
                         // Auto-fill the text box with the selected MAC.
                         macAddress = device.macAddress
                         onMacAddressChanged(device.macAddress)
                     }
                },
                hint = "Select a device to spoof",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Manual Entry Box.
            AzTextBox(
                value = macAddress,
                onValueChange = {
                    macAddress = it
                    onMacAddressChanged(it)
                },
                hint = "New MAC Address",
                modifier = Modifier.fillMaxWidth(),
                isError = state.isError, // Show red border if format is invalid.
                onSubmit = { onApplyClicked() },
                submitButtonContent = { Text("Apply") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Operation Log.
            Text(
                "Status Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(16.dp),
                    reverseLayout = true // Scroll to bottom auto-behavior.
                ) {
                    items(state.logMessages.reversed()) { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // MitM Trigger.
            AzButton(
                onClick = { onStartMitmAttack() },
                text = "Start MITM Attack",
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // MitM Specific Logs (e.g. intercepted packets).
            Text(
                "MITM Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(16.dp),
                ) {
                    // Group logs by victim device.
                    items(state.mitmDevices) { device ->
                        Text(device.name ?: device.macAddress, fontWeight = FontWeight.Bold)
                        state.mitmLogs[device.macAddress]?.forEach { log ->
                            Text(log, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SpoofingScreenPreview() {
    BluSnuTheme {
        SpoofingScreen(
            state = SpoofingState(
                logMessages = listOf("Ready.", "Applying new MAC address...", "Success!"),
                isError = false
            )
        )
    }
}
