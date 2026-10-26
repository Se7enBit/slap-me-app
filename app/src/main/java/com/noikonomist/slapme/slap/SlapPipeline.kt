package com.noikonomist.slapme.slap

import android.content.Context
import android.os.SystemClock
import com.noikonomist.slapme.util.SlapLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What one slap did, for logs and the debug panel. */
data class SlapReport(
    val overlayShown: Boolean,
    val sound: String,
    val haptics: String,
    val homeRequested: Boolean,
    /** Wall-clock time of the Home request, to look it up in usage events. */
    val homeRequestedAtMillis: Long,
) {
    companion object {
        val SKIPPED = SlapReport(false, "skipped", "skipped", false, 0)
    }
}

/**
 * Runs one slap: overlay + animation, and at impact the sound, the haptics and the Home launch
 * (behind the solid comic layer, so the app switch is hidden). The overlay is always removed, at the
 * latest after [SlapTimeline.WATCHDOG].
 */
class SlapPipeline(
    context: Context,
    private val sound: SlapSound,
    private val haptics: SlapHaptics,
) {
    private val context = context.applicationContext
    private var running = false

    suspend fun slap(alwaysPlaySound: Boolean): SlapReport = withContext(Dispatchers.Main.immediate) {
        if (running) {
            SlapLog.w("Slap already running; skipped")
            return@withContext SlapReport.SKIPPED
        }
        running = true
        val start = SystemClock.elapsedRealtime()
        fun step(message: String) = SlapLog.i("slap +${SystemClock.elapsedRealtime() - start}ms $message")

        val overlay = SlapOverlay(context)
        val impact = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        try {
            step("start (overlay permission=${overlay.canShow()})")
            val shown = overlay.show {
                SlapAnimation(onImpact = { impact.complete(Unit) }, onFinished = { finished.complete(Unit) })
            }
            step(if (shown) "overlay shown" else "overlay NOT shown, slapping without visuals")

            if (shown && withTimeoutOrNull(SlapTimeline.WATCHDOG) { impact.await() } == null) {
                step("impact never came (watchdog)")
            }
            step("impact")
            val soundResult = sound.play(alwaysPlaySound).also { step("sound: $it") }
            val hapticsResult = haptics.hit().also { step("haptics: $it") }
            val homeAt = System.currentTimeMillis()
            val home = HomeLauncher.launch(context)
            step(
                if (home) "home launch requested (blocked launches are silent; check ActivityTaskManager)"
                else "home launch FAILED",
            )

            if (shown && withTimeoutOrNull(SlapTimeline.WATCHDOG) { finished.await() } == null) {
                step("animation never finished (watchdog)")
            }
            SlapReport(shown, soundResult, hapticsResult, home, homeAt)
        } finally {
            overlay.remove()
            step("overlay removed")
            running = false
        }
    }
}
