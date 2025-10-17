package com.hereliesaz.blusnu.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun DisclaimerDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Non-skippable */ },
        title = { Text("Ethical Use Mandate") },
        text = {
            Text(
                "This tool is intended for use by security professionals for educational purposes and for security assessments on networks and devices for which you have received explicit, written authorization. Using this tool for unauthorized access or malicious activity is illegal. The developers assume no liability for its misuse."
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("I Understand and Agree")
            }
        }
    )
}