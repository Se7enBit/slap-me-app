package com.noikonomist.slapme.slap

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundPolicyTest {

    @Test
    fun respectSilentPlaysOnTheNotificationStreamWhenTheRingerIsOn() {
        assertEquals(
            SoundDecision.Play(SlapStream.NOTIFICATION),
            SoundPolicy.decide(alwaysPlay = false, ringer = RingerState.NORMAL, doNotDisturb = false),
        )
    }

    @Test
    fun respectSilentMutesOnSilentVibrateOrDnd() {
        assertEquals(
            SoundDecision.Mute(MuteReason.RINGER_SILENT),
            SoundPolicy.decide(alwaysPlay = false, ringer = RingerState.SILENT, doNotDisturb = false),
        )
        assertEquals(
            SoundDecision.Mute(MuteReason.RINGER_VIBRATE),
            SoundPolicy.decide(alwaysPlay = false, ringer = RingerState.VIBRATE, doNotDisturb = false),
        )
        assertEquals(
            SoundDecision.Mute(MuteReason.DO_NOT_DISTURB),
            SoundPolicy.decide(alwaysPlay = false, ringer = RingerState.NORMAL, doNotDisturb = true),
        )
    }

    @Test
    fun alwaysPlayUsesTheAlarmStreamWhateverTheRingerAndDnd() {
        for (ringer in RingerState.entries) {
            for (dnd in listOf(false, true)) {
                assertEquals(
                    SoundDecision.Play(SlapStream.ALARM),
                    SoundPolicy.decide(alwaysPlay = true, ringer = ringer, doNotDisturb = dnd),
                )
            }
        }
    }
}
