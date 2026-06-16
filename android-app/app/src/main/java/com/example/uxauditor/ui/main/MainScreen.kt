package com.example.uxauditor.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uxauditor.data.DatabaseHelper
import com.example.uxauditor.data.SharedPreferencesHelper
import com.example.uxauditor.services.CaptureService
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    prefs: SharedPreferencesHelper,
    onCreateSession: (sessionId: String, flowName: String) -> Unit,
    onViewSession: (sessionId: String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var flowName by remember { mutableStateOf("") }
    var pastSessions by remember { mutableStateOf(listOf<LocalSessionData>()) }
    val db = remember { DatabaseHelper(context) }

    fun refreshSessions() {
        val list = mutableListOf<LocalSessionData>()
        val readableDb = db.readableDatabase
        val cursor = readableDb.rawQuery("SELECT * FROM sessions ORDER BY created_at DESC", null)
        while (cursor.moveToNext()) {
            list.add(
                LocalSessionData(
                    id = cursor.getString(0),
                    flowName = cursor.getString(1),
                    deviceModel = cursor.getString(2),
                    status = cursor.getString(5),
                    createdAt = cursor.getString(6)
                )
            )
        }
        cursor.close()
        pastSessions = list
    }

    LaunchedEffect(Unit) {
        refreshSessions()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val sessionId = UUID.randomUUID().toString()
            
            // Set service capture parameters
            CaptureService.mediaProjectionResultCode = result.resultCode
            CaptureService.mediaProjectionData = result.data
            CaptureService.activeSessionId = sessionId
            CaptureService.activeFlowName = flowName

            // Save to SQLite
            db.insertSession(sessionId, flowName, android.os.Build.MODEL, 1080, 1920)

            onCreateSession(sessionId, flowName)
        } else {
            Toast.makeText(context, "MediaProjection permission required.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UX Auditor Dashboard", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Text("Exit", color = Color(0xFFE63525), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Profile display
            Text("Logged in as: ${prefs.getUserEmail()}", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))

            // Section: New Session
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Start New Capture Session", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE63525))
                    OutlinedTextField(
                        value = flowName,
                        onValueChange = { flowName = it },
                        label = { Text("Flow Name (e.g. Onboarding)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (flowName.isEmpty()) {
                                    Toast.makeText(context, "Flow Name is required.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                // 1. Request Draw Overlay (SYSTEM_ALERT_WINDOW) Permission if not granted
                                if (!android.provider.Settings.canDrawOverlays(context)) {
                                    Toast.makeText(context, "Please grant 'Display over other apps' permission to show the floating button.", Toast.LENGTH_LONG).show()
                                    val intent = Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                    return@Button
                                }

                                // 2. Request Accessibility Service Permission if not granted
                                val enabledServices = android.provider.Settings.Secure.getString(
                                    context.contentResolver,
                                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                                )
                                val serviceName = "${context.packageName}/com.example.uxauditor.services.AuditAccessibilityService"
                                val isAccessibilityEnabled = enabledServices?.contains(serviceName) == true

                                if (!isAccessibilityEnabled) {
                                    Toast.makeText(context, "Please enable 'UX Auditor Tap Tracker' in Installed Services to track taps automatically.", Toast.LENGTH_LONG).show()
                                    val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                    return@Button
                                }

                                // Request MediaProjection permissions
                                val mediaProjManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                launcher.launch(mediaProjManager.createScreenCaptureIntent())
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63525))
                        ) {
                            Text("Auto Capture", color = Color.White)
                        }

                        Button(
                            onClick = {
                                if (flowName.isEmpty()) {
                                    Toast.makeText(context, "Flow Name is required.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val sessionId = UUID.randomUUID().toString()
                                // Save to SQLite
                                db.insertSession(sessionId, flowName, android.os.Build.MODEL, 1080, 1920)
                                // Navigate directly to review screen
                                onViewSession(sessionId)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63525))
                        ) {
                            Text("Manual Flow", color = Color.White)
                        }
                    }
                }
            }

            Text("Previous Sessions", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pastSessions) { s ->
                    Card(
                        onClick = { onViewSession(s.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(s.flowName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Device: ${s.deviceModel}", fontSize = 11.sp, color = Color.Gray)
                            }
                            
                            Badge(
                                containerColor = if (s.status == "complete") Color(0xFFDDEBF7) else Color(0xFFFFF2CC),
                                contentColor = if (s.status == "complete") Color(0xFF1F4E79) else Color(0xFF7F6000)
                            ) {
                                Text(s.status.uppercase())
                            }
                        }
                    }
                }
            }
        }
    }
}

data class LocalSessionData(
    val id: String,
    val flowName: String,
    val deviceModel: String,
    val status: String,
    val createdAt: String
)
