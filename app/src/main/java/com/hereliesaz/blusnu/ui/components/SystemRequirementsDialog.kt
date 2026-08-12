package com.hereliesaz.blusnu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape

/**
 * Blocking dialog that forces the user to enable necessary system settings.
 *
 * It checks for Bluetooth, Location, and Developer Options (for ADB/Root).
 * The dialog cannot be dismissed until requirements are met.
 *
 * @param isBluetoothEnabled State of Bluetooth adapter.
 * @param isLocationEnabled State of Location services.
 * @param isDeveloperOptionsEnabled State of Developer Mode.
 * @param onEnableBluetooth Callback to enable Bluetooth.
 * @param onEnableLocation Callback to open Location Settings.
 * @param onEnableDeveloperOptions Callback to open Dev Settings.
 * @param onDismiss Optional dismiss callback (usually ignored/no-op here).
 */
@Composable
fun SystemRequirementsDialog(
    isBluetoothEnabled: Boolean,
    isLocationEnabled: Boolean,
    isDeveloperOptionsEnabled: Boolean,
    onEnableBluetooth: () -> Unit,
    onEnableLocation: () -> Unit,
    onEnableDeveloperOptions: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false, // User cannot back out.
            dismissOnClickOutside = false // User cannot click away.
        )
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "System Requirements",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "The following settings must be enabled for BluSnu to function correctly:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Show button only if requirement is missing.
            if (!isBluetoothEnabled) {
                AzButton(
                    onClick = onEnableBluetooth,
                    text = "Enable Bluetooth",
                    shape = AzButtonShape.RECTANGLE
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!isLocationEnabled) {
                AzButton(
                    onClick = onEnableLocation,
                    text = "Enable Location Services",
                    shape = AzButtonShape.RECTANGLE
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!isDeveloperOptionsEnabled) {
                AzButton(
                    onClick = onEnableDeveloperOptions,
                    text = "Enable Developer Options",
                    shape = AzButtonShape.RECTANGLE
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
