package com.example.uxauditor.data

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseUploader(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val prefs = SharedPreferencesHelper(context)

    interface UploadCallback {
        fun onProgress(percentage: Int, message: String)
        fun onSuccess()
        fun onFailure(err: String)
    }

    fun uploadSession(sessionId: String, flowName: String, callback: UploadCallback) {
        val url = "https://naneeovpzwyfnbaaujpi.supabase.co"
        val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5hbmVlb3Zwend5Zm5iYWF1anBpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODAxOTYwMzIsImV4cCI6MjA5NTc3MjAzMn0.Ilb6N52RvkwpiQ8iI0vGpIvDOysNgkubzXFh5sSUoUk"
        val token = prefs.getAuthToken()
        val userUuid = prefs.getUserUuid()

        if (token == null || userUuid == null) {
            callback.onFailure("Missing authorization or expired token. Please Sign In again.")
            return
        }

        val db = DatabaseHelper(context)
        val screens = db.getLocalScreens(sessionId)

        if (screens.isEmpty()) {
            callback.onFailure("No screens found locally for this session.")
            return
        }

        // Run upload in background thread
        Thread {
            try {
                callback.onProgress(10, "Creating Remote Session...")
                
                // 1. Post Remote Session Row
                val sessionJson = JSONObject().apply {
                    put("id", sessionId)
                    put("user_id", userUuid)
                    put("flow_name", flowName)
                    put("device_model", android.os.Build.MODEL)
                    put("screen_width_px", 1080) // Standard fallback or computed
                    put("screen_height_px", 1920)
                    put("status", "uploading")
                }

                val sessionReq = Request.Builder()
                    .url("${url}/rest/v1/sessions")
                    .post(sessionJson.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer ${token}")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .build()

                client.newCall(sessionReq).execute().use { res ->
                    if (!res.isSuccessful) {
                        val body = res.body?.string() ?: ""
                        if (!body.contains("duplicate key")) {
                            throw IOException("Failed to create session on Supabase: ${res.code} - ${body}")
                        }
                    }
                }

                // 2. Upload images to Storage & Insert rows to Screens
                for (i in screens.indices) {
                    val s = screens[i]
                    val pct = 20 + (i * 70) / screens.size
                    callback.onProgress(pct, "Uploading screen ${i + 1}/${screens.size}...")

                    val imgFile = File(s.imagePath)
                    if (!imgFile.exists()) {
                        Log.e("SupabaseUploader", "Local image file does not exist: ${s.imagePath}")
                        continue
                    }

                    // Upload Image to Storage Bucket 'screens'
                    // Paths: user_uuid/session_id/index.png
                    val remotePath = "${userUuid}/${sessionId}/${s.sequenceIndex}.png"
                    
                    val fileBody = imgFile.asRequestBody("image/png".toMediaType())
                    val storageReq = Request.Builder()
                        .url("${url}/storage/v1/object/screens/${remotePath}")
                        .post(fileBody)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer ${token}")
                        .build()

                    var storagePublicUrl = ""
                    client.newCall(storageReq).execute().use { res ->
                        val bodyString = res.body?.string() ?: ""
                        if (res.isSuccessful) {
                            storagePublicUrl = "${url}/storage/v1/object/public/screens/${remotePath}"
                        } else if (res.code == 400 && (bodyString.contains("Duplicate", ignoreCase = true) || bodyString.contains("already exists", ignoreCase = true))) {
                            storagePublicUrl = "${url}/storage/v1/object/public/screens/${remotePath}"
                        } else {
                            throw IOException("Storage upload failed: ${res.code} - ${bodyString}")
                        }
                    }

                    // Insert Row in screens table
                    val screenJson = JSONObject().apply {
                        put("id", s.id)
                        put("session_id", sessionId)
                        put("sequence_index", s.sequenceIndex)
                        put("image_url", storagePublicUrl)
                        put("tap_x_pct", s.tapXPct.toDouble())
                        put("tap_y_pct", s.tapYPct.toDouble())
                    }

                    val screenReq = Request.Builder()
                        .url("${url}/rest/v1/screens")
                        .post(screenJson.toString().toRequestBody("application/json".toMediaType()))
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer ${token}")
                        .build()

                    client.newCall(screenReq).execute().use { res ->
                        if (!res.isSuccessful) {
                            throw IOException("Failed to create screen record: ${res.code} - ${res.body?.string()}")
                        }
                    }
                }

                // 3. Mark Session Complete
                callback.onProgress(95, "Completing Sync...")
                
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val isoTimestamp = sdf.format(java.util.Date())

                val patchJson = JSONObject().apply {
                    put("status", "complete")
                    put("completed_at", isoTimestamp)
                }

                val patchReq = Request.Builder()
                    .url("${url}/rest/v1/sessions?id=eq.${sessionId}")
                    .patch(patchJson.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer ${token}")
                    .build()

                client.newCall(patchReq).execute().use { res ->
                    if (!res.isSuccessful) {
                        throw IOException("Failed to complete session: ${res.code}")
                    }
                }

                db.updateSessionStatus(sessionId, "complete")
                callback.onSuccess()

            } catch (e: Exception) {
                Log.e("SupabaseUploader", "Upload failed", e)
                val errMsg = e.message ?: "Unknown upload network error"
                if (errMsg.contains("JWT expired", ignoreCase = true) || errMsg.contains("401", ignoreCase = true)) {
                    callback.onFailure("Your session has expired. Please Sign Out and Sign In again on your phone to refresh your login.")
                } else {
                    callback.onFailure(errMsg)
                }
            }
        }.start()
    }
}
