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

    /**
     * Computes line-by-line difference between oldText and newText using LCS algorithm.
     */
    fun computeDiff(oldText: String, newText: String): List<DiffLine> {
        val oldLines = if (oldText.isBlank()) emptyList() else oldText.split("\n")
        val newLines = if (newText.isBlank()) emptyList() else newText.split("\n")

        val m = oldLines.size
        val n = newLines.size

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0 until m) {
            for (j in 0 until n) {
                if (oldLines[i] == newLines[j]) {
                    dp[i + 1][j + 1] = dp[i][j] + 1
                } else {
                    dp[i + 1][j + 1] = maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        var i = m
        var j = n

        val tempResult = mutableListOf<DiffLine>()

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1] == newLines[j - 1]) {
                tempResult.add(DiffLine(text = oldLines[i - 1], type = DiffType.UNCHANGED))
                i--
                j--
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                tempResult.add(DiffLine(text = newLines[j - 1], type = DiffType.ADDED))
                j--
            } else if (i > 0 && (j == 0 || dp[i][j - 1] < dp[i - 1][j])) {
                tempResult.add(DiffLine(text = oldLines[i - 1], type = DiffType.REMOVED))
                i--
            }
        }

        return tempResult.reversed().mapIndexed { idx, line ->
            line.copy(lineNum = idx + 1)
        }
    }
}
