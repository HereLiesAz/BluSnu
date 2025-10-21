package com.hereliesaz.blusnu.ui.spoofing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.SpoofingModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpoofingState(
    val logMessages: List<String> = emptyList(),
    val isError: Boolean = false
)

class SpoofingViewModel(
    application: Application,
    private val spoofingModule: SpoofingModule
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SpoofingState())
    val state: StateFlow<SpoofingState> = _state.asStateFlow()

    private var macAddress: String = ""

    fun onMacAddressChanged(newMacAddress: String) {
        macAddress = newMacAddress
        _state.update { it.copy(isError = !isValidMacAddress(newMacAddress)) }
    }

    fun onApplyClicked() {
        viewModelScope.launch {
            log("Applying new MAC address: $macAddress")
            val success = spoofingModule.spoofMacAddress(macAddress)
            if (success) {
                log("Successfully changed MAC address to $macAddress")
            } else {
                log("Failed to change MAC address.")
            }
        }
    }

    private fun isValidMacAddress(mac: String): Boolean {
        val regex = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
        return regex.matches(mac)
    }

    private fun log(message: String) {
        _state.update { it.copy(logMessages = it.logMessages + message) }
    }
}
