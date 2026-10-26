package com.noikonomist.slapme.engine

import com.noikonomist.slapme.engine.EngineEffect.CancelWarning
import com.noikonomist.slapme.engine.EngineEffect.DayRolledOver
import com.noikonomist.slapme.engine.EngineEffect.EndLockout
import com.noikonomist.slapme.engine.EngineEffect.LogOverride
import com.noikonomist.slapme.engine.EngineEffect.OverrideRejected
import com.noikonomist.slapme.engine.EngineEffect.ServiceGap
import com.noikonomist.slapme.engine.EngineEffect.SessionExpired
import com.noikonomist.slapme.engine.EngineEffect.Slap
import com.noikonomist.slapme.engine.EngineEffect.StartLockout
import com.noikonomist.slapme.engine.EngineEffect.Warn
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The usage state machine: `(state, event, now) -> (state, effects)`.
 *
 * Each event first credits the time since the previous event to the usage state reported then
 * (split at local midnight and wherever activation changes), then refreshes timers and activation,
 * applies the event, and finally decides on slaps and warnings. The engine never touches Android; the
 * monitor feeds it observations and carries out the returned effects.
 *
 * Config and rules are fixed per instance: when they change, build a new engine and keep the state.
 */
class UsageEngine(
    val config: EngineConfig,
    val rules: ActivationRules,
    private val clock: Clock,
) {
    data class Step(val state: EngineState, val effects: List<EngineEffect>)

    fun handle(state: EngineState, event: EngineEvent): Step = reduce(state, event, clock.instant())

    fun reduce(state: EngineState, event: EngineEvent, now: Instant): Step = Transition(state, now).run(event)

    fun isArmed(now: Instant = clock.instant()): Boolean = rules.isArmed(now)

    fun freeSnoozeAvailable(state: EngineState, now: Instant = clock.instant()): Boolean {
        val period = rules.currentPeriod(now, clock.zone) ?: return false
        return period.key !in state.usedSnoozePeriods
    }

    private inner class Transition(private var s: EngineState, private val now: Instant) {
        private val nowMs = now.toEpochMilli()
        private val zone = clock.zone
        private val effects = mutableListOf<EngineEffect>()
        private var period: ActivePeriod? = null

        private val today: DailyStats get() = checkNotNull(s.today)

        fun run(event: EngineEvent): Step {
            account()
            refresh()
            apply(event)
            enforce()
            return Step(s, effects.toList())
        }

        // --- 1. credit elapsed time -------------------------------------------------------------

        private fun account() {
            val last = s.lastEventAtMillis
            if (last == null) {
                s = s.copy(lastEventAtMillis = nowMs, today = s.today ?: DailyStats(dateOf(nowMs)))
                return
            }
            if (s.today == null) s = s.copy(today = DailyStats(dateOf(last)))

            val elapsed = nowMs - last
            if (elapsed <= 0) {
                // Clock moved backwards: restart measuring from here.
                s = s.copy(lastEventAtMillis = nowMs)
                return
            }

            var usage = s.usage
            if (usage == UsageState.COUNTING && s.active && elapsed > config.maxTickGap.inWholeMilliseconds) {
                effects += ServiceGap(elapsed.milliseconds)
                usage = UsageState.IDLE
            }

            val cuts = rules.changesBetween(Instant.ofEpochMilli(last), now, zone).map { it.toEpochMilli() }
            var cursor: Long = last
            while (cursor < nowMs) {
                val midnight = nextMidnight(cursor)
                val end = minOf(nowMs, midnight, cuts.firstOrNull { it > cursor } ?: Long.MAX_VALUE)
                val active = rules.isActive(Instant.ofEpochMilli(cursor), zone)
                accumulate(end - cursor, if (active) usage else UsageState.IDLE, active)
                cursor = end
                if (cursor == midnight) rollOver(cursor)
            }
            s = s.copy(lastEventAtMillis = nowMs)
        }

        private fun accumulate(ms: Long, usage: UsageState, active: Boolean) {
            when (usage) {
                UsageState.COUNTING -> s = s.copy(
                    streakMillis = s.streakMillis + ms,
                    idleRunMillis = 0,
                    lockoutCountingMillis = if (s.inLockout) s.lockoutCountingMillis + ms else 0,
                    today = today.copy(countedMillis = today.countedMillis + ms),
                )

                UsageState.PAUSED -> Unit

                UsageState.IDLE -> {
                    val idleRun = s.idleRunMillis + ms
                    s = s.copy(idleRunMillis = idleRun, lockoutCountingMillis = 0)
                    if (idleRun >= config.streakResetBreak.inWholeMilliseconds) {
                        s = s.copy(streakMillis = 0)
                        cancelWarning()
                    }
                    if (idleRun >= config.escalationResetBreak.inWholeMilliseconds && !s.inLockout) {
                        s = s.copy(level = 0)
                    }
                }
            }

            s = if (active) {
                val run = s.focusRunMillis + ms
                s.copy(
                    focusRunMillis = run,
                    today = today.copy(longestFocusMillis = maxOf(today.longestFocusMillis, run)),
                )
            } else {
                s.copy(focusRunMillis = 0)
            }
        }

        private fun rollOver(midnightMs: Long) {
            effects += DayRolledOver(today)
            val stillCurrent = rules.currentPeriod(Instant.ofEpochMilli(midnightMs), zone)?.key
            s = s.copy(
                today = DailyStats(dateOf(midnightMs)),
                usedSnoozePeriods = s.usedSnoozePeriods.filterTo(mutableSetOf()) { it == stillCurrent },
            )
        }

        // --- 2. timers and activation -----------------------------------------------------------

        private fun refresh() {
            s.lockoutUntilMillis?.let { until ->
                if (nowMs >= until) {
                    s = s.copy(lockoutUntilMillis = null, lockoutCountingMillis = 0, level = 0, streakMillis = 0)
                    effects += EndLockout
                }
            }
            s.overrideUntilMillis?.let { until ->
                if (nowMs >= until) s = s.copy(overrideUntilMillis = null)
            }
            rules.session?.let { session ->
                val end = session.plannedEndMillis
                if (end != null && nowMs >= end && s.reportedExpiredSessionId != session.id) {
                    effects += SessionExpired(session.id)
                    s = s.copy(reportedExpiredSessionId = session.id)
                }
            }

            period = rules.currentPeriod(now, zone)
            val active = period != null
            if (s.active && !active) {
                s = s.copy(focusRunMillis = 0)
                cancelWarning()
            }
            s = s.copy(active = active)
        }

        // --- 3. the event itself ----------------------------------------------------------------

        private fun apply(event: EngineEvent) {
            when (event) {
                is EngineEvent.Observe -> s = s.copy(
                    usage = UsageClassifier.classify(event.observation, config),
                    foregroundPackage = when (val fg = event.observation.foreground) {
                        is Foreground.App -> fg.packageName
                        is Foreground.Exempt -> fg.packageName
                        Foreground.Launcher, Foreground.Unknown -> null
                    },
                )

                EngineEvent.RequestSnooze -> {
                    val current = period
                    when {
                        current == null -> effects += OverrideRejected(OverrideRejection.NOT_ACTIVE)
                        current.key in s.usedSnoozePeriods ->
                            effects += OverrideRejected(OverrideRejection.FREE_SNOOZE_USED)

                        else -> {
                            s = s.copy(usedSnoozePeriods = s.usedSnoozePeriods + current.key)
                            grantOverride(OverrideType.SNOOZE, config.snoozeDuration)
                        }
                    }
                }

                EngineEvent.GrantFrictionOverride ->
                    if (period == null) {
                        effects += OverrideRejected(OverrideRejection.NOT_ACTIVE)
                    } else {
                        grantOverride(OverrideType.FRICTION, config.frictionOverrideDuration)
                    }
            }
        }

        private fun grantOverride(type: OverrideType, duration: Duration) {
            val until = maxOf(s.overrideUntilMillis ?: 0, nowMs + duration.inWholeMilliseconds)
            s = s.copy(overrideUntilMillis = until)
            cancelWarning()
            effects += LogOverride(type, duration)
        }

        // --- 4. slaps and warnings --------------------------------------------------------------

        private fun enforce() {
            if (!s.active || s.usage != UsageState.COUNTING) return
            val overridden = s.overrideUntilMillis != null

            if (s.inLockout) {
                if (!overridden && s.lockoutCountingMillis >= config.lockoutGrace.inWholeMilliseconds) {
                    slap(SlapReason.LOCKOUT)
                }
                return
            }

            val cap = config.dailyCap
            val capReached = cap != null && today.countedMillis >= cap.inWholeMilliseconds
            val threshold = (if (capReached) config.shortestLevel else config.threshold(s.level)).inWholeMilliseconds

            if (!overridden && s.streakMillis >= threshold) {
                slap(if (capReached) SlapReason.DAILY_CAP else SlapReason.THRESHOLD)
                return
            }

            val lead = config.warningLead ?: return
            if (s.warningIssued) return
            val slapAt = maxOf(nowMs + threshold - s.streakMillis, s.overrideUntilMillis ?: 0)
            if (slapAt - nowMs <= lead.inWholeMilliseconds) {
                val freeSnooze = period?.let { it.key !in s.usedSnoozePeriods } ?: false
                effects += Warn(slapAt, freeSnooze)
                s = s.copy(warningIssued = true)
            }
        }

        private fun slap(reason: SlapReason) {
            effects += Slap(reason, s.level, s.foregroundPackage)
            s = when {
                s.inLockout -> s
                s.level >= config.lastLevel -> {
                    val until = nowMs + config.lockoutDuration.inWholeMilliseconds
                    effects += StartLockout(until)
                    s.copy(lockoutUntilMillis = until, level = config.lastLevel)
                }

                else -> s.copy(level = s.level + 1)
            }
            s = s.copy(streakMillis = 0, lockoutCountingMillis = 0, warningIssued = false, focusRunMillis = 0)
        }

        private fun cancelWarning() {
            if (s.warningIssued) {
                effects += CancelWarning
                s = s.copy(warningIssued = false)
            }
        }

        // --- time helpers -----------------------------------------------------------------------

        private fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

        private fun nextMidnight(ms: Long): Long =
            dateOf(ms).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
