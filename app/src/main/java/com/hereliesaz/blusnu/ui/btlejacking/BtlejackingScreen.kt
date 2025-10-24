package com.hereliesaz.blusnu.ui.btlejacking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.TextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.BtlejackingState
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.TargetDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BtlejackingScreen(viewModel: BtlejackingViewModel, hasPermissions: Boolean) {
    val state by viewModel.state.collectAsState()
    var selectedTarget by remember { mutableStateOf<TargetDevice?>(null) }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = screenHeight * 0.2f),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hardware Status and Controls
            Text("Hardware Status: ${state.hardwareState}")
        when (state.hardwareState) {
            HardwareState.DISCONNECTED, HardwareState.CONNECTION_FAILED -> {
                Button(onClick = { viewModel.connectHardware() }) {
                    Text("Connect Hardware")
                }
            }
            HardwareState.CONNECTING -> {
                CircularProgressIndicator()
            }
            HardwareState.CONNECTED_BTLEJACK, HardwareState.CONNECTED_DUAL -> {
                Button(onClick = { viewModel.disconnectHardware() }) {
                    Text("Disconnect Hardware")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Attack Status and Controls
        if (state.hardwareState == HardwareState.CONNECTED_BTLEJACK || state.hardwareState == HardwareState.CONNECTED_DUAL) {
            Text("Attack Status: ${state.btlejackingState}")

            when (state.btlejackingState) {
                BtlejackingState.IDLE -> {
                    Button(
                        onClick = { selectedTarget?.let { viewModel.startAttack(it) } },
                        enabled = selectedTarget != null
                    ) {
                        Text("Start Attack on ${selectedTarget?.name ?: "..."}")
                    }
                }
                BtlejackingState.SNIFFING, BtlejackingState.JAMMING, BtlejackingState.HIJACKING -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Attack in progress...")
                        OutlinedButton(
                            onClick = { viewModel.stopAttack() },
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Stop Attack")
                        }
                    }
                }
                BtlejackingState.CONNECTED -> {
                    OutlinedButton(
                        onClick = { viewModel.stopAttack() },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Stop Attack")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Device List
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                value = selectedTarget?.name ?: "Select a target device",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                state.discoveredDevices.forEach { device ->
                    val isAvailable = System.currentTimeMillis() - device.lastSeen < 60000
                    val textColor = if (isAvailable) MaterialTheme.colorScheme.primary else Color.Unspecified
                    DropdownMenuItem(
                        text = { Text(device.name ?: "Unknown", color = textColor) },
                        onClick = {
                            selectedTarget = device
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Device Logs
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.deviceLogs) { log ->
                Text(log)
            }
        }
    }
    }
}

