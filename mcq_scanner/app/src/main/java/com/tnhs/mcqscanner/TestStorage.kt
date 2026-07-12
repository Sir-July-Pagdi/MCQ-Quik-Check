package com.tnhs.mcqscanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedTest(
    val template: SheetTemplate,
    val answerKey: List<Int>,
    val paperLayout: PaperLayout = PaperLayout.HALF_CROSS,
    // Points awarded for each question, in order. Defaults to 1 pt each when
    // not customized. Size should match template.questionCount once a key
    // has been created.
    val points: List<Int> = List(answerKey.size) { 1 },
    // When this test was last saved (epoch millis) — used to sort the Load
    // Test list by most-recently-edited first. Defaults to "now" so a test
    // built in memory but not yet saved still sorts sensibly.
    val lastEdited: Long = System.currentTimeMillis()
) {
    val totalPoints: Int get() = points.sum()
}

/** Holds the test currently loaded in MainActivity, so ScanActivity knows what to grade against. */
object ActiveTest {
    var current: SavedTest? = null
}

/**
 * Stores/loads full SheetTemplate + answer key together, per named test, using
 * SharedPreferences with hand-rolled JSON (no external serialization library
 * needed for this small amount of data).
 */
object TestStorage {
    private const val PREFS = "mcq_scanner_tests"

    fun saveTest(context: Context, test: SavedTest) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Stamp with "now" on every save, regardless of whatever lastEdited
        // the passed-in SavedTest already carried — this is the moment the
        // edit is actually committed to storage.
        val json = toJson(test.copy(lastEdited = System.currentTimeMillis()))
        prefs.edit().putString(test.template.testName, json.toString()).apply()
    }

    fun loadTest(context: Context, name: String): SavedTest? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(name, null) ?: return null
        return fromJson(JSONObject(raw))
    }

    fun listTestNames(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.keys.sorted()
    }

    /** Test name + when it was last saved, most-recently-edited first — what
     *  the Load Test picker actually displays. */
    data class TestListing(val name: String, val lastEdited: Long)

    fun listTestsByRecency(context: Context): List<TestListing> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.keys.mapNotNull { name ->
            val raw = prefs.getString(name, null) ?: return@mapNotNull null
            val lastEdited = try {
                JSONObject(raw).optLong("lastEdited", 0L)
            } catch (e: Exception) {
                0L
            }
            TestListing(name, lastEdited)
        }.sortedByDescending { it.lastEdited }
    }

    fun deleteTest(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(name).apply()
    }

    private fun toJson(test: SavedTest): JSONObject {
        val t = test.template
        val obj = JSONObject()
        obj.put("testName", t.testName)
        obj.put("questionCount", t.questionCount)
        obj.put("choiceCount", t.choiceCount)
        obj.put("numbering", t.numbering.name)
        obj.put("rowsPerBlock", t.rowsPerBlock)
        obj.put("maxBlocksPerRowGroup", t.maxBlocksPerRowGroup)
        obj.put("subjectTeacher", t.subjectTeacher)
        obj.put("paperLayout", test.paperLayout.name)
        obj.put("answerKey", JSONArray(test.answerKey))
        obj.put("points", JSONArray(test.points))
        obj.put("lastEdited", test.lastEdited)
        return obj
    }

    private fun fromJson(obj: JSONObject): SavedTest {
        val template = SheetTemplate(
            testName = obj.getString("testName"),
            questionCount = obj.getInt("questionCount"),
            choiceCount = obj.getInt("choiceCount"),
            numbering = NumberingOrder.valueOf(obj.getString("numbering")),
            rowsPerBlock = obj.optInt("rowsPerBlock", 10),
            maxBlocksPerRowGroup = obj.optInt("maxBlocksPerRowGroup", 3),
            subjectTeacher = obj.optString("subjectTeacher", "")
        )
        val keyArray = obj.getJSONArray("answerKey")
        val key = (0 until keyArray.length()).map { keyArray.getInt(it) }
        // Older saved tests won't have a "points" array — default to 1 pt each.
        val points = if (obj.has("points")) {
            val pointsArray = obj.getJSONArray("points")
            (0 until pointsArray.length()).map { pointsArray.getInt(it) }
        } else {
            List(key.size) { 1 }
        }
        // Older saved tests have "sheetsPerPage" (an Int: 1/2/4) instead of the
        // newer "paperLayout" enum name — 1 (full page) no longer exists as an
        // option, so it falls back to HALF_CROSS same as a missing value would.
        val layout = if (obj.has("paperLayout")) {
            PaperLayout.fromStorageName(obj.optString("paperLayout"))
        } else {
            when (obj.optInt("sheetsPerPage", 2)) {
                4 -> PaperLayout.QUARTER
                else -> PaperLayout.HALF_CROSS
            }
        }
        // Older saved tests won't have "lastEdited" — 0L sorts them last
        // (oldest) in the recency-ordered Load Test list, which is the
        // sensible default for a test we have no real edit time for.
        val lastEdited = obj.optLong("lastEdited", 0L)
        return SavedTest(template, key, layout, points, lastEdited)
    }
}
