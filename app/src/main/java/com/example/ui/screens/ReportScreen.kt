package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.MarkdownText
import com.example.ui.viewmodel.WorkLogViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: WorkLogViewModel) {
    val context = LocalContext.current
    val currentDoc by viewModel.currentReport.collectAsState()
    val startDate by viewModel.reportStartDate.collectAsState()
    val endDate by viewModel.reportEndDate.collectAsState()
    val reportType by viewModel.reportType.collectAsState()
    val generatedReport by viewModel.generatedReportMarkdown.collectAsState()
    val isGenerating by viewModel.isGeneratingReport.collectAsState()
    val aiStatusMessage by viewModel.aiStatusMessage.collectAsState()
    val savedFiles by viewModel.savedReportFiles.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()

    val scope = rememberCoroutineScope()
    var showStartDatePicker by rememberSaveable { mutableStateOf(false) }
    var showEndDatePicker by rememberSaveable { mutableStateOf(false) }
    var showCustomSaveDialog by rememberSaveable { mutableStateOf(false) }
    var customSaveTitle by rememberSaveable { mutableStateOf("") }
    var showDownloadDialog by rememberSaveable { mutableStateOf(false) }
    var downloadFileName by rememberSaveable { mutableStateOf("") }
    var pendingDownloadContent by rememberSaveable { mutableStateOf("") }

    // Android Storage Access Framework (SAF) document export launcher for downloading/saving file
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openOutputStream(uri)
                    if (stream == null) {
                        viewModel.showSnack("下载失败：无法创建文件输出流")
                        return@launch
                    }
                    stream.use { outputStream ->
                        outputStream.write(pendingDownloadContent.toByteArray(Charsets.UTF_8))
                        outputStream.flush()
                    }
                    viewModel.showSnack("报告下载成功！已保存到指定位置")
                } catch (e: Exception) {
                    viewModel.showSnack("下载失败: ${e.localizedMessage ?: "未知错误"}")
                }
            }
        }
    }

    val reportTypes = listOf("周报", "月报", "项目报告", "自定义周期总结")
    val todayStr = remember { viewModel.repository.getTodayString() }

    // Count how many logs fall into the selected range
    val logsInRangeCount = remember(allLogs, startDate, endDate) {
        val (s, e) = if (startDate <= endDate) startDate to endDate else endDate to startDate
        allLogs.count { it.date in s..e && (it.summaryMarkdown.isNotBlank() || it.rawNotes.isNotBlank()) }
    }

    val scrollState = rememberScrollState()

    // Start Date Picker Dialog
    if (showStartDatePicker) {
        ReportDatePickerDialog(
            initialDateStr = startDate,
            todayStr = todayStr,
            onDismiss = { showStartDatePicker = false },
            onDateSelected = { selected ->
                viewModel.setReportStartDate(selected)
                showStartDatePicker = false
            }
        )
    }

    // End Date Picker Dialog
    if (showEndDatePicker) {
        ReportDatePickerDialog(
            initialDateStr = endDate,
            todayStr = todayStr,
            onDismiss = { showEndDatePicker = false },
            onDateSelected = { selected ->
                viewModel.setReportEndDate(selected)
                showEndDatePicker = false
            }
        )
    }

    // Download / Export File Dialog
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "下载报告",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("下载报告到本地") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "将此篇工作总结报告导出下载为 Markdown (.md) 纯文本文档：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = downloadFileName,
                        onValueChange = { downloadFileName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("report_download_filename_input"),
                        label = { Text("导出文件名") },
                        placeholder = { Text("如：2026-10-工作周报.md") },
                        singleLine = true
                    )
                    Text(
                        text = "点击“确定下载”后，将调用系统文件保存器，您可以选择下载保存到“下载/Download”目录、文档或外部存储中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = if (downloadFileName.endsWith(".md", ignoreCase = true)) {
                            downloadFileName
                        } else {
                            "$downloadFileName.md"
                        }
                        showDownloadDialog = false
                        exportDocumentLauncher.launch(finalName)
                    },
                    modifier = Modifier.testTag("confirm_download_report_btn")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("确定下载")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Custom Save Dialog
    if (showCustomSaveDialog) {
        AlertDialog(
            onDismissRequest = { showCustomSaveDialog = false },
            title = { Text("保存报告到文档") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请输入报告保存文件名（将存为 Markdown 文档）：", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = customSaveTitle,
                        onValueChange = { customSaveTitle = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("report_save_filename_input"),
                        placeholder = { Text("例如：Q3季度技术攻坚总结") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCurrentReport(customSaveTitle)
                        showCustomSaveDialog = false
                    },
                    modifier = Modifier.testTag("confirm_save_report_btn")
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomSaveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

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
                    imageVector = Icons.Default.Assessment,
                    contentDescription = "总结报告",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "周期总结报告",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "选取指定日期区间，AI 智能提炼专业周报/月报/述职报告",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 1: Date Range & Preset Shortcuts
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
                    Text(
                        text = "选择日期区间",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    SuggestionChip(
                        onClick = { },
                        label = {
                            Text(
                                text = "区间内 $logsInRangeCount 篇有效记录",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }

                // Date Selectors Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start Date Button
                    OutlinedCard(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("report_start_date_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "起始日期",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "起始日期",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = startDate,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Text("至", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)

                    // End Date Button
                    OutlinedCard(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("report_end_date_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "截止日期",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "截止日期",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = endDate,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Quick Range Presets
                Text(
                    text = "快捷快捷周期：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = false,
                            onClick = {
                                // Past 7 days
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                                val cal = Calendar.getInstance()
                                val end = sdf.format(cal.time)
                                cal.add(Calendar.DAY_OF_YEAR, -6)
                                val start = sdf.format(cal.time)
                                viewModel.setReportStartDate(start)
                                viewModel.setReportEndDate(end)
                                viewModel.setReportType("周报")
                            },
                            label = { Text("近 7 天 (周报)") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = false,
                            onClick = {
                                // Past 14 days (bi-weekly)
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                                val cal = Calendar.getInstance()
                                val end = sdf.format(cal.time)
                                cal.add(Calendar.DAY_OF_YEAR, -13)
                                val start = sdf.format(cal.time)
                                viewModel.setReportStartDate(start)
                                viewModel.setReportEndDate(end)
                                viewModel.setReportType("周报")
                            },
                            label = { Text("近 14 天 (双周报)") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = false,
                            onClick = {
                                // Past 30 days
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                                val cal = Calendar.getInstance()
                                val end = sdf.format(cal.time)
                                cal.add(Calendar.DAY_OF_YEAR, -29)
                                val start = sdf.format(cal.time)
                                viewModel.setReportStartDate(start)
                                viewModel.setReportEndDate(end)
                                viewModel.setReportType("月报")
                            },
                            label = { Text("近 30 天 (月报)") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = false,
                            onClick = {
                                // Current natural month
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                                val cal = Calendar.getInstance()
                                val end = sdf.format(cal.time)
                                cal.set(Calendar.DAY_OF_MONTH, 1)
                                val start = sdf.format(cal.time)
                                viewModel.setReportStartDate(start)
                                viewModel.setReportEndDate(end)
                                viewModel.setReportType("月报")
                            },
                            label = { Text("本月至今") }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Report Type Selector
                Text(
                    text = "报告目标类型：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    reportTypes.forEach { type ->
                        FilterChip(
                            selected = reportType == type,
                            onClick = { viewModel.setReportType(type) },
                            label = { Text(type) },
                            modifier = Modifier.testTag("report_type_chip_$type")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action Button: Trigger Report Generation
                Button(
                    onClick = { viewModel.generateReport() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_report_button"),
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI 正在提炼报告中...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "生成报告",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("生成 $reportType 报告")
                    }
                }

                if (isGenerating) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = aiStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { viewModel.cancelActiveAiJob() },
                            modifier = Modifier.testTag("cancel_report_ai_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "取消", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("取消", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Section 2: Saved Historical Reports
        if (savedFiles.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "历史报告",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "已归档报告文档 (${savedFiles.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(savedFiles) { file ->
                            SuggestionChip(
                                onClick = { viewModel.selectSavedReport(file) },
                                label = {
                                    Text(
                                        text = file.nameWithoutExtension,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = "文件",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Generated Report Preview & Operations
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
                            imageVector = Icons.Default.Description,
                            contentDescription = "报告预览",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "报告预览 (Markdown)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Action Buttons (Download, Copy, Save, Share)
                    if (generatedReport.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Download Report Button (Export to device storage)
                            IconButton(
                                onClick = {
                                    val boundTitle = currentDoc?.title?.ifBlank { null } ?: "${startDate}_至_${endDate}_$reportType"
                                    downloadFileName = boundTitle
                                    pendingDownloadContent = generatedReport
                                    showDownloadDialog = true
                                },
                                modifier = Modifier.testTag("download_report_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "下载报告",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("工作总结报告", generatedReport))
                                    viewModel.showSnack("报告已复制到剪贴板！")
                                },
                                modifier = Modifier.testTag("copy_report_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "复制报告",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    val boundTitle = currentDoc?.title?.ifBlank { null } ?: "${startDate}_至_${endDate}_$reportType"
                                    customSaveTitle = boundTitle
                                    showCustomSaveDialog = true
                                },
                                modifier = Modifier.testTag("save_report_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "另存为",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    val boundTitle = currentDoc?.title?.ifBlank { null } ?: "${startDate}至${endDate} $reportType"
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TITLE, boundTitle)
                                        putExtra(Intent.EXTRA_SUBJECT, boundTitle)
                                        putExtra(Intent.EXTRA_TEXT, generatedReport)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "分享报告")
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier.testTag("share_report_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "分享报告",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (generatedReport.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "未生成报告",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "尚未生成报告",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "选取上方日期范围并点击“生成报告”，AI 将汇总各日日志并撰写汇报",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                } else {
                    MarkdownText(
                        markdown = generatedReport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("report_markdown_preview")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDatePickerDialog(
    initialDateStr: String,
    todayStr: String,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val initialEpochMillis = remember(initialDateStr) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(initialDateStr)?.time
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
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                        val formatted = sdf.format(Date(millis))
                        if (formatted <= todayStr) {
                            onDateSelected(formatted)
                        } else {
                            onDateSelected(todayStr)
                        }
                    }
                },
                modifier = Modifier.testTag("report_date_confirm_btn")
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
