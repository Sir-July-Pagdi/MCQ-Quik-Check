package com.tnhs.mcqscanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix

/**
 * Un-skews a captured photo so the 4 corner markers land exactly where
 * SheetTemplate says they should be, using Android's built-in
 * Matrix.setPolyToPoly (no external computer-vision library needed).
 */
object PerspectiveWarp {

    /**
     * Warps [source] so that [detectedCorners] (in source-image pixel coords,
     * ordered top-left, top-right, bottom-left, bottom-right) map onto the
     * expected marker positions for [template], producing an output bitmap
     * sized [outputWidth] x [outputHeight] (defaults to template page size).
     */
    fun warp(
        source: Bitmap,
        detectedCorners: List<MarkerDetector.Point>,
        template: SheetTemplate,
        outputWidth: Int = SheetTemplate.PAGE_WIDTH.toInt(),
        outputHeight: Int = SheetTemplate.PAGE_HEIGHT.toInt()
    ): Bitmap {
        require(detectedCorners.size == 4) { "Expected exactly 4 detected corners" }

        val expected = template.markerCenters().map { (fx, fy) ->
            fx * outputWidth to fy * outputHeight
        }

        val src = FloatArray(8)
        val dst = FloatArray(8)
        detectedCorners.forEachIndexed { i, p ->
            src[i * 2] = p.x
            src[i * 2 + 1] = p.y
        }
        expected.forEachIndexed { i, (ex, ey) ->
            dst[i * 2] = ex
            dst[i * 2 + 1] = ey
        }

        val matrix = Matrix()
        val ok = matrix.setPolyToPoly(src, 0, dst, 0, 4)
        if (!ok) {
            throw IllegalStateException("Could not compute perspective transform from detected markers")
        }

        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(source, matrix, null)
        return output
    }
}
