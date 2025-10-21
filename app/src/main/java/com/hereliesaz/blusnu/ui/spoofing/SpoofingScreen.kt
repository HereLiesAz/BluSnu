package com.hereliesaz.blusnu.ui.spoofing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme

@Composable
fun SpoofingScreen(
    state: SpoofingState = SpoofingState(),
    onMacAddressChanged: (String) -> Unit = {},
    onApplyClicked: () -> Unit = {}
) {
    var macAddress by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Bluetooth MAC Address Spoofing",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Change the Bluetooth adapter's MAC address. Requires root access.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = macAddress,
            onValueChange = {
                macAddress = it
                onMacAddressChanged(it)
            },
            label = { Text("New MAC Address") },
            modifier = Modifier.fillMaxWidth(),
            isError = state.isError
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onApplyClicked,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isError
        ) {
            Text("Apply")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Status Log
        Text(
            "Status Log",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                reverseLayout = true
            ) {
                items(state.logMessages.reversed()) { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SpoofingScreenPreview() {
    BluSnuTheme {
        SpoofingScreen(
            state = SpoofingState(
                logMessages = listOf("Ready.", "Applying new MAC address...", "Success!"),
                isError = false
            )
        )
    }
}
