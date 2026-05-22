package com.hereliesaz.blusnu

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.gson.Gson
import com.hereliesaz.aznavrail.*
import com.hereliesaz.aznavrail.model.AzDockingSide
import com.hereliesaz.blusnu.data.AppDatabase
import com.hereliesaz.blusnu.data.AttackChainTemplateRepository
import com.hereliesaz.blusnu.data.BtlejackingModule
import com.hereliesaz.blusnu.data.BtlejuiceModule
import com.hereliesaz.blusnu.data.CloudBackup
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.MacLookupClient
import com.hereliesaz.blusnu.data.SavedSessionRepository
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.data.VulnerabilityCorrelator
import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingScreen
import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingViewModel
import com.hereliesaz.blusnu.ui.bluetoothlog.BluetoothLogScreen
import com.hereliesaz.blusnu.ui.bluetoothlog.BluetoothLogViewModel
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
import com.hereliesaz.blusnu.ui.dashboard.DashboardScreen
import com.hereliesaz.blusnu.ui.dashboard.DashboardViewModel
import com.hereliesaz.blusnu.ui.devicemanagement.DeviceManagementScreen
import com.hereliesaz.blusnu.ui.devicemanagement.DeviceManagementViewModel
import com.hereliesaz.blusnu.ui.gattfuzzing.GattFuzzingScreen
import com.hereliesaz.blusnu.ui.gattfuzzing.GattFuzzingViewModel
import com.hereliesaz.blusnu.ui.geolocation.FindScreen
import com.hereliesaz.blusnu.ui.geolocation.FindViewModel
import com.hereliesaz.blusnu.ui.geolocation.GeolocationScreen
import com.hereliesaz.blusnu.ui.geolocation.GeolocationViewModel
import com.hereliesaz.blusnu.ui.keystrokeinjection.KeystrokeInjectionScreen
import com.hereliesaz.blusnu.ui.keystrokeinjection.KeystrokeInjectionViewModel
import com.hereliesaz.blusnu.ui.reporting.ReportingScreen
import com.hereliesaz.blusnu.ui.rawcommands.RawCommandsScreen
import com.hereliesaz.blusnu.ui.magisk.MagiskScreen
import com.hereliesaz.blusnu.ui.magisk.MagiskViewModel
import com.hereliesaz.blusnu.ui.rawcommands.RawCommandsViewModel
import com.hereliesaz.blusnu.ui.reporting.ReportingViewModel
import com.hereliesaz.blusnu.ui.settings.SettingsScreen
import com.hereliesaz.blusnu.ui.settings.SettingsViewModel
import com.hereliesaz.blusnu.ui.filetransfer.FileTransferScreen
import com.hereliesaz.blusnu.ui.filetransfer.FileTransferViewModel
import com.hereliesaz.blusnu.ui.hid.HidScreen
import com.hereliesaz.blusnu.ui.hid.HidViewModel
import com.hereliesaz.blusnu.ui.spoofing.SpoofingScreen
import com.hereliesaz.blusnu.ui.spoofing.SpoofingViewModel
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val deviceRepository by lazy { com.hereliesaz.blusnu.data.DeviceRepository(database.targetDeviceDao()) }
    private val savedSessionRepository by lazy { SavedSessionRepository(database.savedSessionDao()) }
    private val attackChainTemplateRepository by lazy { AttackChainTemplateRepository(database.attackChainTemplateDao()) }
    private val hardwareManager by lazy { HardwareManager() }
    private val btlejuiceModule by lazy { BtlejuiceModule(hardwareManager) }
    private val keystrokeInjectionModule by lazy { com.hereliesaz.blusnu.data.KeystrokeInjectionModule() }
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
                        BlueSmackViewModel(application, deviceRepository) as T
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
                        BtlejuiceViewModel(application, deviceRepository) as T
                    }
                    modelClass.isAssignableFrom(GeolocationViewModel::class.java) -> {
                        GeolocationViewModel(application, deviceRepository, bluetoothScanner) as T
                    }
                    modelClass.isAssignableFrom(FindViewModel::class.java) -> {
                        FindViewModel(application, deviceRepository, hardwareManager) as T
                    }
                    modelClass.isAssignableFrom(KeystrokeInjectionViewModel::class.java) -> {
                        KeystrokeInjectionViewModel(application, keystrokeInjectionModule, deviceRepository) as T
                    }
                    modelClass.isAssignableFrom(AttackChainingViewModel::class.java) -> {
                        val repository = com.hereliesaz.blusnu.data.AttackChainRepository(application)
                        AttackChainingViewModel(application, repository, deviceRepository) as T
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
                        HidViewModel(application) as T
                    }
                    modelClass.isAssignableFrom(FileTransferViewModel::class.java) -> {
                        FileTransferViewModel(application) as T
                    }
                    else -> throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Permission results handled by the system
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vulnerabilityCorrelator.loadVulnerabilities()

        requestRequiredPermissions()

        setContent {
            BluSnuTheme {
                var showDisclaimer by remember { mutableStateOf(!getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE).getBoolean("disclaimer_accepted", false)) }

                if (showDisclaimer) {
                    DisclaimerDialog { agreed ->
                        if (agreed) {
                            simulateDatabaseBackup()
                        }
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
                    val primaryColor = MaterialTheme.colorScheme.primary
                    AzHostActivityLayout(
                        navController = navController,
                        initiallyExpanded = false
                    ) {
                        azConfig(
                            packButtons = true,
                            dockingSide = AzDockingSide.LEFT,
                            displayAppName = false
                        )
                        azTheme(activeColor = primaryColor)

                        azRailItem(id = "dashboard", text = "Dashboard", route = "dashboard")
                        azRailItem(id = "targets", text = "Targets", route = "targets")
                        azRailItem(id = "bluebugging", text = "Bugging", route = "bluebugging")
                        azRailItem(id = "bluesnarfing", text = "Snarfing", route = "bluesnarfing")
                        azRailItem(id = "bluesmack", text = "Smack", route = "bluesmack")
                        azRailItem(id = "gattfuzzing", text = "Fuzzing", route = "gattfuzzing")
                        azRailItem(id = "spoofing", text = "Spoofing", route = "spoofing")
                        azRailItem(id = "keystroke_injection", text = "Injection", route = "keystroke_injection")
                        azRailItem(id = "btlejacking", text = "Jacking", route = "btlejacking")
                        azRailItem(id = "btlejuice", text = "Juice", route = "btlejuice")
                        azRailItem(id = "geolocation", text = "Location", route = "geolocation")
                        azRailItem(id = "find", text = "Find", route = "find")
                        azRailItem(id = "attack_chaining", text = "Chaining", route = "attack_chaining")
                        azRailItem(id = "raw_commands", text = "Commands", route = "raw_commands")
                        azRailItem(id = "magisk", text = "Magisk", route = "magisk")
                        azRailItem(id = "reporting", text = "Reporting", route = "reporting")
                        azRailItem(id = "settings", text = "Settings", route = "settings")
                        azRailItem(id = "bluetooth_log", text = "BT Log", route = "bluetooth_log")
                        azRailItem(id = "hid", text = "HID", route = "hid")
                        azRailItem(id = "file_transfer", text = "Files", route = "file_transfer")

                        onscreen {
                            AzNavHost(
                                startDestination = "dashboard",
                                navController = navController
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
                                        onStartScanClicked = { navController.navigate("targets") }
                                    )
                                }
                                composable("targets") {
                                    val viewModel: DeviceManagementViewModel = viewModel(factory = viewModelFactory)
                                    DeviceManagementScreen(viewModel = viewModel) {
                                        val targetDeviceJson = Gson().toJson(it)
                                        navController.navigate("btlejuice?targetDevice=$targetDeviceJson")
                                    }
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
                                        onStartProxy = { targetDevice?.let { viewModel.onStartProxy(it) } },
                                        onStopProxy = viewModel::onStopProxy,
                                        gattTraffic = gattTraffic
                                    )
                                }
                                composable("geolocation") {
                                    val viewModel: GeolocationViewModel = viewModel(factory = viewModelFactory)
                                    GeolocationScreen(viewModel = viewModel)
                                }
                                composable("find") {
                                    val viewModel: FindViewModel = viewModel(factory = viewModelFactory)
                                    FindScreen(viewModel = viewModel)
                                }
                                composable("keystroke_injection") {
                                    val viewModel: KeystrokeInjectionViewModel = viewModel(factory = viewModelFactory)
                                    val state by viewModel.state.collectAsState()
                                    KeystrokeInjectionScreen(
                                        state = state,
                                        onAttemptAttack = viewModel::onAttemptAttack,
                                        onSendKeystrokes = viewModel::onSendKeystrokes,
                                        onDeviceSelected = {
                                            Toast.makeText(applicationContext, "Device selected: ${it.name}", Toast.LENGTH_SHORT).show()
                                        }
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
                                    val viewModel: MagiskViewModel = viewModel(factory = viewModelFactory)
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

    private fun simulateDatabaseBackup() {
        // In a real app, this would connect to a cloud service and upload the database.
        // For now, we'll just log a message.
        CoroutineScope(Dispatchers.IO).launch {
            CloudBackup(applicationContext, httpClient).backupDatabase("https://example.com/backup")
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
