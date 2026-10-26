package com.noikonomist.slapme.data.settings

import com.noikonomist.slapme.engine.EngineConfig

/** Everything the user can configure. Defaults come from [EngineConfig.Defaults]. */
data class Settings(
    val engine: EngineConfig = EngineConfig(),
    /** Activation mode: the threshold applies all day. */
    val alwaysOn: Boolean = false,
    /** Play the slap on the alarm stream even on silent / DND. Off = respect silent. */
    val alwaysPlaySound: Boolean = false,
    /** Navigation apps that pause counting. */
    val navigationPackages: Set<String> = EngineConfig.Defaults.navigationPackages,
)
