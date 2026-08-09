package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.WorkLogViewModel

@Composable
fun HomeScreen(
    viewModel: WorkLogViewModel,
    onNavigateToTimeline: () -> Unit
) {
    val rawText by viewModel.rawInputText.collectAsState()
    val unsummarizedCount by viewModel.unsummarizedCount.collectAsState()
    val isProcessingAI by viewModel.isProcessingAI.collectAsState()
    val aiStatusMessage by viewModel.aiStatusMessage.collectAsState()

    val quickTags = listOf(
        "完成了核心功能",
        "修复阻断性Bug",
        "与团队讨论技术方案",
        "撰写技术设计文档",
        "重构并优化模块代码",
        "上线发布并监控数据"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "记录",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "工作随记与记录",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "随时记录任何工作碎片与任务心得，AI将自动整理为总结、待办与履历。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Unsummarized Status Indicator Banner
        if (unsummarizedCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "待处理",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "当前有 $unsummarizedCount 条未整理输入",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.triggerAISummarizeSelectedDate()
                        },
                        enabled = !isProcessingAI,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("home_ai_summarize_btn")
                    ) {
                        Text(if (isProcessingAI) "AI整理中..." else "立即AI整理")
                    }
                }
            }
        }

        if (isProcessingAI) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = aiStatusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Quick Tag Suggestion Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(quickTags) { tag ->
                FilterChip(
                    selected = false,
                    onClick = {
                        val newText = if (rawText.isBlank()) "• $tag " else "$rawText\n• $tag "
                        viewModel.onRawInputChanged(newText)
                    },
                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Main User Input Area
        OutlinedTextField(
            value = rawText,
            onValueChange = { viewModel.onRawInputChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("home_input_textfield"),
            placeholder = {
                Text(
                    text = "在此输入您今天完成的任何工作内容、技术思考、解决的问题或任务记录...\n\n示例：\n• 重构了用户模块的 Room 数据库结构，提升查询性能30%\n• 完成了 Gemini API 接口防注入过滤处理\n• 与产品经理沟通明天的上线计划",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        // Bottom Action Button
        Button(
            onClick = { viewModel.submitRawInput() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("home_submit_btn"),
            enabled = rawText.isNotBlank(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "提交")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "提交记录到临时日志",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
