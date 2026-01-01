package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class DeviceRepository(private val targetDeviceDao: TargetDeviceDao) {

    val allDevices: Flow<List<TargetDevice>> = targetDeviceDao.getAll()

    suspend fun insert(device: TargetDevice) {
        // Try to insert (will fail if exists due to IGNORE)
        val rowId = targetDeviceDao.insert(device)
        if (rowId == -1L) {
            // Device exists, retrieve it to preserve notes/favorite
            val existing = targetDeviceDao.getDevice(device.macAddress)
            existing?.let {
                val updated = device.copy(
                    notes = it.notes,
                    isFavorite = it.isFavorite,
                    // Merge services if needed, or just append new ones
                    services = (it.services + device.services).distinct()
                )
                targetDeviceDao.update(updated)
            }
        }
    }

    suspend fun getAllDevicesOneShot(): List<TargetDevice> {
        return allDevices.first()
    }

    suspend fun updateNotes(macAddress: String, notes: String) {
        targetDeviceDao.updateNotes(macAddress, notes)
    }

    suspend fun updateIsFavorite(macAddress: String, isFavorite: Boolean) {
        targetDeviceDao.updateIsFavorite(macAddress, isFavorite)
    }
}
