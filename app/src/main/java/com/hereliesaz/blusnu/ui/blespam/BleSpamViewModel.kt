package com.hereliesaz.blusnu.ui.blespam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BleSpamModule
import com.hereliesaz.blusnu.data.PayloadType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the BLE Spam feature.
 *
 * Wraps the [BleSpamModule] to expose state and actions to the UI.
 */
class BleSpamViewModel(private val bleSpamModule: BleSpamModule) : ViewModel() {

    // Expose the advertising state from the module.
    val isAdvertising: StateFlow<Boolean> = bleSpamModule.isAdvertising

    // Track the selected payload in the UI.
    private val _selectedPayloadType = MutableStateFlow(PayloadType.APPLE_CONTINUITY)
    val selectedPayloadType: StateFlow<PayloadType> = _selectedPayloadType.asStateFlow()

    /**
     * Updates the selected payload type.
     * Only allowed if not currently advertising.
     */
    fun selectPayload(type: PayloadType) {
        if (!isAdvertising.value) {
            _selectedPayloadType.value = type
        }
    }

    /**
     * Toggles the attack on/off.
     */
    fun toggleSpam() {
        if (isAdvertising.value) {
            bleSpamModule.stopSpam()
        } else {
            bleSpamModule.startSpam(_selectedPayloadType.value)
        }
    }

    /**
     * Ensure advertising stops if the ViewModel is destroyed (e.g. screen closed).
     */
    override fun onCleared() {
        super.onCleared()
        bleSpamModule.stopSpam()
    }
}
