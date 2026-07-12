package com.tnhs.mcqscanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Draws ZipGrade-style color-coded feedback directly on the scanned/warped
 * sheet bitmap, instead of a separate text list:
 *  - green ring  = the correct answer's bubble (always shown)
 *  - red ring    = student picked this bubble and it's wrong
 *  - orange ring = the student's actual selection (drawn on top, so a wrong
 *                  pick shows red+orange together, a correct pick shows
 *                  green+orange together)
 */
object AnswerOverlayRenderer {

    private const val GREEN = Color.parseColor("#2E7D32")
    private const val RED = Color.parseColor("#C62828")
    private const val ORANGE = Color.parseColor("#B8860B") // darker yellow/goldenrod, per spec

    fun annotate(
        warped: Bitmap,
        template: SheetTemplate,
        scanResults: List<BubbleGrader.QuestionResult>,
        answerKey: List<Int>
    ): Bitmap {
        val out = warped.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()

        val ringRadius = SheetTemplate.BUBBLE_RADIUS_FRACTION * w * 1.6f
        val strokeWidth = (w * 0.004f).coerceAtLeast(3f)

        fun ringPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }
        val greenPaint = ringPaint(GREEN)
        val redPaint = ringPaint(RED)
        val orangePaint = ringPaint(ORANGE).apply { this.strokeWidth = strokeWidth * 1.4f }

        scanResults.forEach { r ->
            val correctIndex = answerKey.getOrNull(r.questionNumber - 1) ?: return@forEach
            val (cfx, cfy) = template.bubbleCenter(r.questionNumber, correctIndex)
            canvas.drawCircle(cfx * w, cfy * h, ringRadius, greenPaint)

            val chosen = r.chosenIndex
            if (chosen != null && chosen != correctIndex) {
                val (wfx, wfy) = template.bubbleCenter(r.questionNumber, chosen)
                canvas.drawCircle(wfx * w, wfy * h, ringRadius, redPaint)
            }
            if (chosen != null) {
                val (sfx, sfy) = template.bubbleCenter(r.questionNumber, chosen)
                canvas.drawCircle(sfx * w, sfy * h, ringRadius * 1.25f, orangePaint)
            }
        }
        return out
    }
}
