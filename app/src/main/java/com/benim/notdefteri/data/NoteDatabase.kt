package com.benim.notdefteri.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note::class, ActivityLog::class, ChatMessage::class], version = 11, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun logDao(): LogDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        // v10 -> v11: sesli notun toplam süresi eklendi (ana ekranda göstermek için)
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN audioDurationMs INTEGER")
            }
        }

        // v9 -> v10: sesli notun ses şiddeti (waveform) çubukları eklendi
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN waveform TEXT")
            }
        }

        // v8 -> v9: notlara sesli not kaydı (ses dosyası yolu) eklendi
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN audioPath TEXT")
            }
        }

        // v1 -> v2: isDone kolonu eklendi (mevcut notlar korunur)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isDone INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v7 -> v8: hatırlatıcı açık/kapalı durumu eklendi
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v6 -> v7: notlara hatırlatma zamanı eklendi
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN reminderTime INTEGER")
            }
        }

        // v5 -> v6: sohbet mesajlarına dosya (PDF/metin) desteği
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN fileUri TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN fileName TEXT")
            }
        }

        // v4 -> v5: sohbet mesajlarına fotoğraf desteği
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN imageUri TEXT")
            }
        }

        // v3 -> v4: kalıcı AI sohbet tablosu eklendi
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`role` TEXT NOT NULL, " +
                            "`text` TEXT NOT NULL, " +
                            "`timestamp` INTEGER NOT NULL)"
                )
            }
        }

        // v2 -> v3: işlem geçmişi (log) tablosu eklendi
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activity_log` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`action` TEXT NOT NULL, " +
                            "`noteTitle` TEXT NOT NULL, " +
                            "`timestamp` INTEGER NOT NULL)"
                )
            }
        }

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
