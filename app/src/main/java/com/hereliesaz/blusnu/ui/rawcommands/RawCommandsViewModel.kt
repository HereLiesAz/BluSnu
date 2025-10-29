package com.hereliesaz.blusnu.ui.rawcommands

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.InputStream

data class RawCommandsScreenState(
    val command: String = "",
    val output: String = ""
)

class RawCommandsViewModel : ViewModel() {

    private val _state = MutableStateFlow(RawCommandsScreenState())
    val state: StateFlow<RawCommandsScreenState> = _state.asStateFlow()

    fun onCommandChanged(command: String) {
        _state.value = _state.value.copy(command = command)
    }

    fun onExecuteClicked() {
        viewModelScope.launch {
            val result = RootExecutor.execute(_state.value.command)
            _state.value = _state.value.copy(output = result)
        }
    }
}
