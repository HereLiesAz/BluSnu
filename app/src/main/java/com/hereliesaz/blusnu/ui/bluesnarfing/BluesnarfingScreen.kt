package com.hereliesaz.blusnu.ui.bluesnarfing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun BluesnarfingScreen(viewModel: BluesnarfingViewModel = viewModel()) {
    val macAddress by viewModel.macAddress.collectAsState()
    val status by viewModel.status.collectAsState()
    val result by viewModel.result.collectAsState()

    Column {
        TextField(
            value = macAddress,
            onValueChange = { viewModel.onMacAddressChanged(it) },
            label = { Text("Target MAC Address") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { viewModel.startAttack() }) {
            Text("Start Attack")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Status: $status")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Result: $result")
    }
}
