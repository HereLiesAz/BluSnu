package com.hereliesaz.blusnu.ui.gattfuzzing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GattFuzzingScreen(viewModel: GattFuzzingViewModel) {
    val macAddress by viewModel.macAddress.collectAsState()
    val status by viewModel.status.collectAsState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = screenHeight * 0.2f),
        contentAlignment = Alignment.TopEnd
    ) {
        Column {
            TextField(
                value = macAddress,
                onValueChange = { viewModel.onMacAddressChanged(it) },
                label = { Text("Target MAC Address") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            // MAC address validation (simple regex for 6 pairs of hex digits)
            val isMacValid = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$").matches(macAddress)

            Button(
                onClick = { viewModel.startAttack() },
                enabled = isMacValid
            ) {
                Text("Start Fuzzing")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Status: $status")
        }
    }
}
