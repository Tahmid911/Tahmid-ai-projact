package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.AgentPreferences
import com.example.agent.AgentUiState
import com.example.agent.ChatEntry
import com.example.agent.GeminiAgentLoop
import com.example.data.AgentMemoryRepository
import com.example.data.AppMemoryTreeNode
import com.example.data.LearnedWorkflowGroup
import com.example.data.QueuedTaskEntity
import com.example.service.AgentAccessibilityService
import com.example.service.AutonomousTaskService
import com.example.service.FloatingOverlayService
import com.example.service.ScreenCaptureService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SystemPermissionState(
    val isAccessibilityEnabled: Boolean = false,
    val isMediaProjectionActive: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val isFloatingOverlayRunning: Boolean = false,
    val isTaskDaemonRunning: Boolean = false
)

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context
        get() = getApplication<Application>().applicationContext

    private val repository: AgentMemoryRepository = AgentMemoryRepository.getInstance(appContext)

    private val _permissionState = MutableStateFlow(SystemPermissionState())
    val permissionState: StateFlow<SystemPermissionState> = _permissionState.asStateFlow()

    private val _apiKeyInput = MutableStateFlow(AgentPreferences.getApiKey(appContext))
    val apiKeyInput: StateFlow<String> = _apiKeyInput.asStateFlow()

    private val _savedApiKeyMasked = MutableStateFlow(maskKey(AgentPreferences.getApiKey(appContext)))
    val savedApiKeyMasked: StateFlow<String> = _savedApiKeyMasked.asStateFlow()

    private val _useCustomKeyMode = MutableStateFlow(AgentPreferences.isUsingCustomKey(appContext))
    val useCustomKeyMode: StateFlow<Boolean> = _useCustomKeyMode.asStateFlow()

    private val _apiKeySaveFeedback = MutableStateFlow<String?>(null)
    val apiKeySaveFeedback: StateFlow<String?> = _apiKeySaveFeedback.asStateFlow()

    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    private val _taskObjectiveInput = MutableStateFlow("")
    val taskObjectiveInput: StateFlow<String> = _taskObjectiveInput.asStateFlow()

    private val _maxSteps = MutableStateFlow(AgentPreferences.getMaxSteps(appContext))
    val maxSteps: StateFlow<Int> = _maxSteps.asStateFlow()

    // Reactive Room Database Flows for MemoryTreeScreen & TaskQueueScreen
    val memoryTree: StateFlow<List<AppMemoryTreeNode>> = repository.memoryTreeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val queuedTasks: StateFlow<List<QueuedTaskEntity>> = repository.queuedTasksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Train Mode Flows from AgentAccessibilityService
    val isTrainModeActive: StateFlow<Boolean> = AgentAccessibilityService.isTrainModeActive
    val trainingWorkflowName: StateFlow<String> = AgentAccessibilityService.activeTrainingWorkflowName
    val trainModeRecordedCount: StateFlow<Int> = AgentAccessibilityService.trainModeRecordedCount
    val lastRecordedNodeSummary: StateFlow<String> = AgentAccessibilityService.lastRecordedNodeSummary

    // AutonomousTaskService Background Daemon Flows
    val isTaskDaemonRunning: StateFlow<Boolean> = AutonomousTaskService.isDaemonRunning
    val daemonStatusSummary: StateFlow<String> = AutonomousTaskService.currentTaskSummary

    // Vision-Action Loop Flows
    val agentUiState: StateFlow<AgentUiState> = GeminiAgentLoop.uiState
    val isLoopRunning: StateFlow<Boolean> = GeminiAgentLoop.isRunning
    val chatEntries: StateFlow<List<ChatEntry>> = GeminiAgentLoop.chatEntries
    val latestHierarchyDump: StateFlow<String> = AgentAccessibilityService.latestHierarchyDump
    val latestNodeCount: StateFlow<Int> = AgentAccessibilityService.latestNodeCount
    val lastEventSummary: StateFlow<String> = AgentAccessibilityService.lastEventSummary

    init {
        refreshSystemPermissions()
        viewModelScope.launch {
            repository.seedInitialMemoryIfEmpty()
        }
    }

    fun refreshSystemPermissions() {
        val a11y = AgentAccessibilityService.isAccessibilityServiceEnabled(appContext)
        val projection = ScreenCaptureService.isProjectionActive.value
        val overlayPerm = Settings.canDrawOverlays(appContext)
        val overlayRunning = FloatingOverlayService.isOverlayRunning.value
        val daemonRunning = AutonomousTaskService.isDaemonRunning.value

        _permissionState.update {
            SystemPermissionState(
                isAccessibilityEnabled = a11y,
                isMediaProjectionActive = projection,
                canDrawOverlays = overlayPerm,
                isFloatingOverlayRunning = overlayRunning,
                isTaskDaemonRunning = daemonRunning
            )
        }
    }

    // --- 1. Train Mode (UI Recording) Actions ---

    fun setTrainMode(enabled: Boolean) {
        AgentAccessibilityService.setTrainMode(enabled)
    }

    fun updateTrainingWorkflowName(name: String) {
        AgentAccessibilityService.updateTrainingWorkflowName(name)
    }

    fun simulateThirdPartyClick(
        packageName: String,
        workflowName: String,
        viewId: String,
        className: String,
        text: String,
        contentDesc: String,
        x: Int,
        y: Int
    ) {
        if (!AgentAccessibilityService.isTrainModeActive.value) {
            AgentAccessibilityService.setTrainMode(true, workflowName)
        }
        AgentAccessibilityService.recordClickedNodeToDatabase(
            context = appContext,
            packageName = packageName,
            workflowName = workflowName,
            viewIdResourceName = viewId,
            className = className,
            nodeText = text,
            contentDescription = contentDesc,
            centerX = x,
            centerY = y
        )
    }

    // --- 2. MemoryTreeScreen Actions ---

    fun executeLearnedWorkflow(workflow: LearnedWorkflowGroup) {
        if (AgentAccessibilityService.isTrainModeActive.value) {
            AgentAccessibilityService.setTrainMode(false)
        }
        val cmd = "Execute learned workflow '${workflow.workflowName}' in ${workflow.packageName}"
        GeminiAgentLoop.startCommandLoop(appContext, cmd)
    }

    fun deleteWorkflow(packageName: String, workflowName: String) {
        viewModelScope.launch {
            repository.deleteWorkflow(packageName, workflowName)
        }
    }

    fun deletePackageMemory(packageName: String) {
        viewModelScope.launch {
            repository.deletePackageMemory(packageName)
        }
    }

    fun restoreSampleMemory() {
        viewModelScope.launch {
            repository.clearAllMemory()
            repository.seedInitialMemoryIfEmpty()
        }
    }

    // --- 3. Task Mode (Autonomous Background Execution) Actions ---

    fun onTaskObjectiveInputChanged(value: String) {
        _taskObjectiveInput.value = value
    }

    fun enqueueObjective(startDaemonImmediately: Boolean) {
        val objective = _taskObjectiveInput.value.trim()
        if (objective.isEmpty()) return
        _taskObjectiveInput.value = ""

        viewModelScope.launch {
            repository.enqueueTask(objective)
            if (startDaemonImmediately) {
                if (AgentAccessibilityService.isTrainModeActive.value) {
                    AgentAccessibilityService.setTrainMode(false)
                }
                AutonomousTaskService.wakeDaemonForNewTask(appContext)
                refreshSystemPermissions()
            }
        }
    }

    fun quickEnqueuePresetObjective(objective: String) {
        viewModelScope.launch {
            repository.enqueueTask(objective)
            if (AgentAccessibilityService.isTrainModeActive.value) {
                AgentAccessibilityService.setTrainMode(false)
            }
            AutonomousTaskService.wakeDaemonForNewTask(appContext)
            refreshSystemPermissions()
        }
    }

    fun startBackgroundTaskDaemon() {
        if (AgentAccessibilityService.isTrainModeActive.value) {
            AgentAccessibilityService.setTrainMode(false)
        }
        AutonomousTaskService.startDaemon(appContext)
        refreshSystemPermissions()
    }

    fun stopBackgroundTaskDaemon() {
        AutonomousTaskService.stopDaemon(appContext)
        refreshSystemPermissions()
    }

    fun retryOrRunQueuedTask(task: QueuedTaskEntity) {
        viewModelScope.launch {
            repository.updateTask(
                task.copy(
                    status = QueuedTaskEntity.STATUS_PENDING,
                    stepsCompleted = 0,
                    progressMessage = "Re-queued — starting background execution"
                )
            )
            if (AgentAccessibilityService.isTrainModeActive.value) {
                AgentAccessibilityService.setTrainMode(false)
            }
            AutonomousTaskService.wakeDaemonForNewTask(appContext)
            refreshSystemPermissions()
        }
    }

    fun deleteQueuedTask(taskId: Long) {
        viewModelScope.launch {
            repository.deleteTaskById(taskId)
        }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            repository.clearFinishedTasks()
        }
    }

    // --- API Key & Interactive Console Actions ---

    fun onApiKeyInputChanged(newValue: String) {
        _apiKeyInput.value = newValue
        _apiKeySaveFeedback.value = null
    }

    fun saveApiKeyToSharedPreferences() {
        val trimmed = _apiKeyInput.value.trim()
        AgentPreferences.saveApiKey(appContext, trimmed)
        _savedApiKeyMasked.value = maskKey(trimmed)
        if (trimmed.isNotEmpty()) {
            _apiKeySaveFeedback.value = "Gemini API Key saved to SharedPreferences (autopilot_agent_prefs)."
        } else {
            _useCustomKeyMode.value = false
            AgentPreferences.setUseCustomKey(appContext, false)
            _apiKeySaveFeedback.value = "Empty key saved — using built-in Firebase AI Logic."
        }
    }

    fun clearSavedApiKey() {
        AgentPreferences.clearApiKey(appContext)
        _apiKeyInput.value = ""
        _savedApiKeyMasked.value = "Not Set"
        _useCustomKeyMode.value = false
        _apiKeySaveFeedback.value = "Cleared saved API key from SharedPreferences."
    }

    fun setUseCustomKeyMode(enabled: Boolean) {
        if (enabled && AgentPreferences.getApiKey(appContext).isBlank()) {
            _apiKeySaveFeedback.value = "Save a non-empty Gemini API key first to enable Custom Key mode."
            return
        }
        AgentPreferences.setUseCustomKey(appContext, enabled)
        _useCustomKeyMode.value = enabled
        _apiKeySaveFeedback.value = if (enabled) {
            "Engine switched to SharedPreferences Gemini API Key."
        } else {
            "Engine switched to Firebase AI Logic (gemini-3.8-flash)."
        }
    }

    fun setMaxSteps(steps: Int) {
        val coerced = steps.coerceIn(1, 20)
        AgentPreferences.setMaxSteps(appContext, coerced)
        _maxSteps.value = coerced
    }

    fun onCommandInputChanged(newCommand: String) {
        _commandInput.value = newCommand
    }

    fun executeCommand(customCommand: String? = null) {
        val targetCommand = (customCommand ?: _commandInput.value).trim()
        if (targetCommand.isEmpty()) return
        if (customCommand == null) {
            _commandInput.value = ""
        }
        GeminiAgentLoop.startCommandLoop(appContext, targetCommand)
    }

    fun stopAgentLoop() {
        GeminiAgentLoop.stopLoop()
    }

    fun clearConsole() {
        GeminiAgentLoop.clearLogs()
    }

    fun refreshUiHierarchySnapshot() {
        AgentAccessibilityService.instance?.refreshLatestNodeSnapshot()
        refreshSystemPermissions()
    }

    fun onMediaProjectionResult(resultCode: Int, data: Intent?) {
        if (data != null) {
            ScreenCaptureService.startServiceWithProjection(appContext, resultCode, data)
        }
        refreshSystemPermissions()
    }

    fun stopMediaProjection() {
        ScreenCaptureService.stopProjectionService(appContext)
        refreshSystemPermissions()
    }

    fun toggleFloatingOverlay(): Boolean {
        val canDraw = Settings.canDrawOverlays(appContext)
        if (!canDraw) {
            refreshSystemPermissions()
            return false
        }
        if (FloatingOverlayService.isOverlayRunning.value) {
            FloatingOverlayService.stopOverlay(appContext)
        } else {
            FloatingOverlayService.startOverlay(appContext)
        }
        refreshSystemPermissions()
        return true
    }

    private fun maskKey(raw: String): String {
        val clean = raw.trim()
        if (clean.isEmpty()) return "Not Set"
        if (clean.length <= 8) return "••••••••"
        return "${clean.take(4)}••••${clean.takeLast(4)}"
    }
}
