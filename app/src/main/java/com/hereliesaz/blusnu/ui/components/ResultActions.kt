package com.hereliesaz.blusnu.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Reusable row of Copy and Share icon buttons for attack result areas.
 *
 * @param resultText The text content to copy to clipboard or share via intent.
 * @param label Short label used for the clipboard clip and share chooser title.
 * @param modifier Optional [Modifier] for the row container.
 */
@Composable
fun ResultActions(
    resultText: String,
    label: String = "Attack Results",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Copy to clipboard.
        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(label, resultText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            enabled = resultText.isNotEmpty()
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy results",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // Share via system sheet.
        IconButton(
            onClick = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, resultText)
                    putExtra(Intent.EXTRA_SUBJECT, label)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share $label"))
            },
            enabled = resultText.isNotEmpty()
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share results",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
