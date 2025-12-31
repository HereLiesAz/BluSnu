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

    private val sharedPreferences = application.getSharedPreferences("blusnu_prefs", android.content.Context.MODE_PRIVATE)
    private val _backupUrl = MutableStateFlow(sharedPreferences.getString("backup_url", "https://example.com/backup") ?: "")
    val backupUrl: StateFlow<String> = _backupUrl

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateStatus.value = "Checking for updates..."
            val result = databaseUpdater.checkForUpdates()
            _updateStatus.value = result
        }
    }

    fun updateBackupUrl(newUrl: String) {
        _backupUrl.value = newUrl
        sharedPreferences.edit().putString("backup_url", newUrl).apply()
    }
}
