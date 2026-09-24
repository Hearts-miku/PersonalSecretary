package com.example.data.utils

import com.example.data.local.DailyWorkLogEntity

object WorkLogFilterUtils {
    /**
     * 筛选出未完成 AI 整理且含有原始笔记的工作日志（纯函数，供业务与单元测试复用）
     */
    fun filterUnsummarizedLogs(logs: List<DailyWorkLogEntity>): List<DailyWorkLogEntity> {
        return logs.filter { !it.isSummarized && it.rawNotes.isNotBlank() }
    }

    /**
     * 针对选择的日期进行边界钳制：不能超过今天（纯函数，供业务与单元测试复用）
     */
    fun clampDateNotAfterToday(dateStr: String, todayStr: String): String {
        return if (dateStr > todayStr) todayStr else dateStr
    }
}
