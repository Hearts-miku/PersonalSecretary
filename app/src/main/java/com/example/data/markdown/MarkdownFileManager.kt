package com.example.data.markdown

import android.content.Context
import java.io.File

/**
 * Manages physical Markdown (.md) documents on disk.
 * Structures files into:
 * - /markdown_records/worklogs/YYYY-MM-DD.md (Type 1: Daily summary docs)
 * - /markdown_records/temp/raw_notes.md (Type 2: Raw uncleared user input doc)
 * - /markdown_records/user/career_profile.md (Type 3: User career & skill profile doc)
 * - /markdown_records/reports/ (Reports doc archive)
 */
class MarkdownFileManager(private val context: Context) {

    private val baseDir: File
        get() = File(context.filesDir, "markdown_records").apply {
            if (!exists()) mkdirs()
        }

    private val worklogsDir: File
        get() = File(baseDir, "worklogs").apply {
            if (!exists()) mkdirs()
        }

    private val tempDir: File
        get() = File(baseDir, "temp").apply {
            if (!exists()) mkdirs()
        }

    private val userDir: File
        get() = File(baseDir, "user").apply {
            if (!exists()) mkdirs()
        }

    private val reportsDir: File
        get() = File(baseDir, "reports").apply {
            if (!exists()) mkdirs()
        }

    private val rawNotesFile: File
        get() = File(tempDir, "raw_notes.md")

    private val careerProfileFile: File
        get() = File(userDir, "career_profile.md")

    private val resumeFile: File
        get() = File(userDir, "generated_resume.md")

    private val workExperiencesFile: File
        get() = File(userDir, "work_experiences.md")
        
    private val projectExperiencesFile: File
        get() = File(userDir, "project_experiences.md")

    fun writeGeneratedResume(markdownContent: String) {
        val file = resumeFile
        file.parentFile?.mkdirs()
        file.writeText(markdownContent)
    }

    fun getGeneratedResumeContent(): String {
        return if (resumeFile.exists()) resumeFile.readText() else ""
    }
    
    fun writeWorkExperiences(markdownContent: String) {
        val file = workExperiencesFile
        file.parentFile?.mkdirs()
        file.writeText(markdownContent)
    }
    
    fun getWorkExperiencesContent(): String {
        return if (workExperiencesFile.exists()) workExperiencesFile.readText() else ""
    }
    
    fun writeProjectExperiences(markdownContent: String) {
        val file = projectExperiencesFile
        file.parentFile?.mkdirs()
        file.writeText(markdownContent)
    }
    
    fun getProjectExperiencesContent(): String {
        return if (projectExperiencesFile.exists()) projectExperiencesFile.readText() else ""
    }

    /**
     * Sanitizes report filename while fully preserving legal Unicode / Chinese characters,
     * removing path traversal, slashes, and characters illegal on file systems (N-29).
     */
    fun sanitizeReportFileName(filename: String): String {
        val baseName = filename.trim().removeSuffix(".md").trim()
        val clean = baseName.replace(Regex("""[/\\:*?"<>|\x00-\x1F]"""), "_")
            .replace("..", "_")
            .trim()
            .ifBlank { "report_${System.currentTimeMillis()}" }
        return "$clean.md"
    }

    fun writeReport(filename: String, markdownContent: String) {
        val safeName = sanitizeReportFileName(filename)
        val file = File(reportsDir, safeName)
        file.parentFile?.mkdirs()
        file.writeText(markdownContent)
    }

    fun getReport(filename: String): String? {
        val safeName = sanitizeReportFileName(filename)
        val file = File(reportsDir, safeName)
        return if (file.exists()) file.readText() else null
    }

    fun listReportFiles(): List<File> {
        return reportsDir.listFiles { _, name -> name.endsWith(".md") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun deleteReport(filename: String): Boolean {
        val safeName = sanitizeReportFileName(filename)
        val file = File(reportsDir, safeName)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    data class RawNoteEntry(
        val id: String,
        val dateStr: String,
        val content: String
    )

    private val rawNotesLock = Any()

    // --- Type 2: Raw Notes Document (temp/raw_notes.md) ---

    fun appendRawNote(
        text: String,
        dateStr: String,
        entryId: String = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
    ): String = synchronized(rawNotesLock) {
        val file = rawNotesFile
        file.parentFile?.mkdirs()
        val entry = "\n\n### [$dateStr] [id:$entryId] 原始输入\n$text\n"
        file.appendText(entry)
        entryId
    }

    fun getRawNotesContent(): String = synchronized(rawNotesLock) {
        val file = rawNotesFile
        if (file.exists()) file.readText() else ""
    }

    fun getRawNoteEntries(dateStr: String? = null): List<RawNoteEntry> = synchronized(rawNotesLock) {
        val file = rawNotesFile
        if (!file.exists()) return emptyList()
        val text = file.readText()
        val blocks = text.split("\n\n### [")
        val entries = mutableListOf<RawNoteEntry>()
        val idRegex = Regex("""\[id:([^\]]+)\]""")

        for (block in blocks.drop(1)) {
            val headerEnd = block.indexOf("]")
            if (headerEnd == -1) continue
            val d = block.substring(0, headerEnd).trim()
            if (dateStr != null && d != dateStr) continue

            val afterDate = block.substring(headerEnd + 1)
            val idMatch = idRegex.find(afterDate)
            val id = idMatch?.groupValues?.get(1).orEmpty()

            val content = if (afterDate.contains("原始输入\n")) {
                afterDate.substringAfter("原始输入\n").trimEnd()
            } else {
                afterDate.substringAfter("\n").trimEnd()
            }
            if (content.isNotBlank()) {
                entries.add(RawNoteEntry(id = id, dateStr = d, content = content))
            }
        }
        entries
    }

    fun removeRawNotesByIds(ids: Set<String>) = synchronized(rawNotesLock) {
        if (ids.isEmpty()) return@synchronized
        val file = rawNotesFile
        if (!file.exists()) return@synchronized
        val text = file.readText()
        val blocks = text.split("\n\n### [")
        val header = blocks.firstOrNull() ?: "# 临时原始输入文档\n> AI总结后将自动清理对应日期的内容。\n\n"
        val idRegex = Regex("""\[id:([^\]]+)\]""")

        val remaining = blocks.drop(1).filterNot { block ->
            val headerEnd = block.indexOf("]")
            if (headerEnd == -1) return@filterNot false
            val afterDate = block.substring(headerEnd + 1)
            val idMatch = idRegex.find(afterDate)
            val id = idMatch?.groupValues?.get(1)
            id != null && ids.contains(id)
        }

        val newContent = if (remaining.isEmpty()) {
            "# 临时原始输入文档\n> AI总结后将自动清理对应日期的内容。\n\n"
        } else {
            if (header.endsWith("\n\n")) header + "### [" + remaining.joinToString("\n\n### [")
            else header + "\n\n### [" + remaining.joinToString("\n\n### [")
        }
        file.parentFile?.mkdirs()
        file.writeText(newContent)
    }

    /**
     * Removes raw notes for a date.
     * If [specificContentToRemove] is provided (e.g. from a specific summary snapshot),
     * only entries exactly matching those target items are removed, preserving any concurrently added
     * notes, substring variations (e.g. 'deploy module A' vs 'module A'), multiline notes, and unsummarized entries (N-37).
     */
    fun removeRawNotesForDate(dateStr: String, specificContentToRemove: String? = null) = synchronized(rawNotesLock) {
        val file = rawNotesFile
        if (!file.exists()) return@synchronized
        val text = file.readText()
        if (!text.contains(dateStr)) return@synchronized

        val blocks = text.split("\n\n### [")
        val header = blocks.firstOrNull() ?: "# 临时原始输入文档\n> AI总结后将自动清理对应日期的内容。\n\n"

        // Prepare list of exact normalized items to remove, preserving multiline item boundaries
        val targetsToRemove: MutableList<String>? = specificContentToRemove?.let { content ->
            val rawItems = if (content.contains("•") || content.trimStart().startsWith("-") || content.trimStart().startsWith("*")) {
                content.split(Regex("""(?m)^[•\-\*]\s*"""))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            } else {
                listOf(content.trim())
            }
            rawItems.toMutableList()
        }

        val remaining = blocks.drop(1).filterNot { block ->
            val headerEnd = block.indexOf("]")
            if (headerEnd == -1) return@filterNot false
            val d = block.substring(0, headerEnd).trim()
            if (d != dateStr) {
                false
            } else {
                if (targetsToRemove == null) {
                    true
                } else {
                    val afterDate = block.substring(headerEnd + 1)
                    val blockContent = if (afterDate.contains("原始输入\n")) {
                        afterDate.substringAfter("原始输入\n").trim()
                    } else {
                        afterDate.substringAfter("\n").trim()
                    }
                    val normalizedBlock = blockContent.lines()
                        .map { it.trim().removePrefix("•").removePrefix("-").removePrefix("*").trim() }
                        .filter { it.isNotBlank() }
                        .joinToString("\n")

                    // Match exact multiline entry or normalized block, consuming one instance to preserve duplicates
                    val matchIdx = targetsToRemove.indexOfFirst { target ->
                        val normalizedTarget = target.lines()
                            .map { it.trim().removePrefix("•").removePrefix("-").removePrefix("*").trim() }
                            .filter { it.isNotBlank() }
                            .joinToString("\n")
                        normalizedBlock == target || blockContent == target || normalizedBlock == normalizedTarget
                    }
                    if (matchIdx != -1) {
                        targetsToRemove.removeAt(matchIdx)
                        true
                    } else {
                        false
                    }
                }
            }
        }

        val newContent = if (remaining.isEmpty()) {
            "# 临时原始输入文档\n> AI总结后将自动清理对应日期的内容。\n\n"
        } else {
            if (header.endsWith("\n\n")) header + "### [" + remaining.joinToString("\n\n### [")
            else header + "\n\n### [" + remaining.joinToString("\n\n### [")
        }
        file.parentFile?.mkdirs()
        file.writeText(newContent)
    }

    // --- Type 1: Daily Work Log Summaries (worklogs/YYYY-MM-DD.md) ---

    fun writeDailySummary(dateStr: String, markdownContent: String) {
        if (!dateStr.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) return
        val file = File(worklogsDir, "$dateStr.md")
        file.parentFile?.mkdirs()
        file.writeText(markdownContent)
    }

    fun getDailySummary(dateStr: String): String? {
        if (!dateStr.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) return null
        val file = File(worklogsDir, "$dateStr.md")
        return if (file.exists()) file.readText() else null
    }

    fun listAllDailySummaryFiles(): List<File> {
        return worklogsDir.listFiles { _, name -> name.endsWith(".md") }
            ?.sortedByDescending { it.name } ?: emptyList()
    }

    // --- Type 3: User Career Profile Document (user/career_profile.md) ---

    fun writeCareerProfile(markdownContent: String) {
        val file = careerProfileFile
        file.parentFile?.mkdirs()
        file.writeText(markdownContent)
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

    /**
     * Clear all markdown records while restoring complete directory structure (N-30).
     */
    fun clearAllData(): Boolean = synchronized(rawNotesLock) {
        val deleted = if (baseDir.exists()) baseDir.deleteRecursively() else true
        val m1 = baseDir.mkdirs() || baseDir.exists()
        val m2 = worklogsDir.mkdirs() || worklogsDir.exists()
        val m3 = tempDir.mkdirs() || tempDir.exists()
        val m4 = userDir.mkdirs() || userDir.exists()
        val m5 = reportsDir.mkdirs() || reportsDir.exists()
        deleted && m1 && m2 && m3 && m4 && m5
    }

    /**
     * Export all data to a zip file with path traversal protection
     */
    fun exportAllDataToZip(outputStream: java.io.OutputStream) {
        java.util.zip.ZipOutputStream(outputStream).use { zos ->
            baseDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relativePath = file.relativeTo(baseDir).path.replace('\\', '/')
                if (!relativePath.contains("..") && !relativePath.startsWith("/")) {
                    val zipEntry = java.util.zip.ZipEntry(relativePath)
                    zos.putNextEntry(zipEntry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    fun saveResumeStyle(style: String) {
        try {
            val f = File(userDir, "resume_style.txt")
            f.parentFile?.mkdirs()
            f.writeText(style)
        } catch (e: Exception) {
            // Ignore write error
        }
    }

    fun getSavedResumeStyle(): String {
        return try {
            val f = File(userDir, "resume_style.txt")
            if (f.exists()) f.readText().trim().ifBlank { "Modern" } else "Modern"
        } catch (e: Exception) {
            "Modern"
        }
    }
}
