package com.hereliesaz.blusnu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hereliesaz.blusnu.ui.components.ScreenTitle

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val updateStatus by viewModel.updateStatus.collectAsState()
    val backupUrl by viewModel.backupUrl.collectAsState()
    val permissionsStatus by viewModel.permissionsStatus.collectAsState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.checkPermissions()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = backupUrl,
                onValueChange = { viewModel.updateBackupUrl(it) },
                label = { Text("Cloud Backup URL") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = { viewModel.checkForUpdates() }) {
                Text("Check for Database Updates")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(updateStatus)
        }
    }
}
