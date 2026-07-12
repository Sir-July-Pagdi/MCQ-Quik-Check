package com.tnhs.mcqscanner

import java.io.Serializable

enum class NumberingOrder { COLUMN_MAJOR, ROW_MAJOR }

/**
 * Defines the layout of ONE self-contained bubble sheet purely as fractions
 * (0f..1f) of that sheet's own region. Both SheetGenerator (drawing the
 * printable PDF) and the scanner (MarkerDetector / BubbleGrader) call the
 * functions below, so the printed sheet and the expected bubble positions can
 * never drift out of sync.
 *
 * A physical page can contain 2 or 4 independent copies of this template
 * tiled side by side (see SheetGenerator's `PaperLayout`), to save paper on
 * short quizzes. Each tiled copy gets its own 4 corner markers and is meant to
 * be cut apart before handing out — from the scanner's point of view, each
 * cut-out sheet is just "the page," so none of the geometry below needs to
 * know about tiling at all.
 *
 * No student-ID bubble grid — just a name line. Layout follows the ZipGrade
 * convention of stacking questions in blocks of [rowsPerBlock] (default 10),
 * with up to [maxBlocksPerRowGroup] blocks side by side before wrapping to a
 * new row of blocks underneath.
 *
 * IMPORTANT — sizing philosophy: every row-group is drawn at a fixed "ideal"
 * pitch (bigger, more legible bubbles) rather than stretched to fill whatever
 * space happens to be available. This means:
 *  - A block that isn't fully used (e.g. the last block only has questions
 *    21-25 out of a 10-row block) still uses the SAME row height as the full
 *    blocks above it — it just leaves the unused rows blank instead of
 *    spreading its few rows out to fill the block's box.
 *  - If the whole grid ends up shorter than the space available on the page,
 *    the grid is centered in that space rather than stretched to fill it.
 *  - Only when a test has so many questions that the ideal size wouldn't fit
 *    on one page does everything shrink uniformly (via [contentScale]) so it
 *    still fits on a single sheet.
 */
data class SheetTemplate(
    val testName: String,
    val questionCount: Int,
    val choiceCount: Int, // 4 (A-D) or 5 (A-E)
    val numbering: NumberingOrder,
    val rowsPerBlock: Int = 10,
    val maxBlocksPerRowGroup: Int = 3,
    val subjectTeacher: String = ""
) : Serializable {

    companion object {
        // A single sheet region is treated as if it were its own US-Letter-shaped
        // page for the purposes of fraction math; SheetGenerator scales this
        // down when tiling multiple sheets onto one physical page.
        const val PAGE_WIDTH = 612f
        const val PAGE_HEIGHT = 792f

        const val MARKER_MARGIN_X = 0.03f
        // Bottom markers sit at this margin from the bottom edge, as before.
        // Top markers no longer use a fixed margin — see TITLE_Y below; they
        // flank the title line instead of sitting at the very top edge, since
        // the details table now occupies that space.
        const val MARKER_MARGIN_Y = 0.03f
        const val MARKER_SIZE = 0.028f

        // --- Details table (Name/Date row, Gr.&Section/Score row) ---------
        const val TABLE_TOP = 0.045f
        const val TABLE_ROW1_BOTTOM = 0.085f   // bottom of Name / Date cells
        const val TABLE_ROW2_BOTTOM = 0.125f   // bottom of the Gr. & Section cell
        const val TABLE_SCORE_BOTTOM = 0.160f  // bottom of the (taller) Score cell
        const val TABLE_MID_X_FRACTION = 0.66f // left column (Name/Gr.&Section) is wider than the right

        // Title sits below the table, centered, with the top corner markers
        // flanking it at the same height.
        const val TITLE_Y = 0.205f

        const val GRID_TOP = 0.25f
        const val GRID_BOTTOM = 0.97f
        const val GRID_LEFT = 0.05f
        const val GRID_RIGHT = 0.97f

        // Bigger, bolder bubbles than the original draft (was 0.009f). This is
        // now a starting point only — SheetGenerator derives the actual drawn
        // radius from the tighter of the real row pitch / column spacing for
        // whatever paper size is being rendered (see BUBBLE_TO_CELL_RATIO),
        // rather than always sizing off page width alone. Sizing bubbles off
        // width alone was the ½-page bug: a ½-page sheet's row pitch shrinks
        // (rows are packed into half the page height) but its width doesn't,
        // so width-only sizing kept bubbles full-size while rows got packed
        // tighter, and adjacent rows started touching.
        const val BUBBLE_RADIUS_FRACTION = 0.016f
        const val BUBBLE_TO_CELL_RATIO = 0.40f

        // Fixed row pitch (fraction of page height) each question row gets,
        // regardless of how many rows happen to be filled in a given block.
        const val IDEAL_ROW_HEIGHT_FRACTION = 0.050f

        // Vertical gap between stacked row-groups (e.g. 1-10/11-20 vs. 21-30).
        const val ROW_GROUP_GAP = 0.045f

        // Fraction of a block's width reserved for the "12." question-number
        // label, condensed down from 0.22f to leave more room for bigger bubbles.
        const val LABEL_WIDTH_FRACTION = 0.18f

        /**
         * Width/height of ONE cut-out sheet for a given tiling (1, 2, or 4 copies
         * per physical page). A half-page sheet is wide/short (landscape); full
         * and quarter page sheets share the same portrait proportions. Used both
         * by the printable PDF (implicitly, via the region math in SheetGenerator)
         * and by the scanner (to frame the on-screen guide and validate detected
         * marker shapes against the right expected proportions).
         */
        fun sheetAspectRatio(layout: PaperLayout): Float = when (layout) {
            PaperLayout.HALF_CROSS -> PAGE_WIDTH / (PAGE_HEIGHT / 2f)   // wide landscape tile
            PaperLayout.HALF_LENGTH -> (PAGE_WIDTH / 2f) / PAGE_HEIGHT  // narrow portrait tile
            PaperLayout.QUARTER -> (PAGE_WIDTH / 2f) / (PAGE_HEIGHT / 2f) // same ratio as full page
        }

        /** Physical width (in the same PAGE_WIDTH/PAGE_HEIGHT point units) of ONE
         *  cut-out sheet for [layout] — HALF_CROSS uses the full page width,
         *  HALF_LENGTH and QUARTER each use half of it. */
        private fun sheetWidthPt(layout: PaperLayout): Float = when (layout) {
            PaperLayout.HALF_CROSS -> PAGE_WIDTH
            PaperLayout.HALF_LENGTH -> PAGE_WIDTH / 2f
            PaperLayout.QUARTER -> PAGE_WIDTH / 2f
        }

        /**
         * Corner marker size, as a fraction of ONE cut sheet's own width, scaled
         * per [layout] so the marker prints/displays at the SAME physical size
         * on every paper layout. MARKER_SIZE (0.028) is defined relative to a
         * full-width HALF_CROSS sheet; HALF_LENGTH/QUARTER sheets are only half
         * as wide, so their fraction is doubled to land on the same absolute
         * size. Without this, HALF_LENGTH/QUARTER markers rendered visibly
         * smaller than HALF_CROSS's, even though all three used the same raw
         * MARKER_SIZE constant against their own (differently-sized) width.
         */
        fun markerSizeFraction(layout: PaperLayout): Float =
            MARKER_SIZE * (PAGE_WIDTH / sheetWidthPt(layout))

        /** Fractional (x, y) position of each of the 4 corner markers, centers.
         *  Top markers flank the title line (see TITLE_Y); bottom markers sit at
         *  the usual small margin from the bottom edge. Shared by SheetGenerator
         *  (drawing) and ScanOverlayView (the on-screen alignment guide), so
         *  they can never drift out of sync with each other. */
        fun markerFractions(): List<Pair<Float, Float>> {
            val left = MARKER_MARGIN_X
            val right = 1f - MARKER_MARGIN_X
            val topY = TITLE_Y
            val bottomY = 1f - MARKER_MARGIN_Y
            return listOf(
                left to topY,       // top-left
                right to topY,      // top-right
                left to bottomY,    // bottom-left
                right to bottomY    // bottom-right
            )
        }

        /** Sensible range for rows-per-column (rowsPerBlock), given how many
         *  questions the test has — no point offering more rows than there
         *  are questions, and beyond ~30 a single column gets awkwardly tall. */
        fun rowsPerBlockRange(questionCount: Int): IntRange {
            val max = questionCount.coerceIn(1, 30)
            return 1..max
        }

        /** Sensible range for columns side by side (maxBlocksPerRowGroup),
         *  given the paper layout — a narrower tile doesn't have room for as
         *  many side-by-side columns before things get too cramped to bubble
         *  in reliably. */
        fun maxBlocksPerRowGroupRange(layout: PaperLayout): IntRange = when (layout) {
            PaperLayout.HALF_CROSS -> 1..6   // wide landscape tile
            PaperLayout.HALF_LENGTH -> 1..3  // narrow portrait tile
            PaperLayout.QUARTER -> 1..3
        }
    }

    val choiceLetters: List<Char>
        get() = ('A'..('A' + choiceCount - 1)).toList()

    val blockCount: Int
        get() = kotlin.math.ceil(questionCount.toDouble() / rowsPerBlock).toInt()

    val rowGroupCount: Int
        get() = kotlin.math.ceil(blockCount.toDouble() / maxBlocksPerRowGroup).toInt()

    /** How many blocks sit side by side in this row-group (0-based). */
    fun blocksInRowGroup(rowGroup: Int): Int {
        val remaining = blockCount - rowGroup * maxBlocksPerRowGroup
        return remaining.coerceIn(0, maxBlocksPerRowGroup)
    }

    /** How many question rows are actually used in this global block index. */
    fun rowsInBlock(globalBlockIndex: Int): Int {
        val used = questionCount - globalBlockIndex * rowsPerBlock
        return used.coerceIn(0, rowsPerBlock)
    }

    // ---- Fixed-pitch sizing / centering -----------------------------------

    private val availableHeight: Float
        get() = GRID_BOTTOM - GRID_TOP

    /** Height a single row-group would take up at the ideal (unshrunk) pitch. */
    private val idealGroupHeight: Float
        get() = rowsPerBlock * IDEAL_ROW_HEIGHT_FRACTION

    private val idealTotalContentHeight: Float
        get() = rowGroupCount * idealGroupHeight +
            (rowGroupCount - 1).coerceAtLeast(0) * ROW_GROUP_GAP

    /**
     * 1f when the ideal (big, legible) sizing fits within the available grid
     * area — the common case for a normal-length quiz. Only drops below 1f
     * for tests with so many questions that the ideal size would run off the
     * page, in which case everything (bubble radius, fonts, row pitch)
     * shrinks together so the sheet still fits on one page.
     */
    val contentScale: Float
        get() {
            val total = idealTotalContentHeight
            return if (total <= availableHeight || total <= 0f) 1f else availableHeight / total
        }

    private val actualGroupHeight: Float
        get() = idealGroupHeight * contentScale

    private val actualGroupGap: Float
        get() = ROW_GROUP_GAP * contentScale

    /** Top of the grid content, after centering any leftover vertical space. */
    private val contentTopOffset: Float
        get() {
            val actualTotal = rowGroupCount * actualGroupHeight +
                (rowGroupCount - 1).coerceAtLeast(0) * actualGroupGap
            val leftover = (availableHeight - actualTotal).coerceAtLeast(0f)
            return GRID_TOP + leftover / 2f
        }

    data class Slot(val questionNumber: Int, val rowGroup: Int, val blockInRowGroup: Int, val rowInBlock: Int)

    // NOTE: this is a plain computed property (not `by lazy`), on purpose.
    // SheetTemplate now travels through Intent.putExtra(...) as Serializable
    // (for AnswerKeyActivity), and `by lazy` stores its cached value behind a
    // Lazy<T> delegate object that does NOT survive Android's real Java
    // serialization of Intent extras — that mismatch is what was crashing the
    // app the moment "Create Answer Key Here" was tapped. Recomputing this
    // list each time is effectively free at quiz/exam sizes.
    private val slots: List<Slot>
        get() = buildSlots()

    private fun buildSlots(): List<Slot> {
        val result = mutableListOf<Slot>()
        var qNum = 1
        if (numbering == NumberingOrder.COLUMN_MAJOR) {
            // Down each block's rows first, then the next block to the right,
            // then the next row-group down.
            outer@ for (rowGroup in 0 until rowGroupCount) {
                for (blockInRowGroup in 0 until blocksInRowGroup(rowGroup)) {
                    val globalBlock = rowGroup * maxBlocksPerRowGroup + blockInRowGroup
                    for (rowInBlock in 0 until rowsInBlock(globalBlock)) {
                        if (qNum > questionCount) break@outer
                        result.add(Slot(qNum, rowGroup, blockInRowGroup, rowInBlock))
                        qNum++
                    }
                }
            }
        } else {
            // Across each row of blocks first, then down to the next row.
            outer@ for (rowGroup in 0 until rowGroupCount) {
                val blocksHere = blocksInRowGroup(rowGroup)
                val maxRows = (0 until blocksHere).maxOfOrNull {
                    rowsInBlock(rowGroup * maxBlocksPerRowGroup + it)
                } ?: 0
                for (rowInBlock in 0 until maxRows) {
                    for (blockInRowGroup in 0 until blocksHere) {
                        val globalBlock = rowGroup * maxBlocksPerRowGroup + blockInRowGroup
                        if (rowInBlock >= rowsInBlock(globalBlock)) continue
                        if (qNum > questionCount) break@outer
                        result.add(Slot(qNum, rowGroup, blockInRowGroup, rowInBlock))
                        qNum++
                    }
                }
            }
        }
        return result
    }

    private fun slotFor(questionNumber: Int): Slot =
        slots.getOrNull(questionNumber - 1)
            ?: error("No layout slot for question $questionNumber")

    /** Fractional (x, y) position of each of the 4 corner markers, centers.
     *  See the companion object's markerFractions() — this instance method
     *  just delegates to it (kept for call-site convenience). */
    fun markerCenters(): List<Pair<Float, Float>> = markerFractions()

    /** (top, bottom) fraction bounds of a given row-group, at its fixed pitch. */
    fun rowGroupBounds(rowGroup: Int): Pair<Float, Float> {
        val top = contentTopOffset + rowGroup * (actualGroupHeight + actualGroupGap)
        return top to (top + actualGroupHeight)
    }

    /** (left, width) fraction bounds of a given block within its row-group. */
    fun blockBounds(rowGroup: Int, blockInRowGroup: Int): Pair<Float, Float> {
        val blocksHere = blocksInRowGroup(rowGroup)
        val blockWidth = (GRID_RIGHT - GRID_LEFT) / blocksHere
        val left = GRID_LEFT + blockInRowGroup * blockWidth
        return left to blockWidth
    }

    /** Uniform row height within a block: always rowsPerBlock slots tall, even
     *  if this particular block only uses some of them. This is what keeps
     *  item 21-25's row spacing identical to item 1-10's and 11-20's. */
    private fun rowHeightFor(rowGroup: Int): Float = actualGroupHeight / rowsPerBlock

    /** Row height (fraction of page height), same for every row-group by design. */
    val rowHeightFraction: Float
        get() = actualGroupHeight / rowsPerBlock

    /**
     * The tightest per-choice horizontal spacing (fraction of page width) across
     * all row-groups actually used. A row-group with fewer blocks side by side
     * has wider blocks (and thus roomier choice spacing); this returns the
     * narrowest case actually drawn, so bubble sizing can be bounded by it.
     */
    fun minChoiceSpacingFraction(): Float {
        var minSpacing = Float.MAX_VALUE
        for (rowGroup in 0 until rowGroupCount) {
            val (_, blockWidth) = blockBounds(rowGroup, 0)
            val labelWidth = blockWidth * LABEL_WIDTH_FRACTION
            val spacing = (blockWidth - labelWidth) / choiceCount
            if (spacing < minSpacing) minSpacing = spacing
        }
        return if (minSpacing == Float.MAX_VALUE) 0f else minSpacing
    }

    /**
     * Fractional center position (x, y) of the bubble for [questionNumber] (1-based)
     * and [choiceIndex] (0-based, 0=A).
     */
    fun bubbleCenter(questionNumber: Int, choiceIndex: Int): Pair<Float, Float> {
        val slot = slotFor(questionNumber)
        val (groupTop, _) = rowGroupBounds(slot.rowGroup)
        val (blockLeft, blockWidth) = blockBounds(slot.rowGroup, slot.blockInRowGroup)

        val rowHeight = rowHeightFor(slot.rowGroup)
        val y = groupTop + slot.rowInBlock * rowHeight + rowHeight / 2f

        val labelWidth = blockWidth * LABEL_WIDTH_FRACTION
        val choiceAreaWidth = blockWidth - labelWidth
        val choiceSpacing = choiceAreaWidth / choiceCount
        val x = blockLeft + labelWidth + choiceIndex * choiceSpacing + choiceSpacing / 2f

        return x to y
    }

    /** Fractional center for where the question-number label is drawn. */
    fun questionLabelCenter(questionNumber: Int): Pair<Float, Float> {
        val slot = slotFor(questionNumber)
        val (groupTop, _) = rowGroupBounds(slot.rowGroup)
        val (blockLeft, blockWidth) = blockBounds(slot.rowGroup, slot.blockInRowGroup)

        val rowHeight = rowHeightFor(slot.rowGroup)
        val y = groupTop + slot.rowInBlock * rowHeight + rowHeight / 2f

        val labelWidth = blockWidth * LABEL_WIDTH_FRACTION
        val x = blockLeft + labelWidth / 2f

        return x to y
    }

    /** Fraction x-position of the choice-letter header above a given block/choice. */
    fun choiceHeaderCenterX(rowGroup: Int, blockInRowGroup: Int, choiceIndex: Int): Float {
        val (blockLeft, blockWidth) = blockBounds(rowGroup, blockInRowGroup)
        val labelWidth = blockWidth * LABEL_WIDTH_FRACTION
        val choiceAreaWidth = blockWidth - labelWidth
        val choiceSpacing = choiceAreaWidth / choiceCount
        return blockLeft + labelWidth + choiceIndex * choiceSpacing + choiceSpacing / 2f
    }
}
