package com.example.uxauditor.ui.review

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uxauditor.data.DatabaseHelper
import com.example.uxauditor.data.LocalScreen
import com.example.uxauditor.data.SupabaseUploader
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    sessionId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { DatabaseHelper(context) }
    var screens by remember { mutableStateOf(listOf<LocalScreen>()) }
    var flowName by remember { mutableStateOf("Audit Session") }
    
    var isUploading by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var progressPercentage by remember { mutableStateOf(0) }

    // Fetch Screens
    LaunchedEffect(sessionId) {
        screens = db.getLocalScreens(sessionId)
        // Get session name
        val readableDb = db.readableDatabase
        val cursor = readableDb.rawQuery("SELECT flow_name FROM sessions WHERE id = ?", arrayOf(sessionId))
        if (cursor.moveToFirst()) {
            flowName = cursor.getString(0)
        }
        cursor.close()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(flowName, fontWeight = FontWeight.Bold) },
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
            if (isUploading) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(progressMessage, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                    LinearProgressIndicator(
                        progress = { progressPercentage / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Color(0xFFE63525)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                        Text("Back", color = Color.White)
                    }

                    Button(
                        onClick = {
                            isUploading = true
                            val uploader = SupabaseUploader(context)
                            uploader.uploadSession(sessionId, flowName, object : SupabaseUploader.UploadCallback {
                                override fun onProgress(percentage: Int, message: String) {
                                    coroutineScope.launch {
                                        progressPercentage = percentage
                                        progressMessage = message
                                    }
                                }

                                override fun onSuccess() {
                                    coroutineScope.launch {
                                        isUploading = false
                                        Toast.makeText(context, "Session uploaded successfully!", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }
                                }

                                override fun onFailure(err: String) {
                                    coroutineScope.launch {
                                        isUploading = false
                                        Toast.makeText(context, "Upload failed: ${err}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            })
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63525))
                    ) {
                        Text("Sync to Figma", color = Color.White)
                    }
                }
            }

            Text("Captured Screens (${screens.size})", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(screens) { s ->
                    Card(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Draw image thumbnail
                            val imgFile = File(s.imagePath)
                            if (imgFile.exists()) {
                                val bitmap = android.graphics.BitmapFactory.decodeFile(imgFile.absolutePath)
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Screen shot",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            
                            // Draw sequence Badge
                            Badge(
                                containerColor = Color(0xFFE63525),
                                contentColor = Color.White,
                                modifier = Modifier.padding(8.dp).align(Alignment.TopStart)
                            ) {
                                Text("${s.sequenceIndex + 1}")
                            }
                        }
                    }
                }
            }
        }
    }
}
