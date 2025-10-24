package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val targetDeviceDao: TargetDeviceDao) {

    val allDevices: Flow<List<TargetDevice>> = targetDeviceDao.getAll()

    suspend fun insert(device: TargetDevice) {
        targetDeviceDao.insert(device)
    }

    suspend fun updateNotes(macAddress: String, notes: String) {
        targetDeviceDao.updateNotes(macAddress, notes)
    }

    suspend fun updateIsFavorite(macAddress: String, isFavorite: Boolean) {
        targetDeviceDao.updateIsFavorite(macAddress, isFavorite)
    }
}
