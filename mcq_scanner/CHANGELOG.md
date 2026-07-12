# Changelog

All notable changes to MCQ Quick Check (formerly MCQ Scanner). Keep this
file in sync with `app/src/main/java/com/tnhs/mcqscanner/Changelog.kt`,
which mirrors this list inside the app (tap the version number on the
home screen).

## v1.4 — 2026-07-12

- **Load Test list now sorts by most-recently-edited first**, with the
  save date/time shown in smaller text under each test name.
- Fixed `Cancel` dialog-button text being unreadable on Dark/Black themes —
  forced white via a themed AlertDialog button style.
- **Fixed inconsistent corner marker size across paper layouts.**
  `SheetTemplate.MARKER_SIZE` was a fraction of each cut sheet's own width,
  but ½ length and ¼ page sheets are half as wide as ½ cross, so their
  printed corner squares came out visibly smaller. Added
  `SheetTemplate.markerSizeFraction(layout)` so all three layouts print
  the same physical marker size.
- **Scan result screen redesigned ZipGrade-style.** The scanned/warped
  sheet now shows color-coded feedback drawn directly on the bubbles
  (green ring = correct answer, red ring = student's wrong pick, orange
  ring = student's actual selection) via the new `AnswerOverlayRenderer`,
  instead of a separate per-question text list.
- `Scan Next` / `Done` button text forced white on Dark/Black themes.
- **Rebuilt the live alignment detector** (`AlignmentAnalyzer`). Previously
  it template-matched against a fixed, assumed corner position, which
  could false-match answer bubbles or other dark scene clutter. Now:
  1. Detects the sheet's actual paper boundary in the frame first
     (largest convex 4-sided contour), and searches for markers at those
     real corners instead of the fixed guide position (falling back to
     the guide position if no paper quad is found yet).
  2. Validates each candidate using contour hierarchy (RETR_CCOMP —
     looks for a shape with a hole, i.e. a ring) plus geometric checks
     (near-square bounding box, plausible size, low circularity to reject
     round bubbles, centered hole) before accepting it as a marker.

## v1.3 — 2026-07-12

- **Fixed the version number not opening the changelog.** In
  `activity_main.xml`, the fixed footer was declared BEFORE the ScrollView.
  In a `FrameLayout`, later-declared children draw on top and get touch
  priority in overlapping regions — so the ScrollView (spanning the full
  screen) was silently swallowing taps meant for the footer underneath it,
  even in the ScrollView's own empty bottom padding where nothing is
  visibly drawn there. Reordered so the footer is declared last.
- Home screen content below the header now vertically centers in the
  available space (via `fillViewport` + a weighted, center-gravity
  container) instead of always starting right under the header.
- **Widened the live alignment guide's search range.** `AlignmentAnalyzer`
  was only checking 3 scales (0.7x-1.4x) per frame, versus `MarkerDetector`
  (the final, proven-working check on the actual captured photo) checking
  8 scales (0.5x-2.2x). That gap was the likely reason the guide rarely
  turned green even when a sheet was clearly well-aligned — the live check
  just wasn't looking at the marker's actual size. Widened to 5 scales
  across the same broader range, still cheap enough to run per-frame.
  (Noted for later: a rotationally-distinct fiducial — e.g. an L-shaped
  cluster of 3 small squares per corner, rotated differently at each
  corner — would likely help further and is worth revisiting if this isn't
  enough on its own; skipped for now since it needs a new printed design.)
- **Redesigned the scan result screen**, modeled after ZipGrade's layout:
  score shown large at the top (always visible, not part of any scrolling
  content), Scan Next/Done pinned at the bottom (always visible), with only
  the captured photo and per-question breakdown scrolling in between. Fixes
  the buttons getting pushed off-screen and effectively unreachable on
  longer tests, where the old single non-scrolling column could overflow
  past the screen height with no way to reach what got clipped off the
  bottom.
- Question numbers and choice letters on the printed sheet now stay a
  fixed, legible size regardless of how dense the bubble grid gets —
  previously they shrank along with the bubbles on a test with enough
  questions to trigger the auto-shrink-to-fit. Only the bubbles and their
  spacing shrink now; text size is tied to the tile's physical page size
  only, not content density.

## v1.2 — 2026-07-12

- **Fixed the v1.1 build failure.** `MarkerTemplateFactory.OUTER_MARGIN_FRACTION`
  and `RING_THICKNESS_FRACTION` were plain `Double` literals (no `f` suffix),
  but `SheetGenerator.drawMarkers` multiplies them with `Float` values and
  passes the result straight into `Canvas.drawRect(Float, Float, Float,
  Float, Paint)`. Kotlin doesn't implicitly narrow `Double` to `Float`, so
  that's a compile error. Fixed by declaring both constants as `Float`.
- **Paper layout overhaul.** Removed "Full page." Renamed "½ page" to
  "½ cross" (the existing top/bottom stacked layout, a crosswise fold) and
  added a new "½ length" option — two copies side by side instead, for a
  lengthwise fold. New `PaperLayout` enum replaces the old
  `sheetsPerPage: Int` throughout. Column limits per layout: up to 6 for
  ½ cross, 3 for ½ length, 3 for ¼ page. Live preview and the real PDF both
  follow whichever is selected.
- **Answer key creation moved** onto the Generate Answer Sheet Template
  screen. Tap "Create Answer Key" for a dialog choice of "Create Manually"
  or "Scan Answer Key Instead" — both used to be separate home-screen
  buttons. Whatever key is built there returns together with the template
  when you tap Use This Template.
- **Delete saved tests.** Load Test now shows a 🗑 next to each entry, with
  a confirmation step before deleting.
- Columns and questions-per-column are directly typeable, not just +/−
  steppers (carried over from the intended v1.1 change).
- Fixed unreadable "Answer Key Preview" button text in Dark/Black themes
  (needed the same explicit `backgroundTint` + `textColor` treatment as the
  other action buttons).
- Version number and developer credit moved to a fixed footer pinned to the
  bottom of the home screen, instead of scrolling away with the content
  above.
- Renamed the app from "MCQ Scanner" to "MCQ Quick Check."
- Replaced the school logo and name in the header with the new app logo
  and a larger, more prominent app name. Replaced the washed-out home
  screen background photo with a new exam-day photo.

## v1.1 — 2026-07-11

- **Low-light grading accuracy.** `BubbleGrader` now computes an adaptive
  darkness threshold per sheet via `OtsuThreshold` (finds the natural split
  between "mostly unmarked" and "the one filled choice per question" in
  that photo's own score distribution) instead of a fixed brightness value.
  A fixed threshold worked great on a backlit iPad screen but not on real
  paper under normal room lighting — a dimmer photo reads darker across the
  board, blank bubbles included, which a fixed absolute value can't account
  for. Falls back to the old fixed value if the computed threshold looks
  implausible (e.g. a uniformly dark/bright sheet with no real separation).
- **Marker redesign.** Corner markers are now a hollow "ring" (solid black
  border, white center) instead of a solid filled square — see
  `MarkerTemplateFactory`. Real-device testing showed the live alignment
  guide reacting to shaded/unshaded answer bubbles almost as readily as the
  actual corners; a solid square isn't THAT different from a cluster of
  dark circles at typical match tolerances, but a ring's two concentric
  contrast transitions are. (Barcodes were considered but aren't actually a
  good fit for a CORNER fiducial — reliably decoding one itself needs the
  photo already roughly de-skewed, which is exactly the problem the corner
  markers exist to solve in the first place.)
- Fixed the ½-page template preview being distorted — `renderPreviewBitmap`
  always assumed full-page (portrait) proportions regardless of paper size;
  a ½-page tile is actually landscape-shaped.
- Moved Generate & Share PDF onto the "Generate Answer Sheet Template"
  screen, right below Use This Template, next to the preview that shows
  exactly what it's about to produce.
- Columns and questions-per-column are now directly typeable (EditText),
  not just +/− steppers. Renamed "Rows per column" to "Questions per
  column".
- Removed the border box around the bubble grid (`drawGridBox`) — it ran
  directly through the corner guide markers.
- Added a Subject Teacher field on the template screen (typed once,
  applies to the whole printed sheet — not a per-student blank line).
  Printed as a third header-table row filling what used to be dead space
  next to the taller Score cell, closing the table border on all sides.
- Scan results now show the correct answer letter in red immediately after
  any remark that isn't a checkmark (incorrect, ambiguous, or blank) —
  nothing extra shown for correct answers.

## v1.0 — 2026-07-10

- **Detection accuracy fix.** Template matching is great at reliably finding
  the right REGION (that's its whole strength over the old brightness-only
  approach), but its reported position is quantized to the search grid —
  not precise enough for the perspective warp that follows. Small errors
  there were showing up downstream as misaligned bubble sampling, which
  read as "blank" even on bubbles that were clearly, fully shaded. Added a
  refinement step after the coarse match: Otsu thresholding + centroid
  within a small local window, which locates the marker's true center to
  sub-pixel precision. This is the main fix in this release.
- Loosened `PaperSurroundCheck.WHITE_THRESHOLD` (165 → 140) — it was likely
  too strict for normal indoor lighting, causing false *negatives* on
  genuine sheets, not just correctly rejecting random scenes.
- Removed the big connecting rectangle from the live scan guide. Only the 4
  corner squares remain — less visual clutter, and they're what you
  actually align to.
- **New "Generate Answer Sheet Template" screen.** Test name, question
  count, choice count, and paper size moved here from the home screen.
  Added new columns/rows controls (`SheetTemplate.rowsPerBlockRange` /
  `maxBlocksPerRowGroupRange` cap them to sensible values depending on
  question count and paper size) and a live preview that re-renders on
  every change via `SheetGenerator.renderPreviewBitmap` — the exact same
  drawing code the real PDF uses, so the preview can't drift from what
  actually prints. If an answer key is already set for the test, a second
  preview mode shows the sheet with the correct bubbles filled in, as a
  preview of the actual reference sheet.
- Home screen now shows a one-line summary of the configured test instead
  of the raw input fields, with a button opening the new template screen.
- Added the school facade photo as a washed-out home-screen background
  (full-opacity image behind a scrim using the theme's own window
  background color at ~93% opacity) — reads as a faint texture rather than
  competing with foreground text in any of the 3 themes.

## v0.9 — 2026-07-09

- Widened alignment tolerance 4x via a shared `GuideGeometry.TOLERANCE_SCALE`
  constant, used by both the drawn guide square size and the actual live
  matching search radius — the guide box now honestly represents how much
  positional slop is actually accepted, instead of being purely decorative.
- Added `PaperSurroundCheck`: a second, independent gate alongside shape
  matching. A match is only accepted if the area around it also looks like
  plain light paper (high average brightness), not just roughly
  marker-shaped. Template matching alone couldn't tell "the right shape
  sitting in the middle of a blank sheet" apart from "the right shape as
  part of some unrelated dark, textured scene" (grass, walls, tables,
  random objects) — this does.
- Fixed unreadable button text in Dark/Black themes on several home-screen
  buttons (Create Answer Key, Scan Answer Key, Generate & Share PDF,
  Save/Load Test). Root cause: these are plain `Button` widgets with an
  explicit `backgroundTint`, which — unlike the Material default button
  style — doesn't automatically pick a contrasting text color to match.
- Suppressed Android's system autofill icon appearing on action buttons
  (via `importantForAutofill="noExcludeDescendants"` on each screen's root
  view) — these are action buttons, not form fields, so the icon was just
  clutter, and looked like a green marker next to the button text.
- Restricted native libraries to `arm64-v8a` and `armeabi-v7a` only (real
  phone CPU architectures) via `ndk { abiFilters }`, dropping the
  emulator-only `x86`/`x86_64` builds OpenCV's AAR includes by default.
  Should noticeably shrink the APK size increase from v0.8's OpenCV upgrade.

## v0.8 — 2026-07-09

- Replaced marker detection's approach entirely: instead of a hand-rolled
  darkness-centroid + bounding-box heuristic, it now uses real OpenCV
  template matching (`Imgproc.matchTemplate`, normalized cross-correlation,
  searched at several scales per corner) against a synthetic reference
  image of the marker. This is the same core technique the open-source
  OMRChecker project uses (see its `CropOnMarkers.py`) — added via OpenCV's
  official Maven Central Android package (`org.opencv:opencv:4.9.0`, no
  native SDK download needed). Template matching correlates actual shape,
  not just brightness, which is why the darkness-only approach kept
  false-matching dark backgrounds (grass, walls, tables, random objects)
  and missing real markers under uneven lighting — no amount of threshold
  tuning was going to fix that, since it was never checking shape at all.
- Fixed the bigger issue with real sheets: found and restored a step that
  had been dropped in an earlier rewrite — cropping the captured photo to
  the on-screen guide region (with padding) before running detection.
  Without it, detection was searching a fixed-size window of the corners of
  the WHOLE photo, but the guide only covers part of the frame (there's
  real background around it), so on a normal handheld photo the actual
  markers often sat outside where detection was even looking. This was the
  main reason real printed sheets were failing to scan and grade at all.
- The live per-corner green-highlight hint now uses the same template
  matching, searching a small window at each corner's exact expected
  position (shared geometry with the guide overlay) instead of scanning a
  broad quadrant for anything dark.

## v0.7 — 2026-07-08

- Removed auto-capture. Two attempts at tuning the live "is this aligned"
  check (guided by screen recordings) still misfired in real testing —
  triggering on non-sheet objects, or firing mid-adjustment while the
  sheet was still being positioned. Rather than keep guessing at
  thresholds that can't be verified without a real device, capture is
  manual again (tap the button), which was already reliable before
  auto-capture was added.
- The corner guide squares still turn green live as a visual aid, checking
  each corner for an actual small solid blob rather than just "is it dark
  here" — so you can see when it looks lined up before tapping Capture
  yourself.
- Fixed one more invisible-text case in Dark/Black themes: the "Result"
  title on the scan screen.

## v0.6 — 2026-07-08

- Added real-time alignment detection while scanning. A new CameraX
  ImageAnalysis pipeline (AlignmentAnalyzer) samples live camera frames at
  the same 4 corner targets as the on-screen guide (shared via
  GuideGeometry, so they can never drift out of sync) and checks each spot
  for marker-like darkness — deliberately loose ("just enough" overlap
  counts), since this is only a live hint. Each corner square turns green
  independently as it looks aligned.
- Capture now happens automatically once all 4 corners have stayed aligned
  for ~4 consecutive checks (~600ms), via a debounce counter, so a quick
  pass-through doesn't trigger a blurry shot. Manual capture (tap the
  button) still works at any time.
- Added vibration feedback (View.performHapticFeedback) on both
  auto-capture and manual capture — no extra permission needed.
- Loosened MarkerDetector's validation thresholds (fill ratio, blob size
  range, aspect tolerance, search window) — the v0.4 values were tuned by
  reasoning about the math rather than against real photos, and turned out
  too strict for normal paper texture, print quality, and lighting. Still
  keeps the core blob-tightness and shape checks that reject non-sheet
  photos, just with realistic tolerance.
- Removed the underscore blanks from the printed sheet's Name / Date /
  Gr. & Section / Score cells now that they have real table borders (from
  v0.5) — the border is enough of a "write here" indicator on its own.

## v0.5 — 2026-07-08

- Fixed the camera not capturing what the live preview showed. CameraX's
  Preview and ImageCapture use cases default to different fields of view
  (Preview shows a cropped/zoomed FILL_CENTER view; ImageCapture defaults to
  the full uncropped sensor frame) — so a sheet that filled the on-screen
  guide frame perfectly came out tiny and off-center in the actual photo,
  with the corner markers nowhere near where MarkerDetector searches for
  them. Fixed by binding both use cases through a shared CameraX ViewPort,
  making capture WYSIWYG with the preview. This was the bug blocking
  scanning entirely.
- Fixed bubbles overlapping on ½-page sheets. Bubble radius used to be
  derived from page width alone, but a ½-page sheet's row pitch shrinks
  (two sheets stacked into one page's height) while its width doesn't —
  so bubbles stayed full-size while rows packed tighter, and adjacent rows
  started touching. Bubble size is now derived from the tighter of the
  actual row pitch (height-based) and column spacing (width-based) for
  whatever paper size is being rendered, so it's correct for full, ½, and
  ¼ page alike.
- Redesigned the details header into a real bordered table instead of
  underscore blanks: Name | Date on one row, Gr. & Section | Score on the
  next, with the Score cell taller than the rest for writing room. All
  four cells are vertically centered, left-aligned.
- Moved the test title below the details table (previously at the very
  top), with the top corner markers repositioned to flank the title line
  instead of sitting at the page's top edge — freed up by the table no
  longer needing that space.
- Fixed more invisible text in Dark/Black themes: the school name and
  version number on the home screen, and the title on the changelog
  screen, all had the same colorPrimary-as-text bug fixed on the
  answer-key screen in v0.4.

## v0.4 — 2026-07-08

- Fixed the scan overlay guide not matching a ½-page sheet's actual shape —
  it was always drawn as a fixed inset square, regardless of paper size. It
  now follows each paper size's real proportions (full, ½, and ¼ page each
  have their own aspect ratio).
- Much stricter sheet detection: MarkerDetector now validates that each
  corner marker is actually a small, solid, mostly-square blob (tight
  bounding box + fill ratio check), not just "the darkest spot in this
  corner" — the old version would happily accept a photo of a keyboard as a
  valid sheet. It also checks the 4 detected corners form a plausible
  sheet-shaped rectangle before accepting them.
- Added BubbleGrader.requireConfident() as a final safety net: if too many
  graded answers come out ambiguous or blank, the app reports that instead
  of a misleading score.
- Fixed invisible text in the Dark and Black themes on the answer-key
  screen — several elements were tinted with colorPrimary, which in the
  Black theme is a near-black surface color meant for backgrounds, not
  text. Text now uses the theme's own text color; interactive accents
  (Save button, selected bubbles) use the gold accent color, which stays
  readable across all 3 themes.

## v0.3 — 2026-07-07

- Fixed a crash when tapping "Create Answer Key Here" — SheetTemplate used a
  `by lazy` cached field that doesn't survive Android's real serialization of
  Intent extras (which happens even within the same app). Switched it to a
  plain recomputed property. This should also fix Save Test / Load Test,
  since they depend on the same object being constructed correctly.
- Added a box around the Name/Grade/Section/Date/Score details area, and a
  separate box around the whole bubble grid, on the printed sheet.
- Removed the "Down each column" / "Across each row" numbering option —
  always numbers down each column now.
- Scanning now happens inside the app with a live camera preview (CameraX)
  instead of handing off to the phone's system camera app, with on-screen
  guide squares for lining up the sheet's corner markers before capture.
- Scan results now appear in a panel right in the scan screen, with a "Scan
  Next" button to keep going without leaving the screen.

## v0.2 — 2026-07-07

- Redesigned the printed answer sheet: bigger, bolder bubbles and text in a
  formal sans-serif style, condensed spacing instead of stretching to fill
  the page.
- Every block of questions now uses the same row spacing as item 1 and item
  11, even a partial last block (e.g. 21-25) — no more oddly spread-out rows.
- If the sheet has extra vertical space, it's centered on the page instead of
  being stretched to fill it.
- Added Grade and Section fields below Name, and Date / Score fields on the
  right.
- Removed the typed answer-key field. Added "Create Answer Key Here" — a
  tap-the-bubble screen generated from your question count, right above
  "Scan Answer Key Instead".
- Added customizable points per item on the new answer-key screen (defaults
  to 1 pt each, with an "apply to all" shortcut).
- Scores are now reported in points earned / points possible, not just items
  correct.

## v0.1 — 2026-07-07

- Redesigned sheet layout: compact ZipGrade-style blocks of 10 questions
  stacked in row-groups, no student-ID bubble grid, to save paper.
- Added per-test paper-size option: Full page, ½ page, or ¼ page. Half/quarter
  tile multiple independent copies (each with their own corner markers) onto
  one physical page — cut apart before handing out.
- Added "Scan Answer Key" — capture a photo of a filled-in key sheet instead
  of typing the answer key by hand. Flags blanks/ambiguous bubbles so you can
  double-check before using the detected key.
- Fixed camera not opening (blank screen on tapping Capture) — Android 11+
  requires a package-visibility `<queries>` declaration for the camera
  intent to resolve; without it the intent silently did nothing.
- Question numbering is generated dynamically from however many questions
  the test has, rather than a fixed count.

## v0.0 — 2026-07-07

- Fresh restart of MCQ Scanner as a true OMR (bubble-sheet) app.
- Ported OMR architecture from the earlier prototype: `SheetTemplate`,
  `SheetGenerator`, `MarkerDetector`, `PerspectiveWarp`, `BubbleGrader`.
- Added Tarlac National High School branding: logo on the home screen and
  as the launcher icon, maroon/gold accent theme (Light/Dark/Black variants).
- Added in-app changelog, accessible by tapping the version number.
- Developed by Sir_JPagdi.

### Known open items
- Completely untested on a real device — first real test will likely
  require tuning `MarkerDetector.DARK_THRESHOLD` / `MIN_WEIGHT` /
  `SEARCH_WINDOW` and `BubbleGrader.FILLED_THRESHOLD` / `AMBIGUOUS_GAP`.
- Single-page sheets only, no multi-page support yet.
- No student ID/name bubbling yet.
