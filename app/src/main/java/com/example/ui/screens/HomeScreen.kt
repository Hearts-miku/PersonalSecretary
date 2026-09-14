package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.WorkLogViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: WorkLogViewModel,
    onNavigateToTimeline: () -> Unit
) {
    val rawText by viewModel.rawInputText.collectAsState()
    val unsummarizedCount by viewModel.unsummarizedCount.collectAsState()
    val isProcessingAI by viewModel.isProcessingAI.collectAsState()
    val aiStatusMessage by viewModel.aiStatusMessage.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    val todayStr = remember { viewModel.repository.getTodayString() }
    val yesterdayStr = remember { viewModel.repository.getYesterdayString() }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    if (showDatePickerDialog) {
        val initialEpochMillis = remember(selectedDate) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(selectedDate)?.time
            } catch (e: Exception) {
                null
            }
        }
        val maxSelectableDateMillis = remember {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            cal.timeInMillis
        }
        val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialEpochMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= maxSelectableDateMillis
                }

                override fun isSelectableYear(year: Int): Boolean {
                    return year <= currentYear
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                            sdf.timeZone = TimeZone.getTimeZone("UTC")
                            val formatted = sdf.format(Date(millis))
                            if (formatted <= todayStr) {
                                viewModel.selectDate(formatted)
                            } else {
                                viewModel.selectDate(todayStr)
                            }
                        }
                        showDatePickerDialog = false
                    },
                    modifier = Modifier.testTag("confirm_date_pick_btn")
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    .padding(16.dp),
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
                            text = "当前有 $unsummarizedCount 个日期的待整理输入",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.triggerAISummarizeAllUnsummarized()
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

        // Recording Date Selector Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "记录日期",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "记录日期: $selectedDate",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = when (selectedDate) {
                            todayStr -> MaterialTheme.colorScheme.primaryContainer
                            yesterdayStr -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.tertiaryContainer
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = when (selectedDate) {
                                todayStr -> "今天"
                                yesterdayStr -> "昨天"
                                else -> "历史"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = when (selectedDate) {
                                todayStr -> MaterialTheme.colorScheme.onPrimaryContainer
                                yesterdayStr -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedDate != todayStr) {
                        FilledTonalButton(
                            onClick = { viewModel.selectDate(todayStr) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("今天", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (selectedDate != yesterdayStr) {
                        OutlinedButton(
                            onClick = { viewModel.selectDate(yesterdayStr) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("昨天", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(
                        onClick = { showDatePickerDialog = true },
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("pick_date_icon_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Today,
                            contentDescription = "选择其他日期",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
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
                    text = if (selectedDate == todayStr) {
                        "在此输入您今天完成的任何工作内容、技术思考、解决的问题或任务记录...\n\n示例：\n• 重构了用户模块的 Room 数据库结构，提升查询性能30%\n• 完成了 AI 接口防注入过滤处理\n• 与产品经理沟通明天的上线计划"
                    } else {
                        "在此输入【$selectedDate】完成的工作内容、技术思考或补记记录...\n\n提交后可点击上方的“立即AI整理”自动生成该日期的总结与待办事项。"
                    },
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
                text = if (selectedDate == todayStr) "提交记录到今日临时日志" else "提交记录到【$selectedDate】临时日志",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

