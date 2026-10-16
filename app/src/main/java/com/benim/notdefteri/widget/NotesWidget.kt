package com.benim.notdefteri.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.benim.notdefteri.MainActivity
import com.benim.notdefteri.data.Note
import com.benim.notdefteri.data.NoteDatabase
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

private val DAY_OFFSET_KEY = intPreferencesKey("day_offset")
private val DELTA_PARAM = ActionParameters.Key<Int>("delta")

// Ana ekran widget'ının alıcısı (manifest'te kayıtlı)
class NotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesWidget()
}

// ‹ › tuşları: gün değiştir (delta = +1 / -1, 0 = bugüne dön)
class ChangeDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[DELTA_PARAM] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = prefs[DAY_OFFSET_KEY] ?: 0
            prefs[DAY_OFFSET_KEY] = if (delta == 0) 0 else current + delta
        }
        NotesWidget().update(context, glanceId)
    }
}

// Seçili günün notlarını gösteren koyu temalı widget
class NotesWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // Gerçek boyutu kullan: not sayısı ve düzen widget'ın o anki boyutuna göre hesaplanır
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val allNotes = NoteDatabase.getDatabase(context).noteDao().getAllNotes().first()

        provideContent {
            val prefs = currentState<Preferences>()
            val dayOffset = prefs[DAY_OFFSET_KEY] ?: 0
            // Geniş/uzun modda + üste taşınır, dar modda sağ altta yüzer
            val widthDp = LocalSize.current.width.value
            val heightDp = LocalSize.current.height.value
            val isWide = widthDp >= 250f
            val isTall = heightDp >= 300f
            // Not sayısı: mevcut yüksekliğe göre, en fazla 6 (font hiç küçülmez)
            val headerH = if (isTall && !isWide) 100f else 60f
            val maxNotes = (((heightDp - headerH - 20f) / 26f).toInt()).coerceIn(2, 6)

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 24 * 60 * 60 * 1000L

            val dayNotes = allNotes
                .filter { it.date in dayStart until dayEnd }
                .sortedBy { it.isDone }

            // Üst satır: kısa etiket (tek satıra sığar), alt satır: küçük detay
            val dayLabel = when (dayOffset) {
                0 -> "Bugün"
                1 -> "Yarın"
                -1 -> "Dün"
                else -> SimpleDateFormat("dd MMM", Locale.forLanguageTag("tr")).format(Date(dayStart))
            }
            val dateText = if (dayOffset in -1..1) {
                SimpleDateFormat("dd MMMM", Locale.forLanguageTag("tr")).format(Date(dayStart))
            } else {
                SimpleDateFormat("EEE", Locale.forLanguageTag("tr")).format(Date(dayStart)) // Pzt, Sal...
            }

            // Gösterilen günden ÖNCEKİ günlerden kalan yapılmamış not sayısı (sol ok rozeti)
            val pastUndoneCount = allNotes.count { !it.isDone && it.date < dayStart }
            // Gösterilen günden SONRAKİ günlerdeki yapılmamış not sayısı (sağ ok rozeti)
            val futureUndoneCount = allNotes.count { !it.isDone && it.date >= dayEnd }

            // "+" tuşu: widget'ta hangi gün açıksa O GÜNE not ekler
            // NOT: data URI'si her gün için benzersiz olmalı, yoksa Android eski intent'i önbellekten kullanıyor
            val addNoteIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.benim.notdefteri.ADD_NOTE"
                data = android.net.Uri.parse("notdefteri://add/$dayStart")
                putExtra("open_add_note", true)
                putExtra("note_date", dayStart)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            // 2x2 kompakt düzen: + tuşu sağ altta yüzer
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFF121212)))
                    .cornerRadius(24.dp)
                    .padding(10.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    if (isTall && !isWide) {
                        // Uzun dar (2x4): üstte gün + ekle tuşu, altında oklar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = GlanceModifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .clickable(actionRunCallback<ChangeDayAction>(actionParametersOf(DELTA_PARAM to 0)))
                            ) {
                                Text(
                                    text = dayLabel,
                                    style = TextStyle(
                                        color = ColorProvider(Color.White),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                if (dateText.isNotEmpty()) {
                                    Text(
                                        text = dateText,
                                        style = TextStyle(
                                            color = ColorProvider(Color(0xFF8E94A3)),
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                            // + sağ üstte
                            AddButton(addNoteIntent)
                        }
                        Spacer(modifier = GlanceModifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = GlanceModifier.fillMaxWidth()
                        ) {
                            NavButtonWithBadge("‹", actionRunCallback<ChangeDayAction>(actionParametersOf(DELTA_PARAM to -1)), pastUndoneCount)
                            Spacer(modifier = GlanceModifier.width(14.dp))
                            NavButtonWithBadge("›", actionRunCallback<ChangeDayAction>(actionParametersOf(DELTA_PARAM to 1)), futureUndoneCount)
                        }
                    } else {
                        // Tek satır başlık: ‹ gün › (+ genişse sağda)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = GlanceModifier.fillMaxWidth()
                        ) {
                            // Önceki gün (geçmişten kalan yapılmamışlar rozetle görünür)
                            NavButtonWithBadge("‹", actionRunCallback<ChangeDayAction>(actionParametersOf(DELTA_PARAM to -1)), pastUndoneCount)

                            // Gün etiketi — dokununca bugüne döner
                            Column(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .clickable(actionRunCallback<ChangeDayAction>(actionParametersOf(DELTA_PARAM to 0))),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = dayLabel,
                                    style = TextStyle(
                                        color = ColorProvider(Color.White),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                if (dateText.isNotEmpty()) {
                                    Text(
                                        text = dateText,
                                        style = TextStyle(
                                            color = ColorProvider(Color(0xFF8E94A3)),
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            // Sonraki gün (gelecekteki yapılmamışlar rozetle görünür)
                            NavButtonWithBadge("›", actionRunCallback<ChangeDayAction>(actionParametersOf(DELTA_PARAM to 1)), futureUndoneCount)

                            // Geniş modda + sağ üstte
                            if (isWide) {
                                Spacer(modifier = GlanceModifier.width(10.dp))
                                AddButton(addNoteIntent)
                            }
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Not listesi — dokununca uygulama o günde açılır
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .clickable(actionStartActivity(Intent(context, MainActivity::class.java).apply {
                                action = "com.benim.notdefteri.OPEN_DATE"
                                data = android.net.Uri.parse("notdefteri://open/$dayStart") // Benzersiz: önbellek sorununu önler
                                putExtra("open_date", dayStart) // Uygulama widget'taki günde açılsın
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }))
                    ) {
                        if (dayNotes.isEmpty()) {
                            Text(
                                text = "Bu gün için not yok",
                                style = TextStyle(color = ColorProvider(Color(0xFF8E94A3)), fontSize = 13.sp)
                            )
                        } else {
                            dayNotes.take(maxNotes).forEach { note ->
                                NoteRow(note)
                            }
                            val remaining = dayNotes.size - maxNotes
                            if (remaining > 0) {
                                Text(
                                    text = "+$remaining not daha",
                                    style = TextStyle(color = ColorProvider(Color(0xFF8E94A3)), fontSize = 11.sp)
                                )
                            }
                        }
                    }
                }

                // Küçük modda (2x2) + sağ altta yüzer
                if (!isWide && !isTall) {
                    AddButton(addNoteIntent)
                }
            }
        }
    }
}

// Ok tuşu + sağ üstünde kırmızı sayı rozeti (geçmişten kalan yapılmamış notlar)
@androidx.compose.runtime.Composable
private fun NavButtonWithBadge(label: String, action: androidx.glance.action.Action, badgeCount: Int) {
    Box(contentAlignment = Alignment.TopEnd) {
        Box(modifier = GlanceModifier.padding(top = 5.dp, end = 5.dp)) {
            NavButton(label, action)
        }
        if (badgeCount > 0) {
            Box(
                modifier = GlanceModifier
                    .size(17.dp)
                    .cornerRadius(9.dp)
                    .background(ColorProvider(Color(0xFFE53935))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badgeCount > 9) "9+" else "$badgeCount",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AddButton(intent: Intent) {
    Box(
        modifier = GlanceModifier
            .size(40.dp)
            .cornerRadius(20.dp)
            .background(ColorProvider(Color.White))
            .clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+",
            style = TextStyle(
                color = ColorProvider(Color.Black),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@androidx.compose.runtime.Composable
private fun NavButton(label: String, action: androidx.glance.action.Action) {
    // Küçük, yumuşak köşeli, göze batmayan ok tuşu
    Box(
        modifier = GlanceModifier
            .size(width = 40.dp, height = 32.dp)
            .cornerRadius(16.dp)
            .background(ColorProvider(Color(0xFF242424)))
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(Color(0xFFB8BCC4)),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@androidx.compose.runtime.Composable
private fun NoteRow(note: Note) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        // Durum noktası: yapılmamış kırmızı, yapılmış yeşil
        Column(
            modifier = GlanceModifier
                .size(8.dp)
                .cornerRadius(4.dp)
                .background(
                    ColorProvider(if (note.isDone) Color(0xFF6FCF97) else Color(0xFFE53935))
                )
        ) {}
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = note.title.ifBlank { "(Başlıksız)" },
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(if (note.isDone) Color(0xFF6E7480) else Color.White),
                fontSize = 13.sp,
                textDecoration = if (note.isDone) TextDecoration.LineThrough else TextDecoration.None
            )
        )
    }
}
