package com.hereliesaz.blusnu.ui.gattrelay

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.RelayRole

/**
 * Screen for configuring and running a GATT Relay Attack.
 *
 * Users can choose which role this device plays (Node A near the peripheral,
 * or Node B near the central) and initiate the relay connection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GattRelayScreen(viewModel: GattRelayViewModel) {
    val selectedRole by viewModel.selectedRole.collectAsState()
    val targetAddress by viewModel.targetAddress.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val logs by viewModel.logs.collectAsState()
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
                text = "GATT Relay (PKES Attack)",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Relays GATT messages between a vehicle and a phone via two attacking devices (Node A and Node B).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            // Role Selection.
            Column {
                Text("Select Node Role:", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedRole == RelayRole.NODE_A_CAR_SIDE,
                        onClick = { viewModel.selectRole(RelayRole.NODE_A_CAR_SIDE) }
                    )
                    Text("Node A (Near Car)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedRole == RelayRole.NODE_B_PHONE_SIDE,
                        onClick = { viewModel.selectRole(RelayRole.NODE_B_PHONE_SIDE) }
                    )
                    Text("Node B (Near Phone)")
                }
            }

            // Target Input (Only needed for Node B to know which phone to connect to).
            if (selectedRole == RelayRole.NODE_B_PHONE_SIDE) {
                TextField(
                    value = targetAddress,
                    onValueChange = { viewModel.updateTargetAddress(it) },
                    label = { Text("Target Phone MAC (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Logs output.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.DarkGray.copy(alpha = 0.3f))
                    .padding(8.dp)
            ) {
                LazyColumn(reverseLayout = true) {
                    items(logs.reversed()) { log ->
                        Text(text = log, style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                }
            }

            if (isRunning) {
                CircularProgressIndicator()
            }

            // Action Buttons.
            if (!isRunning) {
                Button(
                    onClick = { viewModel.startRelay() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("START RELAY")
                }
            } else {
                Button(
                    onClick = { viewModel.stopRelay() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("STOP RELAY")
                }
            }

            Spacer(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.1f))
        }
    }
}
