package com.hereliesaz.blusnu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the `TargetDevice` entity.
 *
 * This interface defines the database interactions required to manage discovered devices.
 * It uses Room annotations to generate the SQL queries and implementation code at compile time.
 */
@Dao
interface TargetDeviceDao {

    /**
     * Retrieves all devices from the `target_devices` table.
     *
     * @return A Kotlin Flow that emits a list of TargetDevice objects.
     *         The Flow will emit a new list whenever the underlying data in the table changes.
     */
    @Query("SELECT * FROM target_devices")
    fun getAll(): Flow<List<TargetDevice>>

    /**
     * Inserts a new device into the database.
     *
     * @param device The TargetDevice object to insert.
     * @return The row ID of the newly inserted item, or -1 if the insert failed (e.g., conflict).
     *
     * Strategy: OnConflictStrategy.IGNORE
     * If a device with the same MAC address (PrimaryKey) already exists, the insert is ignored.
     * This allows us to handle updates manually in the Repository layer (e.g., preserving notes).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(device: TargetDevice): Long

    /**
     * Updates an existing device in the database.
     *
     * @param device The TargetDevice object with updated fields.
     *               The match is done based on the PrimaryKey (macAddress).
     */
    @Update
    suspend fun update(device: TargetDevice)

    /**
     * Retrieves a single device by its MAC address.
     *
     * @param macAddress The MAC address of the device to find.
     * @return The TargetDevice object if found, or null if not exists.
     */
    @Query("SELECT * FROM target_devices WHERE macAddress = :macAddress")
    suspend fun getDevice(macAddress: String): TargetDevice?

    /**
     * Updates only the 'notes' field for a specific device.
     *
     * This is a specific partial update query to avoid overwriting other fields
     * (like rssi or lastSeen) when the user is editing notes.
     *
     * @param macAddress The MAC address of the target device.
     * @param notes The new notes text to save.
     */
    @Query("UPDATE target_devices SET notes = :notes WHERE macAddress = :macAddress")
    suspend fun updateNotes(macAddress: String, notes: String)

    /**
     * Updates only the 'isFavorite' field for a specific device.
     *
     * This allows for quick toggling of the favorite status without full object updates.
     *
     * @param macAddress The MAC address of the target device.
     * @param isFavorite The new boolean state.
     */
    @Query("UPDATE target_devices SET isFavorite = :isFavorite WHERE macAddress = :macAddress")
    suspend fun updateIsFavorite(macAddress: String, isFavorite: Boolean)
}
