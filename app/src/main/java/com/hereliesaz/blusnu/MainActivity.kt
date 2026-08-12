package com.hereliesaz.blusnu

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.gson.Gson
import com.hereliesaz.aznavrail.*
import com.hereliesaz.aznavrail.model.AzDockingSide
import com.hereliesaz.blusnu.data.AppDatabase
import com.hereliesaz.blusnu.data.AttackChainTemplateRepository
import com.hereliesaz.blusnu.data.BleSpamModule
import com.hereliesaz.blusnu.data.BlueSmackModule
import com.hereliesaz.blusnu.data.BluffsModule
import com.hereliesaz.blusnu.data.BrakToothModule
import com.hereliesaz.blusnu.data.BtlejuiceModule
import com.hereliesaz.blusnu.data.BtlejackingModule
import com.hereliesaz.blusnu.data.CloudBackup
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.MacLookupClient
import com.hereliesaz.blusnu.data.SavedSessionRepository
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.data.VulnerabilityCorrelator
import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingScreen
import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingViewModel
import com.hereliesaz.blusnu.ui.blespam.BleSpamScreen
import com.hereliesaz.blusnu.ui.blespam.BleSpamViewModel
import com.hereliesaz.blusnu.ui.bluetoothlog.BluetoothLogScreen
import com.hereliesaz.blusnu.ui.bluetoothlog.BluetoothLogViewModel
import com.hereliesaz.blusnu.ui.bluffs.BluffsScreen
import com.hereliesaz.blusnu.ui.bluffs.BluffsViewModel
import com.hereliesaz.blusnu.ui.braktooth.BrakToothScreen
import com.hereliesaz.blusnu.ui.braktooth.BrakToothViewModel
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
import com.hereliesaz.blusnu.ui.components.DisclaimerDialog
import com.hereliesaz.blusnu.ui.components.SystemRequirementsDialog
import com.hereliesaz.blusnu.ui.dashboard.DashboardScreen
import com.hereliesaz.blusnu.ui.dashboard.DashboardViewModel
import com.hereliesaz.blusnu.ui.devicemanagement.DeviceManagementScreen
import com.hereliesaz.blusnu.ui.devicemanagement.DeviceManagementViewModel
import com.hereliesaz.blusnu.ui.gattfuzzing.GattFuzzingScreen
import com.hereliesaz.blusnu.ui.gattfuzzing.GattFuzzingViewModel
import com.hereliesaz.blusnu.ui.gattrelay.GattRelayScreen
import com.hereliesaz.blusnu.ui.gattrelay.GattRelayViewModel
import com.hereliesaz.blusnu.ui.geolocation.FindScreen
import com.hereliesaz.blusnu.ui.geolocation.FindViewModel
import com.hereliesaz.blusnu.ui.geolocation.GeolocationScreen
import com.hereliesaz.blusnu.ui.geolocation.GeolocationViewModel
import com.hereliesaz.blusnu.ui.keystrokeinjection.KeystrokeInjectionScreen
import com.hereliesaz.blusnu.ui.keystrokeinjection.KeystrokeInjectionViewModel
import com.hereliesaz.blusnu.ui.perfektblue.PerfektBlueScreen
import com.hereliesaz.blusnu.ui.perfektblue.PerfektBlueViewModel
import com.hereliesaz.blusnu.ui.reporting.ReportingScreen
import com.hereliesaz.blusnu.ui.rawcommands.RawCommandsScreen
import com.hereliesaz.blusnu.ui.magisk.MagiskScreen
import com.hereliesaz.blusnu.ui.magisk.MagiskViewModel
import com.hereliesaz.blusnu.ui.rawcommands.RawCommandsViewModel
import com.hereliesaz.blusnu.ui.reporting.ReportingViewModel
import com.hereliesaz.blusnu.ui.settings.SettingsScreen
import com.hereliesaz.blusnu.ui.settings.SettingsViewModel
import com.hereliesaz.blusnu.ui.smpbypass.SmpBypassScreen
import com.hereliesaz.blusnu.ui.smpbypass.SmpBypassViewModel
import com.hereliesaz.blusnu.ui.filetransfer.FileTransferScreen
import com.hereliesaz.blusnu.ui.filetransfer.FileTransferViewModel
import com.hereliesaz.blusnu.ui.hid.HidScreen
import com.hereliesaz.blusnu.ui.hid.HidViewModel
import com.hereliesaz.blusnu.ui.spoofing.SpoofingScreen
import com.hereliesaz.blusnu.ui.spoofing.SpoofingViewModel
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val deviceRepository by lazy { com.hereliesaz.blusnu.data.DeviceRepository(database.targetDeviceDao()) }
    private val savedSessionRepository by lazy { SavedSessionRepository(database.savedSessionDao()) }
    private val attackChainTemplateRepository by lazy { AttackChainTemplateRepository(database.attackChainTemplateDao()) }
    private val hardwareManager by lazy { HardwareManager(applicationContext) }
    private val bleHidController by lazy { com.hereliesaz.blusnu.data.BleHidController(applicationContext) }
    private val keystrokeInjectionModule by lazy { com.hereliesaz.blusnu.data.KeystrokeInjectionModule(bleHidController) }
    private val vulnerabilityCorrelator by lazy { VulnerabilityCorrelator(applicationContext) }
    private val httpClient by lazy {
        io.ktor.client.HttpClient(io.ktor.client.engine.android.Android) {
            install(ContentNegotiation) {
                json(kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    }
    private val macLookupClient by lazy { MacLookupClient(httpClient) }
    private val bluetoothLog by lazy { com.hereliesaz.blusnu.data.BluetoothLog() }
    private val bluetoothScanner: com.hereliesaz.blusnu.data.BluetoothScanner by lazy {
        com.hereliesaz.blusnu.data.BluetoothScanner(applicationContext, deviceRepository, bluetoothAdapter, bluetoothLog)
    }
    private val bluetoothAdapter by lazy { (getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter }

    private val viewModelFactory by lazy {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return when {
                    modelClass.isAssignableFrom(BluebuggingViewModel::class.java) -> {
                        BluebuggingViewModel(application, deviceRepository) as T
                    }
                    modelClass.isAssignableFrom(BlueSmackViewModel::class.java) -> {
                        val blueSmackModule = BlueSmackModule()
                        BlueSmackViewModel(application, deviceRepository, blueSmackModule) as T
                    }
                    modelClass.isAssignableFrom(BluesnarfingViewModel::class.java) -> {
                        BluesnarfingViewModel(application, deviceRepository) as T
                    }
                    modelClass.isAssignableFrom(GattFuzzingViewModel::class.java) -> {
                        GattFuzzingViewModel(application, deviceRepository) as T
                    }
                    modelClass.isAssignableFrom(BtlejackingViewModel::class.java) -> {
                        val btlejackingModule = BtlejackingModule(hardwareManager)
                        BtlejackingViewModel(application, hardwareManager, btlejackingModule, deviceRepository) as T
                    }
                    modelClass.isAssignableFrom(BtlejuiceViewModel::class.java) -> {
                        val btlejuiceModule = BtlejuiceModule(hardwareManager)
                        BtlejuiceViewModel(application, deviceRepository, hardwareManager, btlejuiceModule) as T
                    }
                    modelClass.isAssignableFrom(GeolocationViewModel::class.java) -> {
                        GeolocationViewModel(application, deviceRepository, bluetoothScanner) as T
                    }
                    modelClass.isAssignableFrom(FindViewModel::class.java) -> {
                        FindViewModel(application, deviceRepository, hardwareManager) as T
                    }
                    modelClass.isAssignableFrom(KeystrokeInjectionViewModel::class.java) -> {
                        KeystrokeInjectionViewModel(application, keystrokeInjectionModule, bleHidController, deviceRepository) as T
                    }
                    modelClass.isAssignableFrom(AttackChainingViewModel::class.java) -> {
                        val repository = com.hereliesaz.blusnu.data.AttackChainRepository(application)
                        AttackChainingViewModel(application, repository, deviceRepository, bluetoothAdapter, keystrokeInjectionModule) as T
                    }
                    modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                        DashboardViewModel(application, deviceRepository, savedSessionRepository, attackChainTemplateRepository) as T
                    }
                    modelClass.isAssignableFrom(ReportingViewModel::class.java) -> {
                        ReportingViewModel(application) as T
                    }
                    modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                        SettingsViewModel(application) as T
                    }
                    modelClass.isAssignableFrom(DeviceManagementViewModel::class.java) -> {
                        DeviceManagementViewModel(application, deviceRepository, vulnerabilityCorrelator, macLookupClient, bluetoothLog) as T
                    }
                    modelClass.isAssignableFrom(SpoofingViewModel::class.java) -> {
                        val spoofingModule = com.hereliesaz.blusnu.data.SpoofingModule()
                        SpoofingViewModel(application, spoofingModule, deviceRepository, hardwareManager) as T
                    }
                    modelClass.isAssignableFrom(RawCommandsViewModel::class.java) -> {
                        RawCommandsViewModel() as T
                    }
                    modelClass.isAssignableFrom(MagiskViewModel::class.java) -> {
                        MagiskViewModel() as T
                    }
                    modelClass.isAssignableFrom(BluetoothLogViewModel::class.java) -> {
                        BluetoothLogViewModel(application, bluetoothLog, deviceRepository) as T
                    }
                    modelClass.isAssignableFrom(HidViewModel::class.java) -> {
                        HidViewModel(application, bleHidController) as T
                    }
                    modelClass.isAssignableFrom(FileTransferViewModel::class.java) -> {
                        FileTransferViewModel(application) as T
                    }
                    modelClass.isAssignableFrom(BluffsViewModel::class.java) -> {
                        BluffsViewModel(deviceRepository, BluffsModule()) as T
                    }
                    modelClass.isAssignableFrom(BrakToothViewModel::class.java) -> {
                        BrakToothViewModel(deviceRepository, BrakToothModule(hardwareManager)) as T
                    }
                    modelClass.isAssignableFrom(BleSpamViewModel::class.java) -> {
                        BleSpamViewModel(BleSpamModule(applicationContext)) as T
                    }
                    modelClass.isAssignableFrom(GattRelayViewModel::class.java) -> {
                        GattRelayViewModel(application) as T
                    }
                    modelClass.isAssignableFrom(PerfektBlueViewModel::class.java) -> {
                        val perfektBlueModule = com.hereliesaz.blusnu.data.PerfektBlueModule(applicationContext)
                        PerfektBlueViewModel(deviceRepository, perfektBlueModule) as T
                    }
                    modelClass.isAssignableFrom(SmpBypassViewModel::class.java) -> {
                        SmpBypassViewModel(deviceRepository) as T
                    }
                    else -> throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }

    // Tracks whether all requested permissions were granted. Denials are not silently ignored.
    private var deniedPermissions: List<String> = emptyList()

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val denied = permissions.filterValues { !it }.keys.toList()
            deniedPermissions = denied
            if (denied.isNotEmpty()) {
                Log.w("MainActivity", "Permissions denied: ${denied.joinToString()}")
                Toast.makeText(
                    this,
                    "Some permissions were denied; scanning may not work: " +
                        denied.joinToString { it.substringAfterLast('.') },
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        try {
            vulnerabilityCorrelator.loadVulnerabilities()
        } catch (e: Exception) {
            // A missing or malformed vulnerabilities.json asset must not crash launch.
            Log.e("MainActivity", "Failed to load vulnerability database", e)
        }

        requestRequiredPermissions()

        setContent {
            BluSnuTheme {
                var showDisclaimer by remember { mutableStateOf(!getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE).getBoolean("disclaimer_accepted", false)) }

                if (showDisclaimer) {
                    DisclaimerDialog { agreed ->
                        if (agreed) {
                            getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE).edit {
                                putBoolean("disclaimer_accepted", true)
                            }
                            backupDatabaseIfConfigured()
                            showDisclaimer = false
                        } else {
                            // Declining the disclaimer closes the app -- the user cannot proceed.
                            finish()
                        }
                    }
                } else {
                    // --- System Requirements Check (5C) ---
                    val btManager = remember { getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
                    val locManager = remember { getSystemService(Context.LOCATION_SERVICE) as? LocationManager }

                    // Re-check each recomposition so the dialog disappears once the user enables them.
                    val isBtEnabled = btManager?.adapter?.isEnabled == true
                    val isLocEnabled = locManager?.let {
                        it.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                            it.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    } ?: false

                    val requirementsNotMet = !isBtEnabled || !isLocEnabled
                    var dismissedRequirements by remember { mutableStateOf(false) }

                    if (requirementsNotMet && !dismissedRequirements) {
                        SystemRequirementsDialog(
                            isBluetoothEnabled = isBtEnabled,
                            isLocationEnabled = isLocEnabled,
                            isDeveloperOptionsEnabled = true, // Not blocking on dev options
                            onEnableBluetooth = {
                                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            },
                            onEnableLocation = {
                                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            },
                            onEnableDeveloperOptions = {
                                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                            },
                            onDismiss = { dismissedRequirements = true }
                        )
                    }

                    val navController = rememberNavController()
                    val currentDestination by navController.currentBackStackEntryAsState()
                    val primaryColor = MaterialTheme.colorScheme.primary
                    AzHostActivityLayout(
                        navController = navController,
                        modifier = Modifier.fillMaxSize(),
                        currentDestination = currentDestination?.destination?.route,
                        initiallyExpanded = false
                    ) {
                        azConfig(
                            packButtons = true,
                            dockingSide = AzDockingSide.LEFT,
                            displayAppName = false
                        )
                        azTheme(activeColor = primaryColor)

                        // -- Overview --
                        azRailItem(id = "dashboard", text = "Dashboard", route = "dashboard")
                        azRailItem(id = "targets", text = "Targets", route = "targets")
                        azDivider()

                        // -- Classic Attacks --
                        azRailItem(id = "bluebugging", text = "Bluebugging", route = "bluebugging")
                        azRailItem(id = "bluesnarfing", text = "Snarfing", route = "bluesnarfing")
                        azRailItem(id = "bluesmack", text = "BlueSmack", route = "bluesmack")
                        azRailItem(id = "bluffs", text = "BLUFFS", route = "bluffs")
                        azRailItem(id = "braktooth", text = "BrakTooth", route = "braktooth")
                        azRailItem(id = "perfektblue", text = "PerfektBlue", route = "perfektblue")
                        azDivider()

                        // -- BLE Attacks --
                        azRailItem(id = "gattfuzzing", text = "Fuzzing", route = "gattfuzzing")
                        azRailItem(id = "blespam", text = "Spam", route = "blespam")
                        azRailItem(id = "gattrelay", text = "Relay", route = "gattrelay")
                        azRailItem(id = "smpbypass", text = "SMP Bypass", route = "smpbypass")
                        azRailItem(id = "btlejacking", text = "BtleJacking", route = "btlejacking")
                        azRailItem(id = "btlejuice", text = "BtleJuice", route = "btlejuice")
                        azDivider()

                        // -- Tools --
                        azRailItem(id = "spoofing", text = "Spoofing", route = "spoofing")
                        azRailItem(id = "keystroke_injection", text = "Injection", route = "keystroke_injection")
                        azRailItem(id = "hid", text = "HID", route = "hid")
                        azRailItem(id = "file_transfer", text = "Files", route = "file_transfer")
                        azDivider()

                        // -- Location --
                        azRailItem(id = "geolocation", text = "Location", route = "geolocation")
                        azDivider()

                        // -- Advanced --
                        azRailItem(id = "attack_chaining", text = "Chaining", route = "attack_chaining")
                        azRailItem(id = "raw_commands", text = "Commands", route = "raw_commands")
                        azRailItem(id = "magisk", text = "Magisk", route = "magisk")
                        azDivider()

                        // -- Info --
                        azRailItem(id = "reporting", text = "Reporting", route = "reporting")
                        azRailItem(id = "bluetooth_log", text = "BT Log", route = "bluetooth_log")
                        azRailItem(id = "settings", text = "Settings", route = "settings")

                        onscreen(Alignment.TopStart) {
                            AzNavHost(
                                startDestination = "dashboard"
                            ) {
                                composable("dashboard") {
                                    val viewModel: DashboardViewModel = viewModel(factory = viewModelFactory)
                                    val state by viewModel.state.collectAsState()
                                    DashboardScreen(
                                        bleDeviceCount = state.bleDeviceCount,
                                        classicDeviceCount = state.classicDeviceCount,
                                        devicesWithLocation = state.devicesWithLocation,
                                        savedSessions = state.savedSessions,
                                        attackChainTemplates = state.attackChainTemplates,
                                        activeTasks = state.activeTasks,
                                        onStartScanClicked = { navController.navigate("targets?startScan=true") },
                                        onSessionClicked = { /* Navigate to session detail -- future implementation */ },
                                        onTemplateClicked = { template ->
                                            // Navigate to Attack Chaining with template pre-loaded.
                                            navController.navigate("attack_chaining")
                                        }
                                    )
                                }
                                composable(
                                    "targets?startScan={startScan}",
                                    arguments = listOf(
                                        navArgument("startScan") {
                                            type = NavType.BoolType
                                            defaultValue = false
                                        }
                                    )
                                ) { backStackEntry ->
                                    val startScan = backStackEntry.arguments?.getBoolean("startScan") ?: false
                                    val viewModel: DeviceManagementViewModel = viewModel(factory = viewModelFactory)
                                    DeviceManagementScreen(
                                        viewModel = viewModel,
                                        startScan = startScan,
                                        onNavigateToAttack = { device, route ->
                                            if (route == "btlejuice") {
                                                val targetDeviceJson = Gson().toJson(device)
                                                navController.navigate("btlejuice?targetDevice=$targetDeviceJson")
                                            } else {
                                                navController.navigate(route)
                                            }
                                        }
                                    )
                                }
                                composable("settings") {
                                    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
                                    SettingsScreen(viewModel = viewModel)
                                }
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
                                composable(
                                    "btlejuice?targetDevice={targetDevice}",
                                    arguments = listOf(
                                        navArgument("targetDevice") {
                                            type = NavType.StringType
                                            nullable = true
                                        }
                                    )
                                ) { backStackEntry ->
                                    val targetDeviceJson = backStackEntry.arguments?.getString("targetDevice")
                                    val targetDevice = targetDeviceJson?.let { Gson().fromJson(it, TargetDevice::class.java) }
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
                                        onStartProxy = { it?.let { dev -> viewModel.onStartProxy(dev) } },
                                        onStopProxy = viewModel::onStopProxy,
                                        gattTraffic = gattTraffic
                                    )
                                }
                                composable("geolocation") {
                                    val geoViewModel: GeolocationViewModel = viewModel(factory = viewModelFactory)
                                    val findVm: FindViewModel = viewModel(factory = viewModelFactory)
                                    GeolocationScreen(viewModel = geoViewModel, findViewModel = findVm)
                                }
                                composable("keystroke_injection") {
                                    val viewModel: KeystrokeInjectionViewModel = viewModel(factory = viewModelFactory)
                                    val state by viewModel.state.collectAsState()
                                    KeystrokeInjectionScreen(
                                        state = state,
                                        onAttemptAttack = viewModel::onAttemptAttack,
                                        onSendKeystrokes = viewModel::onSendKeystrokes,
                                        onDeviceSelected = viewModel::onDeviceSelected,
                                        onRunDuckyScript = viewModel::onRunDuckyScript
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
                                composable("bluffs") {
                                    val viewModel: BluffsViewModel = viewModel(factory = viewModelFactory)
                                    BluffsScreen(viewModel = viewModel)
                                }
                                composable("braktooth") {
                                    val viewModel: BrakToothViewModel = viewModel(factory = viewModelFactory)
                                    BrakToothScreen(viewModel = viewModel)
                                }
                                composable("blespam") {
                                    val viewModel: BleSpamViewModel = viewModel(factory = viewModelFactory)
                                    BleSpamScreen(viewModel = viewModel)
                                }
                                composable("gattrelay") {
                                    val viewModel: GattRelayViewModel = viewModel(factory = viewModelFactory)
                                    GattRelayScreen(viewModel = viewModel)
                                }
                                composable("perfektblue") {
                                    val viewModel: PerfektBlueViewModel = viewModel(factory = viewModelFactory)
                                    PerfektBlueScreen(viewModel = viewModel)
                                }
                                composable("smpbypass") {
                                    val viewModel: SmpBypassViewModel = viewModel(factory = viewModelFactory)
                                    SmpBypassScreen(viewModel = viewModel)
                                }
                                composable("reporting") {
                                    val viewModel: ReportingViewModel = viewModel(factory = viewModelFactory)
                                    ReportingScreen(viewModel = viewModel)
                                }
                                composable("spoofing") {
                                    val viewModel: SpoofingViewModel = viewModel(factory = viewModelFactory)
                                    val state by viewModel.state.collectAsState()
                                    SpoofingScreen(
                                        state = state,
                                        onMacAddressChanged = viewModel::onMacAddressChanged,
                                        onApplyClicked = viewModel::onApplyClicked,
                                        onDeviceSelected = viewModel::onDeviceSelected,
                                        onStartMitmAttack = viewModel::onStartMitmAttack
                                    )
                                }
                                composable("bluetooth_log") {
                                    val viewModel: BluetoothLogViewModel = viewModel(factory = viewModelFactory)
                                    BluetoothLogScreen(viewModel = viewModel)
                                }
                                composable("raw_commands") {
                                    val viewModel: RawCommandsViewModel = viewModel(factory = viewModelFactory)
                                    RawCommandsScreen(viewModel)
                                }
                                composable("magisk") {
                                    // MagiskScreen is static informational content; MagiskViewModel is a
                                    // no-op, so no ViewModel is constructed here.
                                    MagiskScreen()
                                }
                                composable("hid") {
                                    val viewModel: HidViewModel = viewModel(factory = viewModelFactory)
                                    HidScreen(viewModel = viewModel)
                                }
                                composable("file_transfer") {
                                    val viewModel: FileTransferViewModel = viewModel(factory = viewModelFactory)
                                    FileTransferScreen(viewModel = viewModel)
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

    /**
     * Attempts a database backup only if the user has configured a real backup URL.
     *
     * The URL is read from the same SharedPreferences source that SettingsViewModel writes to
     * ("blusnu_prefs" / "backup_url"). If the URL is blank or still the placeholder
     * example.com value, the backup is skipped silently.
     */
    private fun backupDatabaseIfConfigured() {
        val backupUrl = getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE)
            .getString("backup_url", "")
            ?.trim()
            .orEmpty()

        if (backupUrl.isBlank() || backupUrl.contains("example.com")) {
            Log.i("MainActivity", "No real backup URL configured; skipping database backup.")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            CloudBackup(applicationContext, httpClient).backupDatabase(backupUrl)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    BluSnuTheme {
        DashboardScreen(
            bleDeviceCount = 0,
            classicDeviceCount = 0,
            devicesWithLocation = emptyList(),
            savedSessions = emptyList(),
            attackChainTemplates = emptyList(),
            onStartScanClicked = {}
        )
    }
}
