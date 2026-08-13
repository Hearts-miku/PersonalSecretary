package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.WorkLogViewModel

@Composable
fun SettingsScreen(viewModel: WorkLogViewModel) {
    val settings by viewModel.settings.collectAsState()
    val isProcessingAI by viewModel.isProcessingAI.collectAsState()
    val aiStatusMessage by viewModel.aiStatusMessage.collectAsState()

    var selectedProvider by remember(settings) { mutableStateOf(settings.apiProvider) }
    var apiKeyText by remember(settings) { mutableStateOf(settings.apiKey) }
    var baseUrlText by remember(settings) { mutableStateOf(settings.baseUrl) }
    var selectedModel by remember(settings) { mutableStateOf(settings.selectedModel) }
    var isAutoEnabled by remember(settings) { mutableStateOf(settings.isAutoSummaryEnabled) }
    var hideKey by remember { mutableStateOf(true) }

    val mdInfo = remember { viewModel.repository.markdownManager.getMarkdownDirectoryInfo() }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
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

        // Section 0: UI Theme Selection
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

                Text(
                    text = "根据偏好选择简约白色 (浅色) 主题、极简黑色 (深色) 主题或跟随系统设置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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

        // Section 1: AI Model & API Settings
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
                        imageVector = Icons.Default.Key,
                        contentDescription = "API",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "模型 API 相关配置",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "支持 Google Gemini、OpenAI、Anthropic (Claude) 及自定义 OpenAI 兼容 API。选择服务商后可使用预设模型或手动指定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // API Provider Selector
                Text("选择 API 服务商：", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val providers = listOf(
                        "GEMINI" to "Google Gemini",
                        "OPENAI" to "OpenAI",
                        "ANTHROPIC" to "Anthropic",
                        "CUSTOM" to "自定义 API"
                    )
                    providers.forEach { (pKey, pLabel) ->
                        FilterChip(
                            selected = selectedProvider == pKey,
                            onClick = {
                                selectedProvider = pKey
                                when (pKey) {
                                    "GEMINI" -> {
                                        baseUrlText = "https://generativelanguage.googleapis.com/"
                                        if (selectedModel.isBlank() || selectedModel.contains("gpt") || selectedModel.contains("claude") || selectedModel == "gemini-3.5-flash") {
                                            selectedModel = "gemini-2.5-flash"
                                        }
                                    }
                                    "OPENAI" -> {
                                        baseUrlText = "https://api.openai.com/v1/"
                                        if (selectedModel.isBlank() || selectedModel.contains("gemini") || selectedModel.contains("claude")) {
                                            selectedModel = "gpt-4o"
                                        }
                                    }
                                    "ANTHROPIC" -> {
                                        baseUrlText = "https://api.anthropic.com/"
                                        if (selectedModel.isBlank() || selectedModel.contains("gemini") || selectedModel.contains("gpt")) {
                                            selectedModel = "claude-3-5-sonnet-20241022"
                                        }
                                    }
                                    "CUSTOM" -> {
                                        if (baseUrlText.isBlank()) {
                                            baseUrlText = "https://api.openai.com/v1/"
                                        }
                                    }
                                }
                            },
                            label = { Text(pLabel) },
                            modifier = Modifier.testTag("provider_chip_$pKey")
                        )
                    }
                }

                // API Key Input
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    label = {
                        val keyLabel = when (selectedProvider) {
                            "OPENAI" -> "OpenAI API Key (sk-...)"
                            "ANTHROPIC" -> "Anthropic API Key (sk-ant-...)"
                            "CUSTOM" -> "自定义 API Key"
                            else -> "Gemini API Key (默认读取 Secrets)"
                        }
                        Text(keyLabel)
                    },
                    visualTransformation = if (hideKey) PasswordVisualTransformation() else VisualTransformation.None,
                    trailingIcon = {
                        TextButton(onClick = { hideKey = !hideKey }) {
                            Text(if (hideKey) "显示" else "隐藏")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("settings_api_key_input")
                )

                // Recommended Model Presets
                val modelPresets = when (selectedProvider) {
                    "OPENAI" -> listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "o3-mini")
                    "ANTHROPIC" -> listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229")
                    "CUSTOM" -> listOf("deepseek-chat", "deepseek-r1", "qwen-max", "llama-3.3-70b")
                    else -> listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")
                }

                Text("快捷选择或手动输入模型：", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modelPresets.forEach { model ->
                        FilterChip(
                            selected = selectedModel == model,
                            onClick = { selectedModel = model },
                            label = { Text(model) },
                            modifier = Modifier.testTag("model_chip_$model")
                        )
                    }
                }

                // Model Name Input Field
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = { selectedModel = it },
                    label = { Text("AI 模型标识名称 (Model Name)") },
                    modifier = Modifier.fillMaxWidth().testTag("settings_model_input")
                )

                // Base URL Input
                OutlinedTextField(
                    value = baseUrlText,
                    onValueChange = { baseUrlText = it },
                    label = { Text("API 请求 Base URL") },
                    modifier = Modifier.fillMaxWidth().testTag("settings_base_url_input")
                )

                Button(
                    onClick = {
                        viewModel.updateSettings(
                            settings.copy(
                                apiKey = apiKeyText,
                                baseUrl = baseUrlText,
                                selectedModel = selectedModel,
                                apiProvider = selectedProvider,
                                isAutoSummaryEnabled = isAutoEnabled
                            )
                        )
                    },
                    modifier = Modifier.align(Alignment.End).testTag("save_api_settings_button")
                ) {
                    Text("保存 API 配置")
                }
            }
        }

        // Section 2: Scheduled Daily Task Settings
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
                            text = "定时任务管理",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Switch(
                        checked = isAutoEnabled,
                        onCheckedChange = {
                            isAutoEnabled = it
                            viewModel.updateSettings(settings.copy(isAutoSummaryEnabled = it))
                        }
                    )
                }

                Text(
                    text = "配置每天固定时间（默认 20:00）自动对未整理的原始工作内容进行 AI 归纳，并自动评估是否更新【用户职业履历与技能】。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
                    Text(
                        text = aiStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Section 3: Markdown Document System Storage Inspector
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
                        text = "后台 Markdown 文档系统状态",
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
                    Text("1. 每日工作总结文档目录 (worklogs/): ${mdInfo["worklogs_count"] ?: "0"}", style = MaterialTheme.typography.bodySmall)
                    Text("2. 临时原始记录文档 (temp/raw_notes.md): ${mdInfo["raw_notes.md"] ?: "0 bytes"}", style = MaterialTheme.typography.bodySmall)
                    Text("3. 用户职业履历文档 (user/career_profile.md): ${mdInfo["career_profile.md"] ?: "0 bytes"}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Section 4: AI Safety & Guardrails Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "安全与约束",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI 侧安全防线与严格约束",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "• 严防提示词注入：输入文本隔离在 XML 专属数据标签内，自动过滤越权指令。\n• 严防幻觉：强制AI只根据真实日志提炼，严禁臆造工作内容与编造简历经历。\n• 隐私脱敏：生成简历强制使用 [姓名]、[电话]、[邮箱] 等标准占位符。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
