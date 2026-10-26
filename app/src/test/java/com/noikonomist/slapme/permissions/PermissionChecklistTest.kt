package com.noikonomist.slapme.permissions

import com.noikonomist.slapme.permissions.FixItem.BATTERY
import com.noikonomist.slapme.permissions.FixItem.NOTIFICATIONS
import com.noikonomist.slapme.permissions.FixItem.OVERLAY
import com.noikonomist.slapme.permissions.FixItem.USAGE_ACCESS
import com.noikonomist.slapme.permissions.OnboardingStep.BATTERY_TIP
import com.noikonomist.slapme.permissions.OnboardingStep.NOTIFICATIONS as NOTIFICATIONS_STEP
import com.noikonomist.slapme.permissions.OnboardingStep.OVERLAY as OVERLAY_STEP
import com.noikonomist.slapme.permissions.OnboardingStep.USAGE_ACCESS as USAGE_STEP
import com.noikonomist.slapme.permissions.OnboardingStep.USAGE_DISCLOSURE
import com.noikonomist.slapme.permissions.OnboardingStep.WELCOME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionChecklistTest {

    private val allGranted = PermissionState(usageAccess = true, overlay = true, notifications = true, batteryUnrestricted = true)
    private val noneGranted = PermissionState(usageAccess = false, overlay = false, notifications = false, batteryUnrestricted = false)
    private val now = 1_800_000_000_000
    private val day = 24 * 60 * 60 * 1_000L

    private fun fixes(state: PermissionState, dismissedAt: Long? = null, lastGapAt: Long? = null) =
        PermissionChecklist.fixes(state, batteryTipDismissedAtMillis = dismissedAt, lastServiceGapAtMillis = lastGapAt, nowMillis = now)

    @Test
    fun nothingToFixWhenEverythingIsGranted() {
        assertEquals(emptyList<Fix>(), fixes(allGranted))
    }

    @Test
    fun missingItemsComeInOrderWithTheirSeverity() {
        assertEquals(
            listOf(
                Fix(USAGE_ACCESS, Severity.REQUIRED),
                Fix(OVERLAY, Severity.REQUIRED),
                Fix(NOTIFICATIONS, Severity.RECOMMENDED),
                Fix(BATTERY, Severity.TIP),
            ),
            fixes(noneGranted),
        )
    }

    @Test
    fun eachMissingPermissionOnItsOwn() {
        assertEquals(listOf(USAGE_ACCESS), fixes(allGranted.copy(usageAccess = false)).map { it.item })
        assertEquals(listOf(OVERLAY), fixes(allGranted.copy(overlay = false)).map { it.item })
        assertEquals(listOf(NOTIFICATIONS), fixes(allGranted.copy(notifications = false)).map { it.item })
        assertEquals(listOf(BATTERY), fixes(allGranted.copy(batteryUnrestricted = false)).map { it.item })
    }

    @Test
    fun dismissedBatteryTipStaysHidden() {
        assertEquals(emptyList<Fix>(), fixes(allGranted.copy(batteryUnrestricted = false), dismissedAt = now - day))
    }

    @Test
    fun recentServiceGapBringsTheBatteryTipBackEvenWhenUnrestricted() {
        assertEquals(listOf(BATTERY), fixes(allGranted, lastGapAt = now - day).map { it.item })
        assertEquals(
            "a gap after the dismissal shows it again",
            listOf(BATTERY),
            fixes(allGranted.copy(batteryUnrestricted = false), dismissedAt = now - 2 * day, lastGapAt = now - day).map { it.item },
        )
    }

    @Test
    fun oldOrAlreadyDismissedGapsDoNotShowTheTip() {
        assertEquals("older than 7 days", emptyList<Fix>(), fixes(allGranted, lastGapAt = now - 8 * day))
        assertEquals("dismissed after the gap", emptyList<Fix>(), fixes(allGranted, dismissedAt = now - day, lastGapAt = now - 2 * day))
    }

    @Test
    fun notificationsStepOnlyOnAndroid13AndUp() {
        assertEquals(
            listOf(WELCOME, USAGE_DISCLOSURE, USAGE_STEP, OVERLAY_STEP, NOTIFICATIONS_STEP, BATTERY_TIP),
            PermissionChecklist.onboardingSteps(sdkInt = 33),
        )
        assertEquals(
            listOf(WELCOME, USAGE_DISCLOSURE, USAGE_STEP, OVERLAY_STEP, BATTERY_TIP),
            PermissionChecklist.onboardingSteps(sdkInt = 32),
        )
    }

    @Test
    fun decliningTheDisclosureSkipsTheUsageAccessStep() {
        val steps = PermissionChecklist.onboardingSteps(sdkInt = 34)
        assertEquals(USAGE_STEP, PermissionChecklist.nextStep(USAGE_DISCLOSURE, steps, disclosureAccepted = true))
        assertEquals(OVERLAY_STEP, PermissionChecklist.nextStep(USAGE_DISCLOSURE, steps, disclosureAccepted = false))
        assertEquals(USAGE_DISCLOSURE, PermissionChecklist.nextStep(WELCOME, steps, disclosureAccepted = false))
        assertNull("after the last step", PermissionChecklist.nextStep(BATTERY_TIP, steps, disclosureAccepted = true))
    }

    @Test
    fun xiaomiFamilyIsRecognised() {
        assertTrue(PermissionChecklist.isXiaomiFamily(manufacturer = "Xiaomi", brand = "POCO"))
        assertTrue(PermissionChecklist.isXiaomiFamily(manufacturer = "Xiaomi", brand = "Redmi"))
        assertTrue(PermissionChecklist.isXiaomiFamily(manufacturer = "unknown", brand = "redmi"))
        assertFalse(PermissionChecklist.isXiaomiFamily(manufacturer = "samsung", brand = "samsung"))
        assertFalse(PermissionChecklist.isXiaomiFamily(manufacturer = "Google", brand = "google"))
    }
}
