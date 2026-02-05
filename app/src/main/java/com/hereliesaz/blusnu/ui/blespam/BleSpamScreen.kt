package com.hereliesaz.blusnu.ui.blespam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.hereliesaz.blusnu.data.PayloadType

/**
 * Screen for the BLE Spam / Advertisement Flooding feature.
 *
 * Allows the user to select a target ecosystem (e.g., iOS, Android) and start broadcasting
 * spoofed pairing packets to trigger popup notifications on nearby devices.
 *
 * @param viewModel The ViewModel controlling the advertiser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleSpamScreen(viewModel: BleSpamViewModel) {
    val isAdvertising by viewModel.isAdvertising.collectAsState()
    val selectedPayloadType by viewModel.selectedPayloadType.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header.
            Text(
                text = "BLE Spam (Advertisement Flooding)",
                style = MaterialTheme.typography.headlineSmall
            )
            // Warning text.
            Text(
                text = "Floods nearby devices with high-priority pairing requests (e.g., 'AirPods detected'). \n\n" +
                       "Warning: This can render nearby devices unusable due to constant pop-ups.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )

            // Dropdown for Payload Type.
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = selectedPayloadType.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payload Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    // Disable changing payload while running.
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isAdvertising)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    PayloadType.values().forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.name) },
                            onClick = {
                                viewModel.selectPayload(type)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Active indicator.
            if (isAdvertising) {
                Text(
                    text = "ADVERTISING ACTIVE",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Red
                )
            }

            // Toggle Button.
            Button(
                onClick = { viewModel.toggleSpam() },
                colors = ButtonDefaults.buttonColors(
                    // Red button to stop, Blue/Primary to start.
                    containerColor = if (isAdvertising) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isAdvertising) "STOP ATTACK" else "START ATTACK")
            }

            // Bottom padding for Nav Rail.
            Spacer(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.1f))
        }
    }
}
