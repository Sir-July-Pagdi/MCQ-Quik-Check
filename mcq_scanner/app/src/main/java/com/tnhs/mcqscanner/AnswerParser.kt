package com.tnhs.mcqscanner

object AnswerParser {

    /**
     * Parses a raw answer key string like "A,B,C,D,E" or "ABCDE" or one answer
     * per line into a list of 0-based choice indices (0=A, 1=B, ...).
     *
     * Kept for backward compatibility (e.g. a pasted key), though the main
     * flow now builds the key by tapping bubbles in AnswerKeyActivity instead
     * of typing it.
     */
    fun parseKey(raw: String, choiceCount: Int): List<Int> {
        val tokens = raw
            .split(Regex("[,\\n\\s]+"))
            .filter { it.isNotBlank() }

        return tokens.map { token ->
            val letter = token.trim().uppercase().first()
            val index = letter - 'A'
            require(index in 0 until choiceCount) { "Invalid answer letter: $token" }
            index
        }
    }

    data class GradeSummary(
        val correct: Int,
        val total: Int,
        val perQuestion: List<Boolean?>, // null = left blank on the scanned sheet
        val earnedPoints: Int = correct,
        val totalPoints: Int = total
    )

    /**
     * Grades a scanned sheet against [key], weighting each question by
     * [points] (defaults to 1 pt each when not provided, so old call sites
     * still work).
     */
    fun grade(
        key: List<Int>,
        scanned: List<BubbleGrader.QuestionResult>,
        points: List<Int> = List(key.size) { 1 }
    ): GradeSummary {
        val perQuestion = scanned.mapIndexed { i, result ->
            val expected = key.getOrNull(i) ?: return@mapIndexed null
            when (result.chosenIndex) {
                null -> null
                else -> result.chosenIndex == expected
            }
        }
        val correct = perQuestion.count { it == true }
        val earnedPoints = perQuestion.withIndex().sumOf { (i, isCorrect) ->
            if (isCorrect == true) points.getOrElse(i) { 1 } else 0
        }
        val totalPoints = points.take(key.size).sum().let { if (it == 0) key.size else it }
        return GradeSummary(
            correct = correct,
            total = key.size,
            perQuestion = perQuestion,
            earnedPoints = earnedPoints,
            totalPoints = totalPoints
        )
    }
}
