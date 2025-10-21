package com.hereliesaz.blusnu.ui.keystrokeinjection

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
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme

@Composable
fun KeystrokeInjectionScreen(
    state: KeystrokeInjectionState = KeystrokeInjectionState(),
    onAttemptAttack: () -> Unit = {},
    onSendKeystrokes: (String) -> Unit = {}
) {
    var textToSend by remember { mutableStateOf("") }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = screenHeight * 0.2f),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Button(
                onClick = onAttemptAttack,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isPared
        ) {
            Text(if (state.isPared) "Paired Successfully" else "Attempt Silent Pairing")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Keystroke Input
        if (state.isPared) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textToSend,
                    onValueChange = { textToSend = it },
                    label = { Text("Enter Keystrokes") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    if (textToSend.isNotBlank()) {
                        onSendKeystrokes(textToSend)
                        textToSend = ""
                    }
                }) {
                    Text("Send")
                }
            }
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
}

@Preview(showBackground = true)
@Composable
fun KeystrokeInjectionScreenPreview() {
    BluSnuTheme {
        KeystrokeInjectionScreen(
            state = KeystrokeInjectionState(
                isPared = true,
                logMessages = listOf("Attempting to pair...", "Paired successfully.", "Sent: 'Hello World'")
            )
        )
    }
}
