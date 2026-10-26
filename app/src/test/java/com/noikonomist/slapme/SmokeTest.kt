package com.noikonomist.slapme

import org.junit.Assert.assertEquals
import org.junit.Test

/** Keeps the JVM test source set alive until the engine tests land. */
class SmokeTest {
    @Test
    fun jvmTestsRun() {
        assertEquals(4, 2 + 2)
    }
}
