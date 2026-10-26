package com.noikonomist.slapme.permissions

/** What the app has been granted right now. */
data class PermissionState(
    val usageAccess: Boolean,
    val overlay: Boolean,
    /** App notifications enabled (includes the Android 13+ runtime permission). */
    val notifications: Boolean,
    /** Exempt from battery optimization. */
    val batteryUnrestricted: Boolean,
)

enum class FixItem { USAGE_ACCESS, OVERLAY, NOTIFICATIONS, BATTERY }

enum class Severity {
    /** The app can't do its job without it. */
    REQUIRED,

    /** Works, but the user misses warnings and the snooze. */
    RECOMMENDED,

    /** Advice; dismissible. */
    TIP,
}

data class Fix(val item: FixItem, val severity: Severity)

enum class OnboardingStep { WELCOME, USAGE_DISCLOSURE, USAGE_ACCESS, OVERLAY, NOTIFICATIONS, BATTERY_TIP }

object PermissionChecklist {
    private const val GAP_TIP_WINDOW_MS = 7 * 24 * 60 * 60 * 1_000L

    /** What the home screen asks the user to fix, most important first. */
    fun fixes(
        state: PermissionState,
        batteryTipDismissedAtMillis: Long?,
        lastServiceGapAtMillis: Long?,
        nowMillis: Long,
    ): List<Fix> = buildList {
        if (!state.usageAccess) add(Fix(FixItem.USAGE_ACCESS, Severity.REQUIRED))
        if (!state.overlay) add(Fix(FixItem.OVERLAY, Severity.REQUIRED))
        if (!state.notifications) add(Fix(FixItem.NOTIFICATIONS, Severity.RECOMMENDED))

        // A recent kill shows the tip again, even if dismissed earlier or already unrestricted.
        val recentGap = lastServiceGapAtMillis != null &&
            nowMillis - lastServiceGapAtMillis <= GAP_TIP_WINDOW_MS &&
            (batteryTipDismissedAtMillis == null || lastServiceGapAtMillis > batteryTipDismissedAtMillis)
        val restricted = !state.batteryUnrestricted && batteryTipDismissedAtMillis == null
        if (recentGap || restricted) add(Fix(FixItem.BATTERY, Severity.TIP))
    }

    fun onboardingSteps(sdkInt: Int): List<OnboardingStep> =
        OnboardingStep.entries.filter { it != OnboardingStep.NOTIFICATIONS || sdkInt >= 33 }

    /** The usage-access settings are only offered after the disclosure was accepted. Null after the last step. */
    fun nextStep(current: OnboardingStep, steps: List<OnboardingStep>, disclosureAccepted: Boolean): OnboardingStep? {
        val rest = steps.dropWhile { it != current }.drop(1)
        return rest.firstOrNull { it != OnboardingStep.USAGE_ACCESS || disclosureAccepted }
    }

    /** Xiaomi, Redmi and POCO phones (MIUI / HyperOS), which need Autostart for boot re-arming. */
    fun isXiaomiFamily(manufacturer: String, brand: String): Boolean =
        listOf(manufacturer, brand).any { it.lowercase() in XIAOMI_FAMILY }

    private val XIAOMI_FAMILY = setOf("xiaomi", "redmi", "poco")
}
