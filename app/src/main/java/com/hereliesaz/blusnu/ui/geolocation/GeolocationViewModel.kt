package com.hereliesaz.blusnu.ui.geolocation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BluetoothScanner
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

data class Location(val latitude: Double, val longitude: Double)

data class ObservationPoint(
    val location: Location,
    val distance: Double,
    val weight: Double
)

enum class WalkingGuidance {
    NOT_TRACKING,
    NEED_MORE_POINTS,
    CHANGE_DIRECTION,
    GOOD_ESTIMATE
}

data class GeolocationUiState(
    val devices: List<TargetDevice> = emptyList(),
    val userLocation: Location? = null,
    val isTracking: Boolean = false,
    val selectedDevice: TargetDevice? = null,
    val observationCount: Int = 0,
    val estimatedAccuracyMeters: Double? = null,
    val spatialSpread: Double = 0.0,
    val walkingGuidance: WalkingGuidance = WalkingGuidance.NOT_TRACKING,
    val estimatedDeviceLocation: Location? = null,
    val observationPositions: List<Location> = emptyList()
)

class GeolocationViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository,
    private val bluetoothScanner: BluetoothScanner
) : AndroidViewModel(application) {

    private val locationManager = LocationManager(application)
    private val geolocationModule = GeolocationModule()
    private val observations = mutableListOf<ObservationPoint>()

    private val _uiState = MutableStateFlow(GeolocationUiState())
    val uiState: StateFlow<GeolocationUiState> = _uiState.asStateFlow()

    private var currentUserLocation: Location? = null
    private var trackingJobs = mutableListOf<Job>()

    init {
        deviceRepository.allDevices
            .onEach { devices ->
                _uiState.value = _uiState.value.copy(devices = devices)
            }
            .launchIn(viewModelScope)
    }

    fun selectDevice(device: TargetDevice) {
        _uiState.value = _uiState.value.copy(selectedDevice = device)
    }

    fun startTracking() {
        val device = _uiState.value.selectedDevice ?: return
        observations.clear()
        geolocationModule.smoothRssi(device.macAddress, 0.0) // reset Kalman

        _uiState.value = _uiState.value.copy(
            isTracking = true,
            observationCount = 0,
            estimatedAccuracyMeters = null,
            spatialSpread = 0.0,
            walkingGuidance = WalkingGuidance.NEED_MORE_POINTS,
            estimatedDeviceLocation = null,
            observationPositions = emptyList()
        )

        // Fast GPS updates (2s interval)
        val locationJob = locationManager.locationFlow(2000L)
            .onEach { loc ->
                currentUserLocation = Location(loc.latitude, loc.longitude)
                _uiState.value = _uiState.value.copy(userLocation = currentUserLocation)
            }
            .launchIn(viewModelScope)
        trackingJobs.add(locationJob)

        // Start BLE scan and collect RSSI for selected device
        bluetoothScanner.startBleScan()

        val rssiJob = bluetoothScanner.rssiFlow
            .onEach { reading ->
                if (reading.macAddress == device.macAddress) {
                    onRssiReceived(device.macAddress, reading.rssi)
                }
            }
            .launchIn(viewModelScope)
        trackingJobs.add(rssiJob)
    }

    fun stopTracking() {
        trackingJobs.forEach { it.cancel() }
        trackingJobs.clear()
        bluetoothScanner.stopBleScan()

        _uiState.value = _uiState.value.copy(
            isTracking = false,
            walkingGuidance = if (_uiState.value.estimatedDeviceLocation != null)
                WalkingGuidance.GOOD_ESTIMATE
            else
                WalkingGuidance.NOT_TRACKING
        )
    }

    private fun onRssiReceived(macAddress: String, rssi: Int) {
        val userLoc = currentUserLocation ?: return

        val smoothedRssi = geolocationModule.smoothRssi(macAddress, rssi.toDouble())
        val distance = geolocationModule.calculateDistance(smoothedRssi)
        // Weight: closer readings are more reliable
        val weight = 1.0 / (distance * distance).coerceAtLeast(1.0)

        observations.add(ObservationPoint(userLoc, distance, weight))

        // Compute spatial spread in local meters
        val origin = observations.first().location
        val localPoints = observations.map { obs ->
            Trilateration.toLocalMeters(origin, obs.location)
        }
        val spread = geolocationModule.spatialSpread(localPoints)

        // Determine walking guidance
        val guidance = when {
            observations.size < 5 -> WalkingGuidance.NEED_MORE_POINTS
            spread < 10.0 -> WalkingGuidance.CHANGE_DIRECTION // < 10 m² hull area
            else -> WalkingGuidance.GOOD_ESTIMATE
        }

        // Run WLS if we have enough observations
        var estimatedLoc = _uiState.value.estimatedDeviceLocation
        var accuracy = _uiState.value.estimatedAccuracyMeters
        if (observations.size >= 3 && spread > 1.0) {
            val geoObs = observations.map { obs ->
                Triple(obs.location, obs.distance, obs.weight)
            }
            val result = Trilateration.weightedLeastSquaresGeo(origin, geoObs)
            if (result != null) {
                estimatedLoc = result
                // Estimate accuracy as weighted RMS residual
                val localResult = Trilateration.toLocalMeters(origin, result)
                var sumWeightedResidualSq = 0.0
                var sumWeights = 0.0
                for (obs in observations) {
                    val (ox, oy) = Trilateration.toLocalMeters(origin, obs.location)
                    val dx = localResult.first - ox
                    val dy = localResult.second - oy
                    val predictedDist = kotlin.math.sqrt(dx * dx + dy * dy)
                    val residual = predictedDist - obs.distance
                    sumWeightedResidualSq += obs.weight * residual * residual
                    sumWeights += obs.weight
                }
                accuracy = if (sumWeights > 0) kotlin.math.sqrt(sumWeightedResidualSq / sumWeights) else null

                // Save to DB
                _uiState.value.selectedDevice?.let { dev ->
                    val updated = dev.copy(latitude = result.latitude, longitude = result.longitude)
                    viewModelScope.launch { deviceRepository.insert(updated) }
                }
            }
        }

        _uiState.value = _uiState.value.copy(
            observationCount = observations.size,
            spatialSpread = spread,
            walkingGuidance = guidance,
            estimatedDeviceLocation = estimatedLoc,
            estimatedAccuracyMeters = accuracy,
            observationPositions = observations.map { it.location }
        )
    }

    override fun onCleared() {
        super.onCleared()
        trackingJobs.forEach { it.cancel() }
        trackingJobs.clear()
    }
}
