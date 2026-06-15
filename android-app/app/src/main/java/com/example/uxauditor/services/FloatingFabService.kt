package com.example.uxauditor.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import com.example.uxauditor.MainActivity

class FloatingFabService : Service() {

    companion object {
        private var instance: FloatingFabService? = null
        
        fun flashIndicator() {
            instance?.flashIndicatorInternal()
        }

        fun setFabVisibility(visible: Boolean) {
            instance?.setFabVisibilityInternal(visible)
        }
    }

    private var windowManager: WindowManager? = null
    private var fabContainer: android.widget.LinearLayout? = null
    private var snapButton: Button? = null
    private var params: WindowManager.LayoutParams? = null
    private var isFabAdded = false

    private fun setFabVisibilityInternal(visible: Boolean) {
        Handler(Looper.getMainLooper()).post {
            try {
                if (visible) {
                    if (!isFabAdded && fabContainer != null && params != null) {
                        windowManager?.addView(fabContainer, params)
                        isFabAdded = true
                    }
                } else {
                    if (isFabAdded && fabContainer != null) {
                        windowManager?.removeView(fabContainer)
                        isFabAdded = false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FloatingFabService", "Error toggling FAB visibility", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingFab()
    }

    private fun createFloatingFab() {
        val containerShape = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 50f
            setColor(Color.parseColor("#1E1E2E")) // Sleek Slate Charcoal
            setStroke(2, Color.parseColor("#313244")) // Subtle modern outline
        }

        fabContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            background = containerShape
            setPadding(10, 10, 10, 10)
        }
        
        val snapButtonDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 40f
            setColor(Color.parseColor("#313244")) // Button background
        }

        snapButton = Button(this).apply {
            text = "📷 SNAP"
            background = snapButtonDrawable
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(15, 10, 15, 10)
        }

        val buttonParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(4, 4, 4, 4)
        }

        fabContainer?.addView(snapButton, buttonParams)

        val paramsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            paramsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 50
            y = 150
        }
        this.params = params

        // Shared touch listener for dragging the entire overlay layout via the snap button
        val touchListener = object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var clickTime = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        clickTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX - (event.rawX - initialTouchX).toInt()
                        params.y = initialY - (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(fabContainer, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - clickTime
                        if (duration < 250) {
                            v.performClick()
                            CaptureService.triggerScreenshot(0.5f, 0.5f)
                        }
                        return true
                    }
                }
                return false
            }
        }

        snapButton?.setOnTouchListener(touchListener)

        windowManager?.addView(fabContainer, params)
        isFabAdded = true
    }

    private fun flashIndicatorInternal() {
        Handler(Looper.getMainLooper()).post {
            // 1. Premium crisp haptic feedback vibration
            try {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(60, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(60)
                }
            } catch (e: Exception) {
                android.util.Log.e("FloatingFabService", "Haptic feedback failed", e)
            }

            // 2. Premium visual bounce micro-interaction (Slide up & down)
            fabContainer?.animate()
                ?.translationY(-50f)
                ?.setDuration(150)
                ?.withEndAction {
                    fabContainer?.animate()
                        ?.translationY(0f)
                        ?.setDuration(150)
                        ?.start()
                }
                ?.start()

            // 3. Quick button flash color confirmation (Pastel Green)
            val originalBg = snapButton?.background
            val originalTextColor = snapButton?.currentTextColor ?: Color.WHITE
            
            val flashBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(Color.parseColor("#A6E3A1")) // Pastel green
            }
            
            snapButton?.background = flashBg
            snapButton?.setTextColor(Color.parseColor("#11111B")) // High contrast dark charcoal text
            
            Handler(Looper.getMainLooper()).postDelayed({
                snapButton?.background = originalBg
                snapButton?.setTextColor(originalTextColor)
            }, 300)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFabAdded && fabContainer != null) {
            try {
                windowManager?.removeView(fabContainer)
            } catch (e: Exception) {}
        }
        isFabAdded = false
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
