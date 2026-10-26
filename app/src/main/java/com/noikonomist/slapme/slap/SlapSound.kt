package com.noikonomist.slapme.slap

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.noikonomist.slapme.R
import com.noikonomist.slapme.util.SlapLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

enum class RingerState { NORMAL, VIBRATE, SILENT }

enum class SlapStream { NOTIFICATION, ALARM }

enum class MuteReason { RINGER_SILENT, RINGER_VIBRATE, DO_NOT_DISTURB }

sealed interface SoundDecision {
    data class Play(val stream: SlapStream) : SoundDecision
    data class Mute(val reason: MuteReason) : SoundDecision
}

/** Respect silent (default): no sound on silent, vibrate or DND. Always play: alarm stream, always. */
object SoundPolicy {
    fun decide(alwaysPlay: Boolean, ringer: RingerState, doNotDisturb: Boolean): SoundDecision = when {
        alwaysPlay -> SoundDecision.Play(SlapStream.ALARM)
        ringer == RingerState.SILENT -> SoundDecision.Mute(MuteReason.RINGER_SILENT)
        ringer == RingerState.VIBRATE -> SoundDecision.Mute(MuteReason.RINGER_VIBRATE)
        doNotDisturb -> SoundDecision.Mute(MuteReason.DO_NOT_DISTURB)
        else -> SoundDecision.Play(SlapStream.NOTIFICATION)
    }
}

/**
 * Plays the slap sample. One SoundPool per stream (audio attributes are fixed per pool), both loaded
 * up front so the sound is ready at impact.
 */
class SlapSound(context: Context) {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(AudioManager::class.java)
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val pools = SlapStream.entries.associateWith { LoadedSound(it) }

    /** Decides and plays; returns what happened, for the log. Waits briefly if the sample is still loading. */
    suspend fun play(alwaysPlay: Boolean): String {
        val decision = SoundPolicy.decide(alwaysPlay, ringerState(), doNotDisturb())
        if (decision !is SoundDecision.Play) return decision.toString()

        val sound = pools.getValue(decision.stream)
        val loaded = withTimeoutOrNull(LOAD_WAIT_MS) { sound.ready.await() } ?: false
        if (!loaded) return "$decision skipped: sample not loaded"
        val streamId = sound.pool.play(sound.soundId, 1f, 1f, 1, 0, 1f)
        val volume = audioManager.getStreamVolume(sound.streamType)
        val max = audioManager.getStreamMaxVolume(sound.streamType)
        return "$decision streamId=$streamId volume=$volume/$max"
    }

    private fun ringerState() = when (audioManager.ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> RingerState.SILENT
        AudioManager.RINGER_MODE_VIBRATE -> RingerState.VIBRATE
        else -> RingerState.NORMAL
    }

    private fun doNotDisturb(): Boolean = when (notificationManager.currentInterruptionFilter) {
        NotificationManager.INTERRUPTION_FILTER_ALL, NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> false
        else -> true
    }

    private inner class LoadedSound(stream: SlapStream) {
        val streamType = when (stream) {
            SlapStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
            SlapStream.ALARM -> AudioManager.STREAM_ALARM
        }
        val pool: SoundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(
                        when (stream) {
                            SlapStream.NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION_EVENT
                            SlapStream.ALARM -> AudioAttributes.USAGE_ALARM
                        },
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()

        val ready = CompletableDeferred<Boolean>()

        init {
            // Listener first: loading is asynchronous and may finish right away.
            pool.setOnLoadCompleteListener { _, _, status ->
                ready.complete(status == 0)
                SlapLog.d("Slap sample loaded for $stream: status=$status")
            }
        }

        val soundId: Int = pool.load(app, R.raw.slap, 1)
    }

    private companion object {
        const val LOAD_WAIT_MS = 300L
    }
}
