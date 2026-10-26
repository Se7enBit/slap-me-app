package com.noikonomist.slapme.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noikonomist.slapme.ui.apps.AppPickerScreen
import com.noikonomist.slapme.ui.home.HomeScreen
import com.noikonomist.slapme.ui.override.OverridePurpose
import com.noikonomist.slapme.ui.override.OverrideScreen
import com.noikonomist.slapme.ui.schedules.BlockEditorScreen
import com.noikonomist.slapme.ui.schedules.SchedulesScreen
import com.noikonomist.slapme.ui.settings.AppListKind
import com.noikonomist.slapme.ui.settings.SettingsScreen
import com.noikonomist.slapme.ui.stats.StatsScreen

private object Routes {
    const val HOME = "home"
    const val SCHEDULES = "schedules"
    const val BLOCK = "schedules/{id}"
    const val SETTINGS = "settings"
    const val APPS = "apps/{kind}"
    const val OVERRIDE = "override?purpose={purpose}"
    const val STATS = "stats"

    fun block(id: Long) = "schedules/$id"
    fun apps(kind: AppListKind) = "apps/${kind.name}"
    fun override(purpose: OverridePurpose) = "override?purpose=${purpose.name}"
}

/** [overrideRequest] grows by one each time a notification asks for the override screen. */
@Composable
fun SlapMeNavHost(overrideRequest: Int) {
    val nav = rememberNavController()
    fun toOverride(purpose: OverridePurpose) = nav.navigate(Routes.override(purpose)) { launchSingleTop = true }

    LaunchedEffect(overrideRequest) {
        if (overrideRequest > 0) toOverride(OverridePurpose.MORE_TIME)
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSchedules = { nav.navigate(Routes.SCHEDULES) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenStats = { nav.navigate(Routes.STATS) },
                onNeedMoreTime = { toOverride(OverridePurpose.MORE_TIME) },
                onSwitchOffParked = { toOverride(OverridePurpose.SWITCH_OFF) },
            )
        }
        composable(Routes.STATS) {
            StatsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SCHEDULES) {
            SchedulesScreen(
                onBack = { nav.popBackStack() },
                onEdit = { id -> nav.navigate(Routes.block(id)) },
                onSwitchOffParked = { toOverride(OverridePurpose.SWITCH_OFF) },
            )
        }
        composable(Routes.BLOCK, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            BlockEditorScreen(
                blockId = entry.arguments?.getLong("id") ?: 0,
                onDone = { nav.popBackStack() },
                onSwitchOffParked = { toOverride(OverridePurpose.SWITCH_OFF) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onPickApps = { kind -> nav.navigate(Routes.apps(kind)) },
                onSwitchOffParked = { toOverride(OverridePurpose.SWITCH_OFF) },
            )
        }
        composable(Routes.APPS) { entry ->
            val kind = entry.arguments?.getString("kind")?.let { name -> AppListKind.entries.firstOrNull { it.name == name } }
                ?: AppListKind.DISTRACTING
            AppPickerScreen(kind = kind, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.OVERRIDE,
            arguments = listOf(navArgument("purpose") { type = NavType.StringType; defaultValue = OverridePurpose.MORE_TIME.name }),
        ) { entry ->
            val purpose = entry.arguments?.getString("purpose")
                ?.let { name -> OverridePurpose.entries.firstOrNull { it.name == name } }
                ?: OverridePurpose.MORE_TIME
            OverrideScreen(purpose = purpose, onDone = { nav.backToHome() }, onBack = { nav.popBackStack() })
        }
    }
}

private fun NavHostController.backToHome() {
    if (!popBackStack(Routes.HOME, inclusive = false)) navigate(Routes.HOME)
}
