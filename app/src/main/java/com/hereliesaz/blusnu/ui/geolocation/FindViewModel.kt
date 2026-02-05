package com.hereliesaz.blusnu.ui.geolocation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.CompassManager
import com.hereliesaz.blusnu.data.CooperativeTriangulation
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.GeolocationModule
import com.hereliesaz.blusnu.data.HardwareManager
import com.hereliesaz.blusnu.data.HardwareState
import com.hereliesaz.blusnu.data.LocationManager
import com.hereliesaz.blusnu.data.TandemData
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
import kotlin.math.abs

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
    val isTandemModeEnabled: Boolean = false,
    val peerLocation: Location? = null,
    val cooperativeLocation: Location? = null
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

    // 36 buckets for 10-degree segments (0-10, 10-20, ...).
    // Stores weighted probability of the signal coming from this direction.
    private val rssiBuckets = FloatArray(36) { 0f }

    init {
        // Observe devices.
        deviceRepository.allDevices
            .onEach { devices ->
                _uiState.value = _uiState.value.copy(devices = devices)
                // If currently selected device is updated (new RSSI), process it.
                _uiState.value.selectedDevice?.let { selected ->
                    val updated = devices.find { it.macAddress == selected.macAddress }
                    if (updated != null) {
                        if (updated.macAddress == selected.macAddress) {
                            if (updated.rssi != selected.rssi) {
                                onDeviceRssiUpdated(updated, updated.rssi)
                            }
                            _uiState.value = _uiState.value.copy(selectedDevice = updated)
                        } else {
                            selectDevice(updated)
                        }
                    }
                }
            }
            .launchIn(viewModelScope)

        // Observe hardware state (for Dual Dongle mode).
        hardwareManager.hardwareState
            .onEach { state ->
                _uiState.value = _uiState.value.copy(isUsbConnected = state == HardwareState.CONNECTED_DUAL)
            }
            .launchIn(viewModelScope)

        // Observe Tandem data from peers.
        tandemManager.tandemData
            .onEach { data ->
                val selected = _uiState.value.selectedDevice
                // If peer is tracking the same target.
                if (selected != null && data.targetMac == selected.macAddress) {
                    val peerLoc = Location(data.latitude, data.longitude)

                    _uiState.value = _uiState.value.copy(peerLocation = peerLoc)

                    val myLoc = _uiState.value.userLocation
                    val myDist = _uiState.value.rssiDistance

                    // If we have our own location and distance, we can triangulate.
                    if (myLoc != null && myDist != null) {
                         // Perform Circle-Circle intersection calculation.
                         val intersections = CooperativeTriangulation.calculateIntersections(
                             CooperativeTriangulation.GeoLocation(myLoc.latitude, myLoc.longitude),
                             myDist,
                             CooperativeTriangulation.GeoLocation(data.latitude, data.longitude),
                             data.distance
                         )

                         if (intersections.isNotEmpty()) {
                             // Disambiguate points:
                             // If we have a local estimated bearing, pick the intersection point closer to that bearing.
                             val best = if (intersections.size >= 2 && _uiState.value.estimatedBearing != null) {
                                 val bearing = _uiState.value.estimatedBearing!!
                                 val p1 = intersections[0]
                                 val p2 = intersections[1]

                                 val b1 = calculateBearing(myLoc.latitude, myLoc.longitude, p1.latitude, p1.longitude)
                                 val b2 = calculateBearing(myLoc.latitude, myLoc.longitude, p2.latitude, p2.longitude)

                                 val diff1 = abs(b1 - bearing).let { if (it > 180) 360 - it else it }
                                 val diff2 = abs(b2 - bearing).let { if (it > 180) 360 - it else it }

                                 if (diff1 < diff2) p1 else p2
                             } else {
                                 intersections.first()
                             }

                             _uiState.value = _uiState.value.copy(
                                 cooperativeLocation = Location(best.latitude, best.longitude)
                             )

                             // Update the device record in DB with the triangulated location.
                             val updatedDevice = selected.copy(latitude = best.latitude, longitude = best.longitude)
                             viewModelScope.launch {
                                 deviceRepository.insert(updatedDevice)
                             }
                         }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun selectDevice(device: TargetDevice?) {
        // Reset state if selection changes.
        if (device?.macAddress != _uiState.value.selectedDevice?.macAddress) {
            _uiState.value = _uiState.value.copy(
                selectedDevice = device,
                rssiDistance = null,
                estimatedBearing = null,
                peerLocation = null,
                cooperativeLocation = null
            )
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

        // Start location updates.
        locationJob = locationManager.locationFlow()
            .onEach { location ->
                _uiState.value = _uiState.value.copy(userLocation = Location(location.latitude, location.longitude))
                recalculateTargetData()
                broadcastTandemData()
            }
            .launchIn(viewModelScope)

        // Start compass updates.
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

    private fun broadcastTandemData() {
        if (_uiState.value.isTandemModeEnabled) {
            val userLoc = _uiState.value.userLocation
            val target = _uiState.value.selectedDevice
            val myDist = _uiState.value.rssiDistance

            if (userLoc != null && target != null && myDist != null) {
                val data = TandemData(
                    deviceName = android.os.Build.MODEL,
                    targetMac = target.macAddress,
                    rssi = target.rssi,
                    latitude = userLoc.latitude,
                    longitude = userLoc.longitude,
                    distance = myDist
                )
                tandemManager.broadcastData(data)
            }
        }
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

    /**
     * Haversine formula for distance between coordinates.
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3
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

    /**
     * Calculates initial bearing between two points.
     */
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) -
                sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)
        return ((Math.toDegrees(theta) + 360) % 360).toFloat()
    }

    /**
     * Called whenever a new RSSI reading comes in for the tracked device.
     */
    fun onDeviceRssiUpdated(device: TargetDevice, rssi: Int) {
        // 1. Smooth RSSI.
        val smoothedRssi = geolocationModule.smoothRssi(device.macAddress, rssi.toDouble())

        // 2. Calculate Distance.
        val distance = geolocationModule.calculateDistance(smoothedRssi)

        if (_uiState.value.selectedDevice?.macAddress == device.macAddress) {
            val updatedDevice = _uiState.value.selectedDevice?.copy(rssi = rssi)

            // 3. Get Secondary RSSI if dual dongle is active.
            val usbRssi = if (_uiState.value.isUsbConnected) {
                hardwareManager.getSecondaryRssi(device.macAddress)
            } else {
                null
            }

            // Average them if available.
            val combinedRssi = if (usbRssi != null) (rssi + usbRssi) / 2 else rssi

            // 4. Update Direction Finding Algorithm.
            updateDirectionProbability(combinedRssi)

            _uiState.value = _uiState.value.copy(
                rssiDistance = distance,
                selectedDevice = updatedDevice
            )

            // 5. Broadcast update if tandem is active.
            broadcastTandemData()
        }
    }

    override fun onCleared() {
        super.onCleared()
        tandemManager.stopSession()
    }

    /**
     * Probabilistic Direction Finding logic.
     * Maps current azimuth (where we are pointing) to RSSI strength.
     * Over time, builds a histogram where the peak indicates the target's bearing.
     */
    private fun updateDirectionProbability(rssi: Int) {
        val currentAzimuth = _uiState.value.currentAzimuth

        // Weight increases with signal strength (closer/stronger = higher confidence).
        val weight = ((rssi + 100).coerceIn(0, 70) / 70f)

        // Decay old values (moving average).
        for (i in rssiBuckets.indices) {
            rssiBuckets[i] *= 0.95f
        }

        // Add weight to the bucket corresponding to current azimuth.
        val positiveAzimuth = ((currentAzimuth % 360) + 360) % 360
        val index = (positiveAzimuth / 10).toInt().coerceIn(0, 35)
        rssiBuckets[index] += weight

        // Find peak.
        var maxVal = 0f
        var maxIndex = 0
        for (i in rssiBuckets.indices) {
            if (rssiBuckets[i] > maxVal) {
                maxVal = rssiBuckets[i]
                maxIndex = i
            }
        }

        // Threshold to avoid jitter when no signal.
        if (maxVal > 0.1f) {
            val estimatedBearing = maxIndex * 10f
            _uiState.value = _uiState.value.copy(estimatedBearing = estimatedBearing)
        }
    }
}
