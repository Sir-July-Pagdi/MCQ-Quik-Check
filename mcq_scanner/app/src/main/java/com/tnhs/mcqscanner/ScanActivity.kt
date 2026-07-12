package com.tnhs.mcqscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Scans a sheet using a live in-app camera preview (CameraX) instead of
 * handing off to the system Camera app. ScanOverlayView shows framing guides
 * that turn green per-corner as AlignmentAnalyzer detects good alignment on
 * live frames — a visual aid only; capture is manual (tap the button).
 * MarkerDetector then does the real, careful validation on the actual
 * captured photo (cropped to the guide region first — see
 * cropToGuideRegion). Results are shown in a panel that covers the preview
 * once a photo has been processed.
 */
class ScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_GRADE = "grade"
        const val MODE_KEY = "key"
        const val EXTRA_SCANNED_KEY = "scanned_key"

        /** Extra margin cropped around the guide rectangle, as a fraction of its
         *  own width/height, to tolerate the sheet not being pixel-perfectly
         *  aligned with the guide. */
        private const val CROP_PADDING_FRACTION = 0.08f
    }

    private lateinit var previewView: PreviewView
    private lateinit var permissionContainer: LinearLayout
    private lateinit var resultPanel: LinearLayout
    private lateinit var ivPreview: ImageView
    private lateinit var tvResult: TextView
    private lateinit var tvScoreHeader: TextView
    private lateinit var btnCapture: Button
    private lateinit var tvScanTitle: TextView
    private lateinit var tvOverlayHint: TextView
    private lateinit var scanOverlay: ScanOverlayView

    private var mode: String = MODE_GRADE
    private var lastScannedKeyText: String? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var alignmentAnalyzer: AlignmentAnalyzer

    private var isCapturing = false

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionContainer.visibility = View.GONE
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to scan sheets", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_GRADE
        Thread { OpenCvSupport.ensureInitialized() }.start()

        previewView = findViewById(R.id.previewView)
        scanOverlay = findViewById(R.id.scanOverlay)
        permissionContainer = findViewById(R.id.permissionContainer)
        resultPanel = findViewById(R.id.resultPanel)
        ivPreview = findViewById(R.id.ivPreview)
        tvResult = findViewById(R.id.tvResult)
        tvScoreHeader = findViewById(R.id.tvScoreHeader)
        btnCapture = findViewById(R.id.btnCapture)
        tvScanTitle = findViewById(R.id.tvScanTitle)
        tvOverlayHint = findViewById(R.id.tvOverlayHint)

        if (ActiveTest.current == null) {
            Toast.makeText(this, "No test loaded — set up a test on the home screen first", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        scanOverlay.paperLayout = ActiveTest.current?.paperLayout ?: PaperLayout.HALF_CROSS

        tvScanTitle.text = if (mode == MODE_KEY) "Scan Answer Key" else "Scan Filled Sheet"

        cameraExecutor = Executors.newSingleThreadExecutor()

        btnCapture.setOnClickListener {
            hapticFeedback()
            capturePhoto()
        }
        findViewById<Button>(R.id.btnGrantPermission).setOnClickListener {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
        findViewById<Button>(R.id.btnScanNext).setOnClickListener { showCameraUi() }
        findViewById<Button>(R.id.btnDone).setOnClickListener { finish() }

        val btnUseAsKey = findViewById<Button>(R.id.btnUseAsKey)
        if (mode == MODE_KEY) {
            btnUseAsKey.setOnClickListener {
                val key = lastScannedKeyText
                if (key == null) {
                    Toast.makeText(this, "Capture a photo of the answer key first", Toast.LENGTH_SHORT).show()
                } else {
                    val resultIntent = Intent().putExtra(EXTRA_SCANNED_KEY, key)
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
        } else {
            btnUseAsKey.visibility = View.GONE
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionContainer.visibility = View.VISIBLE
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Wait for the PreviewView to actually be laid out before reading
            // its viewPort/size — both are derived from the view's real
            // on-screen dimensions and are unreliable until layout has
            // happened at least once.
            previewView.post {
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                imageCapture = capture

                alignmentAnalyzer = AlignmentAnalyzer(
                    viewWidth = previewView.width,
                    viewHeight = previewView.height,
                    paperLayout = ActiveTest.current?.paperLayout ?: PaperLayout.HALF_CROSS
                ) { corners -> onAlignmentResult(corners) }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(android.util.Size(640, 480))
                    .build()
                analysis.setAnalyzer(cameraExecutor, alignmentAnalyzer)

                // THE FIX for capture matching preview: without a shared
                // ViewPort, ImageCapture defaults to the camera's full
                // uncropped sensor frame, while PreviewView (scaleType
                // FILL_CENTER) shows a cropped/zoomed-in view. Binding all
                // three use cases through the same ViewPort makes capture (and
                // the analyzer) WYSIWYG with the preview.
                val viewPort = previewView.viewPort
                val useCaseGroupBuilder = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(capture)
                    .addUseCase(analysis)
                if (viewPort != null) {
                    useCaseGroupBuilder.setViewPort(viewPort)
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, useCaseGroupBuilder.build()
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not start camera: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Called (on a background thread) with each corner's live alignment result.
     *  NOTE: auto-capture was removed after real-device testing showed the live
     *  check firing on non-sheet objects (a mug, an empty desk) even after a
     *  first attempt to fix it — tuning a live heuristic blind, without being
     *  able to test on a real camera, wasn't working. The green/gold squares
     *  are now a visual aid only; capture is manual (tap the button), which
     *  was already working reliably before auto-capture was added. */
    private fun onAlignmentResult(corners: BooleanArray) {
        runOnUiThread {
            if (resultPanel.visibility == View.VISIBLE) return@runOnUiThread
            scanOverlay.cornersAligned = corners
        }
    }

    private fun hapticFeedback() {
        previewView.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        if (isCapturing) return
        isCapturing = true

        val dir = File(cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        btnCapture.isEnabled = false
        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    runOnUiThread {
                        btnCapture.isEnabled = true
                        isCapturing = false
                        processPhoto(file)
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    runOnUiThread {
                        btnCapture.isEnabled = true
                        isCapturing = false
                        Toast.makeText(this@ScanActivity, "Capture failed: ${exc.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    private fun showCameraUi() {
        resultPanel.visibility = View.GONE
        previewView.visibility = View.VISIBLE
        scanOverlay.visibility = View.VISIBLE
        tvScanTitle.visibility = View.VISIBLE
        tvOverlayHint.visibility = View.VISIBLE
        btnCapture.visibility = View.VISIBLE

        // Reset the visual alignment guide for the next sheet.
        scanOverlay.cornersAligned = BooleanArray(4)
    }

    private fun showResultUi() {
        previewView.visibility = View.GONE
        scanOverlay.visibility = View.GONE
        tvScanTitle.visibility = View.GONE
        tvOverlayHint.visibility = View.GONE
        btnCapture.visibility = View.GONE
        resultPanel.visibility = View.VISIBLE
    }

    private fun processPhoto(file: File) {
        showResultUi()
        tvScoreHeader.text = "Processing…"
        tvResult.text = ""
        try {
            val fullBitmap = decodeWithExifCorrection(file)

            // Crop to a padded version of the same guide rectangle shown live
            // (GuideGeometry — shared with the overlay and AlignmentAnalyzer),
            // so MarkerDetector's corner search only has to look where the
            // sheet actually is, not the whole (often mostly background)
            // photo. Without this, the fixed search window can miss the real
            // markers entirely whenever the sheet doesn't fill the frame edge
            // to edge — which is normal for a handheld photo.
            val bitmap = cropToGuideRegion(fullBitmap)
            ivPreview.setImageBitmap(bitmap) // shown if detection/grading fails below

            val active = ActiveTest.current ?: return
            val expectedAspect = SheetTemplate.sheetAspectRatio(active.paperLayout)
            val corners = MarkerDetector.detect(bitmap, expectedAspect, active.paperLayout)
            val warped = PerspectiveWarp.warp(bitmap, corners, active.template)
            val scanResults = BubbleGrader.grade(warped, active.template)
            BubbleGrader.requireConfident(scanResults)

            if (mode == MODE_KEY) {
                // No answer key exists yet in this mode, so there's nothing to
                // color-code against — show the plain warped scan.
                ivPreview.setImageBitmap(warped)
                showKeyScanResult(scanResults)
            } else {
                // ZipGrade-style: color-coded feedback drawn directly on the
                // scanned sheet (green=correct answer, red=wrong pick,
                // orange=student's selection) instead of a separate per-question
                // text list.
                ivPreview.setImageBitmap(AnswerOverlayRenderer.annotate(warped, active.template, scanResults, active.answerKey))
                val summary = AnswerParser.grade(active.answerKey, scanResults, active.points)
                val percent = if (summary.totalPoints > 0) (summary.earnedPoints * 1000 / summary.totalPoints) / 10.0 else 0.0
                tvScoreHeader.text = "${summary.earnedPoints} / ${summary.totalPoints} = $percent%"
                tvResult.text = buildResultText(summary, scanResults)
            }
        } catch (e: MarkerDetector.MarkersNotFoundException) {
            tvScoreHeader.text = "Couldn't find the sheet"
            tvResult.text = "Line the sheet's corner squares up with the guide (they don't need to match exactly) and make sure lighting is even.\n\n(${e.message})"
        } catch (e: BubbleGrader.LowConfidenceException) {
            tvScoreHeader.text = "Unclear scan"
            tvResult.text = "${e.message}"
        } catch (e: Exception) {
            tvScoreHeader.text = "Error"
            tvResult.text = "${e.message}"
        }
    }

    /** Crops [bitmap] to GuideGeometry's frame for the current preview size/paper
     *  size, expanded by CROP_PADDING_FRACTION for tolerance. Relies on Preview,
     *  ImageCapture, and ImageAnalysis all sharing one ViewPort, so a fraction of
     *  previewView's own size lines up with the same fraction of the captured photo. */
    private fun cropToGuideRegion(bitmap: Bitmap): Bitmap {
        val vw = previewView.width.toFloat()
        val vh = previewView.height.toFloat()
        if (vw <= 0f || vh <= 0f) return bitmap
        val paperLayout = ActiveTest.current?.paperLayout ?: PaperLayout.HALF_CROSS
        val frame = GuideGeometry.computeFrame(vw, vh, paperLayout)

        val gl = frame.left / vw
        val gt = frame.top / vh
        val gr = (frame.left + frame.width) / vw
        val gb = (frame.top + frame.height) / vh
        val gw = gr - gl
        val gh = gb - gt
        if (gw <= 0f || gh <= 0f) return bitmap

        val cl = (gl - gw * CROP_PADDING_FRACTION).coerceIn(0f, 1f)
        val ct = (gt - gh * CROP_PADDING_FRACTION).coerceIn(0f, 1f)
        val cr = (gr + gw * CROP_PADDING_FRACTION).coerceIn(0f, 1f)
        val cb = (gb + gh * CROP_PADDING_FRACTION).coerceIn(0f, 1f)

        val left = (cl * bitmap.width).toInt()
        val top = (ct * bitmap.height).toInt()
        val right = (cr * bitmap.width).toInt()
        val bottom = (cb * bitmap.height).toInt()
        val w = (right - left).coerceAtLeast(1)
        val h = (bottom - top).coerceAtLeast(1)

        return try {
            Bitmap.createBitmap(bitmap, left, top, w, h)
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun showKeyScanResult(scanResults: List<BubbleGrader.QuestionResult>) {
        val letters = scanResults.map { r -> r.chosenIndex?.let { ('A' + it).toString() } ?: "?" }
        lastScannedKeyText = letters.joinToString(",")

        val blanks = scanResults.count { it.chosenIndex == null }
        val ambiguous = scanResults.count { it.isAmbiguous }
        tvScoreHeader.text = "Detected Key"

        val sb = StringBuilder()
        if (blanks > 0 || ambiguous > 0) {
            sb.append("⚠ $blanks blank, $ambiguous ambiguous — double-check before using.\n\n")
        }
        scanResults.forEach { r ->
            val letter = r.chosenIndex?.let { ('A' + it).toString() } ?: "?"
            val flag = if (r.isAmbiguous) " ⚠" else if (r.chosenIndex == null) " (blank)" else ""
            sb.append("Q${r.questionNumber}: $letter$flag\n")
        }
        tvResult.text = sb.toString()
        findViewById<Button>(R.id.btnUseAsKey).visibility = View.VISIBLE
    }

    /** Per-question detail now lives as color-coded rings on the scanned image
     *  itself (see AnswerOverlayRenderer) — this is just the score breakdown
     *  and a legend for those colors. */
    private fun buildResultText(
        summary: AnswerParser.GradeSummary,
        scanResults: List<BubbleGrader.QuestionResult>
    ): CharSequence {
        val correct = summary.perQuestion.count { it == true }
        val incorrect = summary.perQuestion.count { it == false }
        val blank = scanResults.count { it.chosenIndex == null }
        val ambiguous = scanResults.count { it.isAmbiguous }

        val sb = StringBuilder()
        sb.append("$correct correct, $incorrect incorrect")
        if (blank > 0) sb.append(", $blank blank")
        sb.append("\n")
        if (ambiguous > 0) sb.append("⚠ $ambiguous ambiguous — double-check on image\n")
        sb.append("\n🟢 correct answer   🔴 wrong pick   🟠 your selection")
        return sb
    }

    /** Decodes the captured JPEG and rotates it upright according to its EXIF orientation tag. */
    private fun decodeWithExifCorrection(file: File): Bitmap {
        val original = BitmapFactory.decodeFile(file.absolutePath)
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return if (matrix.isIdentity) original
        else Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
