package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CustomCalendarView(
    selectedDate: String, // YYYY-MM-DD
    datesWithLogs: Set<String>, // Dates that have recorded work logs
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val calendar = remember { Calendar.getInstance() }
    
    // Initialize calendar to selectedDate
    LaunchedEffect(selectedDate) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val d = sdf.parse(selectedDate)
            if (d != null) {
                calendar.time = d
            }
        } catch (e: Exception) {
            // Default to today
        }
    }

    var currentMonthCalendar by remember { mutableStateOf(calendar.clone() as Calendar) }

    val monthFormat = remember { SimpleDateFormat("yyyy年 MM月", Locale.CHINA) }
    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Month Header Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val cal = currentMonthCalendar.clone() as Calendar
                    cal.add(Calendar.MONTH, -1)
                    currentMonthCalendar = cal
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "上一月"
                    )
                }

                Text(
                    text = monthFormat.format(currentMonthCalendar.time),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = {
                    val cal = currentMonthCalendar.clone() as Calendar
                    cal.add(Calendar.MONTH, 1)
                    currentMonthCalendar = cal
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "下一月"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Days of week header
            Row(modifier = Modifier.fillMaxWidth()) {
                val daysOfWeek = listOf("日", "一", "二", "三", "四", "五", "六")
                daysOfWeek.forEach { dayName ->
                    Text(
                        text = dayName,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar Grid Calculations
            val calGrid = currentMonthCalendar.clone() as Calendar
            calGrid.set(Calendar.DAY_OF_MONTH, 1)
            val firstDayOfWeek = calGrid.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sun
            val maxDaysInMonth = calGrid.getActualMaximum(Calendar.DAY_OF_MONTH)

            val totalCells = firstDayOfWeek + maxDaysInMonth
            val rows = (totalCells + 6) / 7

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                var dayCounter = 1
                for (r in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (c in 0..6) {
                            val cellIndex = r * 7 + c
                            if (cellIndex < firstDayOfWeek || dayCounter > maxDaysInMonth) {
                                // Empty cell
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                val currentDayNum = dayCounter
                                val calDay = currentMonthCalendar.clone() as Calendar
                                calDay.set(Calendar.DAY_OF_MONTH, currentDayNum)
                                val dateStr = dayFormat.format(calDay.time)

                                val isSelected = dateStr == selectedDate
                                val hasLog = datesWithLogs.contains(dateStr)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else if (hasLog) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            onDateSelected(dateStr)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$currentDayNum",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected || hasLog) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                hasLog -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        if (hasLog && !isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                }
                                dayCounter++
                            }
                        }
                    }
                }
            }
        }
    }
}
