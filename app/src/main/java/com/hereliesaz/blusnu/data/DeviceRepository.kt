package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Repository class for managing TargetDevice data.
 *
 * The repository acts as a single source of truth for device data, abstracting the
 * underlying data sources (in this case, the Room database DAO). It handles the logic
 * for fetching, inserting, and updating device records, including specific business logic
 * for merging new scan results with existing data.
 *
 * @property targetDeviceDao The Data Access Object for device database operations.
 */
class DeviceRepository(private val targetDeviceDao: TargetDeviceDao) {

    // Exposes the list of all devices as a Flow.
    // This allows the UI (ViewModel) to observe changes in the database in real-time.
    // For example, when a new device is inserted, this Flow emits the updated list automatically.
    val allDevices: Flow<List<TargetDevice>> = targetDeviceDao.getAll()

    /**
     * Inserts or updates a device in the database.
     *
     * This method implements specific "upsert" logic:
     * 1. It attempts to insert the device.
     * 2. If the insert fails (returns -1) because the device already exists (conflict on MAC address),
     *    it fetches the existing record.
     * 3. It then creates an updated copy that preserves user-defined fields (notes, isFavorite)
     *    from the existing record while updating dynamic fields (RSSI, name, services).
     * 4. Finally, it updates the record in the database.
     *
     * @param device The TargetDevice object to save.
     */
    suspend fun insert(device: TargetDevice) {
        // Try to insert the new device record.
        // The DAO uses OnConflictStrategy.IGNORE, so this returns -1 if the MAC address exists.
        val rowId = targetDeviceDao.insert(device)

        // If rowId is -1, it means the device is already in the DB.
        if (rowId == -1L) {
            // Retrieve the existing record to get its current state (specifically notes and favorites).
            val existing = targetDeviceDao.getDevice(device.macAddress)

            // If the existing record is found (it should be, but safety check via ?.let):
            existing?.let {
                // Some update paths (service discovery via ACTION_UUID / onServicesDiscovered)
                // do not carry a real RSSI reading and pass a sentinel value (0 or
                // Short.MIN_VALUE == -32768). A genuine reading is always a plausible negative
                // dBm. If the incoming RSSI is not a real reading, keep the previously stored
                // value instead of clobbering it with garbage.
                val incomingRssiIsReal =
                    device.rssi != 0 && device.rssi != Short.MIN_VALUE.toInt()
                val mergedRssi = if (incomingRssiIsReal) device.rssi else it.rssi

                // Create a copy of the new device object (which has fresh RSSI/Time data),
                // but overwrite specific fields with values from the existing database record.
                val updated = device.copy(
                    // Preserve a real RSSI reading; fall back to the stored one for service-only updates.
                    rssi = mergedRssi,
                    // Preserve the notes the user wrote.
                    notes = it.notes,
                    // Preserve the favorite status.
                    isFavorite = it.isFavorite,
                    // Merge the service lists. We take existing services and add any newly discovered ones.
                    // Use .distinct() to remove duplicates.
                    services = (it.services + device.services).distinct()
                )
                // Perform the update operation with the merged object.
                targetDeviceDao.update(updated)
            }
        }
    }

    /**
     * Retrieves a one-time snapshot of the current list of devices.
     *
     * Unlike `allDevices` which is a Flow, this suspend function returns a List directly.
     * Useful for operations that need the current state once (e.g., generating a report).
     *
     * @return A List of TargetDevice objects.
     */
    suspend fun getAllDevicesOneShot(): List<TargetDevice> {
        // Collects the first emission from the allDevices Flow and returns it.
        return allDevices.first()
    }

    /**
     * Updates the user notes for a specific device.
     *
     * @param macAddress The MAC address of the device.
     * @param notes The new notes text.
     */
    suspend fun updateNotes(macAddress: String, notes: String) {
        // Delegates to the DAO's specific update method.
        targetDeviceDao.updateNotes(macAddress, notes)
    }

    /**
     * Updates the favorite status for a specific device.
     *
     * @param macAddress The MAC address of the device.
     * @param isFavorite The new favorite status.
     */
    suspend fun updateIsFavorite(macAddress: String, isFavorite: Boolean) {
        // Delegates to the DAO's specific update method.
        targetDeviceDao.updateIsFavorite(macAddress, isFavorite)
    }
}
