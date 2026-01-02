package com.hereliesaz.blusnu

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.gson.Gson
import com.hereliesaz.aznavrail.AzNavRail
import com.hereliesaz.aznavrail.model.AzButtonShape
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
import com.hereliesaz.blusnu.ui.blespam.BleSpamScreen
import com.hereliesaz.blusnu.ui.blespam.BleSpamViewModel
import com.hereliesaz.blusnu.ui.bluebugging.BluebuggingScreen
import com.hereliesaz.blusnu.ui.bluebugging.BluebuggingViewModel
import com.hereliesaz.blusnu.ui.bluesmack.BlueSmackScreen
import com.hereliesaz.blusnu.ui.bluesmack.BlueSmackViewModel
import com.hereliesaz.blusnu.ui.bluesnarfing.BluesnarfingScreen
import com.hereliesaz.blusnu.ui.bluesnarfing.BluesnarfingViewModel
import com.hereliesaz.blusnu.ui.bluffs.BluffsScreen
import com.hereliesaz.blusnu.ui.bluffs.BluffsViewModel
import com.hereliesaz.blusnu.ui.braktooth.BrakToothScreen
import com.hereliesaz.blusnu.ui.braktooth.BrakToothViewModel
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
import com.hereliesaz.blusnu.ui.geolocation.GeolocationScreen
import com.hereliesaz.blusnu.ui.geolocation.GeolocationViewModel
import com.hereliesaz.blusnu.ui.keystrokeinjection.KeystrokeInjectionScreen
import com.hereliesaz.blusnu.ui.keystrokeinjection.KeystrokeInjectionViewModel
import com.hereliesaz.blusnu.ui.reporting.ReportingScreen
import com.hereliesaz.blusnu.ui.rawcommands.RawCommandsScreen
import com.hereliesaz.blusnu.ui.magisk.MagiskScreen
import com.hereliesaz.blusnu.ui.magisk.MagiskViewModel
import com.hereliesaz.blusnu.ui.perfektblue.PerfektBlueScreen
import com.hereliesaz.blusnu.ui.perfektblue.PerfektBlueViewModel
import com.hereliesaz.blusnu.ui.rawcommands.RawCommandsViewModel
import com.hereliesaz.blusnu.ui.reporting.ReportingViewModel
import com.hereliesaz.blusnu.ui.settings.SettingsScreen
import com.hereliesaz.blusnu.ui.settings.SettingsViewModel
import com.hereliesaz.blusnu.ui.smpbypass.SmpBypassScreen
import com.hereliesaz.blusnu.ui.smpbypass.SmpBypassViewModel
import com.hereliesaz.blusnu.ui.spoofing.SpoofingScreen
import com.hereliesaz.blusnu.ui.spoofing.SpoofingViewModel
import com.hereliesaz.blusnu.ui.MENU_CATEGORIES
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme
import com.hereliesaz.blusnu.BuildConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions

    private val _isBluetoothEnabled = MutableStateFlow(false)
    private val _isLocationEnabled = MutableStateFlow(false)
    private val _isDeveloperOptionsEnabled = MutableStateFlow(false)
    private val _isRooted = MutableStateFlow(false)

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val deviceRepository by lazy { com.hereliesaz.blusnu.data.DeviceRepository(database.targetDeviceDao()) }
    private val savedSessionRepository by lazy { SavedSessionRepository(database.savedSessionDao()) }
    private val attackChainTemplateRepository by lazy { AttackChainTemplateRepository(database.attackChainTemplateDao()) }
    private val hardwareManager by lazy { HardwareManager() }
    private val btlejuiceModule by lazy { BtlejuiceModule(hardwareManager) }
    private val bleSpamModule by lazy { com.hereliesaz.blusnu.data.BleSpamModule(applicationContext) }
    private val bluffsModule by lazy { com.hereliesaz.blusnu.data.BluffsModule() }
    private val brakToothModule by lazy { com.hereliesaz.blusnu.data.BrakToothModule() }
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
                    modelClass.isAssignableFrom(BluffsViewModel::class.java) -> {
                        BluffsViewModel(deviceRepository, bluffsModule) as T
                    }
                    modelClass.isAssignableFrom(BrakToothViewModel::class.java) -> {
                        BrakToothViewModel(deviceRepository, brakToothModule) as T
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
                    modelClass.isAssignableFrom(BleSpamViewModel::class.java) -> {
                        BleSpamViewModel(bleSpamModule) as T
                    }
                    modelClass.isAssignableFrom(GattRelayViewModel::class.java) -> {
                        GattRelayViewModel() as T
                    }
                    modelClass.isAssignableFrom(GeolocationViewModel::class.java) -> {
                        GeolocationViewModel(application, deviceRepository) as T
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
                        ReportingViewModel(application, deviceRepository) as T
                    }
                    modelClass.isAssignableFrom(BluetoothLogViewModel::class.java) -> {
                        BluetoothLogViewModel(application, bluetoothLog, deviceRepository) as T
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
                    modelClass.isAssignableFrom(PerfektBlueViewModel::class.java) -> {
                        PerfektBlueViewModel(deviceRepository) as T
                    }
                    modelClass.isAssignableFrom(SmpBypassViewModel::class.java) -> {
                        SmpBypassViewModel(deviceRepository) as T
                    }
                    else -> throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            _hasPermissions.value = permissions.values.all { it }
        }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                checkBluetoothState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        vulnerabilityCorrelator.loadVulnerabilities()

        requestRequiredPermissions()
        checkBluetoothState()
        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        setContent {
            val hasPermissions by hasPermissions.collectAsState()
            val isBluetoothEnabled by _isBluetoothEnabled.collectAsState()
            val isLocationEnabled by _isLocationEnabled.collectAsState()
            val isDeveloperOptionsEnabled by _isDeveloperOptionsEnabled.collectAsState()
            val isRooted by _isRooted.collectAsState()

            // Poll for system settings changes that don't broadcast intent
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        checkSystemRequirements()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            BluSnuTheme {
                var showDisclaimer by remember { mutableStateOf(!getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE).getBoolean("disclaimer_accepted", false)) }

                if (showDisclaimer) {
                    DisclaimerDialog { agreed ->
                        if (agreed) {
                            performDatabaseBackup()
                        }
                        getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE).edit {
                            putBoolean(
                                "disclaimer_accepted",
                                true
                            )
                        }
                        showDisclaimer = false
                        checkSystemRequirements() // Re-check after disclaimer
                    }
                } else if (!isBluetoothEnabled || !isLocationEnabled || !isDeveloperOptionsEnabled) {
                    SystemRequirementsDialog(
                        isBluetoothEnabled = isBluetoothEnabled,
                        isLocationEnabled = isLocationEnabled,
                        isDeveloperOptionsEnabled = isDeveloperOptionsEnabled,
                        onEnableBluetooth = {
                            try {
                                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                startActivity(enableBtIntent)
                            } catch (e: SecurityException) {
                                Toast.makeText(applicationContext, "Permission denied to enable Bluetooth", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onEnableLocation = {
                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                        onEnableDeveloperOptions = {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                        }
                    )
                } else {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    // Normalize route so parameterized destinations like "btlejuice?targetDevice=..."
                    // match base routes such as "btlejuice" in the navigation rail.
                    val currentDestination = navBackStackEntry
                        ?.destination
                        ?.route
                        ?.substringBefore("?")
                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                             val tenPercentHeight = maxHeight * 0.1f

                             Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = tenPercentHeight)
                             ) {
                                AzNavRail(
                                    navController = navController,
                                    currentDestination = currentDestination,
                                    isLandscape = isLandscape,
                                    modifier = Modifier.zIndex(10f)
                                ) {
                                    azSettings(
                                        packRailButtons = true,
                                        displayAppNameInHeader = false,
                                        defaultShape = AzButtonShape.RECTANGLE
                                    )

                                    MENU_CATEGORIES.forEach { category ->
                                        azRailHostItem(id = category.id, text = category.text, onClick = {})
                                        category.items.forEach { item ->
                                            azRailSubItem(
                                                id = item.id,
                                                hostId = category.id,
                                                text = item.text,
                                                route = item.route
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = tenPercentHeight) // 20% total padding for content (10% from Row + 10% here)
                                ) {
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
                                                devicesWithLocation = state.devicesWithLocation,
                                                savedSessions = state.savedSessions,
                                                attackChainTemplates = state.attackChainTemplates,
                                                onStartScanClicked = { navController.navigate("targets?startScan=true") }
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
                                            DeviceManagementScreen(viewModel = viewModel, startScan = startScan) {
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
                                            com.hereliesaz.blusnu.ui.bluesnarfing.BluesnarfingScreen(viewModel = viewModel, hasPermissions = hasPermissions)
                                        }
                                        composable("bluffs") {
                                            val viewModel: BluffsViewModel = viewModel(factory = viewModelFactory)
                                            BluffsScreen(viewModel = viewModel)
                                        }
                                        composable("braktooth") {
                                            val viewModel: BrakToothViewModel = viewModel(factory = viewModelFactory)
                                            BrakToothScreen(viewModel = viewModel)
                                        }
                                        composable("btlejacking") {
                                            val viewModel: BtlejackingViewModel = viewModel(factory = viewModelFactory)
                                            BtlejackingScreen(viewModel = viewModel, hasPermissions = hasPermissions)
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
                                            GeolocationScreen(viewModel = viewModel, deviceRepository = deviceRepository)
                                        }
                                        composable("keystroke_injection") {
                                            val viewModel: KeystrokeInjectionViewModel = viewModel(factory = viewModelFactory)
                                            val state by viewModel.state.collectAsState()
                                            KeystrokeInjectionScreen(
                                                state = state,
                                                onAttemptAttack = viewModel::onAttemptAttack,
                                                onSendKeystrokes = viewModel::onSendKeystrokes,
                                                onRunDuckyScript = viewModel::onRunDuckyScript,
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
                                        composable("blespam") {
                                            val viewModel: BleSpamViewModel = viewModel(factory = viewModelFactory)
                                            BleSpamScreen(viewModel = viewModel)
                                        }
                                        composable("gattrelay") {
                                            val viewModel: GattRelayViewModel = viewModel(factory = viewModelFactory)
                                            GattRelayScreen(viewModel = viewModel)
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
                                        composable("perfektblue") {
                                            val viewModel: PerfektBlueViewModel = viewModel(factory = viewModelFactory)
                                            PerfektBlueScreen(viewModel = viewModel)
                                        }
                                        composable("smpbypass") {
                                            val viewModel: SmpBypassViewModel = viewModel(factory = viewModelFactory)
                                            SmpBypassScreen(viewModel = viewModel)
                                        }
                                        composable("bluetooth_log") {
                                            val viewModel: BluetoothLogViewModel = viewModel(factory = viewModelFactory)
                                            BluetoothLogScreen(
                                                viewModel = viewModel
                                            )
                                        }
                                        composable("raw_commands") {
                                            val viewModel: RawCommandsViewModel = viewModel(factory = viewModelFactory)
                                            RawCommandsScreen(viewModel)
                                        }
                                        composable("magisk") {
                                            val viewModel: MagiskViewModel = viewModel(factory = viewModelFactory)
                                            MagiskScreen()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
    }

    private fun checkBluetoothState() {
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
    }

    private fun checkSystemRequirements() {
        checkBluetoothState()

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        _isLocationEnabled.value = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val devOptions = Settings.Global.getInt(
            contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        )
        _isDeveloperOptionsEnabled.value = devOptions == 1

        checkRootAccess()
    }

    private fun checkRootAccess() {
        CoroutineScope(Dispatchers.IO).launch {
            _isRooted.value = isRootAvailable()
        }
    }

    private fun isRootAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()

            // We must consume the stream to prevent blocking
            process.inputStream.use { it.readBytes() }

            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
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

    private fun performDatabaseBackup() {
        val prefs = getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE)
        val backupUrl = prefs.getString("backup_url", "https://example.com/backup") ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val cloudBackup = CloudBackup(applicationContext, httpClient)
            val success = cloudBackup.backupDatabase(backupUrl)
            if (success) {
                // Ideally show a notification or toast on Main thread
                println("Backup successful")
            } else {
                println("Backup failed")
            }
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
