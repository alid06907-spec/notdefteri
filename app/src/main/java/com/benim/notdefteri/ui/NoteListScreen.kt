package com.benim.notdefteri.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawWithContent
import com.benim.notdefteri.data.Note
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NoteViewModel = viewModel(),
    onAddNoteClick: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onLogClick: () -> Unit = {},
    onAiChatClick: () -> Unit = {}
) {
    val notes by viewModel.filteredNotes.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val undoneCounts by viewModel.undoneCountByDay.collectAsState()
    val autoReminder by viewModel.autoReminder.collectAsState()

    // Açılışta unutulan/yaklaşan notları kontrol et
    LaunchedEffect(Unit) { viewModel.checkForgottenReminder() }

    // Hatırlatıcı ayar sayfası (alt sayfa olarak açılır)
    var reminderSheetNote by remember { mutableStateOf<Note?>(null) }
    var showFeedback by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // --- SESLİ NOT: ana ekrandan tek dokunuşla dinleme (opsiyonel özellik, kaldırılabilir) ---
    var playingAudioNoteId by remember { mutableStateOf<Long?>(null) }
    var playingPositionMs by remember { mutableIntStateOf(0) }
    var playingDurationMs by remember { mutableIntStateOf(0) }
    val listAudioPlayerRef = remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    fun stopListAudio() {
        listAudioPlayerRef.value?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        listAudioPlayerRef.value = null
        playingAudioNoteId = null
        playingPositionMs = 0
        playingDurationMs = 0
    }

    fun toggleListAudio(note: Note) {
        val path = note.audioPath ?: return
        if (playingAudioNoteId == note.id) {
            stopListAudio()
            return
        }
        stopListAudio()
        try {
            val player = android.media.MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { stopListAudio() }
                prepare()
                start()
            }
            listAudioPlayerRef.value = player
            playingAudioNoteId = note.id
            playingDurationMs = player.duration
        } catch (_: Exception) {
            playingAudioNoteId = null
        }
    }

    // Waveform'a dokunup/sürükleyip istediğin yere atlama
    fun seekListAudio(note: Note, ms: Int) {
        if (playingAudioNoteId != note.id) {
            toggleListAudio(note)
        }
        listAudioPlayerRef.value?.seekTo(ms)
        playingPositionMs = ms
    }

    // Çalarken ilerleme çubuğunu güncelle
    LaunchedEffect(playingAudioNoteId) {
        while (playingAudioNoteId != null) {
            delay(150)
            listAudioPlayerRef.value?.let { playingPositionMs = it.currentPosition }
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopListAudio() }
    }
    // --- SESLİ NOT SONU ---

    Scaffold(
        containerColor = Color(0xFF121212), // Koyu grimsi siyah tema
        floatingActionButton = {
            FloatingActionButton(
                containerColor = Color(0xFF333333),
                contentColor = Color.White,
                onClick = onAddNoteClick
            ) {
                Icon(Icons.Default.Add, contentDescription = "Not Ekle")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color(0xFF121212))
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { showFeedback = true }) {
                        Icon(Icons.Default.Feedback, contentDescription = "Geri Bildirim", tint = Color(0xFF8E94A3))
                    }
                    IconButton(onClick = onAiChatClick) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Asistan", tint = Color(0xFF7C9CF5))
                    }
                }
                IconButton(
                    modifier = Modifier.align(Alignment.TopStart),
                    onClick = onLogClick
                ) {
                    Icon(Icons.Default.History, contentDescription = "İşlem Geçmişi", tint = Color.White)
                }
                Column {
                    HorizontalCalendarStrip(
                        selectedDate = selectedDate,
                        onDateSelected = { viewModel.onDateSelected(it) },
                        listState = listState,
                        coroutineScope = coroutineScope,
                        undoneCounts = undoneCounts
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    if (notes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Bu tarihte not bulunamadı.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(notes) { note ->
                                NoteItem(
                                    note = note,
                                    onClick = { onNoteClick(note) },
                                    onToggleDone = { viewModel.toggleDone(note) },
                                    onReminderClick = {
                                        if (note.reminderTime == null) {
                                            reminderSheetNote = note // İlk kez: ayar sayfası açılır
                                        } else {
                                            viewModel.toggleReminder(note) // Kuruluysa: aç/kapat
                                        }
                                    },
                                    onReminderLongClick = { reminderSheetNote = note }, // Basılı tut: ayar sayfası
                                    isAudioPlaying = playingAudioNoteId == note.id,
                                    onToggleAudio = { toggleListAudio(note) },
                                    onSeekAudio = { ms -> seekListAudio(note, ms) },
                                    audioPositionMs = if (playingAudioNoteId == note.id) playingPositionMs else 0
                                )
                            }
                        }
                    }
                }

                // "Günümüze Git" Butonu silindi
            }
        }

        // Hatırlatıcı ayar sayfası
        reminderSheetNote?.let { note ->
            var tempTime by remember(note.id) {
                mutableLongStateOf(note.reminderTime ?: System.currentTimeMillis())
            }
            var showRDatePicker by remember { mutableStateOf(false) }
            var showRTimePicker by remember { mutableStateOf(false) }

            // Ekranın ortasında açılan Hatırlatıcı penceresi
            AlertDialog(
                onDismissRequest = { reminderSheetNote = null },
                containerColor = Color(0xFF1E1E1E),
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Hatırlatıcı",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            note.title.ifBlank { "(Başlıksız not)" },
                            color = Color(0xFF8E94A3),
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                },
                text = {
                    // Tarih ve saat çipleri
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
                                text = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("tr")).format(Date(tempTime)),
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
                                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(tempTime)),
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
                            viewModel.setReminder(note, tempTime)
                            reminderSheetNote = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    Row {
                        if (note.reminderTime != null) {
                            TextButton(onClick = {
                                viewModel.clearReminder(note)
                                reminderSheetNote = null
                            }) { Text("Kaldır", color = Color(0xFFE57373)) }
                        }
                        TextButton(onClick = { reminderSheetNote = null }) {
                            Text("İptal", color = Color.Gray)
                        }
                    }
                }
            )

            // Tarih seçici
            if (showRDatePicker) {
                val dpState = rememberDatePickerState(initialSelectedDateMillis = tempTime)
                DatePickerDialog(
                    onDismissRequest = { showRDatePicker = false },
                    colors = DatePickerDefaults.colors(containerColor = Color(0xFF1E1E1E)),
                    confirmButton = {
                        TextButton(onClick = {
                            dpState.selectedDateMillis?.let { sel ->
                                // Saat kısmını koru, sadece günü değiştir
                                val old = Calendar.getInstance().apply { timeInMillis = tempTime }
                                tempTime = Calendar.getInstance().apply {
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

            // Saat seçici
            if (showRTimePicker) {
                val cal = Calendar.getInstance().apply { timeInMillis = tempTime }
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
                            tempTime = Calendar.getInstance().apply {
                                timeInMillis = tempTime
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

        // Açılışta otomatik AI hatırlatması
        autoReminder?.let { msg ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissAutoReminder() },
                containerColor = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(24.dp),
                icon = {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF7C9CF5).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color(0xFF7C9CF5),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                title = {
                    Text("Hatırlatma", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        msg.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
                            val isBullet = line.startsWith("·")
                            Text(
                                text = line,
                                color = if (isBullet) Color(0xFFB8BCC4) else Color.White,
                                fontSize = if (isBullet) 13.sp else 14.sp,
                                fontWeight = if (isBullet) FontWeight.Normal else FontWeight.SemiBold,
                                lineHeight = 19.sp,
                                modifier = if (isBullet) Modifier.padding(start = 4.dp, top = 2.dp) else Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissAutoReminder() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C9CF5)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Anladım", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        if (showFeedback) {
            FeedbackDialog(onDismiss = { showFeedback = false })
        }

    }
}

@Composable
fun HorizontalCalendarStrip(
    selectedDate: Long,
    onDateSelected: (Long) -> Unit,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    undoneCounts: Map<Long, Int> = emptyMap()
) {
    var showDatePicker by remember { mutableStateOf(false) }

    val dates = remember {
        List(365) { index ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, index - 182)
            cal.time
        }
    }

    // Header: Month Name (Only Month) - Anlık güncellenmesi için türetilmiş state kullanıyoruz
    val currentMonthName by remember(dates) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
            val centerItem = layoutInfo.visibleItemsInfo.minByOrNull { 
                val itemCenter = it.offset + (it.size / 2f)
                abs(itemCenter - viewportCenter)
            }
            val date = centerItem?.let { dates.getOrNull(it.index) } ?: Date(selectedDate)
            SimpleDateFormat("MMMM", Locale.forLanguageTag("tr")).format(date)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp), // Sabit yükseklik ile font kaymasını engelliyoruz (ğ kuyruğu sığsın)
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // "Günümüze Git" Butonu (Ay yazısının üstünde ve daha saydam)
                val isToday = remember(selectedDate) {
                    isSameDay(selectedDate, System.currentTimeMillis())
                }

                Box(
                    modifier = Modifier
                        .offset(y = (-6).dp)
                        .height(30.dp) // Biraz daha geniş alan
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isToday) {
                        Surface(
                            onClick = {
                                val today = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                                onDateSelected(today)
                                coroutineScope.launch {
                                    listState.scrollToItem(182 - 3)
                                }
                            },
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "GÜNÜMÜZE GİT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.4f),
                                    letterSpacing = 0.5.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Text(
                    text = currentMonthName.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E1E1E)) // Kapsülle aynı tema rengi
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null // Kare parlama olmasın
                        ) { showDatePicker = true }
                        .padding(horizontal = 16.dp, vertical = 3.dp)
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            // LazyRow'un 12dp yatay padding'i düşülür ki 7 hücre viewport'a tam otursun
            // (aksi halde tıklayınca merkez 12dp kayıyordu)
            val itemWidth = (maxWidth - 24.dp) / 7

            // Ekranda o an gözüken 7 günün merkez indeksi (currentMonthName ile aynı mantık)
            val centerIndex by remember(dates) {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                    layoutInfo.visibleItemsInfo.minByOrNull {
                        val itemCenter = it.offset + (it.size / 2f)
                        abs(itemCenter - viewportCenter)
                    }?.index ?: 182
                }
            }

            fun dayKeyOf(d: Date): Long = Calendar.getInstance().apply {
                time = d
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Ekranda gözüken 7 günün en solu/en sağı (merkez ± 3) — bunun dışında unutulmuş not varsa köşe işareti
            val visibleStartKey = dates.getOrNull((centerIndex - 3).coerceIn(0, dates.size - 1))?.let { dayKeyOf(it) }
            val visibleEndKey = dates.getOrNull((centerIndex + 3).coerceIn(0, dates.size - 1))?.let { dayKeyOf(it) }
            val hasPastUndone = visibleStartKey != null && undoneCounts.any { it.key < visibleStartKey && it.value > 0 }
            val hasFutureUndone = visibleEndKey != null && undoneCounts.any { it.key > visibleEndKey && it.value > 0 }

            // Koyu kapsül arka plan — ekranda gözükmeyen geçmiş/gelecek günlerde unutulmuş not varsa köşede ince kırmızı çizgi
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF1E1E1E))
            ) {
                // Şeritler kapsülün kendi köşe yuvarlaklığıyla (28dp) aynı çerçeveyi paylaşır:
                // dış Box zaten bu şekle clip'li olduğu için, kenara yaslanan ince şerit üstten/alttan
                // otomatik olarak aynı köşe eğrisini alır.
                if (hasPastUndone) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(7.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFE53935).copy(alpha = 0.55f), Color.Transparent)
                                )
                            )
                    )
                }
                if (hasFutureUndone) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(7.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color(0xFFE53935).copy(alpha = 0.55f))
                                )
                            )
                    )
                }
            }

            // Seçili gün vurgusu (sabit merkezde, hafif açık kutu)
            Box(
                modifier = Modifier
                    .requiredSize(width = 50.dp, height = 60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2F2F2F))
            )

            // Kayan Tarihler
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(28.dp)),
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
            ) {
                itemsIndexed(dates) { index, date ->
                    val cal = remember(date) { Calendar.getInstance().apply { time = date } }
                    val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                    // Gün başlangıcı (00:00) — yapılmamış not sayısı bununla bulunur
                    val dayKey = remember(date) {
                        Calendar.getInstance().apply {
                            time = date
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                    val undoneCount = undoneCounts[dayKey] ?: 0

                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .height(64.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null // Ripple (kare parlama) efektini kapat
                            ) {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index - 3)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val dayFormat = SimpleDateFormat("dd", Locale.getDefault())
                        val dayNameFormat = SimpleDateFormat("EEE", Locale.forLanguageTag("tr"))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Text(
                                text = dayNameFormat.format(date).uppercase(Locale.forLanguageTag("tr")),
                                fontWeight = FontWeight.Bold,
                                color = if (isSunday) Color(0xFFF05A5A) else Color(0xFF8E94A3),
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                letterSpacing = 1.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = dayFormat.format(date),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp,
                                lineHeight = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Yapılmamış not sayısı rozeti
                        if (undoneCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 6.dp, end = 6.dp)
                                    .size(15.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE53935)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (undoneCount > 9) "9+" else "$undoneCount",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    lineHeight = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Seçili günü ortala (widget'tan farklı bir günle açılmış olabilir)
    LaunchedEffect(selectedDate) {
        if (!listState.isScrollInProgress) {
            val zero: Calendar.() -> Unit = {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val today = Calendar.getInstance().apply(zero).timeInMillis
            val sel = Calendar.getInstance().apply {
                timeInMillis = selectedDate
                zero()
            }.timeInMillis
            val diffDays = ((sel - today) / (24 * 60 * 60 * 1000L)).toInt()
            listState.scrollToItem((182 + diffDays - 3).coerceIn(0, 364))
        }
    }

    // Kaydırma durduğunda tam ortadaki tarihi bul ve seç
    val isScrollInProgress = listState.isScrollInProgress
    LaunchedEffect(isScrollInProgress) {
        if (!isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            
            // Merkeze en yakın olan elemanı bul
            val centerItem = layoutInfo.visibleItemsInfo.minByOrNull { 
                val itemCenter = it.offset + (it.size / 2)
                abs(itemCenter - viewportCenter)
            }
            
            centerItem?.let {
                dates.getOrNull(it.index)?.let { date ->
                    onDateSelected(date.time)
                }
            }
        }
    }

    if (showDatePicker) {
        var showMonthGrid by remember { mutableStateOf(false) }
        // Diyalog içindeki geçici seçim için yerel state
        var tempSelectedDate by remember { mutableLongStateOf(selectedDate) }

        DatePickerDialog(
            onDismissRequest = { 
                showDatePicker = false
                showMonthGrid = false
            },
            colors = DatePickerDefaults.colors(
                containerColor = Color(0xFF1E1E1E),
            ),
            confirmButton = {
                TextButton(onClick = {
                    // Sadece TAMAM dendiğinde ana state'i güncelle
                    val finalDate = tempSelectedDate
                    onDateSelected(finalDate)
                    
                    val calendar = Calendar.getInstance().apply { timeInMillis = finalDate }
                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val diffMillis = calendar.timeInMillis - today.timeInMillis
                    val diffDays = (diffMillis / (24 * 60 * 60 * 1000)).toInt()
                    coroutineScope.launch {
                        listState.scrollToItem(182 + diffDays - 3)
                    }
                    showDatePicker = false
                    showMonthGrid = false
                }) { Text("Tamam", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDatePicker = false
                    showMonthGrid = false
                }) { Text("İptal", color = Color.Gray) }
            }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                // Custom Scrolling Month Picker Header
                ScrollingMonthPicker(
                    selectedDate = tempSelectedDate,
                    onMonthSelected = { newDateMillis ->
                        tempSelectedDate = newDateMillis
                    },
                    onMonthClick = { showMonthGrid = !showMonthGrid }
                )

                if (showMonthGrid) {
                    MonthGrid(
                        selectedMonth = Calendar.getInstance().apply { timeInMillis = tempSelectedDate }.get(Calendar.MONTH),
                        onMonthSelected = { monthIndex ->
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = tempSelectedDate
                                set(Calendar.MONTH, monthIndex)
                            }
                            tempSelectedDate = cal.timeInMillis
                            showMonthGrid = false
                        }
                    )
                } else {
                    // Rozetli özel gün ızgarası (yapılmamış not sayıları görünür)
                    DayGridWithBadges(
                        monthDate = tempSelectedDate,
                        selectedDate = tempSelectedDate,
                        undoneCounts = undoneCounts,
                        onDaySelected = { tempSelectedDate = it }
                    )
                }
            }
        }
    }
}

@Composable
fun ScrollingMonthPicker(
    selectedDate: Long,
    onMonthSelected: (Long) -> Unit,
    onMonthClick: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Geniş bir tarih aralığı oluşturuyoruz (10 yıl öncesinden 10 yıl sonrasına kadar aylar)
    val months = remember {
        val today = Calendar.getInstance()
        List(240) { index ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, index - 120)
            cal.time
        }
    }

    val currentMonthIndex = remember(selectedDate) {
        val selectedCal = Calendar.getInstance().apply { timeInMillis = selectedDate }
        val today = Calendar.getInstance()
        val yearDiff = selectedCal.get(Calendar.YEAR) - today.get(Calendar.YEAR)
        val monthDiff = selectedCal.get(Calendar.MONTH) - today.get(Calendar.MONTH)
        120 + (yearDiff * 12) + monthDiff
    }

    LaunchedEffect(currentMonthIndex) {
        if (!listState.isScrollInProgress) {
            listState.scrollToItem(currentMonthIndex)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        val itemWidth = maxWidth / 3
        val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = itemWidth),
            horizontalArrangement = Arrangement.Center,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
        ) {
            itemsIndexed(months) { index, date ->
                val cal = Calendar.getInstance().apply { time = date }
                
                // Animasyon hesaplamaları
                val itemOffset = listState.layoutInfo.visibleItemsInfo
                    .find { it.index == index }?.offset ?: 0
                
                // Merkeze olan uzaklığı 0 ile 1 arasında bir değere dönüştür
                // 0: Tam merkezde, 1: Tamamen yanda
                val distanceFromCenter = if (itemWidthPx > 0) {
                    min(1f, abs(itemOffset).toFloat() / itemWidthPx)
                } else 1f
                
                val isSelected = index == currentMonthIndex
                val monthName = SimpleDateFormat("MMMM", Locale.forLanguageTag("tr")).format(date).uppercase()
                val yearName = cal.get(Calendar.YEAR).toString()
                
                // Renk ve Font Kalınlığı interpolasyonu
                val textColor = lerp(Color.White, Color.Gray, distanceFromCenter)
                val fontWeight = if (distanceFromCenter < 0.5f) FontWeight.ExtraBold else FontWeight.Normal
                val fontSizeBase = if (monthName.length > 7) 18 else 22
                val fontSizeSide = if (monthName.length > 7) 14 else 16
                val animatedFontSize = (fontSizeSide + (fontSizeBase - fontSizeSide) * (1f - distanceFromCenter)).sp

                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                            onMonthSelected(date.time)
                            if (isSelected) onMonthClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = monthName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = fontWeight,
                            color = textColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            fontSize = animatedFontSize
                        )
                        if (distanceFromCenter < 0.5f) {
                            Text(
                                text = yearName,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Kaydırma durduğunda tam ortadaki ayı bul ve seç
    val isScrollInProgress = listState.isScrollInProgress
    LaunchedEffect(isScrollInProgress) {
        if (!isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            
            val centerItem = layoutInfo.visibleItemsInfo.minByOrNull { 
                val itemCenter = it.offset + (it.size / 2)
                abs(itemCenter - viewportCenter)
            }
            
            centerItem?.let {
                if (it.index in months.indices && it.index != currentMonthIndex) {
                    onMonthSelected(months[it.index].time)
                }
            }
        }
    }
}

// Takvim diyaloğundaki gün ızgarası: seçili gün, bugün ve kırmızı yapılmamış-not rozetleri
@Composable
fun DayGridWithBadges(
    monthDate: Long,
    selectedDate: Long,
    undoneCounts: Map<Long, Int>,
    onDaySelected: (Long) -> Unit
) {
    val monthCal = remember(monthDate) {
        Calendar.getInstance().apply {
            timeInMillis = monthDate
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // Hafta Pazartesi ile başlar
    val startOffset = ((monthCal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY) + 7) % 7

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        // Gün adları başlığı
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz").forEach { name ->
                Text(
                    text = name,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        var day = 1 - startOffset
        repeat(6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) {
                    val d = day++
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (d in 1..daysInMonth) {
                            val dayMillis = remember(monthCal, d) {
                                (monthCal.clone() as Calendar).apply {
                                    set(Calendar.DAY_OF_MONTH, d)
                                }.timeInMillis
                            }
                            val isSelected = isSameDay(dayMillis, selectedDate)
                            val isToday = isSameDay(dayMillis, System.currentTimeMillis())
                            val count = undoneCounts[dayMillis] ?: 0

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (isToday && !isSelected) Color.White.copy(alpha = 0.6f) else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onDaySelected(dayMillis) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$d",
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            // Kırmızı yapılmamış-not rozeti
                            if (count > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 2.dp, end = 4.dp)
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (count > 9) "9+" else "$count",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        lineHeight = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthGrid(selectedMonth: Int, onMonthSelected: (Int) -> Unit) {
    val months = listOf(
        "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color(0xFF252525), RoundedCornerShape(16.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 0 until 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (j in 0 until 3) {
                    val index = i * 3 + j
                    val isSelected = index == selectedMonth
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable { onMonthSelected(index) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = months[index],
                            color = if (isSelected) Color.Black else Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            if (i < 3) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = Color.Gray.copy(alpha = 0.3f)
                )
            }
        }
    }
}

fun isSameDay(date1: Long, date2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = date1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = date2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteItem(
    note: Note,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
    onReminderClick: () -> Unit = {},
    onReminderLongClick: () -> Unit = {},
    isAudioPlaying: Boolean = false,
    onToggleAudio: () -> Unit = {},
    onSeekAudio: (Int) -> Unit = {},
    audioPositionMs: Int = 0
) {
    // Her nota kendine ait pastel bir vurgu rengi (id'ye göre sabit kalır)
    val accentColors = listOf(
        Color(0xFF7C9CF5), // mavi
        Color(0xFF6FCF97), // yeşil
        Color(0xFFF2B45A), // amber
        Color(0xFFBB8FCE), // mor
        Color(0xFFE57373), // mercan
        Color(0xFF5AC8FA)  // turkuaz
    )
    val accent = accentColors[(abs(note.id) % accentColors.size).toInt()]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onToggleDone() }, // Karta dokun: yapıldı / geri al
                onLongClick = { onClick() }   // Basılı tut: düzenleme ekranı
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E) // Kartlar için grimsi siyah
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Sol renk şeridi (dekoratif — artık kartın tamamı aç/kapa yapıyor)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 14.dp)
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (note.isDone) accent.copy(alpha = 0.3f) else accent)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (note.isDone) Color(0xFF6E7480) else Color.White,
                    textDecoration = if (note.isDone) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (note.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note.content,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (note.isDone) Color(0xFF5A5F68) else Color(0xFF9AA0A8),
                        textDecoration = if (note.isDone) TextDecoration.LineThrough else null
                    )
                }
                // --- SESLİ NOT göstergesi: mini waveform oynatıcı (opsiyonel özellik, kaldırılabilir) ---
                if (note.audioPath != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val bars = remember(note.waveform) {
                        note.waveform?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.isNotEmpty() }
                            ?: List(28) { i -> 25 + ((i * 37) % 55) }
                    }
                    val totalMs = note.audioDurationMs ?: 0
                    val playedFraction = if (isAudioPlaying && totalMs > 0) audioPositionMs.toFloat() / totalMs else 0f
                    val playedCount = (bars.size * playedFraction).toInt().coerceIn(0, bars.size)
                    var waveformWidthPx by remember { mutableIntStateOf(0) }

                    fun formatMs(ms: Int): String {
                        val s = ms / 1000
                        return "%d:%02d".format(s / 60, s % 60)
                    }

                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.alpha(if (note.isDone) 0.35f else 1f)
                    ) {
                        // İkon, dalga formu çizgisiyle aynı yükseklikte (24.dp) ortalanır —
                        // altındaki süre satırından etkilenmesin diye ayrı bir kutuda.
                        Box(
                            modifier = Modifier.height(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = onToggleAudio,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Sesli not",
                                    tint = accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // Waveform: dokun veya sürükle, istediğin yere atla
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .onSizeChanged { waveformWidthPx = it.width }
                                    .pointerInput(totalMs) {
                                        if (totalMs <= 0) return@pointerInput
                                        awaitEachGesture {
                                            val down = awaitFirstDown()
                                            if (waveformWidthPx > 0) {
                                                val fraction = (down.position.x / waveformWidthPx).coerceIn(0f, 1f)
                                                onSeekAudio((fraction * totalMs).toInt())
                                            }
                                            drag(down.id) { change ->
                                                if (waveformWidthPx > 0) {
                                                    val fraction = (change.position.x / waveformWidthPx).coerceIn(0f, 1f)
                                                    onSeekAudio((fraction * totalMs).toInt())
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
                                    bars.forEachIndexed { index, amp ->
                                        Box(
                                            modifier = Modifier
                                                .width(2.5.dp)
                                                .fillMaxHeight(amp.coerceIn(6, 100) / 100f)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(
                                                    if (isAudioPlaying && index < playedCount) accent
                                                    else accent.copy(alpha = 0.35f)
                                                )
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatMs(if (isAudioPlaying) audioPositionMs else 0),
                                    color = Color(0xFF6E7480),
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = formatMs(totalMs),
                                    color = Color(0xFF6E7480),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
                // --- SESLİ NOT SONU ---
            }

            // İçerik ile hatırlatıcı arasında silik ayırıcı çizgi
            Box(
                modifier = Modifier
                    .padding(vertical = 14.dp)
                    .width(0.7.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.10f))
            )

            // Hatırlatıcı: kartın sağında, dikeyde ortalı
            // Dokun: ilk kez ise ayar aç, kuruluysa aç/kapat. Basılı tut: ayar sayfası.
            val reminderOn = note.reminderTime != null && note.reminderEnabled
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .width(54.dp) // Sabit genişlik: tarih olsa da olmasa da çizgi ve simge aynı hizada
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onReminderClick() },
                        onLongClick = { onReminderLongClick() }
                    )
            ) {
                Icon(
                    imageVector = if (reminderOn) Icons.Default.Alarm else Icons.Default.AlarmOff,
                    contentDescription = "Hatırlatıcı",
                    tint = if (reminderOn) accent else Color(0xFF6E7480),
                    modifier = Modifier.size(20.dp)
                )
                note.reminderTime?.let { rt ->
                    Spacer(modifier = Modifier.height(2.dp))
                    // Üstte saat, altında tarih (yan yana çok uzun duruyordu)
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(rt)),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        color = if (reminderOn) accent else Color(0xFF6E7480)
                    )
                    Text(
                        text = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(rt)),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        color = if (reminderOn) accent else Color(0xFF6E7480)
                    )
                }
            }
        }
    }
}
