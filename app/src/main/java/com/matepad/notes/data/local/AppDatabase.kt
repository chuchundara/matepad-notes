package com.matepad.notes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.matepad.notes.data.local.dao.NoteDao
import com.matepad.notes.data.local.entity.FolderEntity
import com.matepad.notes.data.local.entity.NoteEntity

/**
 * Main Room Database for MatePad Notes.
 * Persists note metadata, folders, and viewport configuration.
 */
@Database(
    entities = [NoteEntity::class, FolderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        const val DATABASE_NAME = "matepad_notes.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
