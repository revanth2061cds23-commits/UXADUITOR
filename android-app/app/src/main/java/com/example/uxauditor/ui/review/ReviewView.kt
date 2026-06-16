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
import com.example.uxauditor.data.copyUriToCache
import java.io.File
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import java.util.UUID
import android.content.Context
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.unit.round

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

    val pickMultipleMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                coroutineScope.launch {
                    var currentIndex = screens.size
                    uris.reversed().forEach { uri ->
                        val file = copyUriToCache(context, uri)
                        if (file != null) {
                            val screenId = UUID.randomUUID().toString()
                            db.insertScreen(
                                id = screenId,
                                sessionId = sessionId,
                                seq = currentIndex++,
                                path = file.absolutePath,
                                x = -1f,
                                y = -1f,
                                tag = null
                            )
                        }
                    }
                    screens = db.getLocalScreens(sessionId)
                }
            }
        }
    )

    val deleteScreen = { screenId: String ->
        db.deleteScreen(screenId)
        val remaining = db.getLocalScreens(sessionId)
        for (i in remaining.indices) {
            db.updateScreenSequence(remaining[i].id, i)
        }
        screens = db.getLocalScreens(sessionId)
    }

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

    val gridState = rememberLazyGridState()
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back", color = Color.White)
                    }

                    Button(
                        onClick = {
                            pickMultipleMedia.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F4E79)),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Add Photos", color = Color.White)
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63525)),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Sync to Figma", color = Color.White)
                    }
                }
            }

            Text("Captured Screens (${screens.size})", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(screens.size, key = { idx -> screens[idx].id }) { index ->
                    val s = screens[index]
                    val isDragging = draggedIndex == index
                    
                    val translationX = if (isDragging) dragOffset.x else 0f
                    val translationY = if (isDragging) dragOffset.y else 0f
                    val scale = if (isDragging) 1.06f else 1.0f
                    val alpha = if (isDragging) 0.8f else 1.0f
                    val zIndex = if (isDragging) 10f else 1f

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .zIndex(zIndex)
                            .graphicsLayer {
                                this.translationX = translationX
                                this.translationY = translationY
                                this.scaleX = scale
                                this.scaleY = scale
                                this.alpha = alpha
                            }
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedIndex = index
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount

                                        val dragIdx = draggedIndex
                                        if (dragIdx != null) {
                                            val visibleItems = gridState.layoutInfo.visibleItemsInfo
                                            val draggedItemInfo = visibleItems.find { it.index == dragIdx }
                                            if (draggedItemInfo != null) {
                                                val draggedCenter = Offset(
                                                    draggedItemInfo.offset.x + draggedItemInfo.size.width / 2f + dragOffset.x,
                                                    draggedItemInfo.offset.y + draggedItemInfo.size.height / 2f + dragOffset.y
                                                )

                                                val targetItem = visibleItems.find { info ->
                                                    info.index != dragIdx &&
                                                    draggedCenter.x >= info.offset.x &&
                                                    draggedCenter.x <= info.offset.x + info.size.width &&
                                                    draggedCenter.y >= info.offset.y &&
                                                    draggedCenter.y <= info.offset.y + info.size.height
                                                }

                                                if (targetItem != null) {
                                                    val current = screens[dragIdx]
                                                    val target = screens[targetItem.index]
                                                    db.updateScreenSequence(current.id, target.sequenceIndex)
                                                    db.updateScreenSequence(target.id, current.sequenceIndex)

                                                    draggedIndex = targetItem.index
                                                    val offsetDiff = Offset(
                                                        (targetItem.offset.x - draggedItemInfo.offset.x).toFloat(),
                                                        (targetItem.offset.y - draggedItemInfo.offset.y).toFloat()
                                                    )
                                                    dragOffset -= offsetDiff

                                                    screens = db.getLocalScreens(sessionId)
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggedIndex = null
                                        dragOffset = Offset.Zero
                                    },
                                    onDragCancel = {
                                        draggedIndex = null
                                        dragOffset = Offset.Zero
                                    }
                                )
                            },
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

                            // Clean translucent Delete Button at top-right
                            IconButton(
                                onClick = { deleteScreen(s.id) },
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .align(Alignment.TopEnd)
                                    .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp))
                            ) {
                                Text("✕", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}



