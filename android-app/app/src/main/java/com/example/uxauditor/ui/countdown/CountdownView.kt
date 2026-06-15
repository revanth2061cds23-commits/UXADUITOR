package com.example.uxauditor.ui.countdown

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun CountdownScreen(
    flowName: String,
    onCountdownFinished: () -> Unit
) {
    var count by remember { mutableStateOf(3) }
    var textDisplay by remember { mutableStateOf("3") }

    LaunchedEffect(Unit) {
        delay(1000)
        count = 2
        textDisplay = "2"
        delay(1000)
        count = 1
        textDisplay = "1"
        delay(1000)
        count = 0
        textDisplay = "GO!"
        delay(800)
        onCountdownFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "GET READY FOR",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.LightGray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = flowName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            Text(
                text = textDisplay,
                fontSize = 120.sp,
                fontWeight = FontWeight.Black,
                color = if (count == 0) Color(0xFFE63525) else Color.White
            )
        }
    }
}
