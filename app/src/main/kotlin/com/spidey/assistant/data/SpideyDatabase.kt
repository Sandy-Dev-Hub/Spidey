package com.spidey.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ContactAlias::class], version = 1, exportSchema = false)
abstract class SpideyDatabase : RoomDatabase() {

    abstract fun aliasDao(): AliasDao

    companion object {
        @Volatile
        private var INSTANCE: SpideyDatabase? = null

        fun getInstance(context: Context): SpideyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpideyDatabase::class.java,
                    "spidey_assistant.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
