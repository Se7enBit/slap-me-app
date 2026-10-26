package com.noikonomist.slapme.monitor

import com.noikonomist.slapme.engine.Foreground

/** Follows the foreground app from "activity resumed" usage events. Reads may overlap; older events are ignored. */
class ForegroundTracker {
    var foreground: String? = null
        private set
    private var lastEventMillis = Long.MIN_VALUE

    fun onResumed(packageName: String, atMillis: Long) {
        if (atMillis < lastEventMillis) return
        lastEventMillis = atMillis
        foreground = packageName
    }
}

/** Packages that are not ordinary app use. */
data class KnownPackages(
    val self: String,
    val launchers: Set<String>,
    val cameras: Set<String>,
    val navigation: Set<String>,
)

object ForegroundClassifier {
    fun classify(packageName: String?, known: KnownPackages): Foreground = when (packageName) {
        null -> Foreground.Unknown
        in known.launchers -> Foreground.Launcher
        known.self, in known.cameras, in known.navigation -> Foreground.Exempt(packageName)
        else -> Foreground.App(packageName)
    }
}
