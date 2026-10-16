package com.benim.notdefteri.data

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val noteDao: NoteDao,
    private val logDao: LogDao,
    private val chatDao: ChatDao,
    private val appContext: Context
) {
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val allLogs: Flow<List<ActivityLog>> = logDao.getAllLogs()
    val allChatMessages: Flow<List<ChatMessage>> = chatDao.getAllMessages()

    suspend fun addChatMessage(
        role: String,
        text: String,
        imageUri: String? = null,
        fileUri: String? = null,
        fileName: String? = null
    ) = chatDao.insertMessage(
        ChatMessage(role = role, text = text, imageUri = imageUri, fileUri = fileUri, fileName = fileName)
    )

    suspend fun clearChat() = chatDao.clearMessages()

    suspend fun insert(note: Note): Long = noteDao.insertNote(note)
    suspend fun update(note: Note) = noteDao.updateNote(note)
    suspend fun delete(note: Note) = noteDao.deleteNote(note)

    suspend fun addLog(action: String, noteTitle: String) {
        logDao.insertLog(
            ActivityLog(
                action = action,
                noteTitle = noteTitle,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private val generativeModel = Firebase.ai.generativeModel(
        modelName = "gemini-flash-latest", // Güncel çalışan model (1.5-flash servis dışı)
    )

    // --- Cihaz başına günlük AI istek sınırı ---
    // Firebase projesinin AI kotası tüm kullanıcılar arasında paylaşılıyor (tek proje, tek fatura).
    // Bir kullanıcının (hatalı döngü, kötüye kullanım vb. yüzünden) kotanın tamamını tüketip
    // diğer kullanıcıları etkilemesini önlemek için cihaz başına günlük üst sınır koyuyoruz.
    private val aiLimitPrefs = appContext.getSharedPreferences("ai_usage_limit", Context.MODE_PRIVATE)
    private val dailyAiRequestLimit = 60

    private fun hasAiQuotaLeft(): Boolean {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val storedDay = aiLimitPrefs.getString("day", "")
        val usedToday = if (storedDay == today) aiLimitPrefs.getInt("count", 0) else 0
        if (usedToday >= dailyAiRequestLimit) return false
        aiLimitPrefs.edit().putString("day", today).putInt("count", usedToday + 1).apply()
        return true
    }

    // Teknik hata mesajlarını kullanıcı dostu kısa Türkçe mesaja çevir
    private fun friendlyError(e: Exception): String {
        val m = e.message ?: ""
        return when {
            m.contains("quota", ignoreCase = true) || m.contains("exceeded", ignoreCase = true) || m.contains("429") ->
                "Ücretsiz AI kullanım limitine takıldın. Bir iki dakika sonra tekrar dene."
            m.contains("network", ignoreCase = true) || m.contains("resolve", ignoreCase = true) || m.contains("timeout", ignoreCase = true) ->
                "İnternet bağlantısı yok gibi görünüyor. Bağlantını kontrol edip tekrar dene."
            m.contains("not found", ignoreCase = true) || m.contains("404") ->
                "Model bulunamadı. (Detay: ${m.take(120)})"
            else -> "Bir şeyler ters gitti, tekrar dener misin?\n(Detay: ${m.take(150)})"
        }
    }

    // Sohbet: kullanıcının tüm notlarını ve işlem geçmişini bilerek cevap verir
    suspend fun chatAboutNotes(
        history: List<Pair<String, String>>, // rol ("user"/"model") -> mesaj
        notes: List<Note>,
        logs: List<ActivityLog>,
        image: android.graphics.Bitmap? = null, // Ekli fotoğraf (varsa)
        pdfBytes: ByteArray? = null,            // Ekli PDF içeriği (varsa)
        fileText: String? = null,               // Ekli metin dosyası içeriği (varsa)
        attachedFileName: String? = null        // Ekli dosyanın adı
    ): String {
        if (!hasAiQuotaLeft()) {
            return "Bugünlük AI kullanım sınırına ulaştın. Yarın tekrar deneyebilirsin."
        }
        val df = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm")
        val prompt = buildString {
            appendLine("Sen 'Not Defterim' uygulamasının zeki asistanısın. Kullanıcının notlarına tam erişimin var. Türkçe, samimi ve çözüm odaklı cevap ver.")
            appendLine("Bugünün tarihi: ${java.text.SimpleDateFormat("dd/MM/yyyy EEEE", java.util.Locale.forLanguageTag("tr")).format(java.util.Date())}")
            appendLine("\n### KONU SINIRI (ÇOK ÖNEMLİ)")
            appendLine("Sadece şu konularda yardımcı olursun: kullanıcının notları, hatırlatıcılar, görevler, uygulamanın kendisi ve takvim/tarih ile ilgili basit hesaplamalar.")
            appendLine("Kullanıcı bunların dışında bir şey sorarsa (genel sohbet, güncel olaylar, ödev/kod yazdırma, tarif, çeviri, kişisel tavsiye, başka bir uygulama/konu vb.) KESİNLİKLE o konuda cevap ÜRETME.")
            appendLine("Bunun yerine SADECE şunu yaz, başka hiçbir şey ekleme: \"Ben sadece notların ve hatırlatmaların hakkında yardımcı olabilirim.\"")
            appendLine("Konu dışı isteklerde uzun açıklama yapma, sebep anlatma, özür dileme — tek cümlelik bu uyarıyı ver ve bitir.")
            appendLine("\n### NOT EKLEME YETENEĞİ")
            appendLine("Kullanıcı yeni bir not eklemeni isterse (Örn: 'Yarın markete gitmemi not al', 'Bunu not et', 'Hatırlatıcı ekle'), işlemi gerçekleştir ve cevabının sonuna MUTLAKA şu formatta ekle:")
            appendLine("[ADD_NOTE: Başlık | İçerik | Tarih(GG/AA/YYYY)]")
            appendLine("- Başlık: Notun kısa başlığı")
            appendLine("- İçerik: Notun detayları")
            appendLine("- Tarih: Eğer kullanıcı 'yarın', 'salı günü' gibi ifadeler kullanırsa ona göre hesapla. Belirtilmezse bugünün tarihini kullan.")
            
            appendLine("\n### KULLANICI NOTLARI")
            if (notes.isEmpty()) appendLine("(Henüz hiç not yok)")
            notes.forEach {
                appendLine("- [${df.format(java.util.Date(it.date))}] \"${it.title}\": ${it.content} | Durum: ${if (it.isDone) "Yapıldı" else "Yapılmadı"}")
            }
            appendLine("\n--- SOHBET GEÇMİŞİ ---")
            history.forEach { (role, text) ->
                appendLine(if (role == "user") "Kullanıcı: $text" else "Asistan: $text")
            }
            if (image != null) appendLine("(Kullanıcı son mesajına bir fotoğraf ekledi, fotoğrafı incele.)")
            if (pdfBytes != null) appendLine("(Kullanıcı '${attachedFileName ?: "dosya"}' adlı PDF ekledi, içeriğini incele.)")
            if (fileText != null) {
                appendLine("\n--- EKLİ DOSYA: ${attachedFileName ?: "dosya"} ---")
                appendLine(fileText.take(10000)) // Çok uzunsa kırp
                appendLine("--- DOSYA SONU ---")
            }
            append("Asistan:")
        }

        return try {
            val response = generativeModel.generateContent(
                content {
                    if (image != null) image(image)
                    if (pdfBytes != null) inlineData(pdfBytes, "application/pdf")
                    text(prompt)
                }
            )
            var reply = response.text?.trim() ?: "Cevap üretilemedi."
            
            // Not ekleme komutunu parse et
            if (reply.contains("[ADD_NOTE:")) {
                val command = reply.substringAfter("[ADD_NOTE:").substringBefore("]")
                val parts = command.split("|").map { it.trim() }
                if (parts.size >= 2) {
                    val title = parts[0]
                    val content = parts[1]
                    var date = System.currentTimeMillis()
                    
                    if (parts.size >= 3) {
                        try {
                            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.forLanguageTag("tr"))
                            date = sdf.parse(parts[2])?.time ?: date
                        } catch (e: Exception) {}
                    }
                    
                    insert(Note(title = title, content = content, date = date))
                    addLog("AI ile eklendi", title)
                    
                    // Komut kısmını kullanıcıdan gizle
                    reply = reply.substringBefore("[ADD_NOTE:").trim() + "\n\n(Not başarıyla eklendi! ✅)"
                }
            }
            
            reply
        } catch (e: Exception) {
            friendlyError(e)
        }
    }

    // Unutulan / yaklaşan notlar için hatırlatma mesajı üretir.
    // AI'ya bırakmak yerine burada elle, sabit ve her zaman satır satır bir biçimde kuruluyor —
    // böylece format asla bozulmuyor ve AI kotası/gecikmesi de gerekmiyor.
    suspend fun getForgottenReminder(
        pastUndone: List<Note>,
        todayNotes: List<Note>,
        tomorrowNotes: List<Note>
    ): String {
        val df = java.text.SimpleDateFormat("d MMMM", java.util.Locale.forLanguageTag("tr"))

        fun dayStart(time: Long): Long = java.util.Calendar.getInstance().apply {
            timeInMillis = time
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun bulletBlock(header: String, notes: List<Note>) = buildString {
            appendLine(header)
            notes.forEach { appendLine("· ${it.title.ifBlank { "(Başlıksız not)" }}") }
        }

        return buildString {
            if (pastUndone.isNotEmpty()) {
                val byDate = pastUndone.groupBy { dayStart(it.date) }.toSortedMap()
                byDate.forEach { (dayMillis, notes) ->
                    val dateLabel = df.format(java.util.Date(dayMillis))
                    append(bulletBlock("$dateLabel tarihinde şunları yapmayı unuttun:", notes))
                    appendLine()
                }
            }
            if (todayNotes.isNotEmpty()) {
                append(bulletBlock("Bugün bunları yapmayı unutma:", todayNotes))
                appendLine()
            }
            if (tomorrowNotes.isNotEmpty()) {
                append(bulletBlock("Yarın için planların:", tomorrowNotes))
            }
        }.trim()
    }

    suspend fun getAiReminders(notes: List<Note>): String {
        if (notes.isEmpty()) return "Henüz notunuz yok."
        if (!hasAiQuotaLeft()) return "Bugünlük AI kullanım sınırına ulaştın. Yarın tekrar deneyebilirsin."

        val prompt = """
            Aşağıdaki notlarımı incele ve bana önemli olanları, unuttuğum veya yakın tarihte yapmam gerekenleri hatırlat:
            ${notes.joinToString("\n") { "Başlık: ${it.title}, İçerik: ${it.content}, Tarih: ${java.text.SimpleDateFormat("dd/MM/yyyy").format(java.util.Date(it.date))}" }}
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(
                content { text(prompt) }
            )
            response.text ?: "Hatırlatıcı oluşturulamadı."
        } catch (e: Exception) {
            friendlyError(e)
        }
    }
}
