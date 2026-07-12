package com.tnhs.mcqscanner

/**
 * Computes where the guide frame (and its 4 corner marker targets) sit within
 * a camera preview view of a given size, for a given paper size. Shared by
 * ScanOverlayView (drawing the guide) and AlignmentAnalyzer (checking live
 * camera frames against the same targets) so they can never drift apart —
 * "aligned" always means the same thing to both.
 */
object GuideGeometry {

    /** Fractional margin left around the guide frame within the whole view. */
    const val OUTER_MARGIN_FRACTION = 0.06f

    /** How much bigger than the marker's own printed size the guide square is
     *  drawn, AND how much positional slop the actual matching search allows
     *  around the expected marker position. Keeping one shared constant means
     *  the drawn guide box always honestly represents how forgiving alignment
     *  actually is — bumped up after real-device testing showed users
     *  struggling to land within a tight tolerance. */
    const val TOLERANCE_SCALE = 4f

    data class GuideFrame(val left: Float, val top: Float, val width: Float, val height: Float)

    /** The guide frame's bounds in pixels, letterboxed to fit [viewWidth]x[viewHeight]
     *  at the real aspect ratio for [layout]. */
    fun computeFrame(viewWidth: Float, viewHeight: Float, layout: PaperLayout): GuideFrame {
        val targetAspect = SheetTemplate.sheetAspectRatio(layout)
        val maxW = viewWidth * (1f - 2f * OUTER_MARGIN_FRACTION)
        val maxH = viewHeight * (1f - 2f * OUTER_MARGIN_FRACTION)

        val frameW: Float
        val frameH: Float
        if (maxW / maxH > targetAspect) {
            frameH = maxH
            frameW = maxH * targetAspect
        } else {
            frameW = maxW
            frameH = maxW / targetAspect
        }

        val left = (viewWidth - frameW) / 2f
        val top = (viewHeight - frameH) / 2f
        return GuideFrame(left, top, frameW, frameH)
    }

    /** The 4 corner marker target centers, as fractions (0..1) of the WHOLE
     *  view (not just the guide frame) — this is the coordinate space both
     *  ScanOverlayView's parent view and the camera preview/analysis frame
     *  share (thanks to CameraX's ViewPort keeping them in sync), so these
     *  fractions can be used directly to sample the right spot in a raw
     *  analysis frame. */
    fun markerFractionsOfView(viewWidth: Float, viewHeight: Float, layout: PaperLayout): List<Pair<Float, Float>> {
        val frame = computeFrame(viewWidth, viewHeight, layout)
        return SheetTemplate.markerFractions().map { (fx, fy) ->
            val px = frame.left + fx * frame.width
            val py = frame.top + fy * frame.height
            (px / viewWidth) to (py / viewHeight)
        }
    }

    /** Marker square size, as a fraction of the view's width — mirrors how
     *  SheetGenerator sizes markers relative to a sheet's own width. */
    fun markerSizeFractionOfView(viewWidth: Float, viewHeight: Float, layout: PaperLayout): Float {
        val frame = computeFrame(viewWidth, viewHeight, layout)
        return (frame.width / viewWidth) * SheetTemplate.markerSizeFraction(layout)
    }
}
