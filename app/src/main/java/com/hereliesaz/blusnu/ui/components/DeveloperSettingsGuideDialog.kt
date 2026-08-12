package com.hereliesaz.blusnu.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.blusnu.data.ManagedDevSetting

/**
 * Explains recommended developer settings and lets the user enable each one for the session.
 *
 * Any setting the user enables here is automatically restored to its previous state when the app
 * exits. Settings the user already had enabled are shown as active but are NOT tracked — they
 * will not be touched on exit.
 *
 * @param settings List of settings with their current and tracked state.
 * @param onEnable Called when the user taps "Enable for Session" on a setting.
 * @param onOpenDeveloperSettings Opens the system Developer Options screen.
 * @param onDismiss Dismisses the dialog and suppresses it for future launches.
 */
@Composable
fun DeveloperSettingsGuideDialog(
    settings: List<DevSettingUiState>,
    onEnable: (ManagedDevSetting) -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Developer Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "These Android developer settings improve attack capabilities. " +
                        "Any setting you enable here is automatically restored to its " +
                        "previous state when the app exits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                settings.forEachIndexed { index, state ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    }
                    DevSettingRow(
                        state = state,
                        onEnable = { onEnable(state.setting) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                AzButton(
                    onClick = onOpenDeveloperSettings,
                    text = "Open Developer Options",
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            AzButton(
                onClick = onDismiss,
                text = "Got It",
                shape = AzButtonShape.RECTANGLE
            )
        }
    )
}

@Composable
private fun DevSettingRow(
    state: DevSettingUiState,
    onEnable: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.setting.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val statusText = when {
                    state.isEnabledByApp -> "Active (auto-restores on exit)"
                    state.isAlreadyOn    -> "Already enabled"
                    else                 -> "Not enabled"
                }
                val statusColor = when {
                    state.isEnabledByApp || state.isAlreadyOn ->
                        MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }

            if (!state.isEnabledByApp && !state.isAlreadyOn) {
                Spacer(modifier = Modifier.width(8.dp))
                AzButton(
                    onClick = onEnable,
                    text = "Enable",
                    shape = AzButtonShape.RECTANGLE
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.setting.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** UI state for a single managed developer setting. */
data class DevSettingUiState(
    val setting: ManagedDevSetting,
    /** True if the setting was already at enableValue before we touched it (user's own config). */
    val isAlreadyOn: Boolean,
    /** True if we enabled it this session and are tracking it for auto-restore. */
    val isEnabledByApp: Boolean
)
