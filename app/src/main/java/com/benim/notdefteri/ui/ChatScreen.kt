package com.benim.notdefteri.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benim.notdefteri.data.ChatMessage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: NoteViewModel,
    onBack: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsState()
    val isLoading by viewModel.isChatLoading.collectAsState()
    val allNotes by viewModel.allNotesList.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var showAttachMenu by remember { mutableStateOf(false) }
    var showNotePicker by remember { mutableStateOf(false) }
    var pendingImagePath by remember { mutableStateOf<String?>(null) }
    var pendingFilePath by remember { mutableStateOf<String?>(null) }
    var pendingFileName by remember { mutableStateOf<String?>(null) }

    // Galeriden fotoğraf seç -> uygulama içine kopyala (kalıcı olsun)
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            try {
                val dir = File(context.filesDir, "chat_images").apply { mkdirs() }
                val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(it)?.use { inp ->
                    file.outputStream().use { out -> inp.copyTo(out) }
                }
                pendingImagePath = file.absolutePath
            } catch (_: Exception) {
            }
        }
    }

    // Dosya seç (PDF / metin) -> uygulama içine kopyala
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                // Dosya adını öğren
                var name = "dosya"
                context.contentResolver.query(it, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && idx >= 0) name = c.getString(idx)
                }
                val dir = File(context.filesDir, "chat_files").apply { mkdirs() }
                val file = File(dir, "${System.currentTimeMillis()}_$name")
                context.contentResolver.openInputStream(it)?.use { inp ->
                    file.outputStream().use { out -> inp.copyTo(out) }
                }
                pendingFilePath = file.absolutePath
                pendingFileName = name
            } catch (_: Exception) {
            }
        }
    }

    // Ekran açılınca direkt en alta in; yeni mesajlarda yumuşak kay
    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size, isLoading) {
        val total = messages.size + if (isLoading) 1 else 0
        if (total > 0) {
            if (!initialScrollDone) {
                listState.scrollToItem(total - 1)
                initialScrollDone = true
            } else {
                listState.animateScrollToItem(total - 1)
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(), // Klavye açılınca giriş kutusu klavyenin üstünde kalır
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF7C9CF5),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Asistan", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
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
            if (messages.isEmpty()) {
                // Boş durum: öneri metni
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF7C9CF5),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Notların hakkında her şeyi sorabilirsin",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Örnek: \"Bu hafta neler yapmam gerekiyor?\", \"Dün hangi notu silmiştim?\", \"Yapılmamış işlerim neler?\"",
                        color = Color(0xFF8E94A3),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubble(msg)
                    }
                    if (isLoading) {
                        item {
                            Row(
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF7C9CF5)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Düşünüyor...", color = Color(0xFF8E94A3), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Gönderilmeyi bekleyen fotoğraf önizlemesi
            pendingImagePath?.let { path ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bmp = remember(path) {
                        try {
                            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })
                        } catch (_: Exception) {
                            null
                        }
                    }
                    bmp?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Ekli fotoğraf",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fotoğraf eklendi", color = Color(0xFF8E94A3), fontSize = 12.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { pendingImagePath = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Fotoğrafı kaldır", tint = Color(0xFF8E94A3))
                    }
                }
            }

            // Gönderilmeyi bekleyen dosya önizlemesi
            pendingFileName?.let { name ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = Color(0xFFF2B45A),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(name, color = Color(0xFF8E94A3), fontSize = 12.sp, maxLines = 1)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        pendingFilePath = null
                        pendingFileName = null
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Dosyayı kaldır", tint = Color(0xFF8E94A3))
                    }
                }
            }

            // Mesaj yazma alanı
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Dosya ekleme (not / fotoğraf)
                Box {
                    FilledIconButton(
                        onClick = { showAttachMenu = true },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF1E1E1E),
                            contentColor = Color(0xFF8E94A3)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Ekle", modifier = Modifier.size(22.dp))
                    }
                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false },
                        containerColor = Color(0xFF1E1E1E)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Not ekle", color = Color.White) },
                            leadingIcon = {
                                Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF7C9CF5))
                            },
                            onClick = {
                                showAttachMenu = false
                                showNotePicker = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Fotoğraf ekle", color = Color.White) },
                            leadingIcon = {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF6FCF97))
                            },
                            onClick = {
                                showAttachMenu = false
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Dosya ekle", color = Color.White) },
                            leadingIcon = {
                                Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color(0xFFF2B45A))
                            },
                            onClick = {
                                showAttachMenu = false
                                filePicker.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "text/plain",
                                        "text/markdown",
                                        "text/csv",
                                        "application/json"
                                    )
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Bir şey sor...", fontSize = 14.sp) },
                    shape = RoundedCornerShape(24.dp),
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
                    maxLines = 4,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        viewModel.sendChatMessage(input, pendingImagePath, pendingFilePath, pendingFileName)
                        input = ""
                        pendingImagePath = null
                        pendingFilePath = null
                        pendingFileName = null
                    },
                    enabled = (input.isNotBlank() || pendingImagePath != null || pendingFilePath != null) && !isLoading,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF2A2A2A),
                        disabledContentColor = Color(0xFF5A5F68)
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Gönder",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Not seçme diyaloğu
        if (showNotePicker) {
            AlertDialog(
                onDismissRequest = { showNotePicker = false },
                containerColor = Color(0xFF1E1E1E),
                titleContentColor = Color.White,
                title = { Text("Hangi notu ekleyelim?") },
                text = {
                    if (allNotes.isEmpty()) {
                        Text("Henüz hiç notun yok.", color = Color.Gray)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(allNotes) { note ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            // Notu mesaja iliştir
                                            input = buildString {
                                                if (input.isNotBlank()) {
                                                    append(input)
                                                    append("\n\n")
                                                }
                                                append("[Not] ${note.title}: ${note.content}")
                                            }
                                            showNotePicker = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        note.title.ifBlank { "(Başlıksız)" },
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(note.date)),
                                        color = Color(0xFF8E94A3),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNotePicker = false }) {
                        Text("Kapat", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .background(if (isUser) Color.White else Color(0xFF1E1E1E))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Ekli dosya varsa kart olarak göster
            msg.fileName?.let { name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isUser) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = Color(0xFFF2B45A),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        name,
                        color = if (isUser) Color.Black else Color(0xFFE4E6EA),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                if (msg.text.isNotBlank() || msg.imageUri != null) Spacer(modifier = Modifier.height(6.dp))
            }

            // Ekli fotoğraf varsa göster
            msg.imageUri?.let { path ->
                val bmp = remember(path) {
                    try {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(path, bounds)
                        var sample = 1
                        while (bounds.outWidth / sample > 800) sample *= 2
                        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                    } catch (_: Exception) {
                        null
                    }
                }
                bmp?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Ekli fotoğraf",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    if (msg.text.isNotBlank()) Spacer(modifier = Modifier.height(6.dp))
                }
            }
            if (msg.text.isNotBlank()) {
                Text(
                    text = msg.text,
                    color = if (isUser) Color.Black else Color(0xFFE4E6EA),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
