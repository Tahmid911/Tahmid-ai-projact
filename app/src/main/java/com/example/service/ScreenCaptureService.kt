package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * 3. ScreenCaptureService (MediaProjection):
 * - A Foreground Service (foregroundServiceType="mediaProjection") that holds an active
 *   MediaProjection session and captures a single frame (Bitmap) of the current screen when
 *   requested by the Gemini Vision-Action Loop.
 * - Includes an automatic window/canvas fallback when MediaProjection permission has not yet been
 *   granted (e.g., in the web preview emulator) so the Gemini Vision-Action Loop always receives
 *   a valid screen Bitmap.
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var captureWidth: Int = 720
    private var captureHeight: Int = 1600
    private var screenDensityDpi: Int = 320

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection stopped by system.")
            releaseProjectionResources()
            _isProjectionActive.value = false
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val metrics = resources.displayMetrics
        // Scale down to max 720px width for fast multimodal Gemini transmission (< 20MB limit)
        val scale = if (metrics.widthPixels > 720) 720f / metrics.widthPixels else 1f
        captureWidth = ((metrics.widthPixels * scale).toInt() / 2 * 2).coerceAtLeast(360)
        captureHeight = ((metrics.heightPixels * scale).toInt() / 2 * 2).coerceAtLeast(640)
        screenDensityDpi = metrics.densityDpi

        backgroundThread = HandlerThread("ScreenCaptureImageThread").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseProjectionResources()
                _isProjectionActive.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startCaptureForegroundNotification()
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultData != null) {
                    initializeMediaProjection(resultCode, resultData)
                } else {
                    Log.w(TAG, "ScreenCaptureService started without MediaProjection resultData.")
                }
            }
        }
        return START_STICKY
    }

    private fun startCaptureForegroundNotification() {
        val channelId = NOTIFICATION_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.capture_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initializeMediaProjection(resultCode: Int, resultData: Intent) {
        try {
            releaseProjectionResources()
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mgr.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                Log.e(TAG, "Failed to obtain MediaProjection instance.")
                _isProjectionActive.value = false
                return
            }

            // Android 14+ (API 34+) requires registering a Callback BEFORE createVirtualDisplay
            projection.registerCallback(projectionCallback, backgroundHandler)

            val reader = ImageReader.newInstance(
                captureWidth,
                captureHeight,
                PixelFormat.RGBA_8888,
                2
            )

            val vDisplay = projection.createVirtualDisplay(
                "AutoPilotScreenCapture",
                captureWidth,
                captureHeight,
                screenDensityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                backgroundHandler
            )

            mediaProjection = projection
            imageReader = reader
            virtualDisplay = vDisplay
            _isProjectionActive.value = true
            Log.i(TAG, "MediaProjection VirtualDisplay initialized (${captureWidth}x${captureHeight}).")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaProjection", e)
            _isProjectionActive.value = false
        }
    }

    /**
     * Captures a single screenshot frame from the MediaProjection ImageReader and converts it to
     * a Bitmap, properly handling rowStride padding.
     */
    suspend fun captureSingleFrame(): Bitmap? = withContext(Dispatchers.Default) {
        val reader = imageReader ?: return@withContext null
        // Allow up to 3 short retries if the buffer hasn't produced a fresh frame yet
        repeat(3) { attempt ->
            val image = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                Log.w(TAG, "acquireLatestImage attempt $attempt failed", e)
                null
            }

            if (image != null) {
                try {
                    val width = image.width
                    val height = image.height
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val paddedBitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    buffer.rewind()
                    paddedBitmap.copyPixelsFromBuffer(buffer)

                    val croppedBitmap = if (rowPadding == 0) {
                        paddedBitmap
                    } else {
                        val exact = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
                        paddedBitmap.recycle()
                        exact
                    }
                    _lastCapturedBitmap.value = croppedBitmap
                    return@withContext croppedBitmap
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to convert Image plane to Bitmap", e)
                } finally {
                    image.close()
                }
            }
            delay(80L)
        }
        return@withContext _lastCapturedBitmap.value
    }

    private fun releaseProjectionResources() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
    }

    override fun onDestroy() {
        releaseProjectionResources()
        _isProjectionActive.value = false
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_CHANNEL_ID = "autopilot_screen_capture_channel"
        private const val NOTIFICATION_ID = 4101

        const val ACTION_START = "com.example.service.action.START_CAPTURE"
        const val ACTION_STOP = "com.example.service.action.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        @Volatile
        var instance: ScreenCaptureService? = null
            private set

        private var activityRef: WeakReference<Activity>? = null

        private val _isProjectionActive = MutableStateFlow(false)
        val isProjectionActive: StateFlow<Boolean> = _isProjectionActive.asStateFlow()

        private val _lastCapturedBitmap = MutableStateFlow<Bitmap?>(null)
        val lastCapturedBitmap: StateFlow<Bitmap?> = _lastCapturedBitmap.asStateFlow()

        fun registerHostActivity(activity: Activity) {
            activityRef = WeakReference(activity)
        }

        fun startServiceWithProjection(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopProjectionService(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /**
         * Primary entry point used by the Gemini Vision-Action Loop:
         * 1. If MediaProjection is active, captures a real system-wide frame from ScreenCaptureService.
         * 2. Otherwise, captures the active Activity window surface via hardware-accelerated PixelCopy
         *    (avoiding software Canvas drawing that triggers RippleDrawable.STYLE_PATTERNED errors),
         *    falling back to a synthetic diagnostic frame if unavailable.
         */
        suspend fun captureScreenForAgent(): Bitmap {
            val activeService = instance
            if (activeService != null && _isProjectionActive.value) {
                val projectedFrame = activeService.captureSingleFrame()
                if (projectedFrame != null) {
                    return projectedFrame
                }
            }

            // Fallback: Capture current Activity window via hardware-accelerated PixelCopy (API 26+)
            val hostActivity = activityRef?.get()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                hostActivity != null &&
                !hostActivity.isFinishing
            ) {
                val pixelCopyBitmap = captureWindowWithPixelCopy(hostActivity)
                if (pixelCopyBitmap != null) {
                    _lastCapturedBitmap.value = pixelCopyBitmap
                    return pixelCopyBitmap
                }
            }

            // Final synthetic diagnostic frame if neither MediaProjection nor PixelCopy is ready
            return createDiagnosticScreenBitmap().also {
                _lastCapturedBitmap.value = it
            }
        }

        private suspend fun captureWindowWithPixelCopy(activity: Activity): Bitmap? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
            val window = activity.window ?: return null
            val decorView = window.decorView ?: return null
            val viewWidth = decorView.width
            val viewHeight = decorView.height
            if (viewWidth <= 0 || viewHeight <= 0) return null

            val scale = if (viewWidth > 360) 360f / viewWidth else 1f
            val targetWidth = (viewWidth * scale).toInt().coerceAtLeast(240)
            val targetHeight = (viewHeight * scale).toInt().coerceAtLeast(420)
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

            return kotlinx.coroutines.withTimeoutOrNull(1500L) {
                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    try {
                        android.view.PixelCopy.request(
                            window,
                            bitmap,
                            { copyResult ->
                                if (cont.isActive) {
                                    if (copyResult == android.view.PixelCopy.SUCCESS) {
                                        cont.resumeWith(Result.success(bitmap))
                                    } else {
                                        bitmap.recycle()
                                        cont.resumeWith(Result.success(null))
                                    }
                                }
                            },
                            Handler(android.os.Looper.getMainLooper())
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "PixelCopy fallback unavailable: ${e.message}")
                        if (cont.isActive) {
                            cont.resumeWith(Result.success(null))
                        }
                    }
                }
            }
        }

        private fun createDiagnosticScreenBitmap(): Bitmap {
            val width = 540
            val height = 960
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    Color.parseColor("#0A0E17"),
                    Color.parseColor("#162032"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00E5FF")
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRect(24f, 64f, width - 24f, height - 64f, strokePaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F1F5F9")
                textSize = 26f
            }
            canvas.drawText("AutoPilot AI Screen Frame (540x960)", 48f, 120f, textPaint)
            return bmp
        }
    }
}
