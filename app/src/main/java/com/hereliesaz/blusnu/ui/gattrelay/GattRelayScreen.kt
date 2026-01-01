package com.hereliesaz.blusnu.ui.gattrelay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GattRelayScreen(viewModel: GattRelayViewModel) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("GATT Relay (Tesla Attack) Module")
        // TODO: Create UI for Node A/B selection
        // TODO: Implement RTT measurement display
    }
}
