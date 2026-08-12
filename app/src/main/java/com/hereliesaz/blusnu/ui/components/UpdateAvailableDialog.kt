package com.hereliesaz.blusnu.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.blusnu.BuildConfig

/**
 * Shown on every app open when a newer release is available on GitHub.
 * The user can dismiss it for the session; it will appear again next launch.
 */
@Composable
fun UpdateAvailableDialog(latestVersion: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Available") },
        text = {
            Text(
                "Version $latestVersion is available on GitHub.\n\n" +
                "You are running ${BuildConfig.VERSION_NAME}.\n\n" +
                "Update to get the latest attack modules, CVE coverage, and bug fixes."
            )
        },
        confirmButton = {
            AzButton(
                onClick = onDismiss,
                text = "Later",
                shape = AzButtonShape.RECTANGLE
            )
        }
    )
}
