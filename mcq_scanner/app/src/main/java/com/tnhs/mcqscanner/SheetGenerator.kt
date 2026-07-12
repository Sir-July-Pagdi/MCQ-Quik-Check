package com.tnhs.mcqscanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Draws the printable bubble sheet PDF using Android's built-in PdfDocument
 * (no external library). Reads all geometry from SheetTemplate so the printed
 * sheet always matches what MarkerDetector / BubbleGrader expect.
 *
 * To save paper, 2 or 4 independent copies of the same template can be tiled
 * onto one physical page (see PaperLayout). Each copy gets its own
 * 4 corner markers and a dashed cut line, since each is meant to be cut apart
 * and scanned individually.
 *
 * Fonts: Android does not ship the actual Arial font. Typeface.SANS_SERIF
 * (Roboto on most devices) is used here as the closest built-in equivalent —
 * a clean, formal sans-serif rather than a decorative or monospace face.
 */
object SheetGenerator {

    private val SANS: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val SANS_BOLD: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    fun generate(template: SheetTemplate, outputFile: File, paperLayout: PaperLayout) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(
            SheetTemplate.PAGE_WIDTH.toInt(),
            SheetTemplate.PAGE_HEIGHT.toInt(),
            1
        ).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val regions = regionsForLayout(paperLayout, SheetTemplate.PAGE_WIDTH, SheetTemplate.PAGE_HEIGHT)
        regions.forEach { region ->
            drawSheetInRegion(canvas, template, region, null, paperLayout)
            drawCutLines(canvas, region, SheetTemplate.PAGE_WIDTH, SheetTemplate.PAGE_HEIGHT)
        }

        document.finishPage(page)
        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }

    /**
     * Renders a preview bitmap of one tiled sheet copy, at [widthPx] wide
     * (height computed automatically to match that tile's real proportions
     * for the given [paperLayout] — a ½-page tile is landscape-shaped, not
     * the same portrait shape as a full or ¼ page tile, so this has to know
     * which layout it's previewing or the preview comes out squished).
     * Reuses the exact same drawing code as the real printed PDF, so what's
     * shown in a live preview while adjusting settings always matches what
     * actually prints — nothing to accidentally let drift between the two.
     *
     * If [answerKey] is supplied (one 0-based choice index per question, A=0),
     * the correct bubble for each question is filled in solid, so this can
     * also serve as a preview of the answer key sheet.
     */
    fun renderPreviewBitmap(
        template: SheetTemplate,
        paperLayout: PaperLayout,
        widthPx: Int,
        answerKey: List<Int>? = null
    ): Bitmap {
        val sampleTile = regionsForLayout(paperLayout, SheetTemplate.PAGE_WIDTH, SheetTemplate.PAGE_HEIGHT).first()
        val tileAspect = sampleTile.width / sampleTile.height
        val heightPx = (widthPx / tileAspect).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val region = Region(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        drawSheetInRegion(canvas, template, region, answerKey, paperLayout)
        return bitmap
    }

    private data class Region(val left: Float, val top: Float, val width: Float, val height: Float)

    /** Returns the pixel rectangles (in [pageWidth] x [pageHeight] space) for each tiled sheet copy. */
    private fun regionsForLayout(paperLayout: PaperLayout, pageWidth: Float, pageHeight: Float): List<Region> {
        val w = pageWidth
        val h = pageHeight
        return when (paperLayout) {
            PaperLayout.HALF_CROSS -> listOf( // stacked top/bottom halves, each landscape
                Region(0f, 0f, w, h / 2f),
                Region(0f, h / 2f, w, h / 2f)
            )
            PaperLayout.HALF_LENGTH -> listOf( // side-by-side halves, each a narrow portrait column
                Region(0f, 0f, w / 2f, h),
                Region(w / 2f, 0f, w / 2f, h)
            )
            PaperLayout.QUARTER -> listOf( // 2x2 quarters
                Region(0f, 0f, w / 2f, h / 2f),
                Region(w / 2f, 0f, w / 2f, h / 2f),
                Region(0f, h / 2f, w / 2f, h / 2f),
                Region(w / 2f, h / 2f, w / 2f, h / 2f)
            )
        }
    }

    private fun drawCutLines(canvas: Canvas, region: Region, pageWidth: Float, pageHeight: Float) {
        val paint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 0.75f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f)
        }
        // Draw a line along the region's top and left edges (skip page's outer edge).
        if (region.top > 0.5f) canvas.drawLine(0f, region.top, pageWidth, region.top, paint)
        if (region.left > 0.5f) canvas.drawLine(region.left, 0f, region.left, pageHeight, paint)
    }

    private fun drawSheetInRegion(
        canvas: Canvas, template: SheetTemplate, region: Region, answerKey: List<Int>?, paperLayout: PaperLayout
    ) {
        fun px(fx: Float, fy: Float): Pair<Float, Float> =
            (region.left + fx * region.width) to (region.top + fy * region.height)

        // Scale text/stroke sizes down for smaller regions so things don't overlap.
        // Uses the SMALLER of the width/height ratios (not their average) so
        // nothing overflows either dimension — a ½-page region is full-width
        // but half-height, and averaging the two ratios used to keep text/
        // strokes too large for how little vertical room a ½-page tile has.
        val regionScale = minOf(region.width / SheetTemplate.PAGE_WIDTH, region.height / SheetTemplate.PAGE_HEIGHT)

        drawHeader(canvas, template, region, regionScale, ::px)
        drawMarkers(canvas, template, region, paperLayout, ::px)
        drawGrid(canvas, template, region, regionScale, ::px, answerKey)
    }

    /**
     * Draws the details table (Name/Date row, Gr. & Section/Subject Teacher
     * row, Score row) and the test title below it, with real cell borders
     * instead of underscore blanks.
     */
    private fun drawHeader(
        canvas: Canvas, template: SheetTemplate, region: Region, scale: Float,
        px: (Float, Float) -> Pair<Float, Float>
    ) {
        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.1f * scale
        }
        val cellPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f * scale
            typeface = SANS
            textAlign = Paint.Align.LEFT
        }
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f * scale
            typeface = SANS_BOLD
            textAlign = Paint.Align.CENTER
        }

        val midX = SheetTemplate.GRID_LEFT +
            (SheetTemplate.GRID_RIGHT - SheetTemplate.GRID_LEFT) * SheetTemplate.TABLE_MID_X_FRACTION

        drawCell(
            canvas, "Name:", SheetTemplate.GRID_LEFT,
            SheetTemplate.TABLE_TOP, midX, SheetTemplate.TABLE_ROW1_BOTTOM, borderPaint, cellPaint, scale, px
        )
        drawCell(
            canvas, "Date:", midX,
            SheetTemplate.TABLE_TOP, SheetTemplate.GRID_RIGHT, SheetTemplate.TABLE_ROW1_BOTTOM, borderPaint, cellPaint, scale, px
        )
        drawCell(
            canvas, "Gr. & Section:", SheetTemplate.GRID_LEFT,
            SheetTemplate.TABLE_ROW1_BOTTOM, midX, SheetTemplate.TABLE_ROW2_BOTTOM, borderPaint, cellPaint, scale, px
        )
        drawCell(
            canvas, "Score:", midX,
            SheetTemplate.TABLE_ROW1_BOTTOM, SheetTemplate.GRID_RIGHT, SheetTemplate.TABLE_SCORE_BOTTOM, borderPaint, cellPaint, scale, px
        )
        // Fills the space below Gr. & Section that would otherwise be left
        // blank next to the taller Score cell, and closes off the table's
        // border on all sides. Subject teacher's name is typed once when
        // generating the template (not a blank line for students to fill).
        val teacherLabel = if (template.subjectTeacher.isBlank()) "Subject Teacher:" else "Subject Teacher: ${template.subjectTeacher}"
        drawCell(
            canvas, teacherLabel, SheetTemplate.GRID_LEFT,
            SheetTemplate.TABLE_ROW2_BOTTOM, midX, SheetTemplate.TABLE_SCORE_BOTTOM, borderPaint, cellPaint, scale, px
        )

        val (titleX, titleY) = px(0.5f, SheetTemplate.TITLE_Y)
        canvas.drawText(template.testName, titleX, titleY, titlePaint)
    }

    /** Draws one bordered table cell with its label vertically centered, left-aligned. */
    private fun drawCell(
        canvas: Canvas, text: String, fLeft: Float, fTop: Float, fRight: Float, fBottom: Float,
        borderPaint: Paint, textPaint: Paint, scale: Float,
        px: (Float, Float) -> Pair<Float, Float>
    ) {
        val (left, top) = px(fLeft, fTop)
        val (right, bottom) = px(fRight, fBottom)
        canvas.drawRect(left, top, right, bottom, borderPaint)

        val textPaddingX = 6f * scale
        val textY = (top + bottom) / 2f + 4f * scale // nudge to visually center the text baseline
        canvas.drawText(text, left + textPaddingX, textY, textPaint)
    }

    /** Draws each corner marker as a hollow black "ring" (border square with a
     *  white center cutout) — see MarkerTemplateFactory for why this shape was
     *  chosen over a solid square. Proportions must match
     *  MarkerTemplateFactory.OUTER_MARGIN_FRACTION / RING_THICKNESS_FRACTION
     *  exactly, since that's what template matching is searching for. */
    private fun drawMarkers(
        canvas: Canvas, template: SheetTemplate, region: Region, paperLayout: PaperLayout,
        px: (Float, Float) -> Pair<Float, Float>
    ) {
        val blackPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        val whitePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        // Layout-aware fraction (not raw MARKER_SIZE) — keeps the printed
        // corner square the same physical size on ½ cross, ½ length, and ¼
        // page sheets. See SheetTemplate.markerSizeFraction.
        val sizePx = SheetTemplate.markerSizeFraction(paperLayout) * region.width
        val outerMargin = sizePx * MarkerTemplateFactory.OUTER_MARGIN_FRACTION
        val ringThickness = sizePx * MarkerTemplateFactory.RING_THICKNESS_FRACTION
        val innerMargin = outerMargin + ringThickness

        template.markerCenters().forEach { (fx, fy) ->
            val (cx, cy) = px(fx, fy)
            val half = sizePx / 2f
            canvas.drawRect(cx - half + outerMargin, cy - half + outerMargin, cx + half - outerMargin, cy + half - outerMargin, blackPaint)
            if (sizePx - innerMargin * 2 >= 1f) {
                canvas.drawRect(cx - half + innerMargin, cy - half + innerMargin, cx + half - innerMargin, cy + half - innerMargin, whitePaint)
            }
        }
    }

    private fun drawGrid(
        canvas: Canvas, template: SheetTemplate, region: Region, regionScale: Float,
        px: (Float, Float) -> Pair<Float, Float>, answerKey: List<Int>?
    ) {
        // Bubbles and spacing scale with contentScale too (shrinking further
        // for a test with so many questions the ideal size wouldn't fit),
        // but text labels intentionally do NOT — question numbers and choice
        // letters need to stay a legible fixed size for a human to read
        // regardless of how dense the bubble grid gets. Only regionScale
        // (the tile's own physical page-space size — full/half/quarter)
        // applies to text, since that's a genuine physical size difference,
        // not a content-density squeeze.
        val scale = regionScale * template.contentScale
        val textScale = regionScale

        val labelPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f * textScale
            typeface = SANS_BOLD
            textAlign = Paint.Align.CENTER
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f * textScale
            typeface = SANS_BOLD
            textAlign = Paint.Align.CENTER
        }
        val bubblePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * scale
        }
        val filledBubblePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        // Bubble radius is derived from the tighter of the two actual on-page
        // measurements — row pitch (height-based) and per-choice column
        // spacing (width-based) — rather than page width alone. That's what
        // keeps bubbles from overlapping on a ½-page sheet, where row pitch
        // shrinks (rows packed into half the page height) but column spacing
        // doesn't change nearly as much.
        val rowHeightPx = template.rowHeightFraction * region.height
        val choiceSpacingPx = template.minChoiceSpacingFraction() * region.width
        val bubbleRadiusPx = (minOf(rowHeightPx, choiceSpacingPx) * SheetTemplate.BUBBLE_TO_CELL_RATIO)
            .coerceAtLeast(3f)

        // Choice-letter headers: once per block, just above that block's row-group.
        for (rowGroup in 0 until template.rowGroupCount) {
            val (groupTop, _) = template.rowGroupBounds(rowGroup)
            val (_, headerYAbs) = px(0f, groupTop)
            val headerY = headerYAbs - 7f * textScale
            for (blockInRowGroup in 0 until template.blocksInRowGroup(rowGroup)) {
                template.choiceLetters.forEachIndexed { i, letter ->
                    val hx = template.choiceHeaderCenterX(rowGroup, blockInRowGroup, i)
                    val (headerX, _) = px(hx, 0f)
                    canvas.drawText(letter.toString(), headerX, headerY, headerPaint)
                }
            }
        }

        for (q in 1..template.questionCount) {
            val (lx, ly) = template.questionLabelCenter(q)
            val (labelX, labelY) = px(lx, ly)
            canvas.drawText("$q.", labelX, labelY + 4f * textScale, labelPaint)

            val correctIndex = answerKey?.getOrNull(q - 1)
            for (choiceIndex in template.choiceLetters.indices) {
                val (bx, by) = template.bubbleCenter(q, choiceIndex)
                val (bubbleX, bubbleY) = px(bx, by)
                canvas.drawCircle(bubbleX, bubbleY, bubbleRadiusPx, bubblePaint)
                if (correctIndex == choiceIndex) {
                    canvas.drawCircle(bubbleX, bubbleY, bubbleRadiusPx * 0.62f, filledBubblePaint)
                }
            }
        }
    }
}
