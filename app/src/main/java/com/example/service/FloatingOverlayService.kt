package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.agent.GeminiAgentLoop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 4. Floating Overlay UI (System Alert Window):
 * - A small draggable floating button that stays on screen over other apps using
 *   WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY.
 * - When tapped, it expands into a sleek translucent input console where the user can type a
 *   command directly to the Gemini Vision-Action Loop without leaving the current app.
 */
class FloatingOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var rootOverlayContainer: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var collapsedBubbleView: View? = null
    private var expandedPanelView: View? = null
    private var statusBadgeText: TextView? = null
    private var commandEditText: EditText? = null
    private var isExpanded: Boolean = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        startOverlayForegroundNotification()
        if (Settings.canDrawOverlays(this)) {
            createAndAttachOverlayWindow()
            _isOverlayRunning.value = true
        } else {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted; stopping FloatingOverlayService.")
            _isOverlayRunning.value = false
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_OVERLAY) {
            removeOverlayWindow()
            _isOverlayRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startOverlayForegroundNotification() {
        val channelId = NOTIFICATION_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.overlay_notification_channel_name),
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
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createAndAttachOverlayWindow() {
        if (rootOverlayContainer != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16)
            y = dpToPx(220)
        }
        layoutParams = params

        val container = FrameLayout(this)

        // 1. Collapsed Draggable Floating Bubble
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(14), dpToPx(10), dpToPx(16), dpToPx(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#E60A0E17"), Color.parseColor("#E61E293B"))
            ).apply {
                cornerRadius = dpToPx(28).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#00E5FF"))
            }
            elevation = dpToPx(8).toFloat()
        }

        val dotView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(10), dpToPx(10)).apply {
                marginEnd = dpToPx(8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E5FF"))
            }
        }

        val bubbleLabel = TextView(this).apply {
            text = "AI Pilot"
            setTextColor(Color.parseColor("#F1F5F9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
        }

        bubble.addView(dotView)
        bubble.addView(bubbleLabel)

        // Drag & Tap touch handler for the floating bubble
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        bubble.setOnTouchListener { _, event ->
            val lp = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > dpToPx(6) || abs(dy) > dpToPx(6)) {
                        isDragging = true
                    }
                    lp.x = initialX + dx
                    lp.y = initialY + dy
                    try {
                        windowManager?.updateViewLayout(container, lp)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to update overlay position", e)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        setExpandedState(true)
                    }
                    true
                }
                else -> false
            }
        }

        // 2. Expanded Translucent Command Input Panel
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#EB0A0E17"), Color.parseColor("#EB111827"))
            ).apply {
                cornerRadius = dpToPx(18).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#00E5FF"))
            }
            elevation = dpToPx(12).toFloat()
        }

        // Top Header Row (Title + Live Status + Minimize Button)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleView = TextView(this).apply {
            text = "AutoPilot Overlay Console"
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(this).apply {
            text = "Minimize"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dpToPx(8), dpToPx(4), dpToPx(4), dpToPx(4))
            setOnClickListener { setExpandedState(false) }
        }

        headerRow.addView(titleView)
        headerRow.addView(closeBtn)

        val statusText = TextView(this).apply {
            text = "Ready — type a command below"
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dpToPx(4), 0, dpToPx(8))
        }
        statusBadgeText = statusText

        // Input Row (Transparent EditText + Run/Stop Action Button)
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val editText = EditText(this).apply {
            hint = "Command (e.g. Open YouTube)…"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#801E293B"))
                cornerRadius = dpToPx(10).toFloat()
                setStroke(1, Color.parseColor("#334155"))
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(210), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dpToPx(8)
            }
        }
        commandEditText = editText

        val sendButton = TextView(this).apply {
            text = "Run"
            setTextColor(Color.parseColor("#001F24"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dpToPx(14), dpToPx(9), dpToPx(14), dpToPx(9))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00E5FF"))
                cornerRadius = dpToPx(10).toFloat()
            }
            setOnClickListener {
                val cmd = editText.text?.toString()?.trim().orEmpty()
                if (GeminiAgentLoop.isRunning.value) {
                    GeminiAgentLoop.stopLoop("Stopped from Floating Overlay.")
                } else if (cmd.isNotEmpty()) {
                    editText.setText("")
                    // Collapse focus so gestures can hit the underlying app uninterrupted
                    setExpandedState(false)
                    GeminiAgentLoop.startCommandLoop(applicationContext, cmd)
                }
            }
        }

        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendButton.performClick()
                true
            } else {
                false
            }
        }

        inputRow.addView(editText)
        inputRow.addView(sendButton)

        panel.addView(headerRow)
        panel.addView(statusText)
        panel.addView(inputRow)

        collapsedBubbleView = bubble
        expandedPanelView = panel

        container.addView(bubble)
        container.addView(panel)
        rootOverlayContainer = container

        try {
            wm.addView(container, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view to WindowManager", e)
        }

        // Observe live loop status so the floating bubble & panel reflect current step
        serviceScope.launch {
            GeminiAgentLoop.statusSummary.collectLatest { summary ->
                statusBadgeText?.text = summary
                bubbleLabel.text = if (GeminiAgentLoop.isRunning.value) {
                    "AI Active"
                } else {
                    "AI Pilot"
                }
                sendButton.text = if (GeminiAgentLoop.isRunning.value) "Stop" else "Run"
            }
        }
    }

    /**
     * Toggles between the compact non-focusable bubble and the focusable transparent command bar.
     */
    private fun setExpandedState(expand: Boolean) {
        val container = rootOverlayContainer ?: return
        val lp = layoutParams ?: return
        isExpanded = expand

        if (expand) {
            collapsedBubbleView?.visibility = View.GONE
            expandedPanelView?.visibility = View.VISIBLE
            // Remove FLAG_NOT_FOCUSABLE so the EditText can receive soft keyboard input
            lp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        } else {
            expandedPanelView?.visibility = View.GONE
            collapsedBubbleView?.visibility = View.VISIBLE
            // Restore FLAG_NOT_FOCUSABLE so underlying apps receive all key/focus events
            lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            commandEditText?.windowToken?.let { token ->
                imm?.hideSoftInputFromWindow(token, 0)
            }
        }

        try {
            windowManager?.updateViewLayout(container, lp)
            if (expand) {
                commandEditText?.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                commandEditText?.let { imm?.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update overlay layout flags", e)
        }
    }

    private fun removeOverlayWindow() {
        val container = rootOverlayContainer ?: return
        try {
            windowManager?.removeView(container)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing overlay view", e)
        }
        rootOverlayContainer = null
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        removeOverlayWindow()
        _isOverlayRunning.value = false
        serviceScope.cancel()
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FloatingOverlaySvc"
        private const val NOTIFICATION_CHANNEL_ID = "autopilot_overlay_channel"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_STOP_OVERLAY = "com.example.service.action.STOP_OVERLAY"

        @Volatile
        var instance: FloatingOverlayService? = null
            private set

        private val _isOverlayRunning = MutableStateFlow(false)
        val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

        fun startOverlay(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context)) {
                return false
            }
            val intent = Intent(context, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            return true
        }

        fun stopOverlay(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_STOP_OVERLAY
            }
            context.startService(intent)
        }
    }
}
