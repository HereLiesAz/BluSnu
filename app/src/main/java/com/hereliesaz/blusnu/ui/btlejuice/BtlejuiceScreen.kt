package com.hereliesaz.blusnu.ui.btlejuice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.BtlejuiceState
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.TargetDevice

@Composable
fun BtlejuiceScreen(viewModel: BtlejuiceViewModel, targetDevice: TargetDevice) {
    val btlejuiceState by viewModel.btlejuiceState.collectAsState()
    val hardwareState by viewModel.hardwareState.collectAsState()
    val deviceLogs by viewModel.deviceLogs.collectAsState(initial = emptyList())
    val gattTraffic by viewModel.gattTraffic.collectAsState(initial = emptyList())

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Target: ${targetDevice.name ?: targetDevice.macAddress}")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Hardware State: $hardwareState")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Btlejuice State: $btlejuiceState")
        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(
                onClick = { viewModel.connectHardware() },
                enabled = hardwareState == HardwareState.DISCONNECTED
            ) {
                Text("Connect Hardware")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.startProxy(targetDevice) },
                enabled = hardwareState == HardwareState.CONNECTED && btlejuiceState == BtlejuiceState.IDLE
            ) {
                Text("Start Proxy")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.stopProxy() },
                enabled = btlejuiceState != BtlejuiceState.IDLE
            ) {
                Text("Stop Proxy")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(deviceLogs) { log ->
                Text(log)
            }
        }

        HorizontalDivider()

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(gattTraffic) { traffic ->
                Text(traffic)
            }
        }
    }
}
