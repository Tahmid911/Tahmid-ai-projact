package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository abstracting Room database access for:
 * 1. Train Mode (recording TYPE_VIEW_CLICKED sequences into Learned Workflows)
 * 2. MemoryTreeScreen (reactive hierarchical App Package -> Workflow -> UI Node tree)
 * 3. Task Mode (persistent TaskQueue for AutonomousTaskService)
 */
class AgentMemoryRepository(private val dao: AgentMemoryDao) {

    val allLearnedNodesFlow: Flow<List<LearnedNodeEntity>> = dao.observeAllLearnedNodes()

    val memoryTreeFlow: Flow<List<AppMemoryTreeNode>> = dao.observeAllLearnedNodes().map { nodes ->
        nodes
            .groupBy { it.packageName }
            .map { (pkg, pkgNodes) ->
                val appLabel = pkgNodes.firstOrNull()?.appLabel?.ifBlank {
                    deriveFriendlyAppName(pkg)
                } ?: deriveFriendlyAppName(pkg)

                val workflows = pkgNodes
                    .groupBy { it.workflowName }
                    .map { (wfName, wfNodes) ->
                        LearnedWorkflowGroup(
                            packageName = pkg,
                            workflowName = wfName,
                            nodes = wfNodes.sortedWith(compareBy({ it.stepOrder }, { it.id }))
                        )
                    }
                    .sortedBy { it.workflowName }

                AppMemoryTreeNode(
                    packageName = pkg,
                    appLabel = appLabel,
                    workflows = workflows,
                    totalNodes = pkgNodes.size
                )
            }
            .sortedBy { it.packageName }
    }

    val queuedTasksFlow: Flow<List<QueuedTaskEntity>> = dao.observeAllQueuedTasks()

    suspend fun recordClickedNode(
        packageName: String,
        workflowName: String,
        viewIdResourceName: String,
        className: String,
        nodeText: String,
        contentDescription: String,
        centerX: Int,
        centerY: Int,
        boundsString: String
    ): LearnedNodeEntity {
        val cleanPkg = packageName.ifBlank { "com.unknown.thirdparty" }
        val cleanWorkflow = workflowName.ifBlank { "Recorded Session" }
        val nextStep = dao.getNextStepOrder(cleanPkg, cleanWorkflow)

        val entity = LearnedNodeEntity(
            packageName = cleanPkg,
            appLabel = deriveFriendlyAppName(cleanPkg),
            workflowName = cleanWorkflow,
            stepOrder = nextStep,
            viewIdResourceName = viewIdResourceName.ifBlank { "unlabeled_view_id" },
            className = className.ifBlank { "android.view.View" },
            nodeText = nodeText,
            contentDescription = contentDescription,
            centerX = centerX,
            centerY = centerY,
            boundsString = boundsString
        )
        val newId = dao.insertLearnedNode(entity)
        return entity.copy(id = newId)
    }

    suspend fun deleteWorkflow(packageName: String, workflowName: String) {
        dao.deleteWorkflow(packageName, workflowName)
    }

    suspend fun deletePackageMemory(packageName: String) {
        dao.deletePackageMemory(packageName)
    }

    suspend fun deleteNodeById(nodeId: Long) {
        dao.deleteLearnedNodeById(nodeId)
    }

    suspend fun clearAllMemory() {
        dao.clearAllLearnedNodes()
    }

    suspend fun enqueueTask(objective: String, targetPackageHint: String = ""): QueuedTaskEntity {
        val inferredPkg = targetPackageHint.ifBlank { inferPackageFromObjective(objective) }
        val task = QueuedTaskEntity(
            objective = objective.trim(),
            targetPackageHint = inferredPkg,
            status = QueuedTaskEntity.STATUS_PENDING,
            progressMessage = "Queued — ready for background execution"
        )
        val id = dao.insertQueuedTask(task)
        return task.copy(id = id)
    }

    suspend fun getNextPendingTask(): QueuedTaskEntity? = dao.getNextPendingTask()

    suspend fun getTaskById(taskId: Long): QueuedTaskEntity? = dao.getTaskById(taskId)

    suspend fun updateTask(task: QueuedTaskEntity) {
        dao.updateQueuedTask(task.copy(updatedAtMillis = System.currentTimeMillis()))
    }

    suspend fun deleteTaskById(taskId: Long) {
        dao.deleteQueuedTaskById(taskId)
    }

    suspend fun clearFinishedTasks() {
        dao.clearFinishedTasks()
    }

    /**
     * Formats relevant learned workflows from the Room database into a structured memory section
     * for the Gemini Vision-Action prompt so the agent leverages previously trained UI paths.
     */
    suspend fun buildLearnedMemoryContextForPrompt(): String {
        val nodes = dao.getAllLearnedNodesSnapshot()
        if (nodes.isEmpty()) return "No previously trained workflows stored in Room memory."

        val sb = StringBuilder()
        nodes.groupBy { "${it.packageName} :: ${it.workflowName}" }
            .entries
            .take(6)
            .forEach { (header, wfNodes) ->
                sb.appendLine("• Workflow [$header]:")
                wfNodes.sortedBy { it.stepOrder }.forEach { n ->
                    sb.appendLine(
                        "   Step ${n.stepOrder}: <${n.className.substringAfterLast('.')}> " +
                            "id=\"${n.viewIdResourceName}\" text=\"${n.nodeText}\" " +
                            "desc=\"${n.contentDescription}\" center=(${n.centerX},${n.centerY})"
                    )
                }
            }
        return sb.toString().trim()
    }

    /**
     * Seeds initial realistic learned 3rd-party app workflows and sample queued tasks if the database
     * is empty on first launch so MemoryTreeScreen and TaskQueue immediately demonstrate the hierarchy.
     */
    suspend fun seedInitialMemoryIfEmpty() {
        if (dao.getLearnedNodeCount() == 0) {
            val now = System.currentTimeMillis()
            val starterNodes = listOf(
                // 1. Gmail: Send Status Report to Boss
                LearnedNodeEntity(
                    packageName = "com.google.android.gm",
                    appLabel = "Gmail",
                    workflowName = "Send Status Report to Boss",
                    stepOrder = 1,
                    viewIdResourceName = "com.google.android.gm:id/compose_button",
                    className = "android.widget.ExtendedFloatingActionButton",
                    nodeText = "Compose",
                    contentDescription = "Compose new email",
                    centerX = 884,
                    centerY = 2040,
                    boundsString = "[740,1968][1028,2112]",
                    recordedAtMillis = now - 360_000L
                ),
                LearnedNodeEntity(
                    packageName = "com.google.android.gm",
                    appLabel = "Gmail",
                    workflowName = "Send Status Report to Boss",
                    stepOrder = 2,
                    viewIdResourceName = "com.google.android.gm:id/to_field_input",
                    className = "android.widget.MultiAutoCompleteTextView",
                    nodeText = "boss@company.com",
                    contentDescription = "To recipient field",
                    centerX = 520,
                    centerY = 390,
                    boundsString = "[160,340][1020,440]",
                    recordedAtMillis = now - 350_000L
                ),
                LearnedNodeEntity(
                    packageName = "com.google.android.gm",
                    appLabel = "Gmail",
                    workflowName = "Send Status Report to Boss",
                    stepOrder = 3,
                    viewIdResourceName = "com.google.android.gm:id/subject",
                    className = "android.widget.EditText",
                    nodeText = "Weekly Engineering Status Report",
                    contentDescription = "Subject input",
                    centerX = 540,
                    centerY = 530,
                    boundsString = "[48,480][1032,580]",
                    recordedAtMillis = now - 340_000L
                ),
                LearnedNodeEntity(
                    packageName = "com.google.android.gm",
                    appLabel = "Gmail",
                    workflowName = "Send Status Report to Boss",
                    stepOrder = 4,
                    viewIdResourceName = "com.google.android.gm:id/send",
                    className = "android.widget.ActionMenuItemView",
                    nodeText = "",
                    contentDescription = "Send",
                    centerX = 980,
                    centerY = 176,
                    boundsString = "[920,120][1040,232]",
                    recordedAtMillis = now - 330_000L
                ),

                // 2. YouTube: Search & Play Phonk Music
                LearnedNodeEntity(
                    packageName = "com.google.android.youtube",
                    appLabel = "YouTube",
                    workflowName = "Search & Play Phonk Music",
                    stepOrder = 1,
                    viewIdResourceName = "com.google.android.youtube:id/menu_item_search",
                    className = "android.widget.ImageView",
                    nodeText = "",
                    contentDescription = "Search",
                    centerX = 910,
                    centerY = 168,
                    boundsString = "[856,114][964,222]",
                    recordedAtMillis = now - 240_000L
                ),
                LearnedNodeEntity(
                    packageName = "com.google.android.youtube",
                    appLabel = "YouTube",
                    workflowName = "Search & Play Phonk Music",
                    stepOrder = 2,
                    viewIdResourceName = "com.google.android.youtube:id/search_edit_text",
                    className = "android.widget.EditText",
                    nodeText = "phonk music mix",
                    contentDescription = "Search YouTube",
                    centerX = 480,
                    centerY = 168,
                    boundsString = "[140,118][820,218]",
                    recordedAtMillis = now - 230_000L
                ),
                LearnedNodeEntity(
                    packageName = "com.google.android.youtube",
                    appLabel = "YouTube",
                    workflowName = "Search & Play Phonk Music",
                    stepOrder = 3,
                    viewIdResourceName = "com.google.android.youtube:id/video_title_item_0",
                    className = "android.view.ViewGroup",
                    nodeText = "NIGHT DRIVE PHONK MIX 2026",
                    contentDescription = "Play video NIGHT DRIVE PHONK MIX",
                    centerX = 540,
                    centerY = 690,
                    boundsString = "[32,420][1048,960]",
                    recordedAtMillis = now - 220_000L
                ),

                // 3. Android Settings: Enable Wi-Fi & Check Network
                LearnedNodeEntity(
                    packageName = "com.android.settings",
                    appLabel = "System Settings",
                    workflowName = "Enable Wi-Fi & Network",
                    stepOrder = 1,
                    viewIdResourceName = "com.android.settings:id/dashboard_tile_network",
                    className = "android.widget.LinearLayout",
                    nodeText = "Network & internet",
                    contentDescription = "Internet, calls, SMS",
                    centerX = 540,
                    centerY = 480,
                    boundsString = "[48,390][1032,570]",
                    recordedAtMillis = now - 120_000L
                ),
                LearnedNodeEntity(
                    packageName = "com.android.settings",
                    appLabel = "System Settings",
                    workflowName = "Enable Wi-Fi & Network",
                    stepOrder = 2,
                    viewIdResourceName = "com.android.settings:id/switchWidget",
                    className = "android.widget.Switch",
                    nodeText = "ON",
                    contentDescription = "Use Wi-Fi",
                    centerX = 948,
                    centerY = 640,
                    boundsString = "[884,592][1012,688]",
                    recordedAtMillis = now - 110_000L
                )
            )
            dao.insertLearnedNodes(starterNodes)
        }

        if (dao.getQueuedTaskCount() == 0) {
            dao.insertQueuedTask(
                QueuedTaskEntity(
                    objective = "Open Gmail and send a status report to my boss",
                    targetPackageHint = "com.google.android.gm",
                    status = QueuedTaskEntity.STATUS_PENDING,
                    progressMessage = "Ready in TaskQueue — tap 'Start Background Daemon' or 'Run Next' to execute"
                )
            )
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AgentMemoryRepository? = null

        fun getInstance(context: Context): AgentMemoryRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AgentMemoryDatabase.getInstance(context)
                INSTANCE ?: AgentMemoryRepository(db.agentMemoryDao()).also { INSTANCE = it }
            }
        }

        fun deriveFriendlyAppName(packageName: String): String {
            val lower = packageName.lowercase()
            return when {
                "youtube" in lower -> "YouTube"
                "com.google.android.gm" in lower || "gmail" in lower -> "Gmail"
                "settings" in lower -> "System Settings"
                "chrome" in lower -> "Google Chrome"
                "whatsapp" in lower -> "WhatsApp"
                "spotify" in lower -> "Spotify"
                "slack" in lower -> "Slack"
                "maps" in lower -> "Google Maps"
                else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }
        }

        fun inferPackageFromObjective(objective: String): String {
            val lower = objective.lowercase()
            return when {
                "gmail" in lower || "email" in lower || "status report" in lower -> "com.google.android.gm"
                "youtube" in lower || "phonk" in lower || "video" in lower -> "com.google.android.youtube"
                "setting" in lower || "wi-fi" in lower || "wifi" in lower || "bluetooth" in lower -> "com.android.settings"
                "chrome" in lower || "browser" in lower || "search" in lower -> "com.android.chrome"
                "spotify" in lower || "playlist" in lower -> "com.spotify.music"
                else -> "com.android.systemui"
            }
        }
    }
}
