package com.hereliesaz.blusnu.ui.findhubtag

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.FindHubTagMode
import com.hereliesaz.blusnu.data.FindHubTagModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class FindHubTagViewModel(application: Application) : AndroidViewModel(application) {

    private val findHubTagModule = FindHubTagModule(application)

    private val _selectedMode = MutableStateFlow(FindHubTagMode.SCAN_FINDHUB_BEACONS)
    val selectedMode: StateFlow<FindHubTagMode> = _selectedMode

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private var attackJob: Job? = null
    private var currentTaskId: String? = null

    fun selectMode(mode: FindHubTagMode) {
        if (!_isRunning.value) {
            _selectedMode.value = mode
        }
    }

    fun startAttack() {
        if (_isRunning.value) return
        _isRunning.value = true
        _logs.value = emptyList()

        val mode = _selectedMode.value
        val taskId = "findhubtag_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("Find Hub Tag: Starting ${mode.name}")
        ActiveTaskManager.add(taskId, "Find Hub Tag", mode.description)

        val logFlow = when (mode) {
            FindHubTagMode.SCAN_FINDHUB_BEACONS -> findHubTagModule.startScan()
            FindHubTagMode.BROADCAST_FINDHUB -> findHubTagModule.startBroadcast()
        }

        attackJob = viewModelScope.launch {
            try {
                logFlow.collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("Find Hub Tag: Finished ${mode.name}")
                ActionLogger.log("Result: $resultSummary")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _logs.value = _logs.value + "ERROR: ${e.message ?: "Unknown error"}"
                ActionLogger.log("Find Hub Tag: Error during ${mode.name} — ${e.message}")
            } finally {
                _isRunning.value = false
                currentTaskId?.let { ActiveTaskManager.remove(it) }
                currentTaskId = null
                attackJob = null
            }
        }
    }

    fun stopAttack() {
        attackJob?.cancel()
        attackJob = null
        findHubTagModule.stop()
        _isRunning.value = false
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
        ActionLogger.log("Find Hub Tag: Stopped by user")
    }

    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        attackJob = null
        findHubTagModule.close()
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
