package com.example.uxauditor.services

import android.accessibilityservice.AccessibilityService
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent

class AuditAccessibilityService : AccessibilityService() {

    private var screenWidth = 1080
    private var screenHeight = 1920

    override fun onServiceConnected() {
        super.onServiceConnected()
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!CaptureService.isServiceRunning) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val sourceNode = event.source
            if (sourceNode != null) {
                var tempNode: android.view.accessibility.AccessibilityNodeInfo? = sourceNode
                while (tempNode != null) {
                    val rect = android.graphics.Rect()
                    tempNode.getBoundsInScreen(rect)
                    
                    val xPx = rect.centerX()
                    val yPx = rect.centerY()
                    
                    if (rect.width() > 0 && rect.height() > 0 && xPx > 0 && yPx > 0 && screenWidth > 0 && screenHeight > 0) {
                        val xPct = xPx.toFloat() / screenWidth
                        val yPct = yPx.toFloat() / screenHeight
                        
                        val clampedX = xPct.coerceIn(0f, 1f)
                        val clampedY = yPct.coerceIn(0f, 1f)
                        
                        android.util.Log.i("AuditAccessibility", "Found clickable node bounds at (${clampedX}, ${clampedY}) after traversing")
                        CaptureService.triggerScreenshot(clampedX, clampedY)
                        
                        if (tempNode != sourceNode) {
                            tempNode.recycle()
                        }
                        sourceNode.recycle()
                        return
                    }
                    
                    val parent = tempNode.parent
                    if (tempNode != sourceNode) {
                        tempNode.recycle()
                    }
                    tempNode = parent
                }
                sourceNode.recycle()
            }
            
            // Fallback if bounds are not retrievable but tap was fired
            android.util.Log.w("AuditAccessibility", "Falling back to center screen coordinates (0.5, 0.5)")
            CaptureService.triggerScreenshot(0.5f, 0.5f)
        }
    }

    override fun onInterrupt() {}
}
