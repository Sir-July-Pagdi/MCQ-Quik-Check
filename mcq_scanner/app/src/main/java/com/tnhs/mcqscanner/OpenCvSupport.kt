package com.tnhs.mcqscanner

import android.util.Log
import org.opencv.android.OpenCVLoader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OpenCV's native libraries have to be loaded once before any org.opencv.*
 * call is made — this wraps that in a guarded call so every screen that
 * needs it (ScanActivity today) can just call ensureInitialized() safely,
 * as many times as it wants, without redoing the work.
 */
object OpenCvSupport {
    private const val TAG = "OpenCvSupport"
    private val initialized = AtomicBoolean(false)
    @Volatile private var initSucceeded = false

    fun ensureInitialized(): Boolean {
        if (initialized.compareAndSet(false, true)) {
            initSucceeded = try {
                OpenCVLoader.initLocal()
            } catch (e: Throwable) {
                Log.e(TAG, "OpenCV failed to initialize", e)
                false
            }
            if (!initSucceeded) {
                Log.e(TAG, "OpenCVLoader.initLocal() returned false")
            }
        }
        return initSucceeded
    }
}
