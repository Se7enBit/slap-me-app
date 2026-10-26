package com.noikonomist.slapme.util

import android.util.Log
import com.noikonomist.slapme.BuildConfig

/**
 * App-wide logger. Everything goes out under one tag so `adb logcat -s SlapMe:*` shows it all.
 * Verbose and debug logs are dropped in release builds.
 */
object SlapLog {
    const val TAG = "SlapMe"

    fun v(message: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, message)
    }

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
