package com.tnhs.mcqscanner

/**
 * Otsu's method finds the threshold that best separates a set of numeric
 * values into two clusters (by maximizing the variance between them). Used
 * by BubbleGrader to adapt the "filled vs blank" darkness threshold to each
 * captured photo's own lighting/exposure, instead of assuming a fixed
 * absolute brightness — a photo taken in dim light reads darker across the
 * board (including blank bubbles), so a fixed threshold either misses real
 * marks in low light or false-positives on faint paper texture/shadows in
 * bright light. A real answer sheet's own darkness scores are naturally
 * bimodal (mostly-unmarked bubbles vs. the one filled choice per question),
 * so Otsu can find a good split point specific to that photo.
 */
object OtsuThreshold {

    /**
     * Returns the value that best separates [values] into two clusters, or
     * null if there aren't enough values to make that meaningful.
     */
    fun compute(values: List<Double>, rangeMin: Double = 0.0, rangeMax: Double = 255.0, bins: Int = 64): Double? {
        if (values.size < 4) return null
        val binWidth = (rangeMax - rangeMin) / bins
        if (binWidth <= 0) return null

        val histogram = IntArray(bins)
        values.forEach { v ->
            val bin = ((v - rangeMin) / binWidth).toInt().coerceIn(0, bins - 1)
            histogram[bin]++
        }

        val total = values.size
        var sum = 0.0
        for (i in 0 until bins) sum += i * histogram[i]

        var sumB = 0.0
        var weightBackground = 0
        var maxVariance = -1.0
        var bestBin = -1

        for (i in 0 until bins) {
            weightBackground += histogram[i]
            if (weightBackground == 0) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0) break

            sumB += i * histogram[i]
            val meanBackground = sumB / weightBackground
            val meanForeground = (sum - sumB) / weightForeground

            val variance = weightBackground.toDouble() * weightForeground.toDouble() *
                (meanBackground - meanForeground) * (meanBackground - meanForeground)

            if (variance > maxVariance) {
                maxVariance = variance
                bestBin = i
            }
        }

        if (bestBin < 0) return null
        return rangeMin + (bestBin + 1) * binWidth
    }
}
