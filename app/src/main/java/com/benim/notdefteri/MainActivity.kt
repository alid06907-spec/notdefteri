package com.benim.notdefteri

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.benim.notdefteri.notifications.DailyReminderWorker
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.benim.notdefteri.ui.ChatScreen
import com.benim.notdefteri.ui.EditNoteScreen
import com.benim.notdefteri.ui.LogScreen
import com.benim.notdefteri.ui.NoteListScreen
import com.benim.notdefteri.ui.NoteViewModel
import com.benim.notdefteri.ui.theme.NotdefteriTheme

class MainActivity : ComponentActivity() {
    // Widget'tan gelen intent'i state olarak tut: uygulama açıkken de güncellenir
    private var launchIntent by mutableStateOf<Intent?>(null)

    // Android 13+ bildirim izni isteme
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchIntent = intent

        // Firebase ve App Check kurulumu
        FirebaseApp.initializeApp(this)
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
        Log.d("AppCheck", "Firebase App Check Debug Provider kuruldu.")

        // Günlük 12:00 bildirimi kur (zaten kuruluysa dokunmaz)
        DailyReminderWorker.schedule(this)

        // Bildirim izni yoksa iste (Android 13+)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            NotdefteriTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NotDefteriApp(launchIntent = launchIntent)
                }
            }
        }
    }

    // Uygulama zaten açıkken widget'tan basılırsa buraya düşer
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchIntent = intent
    }
}

@Composable
fun NotDefteriApp(
    viewModel: NoteViewModel = viewModel(),
    launchIntent: Intent? = null
) {
    val navController = rememberNavController()
    val filteredNotes by viewModel.filteredNotes.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    // İlk kurulumda tanıtım penceresi (bir kez gösterilir)
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("notdefteri_prefs", android.content.Context.MODE_PRIVATE)
    }
    var showOnboarding by remember {
        mutableStateOf(!prefs.getBoolean("onboarding_shown_v4", false))
    }
    if (showOnboarding) {
        com.benim.notdefteri.ui.OnboardingDialog(
            onClose = {
                prefs.edit().putBoolean("onboarding_shown_v4", true).apply()
                showOnboarding = false
            }
        )
    }

    // Widget'tan gelen her yeni intent'te çalışır (uygulama açıkken bile)
    LaunchedEffect(launchIntent) {
        val openDate = launchIntent?.getLongExtra("open_date", 0L) ?: 0L
        val openAddNote = launchIntent?.getBooleanExtra("open_add_note", false) ?: false

        // Widget'taki güne git (listeye dokunulduysa)
        if (openDate != 0L) {
            viewModel.onDateSelected(openDate)
        }
        // Widget'ın + tuşuyla açıldıysa doğrudan not ekleme ekranına git (widget'taki güne)
        if (openAddNote) {
            val noteDate = launchIntent?.getLongExtra("note_date", System.currentTimeMillis())
                ?: System.currentTimeMillis()
            navController.navigate("add?date=$noteDate")
        }
    }

    NavHost(navController = navController, startDestination = "list") {
        composable("list") {
            NoteListScreen(
                viewModel = viewModel,
                onAddNoteClick = { navController.navigate("add?date=$selectedDate") },
                onNoteClick = { note -> navController.navigate("edit/${note.id}") },
                onLogClick = { navController.navigate("log") },
                onAiChatClick = { navController.navigate("chat") }
            )
        }
        composable("chat") {
            ChatScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("log") {
            val logs by viewModel.logs.collectAsState()
            LogScreen(
                logs = logs,
                onBack = { navController.popBackStack() }
            )
        }
        composable("add?date={date}") { backStackEntry ->
            val dayArg = backStackEntry.arguments?.getString("date")?.toLongOrNull() ?: System.currentTimeMillis()
            // Seçili günün tarihini, şu anki gerçek saatle birleştir (yoksa yeni not hep 00:00 gösteriyordu)
            val now = java.util.Calendar.getInstance()
            val initialDate = java.util.Calendar.getInstance().apply {
                timeInMillis = dayArg
                set(java.util.Calendar.HOUR_OF_DAY, now.get(java.util.Calendar.HOUR_OF_DAY))
                set(java.util.Calendar.MINUTE, now.get(java.util.Calendar.MINUTE))
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            EditNoteScreen(
                initialDate = initialDate,
                onSave = { note ->
                    viewModel.insert(note)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("edit/{noteId}") { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId")?.toLongOrNull()
            val note = filteredNotes.find { it.id == noteId }
            EditNoteScreen(
                note = note,
                onSave = { updatedNote ->
                    viewModel.update(updatedNote)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
                onDelete = {
                    note?.let { viewModel.delete(it) }
                    navController.popBackStack()
                }
            )
        }
    }
}
