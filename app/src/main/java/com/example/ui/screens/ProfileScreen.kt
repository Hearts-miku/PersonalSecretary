package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ExperienceVersionEntity
import com.example.data.utils.DiffType
import com.example.data.utils.DiffUtils
import com.example.ui.components.MarkdownText
import com.example.ui.viewmodel.WorkLogViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    viewModel: WorkLogViewModel,
    onNavigateToResume: () -> Unit
) {
    val context = LocalContext.current
    val careerProfile by viewModel.careerProfile.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()
    val isGeneratingWorkExp by viewModel.isGeneratingWorkExp.collectAsState()
    val isGeneratingProjectExp by viewModel.isGeneratingProjectExp.collectAsState()
    val aiStatusMessage by viewModel.aiStatusMessage.collectAsState()

    val workVersions by viewModel.workVersions.collectAsState()
    val projectVersions by viewModel.projectVersions.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 = 工作经历, 1 = 项目经历, 2 = 职业档案全貌
    val editingProfileText by viewModel.editingProfileText.collectAsState()
    val isEditingProfile = editingProfileText != null

    // Dialog state for Version History & Diff
    var showHistoryDialogType by remember { mutableStateOf<String?>(null) } // "WORK" or "PROJECT" or null
    var showDiffDialog by remember { mutableStateOf(false) }
    var diffOldContent by remember { mutableStateOf("") }
    var diffNewContent by remember { mutableStateOf("") }
    var diffTitle by remember { mutableStateOf("差异对比") }

    // File Import state
    var pendingImportContent by remember { mutableStateOf<String?>(null) }
    var pendingImportFileName by remember { mutableStateOf("") }
    var showImportTypeDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                var fileName = "imported_file.txt"
                if (uri.scheme == "content") {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx != -1) fileName = cursor.getString(nameIdx)
                        }
                    }
                }
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (text.isNotBlank()) {
                    pendingImportContent = text
                    pendingImportFileName = fileName
                    showImportTypeDialog = true
                } else {
                    viewModel.showSnack("文件内容为空，请重新选择有效的 .txt 或 .md 文件")
                }
            } catch (e: Exception) {
                viewModel.showSnack("文件读取失败: ${e.localizedMessage}")
            }
        }
    }

    val summarizedDays = remember(allLogs) { allLogs.count { it.isSummarized } }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Profile Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "头像",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "个人中心",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "AI 智能职业履历与经历智库",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Security Badge
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "数据安全",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "隐私保护",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats & Quick Actions Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text("已记录 $summarizedDays 天总结") },
                        leadingIcon = { Icon(Icons.Default.Work, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { filePickerLauncher.launch("*/*") },
                        label = { Text("导入 txt/md 文件") },
                        leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("upload_file_header_chip")
                    )
                }
            }
        }

        // Secondary Page Entry Card: "我的简历" (My Resume)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "简历",
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "我的简历生成器",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "基于 AI 总结的履历，一键脱敏导出专业简历",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Button(
                    onClick = onNavigateToResume,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.testTag("open_resume_page_btn")
                ) {
                    Text("进入简历")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "查看",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Processing Progress
        if (isGeneratingWorkExp || isGeneratingProjectExp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = aiStatusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Section Tabs: 0 = 工作经历, 1 = 项目经历, 2 = 职业档案全貌
        TabRow(selectedTabIndex = activeTab) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("💼 工作经历", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("🚀 项目经历", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("📄 职业档案", fontWeight = FontWeight.Bold) }
            )
        }

        // Detailed Content Display Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (activeTab) {
                    0 -> {
                        // Work Experiences Tab
                        val workExpContent = careerProfile?.workExperiences.orEmpty()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI 总结的工作经历",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Import Button
                                IconButton(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    modifier = Modifier.testTag("import_work_exp_file_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = "导入txt/md文件",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // History Button
                                OutlinedButton(
                                    onClick = { showHistoryDialogType = "WORK" },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("work_exp_history_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "历史版本",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("版本(${workVersions.size})")
                                }

                                // Quick Diff Button if history exists
                                if (workVersions.size >= 2 || (workVersions.size == 1 && workExpContent != workVersions.first().content)) {
                                    OutlinedButton(
                                        onClick = {
                                            val oldVer = workVersions.getOrNull(1)?.content ?: workVersions.firstOrNull()?.content.orEmpty()
                                            diffOldContent = oldVer
                                            diffNewContent = workExpContent
                                            diffTitle = "工作经历 - 最新 vs 历史对比"
                                            showDiffDialog = true
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("work_exp_diff_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CompareArrows,
                                            contentDescription = "差异对比",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("查看差异")
                                    }
                                }

                                Button(
                                    onClick = { viewModel.generateWorkExperiences() },
                                    enabled = !isGeneratingWorkExp,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("generate_work_exp_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "提炼",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (workExpContent.isBlank()) "AI 提炼工作经历" else "重新提炼")
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        if (workExpContent.isBlank()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "尚未生成工作经历总结",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { viewModel.generateWorkExperiences() }) {
                                            Text("AI 智能提炼")
                                        }
                                        OutlinedButton(onClick = { filePickerLauncher.launch("*/*") }) {
                                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("导入 txt/md 文件")
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                            ) {
                                MarkdownText(markdown = workExpContent)
                            }
                        }
                    }

                    1 -> {
                        // Project Experiences Tab
                        val projectExpContent = careerProfile?.projectExperiences.orEmpty()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI 总结的项目经历",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Import Button
                                IconButton(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    modifier = Modifier.testTag("import_project_exp_file_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = "导入txt/md文件",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // History Button
                                OutlinedButton(
                                    onClick = { showHistoryDialogType = "PROJECT" },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("project_exp_history_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "历史版本",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("版本(${projectVersions.size})")
                                }

                                // Quick Diff Button if history exists
                                if (projectVersions.size >= 2 || (projectVersions.size == 1 && projectExpContent != projectVersions.first().content)) {
                                    OutlinedButton(
                                        onClick = {
                                            val oldVer = projectVersions.getOrNull(1)?.content ?: projectVersions.firstOrNull()?.content.orEmpty()
                                            diffOldContent = oldVer
                                            diffNewContent = projectExpContent
                                            diffTitle = "项目经历 - 最新 vs 历史对比"
                                            showDiffDialog = true
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("project_exp_diff_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CompareArrows,
                                            contentDescription = "差异对比",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("查看差异")
                                    }
                                }

                                Button(
                                    onClick = { viewModel.generateProjectExperiences() },
                                    enabled = !isGeneratingProjectExp,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("generate_project_exp_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RocketLaunch,
                                        contentDescription = "提取",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (projectExpContent.isBlank()) "AI 提取项目经历" else "重新提取")
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        if (projectExpContent.isBlank()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "尚未提取项目经历",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { viewModel.generateProjectExperiences() }) {
                                            Text("AI 智能提取")
                                        }
                                        OutlinedButton(onClick = { filePickerLauncher.launch("*/*") }) {
                                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("导入 txt/md 文件")
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                            ) {
                                MarkdownText(markdown = projectExpContent)
                            }
                        }
                    }

                    2 -> {
                        // Career Profile Document Tab
                        val profileContent = careerProfile?.markdownContent.orEmpty()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "职业履历全貌 (career_profile.md)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = "导入txt/md文件",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                TextButton(onClick = {
                                    if (isEditingProfile) {
                                        viewModel.saveCareerProfileManually(editingProfileText ?: "")
                                    } else {
                                        viewModel.startEditingProfile(profileContent)
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "编辑",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isEditingProfile) "保存修改" else "手动编辑")
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        if (isEditingProfile) {
                            OutlinedTextField(
                                value = editingProfileText ?: "",
                                onValueChange = { viewModel.updateEditingProfileText(it) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                            ) {
                                MarkdownText(
                                    markdown = profileContent.ifBlank { "职业履历仍在收集阶段，随着您在【主页】记录每日工作并触发AI整理，此处的职业总结将自动增量扩展。" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Import Type Selection Dialog ---
    if (showImportTypeDialog && pendingImportContent != null) {
        val content = pendingImportContent!!
        val fileName = pendingImportFileName

        AlertDialog(
            onDismissRequest = {
                showImportTypeDialog = false
                pendingImportContent = null
            },
            title = {
                Text(
                    text = "选择文件导入目标",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "已读取文件: $fileName (${content.length} 字)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "请选择将此文本初始化写入哪一项记录？导入后将自动保留历史版本并可随时查看差异点。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.importWorkExperiences(content, fileName)
                            showImportTypeDialog = false
                            pendingImportContent = null
                            activeTab = 0
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("💼 导入初始化为【工作经历】")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.importProjectExperiences(content, fileName)
                            showImportTypeDialog = false
                            pendingImportContent = null
                            activeTab = 1
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🚀 导入初始化为【项目经历】")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.saveCareerProfileManually(content)
                            showImportTypeDialog = false
                            pendingImportContent = null
                            activeTab = 2
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("📄 导入初始化为【职业档案全貌】")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showImportTypeDialog = false
                    pendingImportContent = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    // --- Version History Dialog ---
    if (showHistoryDialogType != null) {
        val type = showHistoryDialogType!!
        val isWork = type == "WORK"
        val versions = if (isWork) workVersions else projectVersions
        val currentContent = if (isWork) careerProfile?.workExperiences.orEmpty() else careerProfile?.projectExperiences.orEmpty()
        val titleText = if (isWork) "工作经历" else "项目经历"

        VersionHistoryDialog(
            typeTitle = titleText,
            versions = versions,
            currentContent = currentContent,
            onRestore = { ver ->
                viewModel.restoreExperienceVersion(ver)
                showHistoryDialogType = null
            },
            onDelete = { id ->
                viewModel.deleteExperienceVersion(id)
            },
            onCompareWithCurrent = { ver ->
                diffOldContent = ver.content
                diffNewContent = currentContent
                diffTitle = "$titleText - 所选历史版本 vs 当前版本"
                showDiffDialog = true
            },
            onDismiss = { showHistoryDialogType = null }
        )
    }

    // --- Diff Comparison Dialog ---
    if (showDiffDialog) {
        DiffComparisonDialog(
            title = diffTitle,
            oldText = diffOldContent,
            newText = diffNewContent,
            onDismiss = { showDiffDialog = false }
        )
    }
}

@Composable
fun VersionHistoryDialog(
    typeTitle: String,
    versions: List<ExperienceVersionEntity>,
    currentContent: String,
    onRestore: (ExperienceVersionEntity) -> Unit,
    onDelete: (Int) -> Unit,
    onCompareWithCurrent: (ExperienceVersionEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "$typeTitle - 版本历史管理",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            if (versions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无历史版本记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    versions.forEachIndexed { index, ver ->
                        val isCurrent = ver.content == currentContent

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "v${versions.size - index}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (isCurrent) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "当前使用",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = ver.summaryNote,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = dateFormat.format(Date(ver.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Snippet Preview
                                Text(
                                    text = ver.content.take(120) + if (ver.content.length > 120) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { onCompareWithCurrent(ver) }) {
                                        Icon(Icons.Default.CompareArrows, contentDescription = "对比", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("查看差异", style = MaterialTheme.typography.labelMedium)
                                    }

                                    if (!isCurrent) {
                                        TextButton(onClick = { onRestore(ver) }) {
                                            Icon(Icons.Default.History, contentDescription = "恢复", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("恢复此版", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    IconButton(
                                        onClick = { onDelete(ver.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除记录",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun DiffComparisonDialog(
    title: String,
    oldText: String,
    newText: String,
    onDismiss: () -> Unit
) {
    val diffLines = remember(oldText, newText) {
        DiffUtils.computeDiff(oldText, newText)
    }

    val addedCount = diffLines.count { it.type == DiffType.ADDED }
    val removedCount = diffLines.count { it.type == DiffType.REMOVED }
    val unchangedCount = diffLines.count { it.type == DiffType.UNCHANGED }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "+$addedCount 行新增",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "-$removedCount 行删除",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFC62828),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "$unchangedCount 行未变",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    diffLines.forEach { line ->
                        val (bgColor, textColor, iconSymbol) = when (line.type) {
                            DiffType.ADDED -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "+")
                            DiffType.REMOVED -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "-")
                            DiffType.UNCHANGED -> Triple(Color.Transparent, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), " ")
                        }

                        Surface(
                            color = bgColor,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = iconSymbol,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = textColor,
                                    modifier = Modifier.width(16.dp)
                                )
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
