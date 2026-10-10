package com.example.signtalk.data.dictionary

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DictionaryEntity::class], version = 3, exportSchema = true)
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

        /**
         * Expert-review metadata: language, gloss, English/Filipino meaning, synonyms, sign
         * type, hands, source and validation status. Bundled rows are filled in right after
         * (RoomDictionaryRepository.backfillBundledMetadata); the user's own entries become
         * "personal" (not verified).
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (column in listOf(
                    "language", "gloss", "meaningEn", "meaningFil", "synonyms",
                    "signType", "hands", "source", "validatedBy"
                )) {
                    db.execSQL("ALTER TABLE dictionary_entries ADD COLUMN $column TEXT NOT NULL DEFAULT ''")
                }
                db.execSQL(
                    "ALTER TABLE dictionary_entries ADD COLUMN validationStatus TEXT NOT NULL DEFAULT 'unverified'"
                )
                db.execSQL("UPDATE dictionary_entries SET validationStatus = 'personal' WHERE isUserAdded = 1")
            }
        }

        fun getInstance(context: Context): SignTalkDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SignTalkDatabase::class.java,
                    "signtalk.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
