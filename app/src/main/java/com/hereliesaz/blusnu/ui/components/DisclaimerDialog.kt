package com.hereliesaz.blusnu.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp

@Composable
fun DisclaimerDialog(onConfirm: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Ethical Use Mandate") },
        text = {
            Column {
                Text("This tool is intended for use by security professionals for educational purposes and for security assessments on networks and devices for which they have received explicit, written authorization. Using this tool for unauthorized access or malicious activity is illegal. The developers assume no liability for its misuse.")
                Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
                Text("To help improve the app, please consider sharing anonymized data with the developer.")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(true) }) {
                Text("Agree and Accept")
            }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(false) }) {
                Text("Decline and Accept")
            }
        }
    )
}
