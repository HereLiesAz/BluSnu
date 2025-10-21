package com.hereliesaz.blusnu.ui.attackchaining

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable
fun AttackChainingScreen(viewModel: AttackChainingViewModel) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = screenHeight * 0.2f),
        contentAlignment = Alignment.TopEnd
    ) {
    }
}
