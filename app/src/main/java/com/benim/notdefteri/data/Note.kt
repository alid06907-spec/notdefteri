package com.benim.notdefteri.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val date: Long, // Timestamp
    val isImportant: Boolean = false,
    val isDone: Boolean = false, // Yapıldı olarak işaretlendi mi
    val reminderTime: Long? = null, // Hatırlatma bildirimi zamanı (hiç kurulmadıysa null)
    val reminderEnabled: Boolean = false, // Hatırlatıcı açık mı (varsayılan kapalı)
    val aiSummary: String? = null,
    val audioPath: String? = null, // Sesli not kaydının cihazdaki dosya yolu (yoksa null)
    val waveform: String? = null, // Ses şiddeti çubukları, virgülle ayrılmış 0-100 değerler (yoksa null)
    val audioDurationMs: Int? = null // Sesli notun toplam süresi (ms) — ana ekranda tekrar çözümlemeden göstermek için
)
