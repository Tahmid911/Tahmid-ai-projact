package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentMemoryDao {

    // --- Learned Workflows & UI Nodes (Train Mode & MemoryTreeScreen) ---

    @Query("SELECT * FROM learned_workflow_nodes ORDER BY packageName ASC, workflowName ASC, stepOrder ASC, id ASC")
    fun observeAllLearnedNodes(): Flow<List<LearnedNodeEntity>>

    @Query("SELECT * FROM learned_workflow_nodes ORDER BY packageName ASC, workflowName ASC, stepOrder ASC, id ASC")
    suspend fun getAllLearnedNodesSnapshot(): List<LearnedNodeEntity>

    @Query("SELECT COUNT(*) FROM learned_workflow_nodes")
    suspend fun getLearnedNodeCount(): Int

    @Query(
        "SELECT COALESCE(MAX(stepOrder), 0) + 1 FROM learned_workflow_nodes " +
            "WHERE packageName = :packageName AND workflowName = :workflowName"
    )
    suspend fun getNextStepOrder(packageName: String, workflowName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLearnedNode(node: LearnedNodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLearnedNodes(nodes: List<LearnedNodeEntity>)

    @Query("DELETE FROM learned_workflow_nodes WHERE packageName = :packageName AND workflowName = :workflowName")
    suspend fun deleteWorkflow(packageName: String, workflowName: String)

    @Query("DELETE FROM learned_workflow_nodes WHERE packageName = :packageName")
    suspend fun deletePackageMemory(packageName: String)

    @Query("DELETE FROM learned_workflow_nodes WHERE id = :nodeId")
    suspend fun deleteLearnedNodeById(nodeId: Long)

    @Query("DELETE FROM learned_workflow_nodes")
    suspend fun clearAllLearnedNodes()

    // --- Autonomous Background TaskQueue (Task Mode) ---

    @Query("SELECT * FROM queued_tasks ORDER BY createdAtMillis DESC")
    fun observeAllQueuedTasks(): Flow<List<QueuedTaskEntity>>

    @Query("SELECT * FROM queued_tasks WHERE status = 'PENDING' ORDER BY createdAtMillis ASC LIMIT 1")
    suspend fun getNextPendingTask(): QueuedTaskEntity?

    @Query("SELECT * FROM queued_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): QueuedTaskEntity?

    @Query("SELECT COUNT(*) FROM queued_tasks")
    suspend fun getQueuedTaskCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueuedTask(task: QueuedTaskEntity): Long

    @Update
    suspend fun updateQueuedTask(task: QueuedTaskEntity)

    @Query("DELETE FROM queued_tasks WHERE id = :taskId")
    suspend fun deleteQueuedTaskById(taskId: Long)

    @Query("DELETE FROM queued_tasks WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearFinishedTasks()
}
