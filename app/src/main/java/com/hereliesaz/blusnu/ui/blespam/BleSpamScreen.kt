package com.hereliesaz.blusnu.ui.blespam

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BleSpamScreen(viewModel: BleSpamViewModel) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("BLE Spam (Advertisement Flooding) Module")
        // TODO: Create UI to select payload types
        // TODO: Implement MAC address rotation
    }
}
