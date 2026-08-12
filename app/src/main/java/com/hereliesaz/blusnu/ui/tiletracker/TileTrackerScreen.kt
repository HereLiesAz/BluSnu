package com.hereliesaz.blusnu.ui.tiletracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.TileTagMode
import com.hereliesaz.blusnu.ui.components.ResultActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileTrackerScreen(viewModel: TileTrackerViewModel) {
    val selectedMode by viewModel.selectedMode.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var modeExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Tile Tracker",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Tile / Life360 tracking network research. Scans for or impersonates Tile " +
                "trackers using BLE Service Data UUID 0xFEED. Tile identifiers rotate " +
                "infrequently — the trackers are themselves trackable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = modeExpanded,
            onExpandedChange = { if (!isRunning) modeExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedMode.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Mode") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                enabled = !isRunning,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = modeExpanded,
                onDismissRequest = { modeExpanded = false }
            ) {
                TileTagMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.name) },
                        onClick = {
                            viewModel.selectMode(mode)
                            modeExpanded = false
                        }
                    )
                }
            }
        }

        Text(
            text = selectedMode.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isRunning) {
            OutlinedButton(
                onClick = { viewModel.stopAttack() },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("STOP")
            }
        } else {
            Button(
                onClick = { viewModel.startAttack() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when (selectedMode) {
                        TileTagMode.SCAN_TILE_DEVICES -> "START SCAN"
                        TileTagMode.BROADCAST_TILE -> "START BROADCAST"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.DarkGray.copy(alpha = 0.3f))
                .padding(8.dp)
        ) {
            LazyColumn(reverseLayout = true) {
                items(logs.reversed()) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
        }

        if (logs.isNotEmpty()) {
            ResultActions(
                resultText = logs.joinToString("\n"),
                label = "Tile Tracker Results"
            )
        }

        Spacer(modifier = Modifier.height(screenHeight * 0.1f))
    }
}
