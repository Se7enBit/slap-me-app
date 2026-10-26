package com.noikonomist.slapme.data

import com.noikonomist.slapme.data.settings.SettingsRepository
import com.noikonomist.slapme.engine.ActivationRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The engine's activation input, kept current from schedules, the running session and always-on. */
class ActivationRulesRepository(
    schedules: ScheduleRepository,
    sessions: FocusSessionRepository,
    settings: SettingsRepository,
) {
    val rules: Flow<ActivationRules> = combine(
        schedules.blocks,
        sessions.running,
        settings.settings.map { it.alwaysOn }.distinctUntilChanged(),
    ) { blocks, session, alwaysOn -> ActivationRules(blocks, session, alwaysOn) }
        .distinctUntilChanged()
}
