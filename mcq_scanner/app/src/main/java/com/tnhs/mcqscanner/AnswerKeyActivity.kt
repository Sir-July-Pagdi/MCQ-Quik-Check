package com.tnhs.mcqscanner

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Lets the teacher build (or edit) the answer key digitally: for every
 * question, tap the correct bubble instead of typing a letter or scanning a
 * physical key sheet. Each question also has an editable "points" box,
 * defaulting to 1 pt, with an "Apply to all" shortcut for uniform scoring.
 *
 * Built entirely from code-created views (no giant generated XML for however
 * many questions the test has) so it works the same for a 10-item quiz or a
 * 100-item exam.
 */
class AnswerKeyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEMPLATE = "template"
        const val EXTRA_EXISTING_KEY = "existing_key"
        const val EXTRA_EXISTING_POINTS = "existing_points"
        const val EXTRA_RESULT_KEY = "result_key"
        const val EXTRA_RESULT_POINTS = "result_points"
    }

    private lateinit var template: SheetTemplate
    private lateinit var selected: IntArray // -1 = unanswered, else 0-based choice index
    private lateinit var pointRows: Array<EditText>
    private lateinit var bubbleRowsButtons: Array<Array<TextView>>
    private lateinit var tvSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)

        template = (intent.getSerializableExtra(EXTRA_TEMPLATE) as? SheetTemplate) ?: run {
            Toast.makeText(this, "No test loaded — set up a test on the home screen first", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val existingKey = intent.getIntArrayExtra(EXTRA_EXISTING_KEY)
        val existingPoints = intent.getIntArrayExtra(EXTRA_EXISTING_POINTS)

        selected = IntArray(template.questionCount) { i -> existingKey?.getOrNull(i) ?: -1 }
        pointRows = Array(template.questionCount) { EditText(this) }
        bubbleRowsButtons = Array(template.questionCount) { emptyArray() }

        setContentView(buildLayout(existingPoints))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildLayout(existingPoints: IntArray?): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }

        val title = TextView(this).apply {
            text = "Create Answer Key\n${template.testName}"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            // Deliberately NOT tinted with colorPrimary here: in the Black
            // theme, colorPrimary is a near-black surface color meant for
            // backgrounds, not text — using it as text color made this
            // invisible. The theme's default text color is always readable
            // against its own window background.
        }
        root.addView(title)

        val hint = TextView(this).apply {
            text = "Tap the correct bubble for each item. Points default to 1 each — adjust any item, or set one value and apply it to all."
            textSize = 13f
            alpha = 0.8f
            setPadding(0, dp(6), 0, dp(12))
        }
        root.addView(hint)

        // Bulk "apply to all" points row.
        val bulkRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val bulkLabel = TextView(this).apply {
            text = "Points per item:"
            textSize = 14f
        }
        val bulkInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
            layoutParams = LinearLayout.LayoutParams(dp(60), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            }
        }
        val bulkApply = Button(this).apply {
            text = "Apply to All"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(12) }
            setOnClickListener {
                val pts = bulkInput.text.toString().toIntOrNull()
                if (pts == null || pts <= 0) {
                    Toast.makeText(this@AnswerKeyActivity, "Enter a valid point value", Toast.LENGTH_SHORT).show()
                } else {
                    pointRows.forEach { it.setText(pts.toString()) }
                    updateSummary()
                }
            }
        }
        bulkRow.addView(bulkLabel)
        bulkRow.addView(bulkInput)
        bulkRow.addView(bulkApply)
        root.addView(bulkRow)

        tvSummary = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(10), 0, dp(4))
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(tvSummary)

        // Scrollable list of one row per question.
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0
            ).apply { weight = 1f; topMargin = dp(4) }
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        for (q in 1..template.questionCount) {
            list.addView(buildQuestionRow(q, existingPoints))
        }
        scroll.addView(list)
        root.addView(scroll)

        val saveButton = Button(this).apply {
            text = "Save Answer Key"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            backgroundTintList = android.content.res.ColorStateList.valueOf(currentAccentColor())
            setTextColor(Color.BLACK) // gold background stays consistent across all 3 themes; black always reads on it
            setOnClickListener { onSaveClicked() }
        }
        root.addView(saveButton)

        updateSummary()
        return root
    }

    private fun currentAccentColor(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, typedValue, true)
        return typedValue.data
    }

    /** The theme's own default text color — correct against that theme's own
     *  background by design, unlike colorPrimary (which is a near-black
     *  surface color in the Black theme, not meant for text) or colorAccent
     *  (gold, which is too low-contrast against the light theme's cream
     *  background to use for body text). */
    private fun currentTextColor(): Int {
        val typedArray = obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        val color = typedArray.getColor(0, Color.BLACK)
        typedArray.recycle()
        return color
    }

    private fun buildQuestionRow(question: Int, existingPoints: IntArray?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }

        val label = TextView(this).apply {
            text = "$question."
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(label)

        val bubbleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1f }
        }

        val buttons = Array(template.choiceCount) { choiceIndex ->
            makeBubbleButton(question, choiceIndex).also { bubbleRow.addView(it) }
        }
        bubbleRowsButtons[question - 1] = buttons
        row.addView(bubbleRow)

        val ptsField = pointRows[question - 1].apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText((existingPoints?.getOrNull(question - 1) ?: 1).toString())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val ptsLabel = TextView(this).apply {
            text = "pt"
            textSize = 12f
            alpha = 0.7f
            setPadding(dp(4), 0, 0, 0)
        }
        row.addView(ptsField)
        row.addView(ptsLabel)

        refreshBubbleRow(question)
        return row
    }

    private fun makeBubbleButton(question: Int, choiceIndex: Int): TextView {
        val letter = template.choiceLetters[choiceIndex]
        val size = dp(36)
        return TextView(this).apply {
            text = letter.toString()
            textSize = 15f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                selected[question - 1] = choiceIndex
                refreshBubbleRow(question)
                updateSummary()
            }
        }
    }

    private fun refreshBubbleRow(question: Int) {
        val accent = currentAccentColor()
        val textColor = currentTextColor()
        val chosen = selected[question - 1]
        bubbleRowsButtons[question - 1].forEachIndexed { i, button ->
            val isChosen = i == chosen
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (isChosen) {
                    setStroke(dp(2), textColor)
                    setColor(accent)
                } else {
                    setStroke(dp(2), textColor)
                    setColor(Color.TRANSPARENT)
                }
            }
            button.background = drawable
            // A gold-filled bubble always reads with black text, regardless of
            // theme. An unselected (transparent-fill) bubble uses the theme's
            // own text color, which is guaranteed to read against that theme's
            // own background.
            button.setTextColor(if (isChosen) Color.BLACK else textColor)
        }
    }

    private fun updateSummary() {
        val answered = selected.count { it >= 0 }
        val totalPoints = pointRows.sumOf { it.text.toString().toIntOrNull() ?: 1 }
        tvSummary.text = "$answered / ${template.questionCount} answered • $totalPoints pts total"
    }

    private fun onSaveClicked() {
        val unanswered = selected.indexOfFirst { it < 0 }
        if (unanswered >= 0) {
            Toast.makeText(this, "Question ${unanswered + 1} has no answer selected yet", Toast.LENGTH_SHORT).show()
            return
        }
        val points = pointRows.map { it.text.toString().toIntOrNull()?.takeIf { p -> p > 0 } ?: 1 }

        val resultIntent = Intent()
            .putExtra(EXTRA_RESULT_KEY, selected)
            .putExtra(EXTRA_RESULT_POINTS, points.toIntArray())
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Discard answer key?")
            .setMessage("Your taps on this screen haven't been saved yet.")
            .setPositiveButton("Discard") { _, _ -> super.onBackPressed() }
            .setNegativeButton("Keep Editing", null)
            .show()
    }
}
