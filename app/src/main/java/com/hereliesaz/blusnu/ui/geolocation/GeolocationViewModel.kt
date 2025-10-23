package com.hereliesaz.blusnu.ui.geolocation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.hereliesaz.blusnu.data.DeviceRepository
import com.hereliesaz.blusnu.data.GeolocationModule
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.utils.Trilateration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class LocationDataPoint(
    val location: LatLng,
    val rssi: Int
)

data class GeolocationUiState(
    val devices: List<TargetDeviceWithLocation> = emptyList()
)

data class TargetDeviceWithLocation(
    val device: TargetDevice,
    val estimatedLocation: LatLng? = null
)

class GeolocationViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val geolocationModule = GeolocationModule()
    private val deviceRssiHistory = mutableMapOf<String, MutableList<LocationDataPoint>>()

    private val _uiState = MutableStateFlow(GeolocationUiState())
    val uiState: StateFlow<GeolocationUiState> = _uiState.asStateFlow()

    init {
        deviceRepository.discoveredDevices
            .onEach { devices ->
                val devicesWithLocation = devices.map { device ->
                    TargetDeviceWithLocation(
                        device = device,
                        estimatedLocation = _uiState.value.devices.find { it.device.macAddress == device.macAddress }?.estimatedLocation
                    )
                }
                _uiState.value = _uiState.value.copy(devices = devicesWithLocation)
            }
            .launchIn(viewModelScope)
    }

    fun onDeviceRssiUpdated(device: TargetDevice, rssi: Int, userLocation: LatLng) {
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

            val d1 = geolocationModule.calculateDistance(p1.rssi.toDouble())
            val d2 = geolocationModule.calculateDistance(p2.rssi.toDouble())
            val d3 = geolocationModule.calculateDistance(p3.rssi.toDouble())

            val estimatedLocation = Trilateration.calculate(p1.location, d1, p2.location, d2, p3.location, d3)
            if (estimatedLocation != null) {
                deviceRepository.updateDeviceLocation(device.macAddress, estimatedLocation)
            }
        }
    }
}
