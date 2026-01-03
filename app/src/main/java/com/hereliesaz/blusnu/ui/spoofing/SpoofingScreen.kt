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
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoofingScreen(
    state: SpoofingState = SpoofingState(),
    onMacAddressChanged: (String) -> Unit = {},
    onApplyClicked: () -> Unit = {},
    onDeviceSelected: (com.hereliesaz.blusnu.data.TargetDevice) -> Unit = {},
    onStartMitmAttack: () -> Unit = {}
) {
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
            Text(
                text = "Spoofing changes the local Bluetooth adapter's MAC address to impersonate another device or evade blocklists.\n\n" +
                       "Requires a rooted device and a Bluetooth chipset/driver that supports address modification. It may not work on all hardware.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Device Selection
            val deviceOptions = state.devices.map { it.name ?: it.macAddress }
            val selectedDeviceName = state.selectedDevice?.let { it.name ?: it.macAddress }
                                     ?: if (state.devices.isEmpty()) "No devices" else "Select a device to spoof"

            AzRoller(
                options = deviceOptions,
                selectedOption = selectedDeviceName,
                onOptionSelected = { selectedName ->
                     val device = state.devices.find { (it.name ?: it.macAddress) == selectedName }
                     if (device != null) {
                         onDeviceSelected(device)
                         macAddress = device.macAddress
                         onMacAddressChanged(device.macAddress)
                     }
                },
                hint = "Select a device to spoof",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            AzTextBox(
                value = macAddress,
                onValueChange = {
                    macAddress = it
                    onMacAddressChanged(it)
                },
                hint = "New MAC Address",
                modifier = Modifier.fillMaxWidth(),
                // AzTextBox doesn't inherently show errors via standard Material API,
                // but we can manage visual feedback or validation in the ViewModel
                // or assume AzTextBox styling handles it.
                // Assuming "isError" visual feedback needs to be handled via custom colors or wrapper
                // if AzTextBox doesn't support it directly. For now, we omit explicit error param
                // as it's not in the AzTextBox signature I saw.
                onSubmit = { onApplyClicked() },
                submitButtonContent = { Text("Apply") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Apply Button - AzTextBox has a submit button, but let's keep the explicit action button if needed
            // or rely on the AzTextBox submit. The original had a separate button.
            // Let's use AzButton for the Apply action to be explicit, even if redundant with AzTextBox submit.
            // Actually, AzTextBox has 'submitButtonContent'. If we used that, we don't need this button.
            // But let's keep the button separate if the flow expects it, OR remove it if we integrated it.
            // Original UI: TextField -> Button("Apply").
            // New UI: AzTextBox(submitButtonContent={Text("Apply")}).
            // So we can remove the separate Apply button and rely on AzTextBox's submit.
            // However, the original button had 'enabled = !state.isError'. AzTextBox doesn't have an error state prop easily visible.
            // We can set `enabled` on AzTextBox if needed, but that disables typing.
            // Let's stick to the AzTextBox integrated submit button for "Apply".

            // Wait, the original had a separate button below the text field.
            // AzTextBox bundles them. So I will NOT add a separate "Apply" button.

            Spacer(modifier = Modifier.height(24.dp))

            // Status Log
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
                    reverseLayout = true
                ) {
                    items(state.logMessages.reversed()) { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AzButton(
                onClick = { onStartMitmAttack() },
                text = "Start MITM Attack",
                // modifier = Modifier.fillMaxWidth() // AzButton doesn't take modifier in the DSL example shown?
                // Wait, the example showed: AzButton(onClick=..., text=..., shape=..., enabled=..., isLoading=..., contentPadding=...)
                // It does NOT explicitly show a 'modifier' parameter in the snippet, but usually Compose functions do.
                // I will assume it does or I wrap it. The snippet said "Can be used independently... AzButton(...)".
                // If it doesn't take a modifier, I might need to wrap it in a Box/Row to fill width.
                // Let's assume standard Compose practices.
            )

            Spacer(modifier = Modifier.height(24.dp))

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
