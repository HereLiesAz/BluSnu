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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.ui.components.ScreenTitle

@Composable
fun KeystrokeInjectionScreen(
    state: KeystrokeInjectionState,
    onAttemptAttack: () -> Unit,
    onSendKeystrokes: (String) -> Unit
) {
    var textToSend by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenTitle(title = "Keystroke Injection")
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAttemptAttack,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isPared
            ) {
                val text = when {
                    state.isPared -> "Paired Successfully"
                    else -> "Attempt Silent Pairing"
                }
                Text(text)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.isPared) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = textToSend,
                        onValueChange = { textToSend = it },
                        label = { Text("Enter text to send") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onSendKeystrokes(textToSend) },
                        enabled = textToSend.isNotEmpty()
                    ) {
                        Text("Send")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.logMessages) { log ->
                    Text(log)
                }
            }
        }
    }
}
