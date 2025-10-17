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
import android.widget.Toast
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun BlueSmackScreen(
    onAttack: (String, Int, Int) -> Unit
) {
    var macAddress by remember { mutableStateOf("") }
    var packetSize by remember { mutableStateOf("1024") }
    var packetCount by remember { mutableStateOf("100") }
    val context = LocalContext.current

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
        Button(onClick = {
            val size = packetSize.toIntOrNull()
            val count = packetCount.toIntOrNull()
            if (size != null && count != null) {
                onAttack(macAddress, size, count)
            } else {
                Toast.makeText(context, "Invalid input", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("Attack")
        }
    }
}