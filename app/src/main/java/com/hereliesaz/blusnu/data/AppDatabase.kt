package com.hereliesaz.blusnu.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The main Room Database definition for the application.
 *
 * This abstract class serves as the main access point for the underlying SQLite database.
 * It defines the entities (tables) and the version number of the schema.
 *
 * @property entities The list of data classes that represent tables in this database.
 *                    Includes [TargetDevice], [SavedSession], and [AttackChainTemplate].
 * @property version The current version of the database schema. Used for migration logic.
 * @property exportSchema Set to false as we do not need to export the schema to a JSON file for this project.
 */
@Database(entities = [TargetDevice::class, SavedSession::class, AttackChainTemplate::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // Abstract method to get the TargetDeviceDao.
    // Room implements this method at runtime.
    abstract fun targetDeviceDao(): TargetDeviceDao

    // Abstract method to get the SavedSessionDao.
    abstract fun savedSessionDao(): SavedSessionDao

    // Abstract method to get the AttackChainTemplateDao.
    abstract fun attackChainTemplateDao(): AttackChainTemplateDao

    /**
     * Companion object to hold the singleton instance of the database.
     * This ensures that only one instance of the database is opened at a time,
     * which is expensive to create.
     */
    companion object {
        // Volatile ensures that the value of INSTANCE is always up-to-date and visible to all threads.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton instance of the AppDatabase.
         *
         * If the instance is null, it creates a new one using a synchronized block
         * to prevent race conditions during initialization.
         *
         * @param context The application context.
         * @return The singleton AppDatabase instance.
         */
        fun getDatabase(context: Context): AppDatabase {
            // Check if the instance is already created. If so, return it.
            return INSTANCE ?: synchronized(this) {
                // Double-check locking inside the synchronized block.
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "blusnu_database" // The name of the SQLite database file on disk.
                )
                // In a production app, we would provide Migration objects.
                // For now, fallbackToDestructiveMigration() destroys the old database if the version changes.
                // This is acceptable for development but should be replaced for production.
                .fallbackToDestructiveMigration()
                .build()

                // Assign the newly created instance to the volatile variable.
                INSTANCE = instance
                // Return the instance.
                instance
            }
        }
    }
}
