package com.hereliesaz.blusnu.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TargetDevice::class, SavedSession::class, AttackChainTemplate::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun targetDeviceDao(): TargetDeviceDao
    abstract fun savedSessionDao(): SavedSessionDao
    abstract fun attackChainTemplateDao(): AttackChainTemplateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "blusnu_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
