package com.tnhs.mcqscanner

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Builds a small synthetic reference image of the printed corner marker, for
 * OpenCV template matching against — kept procedurally in sync with however
 * SheetGenerator actually draws the real marker on the printed sheet.
 *
 * Shape: a hollow square "ring" (solid black border, white center), not a
 * solid filled square. A solid square scores surprisingly well against
 * clusters of nearby filled answer bubbles too (a tight group of dark
 * circles isn't THAT different from a dark square at typical match
 * tolerances) — real-device testing showed the live alignment guide
 * reacting to shaded/unshaded bubble rows almost as readily as to the
 * actual corner markers. A ring has two concentric contrast transitions
 * (white→black→white along any line through the center) that neither a
 * single bubble nor a row/cluster of bubbles reproduces, which should
 * discriminate far better while still being trivial to print precisely
 * (unlike e.g. a barcode, which also isn't a good fit for a CORNER
 * fiducial — decoding a barcode reliably itself needs the photo already
 * roughly de-skewed, which is exactly the problem the corner markers exist
 * to solve in the first place).
 */
object MarkerTemplateFactory {

    /** Ring border thickness, as a fraction of the marker's own size. Kept in
     *  sync with SheetGenerator.drawMarkers, which draws the real printed
     *  version at these exact same proportions. */
    const val OUTER_MARGIN_FRACTION = 0.08f
    const val RING_THICKNESS_FRACTION = 0.30f

    /** Cache by pixel size — detection reuses the same handful of scales a lot. */
    private val cache = HashMap<Int, Mat>()

    @Synchronized
    fun get(sizePx: Int): Mat {
        cache[sizePx]?.let { return it }

        val template = Mat(sizePx, sizePx, CvType.CV_8UC1, Scalar(255.0))
        val outerMargin = (sizePx * OUTER_MARGIN_FRACTION).toInt().coerceAtLeast(1)
        val ringThickness = (sizePx * RING_THICKNESS_FRACTION).toInt().coerceAtLeast(1)
        val innerMargin = outerMargin + ringThickness

        // Outer solid black square.
        Imgproc.rectangle(
            template,
            Point(outerMargin.toDouble(), outerMargin.toDouble()),
            Point((sizePx - outerMargin).toDouble(), (sizePx - outerMargin).toDouble()),
            Scalar(0.0),
            -1
        )

        // White center cutout, leaving a solid black ring — only if there's
        // enough room left for a meaningful hole; at very small template
        // sizes (a few px, only used at the coarsest live-preview scales),
        // fall back to a plain solid square rather than an invalid/degenerate
        // cutout rectangle.
        val innerSize = sizePx - innerMargin * 2
        if (innerSize >= 3) {
            Imgproc.rectangle(
                template,
                Point(innerMargin.toDouble(), innerMargin.toDouble()),
                Point((sizePx - innerMargin).toDouble(), (sizePx - innerMargin).toDouble()),
                Scalar(255.0),
                -1
            )
        }

        cache[sizePx] = template
        return template
    }
}
