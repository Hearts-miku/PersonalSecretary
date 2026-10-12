package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.WorkLogViewModel

@Composable
fun SettingsScreen(viewModel: WorkLogViewModel) {
    val settingsState by viewModel.settings.collectAsState()
    val isProcessingAI by viewModel.isProcessingAI.collectAsState()
    val aiStatusMessage by viewModel.aiStatusMessage.collectAsState()
    val context = LocalContext.current

    if (settingsState == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val settings = settingsState!!

    // 使用 remember 存储敏感 API Key，避免保存到进程状态 Bundle (P1-4)
    var apiKeyText by remember { mutableStateOf(settings.apiKey) }
    var baseUrlText by rememberSaveable { mutableStateOf(settings.baseUrl) }
    var selectedModel by rememberSaveable { mutableStateOf(settings.selectedModel) }
    var hideKey by remember { mutableStateOf(true) }

    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()

    val mdInfo by viewModel.mdInfo.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadMdInfo()
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            try {
                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    viewModel.exportAllDataToZip(outputStream)
                } else {
                    viewModel.showSnack("无法打开目标文件流")
                }
            } catch (e: Exception) {
                viewModel.showSnack("导出异常: ${e.message}")
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题栏
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "系统与外观配置",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        }

        // 卡片 1: 界面主题配置
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "主题",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "界面主题模式",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themeOptions = listOf(
                        "SYSTEM" to "跟随系统",
                        "LIGHT" to "白色主题",
                        "DARK" to "黑色主题"
                    )
                    themeOptions.forEach { (modeKey, label) ->
                        FilterChip(
                            selected = settings.themeMode == modeKey,
                            onClick = {
                                viewModel.updateSettings(settings.copy(themeMode = modeKey))
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("theme_chip_$modeKey")
                        )
                    }
                }
            }
        }

        // 卡片 2: 自定义 API 配置
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "API",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "自定义 API 服务配置",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // API Key 输入框
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    label = { Text("自定义 API Key") },
                    placeholder = { Text("请输入您的 API Key") },
                    visualTransformation = if (hideKey) PasswordVisualTransformation() else VisualTransformation.None,
                    trailingIcon = {
                        TextButton(onClick = { hideKey = !hideKey }) {
                            Text(if (hideKey) "显示" else "隐藏")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("settings_api_key_input")
                )

                // 模型标识名称
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = { selectedModel = it },
                    label = { Text("AI 模型名称 (Model Name)") },
                    placeholder = { Text("例如：gpt-4o、qwen-plus 等") },
                    modifier = Modifier.fillMaxWidth().testTag("settings_model_input")
                )

                // Base URL 输入框
                OutlinedTextField(
                    value = baseUrlText,
                    onValueChange = { baseUrlText = it },
                    label = { Text("API 服务 Base URL") },
                    placeholder = { Text("https://api.your-provider.com/v1/") },
                    modifier = Modifier.fillMaxWidth().testTag("settings_base_url_input")
                )

                // 连接测试结果横幅
                connectionTestResult?.let { testResult ->
                    val isSuccess = testResult.startsWith("连接成功")
                    Surface(
                        color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                               else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connection_test_result_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = if (isSuccess) "测试成功" else "测试失败",
                                tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = testResult,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearConnectionTestResult() },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("dismiss_test_result_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // 操作按钮栏：测试连接 + 保存配置
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.testAiConnection(
                                baseUrl = baseUrlText,
                                apiKey = apiKeyText,
                                model = selectedModel
                            )
                        },
                        enabled = !isTestingConnection,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_api_connection_button")
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("测试中...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("测试连接", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Button(
                        onClick = {
                            val trimmedUrl = baseUrlText.trim()
                            if (trimmedUrl.isNotBlank() && !trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                                viewModel.showSnack("Base URL 必须以 http:// 或 https:// 开头")
                                return@Button
                            }
                            viewModel.updateSettings(
                                settings.copy(
                                    apiKey = apiKeyText.trim(),
                                    baseUrl = trimmedUrl,
                                    selectedModel = selectedModel.trim()
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_api_settings_button")
                    ) {
                        Text("保存配置", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // 卡片 3: 定时任务管理
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "定时任务",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "定时整理任务",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Button(
                    onClick = { viewModel.triggerManualScheduledTask() },
                    enabled = !isProcessingAI,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth().testTag("trigger_scheduled_task_btn")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "运行")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isProcessingAI) "后台任务运行中..." else "立即触发后台 AI 自动整理任务")
                }

                if (isProcessingAI) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = aiStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.cancelActiveAiJob() }) {
                            Text("取消", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // 卡片 4: 本地 Markdown 文档状态
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "文档系统",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "本地 Markdown 文档状态",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("每日工作总结文档 (worklogs/): ${mdInfo["worklogs_count"] ?: "0"}", style = MaterialTheme.typography.bodySmall)
                    Text("临时原始记录 (temp/raw_notes.md): ${mdInfo["raw_notes.md"] ?: "0 bytes"}", style = MaterialTheme.typography.bodySmall)
                    Text("用户职业履历 (user/career_profile.md): ${mdInfo["career_profile.md"] ?: "0 bytes"}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 卡片 5: 数据管理 (导出 ZIP & 清空数据)
        var showClearDataDialog by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "数据备份与重置",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                OutlinedButton(
                    onClick = {
                        val fileName = "PersonalSecretary_Backup_${viewModel.repository.getTodayString()}.zip"
                        exportZipLauncher.launch(fileName)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("export_data_zip_btn")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出全部数据备份 (ZIP)")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Button(
                    onClick = { showClearDataDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().testTag("clear_all_data_btn")
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("清空所有数据")
                }
            }
        }

        if (showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearDataDialog = false },
                title = { Text("确认清空所有数据？", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                text = { Text("这将永久删除所有的工作日志、待办事项、职业经历版本以及所有的本地 Markdown 文件。该操作无法撤销！") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllData()
                            showClearDataDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("确认")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDataDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}
