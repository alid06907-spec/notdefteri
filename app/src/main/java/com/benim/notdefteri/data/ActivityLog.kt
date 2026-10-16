package com.benim.notdefteri.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Uygulamadaki işlemlerin geçmişi (ekleme, silme, yapıldı...)
@Entity(tableName = "activity_log")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val action: String,    // Örn: "Silindi", "Eklendi", "Yapıldı"
    val noteTitle: String, // Hangi not
    val timestamp: Long    // Ne zaman
)

@Dao
interface LogDao {
    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ActivityLog>>

    @Insert
    suspend fun insertLog(log: ActivityLog)

    @Query("DELETE FROM activity_log")
    suspend fun clearLogs()
}
