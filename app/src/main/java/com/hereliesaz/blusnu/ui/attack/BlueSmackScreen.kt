package com.hereliesaz.blusnu.ui.attack

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BlueSmackScreen(
    onAttack: (String, Int, Int) -> Unit
) {
    var macAddress by remember { mutableStateOf("") }
    var packetSize by remember { mutableStateOf("1024") }
    var packetCount by remember { mutableState of("100") }

    Column(modifier = Modifier.padding(16.dp)) {
        TextField(
            value = macAddress,
            onValueChange = { macAddress = it },
            label = { Text("Target MAC Address") }
        )
        TextField(
            value = packetSize,
            onValueChange = { packetSize = it },
            label = { Text("Packet Size") }
        )
        TextField(
            value = packetCount,
            onValueChange = { packetCount = it },
            label = { Text("Packet Count") }
        )
        Button(onClick = { onAttack(macAddress, packetSize.toInt(), packetCount.toInt()) }) {
            Text("Attack")
        }
    }
}