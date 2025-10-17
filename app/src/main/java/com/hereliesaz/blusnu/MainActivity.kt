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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import android.bluetooth.BluetoothAdapter
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.correlator.VulnerabilityCorrelator
import com.hereliesaz.blusnu.scanner.BluetoothScanner
import com.hereliesaz.blusnu.ui.attack.BlueSmackScreen
import com.hereliesaz.blusnu.ui.attack.BluebuggingScreen
import com.hereliesaz.blusnu.ui.attack.BluesnarfingScreen
import com.hereliesaz.blusnu.ui.components.DisclaimerDialog
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                // Log or handle individual permission results
            }
        }

    private val deviceRepository = DeviceRepository()
    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        (getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
    }
    private val bluetoothScanner by lazy {
        BluetoothScanner(this, deviceRepository, bluetoothAdapter)
    }
    private val vulnerabilityCorrelator by lazy {
        VulnerabilityCorrelator(this)
    }
    private val bluesnarfing by lazy {
        com.hereliesaz.blusnu.attack.Bluesnarfing(this, bluetoothAdapter)
    }
    private val bluebugging by lazy {
        com.hereliesaz.blusnu.attack.Bluebugging(this, bluetoothAdapter)
    }
    private val blueSmack by lazy {
        com.hereliesaz.blusnu.attack.BlueSmack(this, bluetoothAdapter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestRequiredPermissions()

        setContent {
            BluSnuTheme {
                val sharedPreferences = getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE)
                val disclaimerAccepted = sharedPreferences.getBoolean("disclaimer_accepted", false)
                val showDisclaimer = remember { mutableStateOf(!disclaimerAccepted) }

                if (showDisclaimer.value) {
                    DisclaimerDialog {
                        sharedPreferences.edit().putBoolean("disclaimer_accepted", true).apply()
                        showDisclaimer.value = false
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
                            composable("dashboard") {
                                DashboardScreen(
                                    onStartScan = { bluetoothScanner.startScan() },
                                    onStopScan = { bluetoothScanner.stopScan() }
                                )
                            }
                            composable("targets") {
                                val devices by deviceRepository.discoveredDevices.collectAsState()
                                TargetManagementScreen(
                                    devices = devices,
                                    vulnerabilityCorrelator = vulnerabilityCorrelator,
                                    onDeviceClick = { device ->
                                        bluetoothScanner.connectToDevice(device)
                                    }
                                )
                            }
                            composable("attacks") { AttackModulesScreen(navController = navController) }
                            composable("settings") { SettingsScreen() }
                            composable("bluesnarfing") {
                                BluesnarfingScreen(onAttack = { macAddress ->
                                    bluesnarfing.attack(macAddress)
                                })
                            }
                            composable("bluebugging") {
                                BluebuggingScreen(onAttack = { macAddress, command ->
                                    bluebugging.attack(macAddress, command)
                                })
                            }
                            composable("bluesmack") {
                                BlueSmackScreen(onAttack = { macAddress, packetSize, packetCount ->
                                    blueSmack.attack(macAddress, packetSize, packetCount)
                                })
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
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onStartScan) {
            Text("Start Scan")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onStopScan) {
            Text("Stop Scan")
        }
    }
}

@Composable
fun TargetManagementScreen(
    modifier: Modifier = Modifier,
    devices: List<com.hereliesaz.blusnu.data.TargetDevice>,
    vulnerabilityCorrelator: VulnerabilityCorrelator,
    onDeviceClick: (com.hereliesaz.blusnu.data.TargetDevice) -> Unit
) {
    var filteredDevices by remember { mutableStateOf(devices) }
    var sortByRssi by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = {
                filteredDevices = devices.filter { it.protocol == com.hereliesaz.blusnu.data.Protocol.CLASSIC }
            }) {
                Text("Classic")
            }
            Button(onClick = {
                filteredDevices = devices.filter { it.protocol == com.hereliesaz.blusnu.data.Protocol.BLE }
            }) {
                Text("BLE")
            }
            Button(onClick = {
                filteredDevices = devices
            }) {
                Text("All")
            }
            Button(onClick = { sortByRssi = !sortByRssi }) {
                Text(if (sortByRssi) "Sorted by RSSI" else "Sort by RSSI")
            }
        }

        val devicesToShow = if (sortByRssi) {
            filteredDevices.sortedByDescending { it.rssi }
        } else {
            filteredDevices
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(devicesToShow) { device ->
                var expanded by remember { mutableStateOf(false) }
                val vulnerabilities = vulnerabilityCorrelator.correlate(device)
                val isVulnerable = vulnerabilities.isNotEmpty()

                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .clickable {
                            if (device.protocol == com.hereliesaz.blusnu.data.Protocol.BLE) {
                                onDeviceClick(device)
                            }
                            expanded = !expanded
                        }
                ) {
                    Row {
                        Text(text = device.name ?: "Unknown Device")
                        if (isVulnerable) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Vulnerable",
                                tint = Color.Red,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    Text(text = device.macAddress)
                    Text(text = "RSSI: ${device.rssi}")
                    Text(text = "Protocol: ${device.protocol}")
                    if (expanded) {
                        device.services.forEach { service ->
                            Text(text = "  - $service")
                        }
                        if (isVulnerable) {
                            Text("Vulnerabilities:")
                            vulnerabilities.forEach { vulnerability ->
                                Text("  - ${vulnerability.description}")
                            }
                        }
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
        Button(onClick = { navController.navigate("bluebugging") }) {
            Text("Bluebugging")
        }
        Button(onClick = { navController.navigate("bluesmack") }) {
            Text("BlueSmack")
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

sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object Dashboard : BottomNavItem("dashboard", Icons.Default.Home, "Dashboard")
    object Targets : BottomNavItem("targets", Icons.Default.List, "Targets")
    object Attacks : BottomNavItem("attacks", Icons.Default.Send, "Attacks")
    object Settings : BottomNavItem("settings", Icons.Default.Settings, "Settings")
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

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    BluSnuTheme {
        DashboardScreen(onStartScan = {}, onStopScan = {})
    }
}