# MCQ Scanner

An Android OMR (Optical Mark Recognition) app for grading multiple-choice
bubble sheets — same principle as Scantron/ZipGrade. No handwriting/OCR
anywhere; grading is pure ink-darkness detection against known bubble
positions.

Built for **Tarlac National High School**. Developed by **Sir_JPagdi**.

## How to build (no local Android Studio needed)

1. Push this repo to GitHub (or open it in a GitHub Codespace and commit/push
   from the terminal).
2. The included GitHub Actions workflow (`.github/workflows/build.yml`) builds
   a signed debug APK automatically on every push to `main`.
3. Go to the **Actions** tab on GitHub → open the latest run → download the
   `mcq-scanner-debug` artifact → unzip → install `app-debug.apk` on your
   Android phone.

### Confirming an update actually installed

The version number shown on the home screen (tap it to open the changelog)
is there specifically so you can confirm a new build landed after installing.
If a new APK seems to "not apply":

1. Check `versionName` in `app/build.gradle` directly on github.com to
   confirm what should be running.
2. Check the **Actions** tab to confirm the build actually ran against your
   latest commit.
3. If the signing key ever changes, Android will refuse the install silently
   — uninstall the old app first, then install the new APK.

The repo ships with a **fixed `app/debug.keystore`** (committed, not
auto-generated) precisely so GitHub Actions' ephemeral runners don't produce
a different random debug key every build, which would otherwise break
updates over a previously-installed copy.

## Versioning

Bump both `versionCode` and `versionName` in `app/build.gradle` for every
meaningful change, and add an entry to both `CHANGELOG.md` and
`Changelog.kt` (the in-app copy, shown when tapping the version number).

## Architecture

- **SheetTemplate.kt** — layout as fractions of the page; single source of
  truth used by both the PDF generator and the scanner.
- **SheetGenerator.kt** — draws the printable PDF via Android's built-in
  `PdfDocument`.
- **MarkerDetector.kt** — finds the 4 corner alignment markers via OpenCV
  template matching (multi-scale, per corner), validated against real
  device testing through v0.8.
- **OpenCvSupport.kt** — one-time OpenCV native library init, called from
  ScanActivity before any detection runs.
- **MarkerTemplateFactory.kt** — builds the synthetic reference marker
  image template matching is run against, kept in sync with SheetGenerator
  automatically since it's generated procedurally, not a static asset.
- **AlignmentAnalyzer.kt** — lightweight live-preview version of the same
  template-matching check, used only to drive the green-corner hint.
- **GuideGeometry.kt** — shared math for where the guide frame/corners sit
  within the camera preview, used by ScanOverlayView (drawing),
  AlignmentAnalyzer (live check), and ScanActivity (cropping the captured
  photo before running MarkerDetector) so all three agree exactly.
- **PerspectiveWarp.kt** — un-skews the photo via `Matrix.setPolyToPoly`.
- **BubbleGrader.kt** — samples ink darkness per bubble to grade.
  **Also untested — expect to tune `FILLED_THRESHOLD`, `AMBIGUOUS_GAP`.**
- **ScanActivity.kt** — camera capture → EXIF-corrected decode → detect →
  warp → grade → display, with a Scan Next / Done batch loop.
- **MainActivity.kt** — test setup, answer key entry, PDF generation/share,
  save/load tests, theme toggle, tappable version → changelog.
- **TestStorage.kt** — SharedPreferences/JSON storage of template + key.
- **ThemePrefs.kt** — Light / Dark / Black theme cycling.
- **Changelog.kt** — in-app mirror of `CHANGELOG.md`.

## Known open items

- Untested on a real device — first real scan will need threshold tuning.
- Single-page sheets only.
- No student ID/name bubbling (deferred — Philippine LRN is ~12 digits,
  too much sheet space for now).
