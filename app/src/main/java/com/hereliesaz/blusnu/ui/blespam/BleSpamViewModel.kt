package com.hereliesaz.blusnu.ui.blespam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.BleSpamModule
import com.hereliesaz.blusnu.data.PayloadType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BleSpamViewModel(private val bleSpamModule: BleSpamModule) : ViewModel() {

    val isAdvertising: StateFlow<Boolean> = bleSpamModule.isAdvertising

    private val _selectedPayloadType = MutableStateFlow(PayloadType.APPLE_CONTINUITY)
    val selectedPayloadType: StateFlow<PayloadType> = _selectedPayloadType.asStateFlow()

    fun selectPayload(type: PayloadType) {
        if (!isAdvertising.value) {
            _selectedPayloadType.value = type
        }
    }

    fun toggleSpam() {
        if (isAdvertising.value) {
            bleSpamModule.stopSpam()
        } else {
            bleSpamModule.startSpam(_selectedPayloadType.value)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleSpamModule.stopSpam()
    }
}
