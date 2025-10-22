package com.hereliesaz.blusnu

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.gson.Gson
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.blusnu.data.BtlejackingModule
import com.hereliesaz.blusnu.data.BtlejuiceModule
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.VulnerabilityCorrelator
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.ui.FilterProtocol
import com.hereliesaz.blusnu.ui.FilterType
import com.hereliesaz.blusnu.ui.TargetManagementScreen
import com.hereliesaz.blusnu.ui.TargetManagementViewModel
import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingScreen
import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingViewModel
import com.hereliesaz.blusnu.ui.bluebugging.BluebuggingScreen
import com.hereliesaz.blusnu.ui.bluebugging.BluebuggingViewModel
import com.hereliesaz.blusnu.ui.bluesmack.BlueSmackScreen
import com.hereliesaz.blusnu.ui.bluesmack.BlueSmackViewModel
import com.hereliesaz.blusnu.ui.bluesnarfing.BluesnarfingScreen
import com.hereliesaz.blusnu.ui.bluesnarfing.BluesnarfingViewModel
import com.hereliesaz.blusnu.ui.btlejacking.BtlejackingScreen
import com.hereliesaz.blusnu.ui.btlejacking.BtlejackingViewModel
import com.hereliesaz.blusnu.ui.btlejuice.BtlejuiceScreen
import com.hereliesaz.blusnu.ui.btlejuice.BtlejuiceViewModel
import com.hereliesaz.blusnu.ui.btlejuicemitm.BtlejuiceMitmScreen
import com.hereliesaz.blusnu.ui.btlejuicemitm.BtlejuiceMitmViewModel
import com.hereliesaz.blusnu.ui.components.DisclaimerDialog
import com.hereliesaz.blusnu.ui.dashboard.DashboardScreen
import com.hereliesaz.blusnu.ui.dashboard.DashboardViewModel
import com.hereliesaz.blusnu.ui.gattfuzzing.GattFuzzingScreen
import com.hereliesaz.blusnu.ui.gattfuzzing.GattFuzzingViewModel
import com.hereliesaz.blusnu.ui.geolocation.GeolocationScreen
import com.hereliesaz.blusnu.ui.geolocation.GeolocationViewModel
import com.hereliesaz.blusnu.ui.keystrokeinjection.KeystrokeInjectionScreen
import com.hereliesaz.blusnu.ui.keystrokeinjection.KeystrokeInjectionViewModel
import com.hereliesaz.blusnu.ui.reporting.ReportingScreen
import com.hereliesaz.blusnu.ui.reporting.ReportingViewModel
import com.hereliesaz.blusnu.ui.settings.SettingsScreen
import com.hereliesaz.blusnu.ui.settings.SettingsViewModel
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme
import com.hereliesaz.blusnu.ui.bluebugging.BluebuggingScreen
import com.hereliesaz.blusnu.ui.bluesnarfing.BluesnarfingScreen
import com.hereliesaz.blusnu.ui.bluesmack.BlueSmackScreen
import com.hereliesaz.blusnu.ui.gattfuzzing.GattFuzzingScreen
import com.hereliesaz.blusnu.ui.btlejacking.BtlejackingScreen
import com.hereliesaz.blusnu.ui.keystrokeinjection.KeystrokeInjectionScreen



class MainActivity : ComponentActivity() {

    private val deviceRepository by lazy { DeviceRepository() }
    private val hardwareManager by lazy { HardwareManager() }
    private val btlejuiceModule by lazy { BtlejuiceModule(hardwareManager) }
    private val keystrokeInjectionModule by lazy { com.hereliesaz.blusnu.data.KeystrokeInjectionModule() }
    private val vulnerabilityCorrelator by lazy { VulnerabilityCorrelator(applicationContext) }

    private val viewModelFactory by lazy {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return when {
                    modelClass.isAssignableFrom(TargetManagementViewModel::class.java) -> {
                        TargetManagementViewModel(application, deviceRepository, vulnerabilityCorrelator) as T
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
                        GeolocationViewModel(application, deviceRepository) as T
                    }
                    modelClass.isAssignableFrom(KeystrokeInjectionViewModel::class.java) -> {
                        KeystrokeInjectionViewModel(application, keystrokeInjectionModule) as T
                    }
                    modelClass.isAssignableFrom(AttackChainingViewModel::class.java) -> {
                        val repository = com.hereliesaz.blusnu.data.AttackChainRepository(application)
                        AttackChainingViewModel(application, repository) as T
                    }
                    modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                        DashboardViewModel(application, deviceRepository) as T
                    }
                    modelClass.isAssignableFrom(ReportingViewModel::class.java) -> {
                        ReportingViewModel(application) as T
                    }
                    modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                        SettingsViewModel(application) as T
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

        vulnerabilityCorrelator.loadVulnerabilities()

        requestRequiredPermissions()

        setContent {
            BluSnuTheme {
                var showDisclaimer by remember { mutableStateOf(!getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE).getBoolean("disclaimer_accepted", false)) }

                if (showDisclaimer) {
                    DisclaimerDialog {
                        getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE).edit {
                            putBoolean(
                                "disclaimer_accepted",
                                true
                            )
                        }
                        showDisclaimer = false
                    }
                } else {
                    val navController = rememberNavController()
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Row(Modifier.fillMaxSize().padding(innerPadding)) {
                            AzNavRail {
                                azSettings(
                                    displayAppNameInHeader = false,
                                    packRailButtons = true,

                                )
                                azRailItem(id = "dashboard", text = "Dashboard", onClick = { navController.navigate("dashboard") }, shape = AzButtonShape.RECTANGLE)
                                azRailItem(id = "targets", text = "Targets", onClick = { navController.navigate("targets") }, shape = AzButtonShape.RECTANGLE)
                                azRailItem(id = "bluebugging", text = "Bugging", onClick = { navController.navigate("bluebugging") }, shape = AzButtonShape.RECTANGLE)
                                azRailItem(id = "bluesnarfing", text = "Snarfing", onClick = { navController.navigate("bluesnarfing") }, shape = AzButtonShape.RECTANGLE)
                                azRailItem(id = "bluesmack", text = "Smack", onClick = { navController.navigate("bluesmack") }, shape = AzButtonShape.RECTANGLE)
                                azRailItem(id = "gattfuzzing", text = "Fuzzing", onClick = { navController.navigate("gattfuzzing") }, shape = AzButtonShape.RECTANGLE)
                                azRailItem(id = "btlejacking", text = "Jacking", onClick = { navController.navigate("btlejacking") }, shape = AzButtonShape.RECTANGLE)
                                azRailItem(id = "btlejuice", text = "Juice", onClick = { navController.navigate("btlejuice/null") }, shape = AzButtonShape.RECTANGLE)
                                azRailItem(id = "geolocation", text = "Location", onClick = { navController.navigate("geolocation") }, shape = AzButtonShape.RECTANGLE)
                                azRailItem(id = "keystroke_injection", text = "Injection", onClick = { navController.navigate("keystroke_injection") }, shape = AzButtonShape.RECTANGLE)
                                azRailItem(id = "attack_chaining", text = "Chaining", onClick = { navController.navigate("attack_chaining") }, shape = AzButtonShape.RECTANGLE)
                                azRailItem(id = "reporting", text = "Reporting", onClick = { navController.navigate("reporting") }, shape = AzButtonShape.RECTANGLE)
                                azRailItem(id = "settings", text = "Settings", onClick = { navController.navigate("settings") }, shape = AzButtonShape.RECTANGLE)
                            }
                            NavHost(
                                navController = navController,
                                startDestination = "dashboard"
                            ) {
                                composable("dashboard") {
                                    val viewModel: DashboardViewModel = viewModel(factory = viewModelFactory)
                                    val state by viewModel.state.collectAsState()
                                    DashboardScreen(
                                        bleDeviceCount = state.bleDeviceCount,
                                        classicDeviceCount = state.classicDeviceCount,
                                        activeTasks = state.activeTasks,
                                        savedSessions = state.savedSessions,
                                        attackChainTemplates = state.attackChainTemplates,
                                        onStartScanClicked = { navController.navigate("targets") }
                                    )
                                }
                                composable("targets") {
                                    val viewModel: TargetManagementViewModel = viewModel(factory = viewModelFactory)
                                    TargetManagementScreen(viewModel = viewModel, navController = navController)
                                }
                                composable("settings") { SettingsScreen() }
                                composable("bluebugging") {
                                    val viewModel: BluebuggingViewModel = viewModel(factory = viewModelFactory)
                                    BluebuggingScreen(viewModel = viewModel)
                                }
                                composable("bluesnarfing") {
                                    val viewModel: BluesnarfingViewModel = viewModel(factory = viewModelFactory)
                                    BluesnarfingScreen(viewModel = viewModel, hasPermissions = true)
                                }
                                composable("btlejacking") {
                                    val viewModel: BtlejackingViewModel = viewModel(factory = viewModelFactory)
                                    BtlejackingScreen(viewModel = viewModel)
                                }
                                composable(
                                    "btlejuice/{targetDevice}",
                                    arguments = listOf(navArgument("targetDevice") { type = NavType.StringType })
                                ) { backStackEntry ->
                                    val targetDeviceJson = backStackEntry.arguments?.getString("targetDevice")
                                    val targetDevice = Gson().fromJson(targetDeviceJson, TargetDevice::class.java)
                                    val viewModel: BtlejuiceViewModel = viewModel(factory = viewModelFactory)
                                    val hardwareState by viewModel.hardwareState.collectAsState()
                                    val btlejuiceState by viewModel.btlejuiceState.collectAsState()
                                    val logs by viewModel.logs.collectAsState()
                                    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
                                    val gattTraffic by viewModel.gattTraffic.collectAsState()
                                    BtlejuiceScreen(
                                        hardwareState = hardwareState,
                                        btlejuiceState = btlejuiceState,
                                        logs = logs,
                                        discoveredDevices = discoveredDevices,
                                        onConnectHardware = viewModel::onConnectHardware,
                                        onConnectDual = viewModel::onConnectDual,
                                        onStartProxy = { viewModel.onStartProxy(targetDevice) },
                                        onStopProxy = viewModel::onStopProxy,
                                        gattTraffic = gattTraffic
                                    )
                                }
                                composable("geolocation") {
                                    val viewModel: GeolocationViewModel = viewModel(factory = viewModelFactory)
                                    GeolocationScreen(viewModel = viewModel, deviceRepository = deviceRepository)
                                }
                                composable("keystroke_injection") {
                                    val viewModel: KeystrokeInjectionViewModel = viewModel(factory = viewModelFactory)
                                    val state by viewModel.state.collectAsState()
                                    KeystrokeInjectionScreen(
                                        state = state,
                                        onAttemptAttack = viewModel::onAttemptAttack,
                                        onSendKeystrokes = viewModel::onSendKeystrokes
                                    )
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
                                composable("reporting") {
                                    val viewModel: ReportingViewModel = viewModel(factory = viewModelFactory)
                                    ReportingScreen(viewModel = viewModel)
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








@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    BluSnuTheme {
        DashboardScreen()
    }
}