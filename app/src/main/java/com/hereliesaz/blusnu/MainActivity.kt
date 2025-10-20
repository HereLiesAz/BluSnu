package com.hereliesaz.blusnu

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.ui.components.DisclaimerDialog
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.ui.TargetManagementViewModel
import androidx.compose.foundation.layout.fillMaxWidth
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.ui.bluebugging.BluebuggingViewModel
import com.hereliesaz.blusnu.ui.bluesmack.BlueSmackViewModel
import com.hereliesaz.blusnu.ui.bluesnarfing.BluesnarfingViewModel
import com.hereliesaz.blusnu.ui.gattfuzzing.GattFuzzingViewModel
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.aznavrail.model.AzButtonShape
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextField
import androidx.compose.foundation.clickable
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val deviceRepository by lazy { DeviceRepository() }

    private val viewModelFactory by lazy {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(TargetManagementViewModel::class.java) -> {
                        TargetManagementViewModel(application, deviceRepository) as T
                    }
                    modelClass.isAssignableFrom(BluebuggingViewModel::class.java) -> {
                        BluebuggingViewModel(application) as T
                    }
                    modelClass.isAssignableFrom(BlueSmackViewModel::class.java) -> {
                        BlueSmackViewModel(application) as T
                    }
                    modelClass.isAssignableFrom(BluesnarfingViewModel::class.java) -> {
                        BluesnarfingViewModel(application) as T
                    }
                    modelClass.isAssignableFrom(GattFuzzingViewModel::class.java) -> {
                        GattFuzzingViewModel(application) as T
                    }
                    else -> throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                // Log or handle individual permission results
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestRequiredPermissions()

        setContent {
            BluSnuTheme {
                var showDisclaimer by remember { mutableStateOf(!getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE).getBoolean("disclaimer_accepted", false)) }

                if (showDisclaimer) {
                    DisclaimerDialog {
                        getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE).edit().putBoolean("disclaimer_accepted", true).apply()
                        showDisclaimer = false
                    }
                } else {
                    val navController = rememberNavController()
                    Row(Modifier.fillMaxSize()) {
                        AzNavRail {
                            azSettings(
                                displayAppNameInHeader = true,
                                packRailButtons = false,
                                defaultShape = AzButtonShape.RECTANGLE
                            )
                            azRailItem(id = "dashboard", text = "Dashboard", onClick = { navController.navigate("dashboard") })
                            azRailItem(id = "targets", text = "Targets", onClick = { navController.navigate("targets") })
                            azRailItem(id = "attacks", text = "Attacks", onClick = { navController.navigate("attacks") })
                            azRailItem(id = "settings", text = "Settings", onClick = { navController.navigate("settings") })
                        }
                        NavHost(
                            navController = navController,
                            startDestination = "dashboard"
                        ) {
                            composable("dashboard") { DashboardScreen() }
                            composable("targets") {
                                val viewModel: TargetManagementViewModel = viewModel(factory = viewModelFactory)
                                TargetManagementScreen(viewModel = viewModel)
                            }
                            composable("attacks") { AttackModulesScreen(navController = navController) }
                            composable("settings") { SettingsScreen() }
                            composable("bluebugging") { 
                                val viewModel: BluebuggingViewModel = viewModel(factory = viewModelFactory)
                                BluebuggingScreen(viewModel = viewModel) 
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.INTERNET,
        )

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        requestPermissionsLauncher.launch(requiredPermissions.toTypedArray())
    }
}

@Composable
fun BluebuggingScreen(viewModel: BluebuggingViewModel) {
    Text(text = "Bluebugging")
}

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    Text(
        text = "Dashboard",
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetManagementScreen(modifier: Modifier = Modifier, viewModel: TargetManagementViewModel) {
    val state by viewModel.state.collectAsState()
    var filterText by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(16.dp)) {
        Row {
            Button(onClick = { viewModel.startScan() }, enabled = !state.isScanning) {
                Text("Start Scan")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { viewModel.stopScan() }, enabled = state.isScanning) {
                Text("Stop Scan")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Button(onClick = { viewModel.setFilter(Protocol.CLASSIC) }) {
                Text("Classic")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { viewModel.setFilter(Protocol.BLE) }) {
                Text("BLE")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { viewModel.setFilter(null) }) {
                Text("All")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = filterText,
            onValueChange = { 
                filterText = it
                viewModel.setFilterText(it) 
            },
            label = { Text("Filter by name or MAC") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (!state.hasPermissions) {
            Text("Permissions not granted")
        } else if (!state.isBluetoothEnabled) {
            Text("Bluetooth is not enabled")
        } else if (state.isScanning && state.discoveredDevices.isEmpty()) {
            CircularProgressIndicator()
        } else if (!state.isScanning && state.discoveredDevices.isEmpty()) {
            Text("No devices found. Click 'Start Scan' to begin.")
        } else {
            LazyColumn {
                items(state.discoveredDevices) { device ->
                    DeviceListItem(device = device, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun DeviceListItem(device: TargetDevice, viewModel: TargetManagementViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.clickable { expanded = !expanded }) {
        Row {
            Text(text = device.name ?: "Unknown")
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = device.macAddress)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "${device.rssi} dBm")
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = device.protocol.name)
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { viewModel.discoverServices(device) }) {
                Text("Services")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { viewModel.checkForVulnerabilities(device) }) {
                Text("Check Vulns")
            }
        }
        if (expanded) {
            if (device.services.isNotEmpty()) {
                Column {
                    device.services.forEach { service ->
                        Text(text = "Service: $service")
                    }
                }
            }
            if (device.vulnerabilities.isNotEmpty()) {
                Column {
                    device.vulnerabilities.forEach { vulnerability ->
                        Text(text = "Vulnerability: ${vulnerability.name} (${vulnerability.cve})")
                    }
                }
            }
        }
    }
}

@Composable
fun AttackModulesScreen(modifier: Modifier = Modifier, navController: NavController) {
    Column(modifier = modifier) {
        Button(onClick = { navController.navigate("bluesnarfing") }) {
            Text("Bluesnarfing")
        }
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Text(
        text = "Settings",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    BluSnuTheme {
        DashboardScreen()
    }
}
