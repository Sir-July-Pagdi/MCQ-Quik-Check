package com.tnhs.mcqscanner

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect

/**
 * A real printed answer sheet is a small dark marker sitting in the middle of
 * a large plain white/cream sheet of paper. Template matching alone only
 * checks "is there something roughly this shape here" — it doesn't know
 * anything about what's AROUND that shape, so a sufficiently square-ish dark
 * patch in a random scene (a window frame, a tile grout line, a shadow) could
 * still score well on shape alone. This adds a second, independent check:
 * the area immediately around a matched marker must actually be bright
 * (paper-like), not just the marker-sized patch itself.
 */
object PaperSurroundCheck {

    /** Minimum average brightness (0..255) the area around a matched marker
     *  must have to count as "sitting on a plain sheet of paper." Kept fairly
     *  permissive — real photos under normal indoor lighting (not a photo
     *  studio) often don't read anywhere near 255, and this only needs to be
     *  bright enough to rule out obviously non-paper scenes, not to nail an
     *  exact whiteness. */
    var WHITE_THRESHOLD = 140.0

    /**
     * Returns true if the region around ([centerX], [centerY]) — roughly
     * [markerSizePx] wide — looks like it's sitting on plain light paper.
     * Uses the mean brightness of a box a bit larger than the marker itself;
     * since the dark marker only occupies a modest fraction of that box's
     * area, a genuinely white surround still pulls the average high, while a
     * random dark/textured scene (no actual white paper around it) stays low.
     */
    fun isOnWhitePaper(gray: Mat, centerX: Int, centerY: Int, markerSizePx: Int): Boolean {
        val outerRadius = (markerSizePx * 1.8).toInt().coerceAtLeast(markerSizePx + 4)
        val left = (centerX - outerRadius).coerceIn(0, gray.cols() - 1)
        val top = (centerY - outerRadius).coerceIn(0, gray.rows() - 1)
        val right = (centerX + outerRadius).coerceIn(left + 1, gray.cols())
        val bottom = (centerY + outerRadius).coerceIn(top + 1, gray.rows())
        if (right - left <= 2 || bottom - top <= 2) return false

        val region = Mat(gray, Rect(left, top, right - left, bottom - top))
        return try {
            Core.mean(region).`val`[0] >= WHITE_THRESHOLD
        } finally {
            region.release()
        }
    }
}
