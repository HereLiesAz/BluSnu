package com.hereliesaz.blusnu.ui.geolocation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.hereliesaz.blusnu.data.GeolocationModule
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GeolocationUiState(
    val selectedDevice: TargetDevice? = null,
    val distance: Double = 0.0
)

class GeolocationViewModel(application: Application) : AndroidViewModel(application) {

    private val geolocationModule = GeolocationModule()

    private val _uiState = MutableStateFlow(GeolocationUiState())
    val uiState: StateFlow<GeolocationUiState> = _uiState.asStateFlow()

    fun onDeviceSelected(device: TargetDevice) {
        val distance = geolocationModule.calculateDistance(device.rssi.toDouble())
        _uiState.value = GeolocationUiState(selectedDevice = device, distance = distance)
    }
}
