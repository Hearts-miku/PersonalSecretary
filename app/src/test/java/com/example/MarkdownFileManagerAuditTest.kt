package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.markdown.MarkdownFileManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
class MarkdownFileManagerAuditTest {

    private lateinit var context: Context
    private lateinit var manager: MarkdownFileManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = MarkdownFileManager(context)
        manager.clearAllData()
    }

    /**
     * N-29: Chinese report filenames normalization collision verification.
     * Weekly and monthly reports in the same date range must not collide or overwrite each other.
     * Different Chinese custom titles must also be distinct.
     */
    @Test
    fun testN29_chineseReportFileNamesDoNotCollide() {
        val weeklyTitle = "2026-10-01_至_2026-10-07_周报.md"
        val monthlyTitle = "2026-10-01_至_2026-10-07_月报.md"
        val weeklyContent = "# 周报内容\n推进了模块A"
        val monthlyContent = "# 月报内容\n完成了全月指标"

        manager.writeReport(weeklyTitle, weeklyContent)
        manager.writeReport(monthlyTitle, monthlyContent)

        val reportFiles = manager.listReportFiles()
        assertEquals("Both reports must exist without colliding", 2, reportFiles.size)

        val readWeekly = manager.getReport(weeklyTitle)
        val readMonthly = manager.getReport(monthlyTitle)

        assertEquals(weeklyContent, readWeekly)
        assertEquals(monthlyContent, readMonthly)
        assertNotEquals(readWeekly, readMonthly)

        // Custom Chinese titles
        val custom1 = "产品上线发布总结.md"
        val custom2 = "架构重构技术复盘.md"
        manager.writeReport(custom1, "上线总结")
        manager.writeReport(custom2, "架构复盘")

        assertEquals("上线总结", manager.getReport(custom1))
        assertEquals("架构复盘", manager.getReport(custom2))
        assertEquals(4, manager.listReportFiles().size)
    }

    /**
     * N-30: Clearing all data must restore directory structures so subsequent writes
     * on the same instance succeed without FileNotFoundException.
     */
    @Test
    fun testN30_writesSucceedImmediatelyAfterClearAllData() {
        // Pre-populate
        manager.writeDailySummary("2026-10-01", "Initial summary")
        manager.appendRawNote("Initial note", "2026-10-01")
        manager.writeCareerProfile("Initial profile")
        manager.writeReport("Test.md", "Initial report")

        // Clear all data
        manager.clearAllData()

        // Immediate subsequent writes on same manager instance must all succeed
        manager.writeDailySummary("2026-10-02", "New daily summary")
        manager.appendRawNote("New raw note", "2026-10-02")
        manager.writeCareerProfile("New profile")
        manager.writeWorkExperiences("New work experiences")
        manager.writeProjectExperiences("New project experiences")
        manager.writeReport("2026-10-02_至_2026-10-08_周报.md", "New report")

        assertEquals("New daily summary", manager.getDailySummary("2026-10-02"))
        assertTrue(manager.getRawNotesContent().contains("New raw note"))
        assertEquals("New work experiences", manager.getWorkExperiencesContent())
        assertEquals("New project experiences", manager.getProjectExperiencesContent())
        assertEquals("New report", manager.getReport("2026-10-02_至_2026-10-08_周报.md"))
    }

    /**
     * N-37: Concurrent note removal coordination.
     * When removing raw notes for a date with a specific content snapshot,
     * newly appended notes for that date must be preserved.
     */
    @Test
    fun testN37_concurrentRawNotesArePreservedOnSummaryRemoval() {
        val date = "2026-10-05"
        val noteA = "完成了认证模块"
        val noteB = "补录了测试用例"

        // 1. Initial note A
        manager.appendRawNote(noteA, date)

        // 2. Concurrently append note B before summary finishes
        manager.appendRawNote(noteB, date)

        val allRaw = manager.getRawNotesContent()
        assertTrue(allRaw.contains(noteA))
        assertTrue(allRaw.contains(noteB))

        // 3. Summary finishes for noteA, remove only noteA's entry
        manager.removeRawNotesForDate(date, noteA)

        val updatedRaw = manager.getRawNotesContent()
        assertFalse("Summarized note A must be removed", updatedRaw.contains(noteA))
        assertTrue("Concurrent note B must be preserved", updatedRaw.contains(noteB))
    }

    /**
     * N-37: Verify that substring notes are not erroneously deleted when a superstring note is removed.
     * Specifically: appending 'deploy module A' then 'module A', and removing '• deploy module A',
     * must preserve 'module A'.
     */
    @Test
    fun testN37_substringNoteIsPreservedWhenSuperstringIsRemoved() {
        val date = "2026-10-06"
        manager.appendRawNote("deploy module A", date)
        manager.appendRawNote("module A", date)

        manager.removeRawNotesForDate(date, "• deploy module A")

        val raw = manager.getRawNotesContent()
        assertFalse("deploy module A must be removed", raw.contains("deploy module A"))
        assertTrue("module A must be preserved", raw.contains("module A"))
    }

    /**
     * N-37: Verify duplicate note handling. Removing 1 occurrence of an identical note
     * must preserve the 2nd occurrence.
     */
    @Test
    fun testN37_duplicateNoteIsPreservedWhenOneOccurrenceRemoved() {
        val date = "2026-10-07"
        manager.appendRawNote("fixed bug 123", date)
        manager.appendRawNote("fixed bug 123", date)

        manager.removeRawNotesForDate(date, "fixed bug 123")

        val raw = manager.getRawNotesContent()
        assertTrue("Second occurrence of identical note must be preserved", raw.contains("fixed bug 123"))
    }

    /**
     * Test deleteReport functionality:
     * - Deleting existing report removes it from disk and listReportFiles
     * - Deleting non-existent report returns false
     * - Deleting one report does not affect other reports
     */
    @Test
    fun testDeleteReport_removesFileSuccessfully() {
        val title1 = "2026-10-01_至_2026-10-07_周报.md"
        val title2 = "2026-10-01_至_2026-10-31_月报.md"
        manager.writeReport(title1, "# 周报")
        manager.writeReport(title2, "# 月报")

        assertEquals(2, manager.listReportFiles().size)
        assertNotNull(manager.getReport(title1))

        val deleteSuccess = manager.deleteReport(title1)
        assertTrue("deleteReport must return true for existing file", deleteSuccess)
        assertNull("Deleted report must not be readable", manager.getReport(title1))

        val remainingFiles = manager.listReportFiles()
        assertEquals(1, remainingFiles.size)
        assertEquals("月报 must remain intact", "2026-10-01_至_2026-10-31_月报.md", remainingFiles.first().name)

        val deleteAgain = manager.deleteReport(title1)
        assertFalse("Deleting non-existent report must return false", deleteAgain)
    }

    /**
     * N-37: Verify that multiline raw notes are properly cleaned up and not permanently retained.
     */
    @Test
    fun testN37_multilineRawNoteIsRemovedCorrectly() {
        val date = "2026-10-08"
        val multilineNote = "line one\nline two"
        manager.appendRawNote(multilineNote, date)

        val before = manager.getRawNotesContent()
        assertTrue("Must contain multiline content", before.contains("line one\nline two"))

        manager.removeRawNotesForDate(date, "• line one\nline two")

        val after = manager.getRawNotesContent()
        assertFalse("Multiline raw note must be cleanly removed", after.contains("line one"))
        assertFalse("Multiline raw note second line must also be removed", after.contains("line two"))
    }

    /**
     * N-37: Verify ID-based removal cleanly preserves duplicate text added across batches.
     */
    @Test
    fun testN37_rawNoteEntriesTrackingByIdsPreservesCrossBatchDuplicates() {
        val date = "2026-10-09"
        // Batch 1: note A
        val idA = manager.appendRawNote("开发了登录页面", date)
        val batch1Entries = manager.getRawNoteEntries(date)
        assertEquals(1, batch1Entries.size)
        assertEquals(idA, batch1Entries.first().id)

        // User concurrently adds note B and another identical note A before summary finishes
        val idB = manager.appendRawNote("补录了用户设置", date)
        val idA2 = manager.appendRawNote("开发了登录页面", date)

        // Summary for Batch 1 completes, removing only batch 1's IDs
        manager.removeRawNotesByIds(setOf(idA))

        val remainingEntries = manager.getRawNoteEntries(date)
        assertEquals(2, remainingEntries.size)
        assertEquals(listOf(idB, idA2), remainingEntries.map { it.id })

        val content = manager.getRawNotesContent()
        assertTrue("Must preserve note B", content.contains("补录了用户设置"))
        assertTrue("Must preserve new identical note A from batch 2", content.contains("开发了登录页面"))
    }
}
