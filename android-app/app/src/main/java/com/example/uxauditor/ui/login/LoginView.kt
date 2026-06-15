package com.example.uxauditor.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uxauditor.data.SharedPreferencesHelper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    prefs: SharedPreferencesHelper,
    onLoginSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Ready") }
    var isLoading by remember { mutableStateOf(false) }

    // Hardcoded credentials
    val sbUrl = "https://naneeovpzwyfnbaaujpi.supabase.co"
    val sbKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5hbmVlb3Zwend5Zm5iYWF1anBpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODAxOTYwMzIsImV4cCI6MjA5NTc3MjAzMn0.Ilb6N52RvkwpiQ8iI0vGpIvDOysNgkubzXFh5sSUoUk"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "UX Audit Tracker",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE63525) // Accent Brand Red
        )
        Text(
            text = "Auto-Sync Companion App",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Divider(modifier = Modifier.padding(bottom = 10.dp))

        // Toggle Buttons Sign In / Sign Up
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { isSignUp = false },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isSignUp) Color(0xFFE63525) else Color.LightGray
                ),
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Sign In")
            }

            Button(
                onClick = { isSignUp = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSignUp) Color(0xFFE63525) else Color.LightGray
                ),
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Sign Up")
            }
        }

        // Email / Password inputs
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            visualTransformation = PasswordVisualTransformation()
        )

        if (isLoading) {
            CircularProgressIndicator(color = Color(0xFFE63525))
        } else {
            Button(
                onClick = {
                    if (email.isEmpty() || password.isEmpty()) {
                        statusText = "Please fill in email and password."
                        return@Button
                    }
                    
                    isLoading = true
                    prefs.saveConnection(sbUrl, sbKey)

                    coroutineScope.launch(Dispatchers.IO) {
                        val client = OkHttpClient()
                        val mediaType = "application/json".toMediaType()
                        val payload = JSONObject().apply {
                            put("email", email)
                            put("password", password)
                        }

                        val endpoint = if (isSignUp) "/auth/v1/signup" else "/auth/v1/token?grant_type=password"
                        val req = Request.Builder()
                            .url(sbUrl.trimEnd('/') + endpoint)
                            .post(payload.toString().toRequestBody(mediaType))
                            .addHeader("apikey", sbKey)
                            .build()

                        try {
                            client.newCall(req).execute().use { response ->
                                val resString = response.body?.string() ?: ""
                                if (response.isSuccessful) {
                                    withContext(Dispatchers.Main) {
                                        if (isSignUp) {
                                            statusText = "Sign up successful! Please log in."
                                            isSignUp = false
                                            isLoading = false
                                        } else {
                                            val json = JSONObject(resString)
                                            val token = json.getString("access_token")
                                            val userObj = json.getJSONObject("user")
                                            val uuid = userObj.getString("id")

                                            // Persist user token and UUID locally
                                            prefs.saveAuth(token, uuid, email)
                                            onLoginSuccess()
                                        }
                                    }
                                } else {
                                    val err = JSONObject(resString).optString("error_description", "Authentication failed.")
                                    withContext(Dispatchers.Main) {
                                        statusText = "Error: ${err}"
                                        isLoading = false
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                statusText = "Network Error: ${e.message}"
                                isLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63525)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isSignUp) "Sign Up Now" else "Sign In Now", color = Color.White)
            }
        }

        Text(
            text = statusText,
            color = Color.DarkGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
