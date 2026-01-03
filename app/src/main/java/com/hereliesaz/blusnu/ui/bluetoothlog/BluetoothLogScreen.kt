package com.hereliesaz.blusnu.ui.bluetoothlog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzRoller
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.blusnu.data.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothLogScreen(viewModel: BluetoothLogViewModel) {
    val state by viewModel.state.collectAsState()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "View and filter real-time Bluetooth logs.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text("Bluetooth Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            AzTextBox(
                value = state.filter,
                onValueChange = { viewModel.onFilterChanged(it) },
                hint = "Filter",
                onSubmit = {},
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.padding(horizontal = 8.dp))

            val levelOptions = LogLevel.values().map { it.name }
            AzRoller(
                options = levelOptions,
                selectedOption = state.minLogLevel.name,
                onOptionSelected = { selectedName ->
                    val level = LogLevel.values().find { it.name == selectedName }
                    if (level != null) {
                        viewModel.onLogLevelChanged(level)
                    }
                },
                hint = "Verbosity",
                modifier = Modifier.weight(0.6f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                reverseLayout = true
            ) {
                items(state.logs.reversed()) { logEntry ->
                    val color = when (logEntry.level) {
                        LogLevel.ERROR -> MaterialTheme.colorScheme.error
                        LogLevel.WARN -> Color(0xFFFFA500) // Orange
                        LogLevel.DEBUG -> Color.Gray
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    val time = dateFormat.format(Date(logEntry.timestamp))
                    Text(
                        text = "[$time] [${logEntry.level}] ${logEntry.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = color
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Box(modifier = Modifier.weight(1f)) {
                AzButton(onClick = { viewModel.onSaveToNotes() }, text = "Save to Notes", shape = AzButtonShape.RECTANGLE)
            }
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Box(modifier = Modifier.weight(1f)) {
                AzButton(onClick = { viewModel.onSaveToFile() }, text = "Save to File", shape = AzButtonShape.RECTANGLE)
            }
        }
    }
}
