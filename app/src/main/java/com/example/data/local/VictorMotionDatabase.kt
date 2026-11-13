package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LocalProjectEntity::class], version = 1, exportSchema = false)
abstract class VictorMotionDatabase : RoomDatabase() {
    abstract fun localProjectDao(): LocalProjectDao

    companion object {
        @Volatile
        private var instance: VictorMotionDatabase? = null

        fun getInstance(context: Context): VictorMotionDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VictorMotionDatabase::class.java,
                    "victor_motion_studio.db"
                ).build().also { instance = it }
            }
        }
    }
}
