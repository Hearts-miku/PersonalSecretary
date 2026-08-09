package com.example.data.worker

import android.content.Context
import android.util.Log
import com.example.data.repository.WorkLogRepository

/**
 * Manages daily scheduled summary tasks.
 */
object AutoSummaryScheduler {

    private const val TAG = "AutoSummaryScheduler"

    /**
     * Executes the daily AI consolidation job directly.
     * Callable by background receiver, worker, or manual trigger button.
     */
    suspend fun executeDailyConsolidation(
        context: Context,
        onProgress: (String) -> Unit = {}
    ): Result<String> {
        val repo = WorkLogRepository(context)
        Log.d(TAG, "Starting daily auto summary consolidation task...")
        return repo.triggerAISummarize(repo.getTodayString(), onProgress)
    }
}
