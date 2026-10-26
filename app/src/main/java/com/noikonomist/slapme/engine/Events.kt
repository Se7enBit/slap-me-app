package com.noikonomist.slapme.engine

sealed interface EngineEvent {
    /** A tick or screen change with the current device snapshot. */
    data class Observe(val observation: Observation) : EngineEvent

    /** Use the free snooze of the current period. */
    data object RequestSnooze : EngineEvent

    /** The user completed the friction flow (wait + typed phrase); the UI enforces both. */
    data object GrantFrictionOverride : EngineEvent
}
