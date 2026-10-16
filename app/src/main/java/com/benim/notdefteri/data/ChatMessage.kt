package com.benim.notdefteri.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// AI sohbet mesajları (kalıcı — uygulama kapansa da silinmez)
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String,   // "user" | "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUri: String? = null, // Ekli fotoğrafın dosya yolu (varsa)
    val fileUri: String? = null,  // Ekli dosyanın yolu (PDF/metin, varsa)
    val fileName: String? = null  // Ekli dosyanın görünen adı
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Insert
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun clearMessages()
}
