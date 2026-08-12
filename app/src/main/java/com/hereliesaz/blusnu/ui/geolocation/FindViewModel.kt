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
import com.hereliesaz.blusnu.data.Location
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

/**
 * UI State for the Find (Geolocation) screen.
 *
 * @property devices List of available devices to track.
 * @property userLocation The local user's current GPS location.
 * @property isTracking True if the active tracking session (GPS + Compass) is running.
 * @property currentAzimuth The direction the phone is currently pointing (0-360 degrees).
 * @property selectedDevice The specific target being tracked.
 * @property distanceToTarget Calculated distance based on known lat/long coordinates (if available).
 * @property estimatedBearing The calculated probability bearing of the target based on RSSI/Rotation history.
 * @property isMetric Preference for unit display.
 * @property rssiDistance The theoretical distance calculated purely from signal strength (Path Loss Model).
 * @property isUsbConnected True if an external directional antenna/dongle is attached.
 * @property isTandemModeEnabled True if Cooperative Triangulation is active.
 * @property peerLocation The GPS location of the tandem partner.
 * @property cooperativeLocation The intersection point calculated from tandem data.
 */
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

/**
 * ViewModel for the Geolocation/Find feature.
 *
 * <p>
 * This ViewModel orchestrates the complex logic required for physical device localization without
 * requiring the target to have GPS. It combines:
 * 1. <b>RSSI Path Loss Modeling:</b> Estimating distance based on signal fade.
 * 2. <b>Fuzzy Logic Direction Finding:</b> Correlating signal strength peaks with the phone's compass azimuth
 *    to guess the direction of the source.
 * 3. <b>Cooperative Triangulation (Tandem):</b> Using P2P communication with another instance of the app
 *    to triangulate the target using circle intersection.
 * </p>
 */
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

    // Backing property for UI State
    private val _uiState = MutableStateFlow(FindUiState(isMetric = sharedPreferences.getBoolean("use_metric", false)))
    val uiState: StateFlow<FindUiState> = _uiState.asStateFlow()

    // Jobs for managing location/sensor streams
    private var locationJob: Job? = null
    private var compassJob: Job? = null

    /**
     * An array of 36 buckets (one per 10 degrees).
     * Used to accumulate RSSI weights to determine the probable direction of the signal.
     */
    private val rssiBuckets = FloatArray(36) { 0f }

    init {
        // Observe the database for device updates (new RSSI values, new devices)
        deviceRepository.allDevices
            .onEach { devices ->
                _uiState.value = _uiState.value.copy(devices = devices)

                // If we are tracking a specific device, ensure we have its latest state
                _uiState.value.selectedDevice?.let { selected ->
                    val updated = devices.find { it.macAddress == selected.macAddress }
                    if (updated != null) {
                        if (updated.macAddress == selected.macAddress) {
                            // If the RSSI has changed, update our tracking logic
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

        // Observe Hardware Manager for external dongle connection state
        hardwareManager.hardwareState
            .onEach { state ->
                _uiState.value = _uiState.value.copy(isUsbConnected = state == HardwareState.CONNECTED_DUAL)
            }
            .launchIn(viewModelScope)

        // Observe Tandem Data (Peer locations and ranges)
        tandemManager.tandemData
            .onEach { data ->
                val selected = _uiState.value.selectedDevice
                // Only process data relevant to our currently selected target
                if (selected != null && data.targetMac == selected.macAddress) {
                    val peerLoc = Location(data.latitude, data.longitude)

                    _uiState.value = _uiState.value.copy(peerLocation = peerLoc)

                    val myLoc = _uiState.value.userLocation
                    val myDist = _uiState.value.rssiDistance

                    // If we have our own location and distance estimate, calculate intersection with peer
                    if (myLoc != null && myDist != null) {
                         // Perform Circle-Circle intersection calculation.
                         val intersections = CooperativeTriangulation.calculateIntersections(
                             CooperativeTriangulation.GeoLocation(myLoc.latitude, myLoc.longitude),
                             myDist,
                             CooperativeTriangulation.GeoLocation(data.latitude, data.longitude),
                             data.distance
                         )

                         if (intersections.isNotEmpty()) {
                             // Ambiguity Resolution: Two circles intersect at two points.
                             // We use our local estimated bearing (compass) to pick the point that aligns best.
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

                             // Persist the triangulated location to the DB so it shows on the map permanently.
                             // NOTE: This tandem/cooperative view and the single-user GeolocationViewModel
                             // are two intentional geolocation stacks that both persist device lat/long.
                             // They upsert by MAC (last write wins); this is expected, not a conflict.
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

    /**
     * Sets the target device for tracking. Resets tracking buckets.
     */
    fun selectDevice(device: TargetDevice?) {
        // Reset state if selection changes.
        if (device?.macAddress != _uiState.value.selectedDevice?.macAddress) {
            // New device selected, clear previous tracking data
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
            hardwareManager.connectDual() // Attempt to enable secondary antenna
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

    /**
     * Begins listening to GPS and Compass sensors.
     */
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

    /**
     * Pushes local telemetry (My Location + My Distance to Target) to the tandem partner.
     */
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
     * Haversine formula to calculate great-circle distance between two points.
     * @param lat1 Latitude of Point 1
     * @param lon1 Longitude of Point 1
     * @param lat2 Latitude of Point 2
     * @param lon2 Longitude of Point 2
     * @return Distance in meters
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3 // Earth radius in meters
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
     * Calculates the bearing from Point A to Point B.
     *
     * Uses the forward azimuth formula:
     * θ = atan2( sin Δλ ⋅ cos φ₂, cos φ₁ ⋅ sin φ₂ − sin φ₁ ⋅ cos φ₂ ⋅ cos Δλ )
     *
     * @param lat1 Latitude of Point 1
     * @param lon1 Longitude of Point 1
     * @param lat2 Latitude of Point 2
     * @param lon2 Longitude of Point 2
     * @return Bearing in degrees (0-360)
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
     * Called when the scanner reports a new RSSI for the tracked device.
     */
    fun onDeviceRssiUpdated(device: TargetDevice, rssi: Int) {
        // Smooth the noisy RSSI signal using a Kalman filter (in GeolocationModule)
        val smoothedRssi = geolocationModule.smoothRssi(device.macAddress, rssi.toDouble())

        // Convert RSSI to Meters
        val distance = geolocationModule.calculateDistance(smoothedRssi)

        if (_uiState.value.selectedDevice?.macAddress == device.macAddress) {
            val updatedDevice = _uiState.value.selectedDevice?.copy(rssi = rssi)

            viewModelScope.launch {
                // If a secondary hardware antenna is used, fuse the data
                val usbRssi = if (_uiState.value.isUsbConnected) {
                    hardwareManager.getSecondaryRssi(device.macAddress)
                } else {
                    null
                }

                // Average them if available.
                val combinedRssi = if (usbRssi != null) (rssi + usbRssi) / 2 else rssi

                // Update the probability buckets for direction finding
                updateDirectionProbability(combinedRssi)

                _uiState.value = _uiState.value.copy(
                    rssiDistance = distance,
                    selectedDevice = updatedDevice
                )

                // 5. Broadcast update if tandem is active.
                broadcastTandemData()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tandemManager.stopSession()
    }

    /**
     * Updates the directional probability buckets based on current azimuth and signal strength.
     *
     * Logic:
     * 1. Decay all existing buckets (old data becomes less relevant).
     * 2. Identify the current direction (Compass Azimuth).
     * 3. Add a weight to the current direction's bucket proportional to the signal strength.
     *    (Stronger signal = Higher probability that the target is in this direction).
     */
    private fun updateDirectionProbability(rssi: Int) {
        val currentAzimuth = _uiState.value.currentAzimuth
        // Normalize RSSI (-100 to -30) to a weight (0.0 to 1.0 approx)
        val weight = ((rssi + 100).coerceIn(0, 70) / 70f)

        // Decay factor
        for (i in rssiBuckets.indices) {
            rssiBuckets[i] *= 0.95f
        }

        // Add weight to the bucket corresponding to the current phone orientation
        val positiveAzimuth = ((currentAzimuth % 360) + 360) % 360
        val index = (positiveAzimuth / 10).toInt().coerceIn(0, 35)
        rssiBuckets[index] += weight

        // Find the bucket with the highest accumulated weight
        var maxVal = 0f
        var maxIndex = 0
        for (i in rssiBuckets.indices) {
            if (rssiBuckets[i] > maxVal) {
                maxVal = rssiBuckets[i]
                maxIndex = i
            }
        }

        // Only update the estimate if confidence is high enough
        if (maxVal > 0.1f) {
            val estimatedBearing = maxIndex * 10f
            _uiState.value = _uiState.value.copy(estimatedBearing = estimatedBearing)
        }
    }
}
