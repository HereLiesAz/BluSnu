package com.hereliesaz.blusnu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.AzToggle
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.blusnu.ui.components.ScreenTitle

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val updateStatus by viewModel.updateStatus.collectAsState()
    val backupUrl by viewModel.backupUrl.collectAsState()
    val permissionsStatus by viewModel.permissionsStatus.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()
    val isRooted by viewModel.isRooted.collectAsState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.checkPermissions()
        viewModel.checkRootAccess()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "Application settings and configuration.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Permissions Status
            Text("Permissions Status:", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            permissionsStatus.forEach { (permission, isGranted) ->
                val color = if (isGranted) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
                val text = permission.substringAfterLast(".")
                Text(text = "$text: ${if (isGranted) "Granted" else "Denied"}", color = color)
            }

            val rootColor = if (isRooted) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
            Text(text = "Root Access: ${if (isRooted) "Granted" else "Denied"}", color = rootColor)
            if (!isRooted) {
                Spacer(modifier = Modifier.height(4.dp))
                AzButton(
                    onClick = { viewModel.checkRootAccess() },
                    text = "Request Root Access",
                    shape = AzButtonShape.RECTANGLE
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use Metric System (Meters)")
                AzToggle(
                    isChecked = isMetric,
                    onToggle = { viewModel.toggleMetric() },
                    toggleOnText = "Metric",
                    toggleOffText = "Imperial"
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            AzTextBox(
                value = backupUrl,
                onValueChange = { viewModel.updateBackupUrl(it) },
                hint = "Cloud Backup URL",
                onSubmit = { viewModel.updateBackupUrl(it) },
                submitButtonContent = { Text("Save") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            AzButton(
                onClick = { viewModel.checkForUpdates() },
                text = "Check for Database Updates",
                shape = AzButtonShape.RECTANGLE
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(updateStatus)

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.1f))
        }
    }
}
