package com.hereliesaz.blusnu.ui.geolocation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.hereliesaz.blusnu.data.GeolocationModule
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GeolocationUiState(
    val distances: Map<String, Double> = emptyMap()
)

class GeolocationViewModel(application: Application) : AndroidViewModel(application) {

    private val geolocationModule = GeolocationModule()

    private val _uiState = MutableStateFlow(GeolocationUiState())
    val uiState: StateFlow<GeolocationUiState> = _uiState.asStateFlow()

    fun onDeviceRssiUpdated(device: TargetDevice, rssi: Int) {
        val smoothedRssi = geolocationModule.smoothRssi(device.macAddress, rssi.toDouble())
        val distance = geolocationModule.calculateDistance(smoothedRssi)
        val updatedDistances = _uiState.value.distances.toMutableMap()
        updatedDistances[device.macAddress] = distance
        _uiState.value = GeolocationUiState(distances = updatedDistances)
    }
}
