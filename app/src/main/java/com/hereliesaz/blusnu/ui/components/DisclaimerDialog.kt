package com.hereliesaz.blusnu.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape

/**
 * A dialog that enforces the Ethical Use Mandate of the application.
 *
 * This dialog appears on first launch (or until accepted) and requires the user to explicitely
 * agree that they will use the tool legally and ethically.
 *
 * @param onConfirm Callback function invoked with the user's choice (true = Agreed, false = Declined).
 */
@Composable
fun DisclaimerDialog(onConfirm: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = {}, // Prevent dismissing by clicking outside. Must choose an option.
        title = { Text("Ethical Use Mandate") },
        text = {
            Column {
                // The core legal disclaimer.
                Text("This tool is intended for use by security professionals for educational purposes and for security assessments on networks and devices for which you have received explicit, written authorization. Using this tool for unauthorized access or malicious activity is illegal. The developers assume no liability for its misuse.")
                Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
                // Optional data sharing prompt (placeholder for future analytics).
                Text("To help improve the app, please consider sharing anonymized data with the developer.")
            }
        },
        confirmButton = {
            // "Agree" button. Uses AzButton for consistent styling.
            AzButton(
                onClick = { onConfirm(true) },
                text = "Agree and Accept",
                shape = AzButtonShape.RECTANGLE
            )
        },
        dismissButton = {
            // "Decline" button. Declining keeps the dialog visible (the activity cannot proceed).
            AzButton(
                onClick = { onConfirm(false) },
                text = "Decline",
                shape = AzButtonShape.RECTANGLE
            )
        }
    )
}
