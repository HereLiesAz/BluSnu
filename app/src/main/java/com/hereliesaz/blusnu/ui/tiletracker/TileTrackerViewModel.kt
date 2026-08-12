package com.hereliesaz.blusnu.ui.tiletracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.ActiveTaskManager
import com.hereliesaz.blusnu.data.TileTagMode
import com.hereliesaz.blusnu.data.TileTagModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class TileTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val tileTagModule = TileTagModule(application)

    private val _selectedMode = MutableStateFlow(TileTagMode.SCAN_TILE_DEVICES)
    val selectedMode: StateFlow<TileTagMode> = _selectedMode

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private var attackJob: Job? = null
    private var currentTaskId: String? = null

    fun selectMode(mode: TileTagMode) {
        if (!_isRunning.value) {
            _selectedMode.value = mode
        }
    }

    fun startAttack() {
        if (_isRunning.value) return
        _isRunning.value = true
        _logs.value = emptyList()

        val mode = _selectedMode.value
        val taskId = "tiletracker_${System.currentTimeMillis()}"
        currentTaskId = taskId
        ActionLogger.log("Tile Tracker: Starting ${mode.name}")
        ActiveTaskManager.add(taskId, "Tile Tracker", mode.description)

        val logFlow = when (mode) {
            TileTagMode.SCAN_TILE_DEVICES -> tileTagModule.startScan()
            TileTagMode.BROADCAST_TILE -> tileTagModule.startBroadcast()
        }

        attackJob = viewModelScope.launch {
            try {
                logFlow.collect { log ->
                    _logs.value = _logs.value + log
                }
                val resultSummary = _logs.value.joinToString("\n").take(200)
                ActionLogger.log("Tile Tracker: Finished ${mode.name}")
                ActionLogger.log("Result: $resultSummary")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _logs.value = _logs.value + "ERROR: ${e.message ?: "Unknown error"}"
                ActionLogger.log("Tile Tracker: Error during ${mode.name} — ${e.message}")
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
        tileTagModule.stop()
        _isRunning.value = false
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
        ActionLogger.log("Tile Tracker: Stopped by user")
    }

    override fun onCleared() {
        super.onCleared()
        attackJob?.cancel()
        attackJob = null
        tileTagModule.close()
        currentTaskId?.let { ActiveTaskManager.remove(it) }
        currentTaskId = null
    }
}
