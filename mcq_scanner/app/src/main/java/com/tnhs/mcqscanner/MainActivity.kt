package com.tnhs.mcqscanner

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvTemplateSummary: TextView
    private lateinit var tvAnswerKeyStatus: TextView
    private lateinit var tvVersion: TextView

    // The test currently configured (via "Generate Answer Sheet Template" or
    // Load Test) — replaces the old inline Test Name/Question Count/Choices/
    // Paper Size fields, which now live on their own screen. Answer key
    // creation also now happens on that screen, so this activity just
    // receives the result of both together.
    private var template: SheetTemplate? = null
    private var paperLayout: PaperLayout = PaperLayout.HALF_CROSS
    private var currentAnswerKey: List<Int>? = null
    private var currentPoints: List<Int>? = null

    private val generateSheetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val newTemplate = result.data?.getSerializableExtra(GenerateSheetActivity.EXTRA_RESULT_TEMPLATE) as? SheetTemplate
            val newLayoutName = result.data?.getStringExtra(GenerateSheetActivity.EXTRA_RESULT_PAPER_LAYOUT)
            val newKey = result.data?.getIntArrayExtra(GenerateSheetActivity.EXTRA_RESULT_ANSWER_KEY)
            val newPoints = result.data?.getIntArrayExtra(GenerateSheetActivity.EXTRA_RESULT_POINTS)
            if (newTemplate != null) {
                template = newTemplate
                paperLayout = PaperLayout.fromStorageName(newLayoutName)
                if (newKey != null) {
                    currentAnswerKey = newKey.toList()
                    currentPoints = newPoints?.toList() ?: List(newKey.size) { 1 }
                }
                updateTemplateSummary()
                updateAnswerKeyStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTemplateSummary = findViewById(R.id.tvTemplateSummary)
        tvAnswerKeyStatus = findViewById(R.id.tvAnswerKeyStatus)
        tvVersion = findViewById(R.id.tvVersion)

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        tvVersion.text = "v$versionName"
        tvVersion.setOnClickListener {
            startActivity(Intent(this, ChangelogActivity::class.java))
        }

        findViewById<TextView>(R.id.btnTheme).setOnClickListener {
            ThemePrefs.cycleTheme(this)
            recreate()
        }

        findViewById<Button>(R.id.btnGenerateSheet).setOnClickListener {
            launchGenerateSheet()
        }

        findViewById<Button>(R.id.btnSaveTest).setOnClickListener {
            saveCurrentTest()
        }

        findViewById<Button>(R.id.btnLoadTest).setOnClickListener {
            loadTestPicker()
        }

        findViewById<Button>(R.id.btnScan).setOnClickListener {
            startScanning()
        }

        updateTemplateSummary()
        updateAnswerKeyStatus()
    }

    private fun launchGenerateSheet() {
        val intent = Intent(this, GenerateSheetActivity::class.java)
        template?.let {
            intent.putExtra(GenerateSheetActivity.EXTRA_EXISTING_TEMPLATE, it)
            intent.putExtra(GenerateSheetActivity.EXTRA_EXISTING_PAPER_LAYOUT, paperLayout.name)
        }
        currentAnswerKey?.let {
            intent.putExtra(GenerateSheetActivity.EXTRA_ANSWER_KEY, it.toIntArray())
            intent.putExtra(GenerateSheetActivity.EXTRA_ANSWER_KEY_POINTS, (currentPoints ?: List(it.size) { 1 }).toIntArray())
        }
        generateSheetLauncher.launch(intent)
    }

    private fun requireTemplate(): SheetTemplate? {
        val t = template
        if (t == null) {
            Toast.makeText(this, "Tap \"Generate Answer Sheet Template\" first", Toast.LENGTH_SHORT).show()
        }
        return t
    }

    private fun startScanning() {
        val t = requireTemplate() ?: return
        val key = currentAnswerKey
        if (key == null || key.size != t.questionCount) {
            Toast.makeText(
                this,
                "Set up the answer key first from \"Generate Answer Sheet Template\"",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val points = currentPoints ?: List(key.size) { 1 }
        ActiveTest.current = SavedTest(t, key, paperLayout, points)
        val intent = Intent(this, ScanActivity::class.java)
            .putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_GRADE)
        startActivity(intent)
    }

    private fun updateTemplateSummary() {
        val t = template
        tvTemplateSummary.text = if (t == null) {
            "No template set up yet"
        } else {
            "${t.testName} — ${t.questionCount} questions, A–${('A' + t.choiceCount - 1)}, ${paperLayout.label}"
        }
    }

    private fun updateAnswerKeyStatus() {
        val key = currentAnswerKey
        val questionCount = template?.questionCount
        tvAnswerKeyStatus.text = when {
            key == null -> "No answer key set yet"
            questionCount != null && key.size != questionCount ->
                "Answer key has ${key.size} items — doesn't match $questionCount questions. Recreate it."
            else -> {
                val totalPoints = (currentPoints ?: List(key.size) { 1 }).sum()
                "Answer key set — ${key.size} items, $totalPoints pts total"
            }
        }
    }

    private fun saveCurrentTest() {
        val t = requireTemplate() ?: return
        val key = currentAnswerKey
        if (key == null || key.size != t.questionCount) {
            Toast.makeText(
                this,
                "Set up the answer key first from \"Generate Answer Sheet Template\"",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val points = currentPoints ?: List(key.size) { 1 }
        TestStorage.saveTest(this, SavedTest(t, key, paperLayout, points))
        Toast.makeText(this, "Saved \"${t.testName}\"", Toast.LENGTH_SHORT).show()
    }

    private fun loadTestPicker() {
        val listings = TestStorage.listTestsByRecency(this).toMutableList()
        if (listings.isEmpty()) {
            Toast.makeText(this, "No saved tests yet", Toast.LENGTH_SHORT).show()
            return
        }

        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val dp = resources.displayMetrics.density
        val dateFormat = java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault())

        lateinit var dialog: AlertDialog

        fun rebuildRows() {
            listContainer.removeAllViews()
            listings.forEach { listing ->
                val name = listing.name
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                val textColumn = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val nameView = TextView(this).apply {
                    text = name
                    textSize = 16f
                }
                val dateView = TextView(this).apply {
                    text = if (listing.lastEdited > 0L) dateFormat.format(java.util.Date(listing.lastEdited)) else ""
                    textSize = 12f
                    alpha = 0.6f
                }
                textColumn.addView(nameView)
                textColumn.addView(dateView)
                textColumn.setOnClickListener {
                    val saved = TestStorage.loadTest(this@MainActivity, name)
                    if (saved != null) {
                        template = saved.template
                        paperLayout = saved.paperLayout
                        currentAnswerKey = saved.answerKey
                        currentPoints = saved.points
                        updateTemplateSummary()
                        updateAnswerKeyStatus()
                    }
                    dialog.dismiss()
                }
                val deleteButton = TextView(this).apply {
                    text = "🗑"
                    textSize = 18f
                    setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Delete test")
                            .setMessage("Delete \"$name\"? This can't be undone.")
                            .setPositiveButton("Delete") { _, _ ->
                                TestStorage.deleteTest(this@MainActivity, name)
                                listings.remove(listing)
                                if (listings.isEmpty()) dialog.dismiss() else rebuildRows()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
                row.addView(textColumn)
                row.addView(deleteButton)
                listContainer.addView(row)
            }
        }
        rebuildRows()

        dialog = AlertDialog.Builder(this)
            .setTitle("Load Test")
            .setView(listContainer)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }
}
