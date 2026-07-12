package com.tnhs.mcqscanner

data class ChangelogEntry(val version: String, val date: String, val notes: List<String>)

/**
 * In-app mirror of CHANGELOG.md. Keep both in sync when bumping versionName.
 * Newest entry first.
 */
object Changelog {
    val entries = listOf(
        ChangelogEntry(
            version = "1.4",
            date = "2026-07-12",
            notes = listOf(
                "Load Test list now sorts by most-recently-edited first, with the save date shown in smaller text under each test name.",
                "Fixed 'Cancel' button text being unreadable in dialogs on Dark and Black themes — now forced white.",
                "Fixed ½ length and ¼ page corner registration squares printing smaller than ½ cross's — all three paper layouts now print the same physical marker size.",
                "Scan result screen redesigned ZipGrade-style: the scanned sheet now shows color-coded feedback directly on the image (green ring = correct answer, red ring = wrong pick, orange ring = the student's selection) instead of a separate per-question text list.",
                "'Scan Next' and 'Done' button text now forced white on Dark and Black themes.",
                "Rebuilt the live alignment detector: it now detects the paper's actual boundary first and searches for registration markers at the real corners (not just the assumed guide position), and validates candidates using contour hierarchy (ring/hole shape) plus geometric checks (squareness, size, circularity) so it no longer reacts to answer bubbles or other dark shapes in the scene."
            )
        ),
        ChangelogEntry(
            version = "1.3",
            date = "2026-07-12",
            notes = listOf(
                "Fixed the version number not opening the changelog: the fixed footer was declared before the ScrollView in the layout, so the ScrollView (drawn on top) was silently swallowing taps meant for it, even in its own empty bottom padding. Reordered so the footer draws on top.",
                "Home screen buttons now vertically center in the space below the header instead of always starting right under it.",
                "Widened the live alignment guide's search range to match the final scanner's proven range (was only checking 3 zoom levels, now checks 5 across a much wider range) — should turn green far more reliably when the sheet is actually aligned.",
                "Redesigned the scan result screen: score shown big at the top (always visible), Scan Next/Done pinned at the bottom (always visible), with only the photo and per-question breakdown scrolling in between — fixes the buttons getting pushed off-screen on longer tests.",
                "Question numbers and choice letters on the printed sheet now stay a fixed, legible size regardless of how dense the layout gets — only the bubbles themselves shrink to fit more content."
            )
        ),
        ChangelogEntry(
            version = "1.2",
            date = "2026-07-12",
            notes = listOf(
                "Fixed a build-breaking bug in the v1.1 marker redesign: two constants in MarkerTemplateFactory were plain Doubles but got multiplied with Float values and passed straight into Canvas.drawRect (which requires Float) — Kotlin doesn't implicitly narrow Double to Float, so this failed the build. Fixed by declaring them as Float from the start.",
                "Paper layout overhaul: removed Full page as an option. Renamed ½ page to ½ cross (the existing top/bottom stacked layout) and added a new ½ length option (side-by-side halves instead, for a lengthwise fold). Column limits: up to 6 for ½ cross, 3 for ½ length, 3 for ¼ page.",
                "Answer key creation moved onto the Generate Answer Sheet Template screen — tap Create Answer Key for a choice of Create Manually or Scan Answer Key Instead. Whatever key you build there now returns together with the template.",
                "Load Test now supports deleting a saved test directly from the list (🗑 next to each entry, with a confirmation step).",
                "Columns and questions-per-column can now be typed directly, not just adjusted with +/−.",
                "Fixed unreadable Answer Key Preview button text in Dark/Black themes.",
                "Version number and developer credit moved to a fixed footer at the bottom of the home screen instead of scrolling with the content.",
                "Renamed the app to MCQ Quick Check.",
                "Replaced the school logo/name in the header with the new app logo and a larger app name, and swapped the washed-out home screen background photo for a new exam-day photo."
            )
        ),
        ChangelogEntry(
            version = "1.1",
            date = "2026-07-11",
            notes = listOf(
                "Bubble grading now adapts its darkness threshold to each photo's own lighting (Otsu's method on that sheet's actual darkness scores) instead of a fixed brightness value — should meaningfully improve accuracy in dim lighting, not just bright/studio conditions.",
                "Redesigned the corner marker as a hollow ring (was a solid square) — much harder to confuse with rows of answer bubbles, which was causing the live alignment guide to react to shaded/unshaded bubbles almost as readily as the real corners.",
                "Fixed the ½-page template preview rendering distorted/broken — it was always assuming full-page proportions regardless of paper size.",
                "Moved Generate & Share PDF onto the template screen, right below Use This Template.",
                "Columns and questions-per-column can now be typed directly, not just adjusted with +/−. Renamed \"Rows per column\" to \"Questions per column\".",
                "Removed the border box around the bubble grid — it ran directly through the corner guides.",
                "Added a Subject Teacher field (typed once when generating the template) — printed on the sheet, filling what used to be dead space next to the Score cell and closing the table's border on all sides.",
                "Scan results now show the correct answer in red next to anything that wasn't marked correct (incorrect, ambiguous, or blank) — nothing shown for correct answers."
            )
        ),
        ChangelogEntry(
            version = "1.0",
            date = "2026-07-10",
            notes = listOf(
                "Fixed the real accuracy problem behind 'blanks even on clearly shaded bubbles': template matching alone only located markers to the resolution of its search grid, which wasn't precise enough for the perspective warp. Added a refinement step (Otsu thresholding + centroid) that locks onto the marker's true center after the coarse match, which should meaningfully improve grading accuracy.",
                "Loosened the white-paper-surround threshold — it was likely rejecting genuine sheets under normal (non-studio) indoor lighting, not just random scenes.",
                "Removed the big connecting rectangle from the live scan guide — just the 4 corner squares now, which are what you actually align to.",
                "New 'Generate Answer Sheet Template' screen: test name, question count, choices, and paper size moved here from the home screen, plus new columns/rows controls (with sensible limits per paper size) and a live preview that re-renders using the exact same drawing code as the real printed PDF — so the preview never drifts from what actually prints. If an answer key is already set, a second preview mode shows it filled in as a reference sheet.",
                "Home screen now shows a summary of the currently configured test instead of the raw fields, with a button to open the new template screen.",
                "Added the school facade photo as a washed-out background on the home screen (photo + a theme-colored scrim on top, so it reads as a faint texture in any of the 3 themes rather than competing with the text)."
            )
        ),
        ChangelogEntry(
            version = "0.9",
            date = "2026-07-09",
            notes = listOf(
                "Widened alignment tolerance 4x (shared by the on-screen guide size and the actual matching search radius, so what you see is honestly what's accepted) — should make it noticeably easier to land a capture.",
                "Added a second check alongside shape matching: the area around a matched marker must actually look like plain white/light paper, not just be roughly the right shape. This should stop the guide from lighting up on random dark scenes (grass, walls, tables, objects) that happened to have a square-ish dark patch but no actual sheet of paper around it.",
                "Fixed unreadable button text in Dark/Black themes on the home screen (Create Answer Key, Scan Answer Key, Generate & Share PDF, Save/Load Test) — same root cause as earlier text-contrast fixes, just missed on these specific buttons.",
                "Suppressed Android's system autofill icon showing up on action buttons (the stray green marks next to button labels) — these are buttons, not form fields.",
                "Restricted the app's native libraries to real-device CPU architectures only (dropping emulator-only x86/x86_64), cutting the APK size down substantially from the OpenCV upgrade in v0.8."
            )
        ),
        ChangelogEntry(
            version = "0.8",
            date = "2026-07-09",
            notes = listOf(
                "Replaced marker detection's hand-rolled darkness-heuristic with real OpenCV template matching (multi-scale, per corner) — the same core technique the open-source OMRChecker project uses. This is a shape check, not just a brightness check, so it should no longer mistake dark backgrounds (grass, walls, tables, random objects) for a marker, and should be more forgiving of real markers under uneven lighting.",
                "Restored cropping the captured photo to the on-screen guide region (plus padding) before running detection — this had been lost in an earlier rewrite, which meant detection was searching a fixed window of the WHOLE photo and could miss the real sheet entirely whenever it didn't fill the frame edge to edge. This was the main reason real sheets were failing to scan.",
                "The live green-corner hint now uses the same template-matching approach, searching a small window at each corner's exact expected position instead of a broad quadrant."
            )
        ),
        ChangelogEntry(
            version = "0.7",
            date = "2026-07-08",
            notes = listOf(
                "Removed auto-capture. Two attempts at tuning the live 'is this aligned' check (from screen recordings) still misfired on real devices — capturing on non-sheet objects, or firing while the sheet was still being positioned. Rather than keep guessing at thresholds I can't test myself, capture is manual again (tap the button), which was already reliable before auto-capture was added.",
                "The corner guide squares still turn green live as a visual aid — checking each corner for an actual small solid blob, not just 'is it dark here' — so you can see when it looks lined up before tapping Capture yourself.",
                "Fixed more invisible text in Dark/Black themes (the 'Result' title on the scan screen had the same colorPrimary-as-text bug as before)."
            )
        ),
        ChangelogEntry(
            version = "0.6",
            date = "2026-07-08",
            notes = listOf(
                "Added real-time alignment detection while scanning: each corner guide turns green live as the camera sees the sheet's marker in roughly the right spot — no need for pixel-perfect alignment, just enough overlap counts.",
                "Capture now happens automatically once all 4 corners stay aligned for about half a second. Manual capture (tap the button) still works too.",
                "Added vibration feedback on both auto-capture and manual capture.",
                "Loosened the post-capture marker validation thresholds — v0.4's checks were tuned by reasoning alone and turned out too strict for real paper/lighting; still rejects clutter, just with more real-world tolerance.",
                "Removed the underscore blanks from the printed sheet's Name/Date/Gr.&Section/Score cells now that they have real borders — the border is enough."
            )
        ),
        ChangelogEntry(
            version = "0.5",
            date = "2026-07-08",
            notes = listOf(
                "Fixed the camera not capturing what the live preview showed — preview and capture now share the same field of view (a CameraX ViewPort), so a sheet that filled the guide frame on screen no longer comes out tiny/off-center in the actual photo. This was the bug blocking scanning entirely.",
                "Fixed bubbles overlapping on ½-page sheets — bubble size is now derived from the tighter of actual row pitch and column spacing for each paper size, instead of page width alone.",
                "Redesigned the details header into a real bordered table: Name/Date on one row, Gr. & Section/Score on the next (Score cell taller, for writing room), all cells vertically centered.",
                "Moved the test title below the details table, with the top corner markers repositioned to flank it instead of sitting at the very top edge.",
                "Fixed more invisible text in Dark/Black themes (school name and version number on the home screen, title on the changelog screen) — same colorPrimary-as-text issue as the answer-key screen fix in v0.4."
            )
        ),
        ChangelogEntry(
            version = "0.4",
            date = "2026-07-08",
            notes = listOf(
                "Fixed the scan overlay guide not matching a ½-page sheet's actual shape — it now follows each paper size's real proportions (full, ½, and ¼ page).",
                "Much stricter sheet detection: the scanner now checks that each corner marker is actually a small solid square (not just 'the darkest spot nearby'), and rejects photos where the 4 corners don't form a plausible sheet outline — so pointing the camera at a random object no longer produces a fake score.",
                "Added a final sanity check after grading: if too many answers come out ambiguous or blank, the app now says so instead of reporting a misleading score.",
                "Fixed invisible text in the Dark and Black themes on the answer-key screen — some text and bubble outlines were using a color meant for backgrounds, not text."
            )
        ),
        ChangelogEntry(
            version = "0.3",
            date = "2026-07-07",
            notes = listOf(
                "Fixed a crash when tapping 'Create Answer Key Here' — the printable sheet layout was silently failing to pass between screens; save/load a test should now work reliably too.",
                "Added a boxed border around the Name/Grade/Section/Date/Score details area, and a separate box around the whole bubble grid, on the printed sheet.",
                "Removed the 'Down each column' / 'Across each row' numbering option — questions are always numbered down each column now.",
                "Scanning now happens inside the app with a live camera preview instead of jumping out to your phone's camera app, with on-screen guide squares to help line up the sheet's corner markers before capturing.",
                "The scan result now appears in a dedicated panel right after capture, with a 'Scan Next' option to keep grading sheets without leaving the screen."
            )
        ),
        ChangelogEntry(
            version = "0.2",
            date = "2026-07-07",
            notes = listOf(
                "Redesigned the printed answer sheet: bigger, bolder bubbles and text in a formal sans-serif style, condensed spacing instead of stretching to fill the page.",
                "Every block of questions now uses the same row spacing as item 1 and item 11, even a partial last block (e.g. 21-25) — no more oddly spread-out rows.",
                "If the sheet has extra vertical space, it's centered on the page instead of being stretched to fill it.",
                "Added Grade and Section fields below Name, and Date / Score fields on the right.",
                "Removed the typed answer-key field. Added 'Create Answer Key Here' — a tap-the-bubble screen generated from your question count, right above 'Scan Answer Key Instead'.",
                "Added customizable points per item on the new answer-key screen (defaults to 1 pt each, with an 'apply to all' shortcut).",
                "Scores are now reported in points earned / points possible, not just items correct."
            )
        ),
        ChangelogEntry(
            version = "0.1",
            date = "2026-07-07",
            notes = listOf(
                "Redesigned sheet layout: compact ZipGrade-style blocks of 10 questions, no student-ID grid, to save paper.",
                "Added per-test paper-size option: Full page, ½ page, or ¼ page (multiple sheets tiled per physical page, cut apart before handing out).",
                "Added 'Scan Answer Key' — capture a filled key sheet instead of typing the key by hand.",
                "Fixed camera not opening (blank screen) on Android 11+ by declaring package-visibility <queries> for the camera intent.",
                "Sheets are numbered dynamically based on however many questions the test has."
            )
        ),
        ChangelogEntry(
            version = "0.0",
            date = "2026-07-07",
            notes = listOf(
                "Fresh restart of MCQ Scanner as a true OMR (bubble-sheet) app.",
                "Ported OMR architecture: SheetTemplate, SheetGenerator, MarkerDetector, PerspectiveWarp, BubbleGrader.",
                "Added Tarlac National High School branding: logo on home screen and launcher icon, maroon/gold accent theme.",
                "Added in-app changelog, accessible by tapping the version number.",
                "Developed by Sir_JPagdi."
            )
        )
    )
}
