package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.agent.AgentStepRecord
import com.example.agent.AgentUiState
import com.example.agent.ChatEntry
import com.example.service.AgentAccessibilityService

private val PRESET_COMMANDS = listOf(
    "Open Gmail and send a status report to my boss",
    "Open YouTube and play phonk music",
    "Open Settings and turn on Wi-Fi",
    "Scroll down and tap the Search bar"
)

@Composable
fun AutoPilotScreen(
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionState by viewModel.permissionState.collectAsStateWithLifecycle()
    val apiKeyInput by viewModel.apiKeyInput.collectAsStateWithLifecycle()
    val savedApiKeyMasked by viewModel.savedApiKeyMasked.collectAsStateWithLifecycle()
    val useCustomKeyMode by viewModel.useCustomKeyMode.collectAsStateWithLifecycle()
    val apiKeySaveFeedback by viewModel.apiKeySaveFeedback.collectAsStateWithLifecycle()
    val commandInput by viewModel.commandInput.collectAsStateWithLifecycle()
    val taskObjectiveInput by viewModel.taskObjectiveInput.collectAsStateWithLifecycle()
    val maxSteps by viewModel.maxSteps.collectAsStateWithLifecycle()

    val memoryTree by viewModel.memoryTree.collectAsStateWithLifecycle()
    val queuedTasks by viewModel.queuedTasks.collectAsStateWithLifecycle()
    val isTrainModeActive by viewModel.isTrainModeActive.collectAsStateWithLifecycle()
    val trainingWorkflowName by viewModel.trainingWorkflowName.collectAsStateWithLifecycle()
    val trainModeRecordedCount by viewModel.trainModeRecordedCount.collectAsStateWithLifecycle()
    val lastRecordedNodeSummary by viewModel.lastRecordedNodeSummary.collectAsStateWithLifecycle()

    val isTaskDaemonRunning by viewModel.isTaskDaemonRunning.collectAsStateWithLifecycle()
    val daemonStatusSummary by viewModel.daemonStatusSummary.collectAsStateWithLifecycle()

    val agentUiState by viewModel.agentUiState.collectAsStateWithLifecycle()
    val isLoopRunning by viewModel.isLoopRunning.collectAsStateWithLifecycle()
    val chatEntries by viewModel.chatEntries.collectAsStateWithLifecycle()
    val latestHierarchyDump by viewModel.latestHierarchyDump.collectAsStateWithLifecycle()
    val latestNodeCount by viewModel.latestNodeCount.collectAsStateWithLifecycle()
    val lastEventSummary by viewModel.lastEventSummary.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showQuickApiKeySheet by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.onMediaProjectionResult(result.resultCode, result.data)
        } else {
            viewModel.refreshSystemPermissions()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    val requestMediaProjectionAction = {
        if (permissionState.isMediaProjectionActive) {
            viewModel.stopMediaProjection()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
        }
    }

    val openAccessibilitySettingsAction = {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    val toggleOrRequestOverlayAction = {
        if (!Settings.canDrawOverlays(context)) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        } else {
            viewModel.toggleFloatingOverlay()
        }
    }

    if (selectedTab != 0) {
        BackHandler {
            selectedTab = 0
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.tab_console)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_console)) },
                    modifier = Modifier.testTag("nav_tab_console")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = stringResource(R.string.tab_memory_tree)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_memory_tree)) },
                    modifier = Modifier.testTag("nav_tab_memory_tree")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PlaylistAddCheck,
                            contentDescription = stringResource(R.string.tab_task_queue)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_task_queue)) },
                    modifier = Modifier.testTag("nav_tab_task_queue")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        viewModel.refreshUiHierarchySnapshot()
                        selectedTab = 3
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = stringResource(R.string.tab_inspector)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_inspector)) },
                    modifier = Modifier.testTag("nav_tab_inspector")
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.tab_setup)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_setup)) },
                    modifier = Modifier.testTag("nav_tab_setup")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (selectedTab) {
                0 -> AgentCommandConsoleTab(
                    permissionState = permissionState,
                    isTrainModeActive = isTrainModeActive,
                    trainModeRecordedCount = trainModeRecordedCount,
                    onToggleTrainMode = viewModel::setTrainMode,
                    onOpenMemoryTree = { selectedTab = 1 },
                    onOpenTaskQueue = { selectedTab = 2 },
                    apiKeyInput = apiKeyInput,
                    savedApiKeyMasked = savedApiKeyMasked,
                    useCustomKeyMode = useCustomKeyMode,
                    apiKeySaveFeedback = apiKeySaveFeedback,
                    showQuickApiKeySheet = showQuickApiKeySheet,
                    onToggleQuickApiKeySheet = { showQuickApiKeySheet = !showQuickApiKeySheet },
                    onApiKeyInputChanged = viewModel::onApiKeyInputChanged,
                    onSaveApiKey = viewModel::saveApiKeyToSharedPreferences,
                    onOpenAccessibilitySettings = openAccessibilitySettingsAction,
                    onRequestMediaProjection = requestMediaProjectionAction,
                    onToggleOverlay = { toggleOrRequestOverlayAction() },
                    commandInput = commandInput,
                    onCommandInputChanged = viewModel::onCommandInputChanged,
                    onExecuteCommand = { viewModel.executeCommand(it) },
                    onStopLoop = viewModel::stopAgentLoop,
                    onClearConsole = viewModel::clearConsole,
                    agentUiState = agentUiState,
                    isLoopRunning = isLoopRunning,
                    chatEntries = chatEntries
                )
                1 -> MemoryTreeScreen(
                    memoryTree = memoryTree,
                    isTrainModeActive = isTrainModeActive,
                    trainingWorkflowName = trainingWorkflowName,
                    trainModeRecordedCount = trainModeRecordedCount,
                    lastRecordedNodeSummary = lastRecordedNodeSummary,
                    onToggleTrainMode = viewModel::setTrainMode,
                    onWorkflowNameChanged = viewModel::updateTrainingWorkflowName,
                    onSimulateThirdPartyTap = viewModel::simulateThirdPartyClick,
                    onReplayWorkflow = { workflow ->
                        viewModel.executeLearnedWorkflow(workflow)
                        selectedTab = 0
                    },
                    onDeleteWorkflow = viewModel::deleteWorkflow,
                    onDeletePackage = viewModel::deletePackageMemory,
                    onResetSampleMemory = viewModel::restoreSampleMemory
                )
                2 -> TaskQueueScreen(
                    queuedTasks = queuedTasks,
                    taskObjectiveInput = taskObjectiveInput,
                    isDaemonRunning = isTaskDaemonRunning,
                    daemonStatusSummary = daemonStatusSummary,
                    onObjectiveInputChanged = viewModel::onTaskObjectiveInputChanged,
                    onEnqueueTask = viewModel::enqueueObjective,
                    onQuickEnqueuePreset = viewModel::quickEnqueuePresetObjective,
                    onStartDaemon = viewModel::startBackgroundTaskDaemon,
                    onStopDaemon = viewModel::stopBackgroundTaskDaemon,
                    onRetryTask = viewModel::retryOrRunQueuedTask,
                    onDeleteTask = viewModel::deleteQueuedTask,
                    onClearCompletedTasks = viewModel::clearCompletedTasks
                )
                3 -> UiHierarchyInspectorTab(
                    isAccessibilityEnabled = permissionState.isAccessibilityEnabled,
                    latestHierarchyDump = latestHierarchyDump,
                    latestNodeCount = latestNodeCount,
                    lastEventSummary = lastEventSummary,
                    onRefreshSnapshot = viewModel::refreshUiHierarchySnapshot,
                    onOpenAccessibilitySettings = openAccessibilitySettingsAction
                )
                4 -> SetupAndKeysTab(
                    permissionState = permissionState,
                    apiKeyInput = apiKeyInput,
                    savedApiKeyMasked = savedApiKeyMasked,
                    useCustomKeyMode = useCustomKeyMode,
                    apiKeySaveFeedback = apiKeySaveFeedback,
                    maxSteps = maxSteps,
                    onApiKeyInputChanged = viewModel::onApiKeyInputChanged,
                    onSaveApiKey = viewModel::saveApiKeyToSharedPreferences,
                    onClearApiKey = viewModel::clearSavedApiKey,
                    onToggleCustomKeyMode = viewModel::setUseCustomKeyMode,
                    onMaxStepsChanged = viewModel::setMaxSteps,
                    onOpenAccessibilitySettings = openAccessibilitySettingsAction,
                    onRequestMediaProjection = requestMediaProjectionAction,
                    onToggleOverlay = { toggleOrRequestOverlayAction() },
                    onRefreshPermissions = viewModel::refreshSystemPermissions
                )
            }
        }
    }
}

@Composable
private fun AgentCommandConsoleTab(
    permissionState: SystemPermissionState,
    isTrainModeActive: Boolean,
    trainModeRecordedCount: Int,
    onToggleTrainMode: (Boolean) -> Unit,
    onOpenMemoryTree: () -> Unit,
    onOpenTaskQueue: () -> Unit,
    apiKeyInput: String,
    savedApiKeyMasked: String,
    useCustomKeyMode: Boolean,
    apiKeySaveFeedback: String?,
    showQuickApiKeySheet: Boolean,
    onToggleQuickApiKeySheet: () -> Unit,
    onApiKeyInputChanged: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestMediaProjection: () -> Unit,
    onToggleOverlay: () -> Unit,
    commandInput: String,
    onCommandInputChanged: (String) -> Unit,
    onExecuteCommand: (String?) -> Unit,
    onStopLoop: () -> Unit,
    onClearConsole: () -> Unit,
    agentUiState: AgentUiState,
    isLoopRunning: Boolean,
    chatEntries: List<ChatEntry>
) {
    val listState = rememberLazyListState()

    LaunchedEffect(chatEntries.size) {
        if (chatEntries.isNotEmpty()) {
            listState.animateScrollToItem(chatEntries.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
            .imePadding()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hero_header") {
                HeroBannerCard(
                    isLoopRunning = isLoopRunning,
                    isTrainModeActive = isTrainModeActive,
                    showQuickApiKeySheet = showQuickApiKeySheet,
                    onToggleQuickApiKeySheet = onToggleQuickApiKeySheet,
                    onClearConsole = onClearConsole
                )
            }

            // Quick Train Mode Toggle Bar + Memory Tree / Task Mode shortcuts
            item(key = "train_and_mode_strip") {
                ConsoleTrainAndModeCard(
                    isTrainModeActive = isTrainModeActive,
                    trainModeRecordedCount = trainModeRecordedCount,
                    isTaskDaemonRunning = permissionState.isTaskDaemonRunning,
                    onToggleTrainMode = onToggleTrainMode,
                    onOpenMemoryTree = onOpenMemoryTree,
                    onOpenTaskQueue = onOpenTaskQueue
                )
            }

            item(key = "system_permissions_row") {
                QuickSystemPermissionsCard(
                    permissionState = permissionState,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onRequestMediaProjection = onRequestMediaProjection,
                    onToggleOverlay = onToggleOverlay
                )
            }

            item(key = "quick_api_key_section") {
                AnimatedVisibility(
                    visible = showQuickApiKeySheet,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    CompactApiKeyCard(
                        apiKeyInput = apiKeyInput,
                        savedApiKeyMasked = savedApiKeyMasked,
                        useCustomKeyMode = useCustomKeyMode,
                        apiKeySaveFeedback = apiKeySaveFeedback,
                        onApiKeyInputChanged = onApiKeyInputChanged,
                        onSaveApiKey = onSaveApiKey
                    )
                }
            }

            item(key = "preset_commands_row") {
                PresetCommandsStrip(
                    enabled = !isLoopRunning && !isTrainModeActive,
                    onSelectPreset = { preset -> onExecuteCommand(preset) }
                )
            }

            if (agentUiState is AgentUiState.Running) {
                item(key = "live_loop_banner") {
                    LiveLoopProgressBanner(
                        state = agentUiState,
                        onStopLoop = onStopLoop
                    )
                }
            }

            items(
                items = chatEntries,
                key = { it.id }
            ) { entry ->
                ChatEntryItemCard(entry = entry)
            }
        }

        // Bottom Command Input Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = onCommandInputChanged,
                    placeholder = {
                        Text(
                            text = if (isTrainModeActive) {
                                "Train Mode active (Listen Only) — toggle off to run autonomous loop"
                            } else {
                                stringResource(R.string.hint_command_input)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isLoopRunning && !isTrainModeActive && commandInput.isNotBlank()) {
                                onExecuteCommand(null)
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input_field"),
                    shape = RoundedCornerShape(14.dp)
                )

                if (isLoopRunning) {
                    Button(
                        onClick = onStopLoop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("stop_agent_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = stringResource(R.string.btn_stop_agent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_stop_agent))
                    }
                } else {
                    Button(
                        onClick = { onExecuteCommand(null) },
                        enabled = commandInput.isNotBlank() && !isTrainModeActive,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("run_agent_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.btn_execute_command)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_execute_command))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleTrainAndModeCard(
    isTrainModeActive: Boolean,
    trainModeRecordedCount: Int,
    isTaskDaemonRunning: Boolean,
    onToggleTrainMode: (Boolean) -> Unit,
    onOpenMemoryTree: () -> Unit,
    onOpenTaskQueue: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTrainModeActive) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isTrainModeActive) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isTrainModeActive) {
                            Icons.Default.RadioButtonChecked
                        } else {
                            Icons.Default.FiberManualRecord
                        },
                        contentDescription = null,
                        tint = if (isTrainModeActive) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = if (isTrainModeActive) {
                                "Train Mode ON (Listening to Taps)"
                            } else {
                                "Train Mode (UI Recording)"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isTrainModeActive) {
                                "Autonomous tapping paused • $trainModeRecordedCount TYPE_VIEW_CLICKED nodes saved to Room"
                            } else {
                                "Switch to listen mode to record 3rd-party app clicks into Room DB"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isTrainModeActive,
                    onCheckedChange = onToggleTrainMode,
                    modifier = Modifier.testTag("console_train_mode_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenMemoryTree,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("open_memory_tree_button")
                ) {
                    Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Memory Tree", style = MaterialTheme.typography.labelMedium)
                }

                FilledTonalButton(
                    onClick = onOpenTaskQueue,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("open_task_queue_button")
                ) {
                    Icon(Icons.Default.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTaskDaemonRunning) "Task Queue (Active)" else "Task Mode Queue",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBannerCard(
    isLoopRunning: Boolean,
    isTrainModeActive: Boolean,
    showQuickApiKeySheet: Boolean,
    onToggleQuickApiKeySheet: () -> Unit,
    onClearConsole: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(142.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_agent_hero_1794667966176),
                contentDescription = "AutoPilot AI Vision-Action Architecture Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x660A0E17),
                                Color(0xE60A0E17)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = when {
                            isTrainModeActive -> MaterialTheme.colorScheme.secondaryContainer
                            isLoopRunning -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isTrainModeActive -> MaterialTheme.colorScheme.secondary
                                            isLoopRunning -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                            )
                            Text(
                                text = when {
                                    isTrainModeActive -> "TRAIN MODE: LISTENING"
                                    isLoopRunning -> "VISION LOOP ACTIVE"
                                    else -> "AUTONOMOUS AGENT READY"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isTrainModeActive -> MaterialTheme.colorScheme.onSecondaryContainer
                                    isLoopRunning -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onToggleQuickApiKeySheet,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("toggle_api_key_card_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Toggle Gemini API Key Card",
                                tint = if (showQuickApiKeySheet) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        IconButton(
                            onClick = onClearConsole,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("clear_console_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Agent Console",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = "AutoPilot AI",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Train Mode • Room Memory Tree • Autonomous Task Daemon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSystemPermissionsCard(
    permissionState: SystemPermissionState,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestMediaProjection: () -> Unit,
    onToggleOverlay: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.section_permissions_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PermissionControlButton(
                    title = "Accessibility",
                    statusLabel = if (permissionState.isAccessibilityEnabled) "Connected" else "Enable",
                    isActive = permissionState.isAccessibilityEnabled,
                    icon = Icons.Default.AccessibilityNew,
                    onClick = onOpenAccessibilitySettings,
                    testTag = "accessibility_permission_button",
                    modifier = Modifier.weight(1f)
                )

                PermissionControlButton(
                    title = "Screen Capture",
                    statusLabel = if (permissionState.isMediaProjectionActive) "Capturing" else "Grant",
                    isActive = permissionState.isMediaProjectionActive,
                    icon = Icons.Default.ScreenshotMonitor,
                    onClick = onRequestMediaProjection,
                    testTag = "media_projection_permission_button",
                    modifier = Modifier.weight(1f)
                )

                PermissionControlButton(
                    title = "Overlay UI",
                    statusLabel = when {
                        permissionState.isFloatingOverlayRunning -> "Active"
                        permissionState.canDrawOverlays -> "Launch"
                        else -> "Permit"
                    },
                    isActive = permissionState.isFloatingOverlayRunning,
                    icon = Icons.Default.Layers,
                    onClick = onToggleOverlay,
                    testTag = "floating_overlay_button",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PermissionControlButton(
    title: String,
    statusLabel: String,
    isActive: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isActive) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CompactApiKeyCard(
    apiKeyInput: String,
    savedApiKeyMasked: String,
    useCustomKeyMode: Boolean,
    apiKeySaveFeedback: String?,
    onApiKeyInputChanged: (String) -> Unit,
    onSaveApiKey: () -> Unit
) {
    var showRawKey by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Gemini API Key",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Gemini API Key (SharedPreferences)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = if (useCustomKeyMode) "Mode: SharedPreferences Key" else "Mode: Firebase AI Logic",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = onApiKeyInputChanged,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.hint_api_key),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (showRawKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showRawKey = !showRawKey }) {
                            Icon(
                                imageVector = if (showRawKey) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = "Toggle API Key Visibility"
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("api_key_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                FilledTonalButton(
                    onClick = onSaveApiKey,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("save_api_key_button")
                ) {
                    Text(stringResource(R.string.btn_save_api_key))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Saved Key: $savedApiKeyMasked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!apiKeySaveFeedback.isNullOrBlank()) {
                    Text(
                        text = apiKeySaveFeedback,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetCommandsStrip(
    enabled: Boolean,
    onSelectPreset: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Quick High-Level Commands:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PRESET_COMMANDS.forEachIndexed { index, preset ->
                AssistChip(
                    onClick = { if (enabled) onSelectPreset(preset) },
                    enabled = enabled,
                    label = {
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.testTag("preset_command_chip_$index")
                )
            }
        }
    }
}

@Composable
private fun LiveLoopProgressBanner(
    state: AgentUiState.Running,
    onStopLoop: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Gemini Vision-Action Loop Executing",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = state.phaseDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            OutlinedButton(
                onClick = onStopLoop,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Stop")
            }
        }
    }
}

@Composable
private fun ChatEntryItemCard(entry: ChatEntry) {
    val containerColor = when {
        entry.isError -> MaterialTheme.colorScheme.errorContainer
        entry.isUser -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        entry.isError -> MaterialTheme.colorScheme.error
        entry.isUser -> MaterialTheme.colorScheme.secondary
        entry.stepRecord != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (entry.isUser) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            entry.stepRecord?.let { step ->
                StepTelemetryDetailBox(step = step)
            }
        }
    }
}

@Composable
private fun StepTelemetryDetailBox(step: AgentStepRecord) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Frame: ${step.screenWidth}x${step.screenHeight}px • UI Nodes: ${step.nodeCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (step.executionSuccess) "DISPATCHED" else "SKIPPED",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (step.executionSuccess) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = step.actionCommand.rawJson,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Text(
                text = step.executionResult,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UiHierarchyInspectorTab(
    isAccessibilityEnabled: Boolean,
    latestHierarchyDump: String,
    latestNodeCount: Int,
    lastEventSummary: String,
    onRefreshSnapshot: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    var gestureFeedback by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AgentAccessibilityService Hierarchy",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Nodes indexed: $latestNodeCount • Last event: $lastEventSummary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onRefreshSnapshot,
                        modifier = Modifier.testTag("refresh_hierarchy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh UI Node Hierarchy",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val svc = AgentAccessibilityService.instance
                            val ok = svc?.performTap(540f, 1200f) ?: false
                            gestureFeedback = if (ok) {
                                "performTap(540f, 1200f) dispatched to AccessibilityService."
                            } else {
                                "performTap(540f, 1200f) invoked (note: blocked if Train Mode is ON)."
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("test_tap_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tap", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            val svc = AgentAccessibilityService.instance
                            val ok = svc?.performSwipe(540f, 1600f, 540f, 600f) ?: false
                            gestureFeedback = if (ok) {
                                "performSwipe(540,1600 -> 540,600) dispatched."
                            } else {
                                "performSwipe(540,1600 -> 540,600) invoked."
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("test_swipe_button")
                    ) {
                        Text("Swipe", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            val svc = AgentAccessibilityService.instance
                            val ok = svc?.performTextInput("phonk music") ?: false
                            gestureFeedback = if (ok) {
                                "performTextInput(\"phonk music\") applied to focused node."
                            } else {
                                "performTextInput(\"phonk music\") invoked."
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("test_text_input_button")
                    ) {
                        Text("Text", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (!gestureFeedback.isNullOrBlank()) {
                    Text(
                        text = gestureFeedback!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                if (!isAccessibilityEnabled) {
                    FilledTonalButton(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AccessibilityNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Android Accessibility Settings")
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = latestHierarchyDump,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SetupAndKeysTab(
    permissionState: SystemPermissionState,
    apiKeyInput: String,
    savedApiKeyMasked: String,
    useCustomKeyMode: Boolean,
    apiKeySaveFeedback: String?,
    maxSteps: Int,
    onApiKeyInputChanged: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onClearApiKey: () -> Unit,
    onToggleCustomKeyMode: (Boolean) -> Unit,
    onMaxStepsChanged: (Int) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestMediaProjection: () -> Unit,
    onToggleOverlay: () -> Unit,
    onRefreshPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.section_api_key_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Save your personal Gemini API key to SharedPreferences for standalone compiled APK usage on a physical device, or use the pre-provisioned Firebase AI Logic backend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = onApiKeyInputChanged,
                    label = { Text("Gemini API Key (SharedPreferences)") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_api_key_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onSaveApiKey,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("setup_save_api_key_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save to SharedPreferences")
                    }

                    OutlinedButton(
                        onClick = onClearApiKey,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("setup_clear_api_key_button")
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_clear_api_key))
                    }
                }

                if (!apiKeySaveFeedback.isNullOrBlank()) {
                    Text(
                        text = apiKeySaveFeedback,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use Custom SharedPreferences Key",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Current saved key: $savedApiKeyMasked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useCustomKeyMode,
                        onCheckedChange = onToggleCustomKeyMode,
                        modifier = Modifier.testTag("custom_key_mode_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Maximum Loop Iterations per Command",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "$maxSteps steps",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = maxSteps.toFloat(),
                        onValueChange = { onMaxStepsChanged(it.toInt()) },
                        valueRange = 1f..15f,
                        steps = 13
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Android System API Services",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onRefreshPermissions) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Permission Statuses"
                        )
                    }
                }

                ServiceSetupRow(
                    title = "1. AgentAccessibilityService",
                    description = "Settings.ACTION_ACCESSIBILITY_SETTINGS • Intercepts TYPE_VIEW_CLICKED in Train Mode & dispatches gestures.",
                    isReady = permissionState.isAccessibilityEnabled,
                    buttonLabel = if (permissionState.isAccessibilityEnabled) "Settings" else "Grant Accessibility",
                    onClick = onOpenAccessibilitySettings
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ServiceSetupRow(
                    title = "2. ScreenCaptureService (MediaProjection)",
                    description = "MediaProjectionManager • Foreground service (mediaProjection) capturing screenshots as Bitmaps.",
                    isReady = permissionState.isMediaProjectionActive,
                    buttonLabel = if (permissionState.isMediaProjectionActive) "Stop Capture" else "Start Capture",
                    onClick = onRequestMediaProjection
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ServiceSetupRow(
                    title = "3. FloatingOverlayService (System Alert Window)",
                    description = "SYSTEM_ALERT_WINDOW • Draggable floating bubble over other apps with an expandable transparent command field.",
                    isReady = permissionState.isFloatingOverlayRunning,
                    buttonLabel = when {
                        permissionState.isFloatingOverlayRunning -> "Stop Overlay"
                        permissionState.canDrawOverlays -> "Launch Overlay"
                        else -> "Grant Overlay"
                    },
                    onClick = onToggleOverlay
                )
            }
        }
    }
}

@Composable
private fun ServiceSetupRow(
    title: String,
    description: String,
    isReady: Boolean,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FilledTonalButton(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Text(buttonLabel)
        }
    }
}
