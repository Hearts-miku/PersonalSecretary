package com.example.data.utils

enum class DiffType {
    UNCHANGED,
    ADDED,
    REMOVED
}

data class DiffLine(
    val lineNum: Int = 0,
    val text: String,
    val type: DiffType
)

object DiffUtils {

    private const val MAX_DP_CELLS = 250_000 // 500x500 safety bound to prevent OOM

    /**
     * Computes line-by-line difference between oldText and newText using LCS algorithm
     * with common prefix/suffix trimming (P2-12) to prevent OOM on long documents.
     */
    fun computeDiff(oldText: String, newText: String): List<DiffLine> {
        val oldLines = if (oldText.isBlank()) emptyList() else oldText.split("\n")
        val newLines = if (newText.isBlank()) emptyList() else newText.split("\n")

        val m = oldLines.size
        val n = newLines.size

        if (m == 0 && n == 0) return emptyList()
        if (m == 0) {
            return newLines.mapIndexed { idx, text -> DiffLine(lineNum = idx + 1, text = text, type = DiffType.ADDED) }
        }
        if (n == 0) {
            return oldLines.mapIndexed { idx, text -> DiffLine(lineNum = idx + 1, text = text, type = DiffType.REMOVED) }
        }

        // 1. Trim common prefix
        var start = 0
        while (start < m && start < n && oldLines[start] == newLines[start]) {
            start++
        }

        // 2. Trim common suffix
        var oldEnd = m - 1
        var newEnd = n - 1
        while (oldEnd >= start && newEnd >= start && oldLines[oldEnd] == newLines[newEnd]) {
            oldEnd--
            newEnd--
        }

        val prefixLines = (0 until start).map { DiffLine(text = oldLines[it], type = DiffType.UNCHANGED) }
        val suffixLines = ((oldEnd + 1) until m).map { DiffLine(text = oldLines[it], type = DiffType.UNCHANGED) }

        val subOld = if (start <= oldEnd) oldLines.subList(start, oldEnd + 1) else emptyList()
        val subNew = if (start <= newEnd) newLines.subList(start, newEnd + 1) else emptyList()

        val middleLines = mutableListOf<DiffLine>()

        if (subOld.isNotEmpty() && subNew.isNotEmpty()) {
            val subM = subOld.size
            val subN = subNew.size

            if (subM.toLong() * subN.toLong() > MAX_DP_CELLS) {
                // Exceeds safe DP size: fallback to block replacement to prevent OOM
                subOld.forEach { middleLines.add(DiffLine(text = it, type = DiffType.REMOVED)) }
                subNew.forEach { middleLines.add(DiffLine(text = it, type = DiffType.ADDED)) }
            } else {
                val dp = Array(subM + 1) { IntArray(subN + 1) }
                for (i in 0 until subM) {
                    for (j in 0 until subN) {
                        if (subOld[i] == subNew[j]) {
                            dp[i + 1][j + 1] = dp[i][j] + 1
                        } else {
                            dp[i + 1][j + 1] = maxOf(dp[i + 1][j], dp[i][j + 1])
                        }
                    }
                }

                var i = subM
                var j = subN
                val tempResult = mutableListOf<DiffLine>()
                while (i > 0 || j > 0) {
                    if (i > 0 && j > 0 && subOld[i - 1] == subNew[j - 1]) {
                        tempResult.add(DiffLine(text = subOld[i - 1], type = DiffType.UNCHANGED))
                        i--
                        j--
                    } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                        tempResult.add(DiffLine(text = subNew[j - 1], type = DiffType.ADDED))
                        j--
                    } else if (i > 0 && (j == 0 || dp[i][j - 1] < dp[i - 1][j])) {
                        tempResult.add(DiffLine(text = subOld[i - 1], type = DiffType.REMOVED))
                        i--
                    }
                }
                middleLines.addAll(tempResult.reversed())
            }
        } else if (subOld.isNotEmpty()) {
            subOld.forEach { middleLines.add(DiffLine(text = it, type = DiffType.REMOVED)) }
        } else if (subNew.isNotEmpty()) {
            subNew.forEach { middleLines.add(DiffLine(text = it, type = DiffType.ADDED)) }
        }

        val all = prefixLines + middleLines + suffixLines
        return all.mapIndexed { idx, line ->
            line.copy(lineNum = idx + 1)
        }
    }
}
