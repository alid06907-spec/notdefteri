package com.benim.notdefteri.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benim.notdefteri.data.ActivityLog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    logs: List<ActivityLog>,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Tümü") }
    var selectedDate by remember { mutableStateOf<Long?>(null) } // null = tüm tarihler
    var showDatePicker by remember { mutableStateOf(false) }

    val filters = listOf("Tümü", "Eklendi", "Silindi", "Düzenlendi", "Yapıldı", "Geri alındı", "Bildirim gönderildi")

    // Arama + tür + tarih filtresi uygula
    val filteredLogs = remember(logs, searchQuery, selectedFilter, selectedDate) {
        logs.filter { log ->
            (selectedFilter == "Tümü" || log.action == selectedFilter) &&
                    (searchQuery.isBlank() || log.noteTitle.contains(searchQuery, ignoreCase = true)) &&
                    (selectedDate == null || dayStartOf(log.timestamp) == selectedDate)
        }
    }

    // Güne göre grupla (en yeni gün üstte)
    val groupedLogs = remember(filteredLogs) {
        filteredLogs.groupBy { dayStartOf(it.timestamp) }
            .toSortedMap(compareByDescending { it })
    }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                title = { Text("İşlem Geçmişi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    // Tarih filtresi: takvimden gün seç
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "Tarihe Git",
                            tint = if (selectedDate != null) Color(0xFF7C9CF5) else Color.White
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Arama kutusu
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Not adına göre ara...", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Ara", tint = Color(0xFF8E94A3))
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedPlaceholderColor = Color(0xFF5A5F68),
                    unfocusedPlaceholderColor = Color(0xFF5A5F68)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // İşlem türü filtre çipleri
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { filter ->
                    val selected = filter == selectedFilter
                    Surface(
                        onClick = { selectedFilter = filter },
                        color = if (selected) Color.White else Color(0xFF1E1E1E),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (selected) Color.Black else Color(0xFFB8BCC4),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Seçili tarih çipi (X ile temizlenir)
            selectedDate?.let { dateMs ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    onClick = { selectedDate = null },
                    color = Color(0xFF7C9CF5),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("tr")).format(Date(dateMs)),
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Tarih filtresini kaldır",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (logs.isEmpty()) "Henüz işlem yok." else "Aramana uygun kayıt bulunamadı.",
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    groupedLogs.forEach { (dayStart, dayLogs) ->
                        item(key = "header_$dayStart") {
                            Text(
                                text = dayHeaderText(dayStart),
                                color = Color(0xFF8E94A3),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp)
                            )
                        }
                        items(dayLogs, key = { it.id }) { log ->
                            LogItem(log)
                        }
                    }
                }
            }
        }

        // Tarihe git: takvim diyaloğu
        if (showDatePicker) {
            val dpState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDate ?: System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                colors = DatePickerDefaults.colors(containerColor = Color(0xFF1E1E1E)),
                confirmButton = {
                    TextButton(onClick = {
                        dpState.selectedDateMillis?.let { selectedDate = dayStartOf(it) }
                        showDatePicker = false
                    }) { Text("Tamam", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("İptal", color = Color.Gray)
                    }
                }
            ) {
                DatePicker(
                    state = dpState,
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
    }
}

// Günün 00:00 zamanı
private fun dayStartOf(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

// Bugün / Dün / 14 Temmuz 2026, Salı
private fun dayHeaderText(dayStart: Long): String {
    val today = dayStartOf(System.currentTimeMillis())
    val oneDay = 24 * 60 * 60 * 1000L
    return when (dayStart) {
        today -> "BUGÜN"
        today - oneDay -> "DÜN"
        else -> SimpleDateFormat("dd MMMM yyyy, EEEE", Locale.forLanguageTag("tr"))
            .format(Date(dayStart)).uppercase(Locale.forLanguageTag("tr"))
    }
}

@Composable
private fun LogItem(log: ActivityLog) {
    val actionColor = when (log.action) {
        "Eklendi" -> Color(0xFF6FCF97)     // yeşil
        "Silindi" -> Color(0xFFE57373)     // kırmızı
        "Yapıldı" -> Color(0xFF7C9CF5)     // mavi
        "Geri alındı" -> Color(0xFFF2B45A) // amber
        "Bildirim gönderildi" -> Color(0xFF5AC8FA) // turkuaz
        else -> Color(0xFFBB8FCE)          // mor (Düzenlendi vs.)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Renkli işlem noktası
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(actionColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.noteTitle.ifBlank { "(Başlıksız not)" },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = log.action,
                color = actionColor,
                fontSize = 12.sp
            )
        }
        // Saat (gün zaten başlıkta yazıyor)
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.timestamp)),
            color = Color(0xFF6E7480),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
