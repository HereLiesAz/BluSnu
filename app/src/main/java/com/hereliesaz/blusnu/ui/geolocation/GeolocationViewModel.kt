package com.hereliesaz.blusnu.ui.geolocation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.GeolocationModule
import com.hereliesaz.blusnu.data.LocationManager
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.utils.Trilateration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class Location(val latitude: Double, val longitude: Double)

data class LocationDataPoint(
    val location: Location,
    val rssi: Int
)

data class GeolocationUiState(
    val devices: List<TargetDevice> = emptyList(),
    val userLocation: Location? = null
)

class GeolocationViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val locationManager = LocationManager(application)
    private val geolocationModule = GeolocationModule()
    private val deviceRssiHistory = mutableMapOf<String, MutableList<LocationDataPoint>>()

    private val _uiState = MutableStateFlow(GeolocationUiState())
    val uiState: StateFlow<GeolocationUiState> = _uiState.asStateFlow()

    init {
        deviceRepository.allDevices
            .onEach { devices ->
                _uiState.value = _uiState.value.copy(devices = devices)
            }
            .launchIn(viewModelScope)

        locationManager.locationFlow()
            .onEach { location ->
                _uiState.value = _uiState.value.copy(userLocation = Location(location.latitude, location.longitude))
                _uiState.value.devices.forEach { device ->
                    onDeviceRssiUpdated(device, device.rssi)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onDeviceRssiUpdated(device: TargetDevice, rssi: Int) {
        val userLocation = _uiState.value.userLocation ?: return
        val smoothedRssi = geolocationModule.smoothRssi(device.macAddress, rssi.toDouble())
        val distance = geolocationModule.calculateDistance(smoothedRssi)

        val history = deviceRssiHistory.getOrPut(device.macAddress) { mutableListOf() }
        history.add(LocationDataPoint(userLocation, rssi))
        if (history.size > 10) {
            history.removeAt(0)
        }

        if (history.size >= 3) {
            val p1 = history[history.size - 1]
            val p2 = history[history.size - 2]
            val p3 = history[history.size - 3]

            // Check if the points are collinear
            val isCollinear = (p2.location.longitude - p1.location.longitude) * (p3.location.latitude - p1.location.latitude) ==
                    (p3.location.longitude - p1.location.longitude) * (p2.location.latitude - p1.location.latitude)

            if (isCollinear) {
                return
            }

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
