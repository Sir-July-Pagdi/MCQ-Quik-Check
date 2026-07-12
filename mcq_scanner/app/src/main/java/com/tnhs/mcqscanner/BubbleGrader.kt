package com.tnhs.mcqscanner

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Grades a warped (un-skewed) bubble sheet image by sampling ink darkness at each
 * known bubble center (from SheetTemplate) and picking the darkest bubble per
 * question. No character recognition anywhere — pure OMR.
 *
 * The darkness threshold for "filled" is computed adaptively per sheet (see
 * OtsuThreshold) rather than using a fixed brightness value — a fixed value
 * either missed real marks in dim light or false-positived on faint paper
 * texture in bright light, since a photo's overall brightness shifts
 * everything, blank bubbles included. FILLED_THRESHOLD below is now only a
 * fallback for the rare case the adaptive value looks implausible.
 */
object BubbleGrader {

    /** Average darkness (0..255, lower = darker) at/above which a bubble counts as "filled". */
    var FILLED_THRESHOLD = 150.0

    /** If the top two candidate darkness scores for a question are within this gap,
     *  the answer is flagged ambiguous (e.g. double-marked or very light shading). */
    var AMBIGUOUS_GAP = 12.0

    /** Radius (px) sampled around each bubble center, as a fraction of image width. */
    var SAMPLE_RADIUS_FRACTION = 0.016f

    data class QuestionResult(
        val questionNumber: Int,
        val chosenIndex: Int?, // null = left blank
        val isAmbiguous: Boolean,
        val darknessScores: List<Double> // one per choice, for debugging/tuning
    )

    class LowConfidenceException(message: String) : Exception(message)

    /**
     * Final safety net after grade(): even when MarkerDetector accepts a photo,
     * this catches the case where what's under the markers isn't actually a
     * clean bubble grid — e.g. it's some other flat rectangular object with
     * dark corners by coincidence. A real filled sheet should have most
     * bubbles clearly one way or the other; if a large chunk of the "answers"
     * come out ambiguous or blank, that's a strong sign this isn't really our
     * sheet, and reporting a score would be actively misleading.
     */
    fun requireConfident(results: List<QuestionResult>) {
        if (results.isEmpty()) return
        val ambiguousCount = results.count { it.isAmbiguous }
        val blankCount = results.count { it.chosenIndex == null }
        val ambiguousRate = ambiguousCount.toDouble() / results.size
        val unclearRate = (ambiguousCount + blankCount).toDouble() / results.size

        if (ambiguousRate > 0.30 || unclearRate > 0.55) {
            throw LowConfidenceException(
                "This doesn't look like a clearly filled answer sheet " +
                    "(${(ambiguousRate * 100).toInt()}% ambiguous, ${(unclearRate * 100).toInt()}% unclear). " +
                    "Retake the photo with the sheet flat, well-lit, and fully in frame."
            )
        }
    }

    fun grade(warped: Bitmap, template: SheetTemplate): List<QuestionResult> {
        val radiusPx = (warped.width * SAMPLE_RADIUS_FRACTION).toInt().coerceAtLeast(3)

        // First pass: raw darkness scores for every bubble on the sheet.
        val allScores = (1..template.questionCount).map { q ->
            template.choiceLetters.indices.map { choiceIndex ->
                val (fx, fy) = template.bubbleCenter(q, choiceIndex)
                val cx = (fx * warped.width).toInt()
                val cy = (fy * warped.height).toInt()
                averageDarkness(warped, cx, cy, radiusPx)
            }
        }

        // Adapt the "filled" threshold to THIS photo's own lighting rather
        // than a fixed brightness value — see OtsuThreshold. Only trust it
        // within a plausible range; if the whole sheet reads uniformly dark
        // or bright (no real separation to find), fall back to the fixed
        // default rather than an unstable computed value.
        val adaptiveThreshold = OtsuThreshold.compute(allScores.flatten())
        val effectiveThreshold = adaptiveThreshold?.takeIf { it in 40.0..220.0 } ?: FILLED_THRESHOLD

        return allScores.mapIndexed { i, scores ->
            val maxDarkness = scores.max() // higher = darker (we invert luminance below)
            val chosenIndex = scores.indexOf(maxDarkness)

            val sorted = scores.sortedDescending()
            val ambiguous = sorted.size > 1 && (sorted[0] - sorted[1]) < AMBIGUOUS_GAP

            val filled = maxDarkness >= effectiveThreshold
            QuestionResult(
                questionNumber = i + 1,
                chosenIndex = if (filled) chosenIndex else null,
                isAmbiguous = filled && ambiguous,
                darknessScores = scores
            )
        }
    }

    /** Returns "darkness" 0..255 where 255 = fully black (inverted luminance), averaged over a disc. */
    private fun averageDarkness(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): Double {
        var sum = 0.0
        var count = 0
        val left = (cx - radius).coerceAtLeast(0)
        val right = (cx + radius).coerceAtMost(bitmap.width - 1)
        val top = (cy - radius).coerceAtLeast(0)
        val bottom = (cy + radius).coerceAtMost(bitmap.height - 1)

        for (y in top..bottom) {
            for (x in left..right) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy > radius * radius) continue
                val pixel = bitmap.getPixel(x, y)
                val luminance = Color.red(pixel) * 0.299 +
                        Color.green(pixel) * 0.587 +
                        Color.blue(pixel) * 0.114
                sum += (255 - luminance)
                count++
            }
        }
        return if (count == 0) 0.0 else sum / count
    }
}
