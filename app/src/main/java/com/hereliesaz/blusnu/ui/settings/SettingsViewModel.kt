package com.hereliesaz.blusnu.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.DatabaseUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val databaseUpdater = DatabaseUpdater(application)

    private val _updateStatus = MutableStateFlow("")
    val updateStatus: StateFlow<String> = _updateStatus

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateStatus.value = "Checking for updates..."
            val result = databaseUpdater.checkForUpdates()
            _updateStatus.value = result
        }
    }
}
