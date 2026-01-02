package com.hereliesaz.blusnu.ui.settings

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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

    private val _permissionsStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val permissionsStatus: StateFlow<Map<String, Boolean>> = _permissionsStatus

    private val _isMetric = MutableStateFlow(sharedPreferences.getBoolean("use_metric", false))
    val isMetric: StateFlow<Boolean> = _isMetric

    private val _isRooted = MutableStateFlow(false)
    val isRooted: StateFlow<Boolean> = _isRooted

    init {
        checkPermissions()
        checkRootAccess()
    }

    fun checkRootAccess() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // First try just "su" to trigger the prompt
                val suProcess = Runtime.getRuntime().exec("su")
                val os = java.io.DataOutputStream(suProcess.outputStream)
                os.writeBytes("id\n")
                os.writeBytes("exit\n")
                os.flush()
                val suExitCode = suProcess.waitFor()

                if (suExitCode == 0) {
                     _isRooted.value = true
                } else {
                    // Fallback to su -c id check
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                    val exitCode = process.waitFor()
                    _isRooted.value = exitCode == 0
                }
            } catch (e: Exception) {
                _isRooted.value = false
            }
        }
    }

    fun toggleMetric() {
        val newValue = !_isMetric.value
        _isMetric.value = newValue
        sharedPreferences.edit().putBoolean("use_metric", newValue).apply()
    }

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

    fun checkPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.INTERNET
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
             permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val statusMap = permissions.associateWith { permission ->
            ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED
        }
        _permissionsStatus.value = statusMap
    }
}
