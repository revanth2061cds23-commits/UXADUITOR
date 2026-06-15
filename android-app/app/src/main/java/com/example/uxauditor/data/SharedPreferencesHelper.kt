package com.example.uxauditor.data

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ux_auditor_prefs", Context.MODE_PRIVATE)

    fun saveConnection(url: String, key: String) {
        prefs.edit().putString("sb_url", url).putString("sb_key", key).apply()
    }

    fun getConnectionUrl(): String? = prefs.getString("sb_url", null)
    fun getConnectionKey(): String? = prefs.getString("sb_key", null)

    fun saveAuth(token: String, userUuid: String, email: String) {
        prefs.edit()
            .putString("auth_token", token)
            .putString("user_uuid", userUuid)
            .putString("user_email", email)
            .apply()
    }

    fun getAuthToken(): String? = prefs.getString("auth_token", null)
    fun getUserUuid(): String? = prefs.getString("user_uuid", null)
    fun getUserEmail(): String? = prefs.getString("user_email", null)

    fun clearAuth() {
        prefs.edit()
            .remove("auth_token")
            .remove("user_uuid")
            .remove("user_email")
            .apply()
    }
}