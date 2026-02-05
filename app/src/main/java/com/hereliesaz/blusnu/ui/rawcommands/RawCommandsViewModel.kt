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

/**
 * State for RawCommands screen.
 */
data class RawCommandsScreenState(
    val command: String = "",
    val output: String = ""
)

/**
 * ViewModel for executing arbitrary shell commands via [RootExecutor].
 */
class RawCommandsViewModel : ViewModel() {

    private val _state = MutableStateFlow(RawCommandsScreenState())
    val state: StateFlow<RawCommandsScreenState> = _state.asStateFlow()

    fun onCommandChanged(command: String) {
        _state.value = _state.value.copy(command = command)
    }

    /**
     * Executes the entered command and updates the output.
     */
    fun onExecuteClicked() {
        viewModelScope.launch {
            // RootExecutor.execute is a blocking/suspending call.
            val result = RootExecutor.execute(_state.value.command)
            _state.value = _state.value.copy(output = result)
        }
    }
}
