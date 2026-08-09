package com.example.data.markdown

import android.content.Context
import java.io.File

/**
 * Manages physical Markdown (.md) documents on disk.
 * Structures files into:
 * - /markdown_records/worklogs/YYYY-MM-DD.md (Type 1: Daily summary docs)
 * - /markdown_records/temp/raw_notes.md (Type 2: Raw uncleared user input doc)
 * - /markdown_records/user/career_profile.md (Type 3: User career & skill profile doc)
 */
class MarkdownFileManager(private val context: Context) {

    private val baseDir: File by lazy {
        File(context.filesDir, "markdown_records").apply {
            if (!exists()) mkdirs()
        }
    }

    private val worklogsDir: File by lazy {
        File(baseDir, "worklogs").apply {
            if (!exists()) mkdirs()
        }
    }

    private val tempDir: File by lazy {
        File(baseDir, "temp").apply {
            if (!exists()) mkdirs()
        }
    }

    private val userDir: File by lazy {
        File(baseDir, "user").apply {
            if (!exists()) mkdirs()
        }
    }

    private val rawNotesFile: File
        get() = File(tempDir, "raw_notes.md")

    private val careerProfileFile: File
        get() = File(userDir, "career_profile.md")

    // --- Type 2: Raw Notes Document (temp/raw_notes.md) ---

    fun appendRawNote(text: String, dateStr: String) {
        val file = rawNotesFile
        val entry = "\n\n### [$dateStr] 原始输入\n$text\n"
        file.appendText(entry)
    }

    fun getRawNotesContent(): String {
        val file = rawNotesFile
        return if (file.exists()) file.readText() else ""
    }

    fun clearRawNotesContent() {
        val file = rawNotesFile
        if (file.exists()) {
            file.writeText("# 临时原始输入文档\n> AI总结后将自动清空此文件内容。\n\n")
        }
    }

    // --- Type 1: Daily Work Log Summaries (worklogs/YYYY-MM-DD.md) ---

    fun writeDailySummary(dateStr: String, markdownContent: String) {
        val file = File(worklogsDir, "$dateStr.md")
        file.writeText(markdownContent)
    }

    fun getDailySummary(dateStr: String): String? {
        val file = File(worklogsDir, "$dateStr.md")
        return if (file.exists()) file.readText() else null
    }

    fun listAllDailySummaryFiles(): List<File> {
        return worklogsDir.listFiles { _, name -> name.endsWith(".md") }
            ?.sortedByDescending { it.name } ?: emptyList()
    }

    // --- Type 3: User Career Profile Document (user/career_profile.md) ---

    fun writeCareerProfile(markdownContent: String) {
        careerProfileFile.writeText(markdownContent)
    }

    fun getCareerProfileContent(): String {
        return if (careerProfileFile.exists()) {
            careerProfileFile.readText()
        } else {
            val defaultTemplate = """
                # 用户职业履历与技能文档
                
                > 本文档由AI根据您日常输入的工作日志自动总结与更新。
                > 隐私信息（如姓名、电话、邮箱）在简历导出时会自动用占位符替代。
                
                ## 核心专业技能
                - 待AI提取...
                
                ## 工作经历与项目成果
                - 待AI提取...
                
                ## 职业成就与里程碑
                - 待AI提取...
            """.trimIndent()
            writeCareerProfile(defaultTemplate)
            defaultTemplate
        }
    }

    /**
     * Get summary of all Markdown documents info for settings / inspect screen
     */
    fun getMarkdownDirectoryInfo(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        result["raw_notes.md"] = "${rawNotesFile.length()} bytes"
        result["career_profile.md"] = "${careerProfileFile.length()} bytes"
        val logFiles = listAllDailySummaryFiles()
        result["worklogs_count"] = "${logFiles.size} 天总结文档"
        return result
    }
}
