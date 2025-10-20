package com.hereliesaz.blusnu.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun DisclaimerDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Non-skippable */ },
        title = { Text("Ethical Use Disclaimer") },
        text = {
            Text(
                "This application is intended for educational and authorized testing purposes only. " +
                        "Unauthorized scanning or attacking of Bluetooth devices is illegal. " +
                        "By using this app, you agree to use it responsibly and in compliance with all applicable laws."
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("I Understand and Agree")
            }
        }
    )
}