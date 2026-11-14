package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single clicked UI node intercepted by AgentAccessibilityService
 * during Train Mode (TYPE_VIEW_CLICKED) and persisted as part of a Learned Workflow.
 */
@Entity(tableName = "learned_workflow_nodes")
data class LearnedNodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val appLabel: String,
    val workflowName: String,
    val stepOrder: Int,
    val viewIdResourceName: String,
    val className: String,
    val nodeText: String,
    val contentDescription: String,
    val centerX: Int,
    val centerY: Int,
    val boundsString: String,
    val recordedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Represents an autonomous objective in the persistent TaskQueue executed by
 * AutonomousTaskService in the background.
 */
@Entity(tableName = "queued_tasks")
data class QueuedTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val objective: String,
    val targetPackageHint: String = "",
    val status: String = STATUS_PENDING,
    val progressMessage: String = "Queued — waiting for AutonomousTaskService",
    val stepsCompleted: Int = 0,
    val lastActionJson: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}

/**
 * Hierarchical models used by MemoryTreeScreen to render the collapsible
 * App Package -> Learned Workflow -> Clicked UI Node Sequence tree.
 */
data class LearnedWorkflowGroup(
    val packageName: String,
    val workflowName: String,
    val nodes: List<LearnedNodeEntity>
)

data class AppMemoryTreeNode(
    val packageName: String,
    val appLabel: String,
    val workflows: List<LearnedWorkflowGroup>,
    val totalNodes: Int
)
