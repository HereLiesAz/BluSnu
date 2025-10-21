package com.hereliesaz.blusnu.ui.keystrokeinjection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.KeystrokeInjectionModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KeystrokeInjectionState(
    val isPared: Boolean = false,
    val logMessages: List<String> = emptyList()
)

class KeystrokeInjectionViewModel(
    application: Application,
    private val keystrokeInjectionModule: KeystrokeInjectionModule
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(KeystrokeInjectionState())
    val state: StateFlow<KeystrokeInjectionState> = _state.asStateFlow()

    fun onAttemptAttack() {
        viewModelScope.launch {
            log("Attempting silent pairing...")
            val success = keystrokeInjectionModule.attemptPairing()
            if (success) {
                log("Pairing successful!")
                _state.update { it.copy(isPared = true) }
            } else {
                log("Pairing failed.")
            }
        }
    }

    fun onSendKeystrokes(text: String) {
        viewModelScope.launch {
            log("Sending keystrokes: '$text'")
            val success = keystrokeInjectionModule.sendKeystrokes(text)
            if (!success) {
                log("Failed to send keystrokes.")
            }
        }
    }

    private fun log(message: String) {
        _state.update { it.copy(logMessages = it.logMessages + message) }
    }
}
