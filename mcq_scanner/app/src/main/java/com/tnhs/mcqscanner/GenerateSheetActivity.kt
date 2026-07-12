package com.tnhs.mcqscanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

/**
 * Dedicated screen for configuring a test's sheet template — test name,
 * question count, subject teacher, choice count, paper layout, and
 * columns/questions-per-column — with a live preview that re-renders on
 * every change using the exact same drawing code as the real printed PDF
 * (SheetGenerator.renderPreviewBitmap), so what's shown here always matches
 * what actually prints. Generate & Share PDF also lives here now, right next
 * to the preview that shows exactly what it's about to produce.
 *
 * Answer key creation also lives here now (tap Create Answer Key -> choose
 * Create Manually or Scan Answer Key Instead) rather than being split across
 * separate buttons on the home screen — both the template AND whatever key
 * was created in this session are returned together to MainActivity.
 */
class GenerateSheetActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EXISTING_TEMPLATE = "existing_template"
        const val EXTRA_EXISTING_PAPER_LAYOUT = "existing_paper_layout"
        const val EXTRA_ANSWER_KEY = "answer_key"
        const val EXTRA_ANSWER_KEY_POINTS = "answer_key_points"

        const val EXTRA_RESULT_TEMPLATE = "result_template"
        const val EXTRA_RESULT_PAPER_LAYOUT = "result_paper_layout"
        const val EXTRA_RESULT_ANSWER_KEY = "result_answer_key"
        const val EXTRA_RESULT_POINTS = "result_points"
    }

    private lateinit var etTestName: EditText
    private lateinit var etQuestionCount: EditText
    private lateinit var etSubjectTeacher: EditText
    private lateinit var rgChoiceCount: RadioGroup
    private lateinit var rgPaperLayout: RadioGroup
    private lateinit var etColumnsValue: EditText
    private lateinit var etRowsValue: EditText
    private lateinit var tvLayoutHint: TextView
    private lateinit var tvPreviewNote: TextView
    private lateinit var tvKeyStatus: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var btnPreviewKey: Button

    private var columns = 3
    private var rows = 10
    private var showingKeyPreview = false
    private var answerKey: List<Int>? = null
    private var answerPoints: List<Int>? = null
    private var suppressFieldEvents = false

    private val createKeyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val key = result.data?.getIntArrayExtra(AnswerKeyActivity.EXTRA_RESULT_KEY)
            val points = result.data?.getIntArrayExtra(AnswerKeyActivity.EXTRA_RESULT_POINTS)
            if (key != null) {
                answerKey = key.toList()
                answerPoints = points?.toList() ?: List(key.size) { 1 }
                onKeyUpdated()
                Toast.makeText(this, "Answer key saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val scanKeyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val scannedKey = result.data?.getStringExtra(ScanActivity.EXTRA_SCANNED_KEY)
        if (result.resultCode == RESULT_OK && scannedKey != null) {
            val choiceCount = if (rgChoiceCount.checkedRadioButtonId == R.id.rb5Choices) 5 else 4
            try {
                val parsed = AnswerParser.parseKey(scannedKey, choiceCount)
                answerKey = parsed
                answerPoints = List(parsed.size) { 1 }
                onKeyUpdated()
                Toast.makeText(this, "Answer key filled from scan — please double-check it", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Scanned key error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_sheet)

        etTestName = findViewById(R.id.etTestName)
        etQuestionCount = findViewById(R.id.etQuestionCount)
        etSubjectTeacher = findViewById(R.id.etSubjectTeacher)
        rgChoiceCount = findViewById(R.id.rgChoiceCount)
        rgPaperLayout = findViewById(R.id.rgPaperLayout)
        etColumnsValue = findViewById(R.id.etColumnsValue)
        etRowsValue = findViewById(R.id.etRowsValue)
        tvLayoutHint = findViewById(R.id.tvLayoutHint)
        tvPreviewNote = findViewById(R.id.tvPreviewNote)
        tvKeyStatus = findViewById(R.id.tvKeyStatus)
        ivPreview = findViewById(R.id.ivPreview)
        btnPreviewKey = findViewById(R.id.btnPreviewKey)

        @Suppress("DEPRECATION")
        val existingTemplate = intent.getSerializableExtra(EXTRA_EXISTING_TEMPLATE) as? SheetTemplate
        val existingLayoutName = intent.getStringExtra(EXTRA_EXISTING_PAPER_LAYOUT)
        val keyArray = intent.getIntArrayExtra(EXTRA_ANSWER_KEY)
        val pointsArray = intent.getIntArrayExtra(EXTRA_ANSWER_KEY_POINTS)
        answerKey = keyArray?.toList()
        answerPoints = pointsArray?.toList() ?: answerKey?.let { List(it.size) { 1 } }

        if (existingTemplate != null) {
            etTestName.setText(existingTemplate.testName)
            etQuestionCount.setText(existingTemplate.questionCount.toString())
            etSubjectTeacher.setText(existingTemplate.subjectTeacher)
            rgChoiceCount.check(if (existingTemplate.choiceCount == 5) R.id.rb5Choices else R.id.rb4Choices)
            rgPaperLayout.check(
                when (PaperLayout.fromStorageName(existingLayoutName)) {
                    PaperLayout.HALF_LENGTH -> R.id.rbHalfLength
                    PaperLayout.QUARTER -> R.id.rbQuarterPage
                    PaperLayout.HALF_CROSS -> R.id.rbHalfCross
                }
            )
            columns = existingTemplate.maxBlocksPerRowGroup
            rows = existingTemplate.rowsPerBlock
        }

        findViewById<Button>(R.id.btnColumnsMinus).setOnClickListener { adjustColumns(-1) }
        findViewById<Button>(R.id.btnColumnsPlus).setOnClickListener { adjustColumns(1) }
        findViewById<Button>(R.id.btnRowsMinus).setOnClickListener { adjustRows(-1) }
        findViewById<Button>(R.id.btnRowsPlus).setOnClickListener { adjustRows(1) }

        findViewById<Button>(R.id.btnCreateKey).setOnClickListener { showCreateKeyDialog() }

        findViewById<Button>(R.id.btnPreviewBlank).setOnClickListener {
            showingKeyPreview = false
            refreshPreview()
        }
        btnPreviewKey.setOnClickListener {
            showingKeyPreview = true
            refreshPreview()
        }

        findViewById<Button>(R.id.btnUseTemplate).setOnClickListener { useTemplate() }
        findViewById<Button>(R.id.btnGeneratePdf).setOnClickListener { generateAndSharePdf() }

        etTestName.addTextChangedListener(afterChange = { refreshPreview() })
        etSubjectTeacher.addTextChangedListener(afterChange = { refreshPreview() })
        etQuestionCount.addTextChangedListener(afterChange = {
            clampColumnsAndRows()
            refreshPreview()
        })
        etColumnsValue.addTextChangedListener(afterChange = {
            if (suppressFieldEvents) return@addTextChangedListener
            val typed = etColumnsValue.text.toString().toIntOrNull()
            if (typed != null) {
                columns = typed.coerceIn(SheetTemplate.maxBlocksPerRowGroupRange(paperLayout()))
                refreshPreview()
            }
        })
        etRowsValue.addTextChangedListener(afterChange = {
            if (suppressFieldEvents) return@addTextChangedListener
            val count = etQuestionCount.text.toString().toIntOrNull() ?: 1
            val typed = etRowsValue.text.toString().toIntOrNull()
            if (typed != null) {
                rows = typed.coerceIn(SheetTemplate.rowsPerBlockRange(count))
                refreshPreview()
            }
        })
        rgChoiceCount.setOnCheckedChangeListener { _, _ -> refreshPreview() }
        rgPaperLayout.setOnCheckedChangeListener { _, _ ->
            clampColumnsAndRows()
            refreshPreview()
        }

        clampColumnsAndRows()
        onKeyUpdated()
        refreshPreview()
    }

    private fun showCreateKeyDialog() {
        val count = etQuestionCount.text.toString().toIntOrNull()
        if (count == null || count <= 0) {
            Toast.makeText(this, "Enter a valid number of questions first", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Create Answer Key")
            .setItems(arrayOf("Create Manually", "Scan Answer Key Instead")) { _, which ->
                val template = buildTemplate() ?: return@setItems
                if (which == 0) {
                    val intent = Intent(this, AnswerKeyActivity::class.java)
                        .putExtra(AnswerKeyActivity.EXTRA_TEMPLATE, template)
                    val existingKey = answerKey
                    if (existingKey != null && existingKey.size == template.questionCount) {
                        intent.putExtra(AnswerKeyActivity.EXTRA_EXISTING_KEY, existingKey.toIntArray())
                        intent.putExtra(
                            AnswerKeyActivity.EXTRA_EXISTING_POINTS,
                            (answerPoints ?: List(existingKey.size) { 1 }).toIntArray()
                        )
                    }
                    createKeyLauncher.launch(intent)
                } else {
                    ActiveTest.current = SavedTest(template, emptyList(), paperLayout())
                    val intent = Intent(this, ScanActivity::class.java)
                        .putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_KEY)
                    scanKeyLauncher.launch(intent)
                }
            }
            .show()
    }

    private fun onKeyUpdated() {
        val key = answerKey
        val count = etQuestionCount.text.toString().toIntOrNull()
        tvKeyStatus.text = when {
            key == null -> "No answer key set yet"
            count != null && key.size != count ->
                "Answer key has ${key.size} items — doesn't match $count questions. Recreate it."
            else -> {
                val total = (answerPoints ?: List(key.size) { 1 }).sum()
                "Answer key set — ${key.size} items, $total pts total"
            }
        }
        btnPreviewKey.isEnabled = key != null
        if (key == null) {
            tvPreviewNote.visibility = android.view.View.VISIBLE
            tvPreviewNote.text = "Create an answer key above to preview it filled in."
        } else {
            tvPreviewNote.visibility = android.view.View.GONE
        }
    }

    private fun adjustColumns(delta: Int) {
        val range = SheetTemplate.maxBlocksPerRowGroupRange(paperLayout())
        columns = (columns + delta).coerceIn(range)
        setFieldTextSilently(etColumnsValue, columns.toString())
        refreshPreview()
    }

    private fun adjustRows(delta: Int) {
        val count = etQuestionCount.text.toString().toIntOrNull() ?: 1
        val range = SheetTemplate.rowsPerBlockRange(count)
        rows = (rows + delta).coerceIn(range)
        setFieldTextSilently(etRowsValue, rows.toString())
        refreshPreview()
    }

    /** Clamps columns/rows into range whenever question count or paper layout changes,
     *  so a value that was valid before doesn't silently become invalid. */
    private fun clampColumnsAndRows() {
        val count = etQuestionCount.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
        val rowsRange = SheetTemplate.rowsPerBlockRange(count)
        val colsRange = SheetTemplate.maxBlocksPerRowGroupRange(paperLayout())
        rows = rows.coerceIn(rowsRange)
        columns = columns.coerceIn(colsRange)
        setFieldTextSilently(etRowsValue, rows.toString())
        setFieldTextSilently(etColumnsValue, columns.toString())
        tvLayoutHint.text = "Up to ${rowsRange.last} questions per column, ${colsRange.last} columns for this paper layout"
    }

    /** Updates a field's text without re-triggering its own change listener
     *  (used when the STEPPER buttons or clamping change the value — typing
     *  directly into the field still goes through the normal listener). */
    private fun setFieldTextSilently(field: EditText, text: String) {
        suppressFieldEvents = true
        if (field.text.toString() != text) field.setText(text)
        suppressFieldEvents = false
    }

    private fun paperLayout(): PaperLayout = when (rgPaperLayout.checkedRadioButtonId) {
        R.id.rbHalfLength -> PaperLayout.HALF_LENGTH
        R.id.rbQuarterPage -> PaperLayout.QUARTER
        else -> PaperLayout.HALF_CROSS
    }

    private fun buildTemplate(): SheetTemplate? {
        val name = etTestName.text.toString().ifBlank { "Untitled Test" }
        val count = etQuestionCount.text.toString().toIntOrNull()
        if (count == null || count <= 0) return null
        val choiceCount = if (rgChoiceCount.checkedRadioButtonId == R.id.rb5Choices) 5 else 4

        return SheetTemplate(
            testName = name,
            questionCount = count,
            choiceCount = choiceCount,
            numbering = NumberingOrder.COLUMN_MAJOR,
            rowsPerBlock = rows,
            maxBlocksPerRowGroup = columns,
            subjectTeacher = etSubjectTeacher.text.toString().trim()
        )
    }

    private fun refreshPreview() {
        val template = buildTemplate() ?: return
        val previewKey = if (showingKeyPreview) answerKey?.takeIf { it.size == template.questionCount } else null
        if (showingKeyPreview && previewKey == null) {
            Toast.makeText(this, "Answer key doesn't match the current question count", Toast.LENGTH_SHORT).show()
            showingKeyPreview = false
        }
        val widthPx = 800
        val bitmap = SheetGenerator.renderPreviewBitmap(template, paperLayout(), widthPx, previewKey)
        ivPreview.setImageBitmap(bitmap)
    }

    private fun useTemplate() {
        val template = buildTemplate()
        if (template == null) {
            Toast.makeText(this, "Enter a valid number of questions", Toast.LENGTH_SHORT).show()
            return
        }
        val resultIntent = Intent()
            .putExtra(EXTRA_RESULT_TEMPLATE, template)
            .putExtra(EXTRA_RESULT_PAPER_LAYOUT, paperLayout().name)
        val key = answerKey
        if (key != null && key.size == template.questionCount) {
            resultIntent.putExtra(EXTRA_RESULT_ANSWER_KEY, key.toIntArray())
            resultIntent.putExtra(EXTRA_RESULT_POINTS, (answerPoints ?: List(key.size) { 1 }).toIntArray())
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun generateAndSharePdf() {
        val template = buildTemplate()
        if (template == null) {
            Toast.makeText(this, "Enter a valid number of questions", Toast.LENGTH_SHORT).show()
            return
        }
        val dir = File(getExternalFilesDir(null), "pdfs").apply { mkdirs() }
        val file = File(dir, "${template.testName}.pdf")
        SheetGenerator.generate(template, file, paperLayout())

        val uri: Uri = FileProvider.getUriForFile(this, "com.tnhs.mcqscanner.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share answer sheet PDF"))
    }
}

/** Small helper so text-change listeners can be added as a single trailing lambda. */
private fun EditText.addTextChangedListener(afterChange: () -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = afterChange()
    })
}
