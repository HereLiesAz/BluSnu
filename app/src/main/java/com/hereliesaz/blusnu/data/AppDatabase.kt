package com.hereliesaz.blusnu.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The main Room Database definition for the application.
 *
 * <p>
 * This abstract class serves as the main access point for the underlying SQLite database.
 * It defines the entities (tables) and the Data Access Objects (DAOs) used to interact with them.
 * </p>
 *
 * <b>Entities:</b>
 * <ul>
 *     <li>[TargetDevice]: Stores discovered Bluetooth devices and their attributes (MAC, RSSI, Features).</li>
 *     <li>[SavedSession]: Stores metadata about past scanning/attack sessions.</li>
 *     <li>[AttackChainTemplate]: Stores user-defined automation workflows.</li>
 * </ul>
 *
 * <b>Version History:</b>
 * <ul>
 *     <li>v1: Initial Schema.</li>
 *     <li>v2: Added AttackChainTemplate table.</li>
 * </ul>
 */
@Database(
    entities = [TargetDevice::class, SavedSession::class, AttackChainTemplate::class],
    version = 2,
    exportSchema = false // Schema export disabled for this build to reduce build artifacts
)
abstract class AppDatabase : RoomDatabase() {

    // ---------------------------------------------------------------------------------------------
    // Data Access Objects (DAOs)
    // ---------------------------------------------------------------------------------------------

    /**
     * Provides access to [TargetDevice] operations (Insert, Query, Update).
     */
    abstract fun targetDeviceDao(): TargetDeviceDao

    /**
     * Provides access to [SavedSession] operations.
     */
    abstract fun savedSessionDao(): SavedSessionDao

    /**
     * Provides access to [AttackChainTemplate] operations.
     */
    abstract fun attackChainTemplateDao(): AttackChainTemplateDao

    // ---------------------------------------------------------------------------------------------
    // Singleton Pattern Implementation
    // ---------------------------------------------------------------------------------------------

    companion object {
        /**
         * Volatile reference to the singleton instance.
         * Ensures that writes to this field are immediately visible to other threads.
         */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * returns the singleton instance of [AppDatabase].
         *
         * <p>
         * This method uses double-checked locking to ensure thread safety during lazy initialization.
         * If the database does not exist, it builds it using the application context.
         * </p>
         *
         * @param context The context used to build the database. Application context is extracted from this.
         * @return The singleton [AppDatabase] instance.
         */
        fun getDatabase(context: Context): AppDatabase {
            // Check for existing instance
            return INSTANCE ?: synchronized(this) {
                // Double-check inside synchronized block
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "blusnu_database" // The name of the SQLite database file on disk.
                )
                // Wipes the database if a migration path is missing (useful for dev/prototyping)
                // TODO: Implement proper Migration strategies for production release
                .fallbackToDestructiveMigration()
                .build()

                INSTANCE = instance
                // Return the instance.
                instance
            }
        }
    }
}
