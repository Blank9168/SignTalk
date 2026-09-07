package com.example.signtalk.data.dictionary

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DictionaryEntity::class], version = 2, exportSchema = true)
abstract class SignTalkDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        @Volatile private var instance: SignTalkDatabase? = null

        /**
         * Adds the nullable `videoUri` column (see [DictionaryEntity]) for the
         * "add a video to a sign" feature. Existing rows -- including all 105
         * seeded FSL-105 entries and any of the user's own custom entries --
         * simply get NULL here, same as SQLite's default for a newly added
         * column with no explicit default.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dictionary_entries ADD COLUMN videoUri TEXT")
            }
        }

        fun getInstance(context: Context): SignTalkDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SignTalkDatabase::class.java,
                    "signtalk.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
