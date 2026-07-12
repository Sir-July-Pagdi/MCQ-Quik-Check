package com.tnhs.mcqscanner

/**
 * How many copies of a sheet are tiled onto one physical page, and in what
 * orientation. Replaces the old `sheetsPerPage: Int` (1/2/4) now that there
 * are two different ways to lay out a half page:
 *
 * - HALF_CROSS: two copies stacked top/bottom, each landscape-oriented —
 *   matches a crosswise ("pahalang") fold, cutting across the shorter side.
 * - HALF_LENGTH: two copies side by side, each a narrow portrait column —
 *   matches a lengthwise ("pahaba") fold, cutting along the longer side.
 * - QUARTER: four copies in a 2x2 grid (same either way).
 *
 * Full page (a single sheet using the whole page) was removed as an option —
 * every test now prints at least 2-up.
 */
enum class PaperLayout(val tileCount: Int, val label: String) {
    HALF_CROSS(2, "½ cross"),
    HALF_LENGTH(2, "½ length"),
    QUARTER(4, "¼ page");

    companion object {
        fun fromStorageName(name: String?): PaperLayout =
            entries.firstOrNull { it.name == name } ?: HALF_CROSS
    }
}
