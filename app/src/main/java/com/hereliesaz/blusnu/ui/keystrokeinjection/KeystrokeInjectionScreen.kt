package com.hereliesaz.blusnu.ui.keystrokeinjection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzRoller
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeystrokeInjectionScreen(
    state: KeystrokeInjectionState,
    onAttemptAttack: () -> Unit,
    onSendKeystrokes: (String) -> Unit,
    onRunDuckyScript: (String) -> Unit,
    onDeviceSelected: (com.hereliesaz.blusnu.data.TargetDevice) -> Unit
) {
    var textToSend by remember { mutableStateOf("") }
    var duckyScript by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Keystroke Injection emulates a Bluetooth HID keyboard to send keystrokes to a target device.\n\n" +
                       "Requires the target to pair with this device. Once paired, arbitrary text can be injected, allowing for remote command execution.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val deviceOptions = state.devices.map { it.name ?: it.macAddress }
            val selectedDeviceName = state.selectedDevice?.let { it.name ?: it.macAddress }
                                     ?: if (state.devices.isEmpty()) "No devices" else "Select a target device"

            AzRoller(
                options = deviceOptions,
                selectedOption = selectedDeviceName,
                onOptionSelected = { selectedName ->
                     val device = state.devices.find { (it.name ?: it.macAddress) == selectedName }
                     if (device != null) {
                         onDeviceSelected(device)
                     }
                },
                hint = "Select a target device",
                modifier = Modifier.fillMaxWidth()
            )

            AzButton(
                onClick = onAttemptAttack,
                enabled = !state.isPared && state.selectedDevice != null,
                text = when {
                    state.isPared -> "Paired Successfully"
                    else -> "Attempt Silent Pairing"
                },
                shape = AzButtonShape.RECTANGLE
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.isPared) {
                AzTextBox(
                    value = textToSend,
                    onValueChange = { textToSend = it },
                    hint = "Enter text to send",
                    onSubmit = { onSendKeystrokes(textToSend) },
                    submitButtonContent = { Text("Send") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                AzTextBox(
                    value = duckyScript,
                    onValueChange = { duckyScript = it },
                    hint = "DuckyScript",
                    multiline = true,
                    onSubmit = { onRunDuckyScript(duckyScript) },
                    submitButtonContent = { Text("Run Script") },
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.logMessages) { log ->
                    Text(log)
                }
            }
        }
    }
}
