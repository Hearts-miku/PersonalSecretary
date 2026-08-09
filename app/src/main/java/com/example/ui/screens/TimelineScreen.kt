package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.ui.components.CustomCalendarView
import com.example.ui.components.MarkdownText
import com.example.ui.viewmodel.WorkLogViewModel

@Composable
fun TimelineScreen(viewModel: WorkLogViewModel) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()
    val selectedDateLog by viewModel.selectedDateLog.collectAsState()
    val isProcessingAI by viewModel.isProcessingAI.collectAsState()
    val aiStatusMessage by viewModel.aiStatusMessage.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchingLogs by viewModel.isSearchingLogs.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    var showRawNotes by remember { mutableStateOf(false) }

    val datesWithLogs = remember(allLogs) {
        allLogs.filter { it.summaryMarkdown.isNotBlank() || it.rawNotes.isNotBlank() }
            .map { it.date }
            .toSet()
    }

    val quickQueries = remember {
        listOf("数据库维度", "Bug 修复", "性能优化", "接口重构", "需求沟通", "项目部署")
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
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "日历时间线",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "工作日历与时间线",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // AI Re-summarize Button
            FilledTonalButton(
                onClick = { viewModel.triggerAISummarizeSelectedDate() },
                enabled = !isProcessingAI,
                modifier = Modifier.testTag("timeline_resummarize_btn")
            ) {
                Icon(
                    imageVector = if (isProcessingAI) Icons.Default.AutoAwesome else Icons.Default.Refresh,
                    contentDescription = "重新总结",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isProcessingAI) "整理中..." else "AI重新总结")
            }
        }

        if (isProcessingAI) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = aiStatusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // --- Semantic Search Section ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("timeline_search_input"),
                    placeholder = {
                        Text("输入意图或关键词 (如: 数据库维度修改/Bug修复)", style = MaterialTheme.typography.bodyMedium)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "语义搜索",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "清除搜索"
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.performSemanticSearch() },
                                enabled = searchQuery.isNotBlank() && !isSearchingLogs
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "执行搜索",
                                    tint = if (searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.performSemanticSearch() }),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Quick Suggestion Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        Text(
                            text = "热搜推荐:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(quickQueries) { tag ->
                        FilterChip(
                            selected = searchQuery == tag,
                            onClick = {
                                viewModel.onSearchQueryChanged(tag)
                                viewModel.performSemanticSearch(tag)
                            },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }

        // Search Loading Indicator
        if (isSearchingLogs) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    Text(
                        text = "AI 正在对历史工作记录进行语义分析与关联匹配...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Search Results List Display
        searchResults?.let { results ->
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
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索结果",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "语义搜索结果 (${results.size} 条)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(onClick = { viewModel.clearSearch() }) {
                            Text("退出搜索", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (results.isEmpty()) {
                        Text(
                            text = "未找到与 \"$searchQuery\" 语义匹配的历史工作日志",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        results.forEach { result ->
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectDate(result.date)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (result.date == selectedDate)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "📅 ${result.date}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        SuggestionChip(
                                            onClick = { viewModel.selectDate(result.date) },
                                            label = {
                                                Text(
                                                    text = "${result.relevanceScore}% 匹配",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }

                                    if (result.matchReason.isNotBlank()) {
                                        Text(
                                            text = "💡 匹配理由: ${result.matchReason}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    if (result.snippet.isNotBlank()) {
                                        Text(
                                            text = result.snippet,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { viewModel.selectDate(result.date) }) {
                                            Text("查看完整日记", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Calendar View Component
        CustomCalendarView(
            selectedDate = selectedDate,
            datesWithLogs = datesWithLogs,
            onDateSelected = { date -> viewModel.selectDate(date) }
        )

        // Selected Date Log Display Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "【$selectedDate】工作内容",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (selectedDateLog?.isSummarized == true) "AI 整理完毕" else "未完成AI总结",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedDateLog?.isSummarized == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                        )
                    }

                    // View Toggle (Summary vs Raw Notes)
                    FilterChip(
                        selected = showRawNotes,
                        onClick = { showRawNotes = !showRawNotes },
                        label = {
                            Text(if (showRawNotes) "查看AI总结" else "查看原始笔记")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Notes,
                                contentDescription = "切换",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                val log = selectedDateLog
                if (log == null || (log.summaryMarkdown.isBlank() && log.rawNotes.isBlank())) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "【$selectedDate】暂无工作记录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "可在主页输入该日期的工作内容，然后点击右上角“AI重新总结”",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else if (showRawNotes) {
                    Text(
                        text = "--- 原始输入笔记 ---\n" + (log.rawNotes.ifBlank { "无原始笔记" }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    MarkdownText(
                        markdown = if (log.summaryMarkdown.isNotBlank()) log.summaryMarkdown else "AI总结尚未生成，请点击上方“AI重新总结”。\n\n原始数据：\n${log.rawNotes}"
                    )
                }
            }
        }
    }
}
