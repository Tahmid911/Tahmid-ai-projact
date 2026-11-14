package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppMemoryTreeNode
import com.example.data.LearnedNodeEntity
import com.example.data.LearnedWorkflowGroup

private data class SimulatedTapPreset(
    val label: String,
    val packageName: String,
    val workflowName: String,
    val viewId: String,
    val className: String,
    val text: String,
    val contentDesc: String,
    val x: Int,
    val y: Int
)

private val SIMULATED_3RD_PARTY_TAPS = listOf(
    SimulatedTapPreset(
        label = "+ Spotify Play Button",
        packageName = "com.spotify.music",
        workflowName = "Play Liked Songs Playlist",
        viewId = "com.spotify.music:id/play_pause_button",
        className = "android.widget.ImageButton",
        text = "",
        contentDesc = "Play Liked Songs",
        x = 920,
        y = 780
    ),
    SimulatedTapPreset(
        label = "+ WhatsApp Send Message",
        packageName = "com.whatsapp",
        workflowName = "Send Team Update",
        viewId = "com.whatsapp:id/send",
        className = "android.widget.ImageButton",
        text = "Send",
        contentDesc = "Send message",
        x = 988,
        y = 2140
    ),
    SimulatedTapPreset(
        label = "+ Gmail Attachment Icon",
        packageName = "com.google.android.gm",
        workflowName = "Send Status Report to Boss",
        viewId = "com.google.android.gm:id/add_attachment",
        className = "android.widget.ActionMenuItemView",
        text = "",
        contentDesc = "Attach file",
        x = 860,
        y = 176
    ),
    SimulatedTapPreset(
        label = "+ YouTube Subscribe Button",
        packageName = "com.google.android.youtube",
        workflowName = "Search & Play Phonk Music",
        viewId = "com.google.android.youtube:id/subscribe_button",
        className = "android.widget.Button",
        text = "Subscribe",
        contentDesc = "Subscribe to channel",
        x = 890,
        y = 1120
    )
)

/**
 * 2. Tree UI (Visualizing the Agent's Memory):
 * Reads reactively from the Room Database and displays a visual, collapsible hierarchical tree
 * of the workflows and UI nodes the agent has learned:
 * - Parent: 3rd-party App Package Name (e.g. com.google.android.gm, com.google.android.youtube)
 * - Branch: Learned Workflow Name
 * - Children: Ordered sequence of clicked UI nodes (viewIdResourceName, text, contentDescription, className, coordinates)
 */
@Composable
fun MemoryTreeScreen(
    memoryTree: List<AppMemoryTreeNode>,
    isTrainModeActive: Boolean,
    trainingWorkflowName: String,
    trainModeRecordedCount: Int,
    lastRecordedNodeSummary: String,
    onToggleTrainMode: (Boolean) -> Unit,
    onWorkflowNameChanged: (String) -> Unit,
    onSimulateThirdPartyTap: (
        packageName: String,
        workflowName: String,
        viewId: String,
        className: String,
        text: String,
        contentDesc: String,
        x: Int,
        y: Int
    ) -> Unit,
    onReplayWorkflow: (LearnedWorkflowGroup) -> Unit,
    onDeleteWorkflow: (packageName: String, workflowName: String) -> Unit,
    onDeletePackage: (packageName: String) -> Unit,
    onResetSampleMemory: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Track expanded state per packageName and per workflow key
    val expandedPackages = remember { mutableStateMapOf<String, Boolean>() }
    val expandedWorkflows = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 720.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Train Mode (UI Recording) Control Card
        item(key = "train_mode_control_card") {
            TrainModeRecorderCard(
                isTrainModeActive = isTrainModeActive,
                trainingWorkflowName = trainingWorkflowName,
                trainModeRecordedCount = trainModeRecordedCount,
                lastRecordedNodeSummary = lastRecordedNodeSummary,
                onToggleTrainMode = onToggleTrainMode,
                onWorkflowNameChanged = onWorkflowNameChanged,
                onSimulateThirdPartyTap = onSimulateThirdPartyTap
            )
        }

        // 2. Tree Header & Stats Bar
        item(key = "memory_tree_header") {
            val totalPackages = memoryTree.size
            val totalWorkflows = memoryTree.sumOf { it.workflows.size }
            val totalNodes = memoryTree.sumOf { it.totalNodes }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Learned App Memory Tree (Room DB)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$totalPackages apps • $totalWorkflows learned workflows • $totalNodes UI nodes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onResetSampleMemory,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("restore_sample_memory_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Seed Samples", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (memoryTree.isEmpty()) {
            item(key = "empty_memory_tree") {
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
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No Learned Workflows Yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Enable Train Mode above and tap through any 3rd-party app (or tap a quick simulator chip) to populate the Room Memory Tree.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = memoryTree,
                key = { it.packageName }
            ) { appNode ->
                val isPkgExpanded = expandedPackages.getOrPut(appNode.packageName) { true }

                AppPackageTreeNodeCard(
                    appNode = appNode,
                    isExpanded = isPkgExpanded,
                    onToggleExpand = {
                        expandedPackages[appNode.packageName] = !isPkgExpanded
                    },
                    isWorkflowExpanded = { wfName ->
                        expandedWorkflows.getOrPut("${appNode.packageName}::$wfName") { true }
                    },
                    onToggleWorkflowExpand = { wfName ->
                        val key = "${appNode.packageName}::$wfName"
                        val current = expandedWorkflows[key] ?: true
                        expandedWorkflows[key] = !current
                    },
                    onReplayWorkflow = onReplayWorkflow,
                    onDeleteWorkflow = { wfName ->
                        onDeleteWorkflow(appNode.packageName, wfName)
                    },
                    onDeletePackage = {
                        onDeletePackage(appNode.packageName)
                    }
                )
            }
        }
    }
}

@Composable
private fun TrainModeRecorderCard(
    isTrainModeActive: Boolean,
    trainingWorkflowName: String,
    trainModeRecordedCount: Int,
    lastRecordedNodeSummary: String,
    onToggleTrainMode: (Boolean) -> Unit,
    onWorkflowNameChanged: (String) -> Unit,
    onSimulateThirdPartyTap: (
        packageName: String,
        workflowName: String,
        viewId: String,
        className: String,
        text: String,
        contentDesc: String,
        x: Int,
        y: Int
    ) -> Unit
) {
    val borderColor = if (isTrainModeActive) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTrainModeActive) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.28f)
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
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = if (isTrainModeActive) {
                                "Train Mode ACTIVE (Listen Mode)"
                            } else {
                                "Train Mode (UI Recording)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isTrainModeActive) {
                                "Autonomous tapping paused • Intercepting TYPE_VIEW_CLICKED ($trainModeRecordedCount recorded)"
                            } else {
                                "Toggle ON to listen to manual taps in 3rd-party apps and save Learned Workflows"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isTrainModeActive,
                    onCheckedChange = onToggleTrainMode,
                    modifier = Modifier.testTag("train_mode_switch")
                )
            }

            OutlinedTextField(
                value = trainingWorkflowName,
                onValueChange = onWorkflowNameChanged,
                label = { Text("Active Learned Workflow Name") },
                placeholder = { Text("e.g., Send Status Report to Boss") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("train_workflow_name_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = lastRecordedNodeSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isTrainModeActive) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(10.dp)
                )
            }

            // Quick Simulator Strip to test TYPE_VIEW_CLICKED recording in the preview emulator
            Text(
                text = "Simulate 3rd-Party App Tap (TYPE_VIEW_CLICKED -> Room DB):",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SIMULATED_3RD_PARTY_TAPS.forEachIndexed { idx, preset ->
                    AssistChip(
                        onClick = {
                            onSimulateThirdPartyTap(
                                preset.packageName,
                                trainingWorkflowName.ifBlank { preset.workflowName },
                                preset.viewId,
                                preset.className,
                                preset.text,
                                preset.contentDesc,
                                preset.x,
                                preset.y
                            )
                        },
                        label = {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("simulate_tap_chip_$idx")
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPackageTreeNodeCard(
    appNode: AppMemoryTreeNode,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isWorkflowExpanded: (String) -> Boolean,
    onToggleWorkflowExpand: (String) -> Unit,
    onReplayWorkflow: (LearnedWorkflowGroup) -> Unit,
    onDeleteWorkflow: (String) -> Unit,
    onDeletePackage: () -> Unit
) {
    val treeLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Parent Row: App Package Name & App Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = appNode.appLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${appNode.totalNodes} nodes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = appNode.packageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onDeletePackage,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Package Memory",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Collapsible Workflows & Clicked UI Nodes Hierarchy
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    appNode.workflows.forEach { workflow ->
                        val wfExpanded = isWorkflowExpanded(workflow.workflowName)

                        LearnedWorkflowBranchCard(
                            workflow = workflow,
                            isExpanded = wfExpanded,
                            treeLineColor = treeLineColor,
                            onToggleExpand = { onToggleWorkflowExpand(workflow.workflowName) },
                            onReplayWorkflow = { onReplayWorkflow(workflow) },
                            onDeleteWorkflow = { onDeleteWorkflow(workflow.workflowName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LearnedWorkflowBranchCard(
    workflow: LearnedWorkflowGroup,
    isExpanded: Boolean,
    treeLineColor: Color,
    onToggleExpand: () -> Unit,
    onReplayWorkflow: () -> Unit,
    onDeleteWorkflow: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Workflow Branch Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workflow.workflowName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${workflow.nodes.size} sequential UI click steps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onReplayWorkflow,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Execute Learned Workflow",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Execute", style = MaterialTheme.typography.labelSmall)
                }

                IconButton(
                    onClick = onDeleteWorkflow,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Workflow",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Child Sequence of Clicked UI Nodes with Tree Connector Lines
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    workflow.nodes.forEachIndexed { index, node ->
                        val isLast = index == workflow.nodes.lastIndex
                        LearnedUiNodeLeafRow(
                            node = node,
                            isLastChild = isLast,
                            treeLineColor = treeLineColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LearnedUiNodeLeafRow(
    node: LearnedNodeEntity,
    isLastChild: Boolean,
    treeLineColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .drawBehind {
                val strokePx = 2.dp.toPx()
                val lineX = 14.dp.toPx()
                val midY = size.height / 2f
                val bottomY = if (isLastChild) midY else size.height

                // Vertical tree trunk line
                drawLine(
                    color = treeLineColor,
                    start = Offset(lineX, 0f),
                    end = Offset(lineX, bottomY),
                    strokeWidth = strokePx
                )
                // Horizontal branch connector to node card
                drawLine(
                    color = treeLineColor,
                    start = Offset(lineX, midY),
                    end = Offset(28.dp.toPx(), midY),
                    strokeWidth = strokePx
                )
            }
            .padding(start = 30.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "#${node.stepOrder}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = node.className.substringAfterLast('.'),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "(${node.centerX}, ${node.centerY})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // viewIdResourceName
                Text(
                    text = "id: ${node.viewIdResourceName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // text & contentDescription
                val labelParts = buildList {
                    if (node.nodeText.isNotBlank()) add("text=\"${node.nodeText}\"")
                    if (node.contentDescription.isNotBlank()) add("desc=\"${node.contentDescription}\"")
                }.joinToString(" • ")

                if (labelParts.isNotEmpty()) {
                    Text(
                        text = labelParts,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
