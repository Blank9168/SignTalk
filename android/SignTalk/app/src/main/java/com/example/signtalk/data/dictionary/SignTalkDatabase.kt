package com.example.signtalk.data.dictionary

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DictionaryEntity::class], version = 1, exportSchema = true)
abstract class SignTalkDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        @Volatile private var instance: SignTalkDatabase? = null

        fun getInstance(context: Context): SignTalkDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SignTalkDatabase::class.java,
                    "signtalk.db"
                ).build().also { instance = it }
            }
    }
}
