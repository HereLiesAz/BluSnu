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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.ui.FilterType
import com.hereliesaz.blusnu.ui.components.DisclaimerDialog
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.ui.TargetManagementViewModel
import androidx.compose.foundation.layout.fillMaxWidth
import com.hereliesaz.blusnu.data.Protocol
import com.hereliesaz.blusnu.ui.bluebugging.BluebuggingScreen
import com.hereliesaz.blusnu.ui.bluebugging.BluebuggingViewModel
import com.hereliesaz.blusnu.ui.bluesmack.BlueSmackScreen
import com.hereliesaz.blusnu.ui.bluesmack.BlueSmackViewModel
import com.hereliesaz.blusnu.ui.bluesnarfing.BluesnarfingScreen
import com.hereliesaz.blusnu.ui.bluesnarfing.BluesnarfingViewModel
import com.hereliesaz.blusnu.ui.gattfuzzing.GattFuzzingScreen
import com.hereliesaz.blusnu.ui.gattfuzzing.GattFuzzingViewModel
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingScreen
import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingViewModel
import com.hereliesaz.blusnu.data.BtlejackingModule
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.ui.btlejacking.BtlejackingScreen
import com.hereliesaz.blusnu.ui.btlejacking.BtlejackingViewModel
import com.hereliesaz.blusnu.ui.btlejuice.BtlejuiceScreen
import com.hereliesaz.blusnu.ui.btlejuice.BtlejuiceViewModel
import com.hereliesaz.blusnu.ui.geolocation.GeolocationScreen
import com.hereliesaz.blusnu.ui.geolocation.GeolocationViewModel
import com.hereliesaz.blusnu.ui.keystrokeinjection.KeystrokeInjectionScreen
import com.hereliesaz.blusnu.ui.keystrokeinjection.KeystrokeInjectionViewModel
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextField
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val deviceRepository by lazy { DeviceRepository() }
    private val hardwareManager by lazy { HardwareManager() }

    private val viewModelFactory by lazy {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
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
                    modelClass.isAssignableFrom(BtlejackingViewModel::class.java) -> {
                        val btlejackingModule = BtlejackingModule(hardwareManager)
                        BtlejackingViewModel(application, hardwareManager, btlejackingModule, deviceRepository) as T
                    }
                    modelClass.isAssignableFrom(BtlejuiceViewModel::class.java) -> {
                        BtlejuiceViewModel(application) as T
                    }
                    modelClass.isAssignableFrom(GeolocationViewModel::class.java) -> {
                        GeolocationViewModel(application) as T
                    }
                    modelClass.isAssignableFrom(KeystrokeInjectionViewModel::class.java) -> {
                        KeystrokeInjectionViewModel(application) as T
                    }
                    modelClass.isAssignableFrom(AttackChainingViewModel::class.java) -> {
                        AttackChainingViewModel(application) as T
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
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Row(Modifier.fillMaxSize().padding(innerPadding)) {
                            AzNavRail {
                                azSettings(
                                    displayAppNameInHeader = false,
                                    packRailButtons = true
                                )
                                azRailItem(id = "dashboard", text = "Dashboard", onClick = { navController.navigate("dashboard") })
                                azRailItem(id = "targets", text = "Targets", onClick = { navController.navigate("targets") })
                                azRailItem(id = "bluebugging", text = "Bugging", onClick = { navController.navigate("bluebugging") })
                                azRailItem(id = "bluesnarfing", text = "Snarfing", onClick = { navController.navigate("bluesnarfing") })
                                azRailItem(id = "btlejacking", text = "Jacking", onClick = { navController.navigate("btlejacking") })
                                azRailItem(id = "btlejuice", text = "Juice", onClick = { navController.navigate("btlejuice") })
                                azRailItem(id = "geolocation", text = "Location", onClick = { navController.navigate("geolocation") })
                                azRailItem(id = "keystroke_injection", text = "Injection", onClick = { navController.navigate("keystroke_injection") })
                                azRailItem(id = "attack_chaining", text = "Chaining", onClick = { navController.navigate("attack_chaining") })
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
                                composable("settings") { SettingsScreen() }
                                composable("bluebugging") {
                                    val viewModel: BluebuggingViewModel = viewModel(factory = viewModelFactory)
                                    BluebuggingScreen(viewModel = viewModel)
                                }
                                composable("bluesnarfing") {
                                    val viewModel: BluesnarfingViewModel = viewModel(factory = viewModelFactory)
                                    BluesnarfingScreen(viewModel = viewModel)
                                }
                                composable("btlejacking") {
                                    val viewModel: BtlejackingViewModel = viewModel(factory = viewModelFactory)
                                    BtlejackingScreen(viewModel = viewModel)
                                }
                                composable("btlejuice") {
                                    val viewModel: BtlejuiceViewModel = viewModel(factory = viewModelFactory)
                                    BtlejuiceScreen(viewModel = viewModel)
                                }
                                composable("geolocation") {
                                    val viewModel: GeolocationViewModel = viewModel(factory = viewModelFactory)
                                    GeolocationScreen(viewModel = viewModel)
                                }
                                composable("keystroke_injection") {
                                    val viewModel: KeystrokeInjectionViewModel = viewModel(factory = viewModelFactory)
                                    KeystrokeInjectionScreen(viewModel = viewModel)
                                }
                                composable("attack_chaining") {
                                    val viewModel: AttackChainingViewModel = viewModel(factory = viewModelFactory)
                                    AttackChainingScreen(viewModel = viewModel)
                                }
                                composable("gattfuzzing") {
                                    val viewModel: GattFuzzingViewModel = viewModel(factory = viewModelFactory)
                                    GattFuzzingScreen(viewModel = viewModel)
                                }
                                composable("bluesmack") {
                                    val viewModel: BlueSmackViewModel = viewModel(factory = viewModelFactory)
                                    BlueSmackScreen(viewModel = viewModel)
                                }
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
fun BluesnarfingScreen(viewModel: BluesnarfingViewModel) {
    Text(text = "Bluesnarfing")
}

@Composable
fun BtlejackingScreen(viewModel: BtlejackingViewModel) {
    Text(text = "Btlejacking")
}

@Composable
fun BtlejuiceScreen(viewModel: BtlejuiceViewModel) {
    Text(text = "Btlejuice")
}

@Composable
fun GeolocationScreen(viewModel: GeolocationViewModel) {
    Text(text = "Geolocation")
}

@Composable
fun KeystrokeInjectionScreen(viewModel: KeystrokeInjectionViewModel) {
    Text(text = "Keystroke Injection")
}

@Composable
fun AttackChainingScreen(viewModel: AttackChainingViewModel) {
    Text(text = "Attack Chaining")
}

@Composable
fun GattFuzzingScreen(viewModel: GattFuzzingViewModel) {
    Text(text = "Gatt Fuzzing")
}

@Composable
fun BlueSmackScreen(viewModel: BlueSmackViewModel) {
    Text(text = "BlueSmack")
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
    var textFilter by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<FilterType>(FilterType.Text) }

    Column(modifier = modifier.padding(16.dp)) {
        Row {
            OutlinedButton(
                onClick = { if (state.isScanning) viewModel.stopScan() else viewModel.startScan() },
                shape = RoundedCornerShape(0.dp)
            ) {
                Text(if (state.isScanning) "Stop Scan" else "Start Scan")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        var filterExpanded by remember { mutableStateOf(false) }
        var filterTypeExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(expanded = filterExpanded, onExpandedChange = { filterExpanded = !filterExpanded }) {
            TextField(
                value = textFilter,
                onValueChange = { 
                    textFilter = it
                    if (it.isNotBlank()) {
                        viewModel.addFilter(filterType, it)
                    } else {
                        viewModel.removeFilter(filterType)
                    }
                },
                label = { Text("Filter by $filterType") },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded)
                }
            )
            ExposedDropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                when(filterType) {
                    FilterType.Text -> {
                        state.discoveredDevices.forEach { device ->
                            DropdownMenuItem(text = { Text(device.name ?: device.macAddress) }, onClick = {
                                textFilter = device.name ?: device.macAddress
                                viewModel.addFilter(FilterType.Text, textFilter)
                                filterExpanded = false
                            })
                        }
                    }
                    FilterType.Protocol -> {
                        DropdownMenuItem(text = { Text("Classic") }, onClick = {
                            viewModel.addFilter(FilterType.Protocol, Protocol.CLASSIC)
                            filterExpanded = false
                        })
                        DropdownMenuItem(text = { Text("BLE") }, onClick = {
                            viewModel.addFilter(FilterType.Protocol, Protocol.BLE)
                            filterExpanded = false
                        })
                        DropdownMenuItem(text = { Text("All") }, onClick = {
                            viewModel.removeFilter(FilterType.Protocol)
                            filterExpanded = false
                        })
                    }
                    else -> {}
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.End)) {
            OutlinedButton(onClick = { filterTypeExpanded = true }, shape = RoundedCornerShape(0.dp)) {
                Text("Filter by")
            }
            DropdownMenu(expanded = filterTypeExpanded, onDismissRequest = { filterTypeExpanded = false }) {
                DropdownMenuItem(text = { Text("Text") }, onClick = {
                    filterType = FilterType.Text
                    filterTypeExpanded = false
                })
                DropdownMenuItem(text = { Text("Protocol") }, onClick = {
                    filterType = FilterType.Protocol
                    filterTypeExpanded = false
                })
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!state.hasPermissions) {
            Text("Permissions not granted")
        } else if (!state.isBluetoothEnabled) {
            Text("Bluetooth is not enabled")
        } else if (state.isScanning && state.discoveredDevices.isEmpty()) {
            CircularProgressIndicator()
        } else if (!state.isScanning && state.discoveredDevices.isEmpty()) {
            Text("No devices found. Click \'Start Scan\' to begin.")
        } else {
            LazyColumn {
                items(state.discoveredDevices, key = { it.macAddress }) { device ->
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
        }
        Row {
            OutlinedButton(onClick = { viewModel.discoverServices(device) }, shape = RoundedCornerShape(0.dp)) {
                Text("Services")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { viewModel.checkForVulnerabilities(device) }, shape = RoundedCornerShape(0.dp)) {
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
