package com.benim.notdefteri.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

// İlk açılışta gösterilen tanıtım penceresi.
// Sağa/sola kaydırarak veya İleri tuşuyla sayfalar arasında gezilir.
@Composable
fun OnboardingDialog(onClose: () -> Unit) {
    val pageCount = 6
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pageCount - 1

    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1E1E)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Sağ üstte kapatma çarpısı
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color(0xFF8E94A3))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 44.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Kaydırmalı sayfalar
                    HorizontalPager(state = pagerState) { page ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Uygulamadan gerçek arayüz önizlemesi
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when (page) {
                                    0 -> DemoCalendar()
                                    1 -> DemoNoteCards()
                                    2 -> DemoReminder()
                                    3 -> DemoChat()
                                    4 -> DemoLog()
                                    else -> DemoWidget()
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = when (page) {
                                    0 -> "Takvimde Gezin"
                                    1 -> "Notları Yönet"
                                    2 -> "Hatırlatıcı Kur"
                                    3 -> "AI Asistan"
                                    4 -> "İşlem Geçmişi"
                                    else -> "Ana Ekran Widget'ı"
                                },
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when (page) {
                                    0 -> "Üstteki şeritte günleri sağa sola kaydırarak gezin; ortadaki gün her zaman seçili olandır. Ay adına dokununca büyük takvim açılır. Kırmızı rozetler, o günde seni bekleyen notların sayısını gösterir."
                                    1 -> "Bir nota dokunduğunda yapıldı olarak işaretlenir ve üzeri çizilir; tekrar dokununca geri alınır. Notu düzenlemek için üzerine basılı tutman yeterli. Not eklerken mikrofon ikonuyla sesli not da kaydedip dinleyebilirsin."
                                    2 -> "Alarm ikonuna ilk dokunuşunda tarih ve saat seçerek hatırlatıcı kurarsın. Kurulu hatırlatıcıyı tek dokunuşla açıp kapatabilir, basılı tutarak yeniden ayarlayabilirsin. Zamanı geldiğinde telefonuna bildirim gelir."
                                    3 -> "Sağ üstteki yıldız simgesi yapay zeka asistanını açar. Asistan tüm notlarını tanır: ona soru sorabilir, fotoğraf ya da dosya gönderebilirsin. \"Yarına not al\" demen yeterli — notu senin yerine oluşturur."
                                    4 -> "Sol üstteki saat simgesi tüm işlemlerin kaydını açar: neyi ne zaman eklediğin, sildiğin, yaptığın ve gelen bildirimler. Arama, işlem türü ve tarih filtreleriyle aradığını saniyede bulursun."
                                    else -> "Ana ekranına widget ekleyerek günün notlarını uygulamayı hiç açmadan görebilirsin. Oklarla günler arasında geçiş yap, artı tuşuyla anında yeni not ekle."
                                },
                                color = Color(0xFFB8BCC4),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.height(122.dp) // Uzun metinler noktalara taşmasın
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp)) // Yazı ile noktalar arası = noktalar ile tuş arası

                    // Sayfa noktaları (dikeyde ortalı, düz hizada)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pageCount) { i ->
                            Box(
                                modifier = Modifier
                                    .size(if (i == pagerState.currentPage) 9.dp else 7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == pagerState.currentPage) Color.White else Color(0xFF3A3A3A)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (isLast) onClose()
                            else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = if (isLast) "Başla" else "İleri",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// --- Uygulamanın gerçek tasarımından küçük canlı önizlemeler ---

// Ana ekrandaki takvim kapsülünün minyatürü
@Composable
private fun DemoCalendar() {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF161616))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("CMT" to "25", "PAZ" to "26", "PZT" to "27", "SAL" to "28", "ÇAR" to "29").forEachIndexed { i, (d, n) ->
                val isCenter = i == 2
                Box(contentAlignment = Alignment.TopEnd) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .padding(top = 5.dp) // Rozet için üstte yer aç
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCenter) Color(0xFF2F2F2F) else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                    ) {
                        Text(
                            d,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (d == "PAZ") Color(0xFFF05A5A) else Color(0xFF8E94A3)
                        )
                        Text(n, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    if (isCenter) {
                        // Rozet: hücrenin sağ üst köşesinde, tam görünür
                        Box(
                            modifier = Modifier
                                .padding(end = 2.dp)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "2",
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

// Not kartlarının minyatürü: biri normal (sesli notlu), biri yapılmış
@Composable
private fun DemoNoteCards() {
    Column {
        DemoCard(title = "Süt almayı unutma", accent = Color(0xFF7C9CF5), done = false, showAudio = true)
        Spacer(modifier = Modifier.height(8.dp))
        DemoCard(title = "Faturayı öde", accent = Color(0xFF6FCF97), done = true)
    }
}

@Composable
private fun DemoCard(
    title: String,
    accent: Color,
    done: Boolean,
    showAlarm: Boolean = false,
    alarmOn: Boolean = false,
    showAudio: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF2A2A2A))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (done) accent.copy(alpha = 0.3f) else accent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (done) Color(0xFF6E7480) else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (done) TextDecoration.LineThrough else null
            )
            if (showAudio) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Sesli not", color = accent, fontSize = 9.sp)
                }
            }
        }
        if (showAlarm) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (alarmOn) Icons.Default.Alarm else Icons.Default.AlarmOff,
                    contentDescription = null,
                    tint = if (alarmOn) accent else Color(0xFF6E7480),
                    modifier = Modifier.size(16.dp)
                )
                if (alarmOn) {
                    Text("16:30", fontSize = 8.sp, color = accent)
                }
            }
        }
    }
}

// Hatırlatıcılı kart minyatürleri: biri kapalı, biri kurulu
@Composable
private fun DemoReminder() {
    Column {
        DemoCard(title = "Toplantı notları", accent = Color(0xFFF2B45A), done = false, showAlarm = true, alarmOn = true)
        Spacer(modifier = Modifier.height(8.dp))
        DemoCard(title = "Kitap oku", accent = Color(0xFFBB8FCE), done = false, showAlarm = true, alarmOn = false)
    }
}

// Sohbet balonlarının minyatürü
@Composable
private fun DemoChat() {
    Column(modifier = Modifier.width(250.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                "Bu hafta ne yapmalıyım?",
                color = Color.Black,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 3.dp))
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 3.dp, bottomEnd = 14.dp))
                    .background(Color(0xFF2A2A2A))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF7C9CF5),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Yarın 2 notun var: Süt al, Faturayı öde.",
                    color = Color(0xFFE4E6EA),
                    fontSize = 12.sp
                )
            }
        }
    }
}

// Log ekranının minyatürü
@Composable
private fun DemoLog() {
    Column(modifier = Modifier.width(240.dp)) {
        listOf(
            Triple("Süt al", "Eklendi", Color(0xFF6FCF97)),
            Triple("Faturayı öde", "Yapıldı", Color(0xFF7C9CF5)),
            Triple("Eski alışveriş listesi", "Silindi", Color(0xFFE57373))
        ).forEachIndexed { i, (title, action, color) ->
            if (i > 0) Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2A2A2A))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(action, color = color, fontSize = 9.sp)
                }
                Text("14:0${i * 3}", color = Color(0xFF6E7480), fontSize = 9.sp)
            }
        }
    }
}

// Ana ekran widget'ının minyatürü
@Composable
private fun DemoWidget() {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF121212))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF242424))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) { Text("‹", color = Color(0xFFB8BCC4), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Bugün", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF242424))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) { Text("›", color = Color(0xFFB8BCC4), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) { Text("+", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        listOf(
            Triple("Süt al", Color(0xFFE53935), false),
            Triple("Faturayı öde", Color(0xFF6FCF97), true)
        ).forEach { (t, c, done) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(c)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    t,
                    color = if (done) Color(0xFF6E7480) else Color.White,
                    fontSize = 12.sp,
                    textDecoration = if (done) TextDecoration.LineThrough else null
                )
            }
        }
    }
}
