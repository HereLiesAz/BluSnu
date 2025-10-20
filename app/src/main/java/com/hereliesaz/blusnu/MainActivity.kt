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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
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
import com.hereliesaz.blusnu.ui.bluesnarfing.BluesnarfingScreen
import com.hereliesaz.blusnu.ui.bluebugging.BluebuggingScreen
import com.hereliesaz.blusnu.ui.bluesmack.BlueSmackScreen
import com.hereliesaz.blusnu.ui.bluesmack.BlueSmackViewModel
import com.hereliesaz.blusnu.ui.bluebugging.BluebuggingViewModel
import com.hereliesaz.blusnu.ui.bluesnarfing.BluesnarfingViewModel
import com.hereliesaz.blusnu.ui.gattfuzzing.GattFuzzingScreen
import com.hereliesaz.blusnu.ui.gattfuzzing.GattFuzzingViewModel
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
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.height

class MainActivity : ComponentActivity() {

    private val deviceRepository by lazy { DeviceRepository() }

    private val viewModelFactory by lazy {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(TargetManagementViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return TargetManagementViewModel(application, deviceRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
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
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            BottomNavigationBar(navController = navController)
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "dashboard",
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("dashboard") { DashboardScreen() }
                            composable("targets") {
                                val viewModel: TargetManagementViewModel = viewModel(factory = viewModelFactory)
                                TargetManagementScreen(viewModel = viewModel)
                            }
                            composable("attacks") { AttackModulesScreen(navController = navController) }
                            composable("settings") { SettingsScreen() }
                            composable("bluesnarfing") {
                                val viewModel: BluesnarfingViewModel = viewModel()
                                viewModel.hasPermissions = hasBluetoothPermissions()
                                BluesnarfingScreen(viewModel = viewModel)
                            }
                            composable("bluebugging") {
                                val viewModel: BluebuggingViewModel = viewModel()
                                viewModel.hasPermissions = hasBluetoothPermissions()
                                BluebuggingScreen(viewModel = viewModel)
                            }
                            composable("bluesmack") {
                                val viewModel: BlueSmackViewModel = viewModel()
                                viewModel.hasPermissions = hasBluetoothPermissions()
                                BlueSmackScreen(viewModel = viewModel)
                            }
                            composable("gattfuzzing") {
                                val viewModel: GattFuzzingViewModel = viewModel()
                                viewModel.hasPermissions = hasBluetoothPermissions()
                                GattFuzzingScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
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

enum class SortOption {
    NAME,
    RSSI,
    PROTOCOL
}

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    Text(
        text = "Dashboard",
        modifier = modifier
    )
}

@Composable
fun TargetManagementScreen(modifier: Modifier = Modifier, viewModel: TargetManagementViewModel = viewModel()) {
    val devices by viewModel.filteredDevices.collectAsState()
    val filterText by viewModel.filterText.collectAsState()

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
        androidx.compose.material3.TextField(
            value = filterText,
            onValueChange = { viewModel.setFilterText(it) },
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
                items(devices) { device ->
                    DeviceListItem(device = device, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun DeviceListItem(device: TargetDevice, viewModel: TargetManagementViewModel) {
    Column(
        modifier = Modifier.background(
            if (device.vulnerabilities.isNotEmpty()) Color.Red.copy(alpha = 0.5f) else Color.Transparent
        )
    ) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttackModulesScreen(modifier: Modifier = Modifier, navController: NavController) {
    Column(modifier = modifier) {
        Button(onClick = { navController.navigate("bluesnarfing") }) {
            Text("Bluesnarfing")
        }
        Button(onClick = { navController.navigate("bluebugging") }) {
            Text("Bluebugging")
        }
        Button(onClick = { navController.navigate("bluesmack") }) {
            Text("BlueSmack")
        }
        Button(onClick = { navController.navigate("gattfuzzing") }) {
            Text("GATT Fuzzing")
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

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Targets,
        BottomNavItem.Attacks,
        BottomNavItem.Settings
    )
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        navController.graph.startDestinationRoute?.let { route ->
                            popUpTo(route) {
                                saveState = true
                            }
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object Dashboard : BottomNavItem("dashboard", Icons.Default.Home, "Dashboard")
    object Targets : BottomNavItem("targets", Icons.Default.List, "Targets")
    object Attacks : BottomNavItem("attacks", Icons.Default.Send, "Attacks")
    object Settings : BottomNavItem("settings", Icons.Default.Settings, "Settings")
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    BluSnuTheme {
        DashboardScreen()
    }
}
