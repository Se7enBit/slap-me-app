package com.noikonomist.slapme.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundTrackerTest {

    private val tracker = ForegroundTracker()

    @Test
    fun nothingSeenYetIsUnknown() {
        assertNull(tracker.foreground)
    }

    @Test
    fun lastResumedPackageWins() {
        tracker.onResumed("com.a", 1_000)
        tracker.onResumed("com.b", 2_000)
        assertEquals("com.b", tracker.foreground)
    }

    @Test
    fun foregroundCarriesOverBetweenReads() {
        tracker.onResumed("com.a", 1_000)
        // a later read with no new events
        assertEquals("com.a", tracker.foreground)
        tracker.onResumed("com.b", 9_000)
        assertEquals("com.b", tracker.foreground)
    }

    @Test
    fun olderEventsFromAnOverlappingReadAreIgnored() {
        tracker.onResumed("com.a", 1_000)
        tracker.onResumed("com.b", 2_000)
        // the next read overlaps the previous window and repeats an older event
        tracker.onResumed("com.a", 1_000)
        assertEquals("com.b", tracker.foreground)
    }

    @Test
    fun sameTimestampStillCounts() {
        tracker.onResumed("com.a", 1_000)
        tracker.onResumed("com.b", 1_000)
        assertEquals("com.b", tracker.foreground)
    }
}
