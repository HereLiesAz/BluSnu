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
fun BluesnarfingScreen(
    onAttack: (String) -> Unit
) {
    var macAddress by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        TextField(
            value = macAddress,
            onValueChange = { macAddress = it },
            label = { Text("Target MAC Address") }
        )
        Button(onClick = { onAttack(macAddress) }) {
            Text("Attack")
        }
    }
}