package com.noikonomist.slapme.util

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** System time in the device's *current* time zone (unlike `Clock.systemDefaultZone()`, which fixes it). */
object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}
