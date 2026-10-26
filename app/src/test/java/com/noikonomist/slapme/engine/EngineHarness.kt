package com.noikonomist.slapme.engine

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val TEST_ZONE: ZoneId = ZoneOffset.UTC

/** 2026-10-26 is a Monday. */
fun instantAt(date: String, time: String): Instant =
    LocalDateTime.parse("${date}T$time").atZone(TEST_ZONE).toInstant()

val MONDAY_10AM: Instant = instantAt("2026-10-26", "10:00:00")

const val FEED = "com.example.feed"
const val NAV = "com.google.android.apps.maps"

fun using(pkg: String = FEED) = Observation(interactive = true, unlocked = true, inCall = false, Foreground.App(pkg))
val SCREEN_OFF = Observation(interactive = false, unlocked = false, inCall = false, Foreground.Unknown)
val HOME = Observation(interactive = true, unlocked = true, inCall = false, Foreground.Launcher)
val IN_CALL = Observation(interactive = false, unlocked = true, inCall = true, Foreground.Unknown)
val NAVIGATING = Observation(interactive = true, unlocked = true, inCall = false, Foreground.Exempt(NAV))

inline fun <reified T : EngineEffect> List<EngineEffect>.ofType(): List<T> = filterIsInstance<T>()

class MutableClock(private var now: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now

    fun advance(by: Duration) {
        now = now.plusMillis(by.inWholeMilliseconds)
    }
}

data class TimedEffect(val at: Instant, val effect: EngineEffect)

/**
 * Drives the engine the way the monitor will: an observation now, then one every [TICK] while it holds.
 * Every effect is also kept in [log] with the time it happened.
 */
class EngineHarness(
    var config: EngineConfig = EngineConfig(),
    var rules: ActivationRules = ActivationRules(alwaysOn = true),
    start: Instant = MONDAY_10AM,
    var state: EngineState = EngineState(),
) {
    val clock = MutableClock(start, TEST_ZONE)
    val log = mutableListOf<TimedEffect>()
    val now: Instant get() = clock.instant()
    val engine: UsageEngine get() = UsageEngine(config, rules, clock)

    fun send(event: EngineEvent): List<EngineEffect> {
        val step = engine.handle(state, event)
        state = step.state
        step.effects.forEach { log += TimedEffect(now, it) }
        return step.effects
    }

    fun observe(observation: Observation) = send(EngineEvent.Observe(observation))

    /** Reports [observation] now and then every tick until [duration] has passed. */
    fun hold(observation: Observation, duration: Duration): List<EngineEffect> {
        val effects = observe(observation).toMutableList()
        var left = duration
        while (left.isPositive()) {
            val step = minOf(TICK, left)
            clock.advance(step)
            left -= step
            effects += observe(observation)
        }
        return effects
    }

    fun use(duration: Duration, pkg: String = FEED) = hold(using(pkg), duration)

    /** Screen off for [duration]: no ticks in between, like the real monitor. */
    fun idle(duration: Duration): List<EngineEffect> {
        val effects = observe(SCREEN_OFF).toMutableList()
        clock.advance(duration)
        effects += observe(SCREEN_OFF)
        return effects
    }

    /** Time passes with no events at all (process dead, phone rebooting). */
    fun silence(duration: Duration) = clock.advance(duration)

    fun snooze() = send(EngineEvent.RequestSnooze)
    fun friction() = send(EngineEvent.GrantFrictionOverride)

    /** Default ladder, used continuously: 10 + 5 + 2 min of use ends in a lockout. */
    fun reachLockout(): List<EngineEffect> = use(10.minutes) + use(5.minutes) + use(2.minutes)

    fun timeOf(predicate: (EngineEffect) -> Boolean): Instant? = log.firstOrNull { predicate(it.effect) }?.at

    companion object {
        val TICK = 5.seconds
    }
}
