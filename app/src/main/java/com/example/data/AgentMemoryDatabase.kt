package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LearnedNodeEntity::class,
        QueuedTaskEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AgentMemoryDatabase : RoomDatabase() {

    abstract fun agentMemoryDao(): AgentMemoryDao

    companion object {
        @Volatile
        private var INSTANCE: AgentMemoryDatabase? = null

        fun getInstance(context: Context): AgentMemoryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentMemoryDatabase::class.java,
                    "autopilot_agent_memory.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
