package com.benim.notdefteri.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.benim.notdefteri.MainActivity
import com.benim.notdefteri.R
import com.benim.notdefteri.data.NoteDatabase
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

// Tek bir not için kullanıcının ayarladığı saatte bildirim atar
class NoteReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val noteId = inputData.getLong("note_id", -1L)
        if (noteId < 0) return Result.success()

        val note = NoteDatabase.getDatabase(applicationContext).noteDao()
            .getAllNotes().first().find { it.id == noteId }
            ?: return Result.success() // Not silinmişse sessiz kal

        if (note.isDone) return Result.success() // Yapıldıysa hatırlatmaya gerek yok
        if (!note.reminderEnabled) return Result.success() // Hatırlatıcı kapatılmışsa gönderme

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Not Alarmları",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Not için kurduğun hatırlatmalar" }
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            noteId.toInt(),
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Unutma: ${note.title.ifBlank { "(Başlıksız not)" }}")
            .setContentText(note.content.ifBlank { "Hatırlatma zamanı geldi" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.content.ifBlank { "Hatırlatma zamanı geldi" }))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify((2000L + noteId).toInt(), notification)

        // Bildirimi işlem geçmişine kaydet
        NoteDatabase.getDatabase(applicationContext).logDao().insertLog(
            com.benim.notdefteri.data.ActivityLog(
                action = "Bildirim gönderildi",
                noteTitle = note.title.ifBlank { "(Başlıksız not)" },
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "note_reminders"

        // Belirtilen zamana hatırlatma kur (aynı nota yeniden kurulursa eskisi iptal olur)
        fun schedule(context: Context, noteId: Long, triggerAt: Long) {
            val delay = (triggerAt - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<NoteReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("note_id" to noteId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "note_reminder_$noteId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, noteId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("note_reminder_$noteId")
        }
    }
}
