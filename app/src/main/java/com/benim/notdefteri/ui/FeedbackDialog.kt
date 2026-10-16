package com.benim.notdefteri.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benim.notdefteri.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Formspree formu: kullanıcı "Gönder"e basınca mail buraya düşer, hiçbir
// harici uygulama açılmaz, uygulama içinden direkt istek atılır.
private const val FORMSPREE_ENDPOINT = "https://formspree.io/f/xlgqqdrq"

/**
 * Kullanıcının hata bildirmesi veya öneri göndermesi için basit bir pencere.
 * Gönder'e basınca:
 * 1) Formspree'ye HTTP isteği atılır -> gerçek mail olarak developer'a gider.
 * 2) Aynı zamanda Firestore'a da yedek olarak yazılır -> Firebase Console'dan
 *    her zaman geçmiş geri bildirimlere bakılabilir, Formspree'nin aylık
 *    limitine takılsa bile hiçbir bildirim kaybolmaz.
 * İkisi de arka planda, uygulama içinde; hiçbir ekstra uygulama açılmaz.
 */
@Composable
fun FeedbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var type by remember { mutableStateOf("Hata") } // "Hata" veya "Öneri"
    var message by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFB8BCC4),
        title = { Text("Geri Bildirim") },
        text = {
            Column {
                Text(
                    "Hatalı bir şey mi gördün, yoksa eklenmesini istediğin bir şey mi var? Kısaca yaz.",
                    color = Color(0xFFB8BCC4),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    listOf("Hata" to "Hata bildir", "Öneri" to "Öneri gönder").forEach { (value, label) ->
                        Surface(
                            onClick = { if (!isSending) type = value },
                            color = if (type == value) Color.White else Color(0xFF2A2A2A),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (type == value) Color.Black else Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    enabled = !isSending,
                    placeholder = {
                        Text(
                            if (type == "Hata") "Ne bozuk, nerede oldu, anlat..."
                            else "Ne eklenmeli, nasıl olmalı, anlat..."
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF7C9CF5),
                        unfocusedBorderColor = Color(0xFF3A3A3A),
                        focusedPlaceholderColor = Color(0xFF5A5F68),
                        unfocusedPlaceholderColor = Color(0xFF5A5F68)
                    )
                )
            }
        },
        confirmButton = {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .size(20.dp),
                    color = Color(0xFF7C9CF5),
                    strokeWidth = 2.dp
                )
            } else {
                TextButton(
                    enabled = message.isNotBlank(),
                    onClick = {
                        val subject = if (type == "Hata") "Not Defterim - Hata Bildirimi" else "Not Defterim - Öneri"
                        val trimmedMessage = message.trim()
                        isSending = true
                        coroutineScope.launch {
                            val formspreeOk = async(Dispatchers.IO) {
                                sendToFormspree(subject, type, trimmedMessage)
                            }
                            val firestoreOk = async(Dispatchers.IO) {
                                sendToFirestore(subject, type, trimmedMessage)
                            }
                            val results = awaitAll(formspreeOk, firestoreOk)
                            isSending = false
                            if (results.any { it }) {
                                Toast.makeText(context, "Geri bildirimin gönderildi, teşekkürler!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Gönderilemedi. İnternetini kontrol edip tekrar dene.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Gönder", color = if (message.isNotBlank()) Color(0xFF7C9CF5) else Color.Gray)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isSending, onClick = onDismiss) {
                Text("İptal", color = Color.Gray)
            }
        }
    )
}

private fun buildFeedbackBody(type: String, message: String): String = buildString {
    appendLine(message)
    appendLine()
    appendLine("---")
    appendLine("Tür: $type")
    appendLine("Uygulama sürümü: ${BuildConfig.VERSION_NAME}")
    appendLine("Cihaz: ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Android: ${Build.VERSION.RELEASE}")
}

private suspend fun sendToFormspree(subject: String, type: String, message: String): Boolean {
    return try {
        val url = URL(FORMSPREE_ENDPOINT)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            connectTimeout = 10000
            readTimeout = 10000
        }
        val json = JSONObject().apply {
            put("_subject", subject)
            put("type", type)
            put("message", buildFeedbackBody(type, message))
        }
        conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        conn.disconnect()
        code in 200..299
    } catch (e: Exception) {
        false
    }
}

private suspend fun sendToFirestore(subject: String, type: String, message: String): Boolean {
    return try {
        val data = hashMapOf(
            "subject" to subject,
            "type" to type,
            "message" to message,
            "appVersion" to BuildConfig.VERSION_NAME,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "androidVersion" to Build.VERSION.RELEASE,
            "timestamp" to System.currentTimeMillis()
        )
        withContext(Dispatchers.IO) {
            com.google.android.gms.tasks.Tasks.await(
                Firebase.firestore.collection("feedback").add(data)
            )
        }
        true
    } catch (e: Exception) {
        false
    }
}
