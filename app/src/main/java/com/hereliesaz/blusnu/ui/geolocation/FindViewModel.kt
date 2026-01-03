package com.hereliesaz.blusnu.ui.geolocation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.CompassManager
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.GeolocationModule
import com.hereliesaz.blusnu.data.LocationManager
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.utils.Trilateration
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

data class LocationDataPoint(
    val location: Location,
    val rssi: Int
)

data class FindUiState(
    val devices: List<TargetDevice> = emptyList(),
    val userLocation: Location? = null,
    val isTracking: Boolean = false,
    val currentAzimuth: Float = 0f,
    val selectedDevice: TargetDevice? = null,
    val distanceToTarget: Double? = null,
    val bearingToTarget: Float? = null,
    val isMetric: Boolean = false,
    val rssiDistance: Double? = null
)

class FindViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val locationManager = LocationManager(application)
    private val compassManager = CompassManager(application)
    private val geolocationModule = GeolocationModule()
    private val deviceRssiHistory = mutableMapOf<String, MutableList<LocationDataPoint>>()
    private val sharedPreferences = application.getSharedPreferences("blusnu_prefs", android.content.Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(FindUiState(isMetric = sharedPreferences.getBoolean("use_metric", false)))
    val uiState: StateFlow<FindUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null
    private var compassJob: Job? = null

    init {
        deviceRepository.allDevices
            .onEach { devices ->
                _uiState.value = _uiState.value.copy(devices = devices)
                // Re-select device to update its data if it changed
                _uiState.value.selectedDevice?.let { selected ->
                    val updated = devices.find { it.macAddress == selected.macAddress }
                    if (updated != null) {
                        selectDevice(updated)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun selectDevice(device: TargetDevice?) {
        _uiState.value = _uiState.value.copy(selectedDevice = device, rssiDistance = null)
        recalculateTargetData()
    }

    fun startTracking() {
        if (locationJob?.isActive == true) return

        _uiState.value = _uiState.value.copy(isTracking = true, isMetric = sharedPreferences.getBoolean("use_metric", false))

        locationJob = locationManager.locationFlow()
            .onEach { location ->
                _uiState.value = _uiState.value.copy(userLocation = Location(location.latitude, location.longitude))
                _uiState.value.devices.forEach { device ->
                    onDeviceRssiUpdated(device, device.rssi)
                }
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
            val bearing = calculateBearing(userLoc.latitude, userLoc.longitude, target.latitude, target.longitude)
            _uiState.value = _uiState.value.copy(distanceToTarget = dist, bearingToTarget = bearing)
        } else {
            // Keep existing data if we lose one input, or reset?
            // If target is null, reset. If userLoc is null (lost fix), maybe keep last?
            // For now, adhere to state correctness:
            if (target == null) {
                _uiState.value = _uiState.value.copy(distanceToTarget = null, bearingToTarget = null)
            }
            // If userLoc is null but target exists, we can't calc bearing.
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

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val lambda1 = Math.toRadians(lon1)
        val lambda2 = Math.toRadians(lon2)

        val y = sin(lambda2 - lambda1) * cos(phi2)
        val x = cos(phi1) * sin(phi2) -
                sin(phi1) * cos(phi2) * cos(lambda2 - lambda1)
        val theta = atan2(y, x)
        return ((Math.toDegrees(theta) + 360) % 360).toFloat()
    }

    fun onDeviceRssiUpdated(device: TargetDevice, rssi: Int) {
        val userLocation = _uiState.value.userLocation
        val smoothedRssi = geolocationModule.smoothRssi(device.macAddress, rssi.toDouble())
        val distance = geolocationModule.calculateDistance(smoothedRssi)

        if (_uiState.value.selectedDevice?.macAddress == device.macAddress) {
            _uiState.value = _uiState.value.copy(rssiDistance = distance)
        }

        if (userLocation != null) {
            val history = deviceRssiHistory.getOrPut(device.macAddress) { mutableListOf() }
            history.add(LocationDataPoint(userLocation, rssi))
            if (history.size > 10) {
                history.removeAt(0)
            }

            if (history.size >= 3) {
                // Trilateration logic
                val p1 = history[history.size - 1]
                val p2 = history[history.size - 2]
                val p3 = history[history.size - 3]

                // Simple check for movement to avoid collinear/degenerate cases
                val distMoved = calculateDistance(p1.location.latitude, p1.location.longitude, p3.location.latitude, p3.location.longitude)

                if (distMoved > 2.0) { // Require at least 2 meters movement
                     val d1 = geolocationModule.calculateDistance(p1.rssi.toDouble())
                     val d2 = geolocationModule.calculateDistance(p2.rssi.toDouble())
                     val d3 = geolocationModule.calculateDistance(p3.rssi.toDouble())

                     val estimatedLocation = Trilateration.calculate(p1.location, d1, p2.location, d2, p3.location, d3)
                     if (estimatedLocation != null) {
                         val updatedDevice = device.copy(latitude = estimatedLocation.latitude, longitude = estimatedLocation.longitude)
                         viewModelScope.launch {
                             deviceRepository.insert(updatedDevice)
                         }
                     }
                }
            }
        }
    }
}
