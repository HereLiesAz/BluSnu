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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.hereliesaz.blusnu.ui.components.DisclaimerDialog
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme

class MainActivity : ComponentActivity() {

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
                            composable("dashboard") { DashboardScreen() }
                            composable("targets") {
                                val viewModel: TargetManagementViewModel = viewModel()
                                viewModel.hasPermissions = hasBluetoothPermissions()
                                viewModel.isBluetoothEnabled = isBluetoothEnabled()
                                TargetManagementScreen(viewModel = viewModel)
                            }
                            composable("attacks") { AttackModulesScreen() }
                            composable("settings") { SettingsScreen() }
                        }
                    }
                }
            }
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        return bluetoothAdapter?.isEnabled ?: false
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
fun DashboardScreen(modifier: Modifier = Modifier) {
    Text(
        text = "Dashboard",
        modifier = modifier
    )
}

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.ui.TargetManagementViewModel

@Composable
fun TargetManagementScreen(modifier: Modifier = Modifier, viewModel: TargetManagementViewModel = viewModel()) {
    val devices by viewModel.discoveredDevices.collectAsState()

    Column(modifier = modifier) {
        Row {
            Button(onClick = { viewModel.startScan() }) {
                Text("Start Scan")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { viewModel.stopScan() }) {
                Text("Stop Scan")
            }
        }

        if (devices.isEmpty()) {
            Text("No devices found")
        } else {
            LazyColumn {
                items(devices) { device ->
                    DeviceListItem(device = device)
                }
            }
        }
    }
}

@Composable
fun DeviceListItem(device: TargetDevice) {
    Row {
        Text(text = device.name ?: "Unknown")
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = device.macAddress)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "${device.rssi} dBm")
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = device.protocol.name)
    }
}

@Composable
fun AttackModulesScreen(modifier: Modifier = Modifier) {
    Text(
        text = "Attack Modules",
        modifier = modifier
    )
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
        DashboardScreen()
    }
}