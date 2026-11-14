package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.agent.GeminiAgentLoop
import com.example.data.AgentMemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * AgentAccessibilityService (Extends AccessibilityService):
 * - Overrides onAccessibilityEvent to observe window/content transitions and intercept
 *   TYPE_VIEW_CLICKED events when Train Mode ("listen" mode) is active.
 * - When Train Mode is active, autonomous gesture dispatching is halted and every user tap
 *   extracts viewIdResourceName, text, contentDescription, className, and screen bounds into
 *   the local Room Database as a Learned Workflow.
 * - Provides gesture dispatchers: performTap(x, y), performSwipe(startX, startY, endX, endY),
 *   and performTextInput(text).
 * - Dumps the current screen's View Node hierarchy into a structured string for Gemini.
 */
class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        Log.i(TAG, "AgentAccessibilityService connected.")
        refreshLatestNodeSnapshot()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isNotBlank()) {
            _activePackageName.value = pkg
        }

        // 1. Train Mode (UI Recording): Intercept TYPE_VIEW_CLICKED events in Listen Mode
        if (_isTrainModeActive.value && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            // Record clicks from 3rd-party apps (or any non-self package) into Room Database
            if (pkg.isNotBlank() && pkg != packageName) {
                recordAccessibilityClickEvent(applicationContext, event, pkg)
            }
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                _lastEventSummary.value =
                    "${AccessibilityEvent.eventTypeToString(event.eventType)} ($pkg)"
            }
            else -> Unit
        }
    }

    private fun recordAccessibilityClickEvent(
        context: Context,
        event: AccessibilityEvent,
        pkg: String
    ) {
        val sourceNode = event.source
        val viewId = sourceNode?.viewIdResourceName
            ?.ifBlank { null }
            ?: "${pkg}:id/clicked_view"

        val className = sourceNode?.className?.toString()
            ?: event.className?.toString()
            ?: "android.view.View"

        val nodeText = sourceNode?.text?.toString()?.trim()?.ifBlank { null }
            ?: event.text.joinToString(" ") { it.toString().trim() }.trim()

        val contentDesc = sourceNode?.contentDescription?.toString()?.trim()?.ifBlank { null }
            ?: event.contentDescription?.toString()?.trim().orEmpty()

        val bounds = Rect()
        if (sourceNode != null) {
            sourceNode.getBoundsInScreen(bounds)
        }
        val centerX = if (bounds.width() > 0) bounds.centerX() else 540
        val centerY = if (bounds.height() > 0) bounds.centerY() else 1200
        val boundsStr = if (bounds.width() > 0) {
            "[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]"
        } else {
            "[0,0][1080,2400]"
        }

        val workflowTitle = _activeTrainingWorkflowName.value.ifBlank {
            "Learned Workflow (${AgentMemoryRepository.deriveFriendlyAppName(pkg)})"
        }

        recordScope.launch {
            val saved = AgentMemoryRepository.getInstance(context).recordClickedNode(
                packageName = pkg,
                workflowName = workflowTitle,
                viewIdResourceName = viewId,
                className = className,
                nodeText = nodeText,
                contentDescription = contentDesc,
                centerX = centerX,
                centerY = centerY,
                boundsString = boundsStr
            )
            _trainModeRecordedCount.value += 1
            _lastRecordedNodeSummary.value =
                "Recorded #${saved.stepOrder} in ${saved.appLabel}: ${saved.viewIdResourceName.substringAfterLast('/')} " +
                    "\"${saved.nodeText.ifBlank { saved.contentDescription }}\""
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AgentAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        _isConnected.value = false
        super.onDestroy()
    }

    /**
     * Dispatches a single tap gesture at the given (x, y) screen coordinates.
     * Blocked when Train Mode ("listen" mode) is active.
     */
    fun performTap(
        x: Float,
        y: Float,
        durationMs: Long = 90L,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (_isTrainModeActive.value) {
            Log.i(TAG, "performTap skipped: Train Mode (listen-only) is active.")
            onComplete?.invoke(false)
            return false
        }

        val safeX = x.coerceAtLeast(1f)
        val safeY = y.coerceAtLeast(1f)
        val path = Path().apply {
            moveTo(safeX, safeY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(10L))
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "performTap($safeX, $safeY) completed")
                    onComplete?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "performTap($safeX, $safeY) cancelled")
                    onComplete?.invoke(false)
                }
            },
            null
        )
    }

    /**
     * Suspend wrapper around performTap(x, y) for sequential coroutine execution in the agent loop.
     */
    suspend fun performTapAwait(x: Float, y: Float, durationMs: Long = 90L): Boolean =
        suspendCancellableCoroutine { cont ->
            val dispatched = performTap(x, y, durationMs) { success ->
                if (cont.isActive) cont.resume(success)
            }
            if (!dispatched && cont.isActive) {
                cont.resume(false)
            }
        }

    /**
     * Dispatches a linear swipe gesture from (startX, startY) to (endX, endY).
     * Blocked when Train Mode ("listen" mode) is active.
     */
    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 350L,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (_isTrainModeActive.value) {
            Log.i(TAG, "performSwipe skipped: Train Mode (listen-only) is active.")
            onComplete?.invoke(false)
            return false
        }

        val path = Path().apply {
            moveTo(startX.coerceAtLeast(1f), startY.coerceAtLeast(1f))
            lineTo(endX.coerceAtLeast(1f), endY.coerceAtLeast(1f))
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(50L))
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "performSwipe($startX,$startY -> $endX,$endY) completed")
                    onComplete?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "performSwipe($startX,$startY -> $endX,$endY) cancelled")
                    onComplete?.invoke(false)
                }
            },
            null
        )
    }

    /**
     * Suspend wrapper around performSwipe(startX, startY, endX, endY).
     */
    suspend fun performSwipeAwait(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 350L
    ): Boolean = suspendCancellableCoroutine { cont ->
        val dispatched = performSwipe(startX, startY, endX, endY, durationMs) { success ->
            if (cont.isActive) cont.resume(success)
        }
        if (!dispatched && cont.isActive) {
            cont.resume(false)
        }
    }

    /**
     * Enters text into the currently focused or first editable node on screen using
     * AccessibilityNodeInfo.ACTION_SET_TEXT.
     */
    fun performTextInput(text: String): Boolean {
        if (_isTrainModeActive.value) {
            Log.i(TAG, "performTextInput skipped: Train Mode (listen-only) is active.")
            return false
        }

        val rootNode = rootInActiveWindow ?: return false
        val targetNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFirstEditableNode(rootNode)

        if (targetNode == null) {
            Log.w(TAG, "performTextInput failed: no focused or editable node found.")
            return false
        }

        if (!targetNode.isFocused) {
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }

        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        val applied = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        Log.d(TAG, "performTextInput('$text') result=$applied")
        return applied
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isVisibleToUser) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findFirstEditableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    /**
     * Dumps the current screen's View Node hierarchy (text, content descriptions, view IDs,
     * interactive flags, and screen coordinates/center points) into a structured string.
     */
    fun dumpViewNodeHierarchy(): String {
        val root = rootInActiveWindow
        if (root == null) {
            return "[Accessibility rootInActiveWindow is null - no active window tree available]"
        }

        val sb = StringBuilder()
        val activePkg = root.packageName?.toString() ?: "unknown"
        sb.appendLine("=== SCREEN UI NODE HIERARCHY (package: $activePkg) ===")
        var nodeCounter = 0

        fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || !node.isVisibleToUser) return

            val text = node.text?.toString()?.trim()?.replace("\n", " ").orEmpty()
            val desc = node.contentDescription?.toString()?.trim()?.replace("\n", " ").orEmpty()
            val viewId = node.viewIdResourceName?.substringAfterLast("/") ?: ""
            val className = node.className?.toString()?.substringAfterLast(".") ?: "View"

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val centerX = bounds.centerX()
            val centerY = bounds.centerY()

            val isInteractive = node.isClickable || node.isEditable || node.isScrollable || node.isCheckable
            val hasMeaningfulContent = text.isNotEmpty() || desc.isNotEmpty() || isInteractive || viewId.isNotEmpty()

            if (hasMeaningfulContent && bounds.width() > 0 && bounds.height() > 0) {
                nodeCounter++
                val indent = "  ".repeat(depth.coerceAtMost(10))
                val flags = buildList {
                    if (node.isClickable) add("CLICKABLE")
                    if (node.isEditable) add("EDITABLE")
                    if (node.isScrollable) add("SCROLLABLE")
                    if (node.isFocused) add("FOCUSED")
                    if (node.isChecked) add("CHECKED")
                }.joinToString(",")

                sb.append(indent)
                sb.append("[$nodeCounter] <$className>")
                if (viewId.isNotEmpty()) sb.append(" id=\"$viewId\"")
                if (text.isNotEmpty()) sb.append(" text=\"${text.take(120)}\"")
                if (desc.isNotEmpty()) sb.append(" desc=\"${desc.take(120)}\"")
                if (flags.isNotEmpty()) sb.append(" [$flags]")
                sb.appendLine(" bounds=[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}] center=($centerX,$centerY)")
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i), depth + 1)
            }
        }

        traverse(root, 0)
        sb.appendLine("=== TOTAL MEANINGFUL NODES: $nodeCounter ===")
        val dump = sb.toString()
        _latestHierarchyDump.value = dump
        _latestNodeCount.value = nodeCounter
        return dump
    }

    fun refreshLatestNodeSnapshot(): String {
        return dumpViewNodeHierarchy()
    }

    companion object {
        private const val TAG = "AgentAccessibilitySvc"
        private val recordScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        var instance: AgentAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        // --- Train Mode ("Listen" Mode) State ---
        private val _isTrainModeActive = MutableStateFlow(false)
        val isTrainModeActive: StateFlow<Boolean> = _isTrainModeActive.asStateFlow()

        private val _activeTrainingWorkflowName = MutableStateFlow("Custom App Workflow")
        val activeTrainingWorkflowName: StateFlow<String> = _activeTrainingWorkflowName.asStateFlow()

        private val _trainModeRecordedCount = MutableStateFlow(0)
        val trainModeRecordedCount: StateFlow<Int> = _trainModeRecordedCount.asStateFlow()

        private val _lastRecordedNodeSummary = MutableStateFlow(
            "Train Mode standby — toggle ON to intercept TYPE_VIEW_CLICKED events across 3rd-party apps."
        )
        val lastRecordedNodeSummary: StateFlow<String> = _lastRecordedNodeSummary.asStateFlow()

        private val _activePackageName = MutableStateFlow("com.aistudio.autopilotai.vkrxqm")
        val activePackageName: StateFlow<String> = _activePackageName.asStateFlow()

        private val _lastEventSummary = MutableStateFlow("Waiting for Accessibility events…")
        val lastEventSummary: StateFlow<String> = _lastEventSummary.asStateFlow()

        private val _latestHierarchyDump = MutableStateFlow(
            """
            === SCREEN UI NODE HIERARCHY (Preview / Standby Snapshot) ===
            [1] <FrameLayout> id="root_container" bounds=[0,0][1080,2400] center=(540,1200)
              [2] <TextView> text="AutoPilot AI Command Center" bounds=[48,140][780,210] center=(414,175)
              [3] <Switch> id="train_mode_switch" text="Train Mode (Listen Only)" [CLICKABLE] bounds=[48,300][1032,390] center=(540,345)
              [4] <Button> id="accessibility_btn" text="Accessibility Service" [CLICKABLE] bounds=[48,420][520,540] center=(284,480)
              [5] <Button> id="mediaprojection_btn" text="MediaProjection Capture" [CLICKABLE] bounds=[560,420][1032,540] center=(796,480)
              [6] <EditText> id="command_input" text="Open Gmail and send a status report to my boss" [CLICKABLE,EDITABLE,FOCUSED] bounds=[48,1860][840,2010] center=(444,1935)
              [7] <Button> id="run_agent_btn" desc="Execute Vision-Action Loop" [CLICKABLE] bounds=[860,1860][1032,2010] center=(946,1935)
            === TOTAL MEANINGFUL NODES: 7 ===
            """.trimIndent()
        )
        val latestHierarchyDump: StateFlow<String> = _latestHierarchyDump.asStateFlow()

        private val _latestNodeCount = MutableStateFlow(7)
        val latestNodeCount: StateFlow<Int> = _latestNodeCount.asStateFlow()

        fun setTrainMode(enabled: Boolean, workflowName: String? = null) {
            if (!workflowName.isNullOrBlank()) {
                _activeTrainingWorkflowName.value = workflowName.trim()
            }
            _isTrainModeActive.value = enabled
            if (enabled) {
                // Stop autonomous tapping immediately when switching to Listen Mode
                if (GeminiAgentLoop.isRunning.value) {
                    GeminiAgentLoop.stopLoop("Paused autonomous loop: Train Mode (Listen Mode) activated.")
                }
                _lastRecordedNodeSummary.value =
                    "LISTENING: Tap elements in any 3rd-party app to record into \"${_activeTrainingWorkflowName.value}\"."
            } else {
                _lastRecordedNodeSummary.value =
                    "Train Mode paused. Autonomous tapping re-enabled."
            }
        }

        fun updateTrainingWorkflowName(name: String) {
            _activeTrainingWorkflowName.value = name
        }

        /**
         * Allows recording a TYPE_VIEW_CLICKED node directly (used both by real Accessibility
         * events and by the in-app 3rd-party tap simulator for emulator testing).
         */
        fun recordClickedNodeToDatabase(
            context: Context,
            packageName: String,
            workflowName: String,
            viewIdResourceName: String,
            className: String,
            nodeText: String,
            contentDescription: String,
            centerX: Int,
            centerY: Int,
            boundsString: String = "[${centerX - 100},${centerY - 40}][${centerX + 100},${centerY + 40}]"
        ) {
            recordScope.launch {
                val saved = AgentMemoryRepository.getInstance(context).recordClickedNode(
                    packageName = packageName,
                    workflowName = workflowName,
                    viewIdResourceName = viewIdResourceName,
                    className = className,
                    nodeText = nodeText,
                    contentDescription = contentDescription,
                    centerX = centerX,
                    centerY = centerY,
                    boundsString = boundsString
                )
                _trainModeRecordedCount.value += 1
                _lastRecordedNodeSummary.value =
                    "Recorded #${saved.stepOrder} in ${saved.appLabel}: ${saved.viewIdResourceName.substringAfterLast('/')} " +
                        "\"${saved.nodeText.ifBlank { saved.contentDescription }}\""
            }
        }

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            if (instance != null) return true
            val expectedComponentName =
                "${context.packageName}/${AgentAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return enabledServices.contains(expectedComponentName, ignoreCase = true)
        }

        fun dumpOrFallbackHierarchy(): String {
            val svc = instance
            return if (svc != null) {
                svc.dumpViewNodeHierarchy()
            } else {
                _latestHierarchyDump.value
            }
        }
    }
}
