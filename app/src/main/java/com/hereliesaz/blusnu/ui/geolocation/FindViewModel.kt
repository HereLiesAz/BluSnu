package com.hereliesaz.blusnu.ui.geolocation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.CompassManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.GeolocationModule
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.LocationManager
import com.hereliesaz.blusnu.data.TandemManager
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class Location(val latitude: Double, val longitude: Double)

data class FindUiState(
    val devices: List<TargetDevice> = emptyList(),
    val userLocation: Location? = null,
    val isTracking: Boolean = false,
    val currentAzimuth: Float = 0f,
    val selectedDevice: TargetDevice? = null,
    val distanceToTarget: Double? = null, // GPS/Calculated distance
    val estimatedBearing: Float? = null, // RSSI Gradient Bearing (0-360)
    val isMetric: Boolean = false,
    val rssiDistance: Double? = null,
    val isUsbConnected: Boolean = false,
    val isTandemModeEnabled: Boolean = false
)

class FindViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository,
    private val hardwareManager: HardwareManager,
    private val locationManager: LocationManager = LocationManager(application),
    private val compassManager: CompassManager = CompassManager(application),
    private val tandemManager: TandemManager = TandemManager(application)
) : AndroidViewModel(application) {

    private val geolocationModule = GeolocationModule()
    private val sharedPreferences = application.getSharedPreferences("blusnu_prefs", android.content.Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(FindUiState(isMetric = sharedPreferences.getBoolean("use_metric", false)))
    val uiState: StateFlow<FindUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null
    private var compassJob: Job? = null

    // RSSI Buckets for Direction Finding (36 buckets of 10 degrees each)
    // Stores accumulated weight for each direction.
    private val rssiBuckets = FloatArray(36) { 0f }

    init {
        deviceRepository.allDevices
            .onEach { devices ->
                _uiState.value = _uiState.value.copy(devices = devices)
                _uiState.value.selectedDevice?.let { selected ->
                    val updated = devices.find { it.macAddress == selected.macAddress }
                    if (updated != null) {
                        // Reset state only if selecting a different device (different MAC)
                        // If same device, update data and trigger RSSI logic without wipe
                        if (updated.macAddress == selected.macAddress) {
                            if (updated.rssi != selected.rssi) {
                                onDeviceRssiUpdated(updated, updated.rssi)
                            }
                            // Keep reference updated
                            _uiState.value = _uiState.value.copy(selectedDevice = updated)
                        } else {
                            selectDevice(updated)
                        }
                    }
                }
            }
            .launchIn(viewModelScope)

        hardwareManager.hardwareState
            .onEach { state ->
                _uiState.value = _uiState.value.copy(isUsbConnected = state == HardwareState.CONNECTED_DUAL)
            }
            .launchIn(viewModelScope)

        tandemManager.tandemData
            .onEach { data ->
                val selected = _uiState.value.selectedDevice
                if (selected != null && data.deviceName == selected.name) { // Simple matching for simulation
                    // Incorporate tandem RSSI into probability buckets
                    updateDirectionProbability(data.rssi)
                }
            }
            .launchIn(viewModelScope)
    }

    fun selectDevice(device: TargetDevice?) {
        // Only wipe state if device actually changed
        if (device?.macAddress != _uiState.value.selectedDevice?.macAddress) {
            _uiState.value = _uiState.value.copy(selectedDevice = device, rssiDistance = null, estimatedBearing = null)
            // Reset buckets on new device
            for (i in rssiBuckets.indices) rssiBuckets[i] = 0f
        } else {
            _uiState.value = _uiState.value.copy(selectedDevice = device)
        }
        recalculateTargetData()
    }

    fun connectUsbDongle() {
        hardwareManager.connect()
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            hardwareManager.connectDual()
        }
    }

    fun toggleTandemMode() {
        val newState = !_uiState.value.isTandemModeEnabled
        _uiState.value = _uiState.value.copy(isTandemModeEnabled = newState)
        if (newState) {
            tandemManager.startSession()
        } else {
            tandemManager.stopSession()
        }
    }

    fun startTracking() {
        if (locationJob?.isActive == true) return

        _uiState.value = _uiState.value.copy(isTracking = true, isMetric = sharedPreferences.getBoolean("use_metric", false))

        locationJob = locationManager.locationFlow()
            .onEach { location ->
                _uiState.value = _uiState.value.copy(userLocation = Location(location.latitude, location.longitude))
                recalculateTargetData()
            }
            .launchIn(viewModelScope)

        compassJob = compassManager.azimuthFlow()
            .onEach { azimuth ->
                _uiState.value = _uiState.value.copy(currentAzimuth = azimuth)
            }
            .launchIn(viewModelScope)
    }

    fun stopTracking() {
        locationJob?.cancel()
        locationJob = null
        compassJob?.cancel()
        compassJob = null
        _uiState.value = _uiState.value.copy(isTracking = false)
    }


    private fun recalculateTargetData() {
        val userLoc = _uiState.value.userLocation
        val target = _uiState.value.selectedDevice

        if (userLoc != null && target != null && target.latitude != null && target.longitude != null) {
            val dist = calculateDistance(userLoc.latitude, userLoc.longitude, target.latitude, target.longitude)
            _uiState.value = _uiState.value.copy(distanceToTarget = dist)
        } else {
            if (target == null) {
                _uiState.value = _uiState.value.copy(distanceToTarget = null)
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3 // metres
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2) * sin(deltaLambda / 2)
        val c = 2 * atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return R * c
    }

    fun onDeviceRssiUpdated(device: TargetDevice, rssi: Int) {
        val smoothedRssi = geolocationModule.smoothRssi(device.macAddress, rssi.toDouble())
        val distance = geolocationModule.calculateDistance(smoothedRssi)

        // Always update RSSI distance for selected device, regardless of location fix
        if (_uiState.value.selectedDevice?.macAddress == device.macAddress) {
            val updatedDevice = _uiState.value.selectedDevice?.copy(rssi = rssi)

            // Fetch secondary RSSI from hardware manager if connected
            val usbRssi = if (_uiState.value.isUsbConnected) {
                hardwareManager.getSecondaryRssi(device.macAddress)
            } else {
                null
            }

            // Combine RSSI sources for direction finding weight
            val combinedRssi = if (usbRssi != null) (rssi + usbRssi) / 2 else rssi

            // --- Direction Finding Logic (Fuzzy/Gradient) ---
            updateDirectionProbability(combinedRssi)

            _uiState.value = _uiState.value.copy(
                rssiDistance = distance,
                selectedDevice = updatedDevice
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        tandemManager.stopSession()
    }

    private fun updateDirectionProbability(rssi: Int) {
        val currentAzimuth = _uiState.value.currentAzimuth
        // Normalize RSSI (-100 to -30) to weight (0.0 to 1.0)
        // We want stronger signal to have higher weight.
        val weight = ((rssi + 100).coerceIn(0, 70) / 70f)

        // Decay all buckets slightly
        for (i in rssiBuckets.indices) {
            rssiBuckets[i] *= 0.95f
        }

        // Add weight to current azimuth bucket
        // Azimuth is 0-360. Bucket index = azimuth / 10.
        // Ensure positive modulus for negative azimuths
        val positiveAzimuth = ((currentAzimuth % 360) + 360) % 360
        val index = (positiveAzimuth / 10).toInt().coerceIn(0, 35)
        rssiBuckets[index] += weight

        // Determine max bucket
        var maxIndex = 0
        var maxVal = 0f
        for (i in rssiBuckets.indices) {
            if (rssiBuckets[i] > maxVal) {
                maxVal = rssiBuckets[i]
                maxIndex = i
            }
        }

        // If we have enough signal data, update estimated bearing
        if (maxVal > 0.1f) {
            val estimatedBearing = maxIndex * 10f
            _uiState.value = _uiState.value.copy(estimatedBearing = estimatedBearing)
        }
    }
}
