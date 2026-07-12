package com.tnhs.mcqscanner

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

/**
 * Locates the 4 solid-black corner markers in a captured (upright) photo of
 * the bubble sheet, so PerspectiveWarp can un-skew the image before grading.
 *
 * This uses real template matching (OpenCV's matchTemplate, normalized
 * cross-correlation) against a synthetic reference marker image, searched at
 * several scales per corner — the same core technique the open-source
 * OMRChecker project uses (see its CropOnMarkers.py: per-quadrant, multi-
 * scale cv2.matchTemplate). This replaced an earlier hand-rolled heuristic
 * (darkness-weighted centroid + bounding-box fill-ratio check) that kept
 * proving too fragile in real testing — it either missed real markers under
 * different lighting, or confidently "matched" dark, textured backgrounds
 * (grass, walls, tables, random objects) that were dark enough on average
 * but had none of the marker's actual square shape. Template matching
 * correlates shape, not just brightness, which is a categorically different
 * (and much stronger) signal.
 *
 * The 4 accepted points are also checked to make sure they form a plausible
 * sheet-shaped quadrilateral before being accepted at all.
 *
 * Each coarse template match is also refined to a precise sub-pixel center
 * via Otsu thresholding + centroid within a tight local window (see
 * refineCentroid) — template matching alone is only accurate to the
 * search grid's resolution, which wasn't precise enough for the perspective
 * warp and was showing up as misaligned bubble sampling (reading "blank"
 * even on clearly, fully-shaded bubbles).
 */
object MarkerDetector {

    /** Normalized cross-correlation score (0..1) a corner match must reach to be accepted. */
    var MIN_MATCH_SCORE = 0.45

    /** Fraction of image width/height defining how far into each quadrant we search
     *  from that corner. */
    var SEARCH_WINDOW = 0.32f

    /** Multi-scale search range, as a multiplier of the "expected" marker pixel size
     *  (estimated from SheetTemplate.MARKER_SIZE and the photo's own width). */
    private val SCALE_STEPS = floatArrayOf(0.5f, 0.65f, 0.8f, 1.0f, 1.25f, 1.5f, 1.8f, 2.2f)

    /** How far outside the expected sheet-proportions a detected quadrilateral is
     *  still allowed to be, to tolerate normal handheld-photo perspective. */
    var MIN_ASPECT_RATIO_FACTOR = 0.30f
    var MAX_ASPECT_RATIO_FACTOR = 3.2f

    data class Point(val x: Float, val y: Float)

    class MarkersNotFoundException(message: String) : Exception(message)

    /**
     * Returns the 4 detected marker centers in this order:
     * top-left, top-right, bottom-left, bottom-right.
     * Throws MarkersNotFoundException if any marker can't be confidently located,
     * or if the 4 found points don't form a plausible sheet-shaped rectangle.
     *
     * [expectedAspect] is the printed sheet's own width/height — pass the value
     * matching whatever paper size this test was printed at.
     */
    fun detect(
        bitmap: Bitmap,
        expectedAspect: Float = SheetTemplate.PAGE_WIDTH / SheetTemplate.PAGE_HEIGHT,
        paperLayout: PaperLayout = PaperLayout.HALF_CROSS
    ): List<Point> {
        if (!OpenCvSupport.ensureInitialized()) {
            throw MarkersNotFoundException("Could not start the image scanner (OpenCV failed to load). Try restarting the app.")
        }

        val rgba = Mat()
        val gray = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()

        try {
            val w = gray.cols()
            val h = gray.rows()
            val winX = (w * SEARCH_WINDOW).toInt()
            val winY = (h * SEARCH_WINDOW).toInt()
            val expectedMarkerPx = SheetTemplate.markerSizeFraction(paperLayout) * w

            val quadrants = listOf(
                "top-left" to intArrayOf(0, 0, winX, winY),
                "top-right" to intArrayOf(w - winX, 0, w, winY),
                "bottom-left" to intArrayOf(0, h - winY, winX, h),
                "bottom-right" to intArrayOf(w - winX, h - winY, w, h)
            )

            val points = quadrants.map { (name, bounds) ->
                findMarker(gray, bounds[0], bounds[1], bounds[2], bounds[3], expectedMarkerPx)
                    ?: throw MarkersNotFoundException(
                        "Could not find a clear alignment marker in the $name corner. Make sure " +
                            "this is a photo of the printed answer sheet with all 4 corner squares visible."
                    )
            }

            validateQuadShape(points, expectedAspect)
            return points
        } finally {
            gray.release()
        }
    }

    /** Multi-scale template match within one corner's search window. */
    private fun findMarker(
        gray: Mat, left: Int, top: Int, right: Int, bottom: Int, expectedMarkerPx: Float
    ): Point? {
        val regionW = right - left
        val regionH = bottom - top
        if (regionW <= 8 || regionH <= 8) return null

        val region = Mat(gray, Rect(left, top, regionW, regionH))
        var bestScore = -1.0
        var bestX = 0.0
        var bestY = 0.0
        var bestSize = 0

        try {
            for (scale in SCALE_STEPS) {
                val templateSize = (expectedMarkerPx * scale).toInt().coerceAtLeast(8)
                if (templateSize >= regionW || templateSize >= regionH) continue

                val template = MarkerTemplateFactory.get(templateSize)
                val result = Mat()
                try {
                    Imgproc.matchTemplate(region, template, result, Imgproc.TM_CCOEFF_NORMED)
                    val mmr = Core.minMaxLoc(result)
                    if (mmr.maxVal > bestScore) {
                        val matchCx = left + mmr.maxLoc.x.toInt() + templateSize / 2
                        val matchCy = top + mmr.maxLoc.y.toInt() + templateSize / 2
                        if (PaperSurroundCheck.isOnWhitePaper(gray, matchCx, matchCy, templateSize)) {
                            bestScore = mmr.maxVal
                            bestX = mmr.maxLoc.x
                            bestY = mmr.maxLoc.y
                            bestSize = templateSize
                        }
                    }
                } finally {
                    result.release()
                }
            }
        } finally {
            region.release()
        }

        if (bestScore < MIN_MATCH_SCORE) return null
        val coarseCx = (left + bestX + bestSize / 2.0).toInt()
        val coarseCy = (top + bestY + bestSize / 2.0).toInt()

        // Template matching finds the right REGION reliably (that's its whole
        // strength — matching shape, not just brightness), but its position is
        // quantized to the search grid, which isn't precise enough for an
        // accurate perspective warp — small errors here directly become
        // misaligned bubble sampling later, which was showing up as "blank"
        // results even on clearly, fully-shaded bubbles. Refine within a tight
        // window using Otsu thresholding + the dark blob's actual centroid,
        // which is accurate to sub-pixel precision.
        return refineCentroid(gray, coarseCx, coarseCy, bestSize)
    }

    /** Refines a coarse match to the true center of the dark marker via Otsu
     *  thresholding + centroid, within a small window around the coarse point. */
    private fun refineCentroid(gray: Mat, coarseCx: Int, coarseCy: Int, markerSizePx: Int): Point {
        val radius = (markerSizePx * 0.65).toInt().coerceAtLeast(3)
        val left = (coarseCx - radius).coerceIn(0, gray.cols() - 1)
        val top = (coarseCy - radius).coerceIn(0, gray.rows() - 1)
        val right = (coarseCx + radius).coerceIn(left + 1, gray.cols())
        val bottom = (coarseCy + radius).coerceIn(top + 1, gray.rows())

        val region = Mat(gray, Rect(left, top, right - left, bottom - top))
        try {
            val binary = Mat()
            try {
                Imgproc.threshold(region, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)
                val moments = Imgproc.moments(binary)
                if (moments.m00 > 1.0) {
                    val cx = left + (moments.m10 / moments.m00)
                    val cy = top + (moments.m01 / moments.m00)
                    return Point(cx.toFloat(), cy.toFloat())
                }
            } finally {
                binary.release()
            }
        } finally {
            region.release()
        }
        // Not enough dark pixels to refine confidently — fall back to the coarse point.
        return Point(coarseCx.toFloat(), coarseCy.toFloat())
    }

    /** Rejects point sets that don't form a plausible sheet-shaped quadrilateral. */
    private fun validateQuadShape(points: List<Point>, expectedAspect: Float) {
        val (tl, tr, bl, br) = points

        val topWidth = dist(tl, tr)
        val bottomWidth = dist(bl, br)
        val leftHeight = dist(tl, bl)
        val rightHeight = dist(tr, br)

        if (topWidth <= 1f || bottomWidth <= 1f || leftHeight <= 1f || rightHeight <= 1f) {
            throw MarkersNotFoundException("Detected markers are too close together to form a sheet outline.")
        }

        val widthRatio = maxOf(topWidth, bottomWidth) / minOf(topWidth, bottomWidth)
        val heightRatio = maxOf(leftHeight, rightHeight) / minOf(leftHeight, rightHeight)
        if (widthRatio > 3.0 || heightRatio > 3.0) {
            throw MarkersNotFoundException(
                "Detected corner markers don't form a consistent rectangle. Retake the photo with the whole sheet visible and flat."
            )
        }

        val avgWidth = (topWidth + bottomWidth) / 2
        val avgHeight = (leftHeight + rightHeight) / 2
        val measuredAspect = avgWidth / avgHeight
        val ratio = measuredAspect / expectedAspect
        if (ratio < MIN_ASPECT_RATIO_FACTOR || ratio > MAX_ASPECT_RATIO_FACTOR) {
            throw MarkersNotFoundException(
                "The detected shape doesn't match this test's expected paper size. Make sure you're scanning the right sheet (full/½/¼ page) for this test."
            )
        }
    }

    private fun dist(a: Point, b: Point): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}
