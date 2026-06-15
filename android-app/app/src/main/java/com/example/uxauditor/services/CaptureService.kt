package com.example.uxauditor.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.uxauditor.MainActivity
import com.example.uxauditor.data.DatabaseHelper
import java.io.File
import java.io.FileOutputStream
import java.util.*

class CaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "CaptureServiceChannel"
        const val NOTIFICATION_ID = 202
        const val ACTION_STOP = "com.example.uxauditor.ACTION_STOP"

        var isServiceRunning = false
            private set

        var activeSessionId: String? = null
        var activeFlowName: String? = null
        
        var mediaProjectionResultCode = 0
        var mediaProjectionData: Intent? = null
        
        private var instance: CaptureService? = null

        fun triggerScreenshot(xPct: Float, yPct: Float) {
            instance?.let { service ->
                // First, hide the floating FAB overlay completely
                FloatingFabService.setFabVisibility(false)
                
                // Wait 150ms for WindowManager to completely execute the view removal and clear the display buffer
                Handler(Looper.getMainLooper()).postDelayed({
                    synchronized(service) {
                        service.isWaitingForCleanFrame = true
                        service.pendingScreenshotCoords = Pair(xPct, yPct)
                        // Clear the current cached frame to force waiting for a new clean frame
                        val oldImg = service.latestImage
                        service.latestImage = null
                        oldImg?.close()
                    }
                    
                    // Safety timeout fallback (in case no new frame arrives within 200ms of starting clean capture)
                    Handler(Looper.getMainLooper()).postDelayed({
                        var shouldFallback = false
                        synchronized(service) {
                            if (service.isWaitingForCleanFrame) {
                                service.isWaitingForCleanFrame = false
                                shouldFallback = true
                            }
                        }
                        if (shouldFallback) {
                            android.util.Log.w("CaptureService", "Clean frame timeout! Using fallback screenshot.")
                            try {
                                service.takeScreenshotInternal(xPct, yPct)
                            } finally {
                                FloatingFabService.setFabVisibility(true)
                            }
                        }
                    }, 200)
                }, 150)
            }
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var windowManager: WindowManager? = null
    private var latestImage: Image? = null
    private var isWaitingForCleanFrame = false
    private var pendingScreenshotCoords: Pair<Float, Float>? = null

    private var screenWidth = 1080
    private var screenHeight = 1920
    private var screenDensity = 400
    private var sequenceIndex = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        isServiceRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val metrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            endSessionFromNotification()
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()

        val stopIntent = Intent(this, CaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UX Auditor Active Session")
            .setContentText("Recording screens and micro-interactions...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true) // Persistent Ongoing notification
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "END SESSION", stopPendingIntent)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)

        // Initialize MediaProjection
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(mediaProjectionResultCode, mediaProjectionData!!)

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        
        val handler = Handler(Looper.getMainLooper())
        imageReader?.setOnImageAvailableListener({ reader ->
            try {
                val img = reader.acquireLatestImage()
                if (img != null) {
                    var shouldCapture = false
                    var coords: Pair<Float, Float>? = null
                    
                    synchronized(this) {
                        if (isWaitingForCleanFrame) {
                            isWaitingForCleanFrame = false
                            shouldCapture = true
                            coords = pendingScreenshotCoords
                            pendingScreenshotCoords = null
                        }
                        
                        val oldImg = latestImage
                        latestImage = img
                        oldImg?.close()
                    }
                    
                    if (shouldCapture && coords != null) {
                        // Clean frame received! Capture it immediately on the main thread and restore FAB
                        android.util.Log.i("CaptureService", "Clean frame received successfully! Taking screenshot.")
                        Handler(Looper.getMainLooper()).post {
                            try {
                                takeScreenshotInternal(coords!!.first, coords!!.second)
                            } finally {
                                FloatingFabService.setFabVisibility(true)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CaptureService", "Error in image listener", e)
            }
        }, handler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        // Floating FAB service disabled to prevent on-screen layout overlay artifacts
        // val fabIntent = Intent(this, FloatingFabService::class.java)
        // startService(fabIntent)

        return START_NOT_STICKY
    }

    private fun endSessionFromNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("end_session_confirmed", true)
            putExtra("session_id", activeSessionId)
        }
        startActivity(intent)
    }

    private var lastCapturedBitmap: Bitmap? = null

    private fun takeScreenshotInternal(xPct: Float, yPct: Float) {
        var image: Image? = null
        synchronized(this) {
            image = latestImage
            latestImage = null // Take ownership
        }

        if (image == null) {
            // Fallback to direct synchronous acquisition if listener hasn't received a frame yet
            image = imageReader?.acquireLatestImage() ?: imageReader?.acquireNextImage()
        }

        if (image == null) {
            val fallbackBitmap = lastCapturedBitmap
            if (fallbackBitmap != null) {
                Log.i("CaptureService", "Using cached fallback bitmap for screenshot (static screen / popup)")
                saveBitmapToDiskAndDb(fallbackBitmap, xPct, yPct)
                return
            }
            Log.w("CaptureService", "No screenshot frame or fallback bitmap available.")
            return
        }

        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            
            // Crop out any buffer padding
            val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            image.close()

            // Update the cached fallback bitmap
            lastCapturedBitmap?.recycle()
            lastCapturedBitmap = cleanBitmap.copy(Bitmap.Config.ARGB_8888, true)

            saveBitmapToDiskAndDb(cleanBitmap, xPct, yPct)
        } catch (e: Exception) {
            Log.e("CaptureService", "Error during screenshot processing", e)
            try {
                image.close()
            } catch (ex: Exception) {}
        }
    }

    private fun saveBitmapToDiskAndDb(cleanBitmap: Bitmap, xPct: Float, yPct: Float) {
        try {
            // Save image to disk
            val filename = "screen_${System.currentTimeMillis()}.png"
            val file = File(cacheDir, filename)
            FileOutputStream(file).use { out ->
                cleanBitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
            }

            // Save to SQLite cache
            val db = DatabaseHelper(this)
            val screenId = UUID.randomUUID().toString()
            db.insertScreen(
                id = screenId,
                sessionId = activeSessionId ?: "",
                seq = sequenceIndex++,
                path = file.absolutePath,
                x = xPct,
                y = yPct,
                tag = null
            )
            
            Log.i("CaptureService", "Captured screen ${sequenceIndex} at (${xPct}, ${yPct})")
            
            // Signal FAB stop service to flash white as visual confirmation
            FloatingFabService.flashIndicator()
        } catch (e: Exception) {
            Log.e("CaptureService", "Error saving bitmap to disk or DB", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        
        synchronized(this) {
            latestImage?.close()
            latestImage = null
        }
        
        lastCapturedBitmap?.recycle()
        lastCapturedBitmap = null
        
        instance = null
        
        val fabIntent = Intent(this, FloatingFabService::class.java)
        stopService(fabIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Capture Service Foreground Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
