package com.tnhs.mcqscanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot

/**
 * Lightweight per-frame "does each corner look aligned yet" check for the
 * live camera preview, so ScanOverlayView can turn each corner square green
 * independently as a hint before the user taps Capture.
 *
 * v1.4: replaced template-matching (which correlated on darkness/shape
 * similarity alone, and could false-match answer-bubble clusters or other
 * dark round scene clutter near the expected corner position) with a
 * two-stage contour-based check:
 *
 *  1. Paper detection — find the sheet's actual quadrilateral boundary in
 *     the frame (largest 4-sided contour). The four corner search ROIs are
 *     centered on the DETECTED paper corners, not just the fixed guide
 *     position — so the live check reacts to where the paper really is,
 *     not only where the user is assumed to have placed it. Falls back to
 *     the fixed guide position when no paper quad is found yet (sheet only
 *     partially in frame), so the guide still gives feedback progressively.
 *  2. Marker validation — within each corner ROI, contour hierarchy
 *     (RETR_CCOMP) finds shapes with a hole (a registration marker is a
 *     black square ring, i.e. an outer contour with one child/inner
 *     contour). Geometric validation then confirms it's actually a marker
 *     and not an answer bubble or other dark blob: near-square bounding box,
 *     plausible size vs. the expected marker size, low circularity (a
 *     square's circularity is ~0.785; round bubbles are ~1.0, so anything
 *     too round is rejected), and a roughly centered hole.
 */
class AlignmentAnalyzer(
    private val viewWidth: Int,
    private val viewHeight: Int,
    private val paperLayout: PaperLayout,
    private val onResult: (BooleanArray) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastProcessedAtMs = 0L
    private val minIntervalMs = 180L // throttle to ~5-6 checks/sec

    // Computed once from the (fixed, for this scan session) preview size —
    // these are fractions of the DISPLAY-oriented view, matching exactly
    // what ScanOverlayView draws. Used as the fallback ROI center when paper
    // detection doesn't find a quad this frame, and to identify which
    // detected paper corner is which (TL/TR/BL/BR).
    private val markerFractions =
        GuideGeometry.markerFractionsOfView(viewWidth.toFloat(), viewHeight.toFloat(), paperLayout)
    private val markerSizeFraction =
        GuideGeometry.markerSizeFractionOfView(viewWidth.toFloat(), viewHeight.toFloat(), paperLayout)

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastProcessedAtMs < minIntervalMs || !OpenCvSupport.ensureInitialized()) {
            image.close()
            return
        }
        lastProcessedAtMs = now

        val result = try {
            checkAlignment(image)
        } catch (e: Exception) {
            BooleanArray(4)
        } finally {
            image.close()
        }
        onResult(result)
    }

    private fun checkAlignment(image: ImageProxy): BooleanArray {
        val yPlane = image.planes[0]
        if (yPlane.pixelStride != 1) return BooleanArray(4) // Y plane is always pixelStride 1 in practice

        val rowStride = yPlane.rowStride
        val rawW = image.width
        val rawH = image.height
        val rotation = ((image.imageInfo.rotationDegrees % 360) + 360) % 360

        val buffer = yPlane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Wrap the raw Y-plane bytes as a Mat, then drop row padding (rowStride
        // can be wider than the actual image width) via a sub-Mat.
        val padded = Mat(rawH, rowStride, CvType.CV_8UC1)
        padded.put(0, 0, bytes)
        val gray = if (rowStride != rawW) padded.submat(0, rawH, 0, rawW) else padded

        try {
            val displayWidth = if (rotation == 90 || rotation == 270) rawH else rawW
            val expectedMarkerPx = markerSizeFraction * displayWidth

            // Stage 1: locate the sheet's own quadrilateral in the frame, if
            // it's fully visible yet.
            val paperQuad = detectPaperQuad(gray)

            // Expected (fallback) corner centers, in buffer coords, from the guide.
            val fallbackCenters = markerFractions.map { (fx, fy) ->
                displayFractionToBuffer(fx, fy, rawW, rawH, rotation)
            }

            // Stage 1 continued: if a paper quad was found, snap each expected
            // corner to whichever detected quad corner is nearest it — this is
            // what actually restricts search to the real sheet corners instead
            // of the assumed guide position.
            val centers = if (paperQuad != null) {
                fallbackCenters.map { (ex, ey) ->
                    paperQuad.minByOrNull { p -> hypot(p.x - ex, p.y - ey) }
                        ?.let { it.x.toInt() to it.y.toInt() }
                        ?: (ex to ey)
                }
            } else {
                fallbackCenters
            }

            val result = BooleanArray(4)
            centers.forEachIndexed { i, (bx, by) ->
                result[i] = hasMarkerRing(gray, bx, by, expectedMarkerPx)
            }
            return result
        } finally {
            if (gray !== padded) gray.release()
            padded.release()
        }
    }

    /** Maps a fraction (fx, fy) in on-screen DISPLAY orientation to actual
     *  sensor-buffer pixel coordinates, accounting for CameraX's reported rotation. */
    private fun displayFractionToBuffer(fx: Float, fy: Float, rawW: Int, rawH: Int, rotation: Int): Pair<Int, Int> =
        when (rotation) {
            90 -> (fy * rawW).toInt() to ((1 - fx) * rawH).toInt()
            270 -> ((1 - fy) * rawW).toInt() to (fx * rawH).toInt()
            180 -> ((1 - fx) * rawW).toInt() to ((1 - fy) * rawH).toInt()
            else -> (fx * rawW).toInt() to (fy * rawH).toInt()
        }

    /** Finds the largest convex 4-sided contour in [gray] (the sheet's outline),
     *  covering at least 15% of the frame. Returns null if none found — normal
     *  while the sheet is only partially in frame. */
    private fun detectPaperQuad(gray: Mat): List<Point>? {
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        try {
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 50.0, 150.0)
            Imgproc.dilate(edges, edges, Mat())
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val minArea = gray.rows().toDouble() * gray.cols().toDouble() * 0.15
            var best: Array<Point>? = null
            var bestArea = 0.0
            for (c in contours) {
                val area = Imgproc.contourArea(c)
                if (area < minArea || area <= bestArea) continue
                val c2f = MatOfPoint2f(*c.toArray())
                val peri = Imgproc.arcLength(c2f, true)
                val approx = MatOfPoint2f()
                try {
                    Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                    val pts = approx.toArray()
                    if (pts.size == 4 && Imgproc.isContourConvex(MatOfPoint(*pts))) {
                        best = pts
                        bestArea = area
                    }
                } finally {
                    c2f.release()
                    approx.release()
                }
            }
            return best?.toList()
        } finally {
            blurred.release()
            edges.release()
            hierarchy.release()
        }
    }

    /**
     * Stage 2: within a small ROI around ([cx], [cy]), looks for a contour
     * that has a hole (a "ring") and passes geometric validation for a
     * registration marker — as opposed to a filled/round answer bubble or
     * other scene clutter.
     */
    private fun hasMarkerRing(gray: Mat, cx: Int, cy: Int, expectedMarkerPx: Float): Boolean {
        val searchRadius = (expectedMarkerPx * GuideGeometry.TOLERANCE_SCALE).toInt().coerceAtLeast(12)
        val left = (cx - searchRadius).coerceIn(0, gray.cols() - 1)
        val top = (cy - searchRadius).coerceIn(0, gray.rows() - 1)
        val right = (cx + searchRadius).coerceIn(left + 1, gray.cols())
        val bottom = (cy + searchRadius).coerceIn(top + 1, gray.rows())
        val regionW = right - left
        val regionH = bottom - top
        if (regionW <= 8 || regionH <= 8) return false

        val region = Mat(gray, Rect(left, top, regionW, regionH))
        val binary = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        try {
            // Marker is a dark ring on white paper — THRESH_BINARY_INV so the
            // ink shows up as foreground (white) in the binary image.
            Imgproc.threshold(region, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)
            Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE)
            if (contours.isEmpty()) return false

            val hierArr = IntArray((hierarchy.total() * hierarchy.channels()).toInt())
            if (hierArr.isNotEmpty()) hierarchy.get(0, 0, hierArr)

            val minArea = (expectedMarkerPx * 0.4).let { it * it * 0.5 }
            val maxArea = (expectedMarkerPx * 2.5).let { it * it * 1.3 }

            for (i in contours.indices) {
                // hierarchy row layout: [next, prev, firstChild, parent]
                val childIdx = if (hierArr.size > i * 4 + 2) hierArr[i * 4 + 2] else -1
                if (childIdx < 0) continue // no hole -> can't be a ring marker

                val contour = contours[i]
                val area = Imgproc.contourArea(contour)
                if (area < minArea || area > maxArea) continue

                val rect = Imgproc.boundingRect(contour)
                if (rect.width == 0 || rect.height == 0) continue
                val aspect = rect.width.toDouble() / rect.height.toDouble()
                if (aspect < 0.7 || aspect > 1.4) continue // roughly square, not elongated

                val c2f = MatOfPoint2f(*contour.toArray())
                val peri = Imgproc.arcLength(c2f, true)
                c2f.release()
                if (peri <= 0) continue
                val circularity = 4 * Math.PI * area / (peri * peri)
                // A square's circularity is ~0.785; a filled circle (answer
                // bubble) is ~1.0. Reject anything too round.
                if (circularity > 0.92) continue

                // The hole should sit roughly centered inside the outer ring,
                // not off to one side (helps reject accidental hole-having
                // blobs, e.g. two overlapping bubbles).
                val childRect = Imgproc.boundingRect(contours[childIdx])
                val childCx = childRect.x + childRect.width / 2.0
                val childCy = childRect.y + childRect.height / 2.0
                val parentCx = rect.x + rect.width / 2.0
                val parentCy = rect.y + rect.height / 2.0
                val centerOffset = hypot(childCx - parentCx, childCy - parentCy)
                if (centerOffset > rect.width * 0.25) continue

                val matchCx = left + rect.x + rect.width / 2
                val matchCy = top + rect.y + rect.height / 2
                if (PaperSurroundCheck.isOnWhitePaper(gray, matchCx, matchCy, rect.width)) {
                    return true
                }
            }
            return false
        } finally {
            binary.release()
            region.release()
            hierarchy.release()
        }
    }
}
