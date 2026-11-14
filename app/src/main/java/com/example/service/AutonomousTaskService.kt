package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.agent.GeminiAgentLoop
import com.example.data.AgentMemoryRepository
import com.example.data.QueuedTaskEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 3. Task Mode (Autonomous Background Execution):
 * Persistent Foreground Service with a permanent sticky notification that runs in the background
 * even when MainActivity is closed. Continuously processes pending tasks in the Room TaskQueue
 * using MediaProjection screen capture, Accessibility UI hierarchy, learned Room workflows, and
 * the Gemini Vision-Action Loop.
 */
class AutonomousTaskService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var workerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        _isDaemonRunning.value = true
        createNotificationChannel()
        startStickyForeground(getString(R.string.task_service_notification_idle))
        startTaskQueueWorkerLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_DAEMON -> {
                _isDaemonRunning.value = false
                _currentTaskSummary.value = "Background Daemon stopped."
                workerJob?.cancel()
                GeminiAgentLoop.stopLoop("AutonomousTaskService stopped by user.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_WAKE_QUEUE -> {
                if (workerJob?.isActive != true) {
                    startTaskQueueWorkerLoop()
                }
            }
        }
        return START_STICKY
    }

    private fun startTaskQueueWorkerLoop() {
        workerJob?.cancel()
        val repository = AgentMemoryRepository.getInstance(applicationContext)

        workerJob = serviceScope.launch {
            while (isActive && _isDaemonRunning.value) {
                try {
                    // Pause queue processing if Train Mode (listen-only) is enabled
                    if (AgentAccessibilityService.isTrainModeActive.value) {
                        val msg = "Standby: Train Mode (Listen Only) is active."
                        _currentTaskSummary.value = msg
                        updateStickyNotification(msg)
                        delay(2000L)
                        continue
                    }

                    val nextTask = repository.getNextPendingTask()
                    if (nextTask == null) {
                        val idleMsg = getString(R.string.task_service_notification_idle)
                        _currentTaskSummary.value = "Daemon Active — waiting for next queued task"
                        updateStickyNotification(idleMsg)
                        delay(2000L)
                        continue
                    }

                    // Mark task as RUNNING
                    val runningTask = nextTask.copy(
                        status = QueuedTaskEntity.STATUS_RUNNING,
                        progressMessage = "Starting autonomous background loop…"
                    )
                    repository.updateTask(runningTask)
                    _currentTaskSummary.value = "Running Task #${nextTask.id}: ${nextTask.objective}"
                    updateStickyNotification("Running: ${nextTask.objective}")

                    // Execute the task via the Gemini Vision-Action Loop
                    val outcome = GeminiAgentLoop.executeBackgroundQueuedTask(
                        context = applicationContext,
                        task = runningTask
                    ) { stepNum, actionJson, stepStatus ->
                        val stepUpdate = runningTask.copy(
                            status = QueuedTaskEntity.STATUS_RUNNING,
                            stepsCompleted = stepNum,
                            lastActionJson = actionJson,
                            progressMessage = stepStatus
                        )
                        repository.updateTask(stepUpdate)
                        val notifyText = "Step $stepNum • $stepStatus"
                        _currentTaskSummary.value = "Task #${nextTask.id}: $notifyText"
                        updateStickyNotification("${nextTask.objective} ($notifyText)")
                    }

                    val finalTask = repository.getTaskById(nextTask.id) ?: runningTask
                    if (outcome.first) {
                        repository.updateTask(
                            finalTask.copy(
                                status = QueuedTaskEntity.STATUS_COMPLETED,
                                progressMessage = outcome.second
                            )
                        )
                        _currentTaskSummary.value = "Completed Task #${nextTask.id}: ${outcome.second}"
                        updateStickyNotification("Completed: ${nextTask.objective}")
                    } else {
                        repository.updateTask(
                            finalTask.copy(
                                status = QueuedTaskEntity.STATUS_FAILED,
                                progressMessage = outcome.second
                            )
                        )
                        _currentTaskSummary.value = "Task #${nextTask.id} halted: ${outcome.second}"
                        updateStickyNotification("Halted: ${outcome.second}")
                    }

                    delay(1200L)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "AutonomousTaskService worker iteration warning: ${e.message}")
                    delay(2500L)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.task_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for AutoPilot AI background task execution"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildStickyNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, AutonomousTaskService::class.java).apply {
            action = ACTION_STOP_DAEMON
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.task_service_notification_title))
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Daemon",
                stopPendingIntent
            )
            .build()
    }

    private fun startStickyForeground(statusText: String) {
        val notification = buildStickyNotification(statusText)
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

    private fun updateStickyNotification(statusText: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, buildStickyNotification(statusText))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update notification: ${e.message}")
        }
    }

    override fun onDestroy() {
        _isDaemonRunning.value = false
        workerJob?.cancel()
        serviceScope.cancel()
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AutonomousTaskSvc"
        private const val CHANNEL_ID = "autopilot_autonomous_task_channel"
        private const val NOTIFICATION_ID = 4103

        const val ACTION_START_DAEMON = "com.example.service.action.START_TASK_DAEMON"
        const val ACTION_STOP_DAEMON = "com.example.service.action.STOP_TASK_DAEMON"
        const val ACTION_WAKE_QUEUE = "com.example.service.action.WAKE_TASK_QUEUE"

        @Volatile
        var instance: AutonomousTaskService? = null
            private set

        private val _isDaemonRunning = MutableStateFlow(false)
        val isDaemonRunning: StateFlow<Boolean> = _isDaemonRunning.asStateFlow()

        private val _currentTaskSummary = MutableStateFlow(
            "Background Task Daemon standby — start daemon to execute TaskQueue autonomously."
        )
        val currentTaskSummary: StateFlow<String> = _currentTaskSummary.asStateFlow()

        fun startDaemon(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, AutonomousTaskService::class.java).apply {
                action = ACTION_START_DAEMON
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appCtx.startForegroundService(intent)
            } else {
                appCtx.startService(intent)
            }
        }

        fun wakeDaemonForNewTask(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, AutonomousTaskService::class.java).apply {
                action = ACTION_WAKE_QUEUE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appCtx.startForegroundService(intent)
            } else {
                appCtx.startService(intent)
            }
        }

        fun stopDaemon(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, AutonomousTaskService::class.java).apply {
                action = ACTION_STOP_DAEMON
            }
            appCtx.startService(intent)
        }
    }
}
