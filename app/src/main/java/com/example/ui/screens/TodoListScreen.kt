package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TodoItemEntity
import com.example.ui.viewmodel.WorkLogViewModel

@Composable
fun TodoListScreen(viewModel: WorkLogViewModel) {
    val allTodos by viewModel.allTodos.collectAsState()
    val lastDeletedTodo by viewModel.lastDeletedTodo.collectAsState()

    var selectedFilter by rememberSaveable { mutableStateOf("ALL") } // ALL, PENDING, COMPLETED, HIGH
    var selectedProject by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    val availableProjects = remember(allTodos) {
        allTodos.map { it.category.trim() }
            .filter { it.isNotBlank() && it != "Work" && it != "工作" && it != "通用项目" }
            .distinct()
    }

    val filteredTodos = remember(allTodos, selectedFilter, selectedProject) {
        allTodos.filter { todo ->
            val statusMatch = when (selectedFilter) {
                "PENDING" -> !todo.isCompleted
                "COMPLETED" -> todo.isCompleted
                "HIGH" -> todo.priority == "HIGH"
                else -> true
            }
            val projectMatch = selectedProject == null || todo.category.trim() == selectedProject
            statusMatch && projectMatch
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_todo_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "手动添加待办")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FormatListNumbered,
                        contentDescription = "待办",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI整理待办事项",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "共 ${filteredTodos.size} 项",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    "ALL" to "全部",
                    "PENDING" to "未完成",
                    "COMPLETED" to "已完成",
                    "HIGH" to "高优先级"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label) }
                    )
                }
            }

            // Project-level Filter Chips (when projects exist)
            if (availableProjects.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        Text(
                            text = "项目维度:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedProject == null,
                            onClick = { selectedProject = null },
                            label = { Text("全部项目", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    items(availableProjects) { proj ->
                        FilterChip(
                            selected = selectedProject == proj,
                            onClick = {
                                selectedProject = if (selectedProject == proj) null else proj
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = { Text(proj, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // Todo Item List
            if (filteredTodos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "空列表",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无待办事项",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "提交工作记录后，AI将按项目粒度自动提取后续里程碑与待办",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTodos, key = { it.id }) { item ->
                        TodoCardItem(
                            item = item,
                            onToggleCompleted = { isCompleted ->
                                viewModel.toggleTodoCompleted(item.id, isCompleted)
                            },
                            onDelete = {
                                viewModel.deleteTodo(item.id)
                            },
                            onProjectClick = { proj ->
                                selectedProject = proj
                            }
                        )
                    }
                }
            }

            if (lastDeletedTodo != null) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已删除待办: ${lastDeletedTodo?.title?.take(15)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface
                        )
                        TextButton(onClick = { viewModel.undoDeleteTodo() }) {
                            Text("撤销", color = MaterialTheme.colorScheme.inversePrimary)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTodoDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, desc, priority, category ->
                viewModel.addTodo(title, desc, priority, category)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun TodoCardItem(
    item: TodoItemEntity,
    onToggleCompleted: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onProjectClick: ((String) -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = item.isCompleted,
                onCheckedChange = { onToggleCompleted(it) }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    PriorityBadge(item.priority)
                }

                if (item.description.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.category.isNotBlank()) {
                        SuggestionChip(
                            onClick = { onProjectClick?.invoke(item.category) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            label = { Text(item.category, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(26.dp)
                        )
                    }
                    if (item.sourceLogDate.isNotBlank()) {
                        Text(
                            text = "来源: ${item.sourceLogDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun PriorityBadge(priority: String) {
    val (bg, fg, label) = when (priority) {
        "HIGH" -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "高优")
        "LOW" -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "低优")
        else -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, "中优")
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AddTodoDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, desc: String, priority: String, category: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    var category by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建项目级待办") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("所属项目/模块 (如：支付中台、数据看板)") },
                    placeholder = { Text("例如：用户中心微服务") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("待办任务标题") },
                    placeholder = { Text("【项目名称】核心任务概述") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("拆解执行子项 / 详细说明（可选）") },
                    placeholder = { Text("1. 子项一；\n2. 子项二；") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("优先级：", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("HIGH" to "高", "MEDIUM" to "中", "LOW" to "低").forEach { (p, name) ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val finalCategory = category.trim().ifBlank { "通用项目" }
                        val finalTitle = if (!title.startsWith("【") && finalCategory.isNotBlank()) {
                            "【$finalCategory】$title"
                        } else {
                            title
                        }
                        onAdd(finalTitle, desc, priority, finalCategory)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
