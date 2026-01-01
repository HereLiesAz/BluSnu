package com.hereliesaz.blusnu.ui.braktooth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BrakToothScreen(viewModel: BrakToothViewModel) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("BrakTooth Module")
        // TODO: Implement external hardware interface (ESP32)
        // TODO: Implement LMP packet injection
    }
}
