package com.benim.notdefteri.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benim.notdefteri.data.ActivityLog
import com.benim.notdefteri.data.ChatMessage
import com.benim.notdefteri.data.Note
import com.benim.notdefteri.data.NoteDatabase
import com.benim.notdefteri.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository
    private val _allNotes: StateFlow<List<Note>>
    
    private val prefs = application.getSharedPreferences("notdefteri_prefs", android.content.Context.MODE_PRIVATE)

    // Son seçilen günü hatırla; kayıt yoksa bugünü aç
    private val _selectedDate = MutableStateFlow(
        prefs.getLong("last_selected_date", 0L).takeIf { it != 0L }
            ?: Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
    )
    val selectedDate: StateFlow<Long> = _selectedDate.asStateFlow()

    val filteredNotes: StateFlow<List<Note>>

    // Gün başlangıcı (00:00) -> o günkü yapılmamış not sayısı
    val undoneCountByDay: StateFlow<Map<Long, Int>>

    // İşlem geçmişi
    val logs: StateFlow<List<ActivityLog>>

    private val _aiReminders = MutableStateFlow<String?>(null)
    val aiReminders: StateFlow<String?> = _aiReminders.asStateFlow()

    // Uygulama açılınca otomatik gösterilen AI hatırlatması
    private val _autoReminder = MutableStateFlow<String?>(null)
    val autoReminder: StateFlow<String?> = _autoReminder.asStateFlow()

    // AI sohbet (kalıcı — veritabanından gelir)
    val chatMessages: StateFlow<List<ChatMessage>>

    // Sohbette not eklemek için tüm notlar
    val allNotesList: StateFlow<List<Note>> get() = _allNotes

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _isLoadingAi = MutableStateFlow(false)
    val isLoadingAi: StateFlow<Boolean> = _isLoadingAi.asStateFlow()

    init {
        val db = NoteDatabase.getDatabase(application)
        repository = NoteRepository(db.noteDao(), db.logDao(), db.chatDao(), application)
        _allNotes = repository.allNotes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        filteredNotes = combine(_allNotes, _selectedDate) { notes, date ->
            notes.filter { noteDateMatches(it.date, date) }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        undoneCountByDay = _allNotes.map { notes ->
            notes.filter { !it.isDone }
                .groupingBy { dayStart(it.date) }
                .eachCount()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

        logs = repository.allLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        chatMessages = repository.allChatMessages.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    private fun dayStart(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun noteDateMatches(noteDate: Long, selectedDate: Long): Boolean {
        val noteCal = Calendar.getInstance().apply { timeInMillis = noteDate }
        val selectedCal = Calendar.getInstance().apply { timeInMillis = selectedDate }
        return noteCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                noteCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)
    }

    fun onDateSelected(date: Long) {
        _selectedDate.value = date
        prefs.edit().putLong("last_selected_date", date).apply()
    }

    // Not değişince ana ekran widget'ını tazele
    private fun refreshWidget() = viewModelScope.launch {
        try {
            com.benim.notdefteri.widget.NotesWidget().updateAll(getApplication())
        } catch (e: Exception) {
            // Widget eklenmemişse sorun değil
        }
    }

    fun insert(note: Note) = viewModelScope.launch {
        val newId = repository.insert(note)
        repository.addLog("Eklendi", note.title)
        // Yeni notta hatırlatıcı ayarlanmışsa (Yeni Not ekranındaki alarm ikonu) bildirimi de kur
        val time = note.reminderTime
        if (note.reminderEnabled && time != null && time > System.currentTimeMillis()) {
            com.benim.notdefteri.notifications.NoteReminderWorker.schedule(getApplication(), newId, time)
        }
        refreshWidget()
    }

    fun update(note: Note) = viewModelScope.launch {
        repository.update(note)
        repository.addLog("Düzenlendi", note.title)
        // Notu Düzenle ekranındaki alarm ikonuyla hatırlatıcı değiştiyse bildirimi de güncelle
        val time = note.reminderTime
        if (note.reminderEnabled && time != null && time > System.currentTimeMillis() && !note.isDone) {
            com.benim.notdefteri.notifications.NoteReminderWorker.schedule(getApplication(), note.id, time)
        } else {
            com.benim.notdefteri.notifications.NoteReminderWorker.cancel(getApplication(), note.id)
        }
        refreshWidget()
    }

    // Notu yapıldı/yapılmadı olarak işaretle (silmez)
    // Yapıldı işaretlenince kurulu hatırlatıcı da otomatik kapanır
    fun toggleDone(note: Note) = viewModelScope.launch {
        val newDone = !note.isDone
        val disableReminder = newDone && note.reminderEnabled
        repository.update(
            note.copy(
                isDone = newDone,
                reminderEnabled = if (disableReminder) false else note.reminderEnabled
            )
        )
        repository.addLog(if (newDone) "Yapıldı" else "Geri alındı", note.title)
        if (disableReminder) {
            com.benim.notdefteri.notifications.NoteReminderWorker.cancel(getApplication(), note.id)
            repository.addLog("Hatırlatma kapatıldı", note.title)
        }
        refreshWidget()
    }

    fun delete(note: Note) = viewModelScope.launch {
        repository.delete(note)
        repository.addLog("Silindi", note.title)
        com.benim.notdefteri.notifications.NoteReminderWorker.cancel(getApplication(), note.id)
        // Sesli not kaydı varsa dosyasını da temizle (yetim dosya kalmasın)
        note.audioPath?.let { runCatching { java.io.File(it).delete() } }
        refreshWidget()
    }

    // Nota hatırlatma kur (açık olarak): ayarlanan saatte gerçek bildirim düşer
    fun setReminder(note: Note, time: Long) = viewModelScope.launch {
        repository.update(note.copy(reminderTime = time, reminderEnabled = true))
        repository.addLog("Hatırlatma kuruldu", note.title)
        com.benim.notdefteri.notifications.NoteReminderWorker.schedule(getApplication(), note.id, time)
    }

    // Kurulu hatırlatıcıyı tek dokunuşla aç/kapat
    fun toggleReminder(note: Note) = viewModelScope.launch {
        val time = note.reminderTime ?: return@launch
        val newEnabled = !note.reminderEnabled
        repository.update(note.copy(reminderEnabled = newEnabled))
        if (newEnabled) {
            if (time > System.currentTimeMillis()) {
                com.benim.notdefteri.notifications.NoteReminderWorker.schedule(getApplication(), note.id, time)
            }
            repository.addLog("Hatırlatma açıldı", note.title)
        } else {
            com.benim.notdefteri.notifications.NoteReminderWorker.cancel(getApplication(), note.id)
            repository.addLog("Hatırlatma kapatıldı", note.title)
        }
    }

    fun clearReminder(note: Note) = viewModelScope.launch {
        repository.update(note.copy(reminderTime = null, reminderEnabled = false))
        repository.addLog("Hatırlatma kaldırıldı", note.title)
        com.benim.notdefteri.notifications.NoteReminderWorker.cancel(getApplication(), note.id)
    }

    // Açılışta: unutulan (geçmiş + bugün) ve yaklaşan (yarın) notlar için AI hatırlatması
    // Saat başına en fazla 1 kez gösterir: 16:20'de gösterdiyse 16:59'a kadar sussmaz, 17:00'den sonraki ilk girişte yine gösterir
    fun checkForgottenReminder() = viewModelScope.launch {
        val now = Calendar.getInstance()
        // Yıl + günün numarası + saat -> her saat dilimi için benzersiz anahtar
        val hourKey = now.get(Calendar.YEAR) * 1000000L +
                now.get(Calendar.DAY_OF_YEAR) * 100L +
                now.get(Calendar.HOUR_OF_DAY)
        if (prefs.getLong("last_reminder_hour", -1L) == hourKey) return@launch // Bu saat içinde zaten gösterildi

        val notes = repository.allNotes.first()
        val today = dayStart(System.currentTimeMillis())
        val tomorrow = today + 24 * 60 * 60 * 1000L

        val pastUndone = notes.filter { !it.isDone && dayStart(it.date) < today }
        val todayUndone = notes.filter { !it.isDone && dayStart(it.date) == today }
        val tomorrowNotes = notes.filter { !it.isDone && dayStart(it.date) == tomorrow }

        if (pastUndone.isEmpty() && todayUndone.isEmpty() && tomorrowNotes.isEmpty()) return@launch

        val msg = repository.getForgottenReminder(pastUndone, todayUndone, tomorrowNotes)
        if (msg.isNotBlank()) {
            _autoReminder.value = msg
            prefs.edit().putLong("last_reminder_hour", hourKey).apply()
            sendOneTimeReminderNotification(msg)
        }
    }

    fun dismissAutoReminder() {
        _autoReminder.value = null
    }

    // Açılışta oluşan AI hatırlatmasını tek seferlik sistem bildirimi olarak da gönderir
    private fun sendOneTimeReminderNotification(msg: String) {
        val context = getApplication<Application>()

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        val channelId = "auto_reminders"

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    channelId,
                    "Anlık Hatırlatmalar",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Uygulama açılışındaki AI hatırlatma özeti" }
            )
        }

        val lines = msg.lines().map { it.trim() }.filter { it.isNotBlank() }
        // Kısa özet (bildirim daraltılmışken görünür): ilk başlık satırı
        val summary = lines.firstOrNull { !it.startsWith("·") } ?: lines.firstOrNull() ?: "Kontrol etmen gereken notların var"
        // Genişletilince: gerçek satır satır tam metin
        val fullText = lines.joinToString("\n")

        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            android.content.Intent(context, com.benim.notdefteri.MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.benim.notdefteri.R.drawable.ic_launcher_foreground)
            .setContentTitle("Hatırlatma")
            .setContentText(summary)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(fullText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(1002, notification)
    }

    fun sendChatMessage(
        text: String,
        imagePath: String? = null,
        filePath: String? = null,
        fileName: String? = null
    ) = viewModelScope.launch {
        if ((text.isBlank() && imagePath == null && filePath == null) || _isChatLoading.value) return@launch
        repository.addChatMessage("user", text.trim(), imagePath, filePath, fileName)
        _isChatLoading.value = true

        // Ekli dosyayı hazırla: PDF ise bayt olarak, metinse içerik olarak gönder
        var pdfBytes: ByteArray? = null
        var fileText: String? = null
        if (filePath != null) {
            try {
                val f = java.io.File(filePath)
                if ((fileName ?: "").endsWith(".pdf", ignoreCase = true)) {
                    if (f.length() <= 15 * 1024 * 1024) pdfBytes = f.readBytes() // 15MB üstünü gönderme
                } else {
                    fileText = f.readText()
                }
            } catch (e: Exception) {
                // Dosya okunamazsa sessizce atla
            }
        }

        // Fotoğraf varsa küçülterek yükle (AI'ya dev dosya gitmesin)
        val bitmap = imagePath?.let { path ->
            try {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1536 || bounds.outHeight / sample > 1536) sample *= 2
                android.graphics.BitmapFactory.decodeFile(
                    path,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                )
            } catch (e: Exception) {
                null
            }
        }

        val notes = repository.allNotes.first()
        val logList = repository.allLogs.first()
        // Son 20 mesajı bağlam olarak gönder (kota tasarrufu)
        val history = repository.allChatMessages.first().takeLast(20).map { it.role to it.text }
        val reply = repository.chatAboutNotes(
            history = history,
            notes = notes,
            logs = logList,
            image = bitmap,
            pdfBytes = pdfBytes,
            fileText = fileText,
            attachedFileName = fileName
        )

        repository.addChatMessage("model", reply)
        _isChatLoading.value = false
    }

    fun clearChat() = viewModelScope.launch {
        repository.clearChat()
    }

    fun fetchAiReminders() = viewModelScope.launch {
        _isLoadingAi.value = true
        val reminders = repository.getAiReminders(_allNotes.value)
        _aiReminders.value = reminders
        _isLoadingAi.value = false
    }
}
