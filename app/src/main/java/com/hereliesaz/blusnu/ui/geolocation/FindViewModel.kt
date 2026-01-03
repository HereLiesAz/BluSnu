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
    val rssiDistance: Double? = null,
    val recordedLocations: List<LocationDataPoint> = emptyList()
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
        _uiState.value = _uiState.value.copy(selectedDevice = device, rssiDistance = null, recordedLocations = emptyList())
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

    fun recordCurrentLocation() {
        val userLocation = _uiState.value.userLocation
        val selectedDevice = _uiState.value.selectedDevice ?: return

        // Use the latest smoothed RSSI if available via distance calc, or fallback to device RSSI.
        // However, since we don't store smoothed RSSI directly, we should trust the `selectedDevice` state
        // which we update in onDeviceRssiUpdated.
        val rssi = selectedDevice.rssi

        if (userLocation != null) {
            val newPoint = LocationDataPoint(userLocation, rssi)
            val currentList = _uiState.value.recordedLocations.toMutableList()
            if (currentList.size < 3) {
                currentList.add(newPoint)
                _uiState.value = _uiState.value.copy(recordedLocations = currentList)

                if (currentList.size == 3) {
                    calculateTriangulation(currentList, selectedDevice)
                }
            }
        }
    }

    fun clearRecordedLocations() {
        _uiState.value = _uiState.value.copy(recordedLocations = emptyList())
    }

    private fun calculateTriangulation(points: List<LocationDataPoint>, device: TargetDevice) {
         if (points.size != 3) return

         val p1 = points[0]
         val p2 = points[1]
         val p3 = points[2]

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
        val smoothedRssi = geolocationModule.smoothRssi(device.macAddress, rssi.toDouble())
        val distance = geolocationModule.calculateDistance(smoothedRssi)

        if (_uiState.value.selectedDevice?.macAddress == device.macAddress) {
            // Update the selected device with the new RSSI so we have the latest value for recording
            val updatedDevice = _uiState.value.selectedDevice?.copy(rssi = rssi)
            _uiState.value = _uiState.value.copy(
                rssiDistance = distance,
                selectedDevice = updatedDevice
            )
        }
    }
}
