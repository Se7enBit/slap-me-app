package com.noikonomist.slapme.monitor

import com.noikonomist.slapme.engine.Foreground
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundClassifierTest {

    private val packages = KnownPackages(
        self = "com.noikonomist.slapme",
        launchers = setOf("com.mi.android.globallauncher"),
        cameras = setOf("com.android.camera"),
        navigation = setOf("com.google.android.apps.maps"),
    )

    private fun classify(pkg: String?) = ForegroundClassifier.classify(pkg, packages)

    @Test
    fun unknownWhenNoPackage() {
        assertEquals(Foreground.Unknown, classify(null))
    }

    @Test
    fun launcherIsLauncher() {
        assertEquals(Foreground.Launcher, classify("com.mi.android.globallauncher"))
    }

    @Test
    fun selfCameraAndNavigationAreExempt() {
        for (pkg in listOf("com.noikonomist.slapme", "com.android.camera", "com.google.android.apps.maps")) {
            assertEquals(Foreground.Exempt(pkg), classify(pkg))
        }
    }

    @Test
    fun anythingElseIsAnApp() {
        assertEquals(Foreground.App("com.google.android.youtube"), classify("com.google.android.youtube"))
    }
}
