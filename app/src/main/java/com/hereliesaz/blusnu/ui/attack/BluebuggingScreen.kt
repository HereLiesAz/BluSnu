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
fun BluebuggingScreen(
    onAttack: (String, String) -> Unit
) {
    var macAddress by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        TextField(
            value = macAddress,
            onValueChange = { macAddress = it },
            label = { Text("Target MAC Address") }
        )
        TextField(
            value = command,
            onValueChange = { command = it },
            label = { Text("AT Command") }
        )
        Button(onClick = { onAttack(macAddress, command) }) {
            Text("Send Command")
        }
    }
}