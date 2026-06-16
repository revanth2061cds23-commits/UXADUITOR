package com.example.uxauditor

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.uxauditor.data.DatabaseHelper
import com.example.uxauditor.data.SharedPreferencesHelper
import com.example.uxauditor.data.copyUriToCache
import com.example.uxauditor.services.CaptureService
import com.example.uxauditor.ui.countdown.CountdownScreen
import com.example.uxauditor.ui.login.LoginScreen
import com.example.uxauditor.ui.main.MainScreen
import com.example.uxauditor.ui.review.ReviewScreen
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(sharedUris: MutableState<List<android.net.Uri>> = remember { mutableStateOf(emptyList()) }) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { SharedPreferencesHelper(context) }
    val db = remember { DatabaseHelper(context) }
    
    // Check if the user is authenticated, else boot to Login
    val startRoute = if (prefs.getAuthToken() != null) Main else Login
    val backStack = rememberNavBackStack(startRoute)

    // Handle end session trigger extra parameter inside MainActivity context
    val activity = context as? Activity
    val extraEndSession = activity?.intent?.getBooleanExtra("end_session_confirmed", false) ?: false
    val extraSessionId = activity?.intent?.getStringExtra("session_id")
    
    if (extraEndSession && extraSessionId != null) {
        LaunchedEffect(extraSessionId) {
            activity.intent.removeExtra("end_session_confirmed")
            backStack.add(Review(extraSessionId))
        }
    }

    // Share Sheet Import Dialog/Overlay
    val uris = sharedUris.value
    if (uris.isNotEmpty()) {
        var flowName by remember { mutableStateOf("") }
        var pastSessions by remember { mutableStateOf(listOf<Pair<String, String>>()) } // sessionId to flowName

        LaunchedEffect(uris) {
            val list = mutableListOf<Pair<String, String>>()
            val readableDb = db.readableDatabase
            val cursor = readableDb.rawQuery("SELECT id, flow_name FROM sessions ORDER BY created_at DESC LIMIT 15", null)
            while (cursor.moveToNext()) {
                list.add(Pair(cursor.getString(0), cursor.getString(1)))
            }
            cursor.close()
            pastSessions = list
        }

        AlertDialog(
            onDismissRequest = { sharedUris.value = emptyList() },
            title = { Text("Import Shared Images (${uris.size})") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Choose where to import these screenshots:", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Option A: Import into a New Session
                    Text("Option A: Import into a New Session", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = flowName,
                        onValueChange = { flowName = it },
                        label = { Text("New Session Flow Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (flowName.isNotEmpty()) {
                                coroutineScope.launch {
                                    val sessionId = UUID.randomUUID().toString()
                                    db.insertSession(sessionId, flowName, android.os.Build.MODEL, 1080, 1920)
                                    
                                    var index = 0
                                    uris.reversed().forEach { uri ->
                                        val file = copyUriToCache(context, uri)
                                        if (file != null) {
                                            db.insertScreen(UUID.randomUUID().toString(), sessionId, index++, file.absolutePath, -1f, -1f, null)
                                        }
                                    }
                                    
                                    sharedUris.value = emptyList()
                                    backStack.add(Review(sessionId))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Create & Import", color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Option B: Append to Existing Session
                    Text("Option B: Append to Existing Session", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (pastSessions.isEmpty()) {
                        Text("No existing sessions found.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(pastSessions) { session ->
                                Card(
                                    onClick = {
                                        coroutineScope.launch {
                                            val sessionId = session.first
                                            val existingScreens = db.getLocalScreens(sessionId)
                                            var index = existingScreens.size
                                            
                                            uris.reversed().forEach { uri ->
                                                val file = copyUriToCache(context, uri)
                                                if (file != null) {
                                                    db.insertScreen(UUID.randomUUID().toString(), sessionId, index++, file.absolutePath, -1f, -1f, null)
                                                }
                                            }
                                            
                                            sharedUris.value = emptyList()
                                            backStack.add(Review(sessionId))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Text(
                                        text = session.second,
                                        modifier = Modifier.padding(12.dp),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { sharedUris.value = emptyList() }) {
                    Text("Cancel", color = Color.Red)
                }
            }
        )
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Login> {
                LoginScreen(
                    prefs = prefs,
                    onLoginSuccess = {
                        backStack.add(Main)
                    }
                )
            }
            entry<Main> {
                MainScreen(
                    prefs = prefs,
                    onCreateSession = { sessionId, name ->
                        backStack.add(Countdown(sessionId, name))
                    },
                    onViewSession = { sessionId ->
                        backStack.add(Review(sessionId))
                    },
                    onSignOut = {
                        prefs.clearAuth()
                        backStack.add(Login)
                    }
                )
            }
            entry<Countdown> { key ->
                CountdownScreen(
                    flowName = key.flowName,
                    onCountdownFinished = {
                        // Start background screenshot capturing service
                        val startIntent = Intent(context, CaptureService::class.java)
                        context.startService(startIntent)

                        // Press Home to let the auditor navigate naturally
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(homeIntent)
                    }
                )
            }
            entry<Review> { key ->
                ReviewScreen(
                    sessionId = key.sessionId,
                    onBack = {
                        backStack.add(Main)
                    }
                )
            }
        }
    )
}
