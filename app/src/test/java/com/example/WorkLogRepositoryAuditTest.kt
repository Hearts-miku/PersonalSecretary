package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiRepository
import com.example.data.ai.AiRepository.ExtractedTodo
import com.example.data.local.AppDatabase
import com.example.data.local.UserSettingsEntity
import com.example.data.repository.WorkLogRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkLogRepositoryAuditTest {

    private lateinit var context: Context
    private lateinit var fakeAi: FakeAiRepository
    private lateinit var repository: WorkLogRepository

    class FakeAiRepository : AiRepository() {
        var onSummarizeHook: (suspend () -> Unit)? = null
        var lastReportPromptReceived: String = ""

        override suspend fun summarizeDailyNotes(
            date: String,
            rawNotes: String,
            settings: UserSettingsEntity?
        ): Result<String> {
            onSummarizeHook?.invoke()
            val summary = if (rawNotes.contains("模块A") && rawNotes.contains("用例B")) {
                "【日报总结】完成了用户认证模块A，并补录了集成测试用例B"
            } else if (rawNotes.contains("模块A")) {
                "【日报总结】完成了用户认证模块A"
            } else {
                "【日报总结】$rawNotes"
            }
            return Result.success(summary)
        }

        override suspend fun extractTodos(
            date: String,
            rawNotes: String,
            settings: UserSettingsEntity?
        ): Result<List<ExtractedTodo>> {
            val list = mutableListOf<ExtractedTodo>()
            if (rawNotes.contains("模块A")) {
                list.add(ExtractedTodo("【认证服务】完成模块A联调", "完成核心接口单测与联调", "HIGH", "认证服务"))
            }
            if (rawNotes.contains("用例B")) {
                list.add(ExtractedTodo("【测试工程】执行集成测试用例B", "补充端到端测试用例", "MEDIUM", "测试工程"))
            }
            return Result.success(list)
        }

        override suspend fun generatePeriodicReport(
            startDate: String,
            endDate: String,
            reportType: String,
            logsContext: String,
            settings: UserSettingsEntity?
        ): Result<String> {
            lastReportPromptReceived = logsContext
            return Result.success("# 工作总结报告\n基于选定区间生成完毕。")
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeAi = FakeAiRepository()
        repository = WorkLogRepository(context, fakeAi)
        runBlocking {
            repository.clearAllData()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            repository.clearAllData()
        }
    }

    /**
     * N-39: A -> summarize -> append B -> summarize again.
     * Verifies:
     * 1. A's raw facts are permanently preserved in Room and not wiped to empty.
     * 2. After appending B, rawNotes contains A + B.
     * 3. Re-summarizing includes both A + B in the final summary.
     * 4. A's pending todo is not lost or wiped when B is summarized.
     */
    @Test
    fun testN39_rawFactsPreservedAndResummarizeIncludesAllFactsAndPreservesTodos() = runBlocking {
        val targetDate = "2026-10-10"

        // 1. Enter Note A
        repository.addRawNote("完成了用户认证模块A", targetDate)
        val db = AppDatabase.getInstance(context)
        val logDao = db.dailyWorkLogDao()
        val todoDao = db.todoItemDao()

        var log = logDao.getLogByDate(targetDate)
        assertNotNull(log)
        assertTrue(log!!.rawNotes.contains("模块A"))
        assertFalse(log.isSummarized)

        // 2. Trigger First AI Summary for A
        val res1 = repository.triggerAISummarize(targetDate)
        assertTrue(res1.isSuccess)

        log = logDao.getLogByDate(targetDate)
        assertNotNull(log)
        // N-39 CRITICAL: rawNotes must NEVER be wiped to empty!
        assertTrue("Raw notes must retain Note A facts after summary", log!!.rawNotes.contains("模块A"))
        assertTrue("Summary must reflect Note A", log.summaryMarkdown.contains("模块A"))
        assertTrue(log.isSummarized)

        val todosAfterA = todoDao.getAllTodosOnce()
        assertEquals(1, todosAfterA.size)
        assertTrue(todosAfterA.first().title.contains("模块A"))

        // 3. User appends Note B later that day (补录 B)
        repository.addRawNote("补录了集成测试用例B", targetDate)

        log = logDao.getLogByDate(targetDate)
        assertNotNull(log)
        assertTrue("rawNotes must contain both A and B", log!!.rawNotes.contains("模块A") && log.rawNotes.contains("用例B"))
        assertFalse("isSummarized must be false because new note B is pending summary", log.isSummarized)

        // A's todo must still exist
        val todosBeforeResummarize = todoDao.getAllTodosOnce()
        assertEquals(1, todosBeforeResummarize.size)

        // 4. Trigger Second AI Summary
        val res2 = repository.triggerAISummarize(targetDate)
        assertTrue(res2.isSuccess)

        log = logDao.getLogByDate(targetDate)
        assertNotNull(log)
        // Raw notes must still contain both A and B
        assertTrue(log!!.rawNotes.contains("模块A") && log.rawNotes.contains("用例B"))
        // Summary must cover both A and B
        assertTrue("Summary must cover both A and B", log.summaryMarkdown.contains("模块A") && log.summaryMarkdown.contains("用例B"))
        assertTrue(log.isSummarized)

        // Todos: A's todo must NOT be lost, and B's todo must be added!
        val todosAfterB = todoDao.getAllTodosOnce()
        assertEquals("Both A and B todos must be present", 2, todosAfterB.size)
        assertTrue(todosAfterB.any { it.title.contains("模块A") })
        assertTrue(todosAfterB.any { it.title.contains("用例B") })
    }

    /**
     * N-37: Concurrently appending Note C during AI summarization window.
     * Note C must be preserved in both Room rawNotes and raw_notes.md, and isSummarized remains false.
     */
    @Test
    fun testN37_concurrentNoteAppendedDuringAiSummarizeIsPreserved() = runBlocking {
        val targetDate = "2026-10-11"
        repository.addRawNote("完成了用户认证模块A", targetDate)

        // Simulate concurrent addition of note C during AI execution
        fakeAi.onSummarizeHook = {
            repository.addRawNote("并发添加的紧急修复C", targetDate)
        }

        val res = repository.triggerAISummarize(targetDate)
        assertTrue(res.isSuccess)

        val db = AppDatabase.getInstance(context)
        val log = db.dailyWorkLogDao().getLogByDate(targetDate)
        assertNotNull(log)
        assertTrue("Concurrent Note C must be preserved in rawNotes", log!!.rawNotes.contains("紧急修复C"))
        assertFalse("isSummarized must be false because Note C was added concurrently", log.isSummarized)

        val rawFileText = repository.markdownManager.getRawNotesContent()
        assertTrue("raw_notes.md must preserve concurrently added Note C", rawFileText.contains("紧急修复C"))
    }

    /**
     * N-36: Calling clearAllData cancels ongoing writes and ensures no post-clear data resurrects.
     */
    @Test
    fun testN36_clearAllDataInvalidatesInFlightCommit() = runBlocking {
        val targetDate = "2026-10-12"
        repository.addRawNote("任务A", targetDate)

        fakeAi.onSummarizeHook = {
            repository.clearAllData()
        }

        repository.triggerAISummarize(targetDate)

        val db = AppDatabase.getInstance(context)
        val allLogs = db.dailyWorkLogDao().getAllLogsOnce()
        assertTrue("Database must remain completely empty after clearAllData", allLogs.isEmpty())
        assertEquals(0, repository.markdownManager.listAllDailySummaryFiles().size)
    }

    /**
     * N-32: Test report generation with multiple days preserves supplementary notes
     * and generates explicit coverage notice header.
     */
    @Test
    fun testN32_generateReportIncludesSupplementaryNotesAndExplicitCoverage() = runBlocking {
        val db = AppDatabase.getInstance(context)
        for (i in 1..5) {
            val date = String.format("2026-10-%02d", i)
            repository.addRawNote("模块$i 事实记录", date)
        }

        val res = repository.generateReport("2026-10-01", "2026-10-05", "周报")
        assertTrue(res.isSuccess)

        val prompt = fakeAi.lastReportPromptReceived
        assertTrue("Must include coverage header", prompt.contains("【覆盖范围提示：本报告完整覆盖区间内全部 5 天工作记录】"))
        for (i in 1..5) {
            assertTrue("Must include day $i facts", prompt.contains("模块$i 事实记录"))
        }
    }
}
