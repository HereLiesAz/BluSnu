package com.hereliesaz.blusnu.ui.rawcommands

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RawCommandsScreen(viewModel: RawCommandsViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Execute raw BlueZ commands with root privileges.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text("Raw BlueZ Commands")

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = state.command,
            onValueChange = { viewModel.onCommandChanged(it) },
            label = { Text("Command") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { viewModel.onExecuteClicked() }) {
            Text("Execute")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = state.output,
            onValueChange = {},
            readOnly = true,
            label = { Text("Output") },
            modifier = Modifier.fillMaxSize()
        )
    }
}
