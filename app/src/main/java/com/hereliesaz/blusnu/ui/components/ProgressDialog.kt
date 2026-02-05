package com.hereliesaz.blusnu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * A modal dialog showing a loading spinner and a log of current actions.
 *
 * Used during long-running operations like "Connecting..." or "Executing Chain".
 *
 * @param onDismissRequest Callback when dialog is dismissed (e.g. back press).
 * @param logMessages A list of strings to display as a progress log.
 */
@Composable
fun ProgressDialog(
    onDismissRequest: () -> Unit,
    logMessages: List<String>
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.End, // Right-align content? Unusual for progress, maybe specific design choice.
                verticalArrangement = Arrangement.Center
            ) {
                // Spinning loader.
                CircularProgressIndicator()

                Spacer(modifier = Modifier.height(16.dp))

                // Display each log message.
                logMessages.forEach {
                    Text(it)
                }
            }
        }
    }
}
