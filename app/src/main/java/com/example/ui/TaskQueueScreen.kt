package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.QueuedTaskEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SAMPLE_COMPLEX_OBJECTIVES = listOf(
    "Open Gmail and send a status report to my boss",
    "Open YouTube and play phonk music",
    "Open Settings, enable Wi-Fi, and verify network status",
    "Open WhatsApp and message the engineering group"
)

/**
 * 3. Task Mode (Autonomous Background Execution):
 * - Controls the persistent Foreground Service (AutonomousTaskService) with a permanent sticky
 *   notification so the agent can run in the background even when MainActivity is closed.
 * - Provides the Compose TaskQueue where the user can queue complex multi-step objectives
 *   stored in the local Room Database.
 */
@Composable
fun TaskQueueScreen(
    queuedTasks: List<QueuedTaskEntity>,
    taskObjectiveInput: String,
    isDaemonRunning: Boolean,
    daemonStatusSummary: String,
    onObjectiveInputChanged: (String) -> Unit,
    onEnqueueTask: (startDaemonImmediately: Boolean) -> Unit,
    onQuickEnqueuePreset: (String) -> Unit,
    onStartDaemon: () -> Unit,
    onStopDaemon: () -> Unit,
    onRetryTask: (QueuedTaskEntity) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onClearCompletedTasks: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 720.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Persistent Foreground Service Daemon Control Card
        item(key = "daemon_service_card") {
            BackgroundDaemonStatusCard(
                isDaemonRunning = isDaemonRunning,
                daemonStatusSummary = daemonStatusSummary,
                onStartDaemon = onStartDaemon,
                onStopDaemon = onStopDaemon
            )
        }

        // 2. TaskQueue Composer Card
        item(key = "task_queue_composer") {
            TaskQueueInputCard(
                taskObjectiveInput = taskObjectiveInput,
                onObjectiveInputChanged = onObjectiveInputChanged,
                onEnqueueTask = onEnqueueTask,
                onQuickEnqueuePreset = onQuickEnqueuePreset
            )
        }

        // 3. TaskQueue Header
        item(key = "task_queue_list_header") {
            val pendingCount = queuedTasks.count { it.status == QueuedTaskEntity.STATUS_PENDING }
            val runningCount = queuedTasks.count { it.status == QueuedTaskEntity.STATUS_RUNNING }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TaskQueue (${queuedTasks.size} total)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$pendingCount pending • $runningCount active in background",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (queuedTasks.any { it.status != QueuedTaskEntity.STATUS_PENDING && it.status != QueuedTaskEntity.STATUS_RUNNING }) {
                    OutlinedButton(
                        onClick = onClearCompletedTasks,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_finished_tasks_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Finished", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // 4. Queued Tasks List
        items(
            items = queuedTasks,
            key = { it.id }
        ) { task ->
            QueuedTaskItemCard(
                task = task,
                onRetryTask = { onRetryTask(task) },
                onDeleteTask = { onDeleteTask(task.id) }
            )
        }
    }
}

@Composable
private fun BackgroundDaemonStatusCard(
    isDaemonRunning: Boolean,
    daemonStatusSummary: String,
    onStartDaemon: () -> Unit,
    onStopDaemon: () -> Unit
) {
    val borderColor = if (isDaemonRunning) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDaemonRunning) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isDaemonRunning) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (isDaemonRunning) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }

                    Column {
                        Text(
                            text = if (isDaemonRunning) {
                                "Background Task Service ACTIVE"
                            } else {
                                "Persistent Foreground Task Service"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Sticky Notification • Runs MediaProjection + Gemini Loop when app is closed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = daemonStatusSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDaemonRunning) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(10.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartDaemon,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("start_task_daemon_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isDaemonRunning) "Wake / Process Queue" else "Start Background Daemon")
                }

                if (isDaemonRunning) {
                    OutlinedButton(
                        onClick = onStopDaemon,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("stop_task_daemon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop")
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskQueueInputCard(
    taskObjectiveInput: String,
    onObjectiveInputChanged: (String) -> Unit,
    onEnqueueTask: (startDaemonImmediately: Boolean) -> Unit,
    onQuickEnqueuePreset: (String) -> Unit
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
                text = "Add Autonomous Objective to TaskQueue",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = taskObjectiveInput,
                onValueChange = onObjectiveInputChanged,
                placeholder = {
                    Text(
                        text = "e.g., Open Gmail and send a status report to my boss",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (taskObjectiveInput.isNotBlank()) {
                            onEnqueueTask(true)
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_queue_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SAMPLE_COMPLEX_OBJECTIVES.forEachIndexed { idx, preset ->
                    AssistChip(
                        onClick = { onQuickEnqueuePreset(preset) },
                        label = {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AddTask,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("task_preset_chip_$idx")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { onEnqueueTask(false) },
                    enabled = taskObjectiveInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("enqueue_task_button")
                ) {
                    Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Queue Only")
                }

                Button(
                    onClick = { onEnqueueTask(true) },
                    enabled = taskObjectiveInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("enqueue_and_run_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Queue & Run")
                }
            }
        }
    }
}

@Composable
private fun QueuedTaskItemCard(
    task: QueuedTaskEntity,
    onRetryTask: () -> Unit,
    onDeleteTask: () -> Unit
) {
    val statusColor = when (task.status) {
        QueuedTaskEntity.STATUS_RUNNING -> MaterialTheme.colorScheme.primary
        QueuedTaskEntity.STATUS_COMPLETED -> MaterialTheme.colorScheme.tertiary
        QueuedTaskEntity.STATUS_FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = task.status,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }

                    if (task.targetPackageHint.isNotBlank()) {
                        Text(
                            text = task.targetPackageHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(task.updatedAtMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = task.objective,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Steps executed: ${task.stepsCompleted} • ${task.progressMessage}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (task.lastActionJson.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = task.lastActionJson,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.status != QueuedTaskEntity.STATUS_RUNNING) {
                    FilledTonalButton(
                        onClick = onRetryTask,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (task.status == QueuedTaskEntity.STATUS_PENDING) {
                                Icons.Default.PlayArrow
                            } else {
                                Icons.Default.Replay
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (task.status == QueuedTaskEntity.STATUS_PENDING) "Run Now" else "Re-queue",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(
                    onClick = onDeleteTask,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Queued Task",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
