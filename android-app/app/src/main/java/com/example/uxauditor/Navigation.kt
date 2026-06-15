package com.example.uxauditor

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.uxauditor.data.SharedPreferencesHelper
import com.example.uxauditor.services.CaptureService
import com.example.uxauditor.ui.countdown.CountdownScreen
import com.example.uxauditor.ui.login.LoginScreen
import com.example.uxauditor.ui.main.MainScreen
import com.example.uxauditor.ui.review.ReviewScreen

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val prefs = remember { SharedPreferencesHelper(context) }
    
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
