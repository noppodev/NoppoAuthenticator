package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OtpAccount::class], version = 2, exportSchema = false)
abstract class OtpDatabase : RoomDatabase() {
    abstract fun otpDao(): OtpDao

    companion object {
        @Volatile
        private var INSTANCE: OtpDatabase? = null

        fun getDatabase(context: Context): OtpDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OtpDatabase::class.java,
                    "noppo_authenticator_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
