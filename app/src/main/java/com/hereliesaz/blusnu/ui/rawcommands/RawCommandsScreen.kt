package com.hereliesaz.blusnu.ui.rawcommands

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Screen for executing raw shell commands (Root).
 *
 * Useful for debugging or executing specific BlueZ commands (`hcitool scan`, `btmgmt info`)
 * that aren't exposed via specific UI modules.
 * Requires Root.
 */
@Composable
fun RawCommandsScreen(viewModel: RawCommandsViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header.
        Text(
            text = "Execute raw BlueZ commands with root privileges.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text("Raw BlueZ Commands")

        Spacer(modifier = Modifier.height(16.dp))

        // Command Input.
        TextField(
            value = state.command,
            onValueChange = { viewModel.onCommandChanged(it) },
            label = { Text("Command") },
            placeholder = { Text("e.g., hcitool scan") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Execute Button.
        Button(onClick = { viewModel.onExecuteClicked() }) {
            Text("Execute")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Output Display.
        TextField(
            value = state.output,
            onValueChange = {},
            readOnly = true,
            label = { Text("Output") },
            modifier = Modifier.fillMaxSize()
        )
    }
}
