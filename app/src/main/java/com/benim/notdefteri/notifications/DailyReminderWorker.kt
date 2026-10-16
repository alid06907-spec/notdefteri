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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.benim.notdefteri.MainActivity
import com.benim.notdefteri.R
import com.benim.notdefteri.data.NoteDatabase
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

// Her gün ~12:00'de çalışır: geçmişte kalan yapılmamış notlar varsa sistem bildirimi atar
// (Uygulama kapalı olsa bile çalışır)
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val notes = NoteDatabase.getDatabase(applicationContext).noteDao().getAllNotes().first()

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val pastUndone = notes.filter { !it.isDone && it.date < todayStart }
        if (pastUndone.isEmpty()) return Result.success() // Unutulan yoksa sessiz kal

        // Android 13+ bildirim izni verilmemişse gönderme
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val nm = applicationContext.getSystemService(NotificationManager::class.java)

        // Bildirim kanalı (Android 8+)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Not Hatırlatmaları",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Unutulan notlar için günlük hatırlatma" }
            )
        }

        val titles = pastUndone.take(3).joinToString(", ") { it.title.ifBlank { "(Başlıksız)" } }
        val more = pastUndone.size - 3
        val text = buildString {
            append("Geçmiş günlerden ${pastUndone.size} yapılmamış notun var: $titles")
            if (more > 0) append(" ve $more tane daha")
        }

        // Bildirime dokununca uygulama açılır
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Unutulan notların var")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)

        // Bildirimi işlem geçmişine kaydet
        NoteDatabase.getDatabase(applicationContext).logDao().insertLog(
            com.benim.notdefteri.data.ActivityLog(
                action = "Bildirim gönderildi",
                noteTitle = "Günlük hatırlatma (${pastUndone.size} yapılmamış not)",
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "daily_reminders"
        private const val NOTIFICATION_ID = 1001
        private const val WORK_NAME = "daily_note_reminder"

        // Her gün 12:00'de çalışacak şekilde planla (ilk çalıştırma bir sonraki 12:00)
        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
            }
            val initialDelay = next.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Zaten kuruluysa dokunma
                request
            )
        }
    }
}
