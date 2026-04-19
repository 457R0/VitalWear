package com.github.cfogrady.vitalwear

import android.content.Context
import android.util.Log
import com.github.cfogrady.vitalwear.common.log.TinyLogTree
import com.github.cfogrady.vitalwear.log.LogSettings
import java.util.concurrent.atomic.AtomicBoolean

class CrashHandler(private val context: Context, private val logSettings: LogSettings, private val originalHandler: Thread.UncaughtExceptionHandler? ) : Thread.UncaughtExceptionHandler {
    companion object {
        private val handlingCrash = AtomicBoolean(false)
        private const val TAG = "CrashHandler"
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        // Guard against recursion if crash reporting itself throws.
        if (!handlingCrash.compareAndSet(false, true)) {
            originalHandler?.uncaughtException(t, e)
            return
        }

        try {
            Log.e(TAG, "Vital Wear version ${BuildConfig.VERSION_NAME} ${BuildConfig.VERSION_CODE} crashed")
            Log.e(TAG, "Thread ${t.name} failed", e)
            TinyLogTree.shutdown()
        } catch (handlerError: Throwable) {
            Log.e(TAG, "Crash handler failed while processing uncaught exception", handlerError)
        } finally {
            originalHandler?.uncaughtException(t, e)
        }
    }
}