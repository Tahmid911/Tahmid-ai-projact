package com.example.agent

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.AgentMemoryRepository
import com.example.data.QueuedTaskEntity
import com.example.service.AgentAccessibilityService
import com.example.service.ScreenCaptureService
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "GeminiAgentLoop"

/**
 * Structured representation of Gemini's strict JSON response:
 * {"action": "tap|swipe|text|done", "x": 100, "y": 200, "text": "optional"}
 */
data class AgentActionCommand(
    val action: String,
    val x: Int = 0,
    val y: Int = 0,
    val endX: Int = 0,
    val endY: Int = 0,
    val text: String = "",
    val reasoning: String = "",
    val rawJson: String = ""
)

data class AgentStepRecord(
    val stepNumber: Int,
    val timestamp: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val nodeCount: Int,
    val actionCommand: AgentActionCommand,
    val executionResult: String,
    val executionSuccess: Boolean
)

data class ChatEntry(
    val id: Long = System.nanoTime(),
    val isUser: Boolean,
    val title: String,
    val body: String,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
    val stepRecord: AgentStepRecord? = null,
    val isError: Boolean = false
)

sealed interface AgentUiState {
    data object Idle : AgentUiState
    data class Running(val currentStep: Int, val maxSteps: Int, val phaseDescription: String) : AgentUiState
    data class Completed(val summary: String, val totalSteps: Int) : AgentUiState
    data class Error(val message: String) : AgentUiState
}

/**
 * The Gemini Vision-Action Loop (The Brain):
 * Supports both interactive foreground commands and background Task Mode execution via
 * AutonomousTaskService, enriched with Learned Workflows from the Room Database.
 */
object GeminiAgentLoop {

    private val loopScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeLoopJob: Job? = null

    private val agentActionSchema = Schema.obj(
        properties = mapOf(
            "action" to Schema.string(
                description = "Action type: must be one of 'tap', 'swipe', 'text', or 'done'"
            ),
            "x" to Schema.integer(
                description = "X coordinate in pixels for tap or swipe start (0 if not applicable)"
            ),
            "y" to Schema.integer(
                description = "Y coordinate in pixels for tap or swipe start (0 if not applicable)"
            ),
            "endX" to Schema.integer(
                description = "Destination X coordinate in pixels when action is 'swipe'"
            ),
            "endY" to Schema.integer(
                description = "Destination Y coordinate in pixels when action is 'swipe'"
            ),
            "text" to Schema.string(
                description = "Text to input when action is 'text', or summary note when 'done'"
            ),
            "reasoning" to Schema.string(
                description = "Short explanation of why this action and coordinate were chosen"
            )
        ),
        optionalProperties = listOf("endX", "endY", "text", "reasoning")
    )

    private val structuredConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = agentActionSchema
        temperature = 0.1f
    }

    private val fastVisionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = structuredConfig
        )
    }

    private val standardVisionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = structuredConfig
        )
    }

    private val _uiState = MutableStateFlow<AgentUiState>(AgentUiState.Idle)
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _statusSummary = MutableStateFlow("Idle — ready for high-level command")
    val statusSummary: StateFlow<String> = _statusSummary.asStateFlow()

    private val _chatEntries = MutableStateFlow<List<ChatEntry>>(
        listOf(
            ChatEntry(
                isUser = false,
                title = "AutoPilot AI Vision-Action Engine",
                body = "Ready. Use Train Mode to record 3rd-party app workflows into Room, inspect them in Memory Tree, or queue autonomous background objectives in Task Mode."
            )
        )
    )
    val chatEntries: StateFlow<List<ChatEntry>> = _chatEntries.asStateFlow()

    fun clearLogs() {
        _chatEntries.value = listOf(
            ChatEntry(
                isUser = false,
                title = "Console Cleared",
                body = "Vision-Action telemetry log reset. Ready for your next command."
            )
        )
        if (!_isRunning.value) {
            _uiState.value = AgentUiState.Idle
            _statusSummary.value = "Idle — ready for high-level command"
        }
    }

    fun stopLoop(reason: String = "Agent loop stopped by user.") {
        activeLoopJob?.cancel()
        activeLoopJob = null
        _isRunning.value = false
        _uiState.value = AgentUiState.Idle
        _statusSummary.value = reason
        appendEntry(
            ChatEntry(
                isUser = false,
                title = "Loop Halted",
                body = reason
            )
        )
    }

    fun startCommandLoop(context: Context, userCommand: String) {
        val trimmedCommand = userCommand.trim()
        if (trimmedCommand.isEmpty()) return

        if (AgentAccessibilityService.isTrainModeActive.value) {
            val msg = "Train Mode (Listen Only) is currently active. Toggle off Train Mode before starting autonomous tapping."
            _uiState.value = AgentUiState.Error(msg)
            appendEntry(
                ChatEntry(
                    isUser = false,
                    title = "Train Mode Active",
                    body = msg,
                    isError = true
                )
            )
            return
        }

        activeLoopJob?.cancel()
        val appContext = context.applicationContext

        appendEntry(
            ChatEntry(
                isUser = true,
                title = "User Command",
                body = trimmedCommand
            )
        )

        activeLoopJob = loopScope.launch {
            runVisionActionSteps(
                appContext = appContext,
                objective = trimmedCommand,
                maxSteps = AgentPreferences.getMaxSteps(appContext),
                onStepUpdate = { _, _, _ -> }
            )
        }
    }

    /**
     * Used by AutonomousTaskService to execute a queued background objective from Room TaskQueue
     * without requiring MainActivity to be in the foreground.
     */
    suspend fun executeBackgroundQueuedTask(
        context: Context,
        task: QueuedTaskEntity,
        onStepProgress: suspend (stepNum: Int, actionJson: String, statusText: String) -> Unit
    ): Pair<Boolean, String> {
        val appContext = context.applicationContext
        appendEntry(
            ChatEntry(
                isUser = true,
                title = "Background Task #${task.id}",
                body = task.objective
            )
        )
        return runVisionActionSteps(
            appContext = appContext,
            objective = task.objective,
            maxSteps = AgentPreferences.getMaxSteps(appContext).coerceAtMost(6),
            onStepUpdate = onStepProgress
        )
    }

    private suspend fun runVisionActionSteps(
        appContext: Context,
        objective: String,
        maxSteps: Int,
        onStepUpdate: suspend (stepNum: Int, actionJson: String, statusText: String) -> Unit
    ): Pair<Boolean, String> {
        _isRunning.value = true
        val previousActions = mutableListOf<String>()
        val repository = AgentMemoryRepository.getInstance(appContext)
        val learnedMemoryContext = repository.buildLearnedMemoryContextForPrompt()

        try {
            for (step in 1..maxSteps) {
                if (AgentAccessibilityService.isTrainModeActive.value) {
                    val haltMsg = "Paused: Train Mode (Listen Only) was enabled."
                    _uiState.value = AgentUiState.Idle
                    _statusSummary.value = haltMsg
                    return false to haltMsg
                }

                // Step (a): Capture the screen (Bitmap) via ScreenCaptureService
                _uiState.value = AgentUiState.Running(
                    currentStep = step,
                    maxSteps = maxSteps,
                    phaseDescription = "Step $step/$maxSteps: Capturing screen frame…"
                )
                _statusSummary.value = "Step $step: Capturing screen…"
                val screenshot: Bitmap = ScreenCaptureService.captureScreenForAgent()

                // Step (b): Extract the UI node tree via AgentAccessibilityService
                _uiState.value = AgentUiState.Running(
                    currentStep = step,
                    maxSteps = maxSteps,
                    phaseDescription = "Step $step/$maxSteps: Dumping Accessibility UI tree…"
                )
                _statusSummary.value = "Step $step: Reading UI node hierarchy…"
                val uiTreeDump: String = AgentAccessibilityService.dumpOrFallbackHierarchy()
                val nodeCount = AgentAccessibilityService.latestNodeCount.value

                // Step (c) & (d): Send Bitmap, UI tree, learned workflows, and command to Gemini
                _uiState.value = AgentUiState.Running(
                    currentStep = step,
                    maxSteps = maxSteps,
                    phaseDescription = "Step $step/$maxSteps: Querying Gemini Vision-Action model…"
                )
                _statusSummary.value = "Step $step: Thinking with Gemini Vision…"

                val promptText = buildVisionActionPrompt(
                    userCommand = objective,
                    step = step,
                    maxSteps = maxSteps,
                    screenWidth = screenshot.width,
                    screenHeight = screenshot.height,
                    uiTreeDump = uiTreeDump,
                    learnedMemoryContext = learnedMemoryContext,
                    previousActions = previousActions
                )

                val rawJsonResponse = withTimeout(60_000L) {
                    requestNextAgentActionJson(
                        appContext = appContext,
                        screenshot = screenshot,
                        promptText = promptText
                    )
                }

                // Step (e): Parse strict JSON directly with JSONObject and execute via AgentAccessibilityService
                val parsedAction = parseAgentActionJson(rawJsonResponse)
                val executionOutcome = executeActionOnDevice(parsedAction, screenshot.width, screenshot.height)

                previousActions.add(
                    "Step $step -> ${parsedAction.rawJson} (${executionOutcome.second})"
                )

                val stepRecord = AgentStepRecord(
                    stepNumber = step,
                    timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
                    screenWidth = screenshot.width,
                    screenHeight = screenshot.height,
                    nodeCount = nodeCount,
                    actionCommand = parsedAction,
                    executionResult = executionOutcome.second,
                    executionSuccess = executionOutcome.first
                )

                appendEntry(
                    ChatEntry(
                        isUser = false,
                        title = "Step $step • ${parsedAction.action.uppercase(Locale.US)}",
                        body = parsedAction.reasoning.ifBlank { executionOutcome.second },
                        stepRecord = stepRecord
                    )
                )

                onStepUpdate(
                    step,
                    parsedAction.rawJson,
                    parsedAction.reasoning.ifBlank { executionOutcome.second }
                )

                // Step (f): Check if Gemini returned "action": "done"
                if (parsedAction.action.equals("done", ignoreCase = true)) {
                    val doneSummary = parsedAction.text.ifBlank {
                        parsedAction.reasoning.ifBlank { "Goal completed at step $step." }
                    }
                    _uiState.value = AgentUiState.Completed(
                        summary = doneSummary,
                        totalSteps = step
                    )
                    _statusSummary.value = "Done: $doneSummary"
                    appendEntry(
                        ChatEntry(
                            isUser = false,
                            title = "Vision-Action Loop Complete",
                            body = doneSummary
                        )
                    )
                    return true to doneSummary
                }

                _uiState.value = AgentUiState.Running(
                    currentStep = step,
                    maxSteps = maxSteps,
                    phaseDescription = "Step $step/$maxSteps: Waiting for screen transition…"
                )
                delay(1100L)
            }

            val capMsg = "Completed $maxSteps autonomous steps for \"$objective\"."
            _uiState.value = AgentUiState.Completed(summary = capMsg, totalSteps = maxSteps)
            _statusSummary.value = capMsg
            return true to capMsg
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Gemini Vision-Action request timed out: ${e.message}")
            val msg = "Gemini Vision-Action request timed out after 60 seconds. Please try again."
            _uiState.value = AgentUiState.Error(msg)
            _statusSummary.value = "Error: Timed out"
            appendEntry(
                ChatEntry(
                    isUser = false,
                    title = "Timeout Notice",
                    body = msg,
                    isError = true
                )
            )
            return false to msg
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Vision-Action Loop exception: ${e.message}")
            val msg = e.localizedMessage ?: "Unexpected error during Vision-Action Loop."
            _uiState.value = AgentUiState.Error(msg)
            _statusSummary.value = "Error: $msg"
            appendEntry(
                ChatEntry(
                    isUser = false,
                    title = "Agent Loop Notice",
                    body = msg,
                    isError = true
                )
            )
            return false to msg
        } finally {
            _isRunning.value = false
        }
    }

    private suspend fun requestNextAgentActionJson(
        appContext: Context,
        screenshot: Bitmap,
        promptText: String
    ): String = withContext(Dispatchers.IO) {
        val compactBitmap = downscaleBitmapForVision(screenshot, maxDimension = 360)

        if (AgentPreferences.isUsingCustomKey(appContext)) {
            val customKey = AgentPreferences.getApiKey(appContext)
            return@withContext callGeminiWithCustomKey(
                apiKey = customKey,
                bitmap = compactBitmap,
                prompt = promptText
            )
        }

        try {
            withTimeout(22_000L) {
                val response = fastVisionModel.generateContent(
                    content {
                        image(compactBitmap)
                        text(promptText)
                    }
                )
                val text = response.text
                if (!text.isNullOrBlank()) {
                    return@withTimeout text
                }
                throw IllegalStateException("Empty response from primary vision model")
            }
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            Log.w(TAG, "Primary multimodal request exceeded 22s; falling back to gemini-3.8-flash")
            val fallbackResponse = standardVisionModel.generateContent(promptText)
            fallbackResponse.text ?: throw IllegalStateException("Gemini returned an empty response.")
        } catch (e: Exception) {
            Log.w(TAG, "Primary vision call failed (${e.message}); retrying with gemini-3.8-flash")
            val fallbackResponse = standardVisionModel.generateContent(
                content {
                    image(compactBitmap)
                    text(promptText)
                }
            )
            fallbackResponse.text ?: throw IllegalStateException("Gemini returned an empty response.")
        }
    }

    private fun downscaleBitmapForVision(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width.coerceAtLeast(1)
        val height = source.height.coerceAtLeast(1)
        val longestSide = maxOf(width, height)
        if (longestSide <= maxDimension) return source

        val ratio = maxDimension.toFloat() / longestSide.toFloat()
        val targetWidth = (width * ratio).toInt().coerceAtLeast(120)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(120)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun buildVisionActionPrompt(
        userCommand: String,
        step: Int,
        maxSteps: Int,
        screenWidth: Int,
        screenHeight: Int,
        uiTreeDump: String,
        learnedMemoryContext: String,
        previousActions: List<String>
    ): String {
        val historySection = if (previousActions.isEmpty()) {
            "None (this is step 1)."
        } else {
            previousActions.joinToString("\n")
        }

        return """
            You are an autonomous Android UI Agent controlling a device via AccessibilityService and MediaProjection.
            
            USER GOAL COMMAND:
            "$userCommand"
            
            CURRENT EXECUTION CONTEXT:
            - Current Step: $step of $maxSteps
            - Coordinate Space Dimensions: ${screenWidth}x${screenHeight} pixels
            - Previous Actions Taken in This Session:
            $historySection
            
            LEARNED WORKFLOWS FROM ROOM DATABASE (TRAIN MODE MEMORY):
            $learnedMemoryContext
            
            CURRENT ACCESSIBILITY UI NODE TREE:
            $uiTreeDump
            
            INSTRUCTIONS:
            1. Inspect the attached screenshot, the Learned Workflows from Room memory, and the Accessibility UI Node Tree.
            2. Choose the single best next action to accomplish the USER GOAL COMMAND:
               - "tap": Tap at screen coordinates (x, y). Prefer matching learned workflow coordinates or UI node center=(x,y).
               - "swipe": Swipe from (x, y) to (endX, endY) to scroll or navigate.
               - "text": Enter text into the focused or editable input field. Provide the string in "text".
               - "done": Return "done" when the user command has been satisfied or when previous actions already completed the workflow.
            3. Respond strictly in JSON matching:
               {"action": "tap|swipe|text|done", "x": 100, "y": 200, "endX": 100, "endY": 200, "text": "optional", "reasoning": "brief explanation"}
        """.trimIndent()
    }

    fun parseAgentActionJson(rawJson: String): AgentActionCommand {
        val json = JSONObject(rawJson.trim())
        val rawAction = json.optString("action", "done").lowercase(Locale.US).trim()
        val action = when (rawAction) {
            "tap", "swipe", "text", "done" -> rawAction
            else -> "done"
        }
        val x = json.optInt("x", 0)
        val y = json.optInt("y", 0)
        val endX = json.optInt("endX", x)
        val endY = json.optInt("endY", (y - 300).coerceAtLeast(100))
        val text = json.optString("text", "")
        val reasoning = json.optString("reasoning", "")

        val canonicalObj = JSONObject().apply {
            put("action", action)
            put("x", x)
            put("y", y)
            if (action == "swipe") {
                put("endX", endX)
                put("endY", endY)
            }
            put("text", text)
        }

        return AgentActionCommand(
            action = action,
            x = x,
            y = y,
            endX = endX,
            endY = endY,
            text = text,
            reasoning = reasoning,
            rawJson = canonicalObj.toString()
        )
    }

    private suspend fun executeActionOnDevice(
        cmd: AgentActionCommand,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Boolean, String> {
        val service = AgentAccessibilityService.instance
        return when (cmd.action) {
            "tap" -> {
                val targetX = cmd.x.toFloat().coerceIn(1f, screenWidth.toFloat().coerceAtLeast(1f))
                val targetY = cmd.y.toFloat().coerceIn(1f, screenHeight.toFloat().coerceAtLeast(1f))
                if (service != null) {
                    val ok = service.performTapAwait(targetX, targetY)
                    ok to if (ok) {
                        "Dispatched Accessibility tap at (${targetX.toInt()}, ${targetY.toInt()})"
                    } else {
                        "Accessibility tap at (${targetX.toInt()}, ${targetY.toInt()}) was cancelled"
                    }
                } else {
                    true to "Preview Mode: Validated tap at (${targetX.toInt()}, ${targetY.toInt()}) — enable AccessibilityService on device to inject hardware touch"
                }
            }
            "swipe" -> {
                val startX = cmd.x.toFloat().coerceIn(1f, screenWidth.toFloat().coerceAtLeast(1f))
                val startY = cmd.y.toFloat().coerceIn(1f, screenHeight.toFloat().coerceAtLeast(1f))
                val toX = cmd.endX.toFloat().coerceIn(1f, screenWidth.toFloat().coerceAtLeast(1f))
                val toY = cmd.endY.toFloat().coerceIn(1f, screenHeight.toFloat().coerceAtLeast(1f))
                if (service != null) {
                    val ok = service.performSwipeAwait(startX, startY, toX, toY)
                    ok to if (ok) {
                        "Dispatched Accessibility swipe (${startX.toInt()},${startY.toInt()}) -> (${toX.toInt()},${toY.toInt()})"
                    } else {
                        "Accessibility swipe cancelled"
                    }
                } else {
                    true to "Preview Mode: Validated swipe (${startX.toInt()},${startY.toInt()}) -> (${toX.toInt()},${toY.toInt()})"
                }
            }
            "text" -> {
                if (service != null) {
                    val ok = service.performTextInput(cmd.text)
                    ok to if (ok) {
                        "Entered text \"${cmd.text}\" via ACTION_SET_TEXT"
                    } else {
                        "No focused editable node found for text \"${cmd.text}\""
                    }
                } else {
                    true to "Preview Mode: Validated text input \"${cmd.text}\""
                }
            }
            "done" -> {
                true to (cmd.text.ifBlank { "Task completed." })
            }
            else -> {
                false to "Unrecognized action '${cmd.action}'"
            }
        }
    }

    private suspend fun callGeminiWithCustomKey(
        apiKey: String,
        bitmap: Bitmap,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val base64Jpeg = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }

        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put(
                            "parts",
                            JSONArray()
                                .put(
                                    JSONObject().apply {
                                        put(
                                            "inline_data",
                                            JSONObject().apply {
                                                put("mime_type", "image/jpeg")
                                                put("data", base64Jpeg)
                                            }
                                        )
                                    }
                                )
                                .put(JSONObject().apply { put("text", prompt) })
                        )
                    }
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                }
            )
        }

        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 35_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            val bodyStream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseBody = bodyStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException("Gemini API HTTP $code: $responseBody")
            }

            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val contentObj = firstCandidate?.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts")
            val textOut = parts?.optJSONObject(0)?.optString("text").orEmpty()
            if (textOut.isBlank()) {
                throw IllegalStateException("Custom key Gemini response contained no text.")
            }
            textOut
        } finally {
            conn.disconnect()
        }
    }

    private fun appendEntry(entry: ChatEntry) {
        _chatEntries.value = _chatEntries.value + entry
    }
}
