package com.noikonomist.slapme.monitor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.MediaStore
import com.noikonomist.slapme.engine.Foreground
import java.time.Clock

/**
 * The built-in exceptions: calls (from the audio mode, no phone permission), the launcher, camera apps,
 * navigation apps and Slap Me itself. Launcher and camera packages are resolved and cached.
 */
class ExceptionResolver(context: Context, private val clock: Clock) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private var cached: KnownPackages? = null
    private var resolvedAt = 0L

    fun inCall(): Boolean = audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION

    fun classify(packageName: String?, navigation: Set<String>): Foreground =
        ForegroundClassifier.classify(packageName, known().copy(navigation = navigation))

    private fun known(): KnownPackages {
        val now = clock.millis()
        cached?.takeIf { now - resolvedAt < REFRESH_MS }?.let { return it }
        return KnownPackages(
            self = app.packageName,
            // Settings declares a fallback HOME activity; it is not a launcher.
            launchers = handlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) - NOT_LAUNCHERS,
            cameras = CAMERA_ACTIONS.flatMap { handlers(Intent(it)) }.toSet(),
            navigation = emptySet(),
        ).also {
            cached = it
            resolvedAt = now
        }
    }

    private fun handlers(intent: Intent): Set<String> {
        val pm = app.packageManager
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return infos.mapTo(mutableSetOf()) { it.activityInfo.packageName }
    }

    private companion object {
        const val REFRESH_MS = 10 * 60 * 1_000L
        val NOT_LAUNCHERS = setOf("android", "com.android.settings")
        val CAMERA_ACTIONS = listOf(
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA,
            MediaStore.INTENT_ACTION_VIDEO_CAMERA,
            MediaStore.ACTION_IMAGE_CAPTURE,
            MediaStore.ACTION_VIDEO_CAPTURE,
        )
    }
}
