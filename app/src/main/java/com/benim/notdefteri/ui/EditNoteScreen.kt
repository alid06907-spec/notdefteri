package com.benim.notdefteri.ui

// --- SESLİ NOT KAYDI (opsiyonel özellik) ---
// Notu sesli kaydedip dinleme özelliği. Kaldırmak istersen: bu importlar,
// aşağıdaki "SESLİ NOT" yorum blokları arasındaki kod ve Note(...) içindeki
// audioPath satırı silinir (Note entity'sindeki alan kalabilir, zararsız).
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
// --- SESLİ NOT SONU ---

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benim.notdefteri.data.Note
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteScreen(
    note: Note? = null,
    initialDate: Long = System.currentTimeMillis(),
    onSave: (Note) -> Unit,
    onBack: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var date by remember { mutableLongStateOf(note?.date ?: initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // --- ALARM / HATIRLATICI (Yeni Not ve Notu Düzenle ekranından da kurulabilir) ---
    var reminderTime by remember { mutableStateOf(note?.reminderTime) }
    var reminderEnabled by remember { mutableStateOf(note?.reminderEnabled ?: false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    // --- ALARM SONU ---

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = date)

    // --- SESLİ NOT KAYDI (opsiyonel özellik, kaldırılabilir) ---
    val context = LocalContext.current
    var audioPath by remember { mutableStateOf(note?.audioPath) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var waveformWidthPx by remember { mutableIntStateOf(0) }
    val mediaRecorderRef = remember { mutableStateOf<MediaRecorder?>(null) }
    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }
    val recordedAmplitudes = remember { mutableStateListOf<Int>() } // Kayıt sırasında yakalanan ham ses şiddeti
    val waveformBarCount = 40
    var waveformBars by remember {
        mutableStateOf<List<Int>>(
            note?.waveform?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        )
    }

    fun formatMs(ms: Int): String {
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    // Ham genlik örneklerini sabit sayıda çubuğa indirger (kayıt süresi ne olursa olsun aynı genişlik)
    // ve kaydın kendi en yüksek sesine göre 0-100 aralığına ölçekler — böylece sessiz konuşmalarda
    // bile çubuklar arasında belirgin fark olur, hepsi aynı düz çizgi gibi görünmez.
    fun downsampleAmplitudes(raw: List<Int>, bars: Int): List<Int> {
        if (raw.isEmpty()) return emptyList()
        val bucketed = if (raw.size <= bars) {
            List(bars) { i -> raw[(i * raw.size / bars).coerceIn(0, raw.size - 1)] }
        } else {
            val bucketSize = raw.size.toDouble() / bars
            List(bars) { i ->
                val start = (i * bucketSize).toInt()
                val end = ((i + 1) * bucketSize).toInt().coerceAtLeast(start + 1).coerceAtMost(raw.size)
                val slice = raw.subList(start, end)
                if (slice.isEmpty()) 0 else slice.average().toInt()
            }
        }
        val maxVal = (bucketed.maxOrNull() ?: 1).coerceAtLeast(1)
        return bucketed.map { ((it * 100) / maxVal).coerceIn(0, 100) }
    }

    fun releasePlayer() {
        val player = mediaPlayerRef.value
        if (player != null) {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        mediaPlayerRef.value = null
        isPlaying = false
        currentPositionMs = 0
    }

    fun startRecording() {
        releasePlayer()
        recordedAmplitudes.clear()
        waveformBars = emptyList<Int>()
        val dir = File(context.filesDir, "audio").apply { mkdirs() }
        val file = File(dir, "note_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorderRef.value = recorder
            audioPath = file.absolutePath
            recordSeconds = 0
            durationMs = 0
            isRecording = true
        } catch (e: Exception) {
            Toast.makeText(context, "Ses kaydı başlatılamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording() {
        try {
            mediaRecorderRef.value?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        mediaRecorderRef.value = null
        isRecording = false
        waveformBars = downsampleAmplitudes(recordedAmplitudes.toList(), waveformBarCount)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else Toast.makeText(context, "Sesli not için mikrofon izni gerekli.", Toast.LENGTH_SHORT).show()
    }

    fun onMicClick() {
        if (isRecording) {
            stopRecording()
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) startRecording() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Oynat/duraklat — aynı MediaPlayer örneği korunur, durduğunda konum kaybolmaz
    fun playPause() {
        val path = audioPath ?: return
        try {
            var player = mediaPlayerRef.value
            if (player == null) {
                player = MediaPlayer().apply {
                    setDataSource(path)
                    setOnCompletionListener {
                        isPlaying = false
                        currentPositionMs = 0
                        seekTo(0)
                    }
                    prepare()
                }
                mediaPlayerRef.value = player
                if (durationMs <= 0) durationMs = player.duration
            }
            if (isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.seekTo(currentPositionMs)
                player.start()
                isPlaying = true
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ses oynatılamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    // Kullanıcı ilerleme çubuğunu sürüklediğinde: hem duraklatılmışken hem çalarken çalışır
    // (İsim çakışmasına yer bırakmamak için MediaPlayer.seekTo() ile karışmasın diye "seekAudioTo" adı kullanıldı.)
    fun seekAudioTo(ms: Int) {
        currentPositionMs = ms
        val path = audioPath ?: return
        var player = mediaPlayerRef.value
        if (player == null) {
            try {
                player = MediaPlayer().apply {
                    setDataSource(path)
                    setOnCompletionListener {
                        isPlaying = false
                        currentPositionMs = 0
                        seekTo(0)
                    }
                    prepare()
                }
                mediaPlayerRef.value = player
                if (durationMs <= 0) durationMs = player.duration
            } catch (_: Exception) { return }
        }
        player.seekTo(ms)
    }

    fun deleteAudio() {
        releasePlayer()
        audioPath?.let { runCatching { File(it).delete() } }
        audioPath = null
        durationMs = 0
        currentPositionMs = 0
        waveformBars = emptyList<Int>()
        recordedAmplitudes.clear()
    }

    // Kayıt sırasında saniye sayacı
    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000)
            recordSeconds++
        }
    }

    // Kayıt sırasında ses şiddetini örnekle (waveform için)
    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(120)
            val amp = mediaRecorderRef.value?.maxAmplitude ?: 0
            recordedAmplitudes.add((amp * 100 / 32767).coerceIn(0, 100))
        }
    }

    // Kayıt biter bitmez (veya mevcut bir not açılınca) süreyi hesapla
    LaunchedEffect(audioPath, isRecording) {
        val path = audioPath
        if (path != null && !isRecording) {
            durationMs = withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(path)
                    val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
                    retriever.release()
                    d
                } catch (e: Exception) {
                    0
                }
            }
        }
    }

    // Çalarken ilerleme çubuğunu güncelle
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(200)
            mediaPlayerRef.value?.let { currentPositionMs = it.currentPosition }
        }
    }

    // Ekrandan çıkarken kayıt/oynatmayı serbest bırak (bellek sızıntısı olmasın)
    DisposableEffect(Unit) {
        onDispose {
            runCatching { mediaRecorderRef.value?.release() }
            runCatching { mediaPlayerRef.value?.release() }
        }
    }
    // --- SESLİ NOT SONU ---

    // Ekran açılınca başlığa odaklan, klavye otomatik açılsın
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (note == null) titleFocus.requestFocus() // Sadece yeni notta
    }

    // Şeffaf, çizgisiz modern text field renkleri
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        cursorColor = Color.White,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedPlaceholderColor = Color(0xFF5A5F68),
        unfocusedPlaceholderColor = Color(0xFF5A5F68)
    )

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                title = {
                    Text(
                        if (note == null) "Yeni Not" else "Notu Düzenle",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (note != null && onDelete != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color(0xFFE57373))
                        }
                    }
                    // Kaydet: dolgulu yuvarlak buton
                    Surface(
                        onClick = {
                            if (isRecording) stopRecording()
                            onSave(
                                Note(
                                    id = note?.id ?: 0,
                                    title = title,
                                    content = content,
                                    date = date,
                                    isImportant = note?.isImportant ?: false,
                                    isDone = note?.isDone ?: false,
                                    reminderTime = reminderTime,
                                    reminderEnabled = reminderEnabled,
                                    aiSummary = note?.aiSummary,
                                    audioPath = audioPath,
                                    waveform = waveformBars.takeIf { it.isNotEmpty() }?.joinToString(","),
                                    audioDurationMs = if (audioPath != null) durationMs else null
                                )
                            )
                        },
                        color = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Kaydet",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Tarih çipi — basınca takvim açılır
                Surface(
                    onClick = { showDatePicker = true },
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .height(38.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "Tarih Seç",
                            tint = Color(0xFF8E94A3),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = SimpleDateFormat("d MMM yyyy, EEE", Locale.forLanguageTag("tr")).format(Date(date)),
                            color = Color(0xFFB8BCC4),
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Saat çipi — notun saati, basınca saat seçici açılır
                Surface(
                    onClick = { showTimePicker = true },
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .height(38.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = "Saat Seç",
                            tint = Color(0xFF8E94A3),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(date)),
                            color = Color(0xFFB8BCC4),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Alarm çipi — diğer çiplerle aynı boyut/stil, hatırlatıcı kuruluysa vurgulu renk
                val reminderOn = reminderTime != null && reminderEnabled
                Surface(
                    onClick = { showReminderDialog = true },
                    color = if (reminderOn) Color(0xFF7C9CF5).copy(alpha = 0.18f) else Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .height(38.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (reminderOn) Icons.Default.Alarm else Icons.Default.AlarmOff,
                            contentDescription = "Hatırlatıcı kur",
                            tint = if (reminderOn) Color(0xFF7C9CF5) else Color(0xFF8E94A3),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Başlık — büyük, çizgisiz
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text("Başlık", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
            )

            // Başlık ile içerik arasında ince ayırıcı çizgi
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.7.dp,
                color = Color.White.copy(alpha = 0.12f)
            )

            // --- SESLİ NOT alanı (opsiyonel özellik, kaldırılabilir) ---
            // İçerik yazma alanının hemen üstünde: notunu sesli de kaydedebilirsin.
            when {
                isRecording -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = { onMicClick() },
                            color = Color(0xFF3A1E1E),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Kaydı durdur",
                                    tint = Color(0xFFE57373),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Kaydediliyor... ${formatMs(recordSeconds * 1000)}",
                                    color = Color(0xFFE57373),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
                audioPath != null -> {
                    // Çubuk yoksa (eski kayıt vb.) sabit dekoratif bir desen göster
                    val displayBars = remember(waveformBars) {
                        if (waveformBars.isNotEmpty()) waveformBars
                        else List(40) { i -> 25 + ((i * 37) % 55) }
                    }
                    val playedFraction = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f
                    val playedCount = (displayBars.size * playedFraction).toInt().coerceIn(0, displayBars.size)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { playPause() }) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Duraklat" else "Dinle",
                                    tint = Color(0xFF7C9CF5)
                                )
                            }
                            // Waveform: dokun veya sürükle, istediğin yere atla (Spotify/WhatsApp tarzı)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .onSizeChanged { waveformWidthPx = it.width }
                                    .pointerInput(durationMs) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown()
                                            if (durationMs > 0 && waveformWidthPx > 0) {
                                                val fraction = (down.position.x / waveformWidthPx).coerceIn(0f, 1f)
                                                seekAudioTo((fraction * durationMs).toInt())
                                            }
                                            drag(down.id) { change ->
                                                if (durationMs > 0 && waveformWidthPx > 0) {
                                                    val fraction = (change.position.x / waveformWidthPx).coerceIn(0f, 1f)
                                                    seekAudioTo((fraction * durationMs).toInt())
                                                }
                                                change.consume()
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    displayBars.forEachIndexed { index, amp ->
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .fillMaxHeight(amp.coerceIn(6, 100) / 100f)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(
                                                    if (index < playedCount) Color(0xFF7C9CF5) else Color(0xFF3A3A3A)
                                                )
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { deleteAudio() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Sesli notu sil",
                                    tint = Color(0xFF8E94A3)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 48.dp, end = 48.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatMs(currentPositionMs), color = Color(0xFF6E7480), fontSize = 10.sp)
                            Text(formatMs(durationMs), color = Color(0xFF6E7480), fontSize = 10.sp)
                        }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = { onMicClick() },
                            color = Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Sesli not ekle",
                                    tint = Color(0xFF8E94A3),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Sesli not ekle",
                                    color = Color(0xFFB8BCC4),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
            // --- SESLİ NOT SONU ---

            // İçerik — kalan alanı doldurur
            TextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text("Notunu buraya yaz...") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFD8DBE0)),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        if (showTimePicker) {
            val cal = Calendar.getInstance().apply { timeInMillis = date }
            val tpState = rememberTimePickerState(
                initialHour = cal.get(Calendar.HOUR_OF_DAY),
                initialMinute = cal.get(Calendar.MINUTE),
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                containerColor = Color(0xFF1E1E1E),
                titleContentColor = Color.White,
                title = { Text("Saat seç") },
                text = { TimePicker(state = tpState) },
                confirmButton = {
                    TextButton(onClick = {
                        date = Calendar.getInstance().apply {
                            timeInMillis = date
                            set(Calendar.HOUR_OF_DAY, tpState.hour)
                            set(Calendar.MINUTE, tpState.minute)
                        }.timeInMillis
                        showTimePicker = false
                    }) { Text("Tamam", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text("İptal", color = Color.Gray)
                    }
                }
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = Color(0xFF1E1E1E),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFB8BCC4),
                title = { Text("Notu sil") },
                text = { Text("Bu not kalıcı olarak silinecek. Emin misin?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    }) { Text("Sil", color = Color(0xFFE57373)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("İptal", color = Color.Gray)
                    }
                }
            )
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                colors = DatePickerDefaults.colors(containerColor = Color(0xFF1E1E1E)),
                confirmButton = {
                    TextButton(onClick = {
                        date = datePickerState.selectedDateMillis ?: date
                        showDatePicker = false
                    }) {
                        Text("Tamam", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("İptal", color = Color.Gray)
                    }
                }
            ) {
                DatePicker(
                    state = datePickerState,
                    colors = DatePickerDefaults.colors(
                        containerColor = Color(0xFF1E1E1E),
                        titleContentColor = Color.White,
                        headlineContentColor = Color.White,
                        weekdayContentColor = Color.Gray,
                        subheadContentColor = Color.Gray,
                        navigationContentColor = Color.White,
                        dayContentColor = Color.White,
                        selectedDayContentColor = Color.Black,
                        selectedDayContainerColor = Color.White,
                        todayContentColor = Color.White,
                        todayDateBorderColor = Color.White
                    )
                )
            }
        }

        // --- ALARM / HATIRLATICI ayar penceresi ---
        if (showReminderDialog) {
            var tempReminderTime by remember { mutableLongStateOf(reminderTime ?: date) }
            var showRDatePicker by remember { mutableStateOf(false) }
            var showRTimePicker by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showReminderDialog = false },
                containerColor = Color(0xFF1E1E1E),
                title = {
                    Text("Hatırlatıcı", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            onClick = { showRDatePicker = true },
                            color = Color(0xFF2A2A2A),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("tr")).format(Date(tempReminderTime)),
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            onClick = { showRTimePicker = true },
                            color = Color(0xFF2A2A2A),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(tempReminderTime)),
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            reminderTime = tempReminderTime
                            reminderEnabled = true
                            showReminderDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    Row {
                        if (reminderTime != null) {
                            TextButton(onClick = {
                                reminderTime = null
                                reminderEnabled = false
                                showReminderDialog = false
                            }) { Text("Kaldır", color = Color(0xFFE57373)) }
                        }
                        TextButton(onClick = { showReminderDialog = false }) {
                            Text("İptal", color = Color.Gray)
                        }
                    }
                }
            )

            if (showRDatePicker) {
                val dpState = rememberDatePickerState(initialSelectedDateMillis = tempReminderTime)
                DatePickerDialog(
                    onDismissRequest = { showRDatePicker = false },
                    colors = DatePickerDefaults.colors(containerColor = Color(0xFF1E1E1E)),
                    confirmButton = {
                        TextButton(onClick = {
                            dpState.selectedDateMillis?.let { sel ->
                                val old = Calendar.getInstance().apply { timeInMillis = tempReminderTime }
                                tempReminderTime = Calendar.getInstance().apply {
                                    timeInMillis = sel
                                    set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY))
                                    set(Calendar.MINUTE, old.get(Calendar.MINUTE))
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            }
                            showRDatePicker = false
                        }) { Text("Tamam", color = Color.White) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRDatePicker = false }) {
                            Text("İptal", color = Color.Gray)
                        }
                    }
                ) {
                    DatePicker(
                        state = dpState,
                        title = null,
                        colors = DatePickerDefaults.colors(
                            containerColor = Color(0xFF1E1E1E),
                            titleContentColor = Color.White,
                            headlineContentColor = Color.White,
                            weekdayContentColor = Color.Gray,
                            subheadContentColor = Color.Gray,
                            navigationContentColor = Color.White,
                            dayContentColor = Color.White,
                            selectedDayContentColor = Color.Black,
                            selectedDayContainerColor = Color.White,
                            todayContentColor = Color.White,
                            todayDateBorderColor = Color.White
                        )
                    )
                }
            }

            if (showRTimePicker) {
                val cal = Calendar.getInstance().apply { timeInMillis = tempReminderTime }
                val tpState = rememberTimePickerState(
                    initialHour = cal.get(Calendar.HOUR_OF_DAY),
                    initialMinute = cal.get(Calendar.MINUTE),
                    is24Hour = true
                )
                AlertDialog(
                    onDismissRequest = { showRTimePicker = false },
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    title = { Text("Saat seç") },
                    text = { TimePicker(state = tpState) },
                    confirmButton = {
                        TextButton(onClick = {
                            tempReminderTime = Calendar.getInstance().apply {
                                timeInMillis = tempReminderTime
                                set(Calendar.HOUR_OF_DAY, tpState.hour)
                                set(Calendar.MINUTE, tpState.minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                            showRTimePicker = false
                        }) { Text("Tamam", color = Color.White) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRTimePicker = false }) {
                            Text("İptal", color = Color.Gray)
                        }
                    }
                )
            }
        }
        // --- ALARM SONU ---
    }
}
