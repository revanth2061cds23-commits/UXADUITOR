package com.example.uxauditor

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Login : NavKey
@Serializable data object Main : NavKey
@Serializable data class Countdown(val sessionId: String, val flowName: String) : NavKey
@Serializable data class Review(val sessionId: String) : NavKey
