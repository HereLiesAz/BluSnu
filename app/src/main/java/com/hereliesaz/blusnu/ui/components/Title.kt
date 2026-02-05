package com.hereliesaz.blusnu.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Generic Title component.
 *
 * Different from `ScreenTitle`, this uses a Row layout with a Spacer to push
 * the text to the end (Right aligned).
 *
 * @param text The title text.
 */
@Composable
fun Title(text: String) {
    Row {
        // Pushes content to the right.
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
    }
}
