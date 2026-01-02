package com.hereliesaz.blusnu.ui.magisk

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@Composable
fun MagiskScreen() {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Instructions for installing BlueZ tools via Magisk.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text("Install BlueZ Tools with Magisk", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("To use the Raw Commands screen, you need to install the BlueZ tools on your device. This can be done with a Magisk module.")
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                uriHandler.openUri("https://github.com/HereLiesAz/BluSnu/tree/main/magisk")
            }
        ) {
            Text("View Module on GitHub")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("1. Obtain precompiled binaries for the BlueZ tools (hcitool, btmgmt, etc.) for your device's architecture.")
        Text("2. Place the binaries in the `magisk/system/bin` directory in the root of this project.")
        Text("3. Zip the contents of the `magisk` directory to create the module file.")
        Text("4. Open the Magisk Manager app.")
        Text("5. Go to the Modules section.")
        Text("6. Tap 'Install from storage' and select the zip file you created.")
        Text("7. Reboot your device.")
    }
}
