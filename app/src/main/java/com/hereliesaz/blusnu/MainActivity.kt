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
import com.hereliesaz.blusnu.ui.geolocation.FindScreen
import com.hereliesaz.blusnu.ui.geolocation.FindViewModel
import com.hereliesaz.blusnu.ui.keystrokeinjection.KeystrokeInjectionScreen
import com.hereliesaz.blusnu.ui.keystrokeinjection.KeystrokeInjectionViewModel
import com.hereliesaz.blusnu.ui.report.ReportScreen
import com.hereliesaz.blusnu.ui.rawcommands.RawCommandsScreen
import com.hereliesaz.blusnu.ui.magisk.MagiskScreen
import com.hereliesaz.blusnu.ui.magisk.MagiskViewModel
import com.hereliesaz.blusnu.ui.perfektblue.PerfektBlueScreen
import com.hereliesaz.blusnu.ui.perfektblue.PerfektBlueViewModel
import com.hereliesaz.blusnu.ui.rawcommands.RawCommandsViewModel
import com.hereliesaz.blusnu.ui.report.ReportViewModel
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

/**
 * [MainActivity] serves as the primary entry point and the Dependency Injection (DI) container
 * for the Blu Snu application.
 *
 * <p>
 * Responsibilities:
 * 1. <b>App Initialization:</b> Sets up the splash screen, theme, and basic UI structure.
 * 2. <b>Permission Management:</b> Requests runtime permissions for Bluetooth, Location, and Storage.
 * 3. <b>Dependency Injection:</b> Manually creates singleton instances of Repositories, Managers,
 *    and Attack Modules, and injects them into ViewModels via a custom Factory.
 * 4. <b>Navigation:</b> Hosts the [NavHost] and integrates it with the [AzNavRail] for side-bar navigation.
 * 5. <b>System Checks:</b> Monitors the status of Bluetooth, Location Services, Developer Options, and Root access.
 * </p>
 */
class MainActivity : AppCompatActivity() {

    // ---------------------------------------------------------------------------------------------
    // State Management
    // ---------------------------------------------------------------------------------------------

    /**
     * Tracks whether the user has granted all required Android runtime permissions.
     * Exposed as a [StateFlow] to trigger UI recomposition when permissions change.
     */
    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions

    // Internal state flows to track system requirements.
    // These are observed by the UI to show blocking dialogs if requirements aren't met.
    private val _isBluetoothEnabled = MutableStateFlow(false)
    private val _isLocationEnabled = MutableStateFlow(false)
    private val _isDeveloperOptionsEnabled = MutableStateFlow(false)
    private val _isRooted = MutableStateFlow(false)

    // ---------------------------------------------------------------------------------------------
    // Dependency Injection (Lazy Singletons)
    // ---------------------------------------------------------------------------------------------

    /**
     * The Room Database instance.
     * Initialized lazily to avoid startup performance hits until DB access is actually needed.
     */
    private val database by lazy { AppDatabase.getDatabase(this) }

    /**
     * Repository for managing discovered Bluetooth devices.
     * Acts as the single source of truth for device data across the app.
     */
    private val deviceRepository by lazy { com.hereliesaz.blusnu.data.DeviceRepository(database.targetDeviceDao()) }

    /**
     * Repository for persisting scan sessions for historical reporting.
     */
    private val savedSessionRepository by lazy { SavedSessionRepository(database.savedSessionDao()) }

    /**
     * Repository for managing Attack Chain templates (automated workflows).
     */
    private val attackChainTemplateRepository by lazy { AttackChainTemplateRepository(database.attackChainTemplateDao()) }

    /**
     * Manager for external hardware dongles (e.g., USB-OTG Bluetooth adapters).
     * Handles driver interaction and mode switching.
     */
    private val hardwareManager by lazy { HardwareManager() }

    // --- Attack Modules ---
    // Each module encapsulates the specific logic for a class of attacks.
    // They are instantiated here and passed to their respective ViewModels.

    private val btlejuiceModule by lazy { BtlejuiceModule(hardwareManager) }
    private val bleSpamModule by lazy { BleSpamModule(applicationContext) }
    private val bluffsModule by lazy { BluffsModule() }
    private val brakToothModule by lazy { BrakToothModule() }
    private val keystrokeInjectionModule by lazy { KeystrokeInjectionModule() }

    /**
     * Correlates discovered device OUI (MAC prefixes) and features with known CVEs.
     * Uses a local JSON database loaded from assets.
     */
    private val vulnerabilityCorrelator by lazy { VulnerabilityCorrelator(applicationContext) }

    /**
     * Ktor HTTP Client configuration.
     * Used for MAC address vendor lookups and cloud backups.
     */
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

    /**
     * The primary Bluetooth Scanner wrapper.
     * Handles the complexity of Android's [BluetoothLeScanner] and [BluetoothAdapter.startDiscovery].
     */
    private val bluetoothScanner: com.hereliesaz.blusnu.data.BluetoothScanner by lazy {
        com.hereliesaz.blusnu.data.BluetoothScanner(applicationContext, deviceRepository, bluetoothAdapter, bluetoothLog)
    }

    /**
     * Reference to the system Bluetooth Adapter.
     * Retrieved via [BluetoothManager] for API level compatibility.
     */
    private val bluetoothAdapter by lazy { (getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter }

    /**
     * Custom ViewModel Factory.
     * This is the core of the manual DI implementation. It intercepts ViewModel creation requests
     * and injects the singleton dependencies defined above into the ViewModel constructors.
     */
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
                        // Injecting BluffsModule for BLUFFS attack logic
                        BluffsViewModel(deviceRepository, bluffsModule) as T
                    }
                    modelClass.isAssignableFrom(BrakToothViewModel::class.java) -> {
                        // Injecting BrakToothModule for crash vectors
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
                    modelClass.isAssignableFrom(ReportViewModel::class.java) -> {
                        ReportViewModel(application, deviceRepository) as T
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

    /**
     * Activity Result Launcher for requesting multiple permissions at once.
     * Updates [_hasPermissions] state upon completion.
     */
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Check if all requested permissions were granted
            _hasPermissions.value = permissions.values.all { it }
        }

    /**
     * BroadcastReceiver to monitor Bluetooth state changes (ON/OFF).
     * This ensures the app can react immediately if the user toggles Bluetooth from the system shade.
     */
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                checkBluetoothState()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lifecycle Methods
    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initializes the mandatory Android Splash Screen (API 31+)
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hide the default Action Bar as we use a custom Jetpack Compose UI
        supportActionBar?.hide()

        // Asynchronously load the vulnerability database (CVEs)
        vulnerabilityCorrelator.loadVulnerabilities()

        // Kick off the startup checks
        requestRequiredPermissions()
        checkBluetoothState()

        // Register the receiver to listen for Bluetooth state changes while the app is running
        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        // Begin Jetpack Compose UI rendering
        setContent {
            // Collect system states as Compose State to trigger recompositions
            val hasPermissions by hasPermissions.collectAsState()
            val isBluetoothEnabled by _isBluetoothEnabled.collectAsState()
            val isLocationEnabled by _isLocationEnabled.collectAsState()
            val isDeveloperOptionsEnabled by _isDeveloperOptionsEnabled.collectAsState()
            val isRooted by _isRooted.collectAsState()

            // Poll for system settings changes that don't broadcast intents (like Location or Dev Options)
            // This ensures that if a user switches apps to enable settings and returns, we detect it immediately.
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

            // Apply the custom application theme
            BluSnuTheme {
                // Determine if the Legal Disclaimer has been accepted.
                // This is critical for ethical compliance.
                var showDisclaimer by remember { mutableStateOf(!getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE).getBoolean("disclaimer_accepted", false)) }

                if (showDisclaimer) {
                    DisclaimerDialog { agreed ->
                        if (agreed) {
                            // If agreed, attempt a backup (if configured) and save preference
                            performDatabaseBackup()
                        }
                        getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE).edit {
                            putBoolean(
                                "disclaimer_accepted",
                                true
                            )
                        }
                        showDisclaimer = false
                        checkSystemRequirements() // Re-check requirements after disclaimer is cleared
                    }
                } else if (!isBluetoothEnabled || !isLocationEnabled || !isDeveloperOptionsEnabled) {
                    // Show a blocking dialog if critical system requirements are missing
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
                    // -----------------------------------------------------------------------------
                    // Main UI Layout (AzNavRail + NavHost)
                    // -----------------------------------------------------------------------------
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()

                    // Normalize route so parameterized destinations like "btlejuice?targetDevice=..."
                    // match base routes such as "btlejuice" in the navigation rail for highlighting.
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
                             // Reserve top 10% vertical space for AzNavRail alignment in some layouts
                             val tenPercentHeight = maxHeight * 0.1f

                             Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = tenPercentHeight)
                             ) {
                                // -----------------------------------------------------------------
                                // Navigation Rail
                                // -----------------------------------------------------------------
                                AzNavRail(
                                    navController = navController,
                                    currentDestination = currentDestination,
                                    isLandscape = isLandscape,
                                    modifier = Modifier.zIndex(10f)
                                ) {
                                    azSettings(
                                        packRailButtons = true,
                                        displayAppNameInHeader = false,
                                        defaultShape = AzButtonShape.RECTANGLE // Enforced design system
                                    )

                                    // Dynamically build menu items from the MENU_CATEGORIES list
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

                                // -----------------------------------------------------------------
                                // Main Content Area
                                // -----------------------------------------------------------------
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = tenPercentHeight) // Additional padding for content alignment
                                ) {
                                    NavHost(
                                        navController = navController,
                                        startDestination = "dashboard"
                                    ) {
                                        // --- Dashboard ---
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

                                        // --- Device Management (Targets) ---
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
                                                // When a device is selected, navigate to the default attack view (Btlejuice here)
                                                val targetDeviceJson = Gson().toJson(it)
                                                navController.navigate("btlejuice?targetDevice=$targetDeviceJson")
                                            }
                                        }

                                        // --- Settings ---
                                        composable("settings") { SettingsScreen() }

                                        // --- Attack Screens ---

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
                                        composable("find") {
                                            val viewModel: FindViewModel = viewModel(factory = viewModelFactory)
                                            FindScreen(viewModel = viewModel, deviceRepository = deviceRepository)
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
                                        composable("report") {
                                            val viewModel: ReportViewModel = viewModel(factory = viewModelFactory)
                                            ReportScreen(viewModel = viewModel)
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
        // Always unregister receivers to prevent memory leaks
        unregisterReceiver(bluetoothReceiver)
    }

    // ---------------------------------------------------------------------------------------------
    // System Requirement Helpers
    // ---------------------------------------------------------------------------------------------

    private fun checkBluetoothState() {
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
    }

    /**
     * Aggregates checks for all mandatory system requirements.
     */
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

    /**
     * Checks if the device has Root access (su).
     * This is a non-blocking check performed on a background thread.
     */
    private fun checkRootAccess() {
        CoroutineScope(Dispatchers.IO).launch {
            _isRooted.value = isRootAvailable()
        }
    }

    /**
     * Executes a simple 'su' command to verify root privileges.
     * @return true if 'su -c id' executes successfully with exit code 0.
     */
    private fun isRootAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()

            // We must consume the stream to prevent blocking, even if we don't use the output.
            process.inputStream.use { it.readBytes() }

            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Requests the necessary Android runtime permissions.
     * Includes logic for older Android versions (pre-Q) which need explicit storage permissions.
     */
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
     * Triggers a cloud backup of the local database.
     * Called after the user accepts the disclaimer if a backup URL is configured.
     */
    private fun performDatabaseBackup() {
        val prefs = getSharedPreferences("blusnu_prefs", Context.MODE_PRIVATE)
        val backupUrl = prefs.getString("backup_url", "https://example.com/backup") ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val cloudBackup = CloudBackup(applicationContext, httpClient)
            val success = cloudBackup.backupDatabase(backupUrl)
            if (success) {
                // TODO: Replace print with a proper UI notification channel
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
